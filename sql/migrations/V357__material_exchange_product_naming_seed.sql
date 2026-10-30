-- 换货申请产品命名配套种子（V356 后列结构）：覆盖勾选清单行换货产品选择的关键组合维度。
-- 场景A：多物料去重拼接（主表 product_code 两值）+ 换货产品精确命中（快照组由产品信息填充）+ 未选留空草稿后补 + 无清单行引用旧快照形态。
-- 场景B：单值拼接 + 未选换货产品。
-- 申报退出已写入（product_name/product_model 有值）与审批进度维度由既有数据（30010/30013 等）承载，本迁移不重复。
-- 快照组条件 status='ACTIVE'：运行时产品停用则不参与（无匹配维度），种子不臆造停用产品取值。
-- 幂等：显式高段主键 + INSERT..SELECT 按真实引用关联 + ON DUPLICATE KEY UPDATE；creator 统一 material_exch_seed。

-- 1. 场景A 主表：项目 1010（PROJ-2026-010），多值拼接
INSERT INTO imp_eng_material_exchange
(id, tenant_id, project_id, code, name, exchange_type,
 product_code, product_name, product_model,
 quantity, unit, reason, crm_push_status,
 applicant_user_id, apply_time, status, version,
 creator, create_time, updater, update_time, deleted)
SELECT 9000000001, 1, p.id, 'ME-SEED-2026-010-MULTI', '换货产品选择种子（多值拼接）', 'INCOMPATIBLE',
 'ITEM-SEC-DEPLOY,ITEM-SEC-DEPLOY-2', NULL, NULL,
 4.00, '台', '<p>换货产品选择演示：两行物料去重拼接，换货产品可留空草稿后补。</p>', 'PENDING',
 1, NOW(3), 0, 0,
 'material_exch_seed', NOW(3), 'material_exch_seed', NOW(3), 0
FROM proj_project p WHERE p.id = 1010 AND p.deleted = b'0'
ON DUPLICATE KEY UPDATE product_code = VALUES(product_code), product_name = VALUES(product_name),
 product_model = VALUES(product_model), quantity = VALUES(quantity), reason = VALUES(reason),
 status = VALUES(status), updater = 'material_exch_seed', update_time = NOW(3), deleted = 0;

-- 2. 场景B 主表：项目 1002（PROJ-2026-002），单值未选
INSERT INTO imp_eng_material_exchange
(id, tenant_id, project_id, code, name, exchange_type,
 product_code, product_name, product_model,
 quantity, unit, reason, crm_push_status,
 applicant_user_id, apply_time, status, version,
 creator, create_time, updater, update_time, deleted)
SELECT 9000000002, 1, p.id, 'ME-SEED-2026-002-SINGLE', '换货产品选择种子（单值未选）', 'INCOMPATIBLE',
 'ITEM-SEC-DEPLOY', NULL, NULL,
 2.00, '台', '<p>换货产品选择演示：单行物料单值拼接，换货产品未选。</p>', 'PENDING',
 1, NOW(3), 0, 0,
 'material_exch_seed', NOW(3), 'material_exch_seed', NOW(3), 0
FROM proj_project p WHERE p.id = 1002 AND p.deleted = b'0'
ON DUPLICATE KEY UPDATE product_code = VALUES(product_code), product_name = VALUES(product_name),
 product_model = VALUES(product_model), quantity = VALUES(quantity), reason = VALUES(reason),
 status = VALUES(status), updater = 'material_exch_seed', update_time = NOW(3), deleted = 0;

-- 3. 场景A 子表行1：真实清单明细拆分行 + 换货产品精确命中 01100003（快照组按 ACTIVE 产品填充）
INSERT INTO imp_eng_material_exchange_serial
(id, tenant_id, exchange_id, product_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code,
 device_type_code, device_type_name, device_id, sn, product_name, product_code, product_model, contract_no,
 creator, create_time, updater, update_time, deleted)
SELECT 9000000011, 1, 9000000001, p.id, 1.00, 993109130002, d.delivery_scope_id, s.order_no, s.line_no, s.item_code,
 NULL, NULL, NULL, NULL, p.product_name, p.product_code, p.product_model, NULL,
 'material_exch_seed', NOW(3), 'material_exch_seed', NOW(3), 0
