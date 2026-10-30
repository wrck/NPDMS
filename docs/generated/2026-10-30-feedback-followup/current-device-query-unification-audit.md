# 当前设备、产品与序列号查询统一审核及最小修改

2026-10-30。本轮先审实际Owner、前后端读链、关联键、权限、编辑与历史入口，再按用户授权修复已证实的当前读取缺口。**当前设备读SQL与序列号导出已共享产品名称回退，设备当前完整投影同时补齐产品描述；项目列表与设备候选的产品型号筛选复用当前展示型号；换货清单“型号”已改读真正的主档型号；产品编码查询与导出原样传递。** 未部署、未执行新的DDL或业务写入，也未把现行SQL/测试结果当作浏览器验收。

## 已核查的Owner与查询链

|读链|现有来源、关联与范围|本轮处理|
|---|---|---|
|AST设备工作台分页、当前详情、按ID当前摘要、项目设备摘要|AST设备事实；主档以tenant_id+product_code+主档deleted=0唯一键关联；项目/有效设备关系/精确合同/配对组织授权各自保持|复用DeviceCurrentModelMapper同一当前产品投影，设备非空原值优先；原值缺失时读主档名称/描述；型号沿原公共投影|
|设备档案普通分页、项目列表设备条件与项目候选|普通页selectVisibleArchiveList已用currentDeviceColumns，并非原始d.*；项目候选是项目及有权合同两个分支；项目列表先解析设备命中项目，再与原可见项目求交集|普通页承接公共当前字段；项目列表型号和候选独立型号筛选复用currentProductModel，保留SN/维保/关键词条件交集及原可见范围|
|archive-record与编辑表单|原始设备读取被DeviceWorkbenchDialogs直接交给编辑表单；validateDeviceExists还被状态、写入和历史调用|保留原始编辑/锁定/历史契约。只读消费者由另一分工改用既有current summary，不能把主档回退值经编辑表单回写设备|
|COM交付范围与项目订单设备清单|COM范围/明细/订单行是分配数量Owner；只在已批准的范围分页、同范围导出读AST主档当前名称/型号|同一产品编码原样聚合，范围缺设备仍保留；不能以设备台数代替订单发货量|
|项目序列号分页与序列号导出|分页用DeviceQueryMapper，导出独立ProjectSerialExportMapper；租户+当前项目+未删设备；网络版本只DEVICE_LOG、维保续保统计保留|导出新增复用同一个currentProductJoin/currentProductName，保持原17个导出数据字段、游标与来源过滤；前后端仅产品编码不trim，SN和设备名称原处理保留|
|已发货设备选择、割接设备范围|非空SN且shipment_time非NULL的直接项目设备；采用公共当前型号；服务端项目VIEW和调用契约仍生效|不把该范围强行扩大为所有设备。84459样本完整130台、已发货110台，两个数量不同有真实业务原因|
|产品列表/换货目标产品|AST产品主档当前副本，产品列表只读权限沿用换货查询；换货命令按ID并要求ACTIVE；产品快照写入服务端历史|不删除ACTIVE等现有准入规则，不用当前名称覆盖原命令和版本快照|
|换货范围清单候选与已选行|MaterialDevicePicker通过原有受权COM范围分页展平，稳定scope/detail引用、同合同同编码分组及数量校验保持|两处“型号”原显示scope.productDesc，现仅显示scope.productModel；缺型号显示“-”，描述不冒充型号，原productName/描述快照不改|
|割接调查表、联调等跨模块只读消费|当前摘要通过AST公开API；冻结设备范围、指定ID及SN一致性校验仍生效|不另建跨模块Mapper/直接访问其他域表；当前读取与已经提交的调查结果、批准方案、设备版本和来源证据分开|

审计入口包括DeviceCurrentModelMapper、DeviceQueryMapper、DeviceJoinedPageMapper、ProjectDeviceSelectionMapper、ProjectDeviceShipmentMapper、ProjectSerialExportMapper、DeviceMapper、DeviceQueryService、DeviceDetailService、DeviceArchiveServiceImpl、DeviceQueryApiImpl、AssetProductOfficialController/API，以及projectEquipment/projectOrderEquipment、ProjectSerialPanel、MaterialDevicePicker、cutoverDeviceSource和编辑弹窗调用链。未改同步、来源定义、历史记录或外部系统。

## 可复现缺口与修复证据

