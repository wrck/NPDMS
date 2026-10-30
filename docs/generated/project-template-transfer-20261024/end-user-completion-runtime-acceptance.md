# 最终用户自动完成修复：59286运行验收

日期：2026-10-24。需求方明确批准重启59286、加载修复包并使用admin验证自动完成、人员指派及无同步提示。

## 结果

- 只停止经监听端口和运行产物双重核验的旧PID9016；59286以PID43968启动，沿用local,dac及既有测试库连接。启动前核对源码指纹、产物SHA256和22项既有修复保护。应用包为e053ae3aac90444fb5a46799c6a20cfeae22b14bd526423ead3afe5a3a84af29，未操作59191或DAC进程。
- admin通过19086真实浏览器在专用验收项目84572新增外部最终用户“自动完成验收-20261024”，保存时间12:31:06。未点击任务提交、完成或阶段推进按钮。任务随后自动显示完成、进度100%，任务总数从1/16变为2/16；S0按冻结规则完成，S1/S2进入进行中。
- 数据库确认PRE_CONTACT为DONE、version=2；追加完成证据为executionMode=AUTOMATIC、evaluated_by=0。新增成员creator=1，对应admin；user_id仍为空、authorization_confirmed仍为0。原三条最终用户保留。
- 重新打开人员指派任务：完成100%，服务经理周文成、主责项目经理杨凯添及已确认授权记录正常展示；数据库PROJECT_ASSIGNMENT仍为DONE、version=2。未变更或重新指派这两位成员。
- 保存后的即时页面及完成后页面均不含“正在同步任务状态”；人员指派视图也不显示该提示。保留静默状态刷新及错误反馈。

## 原分支未暴露的原因

现场对比E:/AICoding/Projects/NPDMS的codex/domain-migration分支，ProjectAssignmentCompletionMapper.members没有authorization_confirmed过滤，文件最近提交为273621cee。当前分支的e21f02d2d迁移整改为该共享查询增加authorization_confirmed=b'1'，保护了人员指派，但遗漏ProjectEndUserCompletionService的外部最终用户消费者。

原最终用户单测模拟Mapper返回值，未经过真实SQL，因此未捕获查询过滤造成的回归。本次通过独立endUserMembers查询修复，并以真实MySQL测试同时证明外部最终用户可完成、未授权项目经理仍被排除。同步文字则来自PAGE保存后的轮询分支，人员指派使用其他刷新分支；按需求方要求移除文字，保留轮询。

## 证据

`.run/end-user-completion-20261024/`：runtime-before-restart.json、runtime-result.json、browser-after-save.txt、browser-end-user-result.txt/png、browser-assignment-result.txt/png；运行清单`.run/direct-test/latest-runtime.json`。此前9项后端、33项前端测试结果仍适用。

启动未就绪期间的一次新增表单请求失败，未保存数据；服务启动后重载并完成上述验收。未执行数据库迁移、直接改任务状态、覆盖冻结快照或删除既有成员。新增验收成员保留供复核。未提交Git。
