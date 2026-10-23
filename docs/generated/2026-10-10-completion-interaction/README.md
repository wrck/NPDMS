# 完工证明交互与设备查看表格验证

需求依据：本会话需求方明确“完工证明的交互界面也要像满意度问卷一样进行调整”，并追加“查看设备类型也要用表格”。保留此前项目名称、客户单位各单独一行、隐藏编号、完工时间在签章前且新建默认当天，以及自动调查、一次签字两份提交和10项平均80分达标的已批准规则。

## 实施范围

ACC前端完工证明列表统一标题、筛选、条纹表格与只读抽屉；新增/编辑独立组件以 DemoV2 完工表格填写，继续复用原项目/工程师选择、设备聚合与手写签章控件。草稿保存和删除接入已有成套API，复用打印模板配置、记录绑定版式、PDF预览与PLT登记下载。满意度仍在推送时自动创建，无手动创建入口。原权限、状态机、驳回/归档、已确认历史及冻结内容保留。

查看设备使用设备类型、设备型号、数量三列表格，同时覆盖管理抽屉与客户核对页。当前项目名称仅用于展示，不替换原保存或冻结的证明内容。无新API、DDL、种子或服务重启。

## 真实浏览器结果

实际环境：宿主机前端 `http://localhost:19191`、后端59191，既有Compose `npdms-domain-test / npdms_domain_test`。使用真实Chromium登录和业务页面，无业务接口模拟；客户链接和登录凭据只在脚本内存使用。

- 证明30031：表格创建时默认2026-10-10；草稿无调查且绑定两份打印版式。改为2026-09-30保存、重开保持原日期。首次推送自动创建原表13题（10评分+购买意愿+2意见）；重推同一调查、旧链接不可用。客户一次签字上传和一次统一提交，得80分、阈值80、达标；证明状态2，客户重开只读。
- 管理抽屉：完工字段、同一手写签字、两份HTML均可见。通过记录绑定版式分别登记下载完工与满意度PDF，重开可见两份PDF。两份均为一页A4，字体、原表矩阵、答案、签字与确认时间已渲染检查。绑定的原打印模板内容保留，本次界面调整不改写已发布打印修订。
- 证明30032：真实表格保存防火墙/LOCAL-FW-01/2与交换机/LOCAL-SW-02/3两行；管理查看与客户核对页逐格回显一致。双窗口同版本编辑，首次保存成功，第二次真实服务端版本冲突失败；失败保留第二窗口填写内容且不覆盖首次保存。390px客户页面无整体横向溢出，完工表格可在局部横向滚动。验证后以原驳回动作撤销链接，刷新客户入口不可用。
- 原已签证明30025与检查前快照全部字段一致；上述浏览器路径页面异常数为0。

结果数据：`result.json`、`devices-result.json`。截图：`completion-table-form.png`（填写）、`completion-devices-detail.png`（设备查看）、`completion-devices-customer-mobile.png`（手机核对）、`completion-save-conflict.png`（并发保存失败保留输入）、`completion-saved-documents.png`（两份交付件）。PDF及渲染页：`completion.pdf`、`satisfaction.pdf`、`completion-pdf-page.png`、`satisfaction-pdf-page.png`。

## 针对性检查

原上下文30项与新保存/上下文/历史打印保护9项，共39项通过；本次4个前端文件ESLint无错误。后端未改动，复用此前52项针对性测试及本次真实业务路径验证。全项目类型检查存在其他模块错误，本次完工证明/设备查看文件无报错，不报告全项目检查通过；详见 `.run/completion-interaction-typecheck-final.log`。日志另见 `.run/completion-interaction-tests-final.log`、`.run/completion-interaction-eslint-final.log`、`.run/completion-interaction-browser.log`、`.run/completion-devices-browser.log`、`.run/completion-devices-reopen.log`。

所有验证记录为本地验收专用，未发送外部通知。修改未提交、未推送，不提升Feature整体完成或门禁状态。
