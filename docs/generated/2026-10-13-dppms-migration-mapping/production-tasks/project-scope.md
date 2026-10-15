# 有效项目迁移范围与旧系统SQL证据

核对日期：2026-10-15。用户要求只迁有效项目，并以第一次整理时要求核查的旧系统SQL为依据。本页说明当前27项任务的共同范围；P16内完成规范身份、主档和来源身份处理，P17不另建任务。

**当前采用：先按二进制同类型、同编码取最大`projectId`，再保留`disabled = 0 AND effectiveTo IS NULL`的规范项目。来源旧ID自身也须满足有效条件，并归属于入选规范组。** 这是结合两代旧系统列表与失效操作得到的迁移条件；不是声称某一条旧SQL原本同时包含两个条件。新验证库的停用配置及项目材料采集已接入此范围，旧库配置保留历史。

## 来源证据与实际调用

旧源码根目录为`D:/IDEAWorkspace/PMS`。原634条SELECT及索引已在[旧查询合集](../sql/legacy-original-queries.sql)、[语句索引](../sql/legacy-query-index.json)和[include片段](../sql/legacy-query-fragments.sql)保留。Struts列表先以`update`声明创建临时表，再查询临时表；失效操作也是`update`，不能只从SELECT合集推断其完整规则。

| 来源位置（相对旧源码根） | 实际条件或操作 | 对范围的含义 |
| --- | --- | --- |
| `PMS-struts/config-ibaits/sql-map-project-config.xml:44,388`，`create_tmp_tb_project` | 活动SQL从`pm_project_header ph`生成列表临时表，`WHERE ISNULL(ph.effectiveTo)`；第187行附近还有被注释的旧段，本次依据第388行活动段 | 旧列表以失效时间为空判断有效项目 |
| 同文件`:808`，`find_project_list` | 查询上述`tmp_tb_project`；项目状态是可选筛选 | 不能仅看临时表SELECT而漏掉先前有效性条件 |
| 同文件`:3397-3401`，`invalid_project_header`；`PMS-struts/src/com/dp/plat/service/ProjectServiceImpl.java:1730` | 旧失效入口调用`UPDATE pm_project SET effectiveTo = NOW(), updateTime = NOW() WHERE projectId = ...` | 旧失效记录可能仅有`effectiveTo`，不能只查`disabled` |
| `PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/controller/ProjectController.java:121-181`，`/list` | 第130/135行固定`setDisabled(false)`；第129/134行`setEffectiveTo(now)`已注释；第181行调用分页查询 | 新列表固定排除停用项目；不能把注释的有效期参数当作正在执行 |
| 同模块`mapping/ProjectHeaderMapper.xml:598-690,789-855`，`sql_model_where_selective`及`selectBySelectivePageable`（原查询索引L0394） | 列表`FROM pm_project AS ph`引用公共片段，参数非空时`ph.disabled = #{model.disabled,jdbcType=BIT}`；`projectState`和`effectiveTo`仅在传参时过滤 | 实际默认条件是`disabled=0`，不默认排除已闭环状态 |
| 同模块`service/impl/ProjectHeaderService.java:894-905` | 合并转出旧项目设`projectState="100"`、`disabled=true`；设置`effectiveTo`已注释 | 只按`effectiveTo IS NULL`会误保留新实现的停用旧项目 |
| 同文件`:929-937`，`invalidProject` | 调用旧失效DAO，并写`disabled=true`和`effectiveTo=new Date()` | 新入口同时维护两种失效标记 |
| [旧视图原定义](../sql/legacy-view-definitions.sql)，`pm_project_header` | 视图仅按`projectType='10'`限定用服 | 视图名不代表已过滤有效项目 |

关键片段保持原语义，下面的UPDATE仅说明旧失效事实，不是迁移执行脚本：

```sql
-- Struts create_tmp_tb_project 活动列表条件
WHERE ISNULL(`ph`.`effectiveTo`)

-- Struts invalid_project_header（原业务失效操作；本轮未执行）
UPDATE pm_project
SET effectiveTo = NOW(), updateTime = NOW()
WHERE projectId = #projectId#
```

```java
// SpringMVC ProjectController.list
// project.setEffectiveTo(now);
project.setDisabled(false);

// ProjectHeaderService 合并转出的原项目
source.setProjectState("100");
source.setDisabled(true);
// source.setEffectiveTo(new Date());
```

