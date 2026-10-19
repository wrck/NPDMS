# P10 CRM客户直写实施记录

新增独立CRM_CUSTOMER_DIRECT及GET /api/v1/pms/integration/templates/crm-customers-direct，保留原CRM模板和Owner实现。INT私有持久化复制既有CRM客户承接规则，通过专用XML写客户和来源映射；沿用统一任务、运行、租户、权限、配置版本、绑定和回执。

来源沿用DPtech_v_account的AccountId原UUID，重复业务码按正式TRIM口径整源检测，避免跨页遗漏。保留源版本早于当前不覆盖、同版本不同载荷冲突、新版本乐观更新、ERP允许显式清空以及平台字段不覆盖的规则；不得接管任意同码客户。客户与CRM映射同事务回滚。

验证：targeted-032418.log中实际XML事务5项及实际H2 MSSQL源查询1项通过，含后置映射约束失败回滚、缺ERP列拒绝、租户及版本/字段保护。前端14项当时通过；真实Chromium组件选择通过，API fixture且未保存任务。证据目录D:/CodexTaskArtifacts/project-detail-followup/p10-direct-20261016。

当前未打包、部署、保存新任务或真实预览。实际隔离目标缺cus_customer_master.erp_customer_code，不能声称预览可通过；本轮无DDL、无业务迁入。
