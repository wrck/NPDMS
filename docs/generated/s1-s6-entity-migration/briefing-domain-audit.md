# SOL 工程交底：限定范围审计与领域改造补丁

## 交付状态

- 仓库：wrck/NPDMS；目标分支：codex/s1-s6-business-entity-migration。
- 本次读取与补丁基线：71a591fc1e302ca4ddf42532d58401b2f2d2ca61。
- 状态：本次将工程交底限定范围改造独立提交到迁移分支；未部署，不能标记工程交底或 S1～S6 迁移完成。
- 只处理工程交底一个单元。需求分析、现场工勘、其他领域、旧交底代码/页面/API/表/入口不修改。
- 领域沿用 PRD 既定 SOL，不重新划分领域，不改变正式 Owner、目标版本或业务角色。
- 本记录是迁移过程证据，不替代 PRD、SDS、Feature Spec 或正式完成状态。

## 依据及审计范围

正式归属来自 PRD §3.5 与 `docs/design/phase-1-domain-ownership.md`；
领域与持久化层区别沿用 `docs/design/02-domain-model.md` 和 `02c-data-ownership-matrix.md`。
PRE-05 的已读取业务边界位于 `specs/001-project-delivery-platform/domains/SOL-交付准备与方案需求规格.md` 的 PRE-05 段。
本次不展开全量 PRD 功能补建；对明确发现的成功事实伪造和授权缺口，只在新副本处理。

前序已完整读取旧 BriefingController、五个请求/响应 VO、BriefingService、
BriefingServiceImpl、BriefingDO、BriefingMapper、独立页面和前端 API，以及 V27/V218。
本次比较 b2f0045e 到 71a591fc：只增加归属映射文档，旧代码和交底副本未变，故复用这些已读证据。
本次逐文件读取当前新 Service、ImportService、Controller、DO、Mapper、Query、迁移 XML、
状态策略、现有两个测试类和结构检查脚本，并读取项目范围重验公开契约。
补丁中所有修改文件的原始内容均重新计算 Git blob SHA，与 GitHub 返回值相符。

未完成全仓库引用搜索和全部直接消费者逐方法/装配审计；旧项目详情的通用入口未改。
因此这不是对整个模块“完整行为审计已通过”的声明，也不能仅以该补丁进入生产。

## 旧行为与约束逐项核对

| 行为 | 旧实现/已有副本事实 | 本次处理 |
|---|---|---|
| 创建 | 编号唯一、草稿、默认 STANDARD；原项目存在性校验为空 | 用 PROJ 公开范围契约校验并锁范围；检查插入行数和生成主键；初始身份/状态/版本不来自客户端 |
| 编辑 | 仅草稿；编号不变；版本允许省略；请求能携带另一个 projectId | 聚合锁定租户/项目/ID/编号；已有版本不能为空且必须匹配；状态、来源和审核信息不由普通请求更新 |
| 删除 | 仅草稿，锁定当前对象后删除 | 保留状态规则；增补功能权限、项目范围及范围先于对象的锁顺序 |
| 详情 | 现有副本主要依赖租户插件与 Controller 权限 | Service 先查功能权限，按显式租户读对象，再核对完整项目访问范围 |
| 分页 | 已按租户筛选，但未按用户项目范围筛选 | 服务端解析可见项目集合，SQL 前过滤；null 拒绝，空集合返回空页；限制页大小并验证时间区间 |
| 生成 | 原函数与副本拼接不存在的 PDF URL、固定文件大小和 auto- 校验值 | 删除新副本的占位成功路径；只接收受信适配器结果并校验对象/版本/元数据，实际文件核验成功后才写 GENERATED |
| 审核 | GENERATED 可 PASS/REJECT；审核人来自请求 | 状态规则不变，必须匹配版本；记录当前服务端操作人；PASS 需实际文件核验，REJECT 不因坏文件失去退回通道 |
| 发布 | AUDITED → PUBLISHED | 聚合判断状态，发布前重新核验文件，不把历史占位元数据当成真实文件证明 |
| 作废 | 草稿/已生成/已审核可作废；已发布/已作废禁止 | 下沉到聚合，规则保持一致 |
| 存量承接 | 保留旧主键、字段、删除标记及元数据；重复比较，冲突拒绝 | 原写入列和完整内容校验不变；先授权并锁项目范围，再锁旧来源/新目标；来源项目或租户漂移时拒绝 |
| 页面与前端 API | 原九个操作、详情/列表与现有请求字段 | 本补丁不改页面/API 路由；文件生成缺失时会明确失败而不显示伪造成功；审核人输入不再作为服务端身份 |

原审核人选择控件仍需在新页面调整为“当前操作人由服务端记录”；该 UI 语义适配尚未实施。
新 Service 的更新、生成、审核现在拒绝缺失版本；旧 API 行为不变。
删除、发布、作废仍沿用既有“按锁定当前对象执行”的接口，没有声称已加入客户端 expectedVersion 协议。

## 聚合与值对象落位

