-- V38: 低代码组件元数据表 + 15 个预置组件

CREATE TABLE IF NOT EXISTS `low_component_meta` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(64)  NOT NULL COMMENT '组件名（注册 key）',
    `display_name` VARCHAR(128) NOT NULL,
    `category`     VARCHAR(32)  NOT NULL COMMENT '分类: SELECTOR/INPUT/DISPLAY/...',
    `icon`         VARCHAR(64)  NULL,
    `props_schema` LONGTEXT     NOT NULL COMMENT '属性 JSON Schema',
    `description`  VARCHAR(512) NULL,
    `builtin`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否内置组件',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码组件元数据';

-- 15 个预置组件元数据
INSERT INTO `low_component_meta` (`name`, `display_name`, `category`, `props_schema`, `description`, `builtin`) VALUES
('UserSelector',      '用户选择器',   'SELECTOR', '{"props":[{"key":"multiple","type":"boolean","default":false}]}', '对接系统用户 API', 1),
('DeptSelector',      '部门选择器',   'SELECTOR', '{"props":[{"key":"multiple","type":"boolean","default":false}]}', '对接系统部门 API', 1),
('DictSelect',        '数据字典下拉', 'SELECTOR', '{"props":[{"key":"dictCode","type":"string","required":true}]}', '根据 dictCode 加载字典', 1),
('FileUploader',      '文件上传',     'INPUT',    '{"props":[{"key":"accept","type":"string"},{"key":"maxSize","type":"number","default":10}]}', '对接 pms-file', 1),
('RichTextEditor',    '富文本编辑器', 'INPUT',    '{"props":[{"key":"height","type":"number","default":300}]}', '所见即所得富文本', 1),
('CodeEditor',        '代码编辑器',   'INPUT',    '{"props":[{"key":"language","type":"string","default":"javascript"}]}', '代码高亮编辑', 1),
('ColorPicker',       '颜色选择器',   'INPUT',    '{"props":[{"key":"showAlpha","type":"boolean","default":true}]}', 'RGBA 颜色选择', 1),
('TreeSelect',        '树形选择',     'SELECTOR', '{"props":[{"key":"data","type":"array"}]}', '树形数据选择', 1),
('DateRangePicker',   '日期范围',     'INPUT',    '{"props":[{"key":"format","type":"string","default":"YYYY-MM-DD"}]}', '日期范围选择', 1),
('NumberRangeInput',  '数字范围',     'INPUT',    '{"props":[{"key":"min","type":"number"},{"key":"max","type":"number"}]}', '数字区间输入', 1),
('AddressPicker',     '地址选择',     'SELECTOR', '{"props":[{"key":"level","type":"number","default":3}]}', '省市区联动', 1),
('BarcodeInput',      '条码扫描',     'INPUT',    '{"props":[{"key":"types","type":"array","default":["CODE_128","EAN_13"]}]}', '摄像头扫码', 1),
('SignaturePad',      '电子签名',     'INPUT',    '{"props":[{"key":"width","type":"number","default":400},{"key":"height","type":"number","default":200}]}', '手写签名', 1),
('ChartPreview',      '图表预览',     'DISPLAY',  '{"props":[{"key":"chartType","type":"string","default":"bar"}]}', 'echarts 图表', 1),
('QrcodeDisplay',     '二维码展示',   'DISPLAY',  '{"props":[{"key":"size","type":"number","default":128}]}', '生成二维码', 1);
