# 默认链已执行验证

本表按测试类/文件的最新通过结果计数，重复运行只计一次。它不是一次全仓测试的统计，也不替代真实浏览器验收。日志位于本机 `.run/default-chain-20261009/`。

## 后端定向回归

共 12 个测试类、134 项，各测试类最新有效结果中失败/错误/跳过均为 0。继承的基线用例按实际测试类执行计入。

| 测试类 | 项数 | 最近有效证据 |
|---|---:|---|
| DirectBusinessInheritanceTest | 17 | common-chain-tests-3.log |
| BusinessConditionEvaluatorTest | 5 | condition-version-final.log：该测试类通过 |
| DirectBusinessRuntimeServiceTest | 2 | common-chain-tests-3.log |
| MemberConditionEvaluatorTest | 5 | backend-condition-parity.log |
| SharedConditionBackendTest | 1 | condition-version-final.log：该测试类通过 |
| EntityFormBindingTest | 8 | backend-definition-source-fixed.log |
| EntityPresentationServiceTest | 5 | baseline-backend.log；对应生产实现本轮未修改 |
| BusinessModelContractAdviceTest | 3 | baseline-backend.log；对应生产实现本轮未修改 |
| DefaultBusinessAggregateTest | 19 | backend-definition-source-fixed.log |
| DirectVersionedBusinessTest | 27 | backend-definition-source-fixed.log |
| SiteSurveyInheritedBusinessTest | 27 | backend-definition-source-fixed.log |
| RequirementInheritedBusinessTest | 15 | backend-definition-source-fixed.log |

`condition-version-final.log` 整次运行曾因聚合测试 Mockito 替身配置失败而失败；只引用其中独立通过的测试类。聚合测试修正后单独重跑的 19 项全部通过，未降低业务断言。

最新五类受影响测试合计 96 项通过。定义来源新增用例验证跨布局首写保留隐藏定义，以及来源必须通过租户、发布状态和业务适用性校验；不能提交客户端自造字段定义。

后端最新独立打包记录为 `build-server-isolated-4.log`（BUILD SUCCESS），其依赖先经 `build-definition-source-install.log` 安装。独立制品复制至 `.run/default-chain-20261009/runtime-final/yudao-server.jar` 后，经用户批准重启本轮 27580 后端，已用于两个薄实体的最终浏览器及任务验收。原运行文件被构建改写的失败及独立打包复用旧依赖的失败均保留，不计通过。

## 前端定向回归

共 9 个文件、46 项，按下列已执行批次的最新结果去重，不把各批次总数重复相加。

| 测试文件 | 项数 | 最近有效证据 |
|---|---:|---|
| ProjectBusinessPage.runtime.spec.ts | 14 | frontend-requirement-context-fixed.log |
| useProjectBusiness.spec.ts | 9 | frontend-workspace-2.log |
| ProjectBusinessForm.runtime.spec.ts | 6 | frontend-requirement-context-fixed.log |
| ProjectBusinessHistory.runtime.spec.ts | 2 | frontend-survey-list-fixed.log |
| ProjectBusinessDetails.runtime.spec.ts | 2 | frontend-details-readonly.log |
| ProjectBusinessFieldConfiguration.runtime.spec.ts | 2 | frontend-profile-copy-fixed.log |
| SurveyFullCaptureForm.runtime.spec.ts | 5 | frontend-requirement-context-fixed.log |
| RequirementBusinessForm.runtime.spec.ts | 2 | frontend-requirement-context-fixed.log |
| declaredFormPresentation.runtime.spec.ts | 4 | frontend-survey-list-fixed.log |

最终类型检查：`frontend-types-final-6.log`，退出码 0。附件区域 CSS 宽度修正后，最终图标检查及构建为 `frontend-build-final-6.log`，32.14 秒完成，退出码 0。类型检查与构建串行执行，纯 CSS 调整未重复类型检查。

