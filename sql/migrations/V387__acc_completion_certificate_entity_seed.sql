-- V387: 完工证明实体字段示例种子（配合 V386 实体化列与设备子表）。
--
-- 覆盖维度：全要素草稿（含设备子表多行与双方签章）、部分限定待客户确认
-- （部分服务内容留空、无签章）。历史记录的 remark/content 信封不回填，
-- 由前端读取兼容，编辑保存后落实体列。
INSERT IGNORE INTO `acc_completion_certificate`
(id, project_id, code, name, certificate_no, customer_id, completion_date, service_type, engineer_user_id, engineer_name, engineer_contact, customer_unit, contract_no, item_arrival, item_install, item_cutover, item_training, item_docs, customer_sign_url, customer_sign_date, vendor_sign_url, vendor_sign_date, status, remark, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(30031, 1006, 'CERT-V387-001', '北京华盛办公网络安全部署项目', 'CERT-2026-V387-001', 1001, '2026-09-28', '工程实施', 1, '管理员', '18818260272', '北京华盛科技有限公司', 'HT-2026-006', '是', '是', '不涉及', '否', '是', '/api/file/cert-v387-001-customer-sign.png', '2026-09-28', '/api/file/cert-v387-001-vendor-sign.png', '2026-09-28', 0, 'V387 实体字段示例：全要素草稿', 1, 'admin', '2026-09-28 10:00:00', 'admin', '2026-09-28 10:00:00', b'0', 1),
(30032, 1006, 'CERT-V387-002', '北京华盛办公网络安全部署项目', 'CERT-2026-V387-002', 1001, '2026-09-29', '工程督导', 1, '管理员', '18818260272', '北京华盛科技有限公司', 'HT-2026-006', '是', '否', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 'V387 实体字段示例：部分限定待客户确认', 1, 'admin', '2026-09-29 10:00:00', 'admin', '2026-09-29 10:00:00', b'0', 1);

INSERT IGNORE INTO `acc_completion_certificate_device`
(id, certificate_id, device_type, device_model, quantity, sort, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
(90001, 30031, '核心交换机', 'S5735-L24T4S-A', 2, 0, 0, 'admin', '2026-09-28 10:00:00', 'admin', '2026-09-28 10:00:00', b'0', 1),
(90002, 30031, '防火墙', 'USG6000E-B03', 1, 1, 0, 'admin', '2026-09-28 10:00:00', 'admin', '2026-09-28 10:00:00', b'0', 1),
(90003, 30032, '入侵防御系统', 'NEIP-8000', 1, 0, 0, 'admin', '2026-09-29 10:00:00', 'admin', '2026-09-29 10:00:00', b'0', 1);