- 公共当前读只补型号，仍直接返回`d.product_name/d.product_desc`；序列号导出完全独立取原设备product_name。新增真实MyBatis查询测试让设备字段NULL/空串、同租户主档有值，修前当前页及导出都返回NULL，修后采用主档原值。保留设备非空原值、未删主档、其他来源/停用产品仍可展示，以及外租户/已删主档不能补值。
- 序列号前端查询、导出过滤与后端导出服务曾对产品编码trim。失败测试观察到传入`" P001 "`变成`"P001"`以及导出同类差异；现前后端都按原文传递，普通无空格编码不受影响。此修改不清洗来源或存量数据。
- 换货组件两处“型号”字段实际绑定name，而name来自scope.productDesc。项目84499三条当前范围都具备主档型号，描述却与型号不同。组件渲染失败测试修前显示“订单产品描述”而没有真实型号，修后候选/已选均显示productModel，无型号时不拿描述兜底。
- 项目列表的deviceProductModelKeyword原通过单表wrapper筛原设备型号；候选页独立productModel原在无keyword时也只筛原设备型号。设备原型号缺失、同租户未删主档有型号时，两者与当前展示不一致而漏搜。新增真实MyBatis失败测试复现；现Mapper同名方法以原场景Query进入XML，复用公共JOIN/型号，保留tenant、deleted、project_id非NULL及DISTINCT；SN与维保继续AND，调用方仍与原可见项目取交集。候选不改变原范围、分页或其他条件。
- [真实只读样本](device-product-query-unification-samples-readonly.json)中84499的24设备与84459的130设备原型号、产品名称均完整；没有把它们冒称为真实缺名样本。前述NULL/空串回退是隔离失败测试覆盖的受支持缺口。84573设备0行也如实记录。

新增CASE直接遵循实际字符列collation。utf8mb4_unicode_ci为PAD SPACE，因此`<>''`的空格比较按数据库规则发生；本轮不宣称H2对所有空白字符的行为等同MySQL，也不新增TRIM、CAST或Java归一化补丁。含文本周围空格的非空原值及主档展示原值均通过测试保持。

## 验证与未验范围

- 后端先失败：`.run/current-device-info-red.log`，6类39项中的3项精准失败，分别是当前名称缺失、序列号导出名称缺失及产品编码被trim，无环境错误。
- 两处型号筛选另先失败：`.run/current-device-info-filter-red.log`，2类12项中的3项精准失败，无错误，分别覆盖主档型号漏搜、SN/维保交集下漏搜及无关键词独立型号漏搜。中间`.run/current-device-info-filter-green.log`的55项出现3个H2不支持MySQL位字面量的语法错误，原日志保留；新增SQL改用与公共JOIN一致的`deleted=0`，删除条件未放宽。
- 固定JDK25、工作树Maven依赖仓及同一独占锁下最终合并验证：`.run/current-device-info-filter-final-green.log`，11类55项、失败/错误/跳过均0，19.304秒；包含分页授权、空集合、租户、未删主档、原值优先、项目去重、SN/维保AND交集、游标与导出失败不返回部分成功等保护。此前`.run/current-device-info-green.log`的10类52项通过证据仍保留，最终结论采用新增型号筛选后的55项结果。锁在验证完成后释放。
- 本分工前端固定Node20.19.6、仓库vitest.pms-file.config.ts：MaterialDevicePicker与ProjectSerialPanel共19项通过；定向vue-tsc退出0，ESLint退出0，4条原有模板换行warning未顺手格式化。早期默认Node执行的组件失败/通过结果仅为调试；正式前端通过结论使用固定Node结果。
- 独立复核以固定Node重新执行4文件28项，日志为`.run/current-device-independent-front-review-20261030.log`（上述2文件19项）及`.run/current-device-summary-independent-front-review-20261030.log`（DeviceTag/jointTestWorkspace 9项）；完整收据`.run/current-device-unification-independent-review-20261030.json`记录17个生产文件SHA256、最终55/15/16项后端日志复核及授权/历史边界。独审结论为源码可进入构建。
- [当前生产XML真实MySQL只读执行](current-device-info-native-readonly.json)：展开公共完整设备投影和序列号导出原SQL，84499各返回24行，设备集合与逐设备产品名称完全一致，名称/描述缺失0；EXPLAIN通过，主档使用物理唯一键。没有修改数据库，未通过连接collation或程序二查规避原生关联。

全部源码测试不代表新运行包、部署、重启或84499真实浏览器验收。本轮没有执行这些操作。

## 有意保留的独立语义

项目列表及候选独立型号筛选按本轮用户明确的统一授权采用“当前展示型号”，原有筛选回归断言同步更新并保留设备原型号优先、关键词交集与权限验证。`ProjectDeviceSelectionMapper.selectSelectionPage`旧wrapper方法全库只有测试调用，生产已使用candidate XML，本轮不扩改无现行业务调用的路径。MES出厂版本、KNO官网版本和公告在设备详情/序列号页面明确标记未接入，不为了填满界面编造新Owner或无依据关联。无当前主档记录的产品继续保留NULL及既有缺失，不择一、不回填历史。
