# 项目模板种子（PM-03 / PM-11，F-PROJ-009）

2026-09-21需求方确认：新库替换早期模板，并移除依赖旧模板的示例项目及其依赖数据；已有库新增迁移停用旧示例，保留项目、发布修订和冻结历史。

## 内容与验收边界

- `accepted-fproj009-20260921.json`：从验收来源库只读提取的 `FPROJ009_DELIVERABLE_FIX_20260921`。模板 `993009001593`、发布修订 `993009001603`、来源项目 `992203060011 / PJT2026000009`。保存原设计文档、原执行快照及4个实际发布业务视图，不迁移项目运行状态或上传文件。原S1—S6配置与验收证据不改写。
- `PMS_MINIMAL_NORMAL_CLOSURE.bpmn`：该验收模板引用的正常闭环流程原始定义。通过既有BPM流程管理入口发布；禁止直接复制Flowable运行表。流程部署、原闭环配置中的审核人映射需要在目标环境确认，本次SQL不部署流程或启动流程实例。
- `prd-project-scenarios.json`：PRD 3.2前六类项目场景的可编辑草稿。五类正式交付场景含S0—S6；售前仅S0、S4及EXE-03/EXE-04。直签含初验、终验、初验后满意度；非直签无初验；督导含现场服务单。
- 场景草稿复用验收模板的办理绑定和交付件配置；删除不适用任务时同步重建门禁引用与阶段规则。交付件条件使用`DELIVERABLE`实际存在性，不用任务完成替代。包含验收里程碑定义，不伪造里程碑达成记录。
- 六个派生模板均为`DRAFT`，未生成发布执行快照、不参与自动选模、未获得独立浏览器验收。发布前须核对S0实际指派、施工计划审批、满意度时点、督导绑定和闭环部署；售前必须明确选择，不得直接发布为全维度通配的自动匹配模板。
- PRD 3.2的维护、割接、巡检由对应服务领域、CUT、SRV承载，不生成新的项目类别或冒充项目模板。

生成与校验：

```powershell
python scripts/generate_project_template_seed_pack.py
python scripts/generate_project_template_seed_pack.py --check
```

输出为`V336__project_template_accepted_seed_pack.sql`及场景JSON。新种子的ID范围为`993009900000`—`993009900011`；验收模板和业务视图保留来源ID。任何ID或自然键冲突均拒绝覆盖并回滚。生成器不连接数据库。

## 已有数据库

继续使用原`compose.yaml`和`sql/migrations`升级。V336先校验并插入新配置，再按租户1、ID、code、creator的精确名单停用15个旧示例；只改变模板根的状态和版本。不修改发布修订、冻结快照、项目或其他租户，不删除用户创建的模板。

历史迁移文件及原种子生成器保持不变，避免破坏已安装数据库的Flyway校验。不要用新库入口升级已有数据库，也不要用`repair`掩盖两条迁移路径的差异。

## 新数据库

先生成不含旧模板及其依赖项目数据的迁移目录：

```powershell
python scripts/prepare_fresh_project_template_migrations.py
python scripts/prepare_fresh_project_template_migrations.py --check
docker compose -f compose.yaml -f compose.fresh.yaml run --rm migrate
```

沿用开发环境已有的数据库连接配置。生成目录是被忽略的`.run/fresh-project-migrations`；每次新增迁移后重新生成。该数据库以后升级仍须使用相同的`compose.fresh.yaml`覆盖文件，不能切回历史升级路径。V0拒绝非空数据库；Flyway检查仍然开启。

新库路径仅对明确列出的旧示例迁移作处理：

- V54/55/56/59/61/62/73/75/79/162/207/208/209/331保留版本位置但不执行旧示例数据写入。
- V72/74/100/105/161保留平台菜单、字典、表单及适用结构，排除旧项目、模板及依赖商务数据。
- V160复用原迁移的10张表DDL与原子改名，不导入已移除的V72示例，也不调用专用于该示例的完整性断言；已有库的原断言保持不变。
- V289中两条改名已由完整V285执行。新库生成器先确认这两条语句与V285一致，再省略重复改名；保留版本表DDL。原V289不变。

其余迁移逐字保留。生成器不执行数据库清理或迁移，不修改历史文件。相关验证与限制见[验证记录](../../docs/generated/2026-09-21-project-template-seed-migration.md)。
