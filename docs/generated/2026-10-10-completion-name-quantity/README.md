# 完工证明设备名称、数量两列验证

需求来源：2026-10-10需求方明确设备名称与项目设备清单一致，完工证明只需要名称和数量，取消单列型号。

本次只调整ACC前端设备表：新增/编辑表单、管理只读表、客户核对页和Word V2打印均显示“设备名称、数量”。继续使用清单共享来源的产品名称及设备数量；API和已有保存数据中的型号字段保留。已保存版式快照、已生成HTML/PDF和已签历史不改写，无后端变更、迁移或重启。

## 真实运行验证

环境：宿主机前端19191、后端59191；项目 `992203060198`。原生设备来源共7类、40件。

| 设备名称 | 数量 |
| --- | ---: |
| DPtech FW1000-TE-C 运营商级双电源AC主机 | 4 |
| DPtech SFP+万兆光模块,单模,(1310nm,10km,LC) | 16 |
| DPtech 1端口40G以太网光接口模块 (QSFP) | 8 |
| DPtech 4端口万兆以太网光接口模块(SFP+) | 4 |
| DPtech 4端口千兆以太网光接口模块(SFP) | 4 |
| DPtech FW1000-TE-C 双电源DC主机 | 2 |
| DPtech FW1000-TE-C 双电源AC主机 | 2 |

真实Chromium登录后新建独立验证草稿30038，检查默认回填名称和数量，保存、重开、再保存后与保存内容一致，隐藏型号数据保留。管理只读表和Word预览的设备表均为两列。通过本次验证记录的原生推送链接，以390px匿名客户页面检查两列、全部7行及页面无横向溢出；未签字或提交答卷。

验证结束后，通过原生驳回动作撤销30038的客户链接；原生API复核最终状态4。30025/30031/30032/30034/30036/30037仅只读复核，复核前后数据一致。运行报告不保存登录凭据或客户链接Token；默认值截图遮盖真实联系方式，保存使用本地验证文本。

Word预览通过现有浏览器导出处理生成本地PDF，未签字、未通过PLT登记。`pdfinfo`确认一页A4；Poppler渲染后逐项检查全部7行、数量、表格及页码完整，无内容截断。该PDF仅为版式验证样张，不是业务交付件。

## 针对性检查

- `CompletionCertificateWorkbench.runtime.spec.ts`、`ConfirmationPrintV2.runtime.spec.ts`、`projectContext.runtime.spec.ts`：3个文件、52项通过；保留保存失败、分页失败、上下文、只读、历史数据和签字保护验证。
- 修改的3个Vue组件与打印测试ESLint通过；手机布局调整后重新检查只读表组件，ESLint通过。
- 本次范围差异检查通过。未重复执行全项目类型检查、后端构建或重启。

## 证据

- [新建回填](defaults.png)
- [管理查看](detail.png)
- [Word打印预览](print-preview.png)
- [客户手机页面](customer-mobile.png)
- [运行结果](result.json)
- [未签字、未登记的版式样张](layout-only-completion.pdf)
- [PDF渲染检查](printed-page-1.png)

本地验证脚本：`.run/completion-name-quantity-browser.py`；针对性测试和ESLint日志：`.run/completion-name-quantity-tests.log`、`.run/completion-name-quantity-lint.log`、`.run/completion-name-quantity-mobile-lint.log`。本次修改未提交或推送，不提升Feature整体实施状态。
