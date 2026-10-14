-- P08 / fnd_basic_data_type | 原任务 2109351154645135362 v0
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT t.id,t.dataTypeCode,t.dataTypeName,t.status,t.createTime,t.createBy,t.updateTime,t.updateBy,t.effectiveFrom,t.effectiveTo,0 AS definitionMissing FROM fnd_basic_data_type t UNION ALL SELECT NULL,g.dataTypeCode,g.dataTypeCode,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1 AS definitionMissing FROM (SELECT DISTINCT dataTypeCode FROM fnd_basic_data) g LEFT JOIN fnd_basic_data_type t ON t.dataTypeCode=g.dataTypeCode WHERE t.id IS NULL;
