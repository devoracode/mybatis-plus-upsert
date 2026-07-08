# Mybatis-plus Upsert

基于 MyBatis Plus 扩展 `BaseMapper`，为 Spring Boot 2.x / 3.x 项目提供开箱即用的跨数据库 **Upsert** 能力（存在则更新，不存在则插入）。

无需写 XML、无需自定义 SQL，只需在实体字段上加注解，调用 `upsert()` / `upsertBatch()` / `upsert(Collection)` 即可。

---

## 目录

- [支持环境](#支持环境)
- [快速开始](#快速开始)
- [多数据源支持](#多数据源支持)
- [注解详解](#注解详解)
- [注解组合规则](#注解组合规则)
- [字段动态判断](#字段动态判断)
- [配置项说明](#配置项说明)
- [与已有自定义 SqlInjector 共存](#与已有自定义-sqlinjector-共存)
- [自定义方言](#自定义方言)
- [各数据库生成的 SQL 示例](#各数据库生成的-sql-示例)
- [异常说明](#异常说明)
- [常见问题](#常见问题)
- [数据库注意事项](#数据库注意事项)

---

## 支持环境

| 项目 | 要求 |
|---|---|
| JDK | 8+（Spring Boot 2.x）/ 17+（Spring Boot 3.x） |
| Spring Boot | 2.4.5+ / 3.2.x |
| MyBatis Plus | 3.5.9 |
| 数据库 | MySQL / MariaDB、PostgreSQL、Oracle、SQL Server、H2（内置）；其他数据库需自行实现 `UpsertDialect`，见[自定义方言](#自定义方言) |

---

## 快速开始

### 第一步：引入依赖

根据你的 Spring Boot 版本选择对应的 MyBatis-Plus starter：

**Spring Boot 2.x（JDK 8+）：**

```xml
<dependency>
    <groupId>io.github.devoracode</groupId>
    <artifactId>mybatis-plus-upsert-boot-starter</artifactId>
    <version>latestVersion</version>
</dependency>

<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.9</version>
</dependency>
```

> **JDK 8 用户注意**：`mybatis-plus-boot-starter:3.5.9` 默认传递依赖 `jsqlparser:5.0`（仅支持 JDK 11+），需排除并替换为 JDK 8 兼容版本：
> ```xml
> <dependency>
>     <groupId>com.baomidou</groupId>
>     <artifactId>mybatis-plus-boot-starter</artifactId>
>     <version>3.5.9</version>
>     <exclusions>
>         <exclusion>
>             <groupId>com.baomidou</groupId>
>             <artifactId>mybatis-plus-jsqlparser</artifactId>
>         </exclusion>
>     </exclusions>
> </dependency>
> <dependency>
>     <groupId>com.baomidou</groupId>
>     <artifactId>mybatis-plus-jsqlparser-4.9</artifactId>
>     <version>3.5.9</version>
> </dependency>
> ```

**Spring Boot 3.x（JDK 17+）：**

```xml
<dependency>
    <groupId>io.github.devoracode</groupId>
    <artifactId>mybatis-plus-upsert-boot-starter</artifactId>
    <version>${latestVersion}</version>
</dependency>

<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>
```

starter 会根据配置的 `db-type` 完成配置，**无需任何额外 Bean 声明**（前提是项目中没有自定义 `ISqlInjector`，该场景见[与已有自定义 SqlInjector 共存](#与已有自定义-sqlinjector-共存)）。

---

### 第二步：建表时添加唯一约束

`@ConflictKey` 标注的字段必须在数据库中存在对应的**唯一索引或主键**，数据库才会触发冲突检测。

```sql
CREATE TABLE t_user (
    id          BIGINT       NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    email       VARCHAR(128),
    age         INT,
    create_time DATETIME,
    update_time DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)   -- @ConflictKey 对应的唯一索引
);
```

---

### 第三步：标注实体类

```java
@TableName("t_user")
public class UserEntity {

    @TableId
    private Long id;

    @ConflictKey                   // 冲突检测字段，必须有对应的数据库唯一约束
    private String username;

    private String email;

    private Integer age;

    @IgnoreOnUpdate                // 更新时跳过，只在首次 INSERT 时写入
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

---

### 第四步：Mapper 继承 UpsertMapper

```java
@Mapper
public interface UserMapper extends UpsertMapper {
    // UpsertMapper 已继承 BaseMapper，所有 MP 原生方法均可用
    // 额外增加：upsert(entity)、upsertBatch(list)、upsert(Collection)/upsert(Collection, batchSize)
}
```

---

### 第五步：调用

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    // 单条 upsert：username 存在则更新 email/age/update_time，不存在则插入
    public void saveOrUpdate(UserEntity user) {
        userMapper.upsert(user);
    }

    // 批量 upsert：固定列集合，拼一条多值 SQL，一次网络往返，吞吐量优先
    public void saveOrUpdateBatch(List users) {
        userMapper.upsertBatch(users);
    }

    // 批量 upsert（与 MP BaseMapper#insert(Collection) 语义对齐）：逐实体按 NOT_NULL/NOT_EMPTY
    // 动态判空拼列，通过 JDBC BATCH 模式逐条执行，返回 List。
    // 列集合可能逐行不同，因此无法像 upsertBatch 那样拼进同一条多值 SQL；
    // 换吞吐量优先的"一条 SQL"为"逐行动态列"，按需选择。
    public List saveOrUpdateBatchDynamic(List users) {
        return userMapper.upsert(users);
    }
}
```

---

## 多数据源支持

当项目使用 **dynamic-datasource-spring-boot-starter**（baomidou）进行多数据源管理时，可引入专用的 starter 来支持混合数据库场景（如 MySQL + PostgreSQL 同时使用）。

### 引入依赖

```xml
<dependency>
    <groupId>io.github.devoracode</groupId>
    <artifactId>mybatis-plus-upsert-dynamic-datasource-boot-starter</artifactId>
    <version>${latestVersion}</version>
</dependency>

<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
    <version>4.3.1</version>
</dependency>
```

### 配置示例

多数据源场景下，`db-type` 会**自动从 JDBC URL 推断**，无需手动配置。只有当数据库类型无法推断或需要自定义方言时，才需要显式声明。

**场景 1：全部 MySQL（零配置）**

```yaml
spring:
  datasource:
    dynamic:
      primary: mysql
      datasource:
        mysql-master:
          url: jdbc:mysql://localhost:3306/db_master
          username: root
          password: ***
        mysql-slave1:
          url: jdbc:mysql://localhost:3307/db_slave
          username: root
          password: ***

mybatis-plus:
  upsert:
    dynamic:
      enabled: true
      use-new-mysql-syntax: true   # 全局配置，所有 MySQL 数据源生效
```

**场景 2：混合类型（完全自动推断）**

```yaml
spring:
  datasource:
    dynamic:
      primary: mysql
      datasource:
        mysql:
          url: jdbc:mysql://localhost:3306/db_mysql
          username: root
          password: ***
        postgresql:
          url: jdbc:postgresql://localhost:5432/db_pg
          username: postgres
          password: ***

mybatis-plus:
  upsert:
    dynamic:
      enabled: true
      use-new-mysql-syntax: true   # MySQL 数据源使用新语法，PostgreSQL 自动忽略此配置
```

**场景 3：MySQL 版本混合（5.x 和 8.0+）**

```yaml
spring:
  datasource:
    dynamic:
      primary: mysql8
      datasource:
        mysql8:
          url: jdbc:mysql://localhost:3306/db_mysql8
          username: root
          password: ***
        mysql5:
          url: jdbc:mysql://localhost:3307/db_mysql5
          username: root
          password: ***

mybatis-plus:
  upsert:
    dynamic:
      enabled: true
      use-new-mysql-syntax: true      # 全局默认：使用新语法（MySQL 8.0.19+）
      datasource:
        mysql5:
          use-new-mysql-syntax: false  # 覆盖：MySQL 5.x 使用旧语法（VALUES()）
```

**场景 4：需要手动指定（无法自动推断或使用自定义方言）**

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        tidb:
          url: jdbc:mysql://localhost:4000/db_tidb   # TiDB 使用 MySQL 协议，自动推断为 mysql
        clickhouse:
          url: jdbc:clickhouse://localhost:8123/db_ch

mybatis-plus:
  upsert:
    dynamic:
      enabled: true
      datasource:
        clickhouse:
          db-type: custom
          dialect-ref: clickHouseUpsertDialect   # 自定义方言需要手动配置
```

### 使用方式

在 Service 层使用 `@DS` 注解切换数据源，upsert 方法会自动使用对应数据源的方言：

```java
@Service
public class UserService {

    private final UserMapper userMapper;

    @DS("mysql")
    @Transactional
    public void upsertToMysql(User user) {
        userMapper.upsert(user);
    }

    @DS("postgresql")
    @Transactional
    public void upsertToPg(User user) {
        userMapper.upsert(user);
    }

    @DS("mysql")
    @Transactional
    public void upsertBatchToMysql(List<User> users) {
        userMapper.upsertBatch(users);
    }
}
```

### 工作原理

1. 启动时读取 `spring.datasource.dynamic.datasource` 配置，遍历所有数据源
2. 对每个数据源：优先使用 `mybatis-plus.upsert.dynamic.datasource.{name}.db-type` 显式配置；若未配置，则从 JDBC URL 自动推断（支持 `jdbc:mysql:`、`jdbc:postgresql:`、`jdbc:oracle:`、`jdbc:sqlserver:`、`jdbc:h2:`）
3. 根据推断结果创建对应的 `UpsertDialect` 实例并注册到 `DynamicUpsertDialect`
4. 运行时通过 `DynamicDataSourceContextHolder.peek()` 获取当前数据源名称，路由到对应的方言生成 SQL

### 配置项说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `mybatis-plus.upsert.dynamic.enabled` | `true` | 是否启用动态数据源支持 |
| `mybatis-plus.upsert.dynamic.use-new-mysql-syntax` | `false` | **全局**默认：MySQL 数据源是否使用新语法（AS new），可被单个数据源配置覆盖 |
| `mybatis-plus.upsert.dynamic.datasource.{dsName}.db-type` | 自动推断 | 该数据源的数据库类型（mysql/postgresql/oracle/sqlserver/h2/custom）。**可选**，未配置时从 JDBC URL 自动推断 |
| `mybatis-plus.upsert.dynamic.datasource.{dsName}.use-new-mysql-syntax` | 继承全局配置 | 单个数据源的 MySQL 语法开关，覆盖全局配置 |
| `mybatis-plus.upsert.dynamic.datasource.{dsName}.dialect-ref` | - | 自定义方言 Bean 名称，仅在 `db-type=custom` 时生效 |

> **注意**：
> - 使用多数据源 starter 时，`mybatis-plus.upsert.db-type` 单数据源配置不再生效。
> - **`mybatis-plus-upsert-boot-starter`（单数据源）与 `mybatis-plus-upsert-dynamic-datasource-boot-starter`（多数据源）互斥**，不能同时在 classpath 上，否则会导致自动配置冲突。

---

## 注解详解

| 注解 | 属性 | 说明 | 位置 |
|---|---|---|---|
| `@ConflictKey` | `order`（int，默认 0） | 冲突检测字段；`order` 控制多字段时在 `CONFLICT(...)` 中的列顺序，值越小越靠前 | 字段 |
| `@IgnoreOnUpdate` | 无 | 冲突时跳过该字段，不参与 UPDATE SET | 字段 |
| `@UpdateColumn` | 无 | 显式指定更新字段；标注后只更新有此注解的字段 | 字段 |

> `@UpdateColumn` 与 `@IgnoreOnUpdate` 互斥：一旦有字段标注了 `@UpdateColumn`，`@IgnoreOnUpdate` 将不再生效。

---

### `@ConflictKey`

标记冲突检测字段，相当于告诉数据库"当这个字段的值已存在时，触发 UPDATE 而不是报错"。

- **必须至少标注一个**，否则启动时抛 `UpsertMetaException`
- **可标注多个**，多字段组合冲突检测（对应联合唯一索引）
- 标注的字段参与 INSERT，但**不参与 UPDATE SET**（不会把自己更新掉）
- `order` 属性控制多字段时在 `CONFLICT(...)` / `ON(...)` 中的列顺序，**值越小越靠前，默认 0**

```java
// 单字段冲突（无需指定 order）
@ConflictKey
private String username;
```

```java
// 多字段联合冲突（对应 UNIQUE(tenant_id, biz_code) 联合唯一索引）
// order 决定生成 SQL 中的列顺序，必须与数据库索引定义顺序一致
@ConflictKey(order = 0)   // 排在前：ON CONFLICT (tenant_id, biz_code)
private String tenantId;

@ConflictKey(order = 1)   // 排在后
private String bizCode;
```

> **为什么顺序重要？**
>
> PostgreSQL 的 `ON CONFLICT (col1, col2)` 要求括号内的列顺序与目标唯一索引的定义顺序完全匹配，否则会报 `there is no unique or exclusion constraint matching the ON CONFLICT specification`。Oracle 和 SQL Server 的 `MERGE ON (...)` 虽然对顺序不强制，但保持与索引一致有助于优化器利用索引。
>
> 当多个字段的 `order` 值相同时，顺序不确定，建议为每个字段指定不同的 `order`。

---

### `@IgnoreOnUpdate`

标记在 UPDATE SET 时跳过的字段，常用于只写一次的审计字段。

- 字段仍然参与 INSERT（首次插入时写入）
- 发生冲突触发 UPDATE 时，该字段不出现在 SET 子句中
- 若同时存在 `@UpdateColumn`，则 `@IgnoreOnUpdate` **不生效**（见[注解组合规则](#注解组合规则)）

```java
@IgnoreOnUpdate
private LocalDateTime createTime;   // 只在插入时写，更新时保留原值

@IgnoreOnUpdate
private Long createdBy;             // 创建人，更新时不覆盖
```

---

### `@UpdateColumn`

显式声明哪些字段在冲突时需要更新。一旦有任何字段标注了 `@UpdateColumn`，**更新列表就只包含这些字段**，其他字段（包括被 `@IgnoreOnUpdate` 标注的字段）均被忽略。

适用于"只想更新其中几个字段"的场景，比全量更新更精确。

```java
@TableName("t_product")
public class ProductEntity {

    @TableId
    private Long id;

    @ConflictKey
    private String sku;

    @UpdateColumn           // 冲突时只更新 stock 和 updateTime
    private Integer stock;

    @UpdateColumn
    private LocalDateTime updateTime;

    private String name;    // 不标 @UpdateColumn，冲突时不更新
    private BigDecimal price;
}
```

---

## 注解组合规则

下表说明不同注解组合时，字段是否参与 INSERT 和 UPDATE SET：

| 场景 | 参与 INSERT | 参与 UPDATE SET |
|---|---|---|
| 普通字段（无注解） | ✅ | ✅ |
| `@ConflictKey` | ✅ | ❌（用于 ON 条件） |
| `@IgnoreOnUpdate` | ✅ | ❌ |
| `@UpdateColumn` | ✅ | ✅ |
| 存在任意 `@UpdateColumn` 时，无此注解的普通字段 | ✅ | ❌ |
| 存在任意 `@UpdateColumn` 时，`@IgnoreOnUpdate` 字段 | ✅ | ❌（已被 `@UpdateColumn` 白名单取代，结果一致但原因不同） |

**优先级总结：`@ConflictKey` > `@UpdateColumn` 白名单 > `@IgnoreOnUpdate` 黑名单 > 默认全量更新**

> 上表说明的是"哪些列出现在 SQL 结构里"。至于这些列在单条 upsert 中是否需要按值动态判断（null 时整列消失），见下一节[字段动态判断](#字段动态判断)，这是两个独立的维度。

---

## 字段动态判断

MyBatis Plus 的原生 `insert` / `updateById` 方法会按字段的 `FieldStrategy`（`insertStrategy` / `updateStrategy`）动态决定该字段是否出现在 SQL 中。本 starter 的**单条** `upsert` 完全遵循这一行为，无需任何额外配置。

### 行为对照

| FieldStrategy | 行为 |
|---|---|
| `NOT_NULL`（**全局默认**） | 字段为 null 时不出现在 SQL 中 |
| `NOT_EMPTY` | 字符串字段为 null 或空字符串时不出现在 SQL 中；非字符串字段退化为 `NOT_NULL` 判断 |
| `IGNORED` | 忽略判断，始终出现在 SQL 中（不代表"忽略该字段"，而是"忽略 null/empty 判断"） |
| `NEVER` | 该字段永远不出现在 SQL 中，无论值是什么；单条和批量场景均不受影响，始终被排除 |
| `DEFAULT` | 注解上代表"跟随全局配置"；全局配置上代表 `NOT_NULL`。MP 在解析阶段已将其转换为实际生效的策略，本 starter 读到的是转换后的值，不会是 `DEFAULT` 本身 |

由于 MP 全局默认策略是 `NOT_NULL`，**未显式标注 `@TableField` 的字段默认就是动态字段**：

```java
@TableName("t_user")
public class UserEntity {

    @TableId
    private Long id;            // 主键始终原样拼接，不受 FieldStrategy 影响

    @ConflictKey
    private String username;    // 冲突键始终原样拼接（用于 ON 条件匹配，不应为 null）

    private String email;       // 未标注，默认 NOT_NULL：为 null 时该列不参与 INSERT/UPDATE

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;      // 显式声明忽略判断，即使值为 null 也会写入（覆盖原值为 NULL）

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String internalToken; // 显式声明永远不出现在 SQL 中，无论 INSERT 还是 UPDATE 都不会被本 starter 写入
}
```

> `NEVER` 与"动态判断为 false 时不出现"是两个不同的概念：动态判断结果取决于运行时的字段值，而 `NEVER` 是在启动阶段解析元数据时就直接把该字段从 INSERT/UPDATE 候选列表中剔除，运行时无论值是什么都不会出现，性质上更接近"永久排除"而非"按值判断"。

### 效果示例

```java
// 数据库中已有 alice 的记录：email=alice@old.com, age=25
UserEntity partial = UserEntity.builder()
        .username("alice")
        .email(null)   // 不想修改 email
        .age(30)        // 只想更新 age
        .build();
userMapper.upsert(partial);
// 结果：email 保持 alice@old.com 不变，age 变为 30
// 等价于 MP 原生 updateById 在 NOT_NULL 策略下的行为
```

### 为什么 upsertBatch 不支持这个行为，以及 upsert(Collection) 怎么做到

这里特指 `NOT_NULL`/`NOT_EMPTY` 这种**按运行时值判断**的动态行为：`upsertBatch` 生成的是一条多行 `VALUES (...), (...), ...` 的单 SQL，要求每一行的列数严格一致才能对齐成一条合法 SQL。如果不同行因为字段值不同导致动态判断结果不同（比如第一行 email 非 null、第二行 email 为 null），就无法生成一条统一的批量语句。

这与 MyBatis Plus 自身 `insertBatchSomeColumn` 的取舍一致：批量方法基于第一条记录确定列集合，不支持逐行动态列。

如果业务上确实需要"批量但每条记录的动态判断结果可能不同"，用 `upsert(Collection<T>)`（对齐 MP `BaseMapper#insert(Collection<T>)` 语义）：它复用单条 `upsert` 那份带 `<if>` 判空的 SQL，逐条通过 JDBC BATCH 模式执行（而不是拼一条多值 SQL），列集合可以逐行不同，返回 `List<BatchResult>`。代价是失去了"一条 SQL、一次网络往返"的吞吐量优势——三种方法按场景选择：

| 方法 | SQL 形态 | 逐行动态判断 | 适用场景 |
|---|---|---|---|
| `upsert(T)` | 单条 | 支持 | 单条写入 |
| `upsertBatch(List<T>)` | 一条多值 SQL | 不支持，固定列集合 | 批量写入，吞吐量优先 |
| `upsert(Collection<T>)` | 逐条执行（BATCH executor） | 支持 | 批量写入，且各记录 null 字段可能不同、需要保留 NOT_NULL 语义 |

> `FieldStrategy.NEVER` 不属于这个限制：它在元数据解析阶段就把字段从候选列表中永久剔除，三种方法使用的是同一份列集合，因此 `NEVER` 字段在 `upsertBatch`/`upsert(Collection)` 中同样会被排除，行为是一致的。

### SQL Server / Oracle / H2 的实现差异

PostgreSQL 和 MySQL 的单条动态 SQL 直接在 `VALUES (...)` 子句上用 `<trim>` 处理。Oracle 和 SQL Server 单条场景改用 `USING (SELECT ...) AS src` 子查询形式（而非 `USING (VALUES (...)) AS src(cols)`），因为后者要求列名声明和取值列表长度严格一致，无法配合 `<if>` 动态增减列；前者基于 `SELECT` 列表，可以用 `<trim>` 动态增减列，原理与 PostgreSQL/MySQL 一致。

H2 的 `MERGE INTO (cols) KEY(...) VALUES (...)` 语法没有子查询变体，单条动态 SQL 对列名和取值使用完全相同的 `<if>` 条件以保证两侧严格同步增减；考虑到 H2 仅用于测试环境（见[数据库注意事项](#数据库注意事项)），这一限制不影响生产使用。

---

## 配置项说明

在 `application.yml` 中可配置以下选项：

```yaml
mybatis-plus:
  upsert:
    enabled: true                # 是否启用，false 时完全跳过自动配置，默认 true
    db-type: mysql                # 数据库类型，可选：未配置时自动从 JDBC URL 推断
                                   # 可选值：mysql | postgresql | oracle | sqlserver | h2 | custom
    use-new-mysql-syntax: false  # 是否使用 MySQL 8.0.19+ 引入的新 upsert 语法（AS new）
                                   # 默认 false 使用向后兼容的 VALUES() 语法
```

**`db-type` 自动推断**

starter 会从 JDBC URL 自动推断数据库类型，无需手动配置。支持的 URL 前缀：`jdbc:mysql:`、`jdbc:postgresql:`、`jdbc:oracle:`、`jdbc:sqlserver:`、`jdbc:h2:`。

```yaml
# 零配置示例：自动推断为 MySQL
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db
    username: root
    password: ***

# 无需 mybatis-plus.upsert.db-type 配置
```

```yaml
# 例：TiDB 兼容 MySQL 语法，但 URL 前缀可能不匹配，需要手动指定
mybatis-plus:
  upsert:
    db-type: mysql
```

```yaml
# 例：使用自定义方言（如 ClickHouse），见下方「自定义方言」章节
mybatis-plus:
  upsert:
    db-type: custom
```

---

## 与已有自定义 SqlInjector 共存

如果项目中已有自定义 `ISqlInjector`（通常继承自 `DefaultSqlInjector`），starter 的自动注册会因 `@ConditionalOnMissingBean(ISqlInjector.class)` 而跳过，导致 `upsert` / `upsertBatch` 方法（以及 `upsert(Collection)` 依赖的内部 `upsertExecutor` statement）无法注入。

**解决方式：让已有的 SqlInjector 继承 `UpsertSqlInjector`。**

```java
// 修改前
@Bean
public ISqlInjector sqlInjector() {
    return new DefaultSqlInjector() {
        @Override
        public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
            List<AbstractMethod> methods = super.getMethodList(mapperClass, tableInfo);
            methods.add(new MyCustomMethod());
            return methods;
        }
    };
}

// 修改后：将 DefaultSqlInjector 替换为 UpsertSqlInjector
@Bean
public ISqlInjector sqlInjector(UpsertDialect upsertDialect) {
    return new UpsertSqlInjector(upsertDialect) {
        @Override
        public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
            List<AbstractMethod> methods = super.getMethodList(mapperClass, tableInfo);
            methods.add(new MyCustomMethod());  // 保留原有自定义方法
            return methods;
        }
    };
}
```

`UpsertSqlInjector` 继承自 `DefaultSqlInjector`，`super.getMethodList()` 会包含 MP 全部原生方法 + `upsert` + `upsertBatch` + 内部 `upsertExecutor`（供 `upsert(Collection)` 使用，不对外暴露为 Mapper 方法），行为完全向下兼容。

---

## 自定义方言

如果目标数据库不在内置支持列表中（如 TDengine、ClickHouse 等），可实现 `UpsertDialect` 接口并注册为 Bean。使用时需要将 `db-type` 配置为 `custom`，starter 会跳过内置方言的自动注册，使用用户自定义的 Bean。

**第一步：配置 `db-type: custom`**

```yaml
mybatis-plus:
  upsert:
    db-type: custom
```

**第二步：实现自定义方言**

```java
@Component
public class ClickHouseUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        // 返回含 MyBatis 占位符的 SQL 字符串
        // 单条参数绑定前缀为 et，例如 #{et.username}
        // ...
        return "INSERT INTO " + meta.getTableName() + " ... ";
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        // 批量 SQL，集合参数名为 list，元素变量名为 item
        // 含动态标签时需包裹 <script>（由 Injector 层自动包裹，这里只返回内层内容）
        // ...
        return "<foreach collection=\"list\" item=\"item\" separator=\",\">...</foreach>";
    }
}
```

`UpsertMeta` 提供以下字段供 SQL 拼接使用：

| 字段 | 类型 | 说明 |
|---|---|---|
| `tableName` | `String` | 数据库表名 |
| `insertColumns` | `List<String>` | INSERT 所有列名（供批量 SQL 使用，固定列集合） |
| `insertFields` | `List<String>` | 与 `insertColumns` 一一对应的 Java 字段名 |
| `conflictColumns` | `List<String>` | 冲突检测列名 |
| `updateColumns` | `List<String>` | UPDATE SET 列名（供批量 SQL 使用，固定列集合） |
| `updateFields` | `List<String>` | 与 `updateColumns` 一一对应的 Java 字段名 |
| `insertFieldMetas` | `List<FieldMeta>` | 带动态判断信息的 INSERT 字段元数据，供单条 upsert 生成 `<if>` 动态 SQL |
| `updateFieldMetas` | `List<FieldMeta>` | 带动态判断信息的 UPDATE 字段元数据，供单条 upsert 生成 `<if>` 动态 SQL |

`FieldMeta` 包含三个属性：`column`（列名）、`property`（Java 字段名）、`dynamic`（是否需要 `<if>` 判断）、`checkEmpty`（`dynamic=true` 时是否同时判断空字符串）。自定义方言若要支持单条动态 SQL，可参考内置 `DynamicSqlBuilder`（包内私有工具类，不对外暴露，可自行实现等价逻辑）按 `<trim suffixOverrides=",">`>+ `<if test="et.xxx != null">` 的模式拼接，需保证列名片段和取值片段使用完全相同的判断条件，避免列数不对齐。

---

### 多数据源场景下的自定义方言

使用 `mybatis-plus-upsert-dynamic-datasource-boot-starter` 时，自定义方言的配置方式与单数据源略有不同：

**第一步：在配置中指定 `db-type: custom` 并提供 `dialect-ref`**

```yaml
mybatis-plus:
  upsert:
    dynamic:
      enabled: true
      datasource:
        mysql:
          db-type: mysql
        clickhouse:
          db-type: custom
          dialect-ref: clickHouseUpsertDialect   # 指向 Spring Bean 名称
```

**第二步：像单数据源一样实现并注册方言 Bean**

```java
@Component("clickHouseUpsertDialect")
public class ClickHouseUpsertDialect implements UpsertDialect {
    // 实现与单数据源完全相同
    @Override
    public String buildUpsertSql(UpsertMeta meta) { ... }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) { ... }
}
```

> **注意**：
> - `dialect-ref` 仅在 `db-type: custom` 时生效，内置数据库类型会被忽略。
> - Bean 必须实现 `UpsertDialect` 接口，否则启动会抛出异常。
> - 方言 Bean 的名称（`@Component("name")` 的 value）必须与配置中的 `dialect-ref` 一致。

---

## 各数据库生成的 SQL 示例

以下示例基于 `UserEntity`（冲突键 `username`，更新 `email`、`age`、`update_time`，忽略 `create_time`）。`UserEntity` 字段均未显式标注 `@TableField`，按 MP 全局默认策略 `NOT_NULL`，因此 `email`、`age`、`update_time` 等非冲突键字段在单条示例中均为动态字段；为保持示例简洁，以下只展示 `email` 的 `<if>` 片段，其余动态字段省略号代替，结构相同。主键 `id` 和冲突键 `username` 始终原样拼接，不做动态判断。

> `upsert(Collection<T>)` 内部复用的是与"单条"完全相同的 SQL（只是注册成独立的 `upsertExecutor` statement，逐条走 BATCH executor），因此下面每个数据库的"单条"示例同样适用于 `upsert(Collection)`；"批量"示例对应的是 `upsertBatch`。

### MySQL / MariaDB

**单条（实际是 MyBatis 动态 SQL，`<trim>` 自动去除收尾逗号）：**
```xml
INSERT INTO t_user (<trim suffixOverrides=",">
  id, username,
  <if test="et.email != null">email, </if>
  <if test="et.age != null">age, </if>
  <if test="et.updateTime != null">update_time, </if>
</trim>)
VALUES (<trim suffixOverrides=",">
  #{et.id}, #{et.username},
  <if test="et.email != null">#{et.email}, </if>
  <if test="et.age != null">#{et.age}, </if>
  <if test="et.updateTime != null">#{et.updateTime}, </if>
</trim>)
ON DUPLICATE KEY UPDATE <trim suffixOverrides=",">
  <if test="et.email != null">email = #{et.email}, </if>
  <if test="et.age != null">age = #{et.age}, </if>
  <if test="et.updateTime != null">update_time = #{et.updateTime}, </if>
</trim>
```
若调用时 `email` 为 `null`，MyBatis 执行期会跳过对应的 `<if>` 块，实际生效的 SQL 等价于 `INSERT INTO t_user (id, username, age, update_time) VALUES (...) ON DUPLICATE KEY UPDATE age = ..., update_time = ...`，`email` 既不参与插入也不参与更新。

**批量（固定列集合，不做动态判断；使用 `VALUES()` 函数引用当次插入值）：**
```sql
INSERT INTO t_user (id, username, email, age, create_time, update_time)
VALUES (#{item.id}, ...), (#{item.id}, ...), ...
ON DUPLICATE KEY UPDATE
  email = VALUES(email), age = VALUES(age), update_time = VALUES(update_time)
```

---

### PostgreSQL

**单条：** 结构与 MySQL 一致，`ON DUPLICATE KEY UPDATE` 替换为 `ON CONFLICT (username) DO UPDATE SET`，UPDATE 部分直接绑定 `#{et.xxx}`（不依赖 `EXCLUDED.col`，因为该列可能因 `<if>` 未出现在 INSERT 列表中）。

```xml
INSERT INTO t_user (<trim suffixOverrides=",">...</trim>)
VALUES (<trim suffixOverrides=",">...</trim>)
ON CONFLICT (username) DO UPDATE SET <trim suffixOverrides=",">
  <if test="et.email != null">email = #{et.email}, </if>
  ...
</trim>
```

**批量（`EXCLUDED` 伪表引用当次插入行的值，固定列集合）：**
```sql
INSERT INTO t_user (...) VALUES (...), (...), ...
ON CONFLICT (username) DO UPDATE SET
  email = EXCLUDED.email, age = EXCLUDED.age, update_time = EXCLUDED.update_time
```

---

### Oracle

**单条（`src` 子查询列表、INSERT 列名、INSERT 取值三处使用完全相同的 `<if>` 条件，保证列数严格对齐）：**
```xml
MERGE INTO t_user t USING (SELECT <trim suffixOverrides=",">
  #{et.id} AS id, #{et.username} AS username,
  <if test="et.email != null">#{et.email} AS email, </if>
  ...
</trim> FROM dual) src
ON (t.username = src.username)
WHEN MATCHED THEN UPDATE SET <trim suffixOverrides=",">
  <if test="et.email != null">t.email = #{et.email}, </if>
  ...
</trim>
WHEN NOT MATCHED THEN INSERT (<trim suffixOverrides=",">
  id, username,
  <if test="et.email != null">email, </if>
  ...
</trim>) VALUES (<trim suffixOverrides=",">
  src.id, src.username,
  <if test="et.email != null">src.email, </if>
  ...
</trim>)
```

> 之所以用 `USING (SELECT ...)` 而非 `USING (SELECT ... FROM dual)` 之外的写法，是因为 Oracle MERGE 没有 `USING (VALUES (...))` 语法；`SELECT` 列表天然支持配合 `<trim>` 动态增减列。

**批量（固定列集合，逐条执行，以 `;` 分隔）：**
```sql
MERGE INTO t_user t USING (SELECT ... FROM dual) src ON (...) WHEN MATCHED ... WHEN NOT MATCHED ...;
MERGE INTO t_user t USING (SELECT ... FROM dual) src ON (...) WHEN MATCHED ... WHEN NOT MATCHED ...;
...
```

> Oracle 批量为逐条执行，与其他数据库不同，详见[数据库注意事项](#数据库注意事项)。

---

### SQL Server

**单条（改用 `USING (SELECT ...) AS src` 而非 `USING (VALUES (...)) AS src(cols)`，原理与 Oracle 一致）：**
```xml
MERGE INTO t_user AS t USING (SELECT <trim suffixOverrides=",">
  #{et.id} AS id, #{et.username} AS username,
  <if test="et.email != null">#{et.email} AS email, </if>
  ...
</trim>) AS src
ON (t.username = src.username)
WHEN MATCHED THEN UPDATE SET <trim suffixOverrides=",">
  <if test="et.email != null">t.email = #{et.email}, </if>
  ...
</trim>
WHEN NOT MATCHED THEN INSERT (<trim suffixOverrides=",">...</trim>)
  VALUES (<trim suffixOverrides=",">...</trim>);
```

> `USING (VALUES (...)) AS src(cols)` 要求列名声明和取值列表严格等长，无法配合 `<if>` 动态增减列，因此单条场景改用 `SELECT` 形式；批量场景仍使用 `VALUES` 多行写法（见下）。

**批量（固定列集合，单条 MERGE 配合多行 VALUES，效率优于逐条）：**
```sql
MERGE INTO t_user AS t
USING (VALUES (...), (...), ...) AS src(id, username, email, ...)
ON (t.username = src.username)
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT (...) VALUES (...);
```

---

### H2（测试环境）

**单条（列名和取值使用完全相同的 `<if>` 条件，因 H2 MERGE 语法没有子查询变体）：**
```xml
MERGE INTO t_user (<trim suffixOverrides=",">
  id, username,
  <if test="et.email != null">email, </if>
  ...
</trim>) KEY(username) VALUES (<trim suffixOverrides=",">
  #{et.id}, #{et.username},
  <if test="et.email != null">#{et.email}, </if>
  ...
</trim>)
```

**批量（固定列集合，逐条执行）：**
```sql
MERGE INTO t_user (id, username, email, age, create_time, update_time)
KEY(username)
VALUES (#{item.id}, #{item.username}, #{item.email}, #{item.age}, #{item.createTime}, #{item.updateTime})
```

---

## 常见问题

**Q：单条 `upsert(T)` 执行后返回值是多少？**

仅 MySQL/MariaDB 与官方 `ON DUPLICATE KEY UPDATE` 规范一致：插入时返回 1，更新时返回 2，值未变化时返回 0。PostgreSQL/SQL Server/Oracle/H2 没有这种编码，单纯返回受影响行数（插入或更新都算 1），不区分插入/更新。

---

**Q：`upsertBatch` 执行后返回值是多少？能看出哪些是插入、哪些是更新吗？**

不能。`upsertBatch` 始终只返回**一个 int**，而批量场景每次调用涉及多行——一个数字不可能拆解出"逐行插入还是更新"的明细，这是 JDBC `executeUpdate()` 的结构性限制，不是本库没实现。各数据库这个 int 的具体含义不同：

- **MySQL/MariaDB**：一条多值 SQL，`ON DUPLICATE KEY UPDATE` 每行的 affected-rows 编码是插入=1、更新=2、值未变化=0，返回值是**逐行求和**。例如返回 4，可能是 4 行插入，也可能是 2 行更新，两种情况算出来都是 4，光看这个数字没法反推到底是哪种。
- **PostgreSQL**：一条多值 SQL，`ON CONFLICT DO UPDATE` 没有上面那种编码，返回值就是单纯的"本次插入+更新的总行数"，含义更简单，但同样不能拆出插入/更新各多少行。
- **SQL Server**：一条多值 `MERGE`（`USING (VALUES ...) AS src`），语义同 PostgreSQL，单纯总行数。
- **Oracle / H2**：批量是用 `;` 拼接的多条独立 `MERGE` 语句（不是一条 SQL，见[数据库注意事项](#数据库注意事项)）。这种情况下 JDBC `executeUpdate()` 的返回值取决于驱动对"一次调用执行多条语句"的支持程度——Oracle JDBC 默认不支持这种用法，驱动层面如果做了兼容处理，目前也没有实测确认返回的是第一条语句、最后一条、还是合计。**这两个数据库下 `upsertBatch` 的返回值不建议作为业务判断依据**，只把它当"调用是否抛异常"的信号即可。

如果业务确实需要"这批里哪些是插入、哪些是更新"的明细，请用 `upsert(Collection<T>)`（对齐 MP `insert(Collection)` 语义，逐行执行）：返回的 `List<BatchResult>` 里每个 `BatchResult.getUpdateCounts()` 是逐行的真实 int 数组，MySQL 下每个元素天然就是 0/1/2 编码，可以按行解读；PostgreSQL/SQL Server/Oracle/H2 下每个元素是该行单独执行的受影响行数，同样比 `upsertBatch` 的单个合计数更可信。

---

**Q：批量 upsert 是一条 SQL 还是多条？**

- MySQL / PostgreSQL / SQL Server：一条 SQL，多行 VALUES，效率最高。
- Oracle / H2：逐条执行，多条 SQL 以 `;` 分隔。

无论哪种数据库，批量 upsert 均使用固定列集合，不做按字段值的动态判断（即[字段动态判断](#字段动态判断)只对单条 upsert 生效）。若业务需要"批量但仍要动态判断"，请改用单条 `upsert` 循环调用。

---

**Q：`@ConflictKey` 可以标注在主键上吗？**

可以。主键本身就是唯一约束，标注 `@ConflictKey` 后会以主键为冲突依据。但通常主键由数据库自动生成，建议以业务唯一键作为冲突键。

---

**Q：多个字段都标注了 `@ConflictKey`，是 OR 关系还是 AND 关系？**

是对应**联合唯一索引**，即 `(biz_code, tenant_id)` 两者组合唯一，不是任意一个唯一就触发冲突。需确保数据库存在对应的联合唯一索引。

---

**Q：项目使用了 MyBatis Plus 的逻辑删除，upsert 会不会有问题？**

`UpsertMetaParser` 基于 MP 的 `TableInfo` 解析字段，逻辑删除字段（`@TableLogic`）通常会被 MP 标记为填充字段，在 `TableInfo.getFieldList()` 中可见，因此会正常参与 INSERT 和 UPDATE。业务层需自行保证逻辑删除字段的值符合预期。

---

**Q：upsert 与 MP 的自动填充（`@TableField(fill = ...)`) 兼容吗？**

当前版本 upsert 绕过了 MP 的 `MetaObjectHandler`，自动填充**不会触发**。需要填充的字段（如 `updateTime`）请在调用前在业务代码中手动赋值。

---

**Q：如何实现"只有字段不为 null 时才更新"？**

这是默认行为，无需任何额外配置。单条 `upsert` 会按字段的 MP `FieldStrategy`（`insertStrategy`/`updateStrategy`）自动生成 `<if test="field != null">` 动态判断，行为与 MP 原生 `insert`/`updateById` 完全一致：未显式标注 `@TableField` 的字段默认遵循全局策略 `NOT_NULL`，字段为 null 时不会出现在 SQL 中，因此插入时由数据库默认值接管，更新时不会覆盖原值。详见[字段动态判断](#字段动态判断)。

---

## 数据库注意事项

### MySQL / MariaDB

- 默认使用 `VALUES()` 函数引用当次插入的列值，该语法向下兼容所有 MySQL/MariaDB 版本。
- 如使用 MySQL 8.0.19+，可在配置中设置 `mybatis-plus.upsert.use-new-mysql-syntax: true` 来启用新的 `AS new` 别名语法（MySQL 8.0.20+ 官方推荐写法）。
- MariaDB 10.x 同样支持 `ON DUPLICATE KEY UPDATE`，完全兼容。

### PostgreSQL

- `ON CONFLICT (cols) DO UPDATE` 要求括号内的列必须有对应的**唯一索引**（主键也算），否则报错 `there is no unique or exclusion constraint matching the ON CONFLICT specification`。

### Oracle

- 批量 upsert 采用逐条 MERGE，多条语句以 `;` 分隔。Oracle JDBC 驱动默认不支持多语句执行，需在 JDBC URL 中确认驱动版本兼容性，或在业务层拆分为单条循环调用。
- 若数据量大，建议业务层自行分批调用，避免单次事务过大。

### SQL Server

- MERGE 语句末尾的 `;` 是 SQL Server 语法规范要求，缺少会报语法错误。
- 批量 upsert 使用 `USING (VALUES (...),(...),...) AS src(cols)` 多行写法，需确认 SQL Server 版本 ≥ 2008。
- 部分版本的 SQL Server JDBC 驱动对多语句支持有限制，若遇到问题可在 JDBC URL 添加 `;sendStringParametersAsUnicode=false` 或升级驱动版本。

### H2

- H2 的 `MERGE INTO ... KEY(...)` 语法为 H2 私有，**不适用于生产环境**，仅用于单元测试。
- 在 `application.yml` 中配置 `mybatis-plus.upsert.db-type: h2` 或使用 H2 DataSource 时自动探测。
- H2 Mode 建议设置为 `MODE=MySQL` 以最大程度模拟 MySQL 行为（建表 DDL 可以复用）。
- 批量 upsert 同 Oracle，是 `;` 拼接的多条独立 `MERGE` 语句，`upsertBatch` 返回的 int 含义不可靠（见[常见问题](#常见问题)），测试断言请用查询结果而非返回值判断。