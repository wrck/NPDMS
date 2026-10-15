SELECT HEX(CAST(typeCode AS BINARY)) AS _groupKey,
  CAST(JSON_MERGE_PRESERVE(
    CAST(CASE WHEN SUM(objectName='fnd_basic_data_type')=0 THEN
      JSON_ARRAY(JSON_OBJECT('object','fnd_basic_data_type','fields',JSON_OBJECT('dataTypeCode',typeCode,'id',NULL,'dataTypeName',typeCode,'status',NULL,'createTime',NULL,'createBy',NULL,'updateTime',NULL,'updateBy',NULL,'effectiveFrom',NULL,'effectiveTo',NULL,'definitionMissing',1))) ELSE JSON_ARRAY() END AS JSON),
    JSON_ARRAYAGG(JSON_OBJECT('object',objectName,'fields',CAST(fields AS JSON)))) AS CHAR) AS _sourceRows
FROM (
 SELECT r.dataTypeCode AS typeCode,'fnd_basic_data_type' AS objectName,JSON_OBJECT('dataTypeCode',r.`dataTypeCode`,'id',r.`id`,'dataTypeName',r.`dataTypeName`,'status',r.`status`,'createTime',r.`createTime`,'createBy',r.`createBy`,'updateTime',r.`updateTime`,'updateBy',r.`updateBy`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`,'definitionMissing',0) AS fields FROM fnd_basic_data_type r
 UNION ALL
 SELECT r.dataTypeCode AS typeCode,'fnd_basic_data' AS objectName,JSON_OBJECT('id',r.`id`,'dataTypeCode',r.`dataTypeCode`,'basicDataId',r.`basicDataId`,'basicDataName',r.`basicDataName`,'basicDataAttri1',r.`basicDataAttri1`,'sortId',r.`sortId`,'createTime',r.`createTime`,'createBy',r.`createBy`,'updateTime',r.`updateTime`,'updateBy',r.`updateBy`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`) AS fields FROM fnd_basic_data r
) combined
GROUP BY typeCode,CAST(typeCode AS BINARY)
