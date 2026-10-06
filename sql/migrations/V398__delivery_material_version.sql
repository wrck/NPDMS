-- Public material commands use their own CAS version; evidence anchors and history remain unchanged.
ALTER TABLE plt_delivery_material
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '公共材料操作版本；独立于文件版本及业务修订';
