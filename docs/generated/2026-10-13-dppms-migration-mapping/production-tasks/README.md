# 27项迁移任务及新验证库配置

37份原来源组件收敛为27项。21份INT配置已保存到 `npdms_reverify_20261014` 并停用；另6项沿用材料采集/领域Owner，完整INT入口缺口保留。初次配置保存时未执行预览或迁移；随后已执行隔离验证库预览，见[本轮预览报告](preview-20261015.md)。业务迁移写入0，生产准备及Owner缺口保留。

## 阅读入口

- [当前修复预览报告](preview-repair-20261015.md)
- [正式前置依赖矩阵](prerequisite-dependencies.md)

- [任务目录及完整复核](../../../superpowers/plans/2026-10-14-dppms-production-migration.md)
- [有效项目范围及旧SQL证据](project-scope.md)
- [共同处理规则](processing-rules.md)
- [结构化索引](manifest.json)
- [原来源/目标结构](schema.json)：原71来源表、43目标表的2026-10-14证据。
- [43份Owner Mapper证据](owner-sql/README.md)
- [初次配置保存验证](verification.json)

原SQL、参数、逐字段映射及完整目标结构保留于configurations/tasks/sql；新保存配置单独位于runtime-configurations，不能将旧基线直接当成新运行配置。

## 当前任务入口

| 当前任务 | 来源组件 | 当前配置 | 生产准备状态 |
| --- | --- | --- | --- |
| [P01 公司、部门与主管关联](tasks/P01.md) | P01、P02、P03 | [P01.json](runtime-configurations/P01.json) | CONFIGURATION_SAVED_EXECUTION_PENDING |
| [P04 账号与任职资料](tasks/P04.md) | P04 | [P04.json](runtime-configurations/P04.json) | ADAPT_FIRST_IMPORT |
| [P05 角色定义及来源角色绑定](tasks/P05.md) | P05 | [P05.json](runtime-configurations/P05.json) | ADAPT_FIRST_IMPORT |
| [P06 用户角色关系](tasks/P06.md) | P06 | [P06.json](runtime-configurations/P06.json) | REUSE_AND_REVALIDATE |
| [P07 用户公司与部门范围](tasks/P07.md) | P07 | [P07.json](runtime-configurations/P07.json) | REUSE_AND_REVALIDATE |
| [P08 基础字典类型与字典项](tasks/P08.md) | P08、P09 | [P08.json](runtime-configurations/P08.json) | CONFIGURATION_SAVED_EXECUTION_PENDING |
| [P10 CRM客户主档](tasks/P10.md) | P10 | [P10.json](runtime-configurations/P10.json) | REUSE_AND_REVALIDATE |
| [P11 CRM正式产品目录](tasks/P11.md) | P11 | [P11.json](runtime-configurations/P11.json) | OWNER_ENTRY_REQUIRED |
| [P12 DPPMS缺失产品补齐与编码关联](tasks/P12.md) | P12 | [P12.json](runtime-configurations/P12.json) | REUSE_AND_REVALIDATE |
| [P13 CRM执行单](tasks/P13.md) | P13 | [P13.json](runtime-configurations/P13.json) | REUSE_AND_REVALIDATE |
| [P14 销售订单与订单行](tasks/P14.md) | P14 | [P14.json](runtime-configurations/P14.json) | ADAPT_FIRST_IMPORT |
| [P15 正式合同、发货合同引用与发货事实](tasks/P15.md) | P15、P26、P27、P28、P29 | [P15.json](runtime-configurations/P15.json) | CONSOLIDATED_ENTRY_PENDING |
| [P16 有效项目主档、来源身份与基础最终客户资料](tasks/P16.md) | P16、P17 | [P16.json](runtime-configurations/P16.json) | CONFIGURATION_SAVED_EXECUTION_PENDING |
| [P18 项目合同关系及正式商务关联](tasks/P18.md) | P18 | [P18.json](runtime-configurations/P18.json) | REUSE_AND_REVALIDATE |
| [P19 项目相关方与发货/执行状态资料](tasks/P19.md) | P19 | [P19.json](runtime-configurations/P19.json) | ADAPT_FIRST_IMPORT |
| [P20 项目真实产品](tasks/P20.md) | P20 | [P20.json](runtime-configurations/P20.json) | REUSE_AND_REVALIDATE |
| [P21 项目租赁产品与产品配置](tasks/P21.md) | P21 | [P21.json](runtime-configurations/P21.json) | REUSE_AND_REVALIDATE |
| [P22 项目产品实施范围](tasks/P22.md) | P22 | [P22.json](runtime-configurations/P22.json) | ADAPT_FIRST_IMPORT |
| [P23 合同回款计划](tasks/P23.md) | P23 | [P23.json](runtime-configurations/P23.json) | REUSE_AND_REVALIDATE |
| [P24 合同实际回款信息](tasks/P24.md) | P24 | [P24.json](runtime-configurations/P24.json) | REUSE_AND_REVALIDATE |
| [P25 设备逐次续保记录](tasks/P25.md) | P25 | [P25.json](runtime-configurations/P25.json) | REUSE_AND_REVALIDATE |
| [P30 设备主档及项目归属衔接](tasks/P30.md) | P30 | 已有材料采集/Owner；全量INT入口待补 | TASK_ENTRY_MISSING |
| [P31 项目成员历史、历史联系人与获准授权](tasks/P31.md) | P31、P32 | 已有材料采集/Owner；全量INT入口待补 | EXISTING_COMBINED_COLLECTOR_FULL_ENTRY_PENDING |
| [P33 普通安装历史及获准当前位置](tasks/P33.md) | P33 | 已有材料采集/Owner；全量INT入口待补 | TASK_ENTRY_MISSING |
| [P34 出厂软件版本记录](tasks/P34.md) | P34 | 已有材料采集/Owner；全量INT入口待补 | TASK_ENTRY_MISSING |
| [P35 软件变更批次与设备版本历史](tasks/P35.md) | P35、P36 | 已有材料采集/Owner；全量INT入口待补 | EXISTING_COMBINED_COLLECTOR_FULL_ENTRY_PENDING |
| [P37 设备许可历史](tasks/P37.md) | P37 | 已有材料采集/Owner；全量INT入口待补 | TASK_ENTRY_MISSING |

