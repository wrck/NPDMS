# ADR-0036：AST产品类型进程内服务调用主体与专用适配器

> 状态：`ACCEPTED`
> 日期：2026-08-30
> 适用基线：PRD V1.8修订010；SDS Phase 1/2/3 `BASELINE`
> Requirement：`EQP-01（V1/P0）`；关联`INS-03（V2）`、`INS-09（V2）`
> 关联Feature：`F-AST-002`
> 决策裁决：`ACCEPTED / Q-FAST002-001`；Implementation差量复审：`GO / NPDMS-FAST002-IDENTITY-CONTRACT-DELTA-20260830-FINAL`

## 背景

F-AST-002原Technical Plan把`tenantId`和`serviceIdentity`放入模块公开Query，再由AST配置注册表将调用方自报字符串映射为主体ID和动作。该方式只能证明字符串已登记，不能证明当前调用由对应服务发起，并且允许租户上下文缺失时相信Query租户；它与API SDS“租户、用户、数据范围和执行服务身份从服务端认证上下文取得”的规则冲突。

当前仓库没有可直接复用的具体OAuth客户端身份上下文。Yudao的`client_credentials`令牌虽保存`clientId`，但校验DTO和`LoginUser`不传播该字段；为本Feature修改共享OAuth DTO、安全主体和过滤器会改变Yudao基础平台，未经批准且超过当前模块化单体正向闭环。

## 决策

1. `AssetProductTypeApi`继续作为AST拥有的模块内公开契约，但公开Query不得携带可覆盖的`tenantId`或`serviceIdentity`：
   - `ProductTypeCodesQuery`只携带产品类型编码集合；
   - `AuthorizedDeviceProductTypeQuery`携带服务端已解析的委托用户ID和设备ID集合；委托用户ID不得直接取客户端自报值。
2. `pms-module-asset-api`新增唯一供Inspection消费的`InspectionAssetProductTypeApi`只读接口；其实现位于AST业务模块并固定消费者代码，只转调`AssetProductTypeApi`的两个查询，不接受任意动作、任意服务身份、任意租户或受控导入命令。Inspection不得直接注入通用`AssetProductTypeApi`。
3. 专用适配器实现调用AST内部、包级不可见的上下文持有器，在调用期间建立受控进程内服务调用上下文；上下文只包含固定消费者代码和当前租户，稳定主体ID与允许动作由AST注册表解析：
   - 当前租户只从`TenantContextHolder`取得；缺失时失败关闭；
   - 上下文使用普通`ThreadLocal<Deque<...>>`按栈管理，并在`finally`中恢复或清理；同步调用可嵌套，异步线程默认不继承；
   - 上下文持有器及任意主体设置能力不进入API模块且不是public类型；普通Controller、公开DTO和业务参数不得建立或覆盖该上下文。
4. AST注册表保留为授权映射，不再承担认证含义。AST最终守卫依次校验：调用上下文存在、上下文租户等于当前租户、消费者登记可解析为稳定主体、动作获准、委托用户有效、设备数据范围和请求设备交集。
5. 服务主体权限不能替代委托用户设备范围，委托用户权限也不能替代服务调用主体。已认证委托用户的设备范围为空时返回空；缺少服务调用上下文、缺少租户、缺少或非法委托用户时失败关闭。
6. 本决策提供模块化单体内的误用防护、最小动作约束和稳定审计归因，不宣称对同JVM恶意代码提供密码学隔离。未来出现HTTP/RPC或独立进程调用方时，必须由独立公共基础能力Feature引入可验证的OAuth客户端、mTLS或等价机器身份，并替换进程内上下文；不得沿用自由字符串。
7. 不修改Yudao OAuth、`LoginUser`、Token过滤器或既有平台接口；不新增HTTP Controller、通用RPC框架、HMAC机器令牌或第三方连接器。

## 备选方案

### 保留Query中的`serviceIdentity`

不采用。配置白名单只能授权已声明的名称，无法认证声明者，并允许其他模块冒用已知名称。

### 直接增强Yudao OAuth Client Credentials

本Feature不采用。该方案适合未来跨进程边界，但当前需要修改Yudao共享安全链路、定义客户端租户和权限模型，超过最小范围且未经允许。

### 删除服务主体，只依赖用户数据范围

不采用。产品类型编码校验包含无交互用户的模块服务调用，且正式SDS要求服务身份与业务对象范围同时校验。

## 后果与门禁

- F-AST-002先按本ADR修订07、10、14、20分册、Feature Spec、Technical Plan和当前Task，再整改Task 1代码。
- Task 1交付AST内部上下文持有器、注册表和最终守卫，并验证公开Query无租户/服务身份字段，以及缺少上下文、错消费者、错动作、租户不一致和上下文泄漏均拒绝。已认证服务主体但委托用户设备范围为空时返回空依赖批量设备范围与查询实现，由Task 5验证，Task 1不得提前宣称关闭该项。
- Inspection专用适配器接口与实现都在F-AST-002消费边界Task交付；实现固定消费者并封装上下文建立，Inspection只注入专用接口。架构测试必须证明API模块不存在通用`runAs(consumerCode, principalId, action)`或公开上下文设置器，AST上下文持有器不是public类型，Service生产代码不直接引用通用`AssetProductTypeApi`。
- 受控导入只接受当前已认证用户且具有独立`pms:asset-product-type:controlled-import`功能权限的AST维护入口；该权限不默认授予任何角色，不复用旧`pms:equipment:update`或普通设备更新权限。导入服务从当前安全上下文取得`actorId`和租户，直接校验`PRODUCT_TYPE_CONTROLLED_IMPORT`动作，不使用Inspection只读主体；入口、菜单授权和初始化数据在对应实现Task落位前，Task 4保持阻断。
- 本ADR不批准修改PRD、Yudao基础平台、CRM/MES连接器、Deployment、SIT、UAT或Release。