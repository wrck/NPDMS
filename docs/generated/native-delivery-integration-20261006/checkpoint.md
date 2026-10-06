# 原生统一交付件 WIP 集成检查点

本检查点用于 wrck/NPDMS 的临时分支 integration/native-delivery-20261006，基于 fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721。2026-10-06 父任务转达用户已批准分别临时分支 checkpoint 提交并普通 push，用于联合整合；没有合入 fix/master、force push 或部署。目标远端分支写入前核查为空。**发布检查点不代表全部业务验收通过。**

实际内容为第一41文件包和第二64文件包共105文件，原包与当前文件哈希完全一致；后续仅核对代码和返回未接入清单，尚无新的原生实现落地。附 changed-files.json 的完整清单、基点/最终SHA和本目录清洁测试证据；原包文件没有为本检查点重写。额外三个文件就是本检查点说明、清单和测试结果。

第一包补到货、需求分析台账、旧方案客户文件、核对清单接入；第二包补配置、联调、外采、外包、领料、换料实际Owner接入与配置设备档案定位符/双授权下载。第二阶段后端9类109项、前端5文件31项、六个真实Chromium页面通过限定链；方法授权控制器、实际事务服务和独占tmpfs MySQL，外部权限/项目树/设备/技术存储端口受控。第一包34类245、9文件62是原有限定证据，不直接与第二包相加。

证据：[第二阶段交接](../native-delivery-stage2-20261006/handoff.md)、[累计验收矩阵](../native-delivery-stage2-20261006/acceptance-matrix.md)、[逐用例清洁结果](test-results.json)、[完整文件哈希](changed-files.json)。没有真实Flyway迁移、完整应用登录/实际角色树、资产实际HTTP/外部存储下载、CUT或新到货验收生产全链通过证据。旧全ts28失败、integration构建歧义及V374原始collation失败保持历史范围。

## 仍未完成

- 外采/外包/领料/换料的业务withdraw/terminate只变原生状态，尚未撤回材料；现有已通过的是显式“撤回材料”，不能替代业务命令失效。需公共可信Java撤回合同按确切Owner/来源/材料锁与CAS，在原生事务中幂等撤回、失败同回滚、保留历史，状态1/2撤回不能借可编辑态HTTP绕过。父任务确认接口后接线。
- KNO公告fileUrl仍裸URL，真实Owner无projectId；原生权限是query/update与状态0，租户只读绑定不是写授权。经现有SPI可独立实现，无代码已落地。
- SOL交底手工fileUrl仍裸URL；生成文档链已存在，手工需独立用途，不能扩大生成快照策略。经现有原生SPI可独立实现。
- 旧ACC/acceptance的attachmentUrl仍裸URL，独立于新验收报告活动；现有ACC成果SPI不覆盖旧根，可独立补原生接入。没有移挂实体。
- 两套SOL方案拓扑四处asIsUrl/toBeUrl、IMP施工photoUrl仍旧图像URL；需要独立图像用途/真实修订锚及相应策略。旧方案章节是首包文件，保持该包基线；图像目录复用及媒体合同仍需具体协调。
- DurationChange已有CUSTOMER_DELAY_EVIDENCE文件策略，缺统一材料来源与登记；需沿实际constructionPlan/change和现有项目经理/客户角色锁，把提交前已锁证据归集，不能省略原角色授权。尚在核对根与修订映射。
- Preparation ITEM已有文件策略，但材料根/历史lineage未明确；不冒充当前工勘。SRV旧执行证据与离线文件为Retired旧范围，仅可获授权历史维护，不承接新Feature。
- 四禁改目标、全局FileUpload.complete钩子禁止边界仍保留；六Host DELIVERY声明、工勘state3追加、历史无授权培训重发失效未决，未绕过。新培训授权撤销/删除不降级匿名。
- 公共CAS/SPI/MIME/目录组合由框架任务处理。log/cfg/conf空浏览器MIME仍需安全识别；原生5MiB与公共50MiB提示需对齐；失败上传整页刷新后的稳定键恢复公共查询尚待合同。没有SQL/V398迁移。

## 排除项

运行目录.run/、Docker临时compose/私有DB内容、target/、node_modules/、工具链、凭据/Token/私钥、仓库外ZIP与完整运行日志均不提交。清洁证据只保留专用夹具JSON/截图、测试汇总/用例结果和文件哈希；新增敏感模式扫描无命中。独占MySQL退出已清理，未触碰共享或生产DB。整合与剩余实现由父任务继续安排，检查点不是任务完成声明。
