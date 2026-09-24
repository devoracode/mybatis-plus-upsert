# Mybatis-plus Upsert

[![CI](https://github.com/devoracode/mybatis-plus-upsert/actions/workflows/ci.yml/badge.svg)](https://github.com/devoracode/mybatis-plus-upsert/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devoracode/mybatis-plus-upsert-boot-starter)](https://central.sonatype.com/artifact/io.github.devoracode/mybatis-plus-upsert-boot-starter)

基于 MyBatis Plus 扩展 `BaseMapper`，为 Spring Boot 2.x / 3.x 项目提供开箱即用的跨数据库 **Upsert** 能力（存在则更新，不存在则插入）。

无需写 XML、无需自定义 SQL，只需在实体字段上加注解，调用 `upsert(entity)` / `upsert(Collection)` 即可。

---

## 目录

- [支持环境](#支持环境)
- [快速开始](#快速开始)
- [多数据源支持](#多数据源支持)
- [注解详解](#注解详解)
- [注解组合规则](#注解组合规则)
- [字段动态判断](#字段动态判断)
- [批量 Upsert 的实现](#批量-upsert-的实现)
- [配置项说明](#配置项说明)
- [与已有自定义 SqlInjector 共存](#与已有自定义-sqlinjector-共存)
- [自定义方言](#自定义方言)
- [各数据库生成的 SQL 示例](#各数据库生成的-sql-示例)
- [异常说明](#异常说明)
- [常见问题](#常见问题)
- [各数据库 UPSERT 对比](#各数据库-upsert-对比)
- [数据库注意事项](#数据库注意事项)

---

## 支持环境

| 项目 | 要求 |
|---|---|
| JDK | 8+（Spring Boot 2.x）/ 17+（Spring Boot 3.x） |
| Spring Boot | 2.4.5+ / 3.2.x |
| MyBatis Plus | 3.5.7+ |
| 数据库 | MySQL / MariaDB、PostgreSQL、Oracle、SQL Server、H2（内置）；其他数据库需自行实现 `UpsertDialect`，见[自定义方言](#自定义方言) |

> **数据库验证等级**：自动回归测试套件只在 **H2（MySQL 模式）** 上运行；**MySQL、PostgreSQL** 通过 `examples/` 下的示例工程做过实际运行验证（非 CI 自动化）；**Oracle 和 SQL Server 方言未在真实数据库上验证过**——SQL 已按官方语法编写并有结构级断言测试覆盖，首次接入生产前请务必在自己的环境中充分测试；**MariaDB、TiDB** 等 MySQL 协议兼容数据库复用 MySQL 方言，属协议级理论兼容，未单独验证。详见[数据库注意事项](#数据库注意事项)。

> **示例工程连接配置**：
> - 四个 `examples/` 子工程均不默认激活数据库 profile；运行时通过 `SPRING_PROFILES_ACTIVE=mysql` 或 `SPRING_PROFILES_ACTIVE=postgresql` 显式选择。动态数据源示例默认初始化两套连接池，启动前两种数据库都必须可达、通过证书校验，并提供两组账号。
> - 必须通过 `MYSQL_USERNAME` / `MYSQL_PASSWORD`、`POSTGRES_USERNAME` / `POSTGRES_PASSWORD` 注入专用最小权限账户；主机、端口和数据库名可分别通过对应的 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`POSTGRES_HOST`、`POSTGRES_PORT`、`POSTGRES_DATABASE` 覆盖。不要使用数据库超级用户，也不要把凭据提交到配置文件。
> - MySQL 默认使用 `sslMode=VERIFY_IDENTITY`，PostgreSQL 默认使用 `sslmode=verify-full`，并校验服务器证书；私有 CA 需加入 JDBC 驱动使用的信任库。
> - 仅本地无 TLS 数据库可显式设置 `MYSQL_SSL_MODE=DISABLED` 或 `POSTGRES_SSL_MODE=disable`，不得用于共享或生产环境。MySQL 8 的 `caching_sha2_password` 在无 TLS 时还要求 Connector/J 通过可信文件获得服务器 RSA 公钥；可配置 `serverRsaPublicKeyFile`（单数据源使用 `spring.datasource.hikari.data-source-properties.serverRsaPublicKeyFile`，动态数据源使用 `spring.datasource.dynamic.datasource.mysql.hikari.data-source-properties.serverRsaPublicKeyFile`），不要开启 `allowPublicKeyRetrieval`。
> - SQL 及绑定参数日志默认关闭；仅本地临时排查可追加 `--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl`，不要在共享或生产环境启用。

> **MyBatis Plus 版本说明**：最低要求 **3.5.7**。
>
> - **下限来源**：批量写入路径依赖 3.5.7 才引入的 API——`MybatisBatchUtils.execute(SqlSessionFactory, Collection, BatchMethod, int batchSize)` 以及 `MybatisUtils.getMybatisMapperProxy(Object)` / `getSqlSessionFactory(MybatisMapperProxy)`（均 `@since 3.5.7`）；3.5.6 及以下因找不到对应方法重载而无法编译。单条 `upsert(entity)` 与 SQL 注入逻辑本身兼容更早的 3.4.0+ API，但受批量路径约束，整体下限仍为 3.5.7。
> - **项目实测通过的版本（全量编译 + 测试套件）**：`3.5.7`、`3.5.9`（默认）、`3.5.11`、`3.5.13`、`3.5.17`，覆盖下限、`FieldStrategy.IGNORED` 移除前后的两侧及最新版本；测试方式为 `-Dmybatis-plus.version` 覆盖后运行项目全部测试套件（含 H2 运行时集成与自动填充行为断言）。
> - **已知的上游差异**：MyBatis-Plus 自 `3.5.11` 起移除了 `FieldStrategy.IGNORED`（由 `ALWAYS` 取代）。本库不引用该枚举值，`IGNORED` 字段的语义（不判空、始终出现在 SQL 中）由同一 default 分支覆盖，`3.5.7` 至 `3.5.17` 全系编译、运行无差异。
> - 本库自身代码对 jsqlparser 零依赖；`3.5.9+` 的 jsqlparser 拆分工件版本差异不影响本库。如使用未列出的更高版本建议自行验证。

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
public interface UserMapper extends UpsertMapper<UserEntity> {
    // UpsertMapper 已继承 BaseMapper，所有 MP 原生方法均可用
    // 额外增加：upsert(entity)、upsert(Collection)/upsert(Collection, batchSize)
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

    // 批量 upsert（与 MP BaseMapper#insert(Collection) 语义对齐）：
    // 复用上面那条单行 SQL，在 JDBC BATCH 执行器下逐条提交，
    // 每个实体各自按 NOT_NULL/NOT_EMPTY 动态判空拼列，返回 List<BatchResult>
    public List<BatchResult> saveOrUpdateBatch(List<UserEntity> users) {
        return userMapper.upsert(users);
    }

    // 同上，自定义每批大小（到达该数量即 flush 并提交一次）
    public List<BatchResult> saveOrUpdateBatch(List<UserEntity> users, int batchSize) {
        return userMapper.upsert(users, batchSize);
    }
}
```

> 批量写入只有一条路径：`upsert(Collection)`。它的实现细节（为什么不是"一条多值 SQL"、生成键何时可见、
> 部分成功语义）见[批量 Upsert 的实现](#批量-upsert-的实现)。

> 传 `null` 实体、`null`/空集合或集合内含 `null` 时的行为在[异常说明](#异常说明)中逐条列出：实体为 `null`（含集合内的 `null` 元素）会在 SQL 绑定之前给出明确的 `UpsertException`，而不是让数据库报一个看不出根因的约束错误；集合本身为 `null` 或为空则视为没有行要写，直接返回空列表。

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
          url: jdbc:mysql://${MYSQL_MASTER_HOST:localhost}:${MYSQL_MASTER_PORT:3306}/${MYSQL_MASTER_DATABASE:db_master}?sslMode=${MYSQL_SSL_MODE:VERIFY_IDENTITY}
          username: ${MYSQL_MASTER_USERNAME}
          password: ${MYSQL_MASTER_PASSWORD}
        mysql-slave1:
          url: jdbc:mysql://${MYSQL_SLAVE1_HOST:localhost}:${MYSQL_SLAVE1_PORT:3307}/${MYSQL_SLAVE1_DATABASE:db_slave}?sslMode=${MYSQL_SSL_MODE:VERIFY_IDENTITY}
          username: ${MYSQL_SLAVE1_USERNAME}
          password: ${MYSQL_SLAVE1_PASSWORD}

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
          url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:db_mysql}?sslMode=${MYSQL_SSL_MODE:VERIFY_IDENTITY}
          username: ${MYSQL_USERNAME}
          password: ${MYSQL_PASSWORD}
        postgresql:
          url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DATABASE:db_pg}?sslmode=${POSTGRES_SSL_MODE:verify-full}
          username: ${POSTGRES_USERNAME}
          password: ${POSTGRES_PASSWORD}

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
          url: jdbc:mysql://${MYSQL8_HOST:localhost}:${MYSQL8_PORT:3306}/${MYSQL8_DATABASE:db_mysql8}?sslMode=${MYSQL8_SSL_MODE:VERIFY_IDENTITY}
          username: ${MYSQL8_USERNAME}
          password: ${MYSQL8_PASSWORD}
        mysql5:
          url: jdbc:mysql://${MYSQL5_HOST:localhost}:${MYSQL5_PORT:3307}/${MYSQL5_DATABASE:db_mysql5}?sslMode=${MYSQL5_SSL_MODE:VERIFY_IDENTITY}
          username: ${MYSQL5_USERNAME}
          password: ${MYSQL5_PASSWORD}

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
          url: jdbc:mysql://${TIDB_HOST:localhost}:${TIDB_PORT:4000}/${TIDB_DATABASE:db_tidb}?sslMode=${TIDB_SSL_MODE:VERIFY_IDENTITY}   # TiDB 使用 MySQL 协议，自动推断为 mysql
          username: ${TIDB_USERNAME}
          password: ${TIDB_PASSWORD}
        clickhouse:
          url: jdbc:clickhouse://${CLICKHOUSE_HOST:localhost}:${CLICKHOUSE_PORT:8123}/${CLICKHOUSE_DATABASE:db_ch}

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
    public void upsertCollectionToMysql(List<User> users) {
        userMapper.upsert(users);
    }
}
```

### 工作原理

1. 启动时读取 `spring.datasource.dynamic.datasource` 配置，遍历所有数据源
2. 对每个数据源：优先使用 `mybatis-plus.upsert.dynamic.datasource.{name}.db-type` 显式配置；若未配置，则从 JDBC URL 自动推断（支持 `jdbc:mysql:`、`jdbc:mariadb:`、`jdbc:postgresql:`、`jdbc:oracle:`、`jdbc:sqlserver:`、`jdbc:h2:`）
3. 根据推断结果创建对应的 `UpsertDialect` 实例并注册到 `DynamicUpsertDialect`
4. 运行时通过 `DynamicDataSourceContextHolder.peek()` 获取当前数据源名称，路由到对应的方言生成 SQL

### SQL 缓存

路由方言内部按 **"<数据源解析出的方言实例> + 实体"** 缓存已构建的 `SqlSource`，
在条目未被淘汰前每个数据源的 SQL 只生成一次；缓存最多保留约 64 条，超出后由 Caffeine 淘汰旧条目。
缓存挂在注入出的 `upsert` statement 自己身上，`upsert(Collection)` 复用的正是这条语句，
单条与批量路径共享同一份缓存。

缓存键落在**方言实例**而不是方言类名上，原因是类名不足以区分两个数据源：

- 两个数据源可以引用同一个自定义方言类的**两个不同实例**（各自带不同的 schema、表前缀等配置）；
- 两个不同的方言类可能有**相同的简单类名**（`getSimpleName()` 不含包名）。

这两种情况下若按类名缓存，数据源 B 会直接命中数据源 A 生成的 `ON DUPLICATE KEY` / `ON CONFLICT` / `MERGE`
语句，产生跨库污染。方言实例若重写了 `equals/hashCode` 声明两个实例等价，则共享同一份缓存 SQL。

> 自定义 `DynamicUpsertDialect` 实现应为每个数据源返回**稳定的方言实例**（如单例 Bean）。
> 如果每次调用 `getCurrentDialect()` 都新建实例，缓存命中率会下降；缓存达到 64 条后由 Caffeine 淘汰旧条目并继续接收新条目。

### 多数据源配置项说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `mybatis-plus.upsert.dynamic.enabled` | `true` | 是否启用动态数据源支持 |
| `mybatis-plus.upsert.dynamic.use-new-mysql-syntax` | `false` | **全局**默认：MySQL 数据源是否使用新语法（AS new），可被单个数据源配置覆盖 |
| `mybatis-plus.upsert.dynamic.fill-strategy` | `insert_update` | 动态 SQL 绑定前是否调用 `MetaObjectHandler` 预填充，可选 `none` / `insert` / `insert_update`，对所有数据源生效 |
| `mybatis-plus.upsert.dynamic.datasource.{dsName}.db-type` | 自动推断 | 该数据源的数据库类型（mysql/postgresql/oracle/sqlserver/h2/custom）。**可选**，未配置时从 JDBC URL 自动推断 |
| `mybatis-plus.upsert.dynamic.datasource.{dsName}.use-new-mysql-syntax` | 未声明则继承全局配置 | 单个数据源的 MySQL 语法开关；仅当显式写出 `true`/`false` 时覆盖全局配置，只配置了 `db-type` 等其他项不会影响该开关 |
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

> 上表说明的是"哪些列出现在 SQL 结构里"。至于这些列在 upsert 中是否需要按值动态判断（null 时整列消失），见下一节[字段动态判断](#字段动态判断)，这是两个独立的维度。

---

## 字段动态判断

MyBatis Plus 的原生 `insert` / `updateById` 方法会按字段的 `FieldStrategy`（`insertStrategy` / `updateStrategy`）动态决定该字段是否出现在 SQL 中。本 starter 的 `upsert` 完全遵循这一行为，无需任何额外配置：单条按当前实体的字段值裁剪列；`upsert(Collection)` 复用同一条语句逐条执行，因此集合里每一行都拥有自己的动态列集合。

### 行为对照

| FieldStrategy | 行为 |
|---|---|
| `NOT_NULL`（**全局默认**） | 字段为 null 时不出现在 SQL 中 |
| `NOT_EMPTY` | 字符串字段为 null 或空字符串时不出现在 SQL 中；非字符串字段退化为 `NOT_NULL` 判断 |
| `IGNORED` | 忽略判断，始终出现在 SQL 中（不代表"忽略该字段"，而是"忽略 null/empty 判断"） |
| `NEVER` | 该字段永远不出现在 SQL 中，无论值是什么；单条与 `upsert(Collection)` 共用同一份元数据列集合，两条路径都始终排除 |
| `DEFAULT` | 注解上代表"跟随全局配置"；全局配置上代表 `NOT_NULL`。MP 在解析阶段已将其转换为实际生效的策略，本 starter 读到的是转换后的值，不会是 `DEFAULT` 本身 |

由于 MP 全局默认策略是 `NOT_NULL`，**未显式标注 `@TableField` 的字段默认就是动态字段**：

```java
@TableName("t_user")
public class UserEntity {

    @TableId
    private Long id;            // 主键不受 FieldStrategy 影响；IdType.AUTO 时不出现在 INSERT 中（见下文"主键 IdType 行为"）

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

### 主键 IdType 行为

| `@TableId` 类型 | INSERT 列表 | 作为 `@ConflictKey` |
|---|---|---|
| `INPUT` / `ASSIGN_ID` / `ASSIGN_UUID`（默认） | 主键列正常参与 INSERT | 允许 |
| `AUTO`（数据库自增） | 主键列**不参与** INSERT（与 MP 原生 insert 行为一致，避免向 PostgreSQL serial 列显式插 NULL） | **禁止**，启动时解析即抛出异常——自增键的值在插入前不存在，无法作为冲突判断依据 |

`@ConflictKey` 应放在业务唯一键列（如 `username`、`order_no`）上，而不是主键上。

> `INPUT` 主键再配 `@KeySequence` 时，主键列仍参与 INSERT，只是值改由 MP 的序列取号语句在语句执行前填入，见[主键回填](#主键回填)。

> 另一特殊组合：`insertStrategy = NEVER` 但参与更新（未排除出 UPDATE）的字段，其 UPDATE SET 赋值会回退为 `#{参数.字段}` 参数引用而非行引用（`new.col` / `EXCLUDED.col` / `src.col` / `VALUES(col)`），因为行引用指向插入行中不存在的列。

### 主键回填

主键由数据库生成时，本库按 MyBatis-Plus 原生 `insert` 的同一机制配置 `KeyGenerator`，不自定义 JDBC 取键逻辑、也不自拼序列 SQL：

| 主键策略 | 取键机制 |
|---|---|
| `IdType.AUTO` | `Jdbc3KeyGenerator`，`keyProperty`/`keyColumn` 取 `@TableId` 的属性名与列名 |
| `IdType.INPUT` + `@KeySequence` | 复用 `TableInfoHelper.genKeyGenerator`：由 MP 注册 `<语句>!selectKey` 取号语句，取号 SQL 来自容器里注册的 `IKeyGenerator` Bean |
| `ASSIGN_ID` / `ASSIGN_UUID` / 无 `@TableId` 的实体 | 不配置 `KeyGenerator`，给什么写什么；实体只有 `@ConflictKey` 而没有主键时也不会因此报错 |

`@KeySequence` 路径与 MP 原生 `insert` 完全同构：取号与写回都由 MP 生成的 `SelectKeyGenerator` 完成——Upsert 语句的参数就是实体本身（不包命名参数映射），取到的号直接写回实体的主键属性，本库不额外包装任何一层。取号方仍是 MP，不引入第二套主键协议。

选择规则与各数据库方言无关，完全跟随 MP 的主键策略语义，因此 Oracle / SQL Server / PostgreSQL 上以 `@KeySequence` + `IdType.INPUT` 使用序列主键的既有写法照常工作；`IdType.AUTO` 只在数据库本身提供自增/标识列（MySQL `AUTO_INCREMENT`、PostgreSQL `serial`/`IDENTITY`、Oracle 12c+ identity column、SQL Server `IDENTITY`）时才应使用，本库不会为任何方言额外猜测取键方式。

> **序列主键的前提**：MP 只在容器里存在 `IKeyGenerator` Bean 时才会读取 `@KeySequence`（多个 Bean 时按 `dbType()` 匹配，单个直接使用）。没有该 Bean 时，`@KeySequence` 会被 MP 忽略、主键按普通 `INPUT` 处理——本库不另造报错或补号协议，行为与 MP 原生 `insert` 完全一致。MP 的 `com.baomidou.mybatisplus.extension.incrementer` 包下已提供 `PostgreKeyGenerator`、`OracleKeyGenerator`、`H2KeyGenerator` 等实现，注册为 Bean 即可。

两条执行路径都会回填，且机制相同——因为批量复用的就是单行语句：

| 路径 | SQL 形态 | 生成主键回填 |
|---|---|---|
| `upsert(entity)` | 单行 | **回填**，语句执行后即可读到 |
| `upsert(collection)` / `upsert(collection, batchSize)` | BATCH 执行器逐条提交同一句单行 SQL | **逐条回填**：时机在批次 `flushStatements`，方法正常返回后集合中每个实体的主键都已就位 |

**回填语义的三条边界**：

1. 回填值是数据库实际生成/返回的键，不是内存里预测出来的值，本库不做任何"预判下一个 ID"的推断。
2. `upsert` 不是纯 `insert`，所以"回填到了值" ≠ "插入了一条新记录"。冲突命中走 UPDATE 分支时不会插入新行：AUTO 路径下 `getGeneratedKeys()` 返回什么取决于驱动（MySQL 驱动通常返回既有主键）；序列路径下取号发生在语句执行之前，那个号已经被消耗掉（序列号不随事务回滚），但落库行的主键仍是原值，实体上却会出现这个新号。依赖主键做后续逻辑前，先确认它代表的是插入还是更新。
3. 调用方预设的主键值不保证保留：`IdType.AUTO` 的主键列本来就不进 INSERT 列表，预设值不会写入数据库，执行后被生成值覆盖；序列主键同理，MP 的 `SelectKeyGenerator` 不判断主键是否已有值，取到的号直接盖上去。需要沿用给定主键请用 `INPUT` 策略且不配 `@KeySequence`。

> `upsert(Collection)` 的分块 flush 与部分成功语义、以及批量路径为何不做"一条多值 SQL"，见[批量 Upsert 的实现](#批量-upsert-的实现)。

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

### SQL Server / Oracle / H2 的实现差异

PostgreSQL 和 MySQL 的动态 SQL 直接在 `VALUES (...)` 子句上用 `<trim>` 处理。Oracle 和 SQL Server 改用 `USING (SELECT ...) AS src` 子查询形式（而非 `USING (VALUES (...)) AS src(cols)`），因为后者要求列名声明和取值列表长度严格一致，无法配合 `<if>` 动态增减列；前者基于 `SELECT` 列表，可以用 `<trim>` 动态增减列，原理与 PostgreSQL/MySQL 一致。

H2 的 `MERGE INTO (cols) KEY(...) VALUES (...)` 语法没有子查询变体，对列名和取值使用完全相同的 `<if>` 条件以保证两侧严格同步增减；考虑到 H2 仅用于测试环境（见[数据库注意事项](#数据库注意事项)），这一限制不影响生产使用。

---

## 批量 Upsert 的实现

本节说明 `upsert(Collection)` 到底做了什么——它是本项目**唯一**的批量写入路径。

### 伪代码

`UpsertMapper` 里两个批量重载的实际实现（去掉注释后的完整逻辑）：

```java
// UpsertMapper.java —— default 方法，无需注入 SQL，也不需要实现类
default List<BatchResult> upsert(Collection<T> entityList) {
    return upsert(entityList, Constants.DEFAULT_BATCH_SIZE);   // MP 定义的默认批次大小
}

default List<BatchResult> upsert(Collection<T> entityList, int batchSize) {
    if (entityList == null || entityList.isEmpty()) {
        return Collections.emptyList();                          // 空输入 = 不执行任何 SQL
    }
    // 1) 从当前 Mapper 代理取出 SqlSessionFactory 与 Mapper 接口 Class
    MybatisMapperProxy<?> proxy = MybatisUtils.getMybatisMapperProxy(this);
    SqlSessionFactory sqlSessionFactory = MybatisUtils.getSqlSessionFactory(proxy);

    // 2) 交给 MyBatis-Plus 的批量工具：它自己开 ExecutorType.BATCH 的 SqlSession
    MybatisBatch.Method<T> method = new MybatisBatch.Method<>(proxy.getMapperInterface());
    // 3) 每条实体的参数转换就是它本身：直接作为单条 upsert 语句的入参
    return MybatisBatchUtils.execute(sqlSessionFactory, entityList,
            method.get("upsert", entity -> entity), batchSize);
}
```

要点逐条对应：

1. **不拼多行 SQL**。这里没有任何"把 N 条记录合并成一条 `VALUES (...), (...)`"的代码；批量执行器复用的是 `upsert(entity)` 那一条单行语句（同一个注入的 `upsert` statement）。
2. **执行的是 MyBatis-Plus 的机制，不是自造的一套**。`MybatisBatch` / `MybatisBatchUtils` 与 MP 原生 `BaseMapper#insert(Collection)` 用的是同一组 API，因此会话、flush、返回值的语义与 MP 的批量 insert 一致，本库不引入第二种批量协议。
3. **参数就是实体本身**。语句与 MP 原生 `insert(T entity)` 以同一形态绑定参数：`#{xxx}` 直接从实体属性取值，`Jdbc3KeyGenerator` 和 `SelectKeyGenerator` 都走"裸参数对象"分支，把生成主键写回实体本身，不需要构造任何参数映射。

### 与 MP 原生批量 insert 的一致性

| 维度 | MP `insert(Collection)` | 本库 `upsert(Collection)` |
|---|---|---|
| 执行器 | `ExecutorType.BATCH`，独立 SqlSession | 同上（由 `MybatisBatch` 负责） |
| SQL 条数 | 1 条单行语句，重复 `addBatch` | 同上（语句就是注入的 `upsert`，与单条路径共用） |
| 分块 | 每 `batchSize` 条 flush 并提交一次 | 同上 |
| 动态列 | 逐条按 `FieldStrategy` 判空 | 逐条按 `FieldStrategy` 判空（同一条 `<if>` 模板） |
| 生成主键回填 | `flushStatements` 时写回实体 | 同上，`@KeySequence` 走 MP 的 selectKey |
| 返回值 | `List<BatchResult>` | `List<BatchResult>` |
| 冲突时行为 | 主键/唯一键冲突直接报错 | 走方言的 UPDATE 分支 |

差异只在最后一行：MP 的 insert 冲突即失败，本库把同一行语句换成 upsert 语义。批量这条链路上的其余机制都是 MP 自己的。

### 为什么不做"一条多值 SQL"的批量

把 N 条记录拼成一条多行 `VALUES (...), (...)` 语句看起来更快，但这个方案有三个无法自洽的问题：

- **逐行动态列做不到**。多行 `VALUES` 要求每行列数严格一致，而 `NOT_NULL`/`NOT_EMPTY` 是按运行时值判断的：第一条记录 email 非 null、第二条为 null 时，没有一条合法 SQL 能同时表达两行。结果是批量路径只能退化成固定列集合，`NOT_NULL` 字段为 null 时不会像单条那样被跳过，而是以 NULL 覆盖库中原值——同一个实体走单条和走批量行为不同，是语义陷阱。
- **生成主键回填不可靠**。多行语句的 generated keys 与数据行的对应关系受数据库和驱动实现影响：MySQL 下冲突更新行返回的键数量不固定，PostgreSQL 的 `ON CONFLICT DO UPDATE` 在更新分支上根本不产生 `RETURNING` 行；序列更直接——一条 `SELECT NEXT VALUE FOR seq` 只能拿到一个号，无法逐行分配。强行配置取键只会让键数校验抛异常。
- **返回值无法解读**。批量语句只返回一个 int（各数据库含义还不一样），既拆不出逐行插入/更新明细，也和逐条路径的 `List<BatchResult>` 形成两套契约。

逐条 BATCH 执行放弃的只是"一次网络往返"这一项吞吐量优势，换来的是与 MP 一致的行为模型：**批量与单条共用同一条语句、同一套动态判断、同一种主键回填**。与其维护两条语义会分叉的路径，不如只留一条。

> 确实需要"一条 SQL 写完 N 行"的极致吞吐时，那是 MP `insertBatchSomeColumn` 那一类方案的适用场景，需要自己接受固定列集合；本库不提供这类批量 upsert。

### 生成主键的可见时机

`IdType.AUTO` 与 `@KeySequence` 主键都按 MP 原生批量 insert 的同一机制回填：

- 生成键在批次 `flushStatements` 时写回实体，**不是**每条 `execute` 返回时立即可见。方法正常返回后，集合中每个实体的主键都已就位。
- 序列路径的取号发生在语句执行之前，因此**调用方预置的主键值会被序列号覆盖**——MP 的 `SelectKeyGenerator` 不判断主键是否已有值，单条与批量都一样。需要自己决定主键值请用 `IdType.INPUT` 且不配 `@KeySequence`。
- `upsert` 不是纯 insert：命中已有行时执行 UPDATE，不产生新行。AUTO 路径下驱动在 UPDATE 分支返回什么并不统一；序列路径下那个号照样被消耗（序列号不随事务回滚），实体上会出现新号而库里那行的主键仍是原值。**不要把"主键有值"当作"这一行是新插入的"的判断依据**，需要区分插入与更新请看 `BatchResult` 里的逐行行数或事后回查。

### 部分成功与可见性

- `MybatisBatch` 按 `batchSize` **分块 flush 并提交**：某块失败时前面各块可能已经落库。本方法不预先扫描集合，元素级问题（如 `null` 元素）要轮到它排队执行时才暴露，所以超出首个批次的失败发生时前面的批次可能已经提交。请把整批放在同一事务里，异常时按整批失败处理，不要假定前 N 条一定成功。
- 本库不做"已回填主键"与"实际落库行"的对账——已写进实体但随事务回滚的主键不会自动清空。
- 批量执行使用独立 SqlSession，与调用方 SqlSession 的一级缓存不互通：同一事务内紧接着用 Mapper 查询，可能读不到刚写入的数据。

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
    fill-strategy: insert_update  # 动态 SQL 绑定前是否调用 MetaObjectHandler 预填充
                                   # 可选值：none | insert | insert_update（默认）
                                   # 仅在实体有 @TableField(fill = ...) 字段时有意义，详见[常见问题](#常见问题)
```

**`db-type` 自动推断**

starter 会从 JDBC URL 自动推断数据库类型，无需手动配置。可识别的 URL 段：`jdbc:mysql:`、`jdbc:mariadb:`、`jdbc:postgresql:`、`jdbc:oracle:`、`jdbc:sqlserver:`、`jdbc:h2:`。

```yaml
# 零配置示例：自动推断为 MySQL
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:db}?sslMode=${MYSQL_SSL_MODE:VERIFY_IDENTITY}
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}

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

如果项目中已有自定义 `ISqlInjector`（通常继承自 `DefaultSqlInjector`），starter 的自动注册会因 `@ConditionalOnMissingBean(ISqlInjector.class)` 而跳过，导致 `upsert` 方法（`upsert(Collection)` 同样依赖它）无法注入。

**解决方式：让已有的 SqlInjector 继承 `UpsertSqlInjector`。**

```java
// 修改前（示意：项目里已有的自定义注入器）
@Bean
public ISqlInjector sqlInjector() {
    return new DefaultSqlInjector() {
        @Override
        public List<AbstractMethod> getMethodList(Configuration configuration,
                                                  Class<?> mapperClass, TableInfo tableInfo) {
            List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
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
        public List<AbstractMethod> getMethodList(Configuration configuration,
                                                  Class<?> mapperClass, TableInfo tableInfo) {
            List<AbstractMethod> methods = super.getMethodList(configuration, mapperClass, tableInfo);
            methods.add(new MyCustomMethod());  // 保留原有自定义方法
            return methods;
        }
    };
}
```

> 重写 `getMethodList` 时请使用带 `Configuration` 的三参数版本（MP 3.5.6+ 签名）。两参数版本 `getMethodList(Class, TableInfo)` 虽仍被 MP 兼容调用但已标记 `@Deprecated`，且只在返回非空列表时生效，容易踩坑。

`UpsertSqlInjector` 继承自 `DefaultSqlInjector`，`super.getMethodList()` 会包含 MP 全部原生方法 + `upsert`（`upsert(Collection)` 复用的就是这条语句，不另外注入内部 statement），行为完全向下兼容。

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
        // 参数就是实体本身，例如 #{username}
        // 含动态标签时需包裹 <script>（由 Injector 层自动包裹，这里只返回内层内容）
        // ...
        return "INSERT INTO " + meta.getTableName() + " ... ";
    }
}
```

> `UpsertDialect` 接口只有一个必须实现的方法 `buildUpsertSql(UpsertMeta)`。`upsert(Collection)` 复用的正是本方法生成的那条单行语句，见[批量 Upsert 的实现](#批量-upsert-的实现)。

`UpsertMeta` 提供以下字段供 SQL 拼接使用：

| 字段 | 类型 | 说明 |
|---|---|---|
| `tableName` | `String` | 数据库表名 |
| `insertColumns` | `List<String>` | INSERT 全部候选列名（不做动态判断的固定列集合，`insertStrategy = NEVER` 的字段已剔除） |
| `insertFields` | `List<String>` | 与 `insertColumns` 一一对应的 Java 字段名 |
| `conflictColumns` | `List<String>` | 冲突检测列名 |
| `updateColumns` | `List<String>` | UPDATE SET 全部候选列名（固定列集合） |
| `updateFields` | `List<String>` | 与 `updateColumns` 一一对应的 Java 字段名 |
| `insertFieldMetas` | `List<FieldMeta>` | 带动态判断信息的 INSERT 字段元数据，upsert 的 `<if>` 动态 SQL 由它生成（单条与 `upsert(Collection)` 的每一行共用） |
| `updateFieldMetas` | `List<FieldMeta>` | 带动态判断信息的 UPDATE 字段元数据，UPDATE SET 的 `<if>` 动态 SQL 由它生成（单条与 `upsert(Collection)` 的每一行共用） |
| `fieldToColumnMap` | `Map<String, String>` | Java 字段名到列名的映射 |
| `entityClass` | `Class<?>` | 元数据解析自的实体类，参与 SQL 缓存键 |

`FieldMeta` 包含五个属性：`column`（列名）、`property`（Java 字段名）、`dynamic`（是否需要 `<if>` 判断）、`checkEmpty`（`dynamic=true` 时是否同时判断空字符串）、`paramRef`（UPDATE SET 赋值是否回退为 `#{字段}` 参数引用而非行引用——仅出现在"参与更新但被排除出 INSERT"的字段上，如 `insertStrategy = NEVER` 的可更新字段）。自定义方言若要支持这种按值裁剪列的动态 SQL，可参考内置 `DynamicSqlBuilder`（包内私有工具类，不对外暴露，可自行实现等价逻辑）按 `<trim suffixOverrides=",">`>+ `<if test="xxx != null">` 的模式拼接，需保证列名片段和取值片段使用完全相同的判断条件，避免列数不对齐。

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
    // 实现与单数据源完全相同：只有 buildUpsertSql 一个方法
    @Override
    public String buildUpsertSql(UpsertMeta meta) { ... }
}
```

> **注意**：
> - `dialect-ref` 仅在 `db-type: custom` 时生效，内置数据库类型会被忽略。
> - Bean 必须实现 `UpsertDialect` 接口，否则启动会抛出异常。
> - 方言 Bean 的名称（`@Component("name")` 的 value）必须与配置中的 `dialect-ref` 一致。

---

## 各数据库生成的 SQL 示例

以下示例基于 `UserEntity`（冲突键 `username`，更新 `email`、`age`、`update_time`，忽略 `create_time`）。`UserEntity` 字段均未显式标注 `@TableField`，按 MP 全局默认策略 `NOT_NULL`，因此 `email`、`age`、`update_time` 等非冲突键字段在下面每个数据库的单行语句中均为动态字段；为保持示例简洁，以下只展示 `email` 的 `<if>` 片段，其余动态字段省略号代替，结构相同。主键 `id` 和冲突键 `username` 始终原样拼接，不做动态判断。

> 本节只列单行语句的形态：`upsert(Collection<T>)` 复用的正是下面每个数据库的这条 SQL（同一条注入的 `upsert` statement，逐条走 BATCH executor），因此这些示例同时就是批量写入实际执行的 SQL。为什么批量不拼一条多行 `VALUES` SQL，见[批量 Upsert 的实现](#批量-upsert-的实现)。

### MySQL / MariaDB

**单行语句（实际是 MyBatis 动态 SQL，`<trim>` 自动去除收尾逗号）：** UPDATE 部分默认用 `VALUES(col)` 引用当次插入值（开启 `use-new-mysql-syntax: true` 时改用 `new.col`）。
```xml
INSERT INTO t_user (<trim suffixOverrides=",">
  id, username,
  <if test="email != null">email, </if>
  <if test="age != null">age, </if>
  <if test="updateTime != null">update_time, </if>
</trim>)
VALUES (<trim suffixOverrides=",">
  #{id}, #{username},
  <if test="email != null">#{email}, </if>
  <if test="age != null">#{age}, </if>
  <if test="updateTime != null">#{updateTime}, </if>
</trim>)
ON DUPLICATE KEY UPDATE <trim suffixOverrides=",">
  <if test="email != null">email = VALUES(email), </if>
  <if test="age != null">age = VALUES(age), </if>
  <if test="updateTime != null">update_time = VALUES(update_time), </if>
</trim>
```
若调用时 `email` 为 `null`，MyBatis 执行期会跳过对应的 `<if>` 块，实际生效的 SQL 等价于 `INSERT INTO t_user (id, username, age, update_time) VALUES (...) ON DUPLICATE KEY UPDATE age = ..., update_time = ...`，`email` 既不参与插入也不参与更新。批量写入时这条语句被逐条 `addBatch`，每行各自渲染自己的 `<if>` 结果。

---

### PostgreSQL

**单行语句：** 结构与 MySQL 一致，`ON DUPLICATE KEY UPDATE` 替换为 `ON CONFLICT (username) DO UPDATE SET`，UPDATE 部分引用 `EXCLUDED.col`（当次插入行的值）。由于 UPDATE 与 INSERT 取值列表使用完全相同的 `<if>` 判空条件，凡被引用的 `EXCLUDED.col` 必然同时出现在当次插入行中，因此可安全引用。

```xml
INSERT INTO t_user (<trim suffixOverrides=",">...</trim>)
VALUES (<trim suffixOverrides=",">...</trim>)
ON CONFLICT (username) DO UPDATE SET <trim suffixOverrides=",">
  <if test="email != null">email = EXCLUDED.email, </if>
  ...
</trim>
```

> 批量写入也是这条语句逐条 `addBatch`，每行各自渲染自己的 `<if>` 结果。

---

### Oracle

**单行语句（`src` 子查询列表、INSERT 列名、INSERT 取值三处使用完全相同的 `<if>` 条件，保证列数严格对齐）：**
```xml
MERGE INTO t_user t USING (SELECT <trim suffixOverrides=",">
  #{id} AS id, #{username} AS username,
  <if test="email != null">#{email} AS email, </if>
  ...
</trim> FROM dual) src
ON (t.username = src.username)
WHEN MATCHED THEN UPDATE SET <trim suffixOverrides=",">
  <if test="email != null">email = src.email, </if>
  ...
</trim>
WHEN NOT MATCHED THEN INSERT (<trim suffixOverrides=",">
  id, username,
  <if test="email != null">email, </if>
  ...
</trim>) VALUES (<trim suffixOverrides=",">
  src.id, src.username,
  <if test="email != null">src.email, </if>
  ...
</trim>)
```

> Oracle MERGE 没有 `USING (VALUES (...)) AS src(cols)` 语法，源子查询写成 `SELECT ... FROM dual`；`SELECT` 列表天然支持用 `<trim>` 动态增减列，这是它能配合 `FieldStrategy` 判空裁剪的原因。

批量写入在 Oracle 下同样是这条 MERGE 逐条 `addBatch`，不需要把多行拼进一个 `UNION ALL` 源子查询，也不依赖 Oracle JDBC 的多语句支持。

---

### SQL Server

**单行语句（改用 `USING (SELECT ...) AS src` 而非 `USING (VALUES (...)) AS src(cols)`，原理与 Oracle 一致）：**
```xml
MERGE INTO t_user AS t USING (SELECT <trim suffixOverrides=",">
  #{id} AS id, #{username} AS username,
  <if test="email != null">#{email} AS email, </if>
  ...
</trim>) AS src
ON (t.username = src.username)
WHEN MATCHED THEN UPDATE SET <trim suffixOverrides=",">
  <if test="email != null">email = src.email, </if>
  ...
</trim>
WHEN NOT MATCHED THEN INSERT (<trim suffixOverrides=",">...</trim>)
  VALUES (<trim suffixOverrides=",">...</trim>);
```

> `USING (VALUES (...)) AS src(cols)` 要求列名声明和取值列表严格等长，无法配合 `<if>` 动态增减列，因此本库统一使用 `SELECT` 形式；批量写入也是这条语句逐条执行。

---

### H2（测试环境）

**单行语句（列名和取值使用完全相同的 `<if>` 条件，因 H2 MERGE 语法没有子查询变体）：**
```xml
MERGE INTO t_user (<trim suffixOverrides=",">
  id, username,
  <if test="email != null">email, </if>
  ...
</trim>) KEY(username) VALUES (<trim suffixOverrides=",">
  #{id}, #{username},
  <if test="email != null">#{email}, </if>
  ...
</trim>)
```

> H2 的 `MERGE ... KEY(...)` 没有子查询变体，动态列只能靠"列名侧与取值侧使用完全相同的 `<if>` 条件"来保证两侧同步增减。批量写入同样是这条语句逐条执行。

---

## 异常说明

本库抛出的异常都继承 `UpsertException`，按发生的时机分两类。

### 启动期：实体元数据校验（`UpsertMetaException`）

注入阶段解析实体时抛出，属于配置错误，应用启动即失败而不是等到第一次调用才暴露：

- `@ConflictKey` 标在 `IdType.AUTO` 主键上——自增键在插入前没有值，无法作为冲突判断依据；
- `@ConflictKey` 字段声明了 `insertStrategy = NEVER`——冲突键必须参与 INSERT，否则 UPDATE 场景会退化成 INSERT；
- 实体没有任何可更新列（只有冲突键，或其余字段全被 `@IgnoreOnUpdate` / `updateStrategy = NEVER` 排除）——必然产生空 `UPDATE SET`，启动期快速失败，而不是留到运行期报 SQL 语法错误。

实体**没有** `@ConflictKey` 时不属于异常：本库直接跳过该 Mapper 的 Upsert 方法注入，不会拖垮启动，普通 CRUD 照常可用。

### 调用期：参数形态校验（`UpsertException`）

参数在进入 SQL 绑定之前就被检查完，不会把问题留给数据库：

| 调用 | 行为 |
|---|---|
| `upsert(entity)` 传入 `null` 实体 | 抛异常 `Upsert entity must not be null` |
| `upsert(Collection)` 集合内含 `null` | 抛异常 `Upsert entity must not be null`：与 MyBatis-Plus 的 `BaseMapper#insert(Collection)` 一致，本库不预扫描集合，该元素轮到排队执行时被单行语句的守卫拒绝 |
| `upsert((Collection) null)` / `upsert(空集合)` | **不抛异常**：没有行要写，返回空的 `List<BatchResult>`，不产生任何语句（对齐 MP `Db#saveBatch` 的 `isEmpty` 短路） |
| `upsert(Collection, batchSize)` 传入 `batchSize <= 0` | 抛异常，消息含 `batchSize`：这一项由 MyBatis-Plus 在进入批次之前把关，本库不重复实现同名校验，因此异常类型是 MP 自己的而不是 `UpsertException` |

> **为什么 `null` 实体必须显式拒绝**：MyBatis 不会拦下它，而是把所有列绑成 `NULL` 照常执行——冲突键列有非空约束时抛出的是看不出根因的数据库约束错误，冲突键列可空时则直接写入一条全空记录。两种结果都比一条明确的异常难排查。

> 参数守卫只判断形态，不触碰主键；各条路径在什么情况下回填生成主键见[主键回填](#主键回填)。

---

## 常见问题

**Q：单条 `upsert(T)` 执行后返回值是多少？**

仅 MySQL/MariaDB 与官方 `ON DUPLICATE KEY UPDATE` 规范一致：插入时返回 1，更新时返回 2，值未变化时返回 0。PostgreSQL/SQL Server/Oracle/H2 没有这种编码，单纯返回受影响行数（插入或更新都算 1），不区分插入/更新。

---

**Q：批量 `upsert(Collection)` 执行后返回值是什么？能看出哪些是插入、哪些是更新吗？**

返回 `List<BatchResult>`（MyBatis 3.5.x 起提供），每个元素对应一次批次 flush，元素内的 `getUpdateCounts()` 是该批次**逐行**的受影响行数，顺序与提交顺序一致，因此每一行的结果都能单独解读：

- **MySQL/MariaDB**：`ON DUPLICATE KEY UPDATE` 每行的 affected-rows 编码是插入=1、更新=2、值未变化=0，所以逐行数组里每个元素就能判断这一行发生了什么。
- **PostgreSQL/SQL Server/Oracle**：没有这种编码，每个元素就是该行的受影响行数（插入或更新都算 1），无法只凭数字区分插入/更新，需要时事后回查。
- **H2**：每行单独执行的受影响行数，语义同上；H2 仅用于测试环境，断言建议以查询结果为主。

注意部分驱动在 BATCH 执行下可能返回 `Statement.SUCCESS_NO_INFO`（-2）而不是真实行数，这取决于驱动实现；对行数敏感的逻辑应先在自己的数据库上实测确认。

---

**Q：批量 upsert 是一条 SQL 还是多条？**

多条，但只有一条**语句模板**。`upsert(Collection)` 复用单条 `upsert` 生成的那条单行 SQL，在 `ExecutorType.BATCH` 执行器下逐条 `addBatch`、按 `batchSize` 分块 flush，不做任何多行 `VALUES` 拼接（原因见[批量 Upsert 的实现](#批量-upsert-的实现)）。

因此[字段动态判断](#字段动态判断)对批量同样逐行生效：集合里两条记录的 `email` 一个为 null、一个非 null 时，两行各自渲染自己的列集合，不存在"基于第一条记录确定列集合"的限制。

---

**Q：`IdType.AUTO` 实体的自增主键会回填到实体上吗？**

单条 `upsert` 与 `upsert(Collection)` 都会回填（沿用 MyBatis-Plus 原生 `Jdbc3KeyGenerator` 机制，批量路径在批次 flush 后可见）。详见[主键回填](#主键回填)。

---

**Q：`@KeySequence` 序列主键能用吗？**

能，但取号完全由 MyBatis-Plus 完成：容器里要有 `IKeyGenerator` Bean（MP `extension.incrementer` 包下自带 `PostgreKeyGenerator`、`OracleKeyGenerator` 等），本库复用 MP 为该主键注册的 `!selectKey` 语句，不另拼序列 SQL。单条 `upsert` 与逐条的 `upsert(Collection)` 都会把取到的号写回实体——因为批量本来就是逐条执行同一句单行 SQL，selectKey 每条各取一个号。注意取号会**覆盖**实体上预置的主键值，需要自己决定主键请用 `IdType.INPUT` 且不配 `@KeySequence`。详见[主键回填](#主键回填)。

---

**Q：`@ConflictKey` 可以标注在主键上吗？**

取决于主键策略：`INPUT` / `ASSIGN_ID` / `ASSIGN_UUID` 主键可以——主键本身就是唯一约束，标注后以主键为冲突依据；**`IdType.AUTO` 自增主键不行**——自增键的值在插入前不存在，无法作为冲突判断依据，启动解析期即抛 `UpsertMetaException`（见[异常说明](#异常说明)）。通常建议以业务唯一键（如 `username`、`order_no`）作为冲突键，而不是主键。

---

**Q：多个字段都标注了 `@ConflictKey`，是 OR 关系还是 AND 关系？**

是对应**联合唯一索引**，即 `(biz_code, tenant_id)` 两者组合唯一，不是任意一个唯一就触发冲突。需确保数据库存在对应的联合唯一索引。

---

**Q：项目使用了 MyBatis Plus 的逻辑删除，upsert 会不会有问题？**

`UpsertMetaParser` 基于 MP 的 `TableInfo` 解析字段，逻辑删除字段（`@TableLogic`）在 `TableInfo.getFieldList()` 中就是一个普通字段，因此会正常参与 INSERT 和 UPDATE（不会被当作"逻辑删除即隐藏"处理——Upsert 的 SQL 里没有 `deleted = 0` 这类条件）。业务层需自行保证逻辑删除字段的值符合预期，例如命中已软删行时 Upsert 会直接更新该行而不会恢复判断。

---

**Q：同一 JVM 里存在多个 Spring 上下文 / 多个 MyBatis Configuration（集成测试、父子上下文、动态刷新），实体元数据会串吗？**

不会。`UpsertMetaParser` 是无状态解析器：不维护全局元数据缓存，也不通过 `TableInfoHelper` 的全局注册表按实体类反查 `TableInfo`，只解析每个 `Configuration` 在 SQL 注入期交给它的那份 `TableInfo`——表名、字段映射、主键策略、字段动态策略都取自各自上下文，结构上不存在跨上下文串用的通道。解析只发生在启动注入期（每个实体每个 Mapper 共几次），运行期执行 Upsert 不再解析元数据，因此无需缓存也不会有额外开销。

---

**Q：upsert 与 MP 的自动填充（`@TableField(fill = ...)`) 兼容吗？**

**兼容。** 从 v1.6.0 起，自动填充内嵌在注入的 upsert `SqlSource` 中（`PreFillSqlSource`），在**动态 SQL 绑定之前**调用 `MetaObjectHandler`，确保所有 `@TableField(fill = ...)` 字段都能被正确填充。该机制只作用于 upsert 语句本身——不再注册全局 MyBatis 拦截器，与分页、乐观锁等插件的顺序无关，其他语句零开销。

由于 upsert 使用 `SqlCommandType.INSERT`，`insertFill` 实际上会被调用**两次**（MyBatis-Plus 机制决定，无法从外部禁用）——单条和批量 upsert 均如此，批量时对**每个实体**各调用两次：

1. **第一次（预绑定）**：由本库的 `PreFillSqlSource` → `UpsertFillProcessor` 在 `getBoundSql()` 阶段触发——确保字段在动态 SQL 列裁剪**之前**已被填充
2. **第二次（原生）**：由 MP 原生的 `MybatisParameterHandler` 在参数处理阶段触发——发生在 SQL 绑定**之后**；其中 `updateFill` 对 INSERT 命令从不调用，因此只会重复触发 `insertFill`

这是**无害的**，因为 MP 的 `strictInsertFill`/`strictUpdateFill` 方法在字段已有值时会跳过，第二次调用等价于空操作。若你使用了非 strict 的自定义 `MetaObjectHandler`（无条件 `setFieldValByName`），建议改为 strict 写法以避免二次覆盖；若填充逻辑有性能开销（如远程调用取号），也请注意第二次调用会重复执行。

> **如何验证/排查两次调用**：在自定义 `MetaObjectHandler.insertFill` 中打印 `Thread.currentThread().getStackTrace()`，两次调用的堆栈来源帧不同——预绑定那次包含 `UpsertFillProcessor` / `PreFillSqlSource`，原生那次包含 `MybatisParameterHandler` / `BaseStatementHandler`。本仓库的 `UpsertFillCountTest.collection_upsert_insertFill_invoked_twice_per_entity_with_source_breakdown` 测试即通过堆栈来源断言锁定了这一行为。

这意味着：

- `createTime`（`fill = INSERT`）：插入时填充 ✅
- `updateTime`（`fill = UPDATE`）：冲突更新时填充 ✅
- `updateTime`（`fill = INSERT_UPDATE`）：插入和冲突更新时都会填充 ✅
- `upsert(Collection)` 的集合参数：逐实体填充 ✅（批量走的是单行语句，每条实体各绑定一次参数，预绑定填充先于 SQL 绑定执行；原生填充在绑定后执行，字段已有值时为空操作）
- 任何自定义 `MetaObjectHandler`（如填充当前用户 ID）：均可复用，无需改动

**配置填充策略（v1.6.0+）：**

```yaml
# 单数据源
mybatis-plus:
  upsert:
    fill-strategy: insert_update   # none | insert | insert_update（默认）

# 多数据源
mybatis-plus:
  upsert:
    dynamic:
      fill-strategy: insert_update
```

- `insert_update`（默认）：绑定前调用 `insertFill` + `updateFill`，对应"插入或更新"语义
- `insert`：绑定前仅调用 `insertFill`
- `none`：不做绑定前填充，仅剩 MP 原生 `insertFill`（SQL 绑定后执行，null 字段可能被 NOT_NULL 策略从 SQL 中剔除）

---

**Q：如何实现"只有字段不为 null 时才更新"？**

这是默认行为，无需任何额外配置。`upsert` 会按字段的 MP `FieldStrategy`（`insertStrategy`/`updateStrategy`）自动生成 `<if test="field != null">` 动态判断——单条与 `upsert(Collection)` 的每一行都一样——行为与 MP 原生 `insert`/`updateById` 完全一致：未显式标注 `@TableField` 的字段默认遵循全局策略 `NOT_NULL`，字段为 null 时不会出现在 SQL 中，因此插入时由数据库默认值接管，更新时不会覆盖原值。详见[字段动态判断](#字段动态判断)。

---

## 各数据库 UPSERT 对比

| 数据库 | 支持 UPSERT 的版本 | 语法关键字 | 是否依赖唯一键冲突 | 并发安全性 | 备注 |
| --- | --- | --- | --- | --- | --- |
| **MySQL** | 4.1+ | `INSERT ... ON DUPLICATE KEY UPDATE` | ✅ 是 | ✅ 安全（事务内） | 简单高效，但仅支持单表 |
| **Oracle** | 9i+ | `MERGE INTO ... USING ...` | ❌ 否，可自定义匹配条件 | ⚠️ 需谨慎 | 灵活但复杂 |
| **SQL Server** | 2008+ | `MERGE INTO ... USING ...` | ❌ 否，可自定义匹配条件 | ⚠️ 不推荐高并发使用 | 功能强大但存在潜在 BUG |
| **PostgreSQL** | 9.5+ | `INSERT ... ON CONFLICT (...) DO UPDATE` | ✅ 是 | ✅ 安全可靠 | 语义最清晰的 UPSERT 实现 |

---

## 数据库注意事项

> **⚠️ 验证等级（必读）**：自动回归测试套件只在 **H2（MySQL 模式）** 上运行；**MySQL、PostgreSQL** 通过 `examples/` 示例工程做过实际运行验证（手工执行，不在 CI 中）；**Oracle 与 SQL Server 的方言未在真实数据库上验证过**——生成的 SQL 严格按各数据库官方语法规范编写，并通过了结构级断言测试（见 `DialectSqlTest`），但缺少真实环境的运行时确认。"语法上支持"与"已在你没跑过的数据库上验证过"是两回事，请勿混同。
>
> 在 Oracle / SQL Server 上首次使用本库前，建议：
>
> 1. 用你的实际实体（含动态字段、自动填充、各注解组合）跑一遍单条 `upsert` 与批量 `upsert(Collection)`，确认 SQL 可执行——两者用的是同一条单行语句，但批量走 JDBC BATCH 执行器，驱动行为仍需单独确认；
> 2. 确认 `List<BatchResult>` 里的逐行受影响行数符合你的预期（部分驱动在 BATCH 下会返回 `SUCCESS_NO_INFO`）；
> 3. 关注下方两个数据库各自的注意事项（如 SQL Server MERGE 的并发特性）。
>
> 如遇问题欢迎提 issue 附上生成 SQL 与报错信息。

### MySQL / MariaDB

- 默认使用 `VALUES()` 函数引用当次插入的列值，该语法向下兼容所有支持 `ON DUPLICATE KEY UPDATE` 的 MySQL/MariaDB 版本。
- 如使用 MySQL 8.0.19+，可在配置中设置 `mybatis-plus.upsert.use-new-mysql-syntax: true` 来启用新的 `AS new` 别名语法（`VALUES()` 自 MySQL 8.0.20 起被官方废弃，当前仍可用）。
- **MariaDB**：`ON DUPLICATE KEY UPDATE` / `VALUES()` 语法与 MySQL 一致，复用同一方言，URL `jdbc:mariadb:` 也会被自动推断为 MySQL 方言；但 MariaDB **不支持** `AS new` 行别名语法，请勿对 MariaDB 开启 `use-new-mysql-syntax`。本库未在真实 MariaDB 实例上单独验证，属协议级兼容。
- **TiDB 等 MySQL 协议兼容数据库**：使用 `jdbc:mysql:` URL 时会被自动识别为 MySQL 方言，属协议级理论兼容——冲突检测与 affected-rows 语义取决于各库自身实现，本库未逐一验证，接入生产前请充分测试；确有差异时自行实现 `UpsertDialect`。PostgreSQL 兼容库（如 CockroachDB、KingbaseES 的 PG 模式）同理。

### PostgreSQL

- `ON CONFLICT (cols) DO UPDATE` 要求括号内的列必须有对应的**唯一索引**（主键也算），否则报错 `there is no unique or exclusion constraint matching the ON CONFLICT specification`。

### Oracle

- 每条记录是**一条独立的单行 MERGE**（`SELECT ... FROM dual` 形式的源子查询），批量写入只是把这些 MERGE 交给 JDBC BATCH 执行器分块提交——不使用 `UNION ALL` 拼成的多行源子查询，也不依赖 Oracle JDBC 的多语句支持（Oracle 驱动在 `;` 分隔的多语句上会报 ORA-00911，本库的写法不会碰到它）。
- 同一批次内**允许出现重复的冲突键**：两行同键会先后各自执行一次 MERGE，第一行插入、第二行更新，不会触发 ORA-30926。
- 逐行行数从 `upsert(Collection)` 返回的 `List<BatchResult>` 的 `getUpdateCounts()` 读取。
- 数据量大时用 `upsert(collection, batchSize)` 控制每块条数，避免单次 flush 占用过多驱动内存与事务空间。

### SQL Server

- MERGE 语句末尾的 `;` 是 SQL Server 语法规范要求，缺少会报语法错误。
- 批量写入为逐条独立的单行 MERGE，不使用 `USING (VALUES (...),(...)) AS src(cols)` 多行写法，因此没有"版本 ≥ 2008"这类多值语法要求，也不受同一批次内重复冲突键的影响。
- 若遇到字符类型/排序规则相关的报错，可在 JDBC URL 添加 `;sendStringParametersAsUnicode=false` 或升级驱动版本。

### H2

- H2 的 `MERGE INTO ... KEY(...)` 语法为 H2 私有，**不适用于生产环境**，仅用于单元测试。
- 在 `application.yml` 中配置 `mybatis-plus.upsert.db-type: h2` 或使用 H2 DataSource 时自动探测。
- H2 Mode 建议设置为 `MODE=MySQL` 以最大程度模拟 MySQL 行为（建表 DDL 可以复用）。
- 批量写入是逐条提交的单行 MERGE，不存在 `;` 拼接多语句那条路径。H2 只用于测试环境，断言请以查询结果为主而不是返回值。