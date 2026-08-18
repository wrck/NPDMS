# F-PM02 Technical Plan

> Feature ID：`F-PM02`（对应 Spec：`features/F-PM02-project-tree-and-detail.md`）
> 文档状态：`PLANNED`（待评审后进入 T1）
> 规格基线：快照 @ `fd6728c`；需求矩阵 PM-02 Feature 列 → `F-PM02`
> 本计划不重新定义领域、权限或状态语义；业务规则以 F-PM02 Spec 第4节 BR-1～BR-8 为准。

## 1. 输入与边界回顾

- 需求：PM-02（PRD V1.7 4.2.2 / 4.3.2）；消费 F-PM01 已交付的 `proj_project` 树邻接字段与创建能力、F-PM03 模板匹配/实例化；
- 迁移契约：Project=`STRUCTURED`（历史 `pms_project` 数据迁移不在本 Feature，待 AI-MIG-000）；
- 存量勘察结论：
  - `proj_project` 已含 `parent_id/root_id/tree_path/tree_depth/tree_sort` 树邻接列（V57 落地），索引 `idx_proj_project_parent`/`idx_proj_project_path` 已就绪；
  - 缺业务层级、进度、权重字段（Spec §7 前向扩列）；
  - 旧链 `project-tree/index.vue`（基于已冻结 `pms_project`，下挂子项目/子树移动）+ `project-detail/index.vue`（顶部档案区+左导轨+右内容）为 PM-02 语义前实现，需冻结旧树写面并新建详情页；
  - 旧 `project-detail/index.vue` 被约 30 处旧页面以只读选择器消费，重构为独立详情页时须保持旧页面过渡可用。

## 2. 目标数据模型（V60）

命名遵循 ADR-0019（`proj_` 前缀、uk 含 `tenant_id`、字符串状态码、乐观锁 `version`）。

### 2.1 `proj_project` 前向扩列（复用 V57 已建树邻接列）

| 列 | 说明 |
|---|---|
| `business_level_code` | 业务层级标签编码（与结构层级 `tree_depth` 分离；字典 `pms_project_business_level`） |
| `business_level_name` | 业务层级标签名称 |
| `progress` | 项目进度百分比（`DECIMAL(5,2) NOT NULL DEFAULT 0`；来源属 PM-11，本 Feature 仅消费） |
| `aggregation_weight` | 相对直接父项目的权重（`DECIMAL(5,2) NULL`，NULL=等权） |
| `weight_source` | 权重来源（`DEFAULT_EQUAL`/`MANUAL`） |

### 2.2 树真值维护（领域层，单事务）

- 根项目：`parent_id=NULL`、`root_id=id`、`tree_path='/'`、`tree_depth=0`；
- 子项目：`root_id` 继承父项目、`tree_path=父tree_path + 父id + '/'`、`tree_depth=父tree_depth+1`；
- 子树移动：仅重建被移动子树的 `root_id`/`tree_path`/`tree_depth` 前缀（`tree_sort` 不变），同事务校验无环。

### 2.3 编码（ADR-0020）

- 根项目：`PJT` + 年份(4) + 流水(6)（F-PM01 已实现，`code_root_id=id`、`project_sequence=0`）；
- 子项目：`<根项目编码>-SP<流水>`，`code_root_id` 继承根项目、`project_sequence>0` 不回收；流水自 `proj_project_code_sequence`（`code_namespace='ROOT:<code_root_id>'`）行锁原子递增。

### 2.4 权重与进度汇总

- 权重：`aggregation_weight` 相对直接父项目；`NULL`=等权，读时按直接子项目数归一化（`100/n`）；
- 校验：同一父下全部直接子项目归一化权重合计必须 =100%，否则拒绝生效（BR-7）；
- 汇总：`父进度 = Σ(直接子项目.progress × 归一化权重)`；设备数量不参与。

### 2.5 字典与菜单（V60 内登记）

- 字典：`pms_project_business_level`（示例值，如 `LEVEL_REGION` 大区 / `LEVEL_OFFICE` 办事处 / `LEVEL_NODE` 节点，非权威来源映射，可扩展）；
- 菜单（18071 段，V52 占用至 18066、F-PM01 占用 18067~18070）：18071 项目详情（页面，挂 19261 项目管理组，非导航直链，由列表"详情"跳转）+ 按钮 18072 子树移动 `pms:project:update`；查询/创建复用 `pms:project:query/create`。

## 3. API 契约（admin 装配 `/pms` 前缀，复数新路由扩展）

| 方法与路径 | 语义 | 权限 |
|---|---|---|
| `POST /pms/projects`（扩展 `parentId`） | 手工创建：`parentId` 空=根项目；非空=下挂子项目（继承父模板+关键属性，ADR-0020 子编码） | `pms:project:create` |
| `GET /pms/projects/{id}/children` | 直接下级（按 `tree_sort` 排序，按需加载） | `pms:project:query` |
| `GET /pms/projects/{id}/descendants` | 全部后代（`tree_path` 前缀） | `pms:project:query` |
| `GET /pms/projects/{id}/ancestors` | 完整上级链（`tree_path` 解析，根→父） | `pms:project:query` |
| `GET /pms/projects/actions/by-business-level` | 指定业务层级查询（`businessLevelCode` 参数） | `pms:project:query` |
| `POST /pms/projects/{id}/actions/move` | 子树移动（`newParentId`；校验存在/同租户/非自身/非后代） | `pms:project:update` |
| `GET /pms/projects/{id}/progress` | 进度汇总（直接子项目进度列表 + 归一化权重 + 汇总进度） | `pms:project:query` |

