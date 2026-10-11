# 安装地点与服务端批量保存验收（2026-10-08）

## 结果

当前19191前端、59191后端及npdms_domain_test已验证。新增接口为`POST /api/v1/pms/implementation/installation-records/batch`，前端一次提交设备ID集合和共用安装地点；服务端完整项目编辑授权、设备集合校验及同事务保存生效。

真实浏览器在项目1002选择三台设备，现场维护新站点并添加机房，缺少详细地址、行政区划时均被阻止提交；填写完整后一次批量请求成功（单条新增请求为0），落库三条安装记录并重新打开查看。三条记录共享地址930844、站点930835、位置930866；按客户1002及验收名称查询仅一个站点，站点树仅一个位置。记录ID：2108105236201730078、2108105236201730079、2108105236201730080，验收名称为`安装批量地点验收-20261008-2059`，验收记录保留，未删除既有记录。

当前客户已保存站点选择、手动地点文本的批量请求内容亦经真实浏览器核对；这两项请求拦截后未新增业务记录。空设备集合、重复设备集合和混入无效设备被真实服务端拒绝，安装记录总数保持7条。

## 验证

- Maven打包通过；InstallationBatchServiceTest 4项、ProjectInstallationBatchControllerTest 3项、ProjectInstallationAddressControllerTest 2项通过。事务失败测试以Spring事务代理及H2 JDBC验证第二条失败后安装记录及新地点均回滚；项目部分范围拒绝、不合法设备集合在写入前拒绝、手动文本不创建结构化地点均覆盖。
- 前端projectInstallationWorkbench.runtime.spec.ts 4项通过，覆盖单次批量请求、失败保留完整选择、完整项目安装地址统计、历史只读、新地点地址补全及空位置剔除。
- 通用地点组件既有16项契约检查通过；局部ESLint、Vue SFC编译与git diff --check通过。没有将健康接口或编译成功作为业务验收。
- 部署加载已有DAC HTTPS环境，59191后端及DAC就绪检查通过；保留npdms_domain_test、Redis及local,dac配置。

## 本机证据

`.run/dac-host/installation-server-batch-acceptance/acceptance.json`、`real-save-reopened.png`、`manual-existing-site-acceptance.json`、`invalid-device-set-acceptance.json`；构建日志`.run/dac-host/installation-batch-package.log`。首次验收已成功落库，其后数量核对的查询路径错误通过只读续验修正，没有重复执行新增。

## 已有站点完整地址补正

按项目客户限定站点的分支原先只查询站点，没有读取关联地址，导致已有站点保存文本缺少完整行政区划、详细地址。现按站点引用的地址ID去重读取地址，再提供站点选择，并同步完整站点路径回传。

真实浏览器已核对已有站点新建批量请求包含完整地址；另仅更新原专用验收记录2108105236201730080并重新打开，安装位置实际保存为`安装批量地点验收-20261008-2059（中国北京市北京市东城区验收专用安装地址1号） / 批量验收机房`。站点ID930835、位置ID930866不变，站点主数据版本不变。没有批量改写用户历史记录。局部ESLint和Vue SFC编译通过；证据为`.run/dac-host/installation-server-batch-acceptance/existing-site-full-address-real-save.json`及`existing-site-full-address-reopened.png`。

## 安装地址格式统一

用户确认统一格式为`完整地址 / 站点名称 / 下级位置`。已有站点回传的完整路径改为与新建站点一致，选择项标签仍用于识别站点。已对专用验收记录2108105236201730080真实保存并重新打开，断言保存文本严格等于完整地址、站点名称、机房按分隔符拼接的结果；站点和位置ID、站点主数据版本保持不变。局部ESLint与Vue SFC编译通过，证据为`.run/dac-host/installation-server-batch-acceptance/existing-site-unified-format-real-save.json`。既有用户历史记录未批量改写。

## 多级位置选中回显

通用地点组件的树选择框通过选中标签插槽显示从位置根节点到选中节点的完整路径，提交仍使用选中节点ID，不改变树层级与引用语义。真实浏览器在专用验收记录2108105236201730080维护并保存三级链路，重新打开后选择框完整显示`批量验收机房 / 验收机柜 / 验收槽位`，安装地址亦包含上述完整链路，最终位置引用为930868。局部ESLint、Vue SFC编译通过。证据：`.run/dac-host/installation-server-batch-acceptance/multi-level-location-selected.json`、`multi-level-location-selected.png`。

