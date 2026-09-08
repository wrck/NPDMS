# F-PLT-003 业务视图注册与页面/动态表单接入

> 文档状态：`BASELINE`
> Feature Ready：`READY`（用户方案批准；SDS08/09/10契约及Owner边界已落位，不代表实现或运行通过）
> Requirement：`PM-03`
> Requirement切片覆盖：`PM-03@V1=PARTIAL`
> Owner Context：`PLT`
> Implementation：`tasks/features/F-PLT-003.md`
> Technical Plan：`docs/superpowers/plans/2026-09-08-template-business-view-foundation.md`

## 权威与业务目标

遵循PRD V1.8 PM-03第9～14条、SDS04/07/08/09及用户2026-09-08批准的单一模板升级方案。授权配置人员把已实现的页面或动态表单注册为稳定业务视图；模板Stage/Task选择精确版本，已有页面保持Owner、业务命令、权限与文件范围。追加专用页面无需修改模板引擎。

## Scope

1. 视图身份为同租户`entityType/viewKey/revisionNo`，保存Owner、PAGE/DYNAMIC_FORM来源、组件精确版本、上下文Schema、支持动作和查询/命令/权限Provider引用。
2. 草稿创建/编辑、复制新版本、校验、发布、停用、版本查询；已发布内容不可覆盖，不提供通用删除。
3. PAGE复用受控页面适配器；DYNAMIC_FORM复用当前PLT已发布表单修订与渲染器，禁止复制填写值或领域实体。
4. 共用宿主按冻结视图与服务端上下文选择加载器，保留原业务API、版本、幂等、allowedActions及dirty保护。
5. 首批页面以现有需求分析面板验证；动态表单复用现有实例渲染链。尚无Provider的视图不允许发布为可用配置。

## Out of Scope

不重建动态表单、文件、BPM；不实现领域业务状态机、任意页面代码上传、动态脚本执行、独立验收创建/范围绑定或全部业务页面接入。视图渲染不是业务对象创建/完成；Q-TPLACC-001只限制其依赖路径。

## 领域与授权规则

- 领域字段组合、受控编码、生命周期与运行行为按SDS08“PM-03单一模板与业务视图来源”执行。
- 发布时校验组件清单与Provider唯一性、Owner/实体/动作兼容、上下文Schema及动态表单修订真实可用性。
- 配置管理权限不等于对象读写权；页面/表单读取和写入每次由Owner服务端重验，节点范围与Owner范围取交集。
- 预览、加载和readiness只读；创建必须是已定义的受控命令，未知结果不能记为成功。
- 停用只阻止新引用；历史精确版本可解释，Provider失效显示可恢复错误而不伪造操作成功。

## 数据与接口

数据采用SDS09既有`plt_business_view_revision`，补PAGE/DYNAMIC_FORM来源与表单修订引用。跨Owner用公开API，不读其他模块表。

公开API、请求/响应、锁序、幂等完成点及Owner配置权限已在SDS10“PM-03业务视图注册API与单一模板接入”落位。PM01在d31f7903释放共享边界，本DU在7a27daf8接续并同步；允许按当前契约实施注册和宿主，真实API/数据库/浏览器验证仍由Task记录。

## 验收

- AC-01：PAGE与DYNAMIC_FORM字段组合、受控标识、上下文及Provider兼容得到正向和负向验证。
- AC-02：完整注册—复制—校验—发布—查询—停用闭环，已发布正文与历史不可覆盖。
- AC-03：跨租户、越权、版本漂移、重复异载荷、未知Provider、缺失表单修订拒绝且无业务副作用。
- AC-04：同一个宿主真实装载已有页面和动态表单，原有页面独立入口保持可用；数据保存仍经对应Owner。
- AC-05：新增专用页面通过受控映射和注册接入，不改模板引擎；节点切换保护未保存内容。
- AC-06：真实MySQL、迁移及浏览器完成上述闭环，不以单元测试或页面存在代替。
