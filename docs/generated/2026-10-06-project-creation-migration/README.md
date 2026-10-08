# 本机项目创建、详情与历史迁移验证

> 2026-10-07 最新结果：最终客户与购货方分离已实施；28 项相关后端测试、6 项前端测试、45 项真实 HTTP/浏览器检查通过。十个真实项目因权威最终客户身份缺失全部隔离，实际迁入 0。详见 [customer-decision-validation.md](customer-decision-validation.md)。下文前期验证保留为历史证据。



2026-10-06；工作树 M:/AICoding/CodexData/worktrees/4600/NPDMS；分支 fix/visio-template-followup；HEAD fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721。未 commit/push/merge/deploy，保留用户与并行改动。



## 已实现并验证



合同下选择销售订单头，执行单沿订单关系带出；服务端校验租户、公司、来源身份、关联与来源摘要，锁定关系并在创建事务内再次核验。复用正式授权、幂等、模板实例化和初始化。两套创建页面与详情页均已通过真实登录和浏览器验收，保留客户主档权威身份和源字段。旧 project 路径和旧 Struts 源码未修改。



公共文件只应用用户精确批准的 DeclaredBusinessDeliveryBridge 显式构造器及两个 @Lazy 补丁，SHA-256 为 7bc13fcaf306be198a1dcf7792c554815740ed7cb6847ef6d55784eb83c93b86。后端成功启动；旧版启动阻塞结论已被本次真实启动证据替代。



真实 HTTP 发现的来源异常被显示成系统 500，已在来源代码内改用现有 invalidParamException，保留校验并重新验证。真实浏览器发现新功能详情未接入完整基本信息组件，已复用组件，保留命名空间、父项目、状态及主联系人。



## 验证结果



| 状态 | 验证 | 证据与边界 |

|---|---|---|

| PASSED | 最终后端回归 77 项 | http-error-transport-tests.log、backend-test-final-summary.json |

| PASSED | 真实 MySQL 8 项 | mysql-company-relation-tests.log；公司校验、关系摘要/锁及并发修改回滚。此后 SQL 未改，后续仅异常类型传输修复 |

| PASSED | 真实 HTTP 24 项 | http-acceptance.json；两入口、来源校验、授权拒绝、幂等重放/冲突、同合同并发仅一次成功，拒绝系统 500 |

| PASSED | 晚期绑定失败整笔回滚 | http-late-failure-rollback.json；实际已分配 ID 区间 [992203060186,992203060187)，业务错误 1016002001；118 处存储无孤儿记录，原执行单绑定不变 |

| PASSED | 真实 Chromium 14 项、两入口均创建并查看详情 | real-browser/results.json 及截图；真实后端，无模拟登录；含授权用户零金额显示 |

| PASSED | Hook 6 项、档案工具 14 项 | 前期证据 frontend-tests.log、archive-identity-schema-tests.log；此项为前期证据；本轮 Hook 已按客户裁决更新并复验。独立复验另记录档案 21 项 |

| PASSED | 合成历史档案 dry-run | archive-dry-run.json：5 输入，2 保留、1 重放、2 隔离，运行项目创建数 0 |

| PASSED | 后端 package、完整启动与源码匹配 | candidate-final-package.log、final-startup-evidence.log、candidate-source-match.json、final-environment-evidence.json |

| FAILED | 前端全量类型检查 | 早期 frontend-types.log 有 28 项未修改文件错误；最终 frontend-types-final.log 达到 3GB 堆限制退出，未完成，不能声称最终仍恰好 28 项 |

| FAILED | 既有模板服务测试 | HEAD 独立副本与修改后均 25 项中 4 失败、3 错误；baseline-comparison.json，未降低模板规则 |

| FAILED | 专用空库初始化 | 原 Flyway V374 第72行 MySQL 1267 排序规则冲突；未改公共迁移、未重置库 |

| NOT RUN | 金额受限身份的负向浏览器检查 | 当前租户无现成“合同可读且非敏感读取/非超级管理员”身份，未改共享用户权限；服务端脱敏单测已通过 |



实际环境 npdms-domain-test：MySQL 24306 / npdms_domain_test，Redis 24379 DB15；观察 Flyway V390，本活动未对共享库执行 Flyway。最终 JAR SHA-256：2528abf82a195249ce27ce0ad9529714353e2e3b697c5f1bf3df3fcec287672b。测试结束仅关闭本活动 Java 51420 / Vite 38668，未停其他活动服务。保留 73 条专用合成来源记录及 6 个合成运行项目，见 final-test-data-inventory.json。



## 历史迁移边界与新授权



前期 scripts/legacy_project_archive.py 是离线只读档案工具，不能替代新系统项目实体迁移。映射、幂等、异常隔离、回滚和合成 dry-run 见 migration-runbook.md；旧状态未冒充新模板阶段或门禁完成。



用户 10:50 新增授权：从真实旧项目中筛选类型有代表性、合同/执行单/订单/发货完整的10个项目，迁入 npdms_domain_test，指派状态为待指派。本批替代此前这10个项目仅只读档案的限制；源系统只读，目标保留既有数据，合法唯一映射不足则隔离。该真实批次已完成526候选的只读筛选：139项来源链初筛通过、97项组织范围可用，10项备选完成来源预检与数量/金额对账。客户语义已于2026-10-07确认，实际迁入0项；可信最终客户身份映射仍缺失，不能将上表合成验收成功等同真实业务通过。最新事实见 [real-pilot-preflight.md](real-pilot-preflight.md)。完整真实候选和对账报告仅保存在仓库外本机私有批次目录，不上传远端。



## 文件与复核



source-files.sha256.json 是当前45文件清单（新增商务详情字段与权限测试纳入本次范围）。最终客户裁决后的 JAR 与源码匹配见 customer-candidate-source-match.json；post-review-boundary.json 标明独立复验后的8处变化（7文件异常传输与断言、1个详情页），final.diff 是限定范围已跟踪差异。docs/decisions/open-questions.md 含保留的其他活动追加；其他设备日志/交付件活动文件不纳入本任务修改。



源码取证见 audit.md。旧报告快照、首次失败和修复前证据保留；不把脚本时序错误、内存失败、合成组件 HTTP 测试写成真实业务通过。