```xml
<!-- SpringMVC sql_model_where_selective，分页列表引用 -->
<if test="model.disabled != null">
    ph.disabled = #{model.disabled,jdbcType=BIT} AND
</if>
```

以上证据同时解释为何两个条件都需要：旧失效路径只写结束时间，新合并路径可以只写停用标记。生命周期状态与有效性分开处理；不引入“最近几年”“有无明细”“完成/关闭即无效”等额外条件，也不将`disabled IS NULL`补成0。

## P16与下游共用的范围

1. 在同一冻结来源上，先对`pm_project`全部记录按`CAST(projectType AS BINARY)`、`CAST(projectCode AS BINARY)`分组取`MAX(projectId)`，沿用[问题记录中的Q011原裁决](../../../decisions/open-questions.md)。不得先滤有效行再取最大值；最大行无效则不迁该组，不用较小旧ID替代。
2. 对选出的规范行应用已批准的类型10、可选规范ID范围及`disabled=0 AND effectiveTo IS NULL`。
3. 仅把自身也符合有效条件、且属于入选规范组的旧ID作为来源身份。仍有效的非最大ID用于承接范围内来源数据，不另造一个正式项目；失效/停用旧ID及其项目归属数据不迁入，也不因同码自动回挂。历史源码、旧配置、原绑定及回执仍作为原日期证据保留。
4. 规范主档、基础最终客户资料、项目合同关系、相关方、产品/配置、实施范围、成员及项目归属设备历史共用此集合。合同、执行单、订单、回款、续保、发货等任务中原本按项目限定的来源部分也复用此集合，不能为补齐依赖重新扩大到已排除项目。
5. 组织、账号、客户、产品等独立共享基础资料继续按各自已批准范围。来源集合可以在正式项目创建前只读取得；它不增加“正式项目必须先存在”的依赖，也不收紧原已裁决的可空正式引用。缺失/多义来源身份按原问题规则记录，不能伪称已判定无效或强行关联。
6. `projectIds=""`表示在有效规范项目内不再按ID缩小。**有效集合为空时必须读取零行**，不能将计算出的空集合重新编码为“不限制历史项目”；空权限集合也继续返回空。各下游来源原有其他筛选、权限、Owner和冲突保护保持。

[P16范围SELECT](sql/P16-valid-project-scope.sql)一次计算规范ID并返回入选的规范ID/原ID关系，可供同一任务后续主档读取与身份绑定复用，不新增独立范围任务。它使用派生表，未要求旧源库支持CTE；具名参数沿现有来源参数形式展示。字段全量承接、审计身份解析与目标Owner逻辑继续取自原组件，不由这份范围SELECT替代。

原51份SQL逐字保留，新增范围SELECT仍为评审副本。当前应用的valid-projects.sql已被下游SQL展开，P16在同一分组查询内生成主档与身份；材料采集先核对有效来源身份再查询子表。21份新配置已保存到新验证库并停用。32份SQL已作MySQL5.7只读EXPLAIN，P16作只读抽样；未发起预览或迁移。

## 已保存快照的范围影响

证据为仓库外`D:/CodexTaskArtifacts/project-detail-followup/preview-recheck-20261014/master-current-source.json.gz`；文件保存时间`2026-10-14T17:50:45.763078`。其中80,170条已经是原类型10口径的规范项目，不能据此核对全部非最大旧ID。

| 原项目状态码 | 原规范项目数 | 新范围入选 | 有效期非空排除 |
| --- | ---: | ---: | ---: |
| 20 | 51,877 | 51,799 | 78 |
| 100 | 20,676 | 20,674 | 2 |
| 32 | 4,730 | 4,404 | 326 |
| 31 | 2,860 | 2,552 | 308 |
| 30 | 27 | 0 | 27 |
| 合计 | 80,170 | 79,429 | 741 |

快照中`disabled`全部为字节零的十六进制表示`"00"`（保存工具对bytes调用`.hex()`），不是布尔字符串真值；741条因`effectiveTo`非空排除。这里只作本地快照分类，没有实时查库、发起新预览或得出生产预计新增量。79,429条包含仍有效的已闭环项目，并非“全部未完成项目”。

原80,320个旧身份、73,580个待新增项目及下游数量仍是旧口径，不能直接沿用为新范围数量。全部旧ID、项目归属明细和新批次新增/更新/不变数量需按对应冻结范围核对；本轮没有这些新的运行结果。既有“暂不新增”和“合同/发货暂不执行”保持，也不删除或清理已经迁入的数据。
