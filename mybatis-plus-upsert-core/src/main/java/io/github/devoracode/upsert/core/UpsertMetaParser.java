package io.github.devoracode.upsert.core;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
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
 * 将实体类解析为 {@link UpsertMeta} 并缓存结果。
 * 注解扫描在首次访问时立即执行；完整的 UpsertMeta 则在首次调用 {@code getMeta} 时惰性构建。
 * 两者均在 JVM 生命周期内（或 Spring 上下文刷新前）保持缓存。
 *
 * <p>线程安全：所有公共方法均为线程安全的。内部缓存使用
 * {@link ConcurrentHashMap}，惰性 UpsertMeta 初始化采用双重检查锁。
 *
 * <p>已知限制：缓存为 JVM 全局级别，仅以实体类为键。在典型的每个 JVM 单一
 * {@code ApplicationContext} 场景下这是安全的。如果多个独立的上下文将同一个实体类
 * 注册到不同的 MyBatis-Plus {@code TableInfo} 配置上，第二个上下文会错误地复用第一个上下文的缓存元数据。
 * 正确的修复方式是将缓存按上下文作用域进行限定。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaParser {

    private static final Map<Class<?>, CacheEntry> CACHE = new ConcurrentHashMap<>();

    /**
     * 检查实体类是否至少包含一个 {@link ConflictKey} 字段。
     * 这是一个轻量级检查，仅扫描注解。
     *
     * @param entityClass 待检查的实体类（不能为 null）
     * @return 如果实体包含至少一个 @ConflictKey 字段则返回 true，否则返回 false
     */
    public static boolean hasConflictKey(Class<?> entityClass) {
        return getOrCreateEntry(entityClass).scan.hasConflictKey;
    }

    /**
     * 获取实体类的完整 {@link UpsertMeta}，在首次访问时进行解析和缓存。
     *
     * @param entityClass 实体类（不能为 null）
     * @return 包含全部 SQL 生成元数据的 UpsertMeta
     * @throws UpsertMetaException 如果实体缺少 @ConflictKey、无可更新列或 MyBatis-Plus TableInfo 不可用
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
            throw new UpsertMetaException("No MyBatis Plus TableInfo found for entity " + entityClass.getName()
                    + ". Make sure the entity is scanned by MyBatis Plus.");
        }

        if (!scan.hasConflictKey) {
            throw new UpsertMetaException(entityClass.getName() + ": no @ConflictKey field found");
        }
        if (tableInfo.getKeyProperty() != null
                && tableInfo.getIdType() == IdType.AUTO
                && scan.conflictFieldOrder.containsKey(tableInfo.getKeyProperty())) {
            throw new UpsertMetaException(entityClass.getName()
                    + ": @ConflictKey cannot be placed on an auto-increment (IdType.AUTO) primary key;"
                    + " the conflict key must be a user-provided column");
        }
        List<String> sortedConflictFields = sortConflictFields(scan.conflictFieldOrder);

        int fieldCount = tableInfo.getFieldList().size() + 1; // +1 用于主键
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
                insertFieldMetas.add(toFieldMeta(fi, fi.getInsertStrategy(), false));
            }

            if (scan.conflictFieldOrder.containsKey(fieldName)) {
                continue;
            }
            if (shouldUpdateField(scan, fieldName)
                    && fi.getUpdateStrategy() != FieldStrategy.NEVER) {
                updateColumns.add(colName);
                updateFields.add(fieldName);
                // 只更新不插入的字段（insertStrategy=NEVER）不能用行引用赋值
                boolean paramRef = fi.getInsertStrategy() == FieldStrategy.NEVER;
                updateFieldMetas.add(toFieldMeta(fi, fi.getUpdateStrategy(), paramRef));
            }
        }

        if (updateColumns.isEmpty()) {
            throw new UpsertMetaException(entityClass.getName()
                    + ": no updatable column found. At least one field that is not a @ConflictKey,"
                    + " not @IgnoreOnUpdate and has a non-NEVER update strategy is required.");
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
        if (tableInfo.getIdType() == IdType.AUTO) {
            // 自增主键由数据库生成，不进入 INSERT 列表——显式插入 NULL 在
            // PostgreSQL（serial 列 NOT NULL 约束）等数据库下会失败，
            // 且与 MyBatis-Plus 自身 insert 的行为保持一致
            return;
        }
        insertColumns.add(kc);
        insertFields.add(kp);
        insertFieldMetas.add(FieldMeta.builder().column(kc).property(kp).dynamic(false).build());
    }

    private static boolean shouldUpdateField(AnnotationScan scan, String fieldName) {
        return scan.hasExplicitUpdate
                ? scan.explicitUpdateFieldNames.contains(fieldName)
                : !scan.ignoreFieldNames.contains(fieldName);
    }

    /**
     * 按 order 升序排序冲突键字段；order 相同（含全部为默认值 0）时按字段名
     * 字典序稳定排序，保证生成的 SQL 在不同 JVM 运行间一致。
     */
    private static List<String> sortConflictFields(Map<String, Integer> conflictFieldOrder) {
        List<String> sorted = new ArrayList<>(conflictFieldOrder.keySet());
        sorted.sort(Comparator.comparingInt((String a) -> conflictFieldOrder.get(a)).thenComparing(a -> a));
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
                        + ": @ConflictKey field '" + fieldName + "' not found in TableInfo, check the property name mapping");
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

    private static FieldMeta toFieldMeta(TableFieldInfo fi, FieldStrategy strategy, boolean paramRef) {
        boolean isStringType = String.class.equals(fi.getPropertyType());
        boolean dynamic;
        boolean checkEmpty;
        // FieldStrategy.IGNORED（3.5.11 起已从 MyBatis-Plus 移除，由 ALWAYS 取代）不单独
        // 列 case——其语义与 default 分支一致（不做判空、始终出现在 SQL 中），
        // 3.5.10 及以下由 default 覆盖，保证 3.5.7 至 3.5.17+ 全版本编译通过
        switch (strategy) {
            case NOT_NULL:
                dynamic = true;
                checkEmpty = false;
                break;
            case NOT_EMPTY:
                dynamic = true;
                checkEmpty = isStringType;
                break;
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
                .paramRef(paramRef)
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
     * 组合缓存条目：持有立即计算的 AnnotationScan 和惰性计算的 UpsertMeta
     * （在首次调用 getMeta 之前为 null）。
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