## 保存即完成及设备历史入口

按用户确认，项目安装工作台的服务端批量事务在创建记录后衔接现有开始、完成状态动作，并执行AST设备当前位置生效；前端仅一次批量请求。界面移除下方独立安装记录区以及开始、完成、异常、删除、历史编辑操作，设备操作列提供维护安装地址和安装历史。安装历史按设备隔离、分页，并通过只读详情查看；纠正位置以新增记录保留历史。

实际部署59191后，真实浏览器批量保存三台测试设备，记录2108105236201730221、2108105236201730222、2108105236201730223均立即成为状态2且有效起始时间非空，共用站点930835及位置930868；设备列表读回确认RESOLVED及当前位置信息生效。设备历史抽屉仅展示对应设备的记录，详情无保存按钮，主页面不存在独立记录表和开始/完成按钮。

真实浏览器进一步给同一测试设备手动填写新地址，记录2108105236201730224立即完成，前一当前记录2108105236201730221的有效结束时间等于新记录的有效开始时间，旧地址快照原样保留，两条记录均在设备历史可见。旧有待安装记录未追溯改写为完成。

后端10项定向测试及打包通过（事务测试额外覆盖设备位置投影生效失败时安装、地点及位置投影全部回滚），前端5项运行时测试通过（包括历史设备隔离、历史只读），局部ESLint、Vue SFC编译及diff检查通过。部署前检查DAC并加载既有HTTPS环境，保持npdms_domain_test及原端口、Redis配置。

证据：`.run/dac-host/installation-save-complete-acceptance/acceptance.json`、`manual-history-acceptance.json`、`device-table-only.png`、`device-history-readonly.png`、`superseded-history.png`；构建日志`.run/dac-host/installation-save-complete-package.log`。

## 序列号详情安装地址展示

序列号详情此前仅显示AST设备位置快照：手动安装记录的设备快照为空，结构化快照为JSON；并且已访问的页签通过v-show缓存，安装后返回不重新读取。现按当前页设备及项目查询已完成安装记录，取有效起始时间非空、有效结束时间为空的当前记录完整安装地址，排除已失效历史；兼容设备快照时只提取可读地点文本。父页面传递active，返回序列号页签时刷新，列名统一为安装地址。

真实浏览器确认手动地址、包含完整行政区划/站点/机房/机柜/槽位的结构化地址均正常显示。先打开序列号详情，再在硬件安装中保存专用测试设备新手动地址`序列号安装地址刷新验收-20261008`（记录2108105236201730225），切回已缓存序列号详情后自动显示新地址；另一结构化设备的完整地址保持正确。序列号定向测试2项、局部ESLint及两个Vue SFC编译通过。

证据：`.run/dac-host/serial-installation-address-acceptance/acceptance.json`、`serial-address-before.png`、`serial-address-refreshed.png`。

## 2026-10-09 维护安装地址默认回填

- 用户要求打开“维护安装地址”时回填最新安装地址。复用当前项目已加载的安装记录，只选择当前有效、已完成（status=2、effectiveFrom存在、effectiveTo为空）的设备记录。
- 回填仅复制地址文本、站点及下级位置引用和版本；新表单不带历史记录ID，不复制旧安装时间、人员、照片或备注，保存仍使用既有服务端批量新增安装记录。
- 批量设备当前地址及站点、位置引用一致时回填公共地址；不同或未维护时保持空白。设备列表地址同步使用当前有效记录，排除未生效和已结束历史。
- 前端运行测试6项、相关ESLint通过。真实浏览器项目1002：设备17899180070026手工地址回填“序列号安装地址刷新验收-20261008”；设备17899180070025站点、完整“批量验收机房 / 验收机柜 / 验收槽位”自动选中，保存按钮可用，依次关闭重开无串值。未提交保存，业务写入0。
- 证据：`.run/dac-host/installation-address-prefill/acceptance.json`及manual/structured截图；复现脚本`.run/dac-host/accept-installation-address-prefill.py`。前端热更新生效，无后端重启或数据库迁移。