- `domain.briefing.BriefingAggregate`：旧独立交底对象的迁移聚合；身份和状态约束由 SOL 持有。
- `BriefingAggregate.Identity`：租户、项目、对象 ID 与不可变业务编号。不是可写项目副本。
- `BriefingAggregate.State`：保持旧五个状态编码，拒绝未知状态与非法迁移。
- `BriefingDocumentArtifact`：生成/核验过程中传递的不可变文件及来源值。哈希格式正确不等于文件真实。
- `BriefingGenerationPort`：应用端口；生产适配器需要复用已有 SOL/PROJ/AST/PLT 事实和文件能力。
- `BriefingEntityDO`：仍是独立表的持久化映射，不能替代领域聚合或充当项目/模板/文件 Owner。

聚合不依赖 Controller、Mapper、Spring 或旧业务类。应用层负责权限、事务与持久化转换。
聚合转换不递增 MyBatis @Version；持久化成功时由现有插件递增且检查影响行数，避免双重递增。

这只是旧行为的领域内承接，不是 PRE-05 完整新模型。
当前 `version` 是行级并发水位，不能当作不可变文档修订、模板版本或文件版本。
已确认交底的新修订关系、完整历史、文件精确版本及来源水位的最终模型仍须补齐并对齐既有正式契约。

## 表结构与存量承接

本次不修改 V251，不新增 DDL，不改旧 `pms_eng_briefing`。
继续使用已有副本 `sol_engineering_briefing` 与专用 ImportMapper；普通查询只访问新表。
新增来源定位 SELECT 的目的仅是取得项目以完成授权/范围锁；它没有 FOR UPDATE。
锁顺序统一为项目范围 → 旧来源（仅迁移）→ 新交底。锁后重新比较来源身份。
原 INSERT 列及参数逐字保留，包含 NULL、空字符串、零值、删除标记、审核及审计字段。
不调用 create/generate/approve/publish 来搬运旧数据；迁移不会生产新的业务完成事实。

目标数据被修改、旧来源变化或旧主键已被新对象占用仍明确拒绝，不自动合并、不覆盖、不重编号。
旧来源不满足当前项目授权或项目关联无效时不能靠跳过权限迁移；需另外的受控历史处理方案。
新修订模型未定前不再按旧字段堆新表；这不表示表结构与存量承接阶段已全部完成。

## 生成适配器缺口与独立功能边界

本补丁刻意没有“返回成功”的默认生成实现。当前仓库未提供本端口的经验证生产适配器。
适配器缺失时 generate 明确失败，审核通过/发布也不接受无法验证的文件；已导入历史不被删除或改写。
生成失败前不改变对象；数据库更新失败仍由 Spring 事务回滚。文件是外部副作用，不能假定数据库回滚会删除文件。
生产适配器仍需完成模板匹配、实际来源权限、设备清单确认、真实文件生成/存储/下载验证、稳定重试及孤立文件对账。
本地 Mock 仅验证应用是否正确调用接口，绝不是文件实际生成证据。

独立 CRUD 的项目功能权限/范围校验不要求 WorkBinding、S1/S6 或固定任务。
ProjectScopeApi 的集成装配、并发撤权、项目生命周期准入和具体业务角色规则还需实际验证；不能仅由单测认定完整授权验收。
不增加模板/阶段消费者或完成事实 Provider 来掩盖上述缺口。

## 实际验证

- 使用当前 JDK 21 编译真实聚合、值对象、生成端口和旧策略参照，`-Xlint:all -Werror` 通过。
- 114 项领域断言实际通过，包含五状态矩阵、身份固定、版本缺失/过期/溢出、非法动作和错误文件归属。
- 37 项源码结构检查实际通过；仅检查约束接线、SQL 边界与锁调用顺序，不表示数据库锁语义已实测。
- JUnit/Mockito 用例：Service 18 项、ImportService 9 项、Access 8 项，共 35 项，已编写/适配但未执行。
- 未执行 JDK 25/Maven 完整构建、Spring/JUnit、MySQL、真实文件、API、前端构建与浏览器回归。
- 本地补丁可应用性检查只针对按 Git blob SHA 核对的文件快照，不是完整仓库构建。
- 提交前再次核对补丁基线及全部 16 个文件，反向还原/重新应用后代码逐字节一致；重新编译并运行 114 项领域断言、37 项结构检查通过。本次只更新本文的提交状态，业务代码与原补丁一致。

复核命令：

```sh
python tests/migration/briefing/verify_domain_migration.py
javac --release 21 -Xlint:all -Werror -d /tmp/briefing-domain-check \
  pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/briefing/*.java \
  pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/briefing/entity/BriefingGenerationPort.java \
  pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/briefing/entity/BriefingEntityStatePolicy.java \
  tests/migration/briefing/BriefingAggregateCheck.java
java -cp /tmp/briefing-domain-check BriefingAggregateCheck
```

## 下一步完成条件

先补齐本单元全部消费者审计、真实生成与文件引用契约、不可变修订/历史承接及适用验证，
再判定是否满足“独立业务功能”；不能把该补丁或一个提交当成模块已迁移完成。
其余模块未在本次补丁中混入，已完成的需求分析与现场工勘保持原样。
