# 按实际命令的交付件验收矩阵

这是累计矩阵，第一包证据保持原文件，第二阶段只晋级实际六链。限定 PASS 只指列出的浏览器/控制器/事务/MySQL 夹具，UNIT 不替代业务端到端；NOTRUN、UNIMPLEMENTED_NATIVE、BLOCKED 不算通过。`32组生产入口/29条写路径` 缺可逐行复算的原表，当前不能沿用为通过数。工勘旧 `savefile/confirmresult` 源码符号已不存在；当前为实体 create/update/confirm/archive。

当前[源码索引](source-inventory.json)有17个直接策略实现标记、21个原生登记调用标记、5个组件7处原始 UploadFile。新增 NativeAttachmentFilePolicy 由六个 Bean 装配，故类数不是策略/业务入口数。索引不等于路由生产装配；另列 UploadImg 和 Retired 服务旧入口。所有来源与快照均为真实身份，未伪造历史 revision/file。

| 实际 Owner / 命令链 | 归集及公共完成 | 实际证据 / 结论 | 未完成部分 |
| --- | --- | --- | --- |
| SOL/requirementAnalysis save/complete/copy | 已有真实文件修订归根；第一包新增当前/历史统一台账 | 第一包后端文件/授权/成果与UI运行测试；UNIT | 完整上传/保存/复制/历史浏览器、Host 声明 |
| SOL/siteSurvey create/update/confirm/archive | 当前完整材料来源/原生归集缺口 | BLOCKED；仅源码核对 | 禁改页面/访问策略/Contributor；state3追加未决定；不能借旧 Preparation策略冒充 |
| SOL/solution 原页面客户文件 upload/attach | 第一包修复，保留原 remark/历史 URL | 真实 Chromium + 实际 Vue/helper + HTTP 夹具，UI 限定 PASS | 真实方案控制器/服务/数据库全链未跑；Host |
| SOL/solution reviewed客户 attach/生成HTML/审批成果 | 既有来源与 BUSINESS_RESULT | 第一包服务15、审批/成果与共享上传回归；UNIT | 完整方案/BPM/生成/下载浏览器 |
| SOL/solution 两套拓扑 asIsUrl/toBeUrl | 两组件四处 UploadFile 仍裸 URL | UNIMPLEMENTED_NATIVE | 独立图像用途/目录媒体、真实修订锚与原生编辑授权协调；不能套客户/生成文档用途 |
| SOL/briefing generated file / manual fileUrl | 生成真实文件/快照已有；手工文件仍裸 URL | 生成/策略 UNIT；手工 UNIMPLEMENTED_NATIVE | 独立手工用途/来源；不能扩大生成快照策略；Host |
| IMP/training 新授权 issue/reissue/render/confirm | 受控生成、实际新授权及成果，撤销/删除不能匿名降级 | 第一包服务7/授权14/成果5/上传授权3；UNIT | 完整真实生成/客户确认/归档/浏览器；Host |
| IMP/training 无授权历史 traininglink/reissue | 匿名 createFile 分支仍在，未假造历史授权/文件 | BLOCKED_BY_SPEC | 历史链接重发失效策略尚未决定 |
| IMP/arrival upload/update/material withdrawal | 第一包公共上传、真实材料/项目同一ID、显式替换旧材料撤回 | Chromium+控制器+真实服务+MySQL限定 PASS；Owner授权负例/回滚 UNIT | 完整实际登录/角色/项目树/下载、Host；失败后整页刷新恢复依赖公共查询 |
| IMP/configuration create/update/start/abnormal/complete | 六链之一；update归集，complete锁文件再冻结；原生配置日志与材料同事务 | 45项MySQL组合+配置日志去重/回滚+六页Chromium限定 PASS；状态回归5 | `.log/.cfg/.conf` MIME框架待接；实际资产日志写端口/下载HTTP/存储全链 |
| IMP/jointTest create/update/start/pass/fail | 六链之一；update归集，pass/fail锁文件再冻结 | 45项组合+六页Chromium限定 PASS；状态回归5 | 同上特殊文本MIME；完整实际联调业务、角色 |
| IMP/externalProcurement create/update/submit | 六链之一；保存归集、提交冻结、既有审批/CAS | 45项组合+六页Chromium限定 PASS | 原生withdraw/terminate未使材料失效；完整审批/BPM/角色 |
| RES/outsourceRequest create/update/submit | 六链之一；真正RES根，SITE_SURVEY关联保留，提交冻结 | 45项组合+六页Chromium限定 PASS；执行关联/事务/版本13 | 原生withdraw/terminate材料失效；完整工勘关联审批/角色 |
| IMP/materialRequisition create/update/submit | 六链之一；实际设备选择/CAS、保存归集、提交冻结 | 45项组合+六页Chromium限定 PASS | 原生withdraw/terminate材料失效；真实设备/审批/角色 |
| IMP/materialExchange create/update/submit | 六链之一；理由文件实际来源；序列快照与CRM分支保留 | 45项组合+六页Chromium限定 PASS；序列15/CRM负例3 | 原生withdraw/terminate材料失效；实际审批/CRM联通未通过 |
| 上述六链 replace/detach / material withdraw | 替换/解绑使冻结旧文件不可用，旧ACTIVE使计数失败；撤回旧材料后新满足；历史两个版本保留 | 六类真实MySQL替换/解绑、六页材料撤回限定 PASS | **不等于业务状态withdraw/terminate已失效**；公共Java合同待父任务 |
| 资产配置日志 issueGrant/download | 旧URL兼容；原生定位符精确材料/冻结版本/设备+文件双授权 | 实际下载服务23项 UNIT；实际文件替换/解绑事实由MySQL另验证 | 资产真实HTTP/实际日志writer/实际存储下载全链 NOTRUN |
| KNO/announcement create/update/publish/disable | fileUrl仍裸URL，只有公共租户只读绑定 | UNIMPLEMENTED_NATIVE | 非项目原生Owner写桥与用途策略；query/update/state0/版本锁，不能把只读绑定当授权 |
| IMP/installation photoUrl | UploadImg仍裸URL | UNIMPLEMENTED_NATIVE；源码定位 | 原生图像用途、材料来源/保存事务/统一展示 |
| ACC/acceptance 旧 create/update/confirm attachmentUrl | 原 UploadFile，不是新报告根 | UNIMPLEMENTED_NATIVE | 旧真实ACC/acceptance独立策略/来源/归集，不能换挂新活动 |
| ACC/acceptanceActivity saveDraft/submit | 新独立报告登记文件与成果，绑定投影保持要求 | 第一包报告命令/访问授权 UNIT | 实际报告上传/结果/归档端到端 NOTRUN |
| ACC/satisfaction response/reservation/decision/generated | 已有来源、文件/生成与成果登记 | 第一包六套件19（包括报告）/授权上传 UNIT | 客户/代办/生成/归档浏览器 NOTRUN |
| ACC/completionCertificate customerConfirm/archive | 实际成果登记 | 第一包服务10 UNIT | 原生完整办理浏览器 NOTRUN |
| ACC/archiveDocument archive | 实际来源与归档成果 | 第一包服务11 UNIT | 归档/下载/失效实际业务端到端 NOTRUN |
| ACC/deliverableChecklist update/submit/withdraw | 第一包附件公共归集、PASS成果独立、提交冻结 | Chromium+控制器+真实服务+MySQL限定 PASS；文件/成果回归 | 实际核对/关联验收、登录/项目角色/下载 NOTRUN |
| Preparation ITEM证据 patch/review | 有SOL/SITE_SURVEY_ITEM文件策略，暂无材料来源/登记 | UNIMPLEMENTED_NATIVE + lineage协调 | 明确Preparation/item/实体根/历史来源，不能称当前工勘已接入 |
| ConstructionPlanChange延期客户证据 | 有SOL/CONSTRUCTION_PLAN_CHANGE文件策略，暂无材料登记链 | UNIMPLEMENTED_NATIVE | 真实constructionPlan根/change来源、项目经理/客户授权与冻结证据 |
| SRV旧 execution evidenceUrl / offlineFile fileUrl | Retired旧服务仍两处UploadFile/裸URL保存 | 旧范围维护缺口；未扩实施 | 废弃路径只可授权历史维护/安全修复，不承接新功能；不计生产通过 |
| CUT/新 arrivalAcceptance | 本任务未取得完整生产装配/入口证据 | NOTRUN | 当前依赖/源代码不等于完整Boot/业务验收 |
| 六Host DELIVERY / 通用默认框架/泛型桥 | 四SOL+两IMP声明缺口仍在禁改Contributor；ACC五已有 | BLOCKED / 并行01a10fe7，NOTRUN | 父任务处理禁改边界；公共目录/Owner/冻结/CAS/迁移组合后整合验收 |
| 项目创建/详情/ProjectArrivalReceiptPanel | 本任务明确排除 | OUT_OF_SCOPE | 不修改或迁移 |

