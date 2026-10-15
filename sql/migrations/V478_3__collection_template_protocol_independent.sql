-- 命令模板与连接协议解耦；保留已有协议值作为历史信息，不修改命令和发布版本。
ALTER TABLE plt_collection_template
    MODIFY COLUMN protocol VARCHAR(16) NULL DEFAULT NULL COMMENT '历史协议信息；新模板不填写，执行协议由设备连接指定';
