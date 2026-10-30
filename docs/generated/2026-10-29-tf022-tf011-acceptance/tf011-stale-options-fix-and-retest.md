# TF-011 切词旧候选修复与复验

2026-10-29；选择器源码已稳定。只修改SystemUserScoped.vue和其既有runtime spec；没有修改授权、接口、数据库、后端包或服务启停。前端19191为现有工作树Vite入口，HTML含`/@vite/client`与`/src/main.ts`；HTTP读取该组件模块确认已含本次修改，因此实际复验使用热更新源码。

## 修复行为

原问题：输入新词后，旧候选在默认300ms远程防抖及新请求等待期间仍可保留，键盘存在选中旧候选的窗口。旧响应序号保护只能防止较晚响应覆盖，不能替代输入时立即失效。

现行为：原生input捕获时立即失效候选、total、页码与旧请求序号；已选人员保存为只读历史标签。第一页替换立即清候选，追加页继续去重。新增选择必须属于当前候选；既有历史ID仍可保留、移除或清空。

ElSelect内部防抖设为0，由本组件统一保留300ms防抖。这样，即使用户输入其他词后迅速退回已查询词、ElSelect按previousQuery跳过remote-method，组件仍会调度新请求，避免永久加载。中文组合输入期间先失效、不发请求；compositionend继续调度。来源切换与卸载清理定时器；原in-flight复用和sequence保护保留。

实现依据：[Vue事件捕获修饰符](https://vuejs.org/guide/essentials/event-handling.html#event-modifiers)、[Element Plus Select属性](https://element-plus.org/en-US/component/select.html#select-attributes)，并核对仓库实际锁定Element Plus 2.13.7的previousQuery、默认debounce与键盘选择实现。

## 失败与回归证据

- `.run/feedback-20261028/tests/tf011-stale-options-behavior-red.log`：修复前3个行为失败、13个既有测试通过。失败分别证明新输入后旧rows保留、旧请求在防抖窗口回填、失效候选ID仍被新增emit。
- `.run/feedback-20261028/tests/tf011-stale-options-final-green.log`：最终3个文件27测试通过，覆盖选择器runtime、SYSTEM selection来源、TF-011项目筛选来源；含299ms不查询/300ms查询、原词回退、组合输入收尾、历史标签与多选移除、分页、in-flight复用、来源变化、失败不回退目录。
- `.run/feedback-20261028/tests/tf011-stale-options-typecheck.log`：当前组件及其实际依赖定向vue-tsc退出0，无诊断。配置：`.run/feedback-20261028/tests/tf011-scoped-current-typecheck.json`，未使用隔离旧文件覆盖。
- 上述检查均加载`scripts/worktree-toolchain.ps1`并持有相同`Enter-WorktreeBuildLock`；其他会话构建占锁期间未绕锁。`git diff --check`通过。
- 独立审核代理复核候选8/3/0精确ID集合以及156成员项目双向等集；选择器单一防抖、旧请求隔离、历史标签和原词回退方案经独立复审。

## 原反馈账号真实浏览器复验

以下由父任务在本人`l04533`真实浏览器会话操作并回报；本子任务没有操控主浏览器、修改DOM、注入业务状态或调用写接口。

- 初次项目经理/服务经理/销售显示8/3/0，与独立期望数量一致。
- 从旧8候选输入不存在关键词，旧候选立即消失且显示加载；ArrowDown+Enter未选择旧项；完成后无数据。
- 空词→输入一字→立即删空，重新得到8候选，没有卡在加载。
- 从空结果搜索账号`f06009`，最终显示当前对应人员；服务经理搜索`x05721`可命中当前对应人员。
- CDP确认真实请求为GET `/admin-api/api/v1/pms/projects/member-filter-options`，query键为keyword、pageNo、pageSize、field。CDP无法取得响应正文，因此本轮浏览器记录的数量、名称及账号映射是UI和请求参数的间接证据，不能写成响应字符串ID逐项核对已完成。
- 先前选择`f06009`后得到两项目，与独立130管理成员项目中属于本人156FULL基准的交集完全相等，另外128项目未扩大返回；见`tf011-l04533-filtered-browser.json`与`tf011-f06009-shared-member-readonly.json`。

父任务测得账号搜索热样本工具墙钟898、897、903ms；另在页面重载后首次搜索`f06009`为741ms，后两次先切空结果再搜索为899、906ms，均等待正确选项可见。以上均包含工具开销，后端/JIT未冷启动，并非精确网络时序或纯DOM性能，也未核对候选响应ID。仅作观测，不能替代冷次加后续两次的正确稳定结果精确端到端计时，不能据此宣称Owner prepare、SQL或全部TF-011的1秒目标验收通过。未捕获实际Owner prepare响应；部门可见性、pageSize=1、特殊权限/确认位/时间边界和非法参数等剩余范围仍按`tf011-expected-candidates-readonly.json`保留未执行。

## 交付边界

本次完成现成合法夹具只读盘点、独立期望计算、切词交互修复、27项离线回归及本人真实浏览器部分复验。没有构造夹具数据、变更权限、执行迁移/DDL、重启后端、发布冻结包或推送提交；不能据部分复验将整个TF-011标记全部完成。
