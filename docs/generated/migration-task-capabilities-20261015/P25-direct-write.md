# P25 设备维保延期直写实施记录

新增独立DPPMS_WARRANTY_RENEWAL_DIRECT和dppms-warranty-renewals-direct模板GET入口。私有DirectWarrantyPersistence/Row及专用XML复制既有AST规则，保留原Owner、任务和历史。原十字段完整承接；参考年数转月数，实际起止日期保持，未知标志和日期精度损失拒绝，原有完整基线与乐观版本保护保持，正式ID独立生成。

实源元数据核对：fb_service.id是普通索引，无PRIMARY和auto_increment；原id可以跨SN重复，保留审核的原ID+SN组合身份，不把它简化成id读取。没有修改来源数据或原P23缓存方案。

targeted-035952.log实际XML事务4项通过，覆盖预览零写/组合身份、无变化重放、版本更新/创建审计保护/人工修改拒绝、后置约束失败整批回滚、租户和权限。TaskEditor运行18项通过；真实Chromium组件选择通过，使用API fixture且保存0任务，证据D:/CodexTaskArtifacts/project-detail-followup/p25-direct-20261016/browser/browser-result.json。

本轮曾将P25误对应为回款计划，targeted-035733.log的配置身份断言发现问题；多余计划实现及对应接口/页面分支已全部移除，原失败证据保留。实际P25实现与测试均为维保延期。当前未部署、保存新任务或真实预览，没有业务迁入或DDL。
