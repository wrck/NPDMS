# 数据库查询接口编码规则

- 状态：BASELINE
- 生效日期：2026-08-24
- 适用范围：所有 `pms-module-*` 后端模块及后续新增的 PMS 模块
- 目标：统一查询接口，消除参数膨胀和内联 SQL，在保持查询语义准确的前提下支持条件扩展

本文使用以下约束词：

- **必须**：代码评审和任务验收的强制条件。
- **应当**：默认做法；偏离时必须在代码或设计中说明原因。
- **可以**：满足上下文条件时允许采用。

## 1. 总体原则

数据库查询遵循：**一个查询意图、一个语义方法、一个查询对象**。

- Mapper 方法必须表达业务查询意图，不得暴露任意表、字段、操作符或 SQL 片段。
- 除主键和稳定复合唯一键查询外，查询方法必须只接收一个 Query 对象。
- Query 对象必须面向具体查询场景，不得设计为包含大量可选字段的万能查询器。
- 查询条件扩展应当通过新增 Query 字段完成，不得持续增加 Mapper 方法的位置参数。
- 查询语义、权限范围、排序和空值行为必须明确，不能依赖调用方猜测。

## 2. Mapper 方法签名

### 2.1 允许的显式参数

以下查询可以直接使用显式参数：

| 场景 | 允许形式 | 示例 |
|---|---|---|
| 主键查询 | 一个参数 | `selectById(Long id)` |
| 单字段稳定唯一键 | 一个参数 | `selectByCode(String code)` |
| 两字段稳定复合唯一键 | 最多两个参数 | `selectByProjectIdAndCode(Long projectId, String code)` |

“稳定唯一键”必须有明确业务唯一性或数据库唯一约束，不能只是当前碰巧只需要两个筛选条件。

### 2.2 必须使用 Query 对象的场景

下列查询无论当前字段多少，都必须使用一个 Query 对象：

- 分页查询或游标查询；
- 包含可选筛选条件的列表查询；
- 包含租户、数据权限或可见范围的查询；
- 树、路径、聚合、统计、快照和进度查询；
- 包含时间范围、集合条件、查询模式或排序规则的查询；
- 当前只有少量字段，但业务上预计继续扩展的查询。

```java
List<ProjectMasterDO> selectDescendantsPage(
        @Param("query") ProjectTreePageQuery query);
```

禁止使用长位置参数列表：

```java
// 禁止
List<ProjectMasterDO> selectDescendantsPage(
        Long tenantId, Long rootId, Long treeVersion, Long ancestorId,
        boolean directOnly, Collection<Long> visibleProjectIds,
        int offset, int limit);
```

### 2.3 禁止的参数类型

查询方法不得接收：

- `Map<String, Object>`、通用 `Object` 或键值数组；
- 由 Service 或 Controller 构造的 `QueryWrapper`、`LambdaQueryWrapper`；
- 原始表名、列名、排序 SQL 或条件 SQL；
- 用于控制多种查询语义的一组位置 boolean 参数；
- Controller 的保存请求对象或数据库 DO 作为查询条件载体。

## 3. Query 对象设计

### 3.1 命名与位置

统一采用能够表达场景的名称：

- 分页：`XxxPageQuery`
- 列表：`XxxListQuery`
- 特定语义：`ProjectTreeQuery`、`LatestProgressQuery`

DAL 专用查询对象放在所属聚合的 `dal.mysql.<aggregate>.query` 包下。Controller 的
`XxxPageReqVO` 只作为 HTTP 边界入参；Service 完成校验、权限解析并转换为 Query，
Mapper 不依赖 Controller 包。

### 3.2 字段构成

Query 对象只承载本次查询需要的四类信息：

1. 必要业务定位，例如 `projectId`、`rootProjectId`、`treeVersion`；
2. 可选业务筛选，例如状态、编码前缀、时间范围；
3. 服务端解析的数据范围，例如 `tenantId`、`visibleProjectIds`；
4. 分页和受控排序信息。

字段必须使用明确类型和名称：

- 枚举或受控编码表达状态、模式和排序选项；
- 集合字段使用具体元素类型，例如 `Set<Long>`；
- 不得在 Query 中放入 Mapper、Service、Wrapper 或执行回调；
- 不得提供任意字段名、任意操作符或任意 SQL 条件字段。

