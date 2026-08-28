# P3-E08 前端类型检查证据

> 状态：`OPEN / FAIL`
> 实现提交：`856d05264ab4a4fb69b94896c172e4a1c29aae02`
> 命令：`corepack pnpm ts:check`
> 结果：exit code `1`，错误 `182` 项

该证据只记录可复现失败，不以生产构建成功覆盖类型门禁，也不通过关闭检查、放宽TypeScript规则或批量断言消除错误。JSON为逐错误机器证据。

## 1. 按范围

|范围|错误数|
|---|---:|
|PMS_ENGINEERING|85|
|PMS_PROJECT|80|
|PMS_SERVICE|9|
|UPSTREAM_OR_NON_PMS|4|
|PMS_CUTOVER|3|
|PMS_SHARED|1|

## 2. 按错误代码

|代码|错误数|
|---|---:|
|TS2322|103|
|TS2353|30|
|TS6133|21|
|TS2345|18|
|TS2304|3|
|TS18048|3|
|TS2339|2|
|TS2551|2|

## 3. 高错误文件

|文件|错误数|
|---|---:|
|`src/views/pms/project/project-detail/index.vue`|32|
|`src/views/pms/engineering/ext-proc/index.vue`|10|
|`src/views/pms/engineering/material-exch/index.vue`|9|
|`src/views/pms/engineering/material-req/index.vue`|9|
|`src/views/pms/engineering/outsource/index.vue`|8|
|`src/views/pms/engineering/doc-template/index.vue`|7|
|`src/views/pms/project/project-governance/index.vue`|7|
|`src/views/pms/engineering/announcement-check/index.vue`|6|
|`src/views/pms/engineering/briefing/index.vue`|6|
|`src/views/pms/engineering/form-instance/index.vue`|6|
|`src/views/pms/engineering/risk/index.vue`|6|
|`src/views/pms/project/plan-change/index.vue`|6|
|`src/views/pms/engineering/announcement/index.vue`|5|
|`src/views/pms/project/batch-change/index.vue`|5|
|`src/views/pms/project/project-tree/index.vue`|5|
|`src/views/pms/service/srv-task/index.vue`|5|
|`src/views/pms/engineering/authorization/index.vue`|4|
|`src/views/pms/project/portfolio/index.vue`|4|
|`src/views/pms/engineering/form-template/index.vue`|3|
|`src/views/pms/project/completion-certificate/index.vue`|3|
|`src/views/pms/project/project-risk/index.vue`|3|
|`src/views/pms/project/schedule-backward/index.vue`|3|
|`src/views/pms/cutover/cut-execution/index.vue`|2|
|`src/views/pms/engineering/arrival/index.vue`|2|
|`src/views/pms/engineering/configuration/index.vue`|2|
|`src/views/pms/project/acceptance/index.vue`|2|
|`src/views/pms/project/archive-document/index.vue`|2|
|`src/views/pms/project/deliverable-checklist/index.vue`|2|
|`src/views/pms/project/maintenance-transition/index.vue`|2|
|`src/views/pms/project/service-level/index.vue`|2|

## 4. 工程判定

1. 失败可稳定复现，属于锁定实现提交的现存质量债，不是本次Phase 3文档生成引入。
2. 错误横跨PMS公共组件、多个PMS业务域和非PMS上游页面，不能作为单一Feature的局部修补处理。
3. P3-E08继续阻塞任何前端Feature验收、真实浏览器验收和发布；应拆为公共契约/组件、PMS领域页面、上游兼容三类修复工作包。
4. 每个工作包必须保留严格检查，先以当前JSON中的错误集合建立失败基线，再逐类清零并回归`ts:check`、构建及实际页面。
