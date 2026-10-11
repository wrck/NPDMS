# 完工证明、满意度调查客户手机端适配验证

需求来源：2026-10-10需求方指出当前手机端不利于客户填写和确认，要求完工证明、满意度调查表适配手机。

适配现有客户受控入口，保持原冻结题目、评分、签字文件、保存和联合提交契约。900px及以下采用逐题评分、纵向完工信息及固定底部操作；大屏保留DemoV2评价矩阵。设备仍显示名称、数量两列。

## 复现及修改

- 390px真实触屏Chromium中，原评分表可视宽352px、内容宽900px，只能直接看到最左侧评分。适配后可视及内容同为358px，全部五档选项在当前宽度内。
- 工程字段按标签/内容纵向排布，服务内容与设备名称可以自然换行，7类40件不丢失。购买意愿、其他说明、两项意见在当前屏幕内填写。
- 评分选项同时显示文字和分数，触控范围不小于44px；选中状态及已评价数量即时回显。原100/80/60/40/0编码和10项平均分、80分达标规则未改。
- 客户签字采用原生控件的可选竖屏显示，默认关闭，仅本入口启用；保留PNG和坐标转换。画布、重置及确认不旋转，预览比例适合核对。提交锁定及已提交时显式禁用签字，已提交不显示删除入口。
- 本次不修改后端、数据库、打印组件或已生成交付件；不重启应用。

## 真实浏览器闭环

环境：宿主机前端19191、后端59191，项目 `992203060198`。通过真实登录及原生新增/推送入口创建独立本地验证记录30039、30040，仅修改本次自建记录。客户资料使用本地验收文本，未保存客户链接Token或登录凭据。

30039完成首次完整触屏流程；最终修订后用30040复验签字显示与只读控制。两份记录均10项选80分、平均80分、达标，证明最终状态2。每次分别只上传一次签字、调用一次联合提交；已签事实保留，不删除或驳回已签记录。

最终复验覆盖：

| 浏览器尺寸 | 验证结果 |
| --- | --- |
| 320×568 | 五档选项触控范围至少44px，7类设备两列可见，无横向溢出，竖屏签字动作在屏内 |
| 390×844 | 全部评分、购买意愿、意见可填写，切换页面保留答案；真实触屏绘制签字并一次联合提交 |
| 768×1024 | 五档可点选，设备、意见及底部动作在可视宽度内，保留原答案 |
| 844×390 | 横屏选项和签字动作在屏内，页面切换保留答案 |
| 1440×1000 | 仍为原8列评价矩阵，冻结编码和已填写答案不变 |

未勾选联合确认、缺少评分或缺少签字均零上传、零提交。实际触屏手写生成PNG后，通过原生受控上传及提交接口完成两份表单；重开同一入口，原评分、意见与同一签字保留，所有评分禁用，签字无删除入口且无提交操作。页面异常为空。

最终复验前后只读API快照比较30025/30031/30032/30034/30036/30037/30038/30039一致；旧历史不改写。原生成HTML/PDF不重新生成或覆盖。

## 针对性检查与边界

- `QuestionnaireTable.runtime.spec.ts`和`questionnaire.runtime.spec.ts`共9项：8项通过；原有`retains the existing signature-file upload path`失败。修改前已复现同一失败；保留测试，未降低校验或宣称全套通过。
- 修改的3个Vue组件与SignaturePad.ts的ESLint无错误，保留1项原生`signaturePad`注册名称警告；范围差异检查通过。
- 本次未重复全项目类型检查、后端测试或构建；浏览器验收为真实Chromium的触屏及手机尺寸模拟。

## 证据与执行

- [原手机评分表](before-survey-390.png)
- [适配后的手机评分界面](after-survey-390.png)
- [最终完工信息](final-completion-390.png)
- [最终触屏签字](final-touch-signature.png)
- [最终签字核对与联合确认](final-confirmation-390.png)
- [联合提交结果](final-submitted-390.png)
- [提交后只读回显](final-readonly-390.png)
- [大屏原评价矩阵](final-desktop-matrix.png)
- [改动前结果](before-result.json)
- [最终验证结果](final-result.json)

脚本：`.run/completion-mobile-browser.py`；日志：`.run/completion-mobile-before-tests.log`、`.run/completion-mobile-tests.log`、`.run/completion-mobile-lint.log`。

测试命令：

```powershell
pnpm.cmd exec vitest run --config vitest.pms-file.config.ts src/views/pms/acceptance/satisfaction/QuestionnaireTable.runtime.spec.ts src/views/pms/acceptance/satisfaction/questionnaire.runtime.spec.ts
```

最终浏览器命令（已有已签结果后不得再次以该模式推送）：

```powershell
& 'D:/常规软件/python/python.exe' '.run/completion-mobile-browser.py' final
```

修改未提交、未推送；本回执不提升Feature整体实施状态。
