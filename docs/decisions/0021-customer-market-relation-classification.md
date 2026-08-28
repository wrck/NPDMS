# ADR-0021：客户市场行业划分模型

## 状态

`ACCEPTED`

## 日期

2026-08-13

## 需求与证据

- 需求：CUS-03、PM-01、INT-01、INT-03
- PRD：客户和项目均需要行业信息，CRM是客户与项目市场行业字段的权威来源。
- 数据元：`pm_project_market_relations_from_sms`包含`marketCode/marketName`、`systemCode/systemName`、`expendCode/expendName`、`industryCode/industryName`。
- 数据模型门禁：P3-E09 / AI-MIG-000。

## 背景

历史设计把`pm_project.column004～006`解释成市场部、系统部、拓展部组织关系，并写入项目公司—部门关系；把`column007`单独写入`industry_code`。业务确认上述四组值共同表达客户市场行业划分，不是平台组织部门，也不是四个彼此独立的普通字典值。

`pm_project_market_relations_from_sms`不含项目ID或客户ID，它是CRM同步到旧系统的市场行业组合目录，不是项目关系表。

## 决策

1. 该模型归属CUS领域，目标表命名为`cus_market_relation`。
2. 物理字段沿用原业务语义，仅按数据库命名规范转为snake_case：
   - `market_code/market_name`
   - `system_code/system_name`
   - `expend_code/expend_name`
   - `industry_code/industry_name`
3. `cus_market_relation`保存CRM同步的合法市场行业组合，用于匹配、校验、筛选和对账。
4. `proj_project`和`cus_customer`直接保存上述八个字段，不保存`market_relation_id`或其他关系ID。
5. 项目与客户字段是各自业务发生时的当前值和展示快照；客户变更不得自动覆盖已有项目，项目变更也不得反写客户。
6. 历史变化通过业务字段变更审计、CRM同步批次和迁移原始证据保留，不另建基于关系ID的历史链。
7. `pm_project.column004～007`分别映射项目的`market_name`、`system_name`、`expend_name`、`industry_code`，不得再生成公司—部门关系。缺失编码不得根据名称猜测，进入待映射或保留空值并留痕。
8. `pm_project_market_relations_from_sms`逐行映射到`cus_market_relation`；不完整、重复或层级冲突的组合进入迁移问题表，原始行不得修改。

## 影响

- 项目组织关系只表示真实公司、部门和业务角色，不再混入市场行业分类。
- 项目和客户可直接按四级字段查询，无需关联目录表；目录表调整不会改变项目或客户记录的业务事实。
- CRM同步适配器必须分别处理组合目录、客户当前值和项目当前值，不能只同步目录后推断对象归属。

## 不包含

- 本ADR不定义市场行业组合的人工维护流程。
- 本ADR不把市场行业值用于替代组织权限；行业数据权限应引用项目或客户保存的字段并经过服务端授权策略。
