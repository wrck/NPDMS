# 本机 DAC 原任务恢复验收

2026-10-22，需求方已批准修复、部署及59191重启。

- 根因：执行票据复核设备范围时误用网页登录用户，导致本机DAC仅携任务票据时收到“项目不存在”。现沿已验证任务传递发起人，保留租户、项目、设备、凭据、状态和票据校验。
- 授权票据在无网页登录Authorization时通过。真实浏览器点击原历史任务“继续下发”，任务完成并回传，原日志可查看。
- 原任务：`f7dee74b-f59b-46b8-b2b1-2c7cce83b73d`；执行记录：`2113284136744345601`；collection：`3b78d35f-8e89-4ba4-8d40-a9f4a607cfd4`。
- 重复请求两次均`existing=true`、同一collection；执行历史仍1条，原target=3、命令块1个、resultVersion=1、fileVersionId=990。未点击“重新执行”，未创建新任务。
- 定向测试：平台/项目/资产/集成28项、DAC6项、前端21项通过；DAC重复提交测试确认命令执行器仅调用1次。
- 发布：`.run/build/releases/211bce1d-a51e-45b8-b1ea-459c665992ba/manifest.json`；后端PID20924，local,dac；健康检查通过。本机DAC安装包和服务未变。
- 机器证据：`.run/dac-original-resume-evidence.json`。正式任务记录：`tasks/features/F-INT-012.md`。
