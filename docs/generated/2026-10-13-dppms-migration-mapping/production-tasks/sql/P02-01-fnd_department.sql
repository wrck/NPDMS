-- P02 / fnd_department | 原任务 2108524501517955073 v1
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT d.id,d.departmentNum,d.departmentName,d.isparam,d.status,d.createTime,d.effectiveFrom,d.effectiveTo,d.updateTime,e.depID AS migrationEhrDepartmentId,CAST(e.isDisabled AS UNSIGNED) AS migrationEhrDisabled FROM fnd_department d LEFT JOIN ehr_department e ON CAST(e.depCode AS BINARY)=CAST(d.departmentNum AS BINARY) WHERE d.departmentNum=:departmentCode0;