六链的成功验证使用专用来源目录和数量义务夹具，**没有生产目录/义务初始化通过证据**。给业务类型创建生产必交要求仍须父任务按真实契约协调配置/种子和迁移；不靠注册调用数替代。所有新材料是实际 artifact/version/hash，不造旧 revision。

## 精确剩余源入口

| 路径（相对前端 src/views/pms） | 行/字段 | 责任与状态 |
| --- | --- | --- |
| engineering/announcement/index.vue | 194 fileUrl | 原生未实现+非项目公共桥协调 |
| engineering/briefing/index.vue | 187 fileUrl | 手工附件原生未实现；生成链保留 |
| engineering/solution/SolutionChapterForm.vue | 151/176 asIsUrl/toBeUrl | 两拓扑原生未实现 |
| engineering/solution-reviewed/SolutionReviewChapterForm.vue | 107/132 asIsUrl/toBeUrl | 两拓扑原生未实现，同一SOL根 |
| acceptance/acceptance/index.vue | 175 attachmentUrl | 旧验收原生未实现 |
| engineering/installation/index.vue | 167 photoUrl | 施工照片原生未实现 |
| service/srv-task/index.vue | 326 evidenceUrl / 409 fileUrl | Retired旧范围，未计新生产任务 |

工勘与培训精确阻塞、公共合同目标、禁止目标及复验限制见[交接](handoff.md)。这个矩阵完整登记已核对链及缺口，**不宣称所有业务已通过端到端验收**。
