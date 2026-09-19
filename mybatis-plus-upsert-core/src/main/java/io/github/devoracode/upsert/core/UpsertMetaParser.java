package io.github.devoracode.upsert.core;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import io.github.devoracode.upsert.annotation.UpdateColumn;
import io.github.devoracode.upsert.exception.UpsertMetaException;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 将 MyBatis-Plus {@link TableInfo} 解析为 {@link UpsertMeta} 的无状态解析器。
 *
 * <p><strong>本类不持有任何静态可变状态</strong>：没有全局元数据缓存，也不通过
 * {@code TableInfoHelper} 的全局注册表按实体类反查 TableInfo。解析的唯一数据来源
 * 是调用方在 SQL 注入期传入的 {@link TableInfo}——它由 MyBatis-Plus 在当前
 * {@code Configuration} 上初始化并直接交给注入方法，天然属于该上下文。
 * 因此多个 Spring ApplicationContext / 多个 MyBatis Configuration 共存时，
 * 各自注入的语句只会使用各自上下文的元数据，结构上不存在跨上下文串用的通道。
 *
 * <p>之所以不需要缓存：{@code getMeta} 只在 SQL 注入期（应用启动阶段）被调用，
 * 每个实体在每个 Mapper 上至多调用几次（upsert / upsertBatch / upsertExecutor），
 * 运行期执行 Upsert 不经过本类。解析一次的开销是微秒级的注解扫描加列表构建，
 * 远小于其换取的正确性收益。
 *
 * <p>线程安全：本类无可变状态，所有公共方法天然线程安全。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMetaParser {

    /**
     * 检查实体类是否至少包含一个 {@link ConflictKey} 字段。
     * 这是一个轻量级检查，仅扫描注解，与具体 {@code Configuration} 无关。
     *
     * @param entityClass 待检查的实体类（不能为 null）
     * @return 如果实体包含至少一个 @ConflictKey 字段则返回 true，否则返回 false
     */
    public static boolean hasConflictKey(Class<?> entityClass) {
        return scanAnnotations(entityClass).hasConflictKey;
    }

    /**
     * 解析给定 {@link TableInfo} 所属实体的完整 {@link UpsertMeta}。
     *
     * <p>每次调用都基于传入的 TableInfo 重新解析，不读写任何共享缓存；
     * 调用方应传入自己上下文中 MyBatis-Plus 初始化并递交的那份 TableInfo
     * （例如注入方法 {@code injectMappedStatement} 的参数）。
     *
     * @param tableInfo 当前 Configuration 下的实体表元数据（不能为 null）
     * @return 包含全部 SQL 生成元数据的 UpsertMeta
     * @throws UpsertMetaException 如果实体缺少 @ConflictKey、无可更新列，
     *         或 @ConflictKey 字段声明了 {@code insertStrategy = NEVER}
     *         （冲突键必须参与 INSERT）
     */
    public static UpsertMeta getMeta(TableInfo tableInfo) {
        Objects.requireNonNull(tableInfo, "tableInfo must not be null");
        Class<?> entityClass = tableInfo.getEntityType();
        AnnotationScan scan = scanAnnotations(entityClass);

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

            boolean conflictKey = scan.conflictFieldOrder.containsKey(fieldName);
            if (conflictKey && fi.getInsertStrategy() == FieldStrategy.NEVER) {
                throw new UpsertMetaException(entityClass.getName() + ": @ConflictKey field '" + fieldName
                        + "' declares insertStrategy=NEVER; a conflict key must participate in INSERT"
                        + " to keep conflict detection and the INSERT column list consistent");
            }
            if (fi.getInsertStrategy() != FieldStrategy.NEVER) {
                insertColumns.add(colName);
                insertFields.add(fieldName);
                // 冲突键是 Upsert 语义必需字段：强制非动态（不做判空），
                // 确保其始终出现在 INSERT 列与参数中，与 ON 冲突判断保持一致——
                // 否则冲突键为 null 时会被动态策略静默剔除，UPDATE 场景退化为 INSERT
                insertFieldMetas.add(conflictKey
                        ? FieldMeta.builder().column(colName).property(fieldName).dynamic(false).build()
                        : toFieldMeta(fi, fi.getInsertStrategy(), false));
            }

            if (conflictKey) {
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
     * 一次性的注解扫描结果，仅在单次解析内复用（本类不缓存它）。
     */
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
