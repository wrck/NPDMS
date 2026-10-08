# 2026-10-07 最终客户与购货方：实现与本机验证

项目创建与详情的客户角色已分离。十个真实项目仍未迁入：业务口径已裁决，缺的是可核验的最终客户身份映射。

## 已批准的语义与实现

- 用户 02:22 UTC：“沿用最终客户，并另外保留购货方关联”，transcript `Sentinel_22efbbfe469c81919260dc5e3e478c6a`。Q-PROJECT-CUSTOMER-20261006-001 已标为 RESOLVED；没有重复请求十项目写入授权。
- 两个创建入口独立选择最终客户 CUS 主档；服务端读取规范身份并检查租户、可见范围和启用状态。合同不能替代最终客户选择，购货方编码不再用于项目客户解析。
- 合同下选择订单，执行单仍由订单关系带出。来源窗口单独展示购货方及执行单最终客户名称；异步响应不覆盖用户选择的最终客户。
- 详情分别显示项目最终客户、合同购货方和订单购货方；新增订单购货方字段继续受 `pms:commerce:contract:sensitive-read` 控制。合同/订单的原客户数据和关系保留。
- 本轮未改公共框架。已获精确授权的 DeclaredBusinessDeliveryBridge SHA-256 仍为 `7bc13fcaf306be198a1dcf7792c554815740ed7cb6847ef6d55784eb83c93b86`。

## 验证结果

| 状态 | 检查 | 结果与证据 |
|---|---|---|
| PASSED | 创建服务及输入契约 | 25 项，包含缺失/停用/跨租户最终客户、购货方与最终客户不同、原绑定失败及幂等路径；customer-backend-tests.log |
| PASSED | 商务详情权限 | 3 项，有敏感权限返回购货方，无权限返回空；buyer-detail-backend-package.log |
| PASSED | 前端来源与合同选择 | 6 项，含旧响应竞态、加载失败和不覆盖最终客户；customer-frontend-tests-retry.log |
| PASSED | 实际 HTTP 与 Chromium 创建闭环 | 36 项；两入口各创建一个专用合成项目，最终客户与购货方不同，缺失客户被拒绝，重放无重复、改客户同幂等键冲突；customer-http-browser-results.json |
| PASSED | 最终构建的真实详情 | 9 项，API 和两入口浏览器分别显示最终客户及订单购货方；buyer-detail-acceptance.json |
| PASSED | 构建、启动、源文件匹配 | 最终 JAR `4d696be2789d92573f33a36d41a34c4e75bd24d869e33cce3ca776a35288472a`；28 项相关后端测试均无失败/跳过，customer-candidate-source-match.json |
| PASSED | 真实十项目刷新和 dry-run | 源包 10/10 与前次一致，139 订单行、653 发货行；来源预检 10/10。最终客户身份不足，10 项全部隔离；pilot-customer-dry-run.json |
| PASSED | 目标十项目保护 | 7 组目标表前后内容完全一致，真实项目 0、新增客户 0；target-ten-unchanged.json |
| FAILED → 修正后 PASSED | 首次前端命令 | Vitest 4 不支持 minWorkers，未执行测试；去掉无效参数并使用仓库配置后 6 项通过，初始失败日志保留 |
| NOT RUN | 真实十项目创建/重放/已提交批次撤回/API/浏览器 | 缺可信最终客户主键映射，不能以合成验收代替 |
| NOT RUN | 本轮全量前端类型检查、全量模板基线 | 未重复前期已记录的堆内存失败/既有模板失败；本次不声称这些检查通过 |
| NOT RUN | 敏感字段受限身份的真实浏览器 | 无现成适用身份，不创建或修改用户权限；服务端遮蔽测试通过 |

本轮保留专用合成来源 6 条及合成项目 2 个，前缀 `PCREATE-20261007-FINAL-b5e730`。没有把它们计入真实迁移。测试使用真实登录；MySQL 24306/npdms_domain_test，Redis 24379/DB15；后台 Quartz 和事件轮询关闭。前期验证表是当时构建的历史证据；关于“合同代替客户选择”的旧断言已由本轮测试替换，不能累计为当前行为。

## 十项目身份缺口

只核对已选十项目，未扩大至 93 客户集合。现有 CRM 视图明确最终客户外键是 `new_order_ack_headerBase.new_account_id → AccountBase.AccountId`，购货方外键是 `new_contract_party_a_account_id`。但本批执行单号在当前连接的 `new_id` 和保留旧键 `new_initialized_id` 均命中 0；五项有旧对象键、五项为空，已有键在视图 `oldObjId` 也命中 0。没有把名称相同当同一客户，没有伪造 ERP 编码。

已向用户请求这十个旧项目到可信 CRM AccountId/CUS 编码的权威映射，或包含这些执行单的正确 CRM 连接。数据补齐后须重新验证源摘要、CUS 范围/启用、复用目标版本，并完成受控公司码规范化、订单行/发货关系与正式匹配选模。旧状态、成员及完成历史只保留来源证据，不直接写成新阶段或 Gate 完成。

完整真实数据和 SQL 探查只在本机私有目录 `C:\Users\user\Documents\Codex\2026-10-06\task-3\legacy-pilot-20261007-final-customer`，报告仅保存计数、键规则和摘要。未修改旧系统、源库、凭据、同步配置、权限、公共迁移或其他活动文件；没有 commit/push/merge/deploy。

## 2026-10-07 来源身份复核补记

[最终客户身份只读追溯](final-customer-identity-audit.md) 更正早期“旧最终客户必填”的表述：该校验实际在 JSP 注释块中。用户指定 CRM 连接已确认；缺失的是本批执行单到真实最终客户 FK 的可验证关联，不再要求确认客户语义或笼统提供另一连接。早期购货方代码查询不能作为最终客户身份结论。十项仍未迁入；此次补记保留此前测试和取证的历史范围。
