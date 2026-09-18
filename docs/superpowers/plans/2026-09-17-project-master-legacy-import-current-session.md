# 项目主档 Legacy 初始化数据直接导入（AI-MIG-000 / PROJ）当前会话记录

- 日期：2026-09-17
- 用户请求：pms_project 项目信息进行迁移 proj_project → 澄清后改向："都是初始化数据，直接 sql 记录插入到新表即可"。
- 意图澄清：proj_project 已被 V57 新主档占用（非重命名）；旧表 40 行为初始化数据，按用户指示直接 SQL 前向导入（废弃此前编写的 Java 两段式 PLT 证据迁移）。

## 交付

- sql/migrations/V260__proj_project_legacy_data_import.sql（唯一交付，5 条 INSERT，全部幂等守卫）：
  1. 主档 40 行：id=旧 id（1001-1040 与现存 920001+/9922e9+ 不相交）；project_code=trim+upper；
     customer 经 cus_customer_master 快照；manager 经 system_users（user 2/3 不存在→NULL）；
     project_type/category/industry/contract_code 原值（MIGRATION_UPGRADE 不在字典，登记待映射）；
     implementation_mode/major_project_level/company/department 不造（NULL）；
     coarse status 映射 0→S0/ACTIVE、1→S1/ACTIVE、2,3→S6/NORMAL_CLOSED；closure_type/closed_at 不补造；
     树结构按 ProjectTreeRules 规范化（根 tree_path=''，子=path 去前导'/'）；
     code_root_id=自身、sequence=0、code_rule_version='LEGACY_IMPORT'；source_type='MIGRATION'。
  2. 生命周期阶段行 40×7（S0-S6 创建语义：当前阶段 ACTIVE 其余 PENDING）。
  3. 树版本 10 行（每根 ACTIVE tree_version=1，node_count=节点数、path_count=闭包数）。
  4. 树闭包 70 行（自对 distance=0 ×40 + 根→子 distance=1 ×30），预生成确定 ID 段
     2600000000000+root_id / 2600000010000+root_id*10000+descendant*10+distance。
- pms_project 只读不改不删。

## 过程修正（实库暴露）

- 闭包行首版 NOT EXISTS(树版本) 守卫会因 3/4 语句先插版本行而跳过全部闭包行——已移除（唯一键守卫足够）。
- 排序规则冲突：旧表 utf8mb4_0900_ai_ci vs 新表 utf8mb4_unicode_ci——跨表/字面量比较处加显式 COLLATE utf8mb4_unicode_ci。
- 首跑失败后 Flyway repair + 重放成功；失败重放安全由 NOT EXISTS 守卫保证。
- 树版本 node_count 首版误写为 path_count（7）——修正为节点数（4）并更正实库 10 行。

## 验证（domain-test 实库 npdms_domain_test，Flyway 现版本 v260）

- 计数：proj_project 42→82；proj_project_stage 54→334；tree_version 4→14；tree_path 503→573；pms_project 不变 40。
- 状态分布与旧表一致：8 S0/ACTIVE、14 S1/ACTIVE、18 S6/NORMAL_CLOSED。
- 树 1001 抽查：版本 2600000001001 node_count=4/path_count=7；闭包 4 自对+3 根→子；根 tree_path=''、子 '1001/1011/'。
- 阶段行抽查：S6 闭环项目（1001/1004）S6 ACTIVE 其余 PENDING，符合创建语义。
- 幂等重放：手动重放 5 条语句全部 0 行，计数不变。
- 注：domain-test mysql 以 NPDMS_MYSQL_PORT=24306 重启（13306 被主 npdms-mysql-1 占用）。

## 未做

- pms-module-project 无 Java 改动（此前编写的两段式迁移代码与 V259 权限已按改向删除）。
- V260 未提交（等待用户明确提交指令）。
