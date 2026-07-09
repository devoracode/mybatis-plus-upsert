package io.github.devoracode.upsert.core;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import io.github.devoracode.upsert.annotation.UpdateColumn;
import io.github.devoracode.upsert.exception.UpsertMetaException;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses an entity class into {@link UpsertMeta} and caches the result.
 * The annotation scan is performed eagerly at first access; the full UpsertMeta
 * is built lazily on first {@code getMeta} call. Both are cached for the lifetime
 * of the JVM (or until the Spring context is refreshed).
 *
 * <p>Thread-safety: All public methods are thread-safe. The internal cache uses
 * {@link ConcurrentHashMap} and double-checked locking for the lazy UpsertMeta
 * initialization.
 *
 * <p>Known limitation: The cache is JVM-wide and keyed only by entity class. In the
 * typical single {@code ApplicationContext} per JVM scenario this is safe. If multiple
 * independent contexts register the same entity class against different MyBatis-Plus
 * {@code TableInfo} configurations, the second context would incorrectly reuse the
 * first one's cached metadata. A proper fix requires scoping the cache per context.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaParser {

    private static final Map<Class<?>, CacheEntry> CACHE = new ConcurrentHashMap<>();

    /**
     * Checks whether the entity class has at least one {@link ConflictKey} field.
     * This is a lightweight check that only scans annotations.
     *
     * @param entityClass the entity class to check (must not be null)
     * @return true if the entity has at least one @ConflictKey field, false otherwise
     */
    public static boolean hasConflictKey(Class<?> entityClass) {
        return getOrCreateEntry(entityClass).scan.hasConflictKey;
    }

    /**
     * Gets the full {@link UpsertMeta} for the entity class, parsing and caching it
     * on first access.
     *
     * @param entityClass the entity class (must not be null)
     * @return the UpsertMeta containing all SQL generation metadata
     * @throws UpsertMetaException if the entity lacks @ConflictKey, has no updatable columns,
     *         or MyBatis-Plus TableInfo is not available
     */
    public static UpsertMeta getMeta(Class<?> entityClass) {
        CacheEntry entry = getOrCreateEntry(entityClass);
        UpsertMeta meta = entry.meta;
        if (meta == null) {
            synchronized (entry) {
                meta = entry.meta;
                if (meta == null) {
                    meta = parse(entityClass, entry.scan);
                    entry.meta = meta;
                }
            }
        }
        return meta;
    }

    private static CacheEntry getOrCreateEntry(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, c -> new CacheEntry(scanAnnotations(c)));
    }

    private static UpsertMeta parse(Class<?> entityClass, AnnotationScan scan) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            throw new UpsertMetaException("Cannot find MyBatis Plus TableInfo for: " + entityClass.getName()
                    + ". Ensure the entity is scanned by MyBatis Plus.");
        }

        if (!scan.hasConflictKey) {
            throw new UpsertMetaException(entityClass.getName() + ": no @ConflictKey field found");
        }
        List<String> sortedConflictFields = sortConflictFields(scan.conflictFieldOrder);

        int fieldCount = tableInfo.getFieldList().size() + 1; // +1 for pk
        List<String> insertColumns      = new ArrayList<>(fieldCount);
        List<String> insertFields       = new ArrayList<>(fieldCount);
        List<FieldMeta> insertFieldMetas = new ArrayList<>(fieldCount);
        List<String> updateColumns      = new ArrayList<>(fieldCount);
        List<String> updateFields       = new ArrayList<>(fieldCount);
        List<FieldMeta> updateFieldMetas = new ArrayList<>(fieldCount);
        Map<String, String> fieldToColumnMap = new HashMap<>(fieldCount * 2);

        addPrimaryKey(tableInfo, fieldToColumnMap, insertColumns, insertFields, insertFieldMetas);

        for (TableFieldInfo fi : tableInfo.getFieldList()) {
            String fieldName = fi.getProperty();
            String colName   = fi.getColumn();
            fieldToColumnMap.put(fieldName, colName);

            if (fi.getInsertStrategy() != FieldStrategy.NEVER) {
                insertColumns.add(colName);
                insertFields.add(fieldName);
                insertFieldMetas.add(toFieldMeta(fi, fi.getInsertStrategy()));
            }

            if (scan.conflictFieldOrder.containsKey(fieldName)) {
                continue;
            }
            if (shouldUpdateField(scan, fieldName)
                    && fi.getUpdateStrategy() != FieldStrategy.NEVER) {
                updateColumns.add(colName);
                updateFields.add(fieldName);
                updateFieldMetas.add(toFieldMeta(fi, fi.getUpdateStrategy()));
            }
        }

        if (updateColumns.isEmpty()) {
            throw new UpsertMetaException(entityClass.getName()
                    + ": no updatable columns found. At least one non-@ConflictKey, "
                    + "non-@IgnoreOnUpdate field with non-NEVER update strategy is required.");
        }

        List<String> conflictColumns = resolveConflictColumns(entityClass, sortedConflictFields, fieldToColumnMap);

        return UpsertMeta.builder()
                .tableName(tableInfo.getTableName())
                .insertColumns(Collections.unmodifiableList(insertColumns))
                .insertFields(Collections.unmodifiableList(insertFields))
                .conflictColumns(Collections.unmodifiableList(conflictColumns))
                .updateColumns(Collections.unmodifiableList(updateColumns))
                .updateFields(Collections.unmodifiableList(updateFields))
                .insertFieldMetas(Collections.unmodifiableList(insertFieldMetas))
                .updateFieldMetas(Collections.unmodifiableList(updateFieldMetas))
                .fieldToColumnMap(Collections.unmodifiableMap(fieldToColumnMap))
                .entityClass(entityClass)
                .build();
    }

    private static void addPrimaryKey(TableInfo tableInfo, Map<String, String> fieldToColumnMap,
                                      List<String> insertColumns, List<String> insertFields,
                                      List<FieldMeta> insertFieldMetas) {
        if (tableInfo.getKeyProperty() == null) {
            return;
        }
        String kp = tableInfo.getKeyProperty();
        String kc = tableInfo.getKeyColumn();
        fieldToColumnMap.put(kp, kc);
        insertColumns.add(kc);
        insertFields.add(kp);
        insertFieldMetas.add(FieldMeta.builder().column(kc).property(kp).dynamic(false).build());
    }

    private static boolean shouldUpdateField(AnnotationScan scan, String fieldName) {
        return scan.hasExplicitUpdate
                ? scan.explicitUpdateFieldNames.contains(fieldName)
                : !scan.ignoreFieldNames.contains(fieldName);
    }

    private static List<String> sortConflictFields(Map<String, Integer> conflictFieldOrder) {
        List<String> sorted = new ArrayList<>(conflictFieldOrder.keySet());
        sorted.sort(Comparator.comparingInt(conflictFieldOrder::get));
        return sorted;
    }

    private static List<String> resolveConflictColumns(Class<?> entityClass,
                                                       List<String> sortedConflictFields,
                                                       Map<String, String> fieldToColumnMap) {
        List<String> conflictColumns = new ArrayList<>(sortedConflictFields.size());
        for (String fieldName : sortedConflictFields) {
            String col = fieldToColumnMap.get(fieldName);
            if (col == null) {
                throw new UpsertMetaException(entityClass.getName()
                        + ": @ConflictKey field '" + fieldName + "' not found in TableInfo, check property name");
            }
            conflictColumns.add(col);
        }
        return conflictColumns;
    }

    private static AnnotationScan scanAnnotations(Class<?> entityClass) {
        Map<String, Integer> conflictFieldOrder = new HashMap<>();
        Set<String> ignoreFieldNames = new HashSet<>();
        Set<String> explicitUpdateFieldNames = new HashSet<>();
        boolean hasExplicitUpdate = false;
        boolean hasConflictKey = false;

        for (Field field : getAllFields(entityClass)) {
            if (field.isAnnotationPresent(ConflictKey.class)) {
                hasConflictKey = true;
                conflictFieldOrder.put(field.getName(), field.getAnnotation(ConflictKey.class).order());
            }
            if (field.isAnnotationPresent(IgnoreOnUpdate.class)) {
                ignoreFieldNames.add(field.getName());
            }
            if (field.isAnnotationPresent(UpdateColumn.class)) {
                hasExplicitUpdate = true;
                explicitUpdateFieldNames.add(field.getName());
            }
        }

        return new AnnotationScan(hasConflictKey, conflictFieldOrder, ignoreFieldNames,
                explicitUpdateFieldNames, hasExplicitUpdate);
    }

    private static FieldMeta toFieldMeta(TableFieldInfo fi, FieldStrategy strategy) {
        boolean isStringType = String.class.equals(fi.getPropertyType());
        boolean dynamic;
        boolean checkEmpty;
        switch (strategy) {
            case NOT_NULL:
                dynamic = true;
                checkEmpty = false;
                break;
            case NOT_EMPTY:
                dynamic = true;
                checkEmpty = isStringType;
                break;
            case IGNORED:
            case DEFAULT:
            default:
                dynamic = false;
                checkEmpty = false;
                break;
        }
        return FieldMeta.builder()
                .column(fi.getColumn())
                .property(fi.getProperty())
                .dynamic(dynamic)
                .checkEmpty(checkEmpty)
                .build();
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>(32);
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] declared = c.getDeclaredFields();
            for (Field f : declared) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * Combined cache entry: holds the eagerly-computed AnnotationScan and the
     * lazily-computed UpsertMeta (null until first getMeta call).
     */
    private static final class CacheEntry {
        final AnnotationScan scan;
        volatile UpsertMeta meta;

        CacheEntry(AnnotationScan scan) {
            this.scan = scan;
        }
    }

    private static final class AnnotationScan {
        final boolean hasConflictKey;
        final Map<String, Integer> conflictFieldOrder;
        final Set<String> ignoreFieldNames;
        final Set<String> explicitUpdateFieldNames;
        final boolean hasExplicitUpdate;

        AnnotationScan(boolean hasConflictKey, Map<String, Integer> conflictFieldOrder,
                       Set<String> ignoreFieldNames, Set<String> explicitUpdateFieldNames,
                       boolean hasExplicitUpdate) {
            this.hasConflictKey = hasConflictKey;
            this.conflictFieldOrder = conflictFieldOrder;
            this.ignoreFieldNames = ignoreFieldNames;
            this.explicitUpdateFieldNames = explicitUpdateFieldNames;
            this.hasExplicitUpdate = hasExplicitUpdate;
        }
    }
}