## 不再独立建任务的原编号

| 原组件 | 当前归属 |
| --- | --- |
| [P02](tasks/P02.md) | [P01](tasks/P01.md) |
| [P03](tasks/P03.md) | [P01](tasks/P01.md) |
| [P09](tasks/P09.md) | [P08](tasks/P08.md) |
| [P17](tasks/P17.md) | [P16](tasks/P16.md) |
| [P26](tasks/P26.md) | [P15](tasks/P15.md) |
| [P27](tasks/P27.md) | [P15](tasks/P15.md) |
| [P28](tasks/P28.md) | [P15](tasks/P15.md) |
| [P29](tasks/P29.md) | [P15](tasks/P15.md) |
| [P32](tasks/P32.md) | [P31](tasks/P31.md) |
| [P36](tasks/P36.md) | [P35](tasks/P35.md) |

## 实现及验证边界

P01/P04/P05/P08/P15在SQL中合并来源并按业务身份归组；P16一次MAX产出主档和有效旧ID；P06/P07复用前序成功来源证据，缺证据阻断；P31/P35沿用已有一次采集及组合Owner，不新增重复任务。
P12已消除revision/标签扩展依赖并完成真实预览；P16基础最终客户、P31角色60历史Owner、P33首次迁入、COM/AST正式Owner等其他缺口保持。当前修复结果见[修复预览报告](preview-repair-20261015.md)，旧预览和初次配置保存证据保留。
