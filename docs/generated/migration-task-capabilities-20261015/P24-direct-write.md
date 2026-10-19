# P24 合同回款信息直写实施记录

新增独立DPPMS_CONTRACT_RECEIVABLE_DIRECT和dppms-receivables-direct模板GET入口。INT私有DirectReceivablePersistence、Row及专用Mapper/XML复制原已审核规则，不调用COM的preview/apply；原Owner、任务及历史保持。39原字段承接52正式字段，基线覆盖全部正式值；金额/比例精度、毫秒审计、历史账号精确关联、空合同号过滤和既有主数据关联保护保持。根读取沿已审核SQL的原id，不增加修改时间排序。

targeted-035529.log中实际XML事务4项通过：预览零写与完整金融/审计字段、更新需精确基线且保留当前关联、后置第二条约束失败回滚首条、空合同及权限/租户/版本保护。TaskEditor运行测试18项通过，真实Chromium组件选择通过，证据D:/CodexTaskArtifacts/project-detail-followup/p25-direct-20261016。组件使用API fixture，没有保存任务。

当前未打包部署、真实保存新任务或业务预览；无迁入、无DDL。上述离线通过不代表真实业务闭环。