预计会扩展的 Query 应通过 Builder、转换器或命名工厂创建，不得在调用点散布长位置构造器。

### 3.3 查询模式

当一个查询存在互斥模式时，使用枚举表达，不使用多个 boolean：

```java
public enum TreeQueryMode {
    CHILDREN,
    DESCENDANTS,
    ANCESTORS,
    LOCATE,
    BUSINESS_LEVEL
}
```

如果不同模式的输入、结果或 SQL 主体明显不同，应拆成不同的 Query 和 Mapper 方法，
不得强行合并成万能方法。

## 4. SQL 实现位置

### 4.1 类型安全的简单查询

简单单表查询可以在 Mapper 默认方法中使用 `LambdaQueryWrapperX`：

```java
default PageResult<AddressDO> selectPage(AddressPageQuery query) {
    return selectPage(query, new LambdaQueryWrapperX<AddressDO>()
            .likeIfPresent(AddressDO::getFullAddress, query.getFullAddressKeyword())
            .eqIfPresent(AddressDO::getDistrictCode, query.getDistrictCode())
            .eqIfPresent(AddressDO::getStatus, query.getStatus())
            .orderByDesc(AddressDO::getId));
}
```

该方式只允许使用类型安全的字段引用和 Wrapper API，不得混入文本 SQL。

### 4.2 必须进入 Mapper XML 的查询

以下 SQL 必须放在 `src/main/resources/mapper/<aggregate>/*Mapper.xml`：

- 联表、子查询、`UNION`、CTE 或窗口函数；
- `<if>`、`<foreach>`、`<choose>` 等动态 SQL；
- 锁查询、数据库特定语法或需要精确执行计划的查询；
- Wrapper 无法清晰、类型安全表达的查询。

Mapper 接口只保留声明：

```java
List<ProjectMasterDO> selectTreePage(
        @Param("query") ProjectTreePageQuery query);
```

XML 统一引用 `query`：

```xml
<select id="selectTreePage"
        resultType="cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO">
    SELECT p.*
    FROM proj_project_tree_path t
    JOIN proj_project p ON p.id = t.descendant_project_id
    WHERE t.root_project_id = #{query.rootProjectId}
      AND t.tree_version = #{query.treeVersion}
    <foreach collection="query.visibleProjectIds" item="projectId"
             open="AND p.id IN (" separator="," close=")">
        #{projectId}
    </foreach>
    ORDER BY p.tree_depth, p.tree_sort, p.id
    LIMIT #{query.offset}, #{query.limit}
</select>
```

### 4.3 禁止的 SQL 写法

- 禁止 `@Select`、`@Update`、`@Delete`、`@Insert` 及对应 Provider 注解；
- 禁止 `.last("FOR UPDATE")`、`.last("LIMIT 1")` 等文本 SQL 片段；
- 禁止通过 `.apply(...)`、`.inSql(...)` 等接口拼入外部字符串；
- 禁止使用 `${}` 拼接客户端或业务输入；统一使用 `#{}` 参数绑定；
- 禁止在 Service 或 Controller 中拼接 SQL。

## 5. 条件语义

### 5.1 空值和集合

统一语义如下：

| 输入 | 语义 |
|---|---|
| 单值 `null` | 未提供该可选条件 |
| 集合 `null` | 未提供该可选条件 |
| 空集合 | 条件集合没有候选值，返回空结果 |
| 必填权限集合为 `null` | 非法调用，Service 必须拒绝 |
| 权限集合为空 | 无可见数据，返回空结果 |

集合为空时不得省略 `IN` 条件后执行扩大范围的查询。对于权限集合和调用方传入的
集合条件，不得直接使用会把空集合解释为“忽略条件”的 `inIfPresent`；应在 Mapper
调用前或默认方法开头返回空集合。

### 5.2 字符串匹配

名称必须体现匹配方式：

- `projectCode`：精确匹配；
- `projectCodePrefix`：前缀匹配；
- `projectNameKeyword`：包含匹配。

不得让同一个字段在不同 Mapper 中有时精确、有时模糊而不在名称中体现。

### 5.3 时间范围

新增查询的时间范围统一使用左闭右开区间：

