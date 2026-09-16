-- V46: 低代码流程绑定表增加任务回调配置字段
-- 用于 ProcessTaskCallbackListener 在 Flowable 任务事件（create/assignment/complete）触发绑定微流
-- JSON 格式: {nodeId: {onCreate: microflowCode, onAssign: microflowCode, onComplete: microflowCode}}

ALTER TABLE `low_process_binding`
    ADD COLUMN `task_callbacks` LONGTEXT NULL COMMENT '任务回调 JSON: {nodeId: {onCreate: microflowCode, onAssign: microflowCode, onComplete: microflowCode}}' AFTER `node_form_bindings`;
