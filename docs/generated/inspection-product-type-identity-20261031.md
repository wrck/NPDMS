# 巡检产品类型服务身份报错复验

日期：2026-10-31。反馈：新建巡检/选择设备时提示“产品类型服务身份未授权”，账号y05422（余俊霄）。

## 代码与配置核查

- 错误码`1_015_005_001`来自AST `TrustedAssetProductTypeServicePrincipalRegistry.resolve`，检查模块消费方INSPECTION及动作白名单，不直接按登录账号判定。用户设备可见范围仍由后续查询独立校验。
- 当前59286运行包`a88aa1a336ce4cd89a11b30ce9801a2756fc5f8fd6f75bbe0ea8738d78d8b1e5`的local配置包含技术主体970477601，仅允许`PRODUCT_TYPE_READ_CODES`和`DEVICE_PRODUCT_TYPE_READ`两个只读动作。
- 新增`AssetProductTypePrincipalBindingTest`使用实际application-local.yaml及Spring配置绑定，验证两个读取动作成功、导入动作仍拒绝。连同既有guard及适配器测试共10项通过，日志`.run/direct-test/inspection-principal-tests.log`。没有修改生产业务代码、角色或服务身份授权配置。

## 真实浏览器结果

用户提供登录凭据后，正常切换登录y05422，关闭“记住我”。在19086前端、59286后端：

1. 巡检任务列表和新增入口可见。
2. 选择授权项目“天津市城管委网络安全改造项目”，项目编号16200325012002N-0；候选列表正常返回已发货设备。
3. 添加设备01101669D256000001，填写明确标记TEST_ONLY的复验背景，选择离线巡检，通过“保存巡检准备”提交。
4. 成功生成`INS-2116355272920236032`，状态“待准备”，设备产品类型显示“未提供”；没有出现服务身份未授权错误。

复验仅新增这条标记TEST_ONLY的准备记录，没有执行设备命令、上传日志、发起UMC巡检、发送调查或改动历史项目/设备事实。没有重启服务，也没有操作59191。浏览器保留y05422及准备详情页面。

## 原环境追踪补充

### 运行时绑定与原入口复验（10:42后）

用户补充原入口`/pms/inspection/tasks?create=1`。本轮以浏览器现有y05422（页面显示余俊霄）会话直接打开该入口，查询项目16200325012002N-0，设备弹窗返回111台已发货设备；通过弹窗添加01101669D256000001，再通过序列号查询添加01101700D256000001，页面显示已选择2台。仅改变未保存表单，没有提交、改动历史或新增记录。

本轮不再仅检查包文件：只读访问59286已有Actuator端点`env/pms.asset-product-type.trusted-service-principals.INSPECTION.principal-id`及`configprops/pms.asset-product-type`。运行时激活local,dac；Spring已绑定的assetProductTypeServicePrincipalProperties实例包含INSPECTION、principalId和两个allowedActions，输入来源明确为application.jar中的application-local.yaml第262、264、265行。端点值保持脱敏，没有解除脱敏或调整监控配置。证据`.run/direct-test/inspection-runtime-binding-evidence.json`。这证明运行时绑定存在；不宣称直接读出了Registry内部对象或脱敏值。

请求链路与证据层次：

|步骤|请求/调用|本轮结果|
|---|---|---|
|新建页搜索项目|GET /admin-api/api/v1/pms/projects/options|浏览器授权项目选项正常|
|选择项目|GET /admin-api/api/v1/pms/projects/{projectId}/context|浏览器展示项目经理、服务经理及办事处|
|设备弹窗及序列号搜索|GET /admin-api/api/v1/pms/projects/{projectId}/devices|两条分支成功，服务器设备请求COUNT从3增加至4，新增请求约78ms，HTTP200，页面实际出现第二台设备|
|保存准备|POST /api/v1/pms/inspection-tasks → InspectionTaskService.create → InspectionAssetProductTypeApi → CallerContext INSPECTION → Registry → 授权设备范围查询|本轮不重复提交；此前同包同账号已成功创建TEST_ONLY任务33作为运行行为证据|

上述HTTP证据来自服务器聚合指标，不是浏览器HAR，不能当作原失败请求的响应体。设备查询路径不调用产品类型Registry，不能用设备列表成功单独证明服务身份成功。结合本轮实际绑定和此前创建成功，可以确认当前实例的已测新建路径有有效身份配置；原报错的失败请求、时间和对应运行实例仍未找到，故根因调查继续保持未闭合。没有修改代码、权限、历史数据或59191。

- 已比较本地18个可读取的冻结应用包（2026-10-23至2026-10-31，按文件时间），均包含INSPECTION两项读取动作配置；Registry、Properties、Inspection适配器三个类的字节码摘要完全一致。不能将原错误归因为这些包之间的身份实现或配置遗漏。逐包证据保存在`.run/direct-test/inspection-package-comparison.json`。
- 读取当前19086实际提供的Vite转换后`src/config/axios/config.ts`，确认VITE_BASE_URL为`http://127.0.0.1:59286`。`.env.direct-test`默认59285由`start-frontend-19086.py`进程环境覆盖；默认文件差异不代表当前请求串环境。
- 对测试库执行只读查询，租户1访问日志总数为0，按错误码1015005001跨账号查询亦无记录。日志为空没有排除故障的证明力。当前保留的direct-test日志中未找到该错误原文。
- 该错误的明确触发条件为消费方/动作非法、缺少技术主体、主体ID非法、读取动作未授权，或INSPECTION误配受控导入动作；账号项目范围属于后续独立检查。不得通过增加y05422角色解决。
- 根因状态：**未闭合，不能结案**。用户已补充原页面路径；仍缺少原失败请求、时间及对应进程配置证据。当前成功仅证明当前实例的已测试路径有效，不证明原错误已修复。
- 本轮未改业务代码、服务授权或历史数据，未重启服务，未操作59191。

## 结论边界

当前测试运行包下，已使用反馈账号验证新建保存成功，无法复现原报错；不能据此声称已查明或修复原报错根因。若原问题来自其他地址、其他运行包或其他操作，需要对应环境/请求证据再判断；不应通过扩大账号权限或删除服务身份校验解决。
