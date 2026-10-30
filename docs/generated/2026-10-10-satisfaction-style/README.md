# 完工证明与满意度调查本地验收（2026-10-10）

当前结果：完工证明保留原界面；满意度填写采用需求方截图及 `需求/项目交付/项目交付页面数据DemoV2.html` 页面17的原表。调查随完工证明首次推送自动创建，两页一起外发，一次手写签字统一提交。10项等权平均，满分100、80分及以上达标；购买意愿与两项意见不计分。

## 最终行为

- 保存完工证明草稿无需选择或创建调查；满意度管理入口没有手动创建按钮。
- 首次推送使用新发布的 `COMPLETION_SATISFACTION_STANDARD` 原表，冻结13题及证明版本；重推复用该调查，撤销旧链接。原表不可用时失败，不改用其他示例。
- 评分矩阵保留工程质量3项、产品质量2项、服务质量5项，以及100/80/60/40/0五档评分；购买意愿提供“是/否/其他”及说明，两项意见按原表填写。
- 统一提交保存同一次规范签字、答卷、完工确认及两份不可变HTML交付文件；成功后重开客户链接只读显示原答案、签字和时间。
- 满意度管理参照现场培训提供详情、历史、打印版式与受控下载。正式PDF先登记交付件再下载，重开详情能看到两份文件。原证明查看回显相同客户签字。
- 驳回或作废同时撤销客户授权；已确认或归档事实受保护，整改追加轮次而不覆盖首次完工确认。

## 当前本地环境

前端 `http://localhost:19191`，后端59191。Compose项目 `npdms-domain-test`，MySQL24306、Redis24379，数据库 `npdms_domain_test`。V427～V430仅对本任务执行前向迁移，迁移及校验成功；未执行其他待迁移文件。原后台配置与其他模块保留，以本任务选择性增量包 `.run/completion-workflow-verified-server.jar` 重启59191。

V430新增原表模板及发布修订，采用高段ID和 `seed_completion_confirmation` 标记；没有更新既有示例模板、已发布修订、冻结问卷、答卷或结果。用户最终裁决取代此前“唯一任意已发布模板”和手动选择的实施假设。

## 真实浏览器闭环

使用原登录和实际接口、实际MySQL及PLT文件服务，无接口模拟。项目为本地既有验收项目992203060005，验证记录与原有历史分开。

正向记录30027（`PJT2026000003-WZ-002`）：

- 原完工界面新增草稿时无调查；首次推送自动创建任务2108761551454253057，重推仍为同一任务，旧客户链接失效。
- 客户填写10项80分、其他购买意愿和两项意见；仅一次签字初始化/上传、一次答卷POST，Response及Result为2108761580071989249，得分80、阈值80、达标，证明状态2、版本3。
- 关闭后重开客户链接及管理详情，冻结答案和同一签字保存；原证明查看仍为原布局、只读展示并回显规范签字。两份HTML和分别登记下载的PDF可见。
- PDF实际生成、登记、下载和重新打开列表均通过。最终文件各一页A4，使用Poppler渲染检查，类别文字、10项选中评分、意见、签字和日期完整，无裁切。

负向记录30029：390px手机浏览器评分矩阵可横向滚动，页面无横向溢出。未勾选统一确认、缺必填评分、缺签字分别阻止提交，未发起签字上传或答卷提交。管理端通过原证明“驳回”操作变为状态4；已打开客户链接的签字初始化被服务端拒绝，刷新显示“问卷暂不可用”，无答卷提交。

已有已签记录30025在正向、PDF和负向检查后均与检查前快照一致。上述流程无未处理页面错误。

## 可查看的最终证据

- [正向结果](workflow-result.json)、[负向结果](workflow-negative-result.json)
- [完工证明最终PDF](workflow-completion-final.pdf)、[满意度最终PDF](workflow-satisfaction-final.pdf)
- [完工PDF渲染](pdf-completion-final-1.png)、[满意度PDF渲染](pdf-satisfaction-final.png)
- [原完工界面查看](workflow-original-completion-saved.png)、[原表填写](workflow-survey-original-matrix.png)、[两份文件已保存](workflow-both-documents-saved.png)
- [客户统一提交](workflow-joint-submitted.png)、[重开只读](workflow-reopen-readonly.png)、[手机原表](workflow-survey-mobile.png)、[驳回后客户页](workflow-rejected-customer.png)

`.run/completion-workflow-browser-live.log`、`completion-workflow-pdf-final.log`、`completion-workflow-negative-live.log`记录实际运行；`completion-workflow-migration-final.log`记录迁移与校验返回0。证据未保存客户Token或登录凭据。

## 验证边界

- 后端7个针对性测试类52项通过，含实际原表计分（80达标、78不达标）、租户/项目授权、状态与版本冲突、签字及双文档失败回滚、重试幂等、重推、整改历史与驳回授权撤销。
- 前端4个针对性文件41项通过、1项既有失败：`retains the existing signature-file upload path`。已在HEAD原页面和原测试复现；原实现已只支持手写签字，该测试仍期望另一种签字路径。本次未删除失败测试或恢复未经要求的签字方式。
- 本次文件ESLint无错误，范围内差异检查通过。全项目vue-tsc仍有其他模块错误，本次完工/满意度模块无报错，不能宣称全项目类型检查通过。
- 本地真实正向、负向及文件闭环已验证；未进行生产部署、全仓测试或Feature整体Done裁决，保持 `IN_PROGRESS / NOT_ESTABLISHED`。代码未提交、未推送。

本目录早期 `desktop.png`、`mobile.png`、`joint-*.png` 是模拟接口的样式/交互检查；`live-*` 是更早阶段记录30025的真实闭环；早期PDF是打印排版迭代产物。它们不替代上述最终 `workflow-*` 证据，也未用于覆写原有业务历史。
