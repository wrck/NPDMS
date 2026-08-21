# Phase 3 Owner证据包模板

本目录由`scripts/generate_phase3_evidence_packets.py`生成，保存P3-E01～E07、P3-E09的空白证据包，不保存已验证生产事实。

## 使用方式

1. Owner复制对应模板到`../submissions/<证据编号>/<环境或发布批次>-<时间>.json`，不得直接填写或覆盖本目录模板。
2. 保留ADR-0004预填的`directionDecision`、`directionStatus`和`chosenDirection`；如方向需要改变，必须先新增替代ADR。
3. 填写全部空值，`evidenceRefs`只登记受控证据引用；不得写入密码、Token、私钥、连接串或内部敏感拓扑正文。
4. 将状态改为`EVIDENCE_SUBMITTED`，执行：

```powershell
py -3 -B scripts/validate_phase3_evidence_submission.py <提交文件>
```

5. 独立复核人确认事实、引用和验收断言后填写`reviewOwner`、`verificationResult=PASS`并改为`VERIFIED`，再次运行校验。
6. 只有复核通过的提交才能用于更新`../phase3-evidence-register.json`；方向确认本身不能关闭Gate。

## 模板防漂移

```powershell
py -3 -B scripts/generate_phase3_evidence_packets.py --check
```

该命令只检查本目录模板，不检查或覆盖`../submissions/`中的Owner实际提交。
