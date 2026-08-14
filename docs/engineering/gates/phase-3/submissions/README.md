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

P3-E09已通过独立复审：DDL、逐项裁决和隔离执行证据由正式制品及Git基线提交保存。不得在本目录创建四角色附件、OA/电子签名记录、独立批准JSON或双确认提交。`MODEL_BASELINE_READY`只解除`DATA_MODEL_BASELINE`阻断；`AI-MIG-000`、历史数据迁移和数据切换继续`OPEN`，未经真实批次验证不得执行，届时按该批次保存必要运行证据。
