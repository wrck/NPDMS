# Phase 3 Owner实际证据提交

本目录只保存按环境或发布批次版本化的实际提交及其受控证据引用。空白模板位于`../evidence-packet-templates/`。

建议路径：

```text
submissions/
  P3-E01/
    <environment-or-release>-<capturedAt>.json
  ...
```

禁止覆盖旧提交；整改或复验必须创建新版本。生产秘密、连接串和敏感拓扑正文不得进入Git。

P3-E09当前DDL、逐项裁决、隔离执行证据和整体一致性独立复审均由正式制品保存，状态为`MODEL_BASELINE_READY`。不得在本目录创建四角色附件、OA/电子签名记录、独立批准JSON或双确认提交。只有Release包含历史数据迁移或数据切换时才启用`AI-MIG-000`，须在Release前达到`VERIFIED`并在批准窗口内执行；普通功能Release为`NOT_APPLICABLE`。届时按真实批次保存必要运行证据。