FROM com_delivery_scope_detail d
JOIN com_delivery_scope s ON s.id = d.delivery_scope_id AND s.deleted = b'0'
JOIN ast_product_official_info p ON p.product_code = '01100003' AND p.status = 'ACTIVE' AND p.deleted = b'0' AND p.tenant_id = 1
WHERE d.id = 993109130002 AND d.deleted = b'0'
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), quantity = VALUES(quantity),
 order_no = VALUES(order_no), line_no = VALUES(line_no), item_code = VALUES(item_code),
 product_name = VALUES(product_name), product_code = VALUES(product_code), product_model = VALUES(product_model),
 updater = 'material_exch_seed', update_time = NOW(3), deleted = 0;

-- 4. 场景A 子表行2：真实清单明细拆分行 + 未选换货产品（product_id NULL 快照组 NULL，草稿后补形态）
INSERT INTO imp_eng_material_exchange_serial
(id, tenant_id, exchange_id, product_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code,
 device_type_code, device_type_name, device_id, sn, product_name, product_code, product_model, contract_no,
 creator, create_time, updater, update_time, deleted)
SELECT 9000000012, 1, 9000000001, NULL, 1.00, 993109130003, d.delivery_scope_id, s.order_no, s.line_no, s.item_code,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'material_exch_seed', NOW(3), 'material_exch_seed', NOW(3), 0
FROM com_delivery_scope_detail d
JOIN com_delivery_scope s ON s.id = d.delivery_scope_id AND s.deleted = b'0'
WHERE d.id = 993109130003 AND d.deleted = b'0'
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), quantity = VALUES(quantity),
 order_no = VALUES(order_no), line_no = VALUES(line_no), item_code = VALUES(item_code),
 product_name = VALUES(product_name), product_code = VALUES(product_code), product_model = VALUES(product_model),
 updater = 'material_exch_seed', update_time = NOW(3), deleted = 0;

-- 5. 场景A 子表行3：无清单行引用旧快照形态 + 换货产品精确命中 01100006（第二物料演示多值拼接）
INSERT INTO imp_eng_material_exchange_serial
(id, tenant_id, exchange_id, product_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code,
 device_type_code, device_type_name, device_id, sn, product_name, product_code, product_model, contract_no,
 creator, create_time, updater, update_time, deleted)
SELECT 9000000013, 1, 9000000001, p.id, 2.00, NULL, NULL, '', '', 'ITEM-SEC-DEPLOY-2',
 NULL, NULL, NULL, NULL, p.product_name, p.product_code, p.product_model, NULL,
 'material_exch_seed', NOW(3), 'material_exch_seed', NOW(3), 0
FROM ast_product_official_info p
WHERE p.product_code = '01100006' AND p.status = 'ACTIVE' AND p.deleted = b'0' AND p.tenant_id = 1
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), quantity = VALUES(quantity),
 item_code = VALUES(item_code),
 product_name = VALUES(product_name), product_code = VALUES(product_code), product_model = VALUES(product_model),
 updater = 'material_exch_seed', update_time = NOW(3), deleted = 0;

-- 6. 场景B 子表行：真实清单明细拆分行 + 未选换货产品（单值场景）
INSERT INTO imp_eng_material_exchange_serial
(id, tenant_id, exchange_id, product_id, quantity, scope_detail_id, scope_id, order_no, line_no, item_code,
 device_type_code, device_type_name, device_id, sn, product_name, product_code, product_model, contract_no,
 creator, create_time, updater, update_time, deleted)
SELECT 9000000014, 1, 9000000002, NULL, 2.00, 993109130001, d.delivery_scope_id, s.order_no, s.line_no, s.item_code,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'material_exch_seed', NOW(3), 'material_exch_seed', NOW(3), 0
FROM com_delivery_scope_detail d
JOIN com_delivery_scope s ON s.id = d.delivery_scope_id AND s.deleted = b'0'
WHERE d.id = 993109130001 AND d.deleted = b'0'
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), quantity = VALUES(quantity),
 order_no = VALUES(order_no), line_no = VALUES(line_no), item_code = VALUES(item_code),
 product_name = VALUES(product_name), product_code = VALUES(product_code), product_model = VALUES(product_model),
 updater = 'material_exch_seed', update_time = NOW(3), deleted = 0;
