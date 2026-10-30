# 项目列表大范围查询栈溢出修复

日期：2026-10-24。用户授权修复前轮发现的问题；仍遵守不重启、不部署约束。本轮修改代码及局部证据，不再修改数据库。

## 问题与修复

成员授权确认后，z01010可见范围增长至数千项目，项目列表返回code=500。运行日志记录`StackOverflowError`及`ObjectOutputStream`递归调用。原ProjectTreePathMapper批量根路径、后代路径SQL以深层OR串联大量项目树条件；生产JDK序列化SQL缓存无法处理该深度。

真实Mapper绑定SQL的失败测试已确认：5000组根/版本与5000组子树均在生产JsqlParserGlobal缓存中触发StackOverflowError。测试固定512KiB工作线程栈，避免受Maven进程栈参数影响；未增加运行服务堆栈或关闭缓存、租户、授权规则。

修复为：

- 根路径按`(root_project_id,tree_version) IN (...)`保持完整键配对。
- 后代路径按`(root_project_id,tree_version,ancestor_project_id) IN (...)`匹配，Query仅将既有子树/祖先集合展开为精确三元组，保持原调用接口。
- 空/缺失树集合、空/缺失祖先集合仍生成`AND 1=0`；混合空子树不会丢弃其他有效范围。租户、逻辑删除及根路径限定原样保留。
- 不将各维度拆成独立IN集合，避免不同项目树、版本和祖先交叉组合造成越权；单树授权、角色、成员资格、有效期与写入锁重验不变。

SQL形式参考[MySQL行构造器说明](https://dev.mysql.com/doc/refman/8.0/en/row-constructor-optimization.html)；参数仍使用MyBatis绑定，见[MyBatis动态SQL说明](https://mybatis.org/mybatis-3/dynamic-sql.html)。

## 实际验证

- 固定JDK25、工作树Maven依赖仓库及同一独占构建锁执行。
- Red：ProjectScopeBatchSqlTest共3项，原语义测试通过，2项大集合缓存测试均因StackOverflowError失败。
- Green：29项定向测试通过，0失败/错误/跳过。包括ProjectScopeBatchSqlTest 3项、ProjectAllScopeBatchTest 5项、ProjectTreeScopeServiceTest 21项。
- SQL行为测试覆盖跨租户、逻辑删除、精确根/版本配对、祖先隔离、空集合、混合空祖先和null子树集合。现有服务测试继续覆盖角色、发现后授权事实重读、版本重验等边界。
- 目标MySQL只读等价SQL比较：2881组真实根/版本，旧OR和新元组IN均返回相同2881条路径；该轮耗时0.537/0.054秒，新SQL执行计划使用uk_proj_tree_path范围访问。这只是SQL级对照，不是运行接口或浏览器时延验收。
- 自审及git diff --check通过；未提交或推送。

日志、测试命令及MySQL结果：`.run/permission-data-audit-20261024/query-repair/`中的`red.log`、`green.log`、`test.ps1`、`mysql-readonly.py`、`mysql-readonly-result.json`。

## 完成边界与其余问题

代码已修复、相关模块及依赖编译和29项定向回归通过；未进行完整发布打包、部署、服务重启或修复版真实浏览器验收。原运行进程仍不能视为已经应用该源码修复。

224条成员区间冲突仍保留原状，涉及32账号/107项目，其中15组30条还会违反现有唯一键。决定哪些业务区间有效不能由工程实现猜测；见open-questions中的Q-PROJ-MEMBER-OVERLAP-CONFIRM-20261024。本次没有删除历史、放宽约束或任选一条确认。

BASE_P31原同步指纹保护也未降低。已确认授权的137049条记录属于显式人工变更，原任务重跑拒绝覆盖它们是现有保护行为；不通过改写来源、绑定指纹或取消检查冒充重跑成功。若需重新运行该历史同步，必须另行明确当前授权与来源历史的归属/重放契约。本轮不执行真实同步。


## 2026-10-24 独立部署补充

用户确认保留59285现有Vite前端，因此修复后端改为59286，独立验收前端为19086。通过scripts/worktree-build.ps1完成完整打包，源码指纹一致且22项既有修复保护检查通过。标准冻结步骤因原独立实例PID文件缺失退出；独立包装器在确认BUILD SUCCESS和保护检查后冻结产物，没有降低源码或保护校验。

冻结应用SHA256：`6d1a0f78a2cb4c91b50a2df692fd4c8487b266a0dc876e7e40f1b0f2395797de`。后端PID33540，local,dac配置，59286健康检查status=UP；数据库迁移、自动建表、调度及异步消费者保持关闭，运行时辅助包仅隔离调度，不替换业务类。

逐项复核原监听地址与PID不变，包括59191、59285及其他既有服务。224条冲突成员的全部列哈希一致，Flyway仍17条记录，V515不存在。证据见.run/permission-data-audit-20261024/deployment-59285/deployment-59286-verification.json与protected-data-after.json。

真实账号验收尚未完成：19086已打开z01010登录页并关闭记住我，等待用户登录。服务经理和项目经理的项目列表、详情及越权拒绝不能以构建、健康检查或管理员会话代替。
