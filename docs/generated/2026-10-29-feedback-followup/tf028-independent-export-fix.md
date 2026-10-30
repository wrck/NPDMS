# TF-028 引用拓扑导出的独立方案回归修复

2026-10-29，继续问题跟进清单时审计发现。该记录只覆盖本次本地增量，不替代多 Visio 的真实业务链验收。

## 缺陷与修正

`SolutionReviewChapterForm.vue` 导出时只要现网图为空，便要求加载需求来源；独立编制、未选择 `requirementInput.revisionId` 的方案因此报“请先加载所引用的需求分析拓扑附件”，无法进入 Word 转换。

`SolutionSubmissionValidator.java` 已明确需求分析按需引入，未引用时独立编制。本次只将需求拓扑解析条件增加为“存在显式需求修订引用”；已选择来源但未加载、附件不可用或预览被拒绝仍失败关闭。方案切换/正文变化的异步重验不变。

生产改动只有 `solution-reviewed/SolutionReviewChapterForm.vue` 的 `needsRequirementTopology` 判断。`solutionExport.runtime.spec.ts` 在原 3 项异步隔离测试之上增加独立编制到达转换边界及显式引用未加载拒绝 2 项，补齐文件 API mock 隔离，并重置每例的一次性 mock。

## 验证

- 失败测试先复现：`.run/feedback-20261029-tf028-export-failure.log`，1 项失败、4 项通过，失败为独立方案未进入转换。
- 修复后：`.run/feedback-20261029-tf028-export-tests-final.log`，`solutionExport.runtime.spec.ts` 与 `requirementTopology.spec.ts` 共 8 项通过；后者保留多文件逐页处理、拒绝访问即失败与有效引用中不可用附件不静默遗漏的测试。
- 两个修改文件 ESLint 通过：`.run/feedback-20261029-tf028-export-eslint.log`。
- 定向 vue-tsc 通过：`.run/feedback-20261029-tf028-export-vue-tsc.log`；配置 `.run/feedback-20261029-tf028-export-tsconfig.json`。
- 额外运行既有 `documentImages.spec.ts` 时，实际 DOCX 用例在原测试环境因 `DOMParser` 未定义失败；指定 jsdom 后因 jsdom `File.arrayBuffer` 缺失失败。另 2 项异常图片响应检查通过。日志分别为 `.run/feedback-20261029-tf028-export-tests.log`、`.run/feedback-20261029-tf028-document-images.log`。未将该环境失败算作业务通过，也未修改其测试环境。

## 尚未验证

本次没有进行真实浏览器导出、上传或保存，没有重启/部署，也未执行迁移。TF-021/028 多 Visio 全链仍需独立合法需求版本绑定至少两份 Visio 并完成，再通过项目详情 S3 方案任务引用、生成和下载 Word，核对各页图片、原件下载和冻结来源。

只读盘点结果见 [现有测试样本](tf028-independent-solution-candidates-readonly.json)：数据库会话 `transaction_read_only=1`，独立 TEST_ONLY 项目 84577/84578 的方案 52/51/50 全部已经引用需求。仅发现 66943 的 TEST 背景草稿 44 无需求引用；该项目不是独立 TEST_ONLY 且既有记录为 S0 前置负例，不作为本次合法 S3 导出样本。未为获得样本清空引用或修改历史。

19191 对组件模块返回 HTTP 200，提供当前 E 工作树及显式需求引用判断，证据 [Vite 源码响应](tf028-independent-export-vite-source.json)。此证据不等于真实页面导出验收。

已有材料 `需求/项目交付/项目交付流程 (2).vsdx` 与 `.run/feedback-20261027/acceptance/tf021-visio-preview.pdf` 能支持真实转换；历史转换 6 项通过不能替代上述业务链。84459 方案 43 的既有来源是单一 PNG，也不能用作多 Visio 证据。