- 树查询均先执行租户过滤；默认按需加载直接下级，展开时再加载下一层；
- 错误码：新增 `PROJECT_MOVE_CYCLE`（循环引用）、`PROJECT_MOVE_INVALID_PARENT`（父项目不存在/跨租户）、`PROJECT_WEIGHT_SUM_INVALID`（权重合计≠100%）等；沿用 16 分册错误分类。

## 4. 存量冻结（代码收敛）

- 旧 `project-tree/index.vue`：移除"下挂子项目/子树移动"写入口（旧链基于 `pms_project` 写面冻结）；保留树只读展示过渡或隐藏（参照 V58 先例）；旧链 `ProjectTreeController` 写端点退役；
- 旧 `project-detail/index.vue`：保持只读过渡（约 30 处旧页面选择器依赖），新详情页以新路由承载，不与旧页冲突；
- 守卫扩展（`validate_implementation_baseline_inventory.py`）：新增 `RETIRED_PROJECT_TREE_WRITE_PATTERNS`，匹配旧树写路由/权限（T1 红、T5 绿）。

## 5. 新前端

- `views/pms/project/project-detail/index.vue`（重建为独立详情页，参考旧 project-detail 布局）：
  - 顶部项目档案区（编码 badge + 名称 + 状态 + 四维 + 模板绑定 + 返回列表）；
  - 左侧导轨：项目概览（基本信息/实例五要素/成员区间）/ 项目树（直接下级按需加载 + 下挂子项目 + 子树移动入口）/ 进度汇总（直接子项目进度列表 + 汇总）；
  - 右侧内容区按导轨选择渲染；
- 列表页（F-PM01 `projects/index.vue`）："详情"按钮由打开抽屉改为跳转新详情页；
- 旧 `project-detail/index.vue` 保留只读过渡（供约 30 处旧页面），新详情页用新路由。

## 6. 任务分解（TDD：失败测试→最小实现→重构→验证）

| # | 任务 | 产出 | 验证 |
|---|---|---|---|
| T1 | 守卫先行：RETIRED_PROJECT_TREE_WRITE_PATTERNS 用例（红） | 测试更新 | unittest 红 |
| T2 | V60 DDL（proj_project 扩列 + 字典 + 菜单） | 迁移文件 | Flyway 本地库执行、迁移测试 |
| T3 | 领域层：树邻接维护（`ProjectTreeRules` 无环/路径重建）、子编码序号、权重归一化、进度汇总 | domain+service 包 | 单测：BR-1~BR-7 |
| T4 | Controller + VO + 树查询/移动/进度/下挂子项目 + 错误码 + 权限注解 | controller 包 | API 契约/权限拒绝测试 |
| T5 | 存量冻结：旧 project-tree 写面收敛 | 代码修改 | 编译+守卫绿+残留 grep |
| T6 | 新前端（独立详情页 + 项目树 + 进度汇总；列表详情跳转） | vue/ts | 类型检查+构建 |
| T7 | 集成验证：`mvn -pl pms-module-project -am test` + 全脚本套件 + 校验器 | 全绿 | CI 本地等价 |
| T8 | 真实浏览器 UI 验收（详情页/树按需加载/下挂子项目/子树移动/进度汇总，逐菜单走查截图） | 验收记录 | Spec 第5节逐条 |
| T9 | 清单/追溯回写：inventory 登记 `ProjectTreeAndDetail`；需求矩阵 PM-02 Feature 列 → `F-PM02-IMPLEMENTED`；示例数据核验 | 清单+矩阵 | 校验器 PASS |

每任务完成形成一次聚焦提交。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 树缓存重建与并发移动导致不一致 | 移动/下挂单事务重建子树缓存；`tree_path` 前缀匹配只更新受影响子树 |
| 权重归一化口径漂移 | 归一化规则集中在 `ProjectTreeRules`，等权/手动统一折算 |
| 旧 project-detail 与新详情页路由冲突 | 新详情页用新路由（18071），旧页保留只读过渡 |
| 子项目编码并发分配冲突 | 复用 F-PM01 序列表行锁 + uk 兜底，失败重试一次 |
| 菜单 ID 冲突 | 落笔前 `git grep '1807[0-9]'` 核对：18071+ 空闲 |

## 8. 完成定义（DoD）

1. Spec 第5节验收标准全部通过（含真实浏览器走查与组合覆盖）；
2. T1～T7 自动化验证全绿；守卫无残留命中；
3. V60 本地 MySQL 8.4 可重复执行；
4. 清单与需求矩阵回写完成，`F-PM02` → `IMPLEMENTED`；
5. 工程链 DoD 第8条（初始化数据）：字典/菜单种子落 V60；示例子项目数据覆盖关键组合（多级深度/业务层级复用/权重等权兜底+手动100%+合计≠100%被拒/无环移动阻断/四类查询），以幂等种子迁移补充（creator='seed'、高段 ID）。