```text
[startInclusive, endExclusive)
```

实现时使用 `>= startInclusive` 和 `< endExclusive`，避免跨天、毫秒精度和相邻区间重复问题。

### 5.4 查询校验

- 必填字段、互斥条件和模式依赖由 Service 在调用 Mapper 前校验；
- Mapper 不得静默补造业务值或修改错误的查询意图；
- 数据库查询仍需通过唯一约束、外键或状态约束保证最终一致性。

## 6. 租户、权限和模块边界

- `tenantId`、用户身份和数据权限范围必须由服务端上下文解析，不能直接信任客户端值。
- 简单 Wrapper 查询必须保持平台租户和逻辑删除机制生效。
- 自定义 XML 必须明确验证租户、逻辑删除和数据权限是否正确生效，不能仅因查询返回数据就判定正确。
- 权限可见集合为空时必须返回空结果，禁止降级为全量查询。
- Mapper 只能访问本模块拥有的业务表；禁止通过跨模块联表绕过 Business API 和领域边界。
- 跨模块数据由 Service 调用目标模块 Business API 后编排，不得把跨模块访问能力塞入 Query。

## 7. 分页和排序

- 普通分页统一使用 `PageParam` / `PageResult<T>`；游标分页使用场景专用 Query 和结果对象。
- 外部可调用的分页查询必须设置页大小上限，禁止默认无界读取。
- 导出、批处理和后台扫描不得通过 `PAGE_SIZE_NONE` 复用用户列表接口，应使用专用批次或游标查询。
- 排序只允许服务端固定规则或白名单枚举，不得接收原始列名和排序表达式。
- 分页排序必须追加唯一字段作为最终排序项，例如 `id`，保证翻页结果稳定。
- 树和路径查询必须明确结构排序，例如 `tree_depth, tree_sort, id`。

## 8. 返回值和锁查询

- 列表查询返回空集合，不返回 `null`。
- 分页查询返回 `PageResult<T>`。
- 唯一查询不存在时返回 `null`；出现多条时暴露数据完整性异常，不得用 `LIMIT 1` 掩盖。
- “首条”或“最新一条”必须是明确业务语义，并包含确定性排序及 `id` 兜底。
- 锁查询方法名必须以 `ForUpdate` 结尾，并且只能在事务内调用。
- 复杂锁查询放入 Mapper XML；调用方必须保证锁顺序一致，避免死锁。
- Mapper 返回本模块 DO 或明确命名的查询投影，不得返回 `Map` 或未定义结构。

## 9. 方法命名

使用以下统一前缀：

- `selectBy...`：单条或由名称明确限定的查询；
- `selectList...`：列表查询；
- `selectPage...`：分页查询；
- `selectCount...`：计数查询；
- `exists...`：存在性查询；
- `select...ForUpdate`：加锁查询。

使用 Query 对象后，方法名表达业务意图，不再把所有 Query 字段拼进方法名。

## 10. 新旧代码执行规则

- 新增 PMS 查询必须立即遵守本文。
- 修改既有 Feature 时，必须检查本次涉及的 Mapper，并在同一改造范围内消除长参数列表和内联 SQL。
- 不得仅因旧实现存在就继续复制不符合规则的写法。
- 不得为了统一规则顺带重构与当前 Feature 无关的 Mapper；无关问题登记后由对应 Feature 处理。

## 11. 代码评审检查清单

- [ ] 查询是否对应一个清晰的业务意图？
- [ ] 除主键或稳定复合唯一键外，Mapper 是否只接收一个 Query？
- [ ] Query 是否具体、类型明确且不包含任意 SQL 能力？
- [ ] Mapper 是否已经与 Controller ReqVO 解耦？
- [ ] 文本 SQL 是否全部位于 Mapper XML？
- [ ] 是否不存在 SQL 注解、`${}` 和 `.last(...)` 等文本片段？
- [ ] 空集合是否返回空结果，而不是扩大查询范围？
- [ ] 租户、逻辑删除和数据权限是否由服务端控制并经过验证？
- [ ] 是否保持模块表所有权边界？
- [ ] 分页是否有上限、稳定排序和唯一字段兜底？
- [ ] 查询结果和不存在/多条场景的语义是否明确？
