# 四个项目业务身份的公共读取范围接入

基线 `d4fca642fe17a230286ae9ef6ed5ea883f927701`，全部变更未提交、未推送。生产实现仅在 `LegacyProjectReadBindings.java` 同一个集合增加四个身份：`IMP/configuration`、`IMP/jointTest`、旧 `IMP/installation` 记录、`COM/deliveryScope`。它们复用 `OwnerProjectReadScopePolicy` 的 `projectId` 与公共 `ProjectBusinessScopeAccess`，不复制实体专用策略，也不改变任何原生业务服务、状态机、权限声明、字段投影或写入操作列表。

当前待发布实现是四个文件：上述绑定、`yudao-server/pom.xml` 的测试范围依赖、一个应用装配模块的共同 MySQL 验收类、一个独占运行脚本。测试从第一阶段工程模块移到已有完整模块装配的 yudao-server，避免增加业务模块之间的生产依赖或复制整套权限/数据库夹具。原13项快照源码、patch、红绿日志和两次夹具错误仍保留，当前结果以本目录为准。

## 范围依据

| 身份 | 原生读取依据 | 公共读取边界 |
|---|---|---|
| IMP/configuration | ConfigurationDO.projectId；目录与控制器 `pms:imp-configuration:query`；NativeAttachmentAccess query + PROJECT_VIEW | 租户、真实登录主体、原生query权限、当前项目VIEW；原字段投影 |
| IMP/jointTest | JointTestDO.projectId；目录与控制器 `pms:imp-joint-test:query`；同一原生附件访问器 | 同上 |
| IMP/installation | 旧imp_eng_installation的InstallationDO.projectId；控制器与目录 `pms:imp-installation:query`；原生Mapper查询保持租户插件 | 公共读取额外按当前Project VIEW裁剪，不能替代新InstallationRecord的设备分配、确认或位置生效流程 |
| COM/deliveryScope | DeliveryScopeDO.projectId；目录/控制器 `pms:commerce:scope:query`；CommerceDeliveryScopeQueryService明确ProjectScopeApi.ACTION_VIEW，SQL显式租户/项目集 | 同样的租户/原生query/当前VIEW；公开projectId筛选与权限集取交集；历史行仍须当前范围，原生API同权限支持includeHistory |

## 当前源码验收

独占 tmpfs MySQL `27601/imp_project_reads_verify`，实际四类 DO/Mapper、原 XML、两个生产 Contributor、Spring公共读取装配及真实系统 Role/Menu/Permission 服务和表。ProjectScopeApi范围事实受控；生产授权树算法、缓存代理和完整登录未在本段运行。

四身份共同选择集为 **28通过、0失败/错误/跳过**。新增两项在尚未接入时，同一测试/POM/脚本为 **17通过、2断言失败、9错误、0跳过**；错误集中在新增安装和交付范围合法读取的 `SCOPE_POLICY_NOT_DECLARED`，已接入两项保持通过。新增实体逐项前后用例见[test-results.json](test-results.json)。第一阶段13项被当前选择集覆盖并扩展，不累加。

| 检查 | 用例数 | 当前结果 |
|---|---:|---|
| 本项目实际行、跨项目/已删除/无Owner拒绝、游标分页、客户端筛选不能扩权 | 4 | 通过 |
| 空/null项目范围列表为空且详情拒绝 | 4 | 通过 |
| 跨租户行、tenant/user伪造身份拒绝 | 4 | 通过 |
| 原生query权限独立校验、撤销单实体权限后其他已授权实体正常 | 4 | 通过 |
| query-only及原生写权限用户的统一CREATE/UPDATE/DELETE/COMPLETE都拒绝；行code/orderNo与version不变，幂等/审计/事件端口未使用 | 4 | 通过 |
| 真实Owner改归属及当前项目范围撤销后读取立即收紧 | 4 | 通过 |
| 原有配置/联调原生文件READ可用，UPLOAD/REPLACE/DETACH仍被query-only用户拒绝 | 2 | 通过 |
| 未接入IMP/arrivalAcceptance仍安全拒绝 | 1 | 通过 |
| COM原生Mapper当前/历史行与公共读取核对，跨项目原生查询为空 | 1 | 通过 |

[36项源码哈希](source-files.json)和[日志/XML/编译class哈希](private-evidence-manifest.json)固定当前结果；[implementation.patch](implementation.patch)固定完整实现diff。专用Compose已删除，共享13306/16379原容器保持运行，见[边界与清理](boundaries.json)。

运行：激活 `/workspace/toolchains/activate.sh`，执行 `IMP_READ_MAVEN_SETTINGS=/workspace/toolchains/maven-settings.xml bash scripts/tests/verify_legacy_engineering_project_reads_mysql.sh`。

## 有界分类与停止项

[剩余32项逐项分类](remaining32-classification.md)选出本段2项；其他30项继续拒绝：8项无Project Owner目录/版本、6项商业/客户域范围、7项严格原生Owner/投影、7项父级/引用/批次多Owner、2项受信依赖或独立项目活动。所有来源DO与关键授权代码的hash在[分类JSON](remaining32-classification.json)。没有把辅助表的冗余projectId等同授权。

具体停止依据包括：割接审批只允许发起人、合格当前审批人或终态摘要，Project VIEW不足；割接收尾还验证任务/方案/审批/版本冻结一致性；到货验收受信Context和QueryService明确生产COM/AST依赖接通前不注册；任务指派只有projectTaskId且原Task不在当前父目录；凭据、采集任务保留设备/协议/命令/有效期；客户/合同保留本域可见集合。无证据项需要原生公开范围/投影适配、可信Owner关系或依赖接通，本轮不猜关系或制定新产品权限。

与原清单相比，已有范围绑定数推导为49、缺口30；这不是全79生产查询验收。本段没有重建普通应用JAR、运行完整Tomcat/login/UI/浏览器、全量后端或实际树授权/Redis缓存回归。上一轮普通JAR结果不作当前新源码证明。KNO与四禁改路径不动；V374不编辑历史SQL、不repair/skip；旧阶段合同不补造；项目创建/详情/真实历史迁移维持独立活动。

完整待发布路径和逐文件hash见[publication-files.json](publication-files.json)。本段发布尚待复核，未借用此前33文件的推送授权。
