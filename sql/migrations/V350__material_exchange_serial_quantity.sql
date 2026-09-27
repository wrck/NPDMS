-- 换货申请设备行换货数量：按勾选的设备行填写数量；既有快照默认 1，不重写设备档案或历史申请。
ALTER TABLE imp_eng_material_exchange_serial
    ADD COLUMN quantity decimal(18, 2) NOT NULL DEFAULT 1 COMMENT '换货数量' AFTER equipment_id;