命名配置复制丢失表单版本由 `frontend-profile-copy-regression.log` 复现；切回已保存布局丢失新定义来源由 `frontend-return-binding-failure.log` 复现；任务旧关联筛掉新增行由页面回归用例复现；需求分析附件缺失 API 上下文由 `frontend-requirement-context-failure.log` 复现。修正后受影响批次通过；最终公共包装层、页面、工勘与需求分析四个文件合计 27 项通过。先前失败保留，不计为通过。

## 真实 API 与浏览器

| 对象 | 已执行范围 | 证据 |
|---|---|---|
| IT_DEFAULT_SHEET | 实际 MySQL 主从/扩展原子保存、幂等重放、绑定切换保值、跨租户/伪造明细/陈旧版本拒绝、失败整体回滚、条件求值 | fixture-sheet-api.log、api-evidence.jsonl |
| IT_DEFAULT_MEMO | 第二薄实体创建、修订冻结/生效、复制、显式空值、历史不变、禁止冻结修改和绕过版本、对比、放弃 | fixture-memo-api.log、api-evidence.jsonl |
| 新薄实体通用页面 | 首写前完整/精简布局切换、主体/明细/false/0/数组保留、命名列表/表单/Tab、配置复制、独立 CRUD 删除 | browser-generic-create-success.json、generic-confirmed-no-file.json、browser-named-profile-copy.json、crud-browser-deleted.json |
| 新薄实体任务链 | 保存及确认但无文件不完成；错误类型及删除不完成；正确类型上传自动 DONE/100%，项目归集与实体 ID 一致 | generic-context-after-save.json、generic-confirmed-no-file.json、generic-wrong-file.json、generic-deleted-file.json、generic-required-file.json |
| 第二薄实体版本页面 | V1/V2 草稿隔离、冻结、生效、复制、差异，V3 放弃后仅两份冻结历史，重开只读 | memo-browser-draft-isolation.json、memo-browser-v2-draft.json、memo-browser-compare.json、memo-browser-final.json |
| 工勘真实页面 | 普通 PUT、扩展重开、离开保护、工期同步、结构化地点、列表确认；错误类型/删除不完成，正确类型公共上传自动 DONE | browser-survey-location-put.json、browser-survey-deadline.png、browser-task-auto-done.png、task-completion-and-collection.json |
| 用户反馈项目 992203060009 | GK-002/GK-003 原已保存；列表从错误显示一条恢复为三条，关闭详情、刷新重入仍正常，无需改用户数据 | browser-user-survey-list-before.png、browser-user-survey-list-fixed.png、browser-user-survey-list-reopened.png |
| 需求分析真实页面 | 11 项正文、字段附件、V1 冻结、复制并完成 V2、V1 只读与正文不变、版本对比；最终包装层修复后重新读取 V1/V2 和历史附件 | browser-requirement-frozen.png、browser-requirement-compare.png、browser-requirement-context-fixed.json |

最终通用实体及条件任务验收运行于最新制品。工勘早期写入样本没有重复创建；用户反馈列表、需求分析历史和附件经最终代码重新读取。截图复核修正附件区域在表单 flex 容器内收缩的问题，浏览器实测附件宽度与父表单同为 1258px。前端为宿主机 Vite 服务，修改经 HMR 使用。

报告内可直接查看 [结构化验收数据](evidence/acceptance.json)、[任务完成截图](evidence/browser-generic-task-done.png)、[版本差异截图](evidence/browser-generic-memo-compare.png)、[工勘列表截图](evidence/browser-user-survey-list-reopened.png)、[需求分析历史截图](evidence/browser-requirement-history-readonly.png)。

## 入口与验证边界

27581 通用页面 `/pms/business/records/IT_DEFAULT_SHEET?projectId=992203060012`、`/pms/business/records/IT_DEFAULT_MEMO?projectId=992203060012`；任务入口 `/pms/project-management/project-master-detail?projectId=992203060012`。薄实体位于测试装配，正常业务框架中没有测试编码分支。

本轮没有全仓测试、生产部署、全部生产业务迁移或旧材料批量迁移的结论。既有冻结/确认样本保留用于历史读取，不回退状态；原 27480/27481 与基础设施未重启，未提交或推送。
