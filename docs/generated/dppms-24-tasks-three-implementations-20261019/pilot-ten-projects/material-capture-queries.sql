-- 旧十项目材料采集附件；来源文件：docs/generated/2026-10-13-dppms-migration-mapping/sql/legacy-material-capture-queries.sql
-- 历史44类资料不是44个全量同步任务。没有执行本附件。
-- 已提交scripts/dppms_project_detail_sync.py中的实际来源采集语句形态。
-- SELECT列来自该采集器读取的完整审查catalog；这里仅将驱动%s/集合占位表示为命名参数，关联谓词保持。
-- 此为旧试点材料采集过程，尚非所有对象的独立整表迁移任务；不能据此计为业务承接。
-- 没有执行本附件；view_warranty原契约缺失仍按裁决阻断新的完整采集。

-- C001 | domain=project | source=pm_project_header
SELECT `projectId`,`projectType`,`projectCode`,`projectName`,`projectState`,`isback`,`column001`,`column002`,`column003`,`column004`,`column005`,`column006`,`column007`,`column008`,`column009`,`column010`,`column011`,`column012`,`columno12_readonly`,`column013`,`column014`,`customerProjectName`,`salesType`,`majorProjectLevel`,`compId`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo`,`disabled`,`projectStartTime`,`projectRefreshTime`,`projectCloseTime`,`customInfo`,`customConfig` FROM `pm_project_header` WHERE `projectId`=:projectId;

-- C002 | domain=groups | source=pm_project_group_relationship
SELECT `id`,`projectGroupCode`,`projectCode`,`mergeBranchMark`,`smsProjectCode`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_group_relationship` WHERE `projectCode`=:projectCode;

-- C003 | domain=contracts | source=pm_project_contract
SELECT `id`,`contractNo`,`projectGroupCode`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_contract` WHERE `projectGroupCode` IN (:projectGroupCodes);

-- C004 | domain=state | source=pm_project_state
SELECT `projectId`,`projectPlanState`,`projectplanTime`,`shipmentState`,`shipmentTime`,`executionState`,`executionStateTime`,`closeProcessState`,`closeProcessStateTime` FROM `pm_project_state` WHERE `projectId`=:projectId;

-- C005 | domain=parties | source=pm_project_related_party
SELECT `id`,`projectId`,`partyRole`,`partyCode`,`partyName`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `pm_project_related_party` WHERE `projectId`=:projectId;

-- C006 | domain=members | source=pm_project_member
SELECT `id`,`projectId`,`projectType`,`memberRole`,`memberCode`,`memberName`,`phoneNum`,`email`,`fromFlag`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveTo`,`effectiveFrom` FROM `pm_project_member` WHERE `projectId`=:projectId;

-- C007 | domain=products | source=pm_project_product_line
SELECT `id`,`projectId`,`contractNo`,`itemCode`,`itemName`,`projectQuantity`,`orderQuantity`,`deliverQuantity`,`openQuantity`,`orderNumber`,`lineNum` FROM `pm_project_product_line` WHERE `projectId`=:projectId;

-- C008 | domain=installations | source=pm_project_shipment
SELECT `id`,`projectId`,`barcode`,`itemCode`,`itemModel`,`itemName`,`receiveName`,`emsNum`,`emsCompany`,`packdate`,`contractNo`,`installAddress`,`chProjectId`,`chContractNo`,`transferProjectId`,`transferContractNo`,`transferFlag`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_shipment` WHERE `projectId`=:projectId;

-- C009 | domain=software-changes | source=pm_project_soft_change_logs
SELECT `id`,`projectId`,`changeVersion`,`changeRemark`,`latest`,`createBy`,`createTime`,`updateBy`,`updateTime` FROM `pm_project_soft_change_logs` WHERE `projectId`=:projectId;

-- C010 | domain=software-versions | source=pm_project_soft_version
SELECT `id`,`projectId`,`logId`,`contractNo`,`itemCode`,`barCode`,`conp`,`conpType`,`conpSeries`,`conpMark`,`conpBak`,`conpChange`,`cpld`,`cpldBak`,`cpldChange`,`boot`,`bootBak`,`bootChange`,`pcb`,`pcbBak`,`pcbChange`,`executeTime`,`datastate`,`createTime`,`createBy`,`updateTime`,`updateBy`,`customInfo` FROM `pm_project_soft_version` WHERE `projectId`=:projectId;

-- C011 | domain=tasks | source=pm_project_task
SELECT `taskId`,`projectId`,`projectType`,`contractNo`,`taskTypeCode`,`taskTypeId`,`taskName`,`eventPlanHappenDate`,`eventPlanHappenDateENG`,`planStartTime`,`planEndTime`,`actualStartTime`,`eventActualFinishDate`,`priority`,`progress`,`progressDesc`,`status`,`parentId`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo`,`visibleFlag`,`deliverFileIds`,`customInfo` FROM `pm_project_task` WHERE `projectId`=:projectId;

-- C012 | domain=maintenance | source=pm_project_maintenance
SELECT `id`,`projectId`,`projectCode`,`projectName`,`projectType`,`projectExecutionState`,`contractNo`,`officeCode`,`compId`,`type`,`category`,`subCategory`,`processTime`,`processDesc`,`processStep`,`remainProblem`,`transitHour`,`processHour`,`itemModel`,`softVersion`,`enabledFeatures`,`customTos`,`customCcs`,`hasReport`,`quesnaireId`,`deliverFileIds`,`warrantyStatus`,`industryName`,`userOffice`,`year`,`quarter`,`month`,`wsCount`,`wafCount`,`wsYearCount`,`wafYearCount`,`warrantyInfo`,`serviceInfo`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy`,`customInfo` FROM `pm_project_maintenance` WHERE `projectId`=:projectId;

-- C013 | domain=callbacks | source=pm_cl_callback
SELECT `id`,`projectId`,`instId`,`remark`,`applyState`,`applyBy`,`applyTime`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `pm_cl_callback` WHERE `projectId`=:projectId;

-- C014 | domain=notifications | source=pm_project_notification
SELECT `id`,`notifySubject`,`notifyContent`,`projectId`,`createTime`,`createBy` FROM `pm_project_notification` WHERE `projectId`=:projectId;

-- C015 | domain=weekly | source=pm_project_weekly
SELECT `weeklyId`,`projectId`,`currentTask`,`taskStartTime`,`taskEndTime`,`taskDeviation`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy`,`weeklyStartTime`,`weeklyEndTime`,`weeklyState` FROM `pm_project_weekly` WHERE `projectId`=:projectId;

-- C016 | domain=instructions | source=pm_project_instruction
SELECT `id`,`projectId`,`instructionsInfo`,`instructionsTime`,`instructionsUser`,`dataType`,`instructionsId`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_instruction` WHERE `projectId`=:projectId;

-- C017 | domain=real-products | source=pm_project_product_line_real
SELECT `id`,`projectId`,`contractNo`,`projectCode`,`orderExecNumber`,`productFirstName`,`productName`,`productSubCode`,`productSubModel`,`productSubName`,`num`,`memo` FROM `pm_project_product_line_real` WHERE `projectId`=:projectId;

-- C018 | domain=supervision | source=pm_project_supervision
SELECT `id`,`projectId`,`projectCode`,`projectName`,`channel`,`officeCode`,`type`,`processTime`,`state`,`isDelete`,`quesnaireId`,`deliverFileIds`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_supervision` WHERE `projectId`=:projectId;

-- C019 | domain=orders | source=pm_order_data_from_erp
SELECT `id`,`orderNumber`,`contractNo`,`orderExecNumber`,`orderCreateTime`,`customerRequireTime`,`customerCode`,`customerName`,`projectName`,`orderComment`,`orderType`,`compCode`,`salesType`,`source`,`customInfo`,`syncTime` FROM `pm_order_data_from_erp` WHERE `id` IN (:sourceOrderIds);

-- C020 | domain=executions | source=pm_project_property_from_sms
SELECT `id`,`orderExecNumber`,`projectCode`,`projectName`,`salesManCode`,`salesManName`,`marketCode`,`marketName`,`systemId`,`systemName`,`expendId`,`expendName`,`industryId`,`industryName`,`officeCode`,`officeName`,`serviceTypeName`,`channelName`,`engineeFee`,`objId`,`applyType`,`corporationCode`,`customerProjectName`,`finalCustomerName`,`agentName`,`projectMoney`,`submitTime`,`majorProjectLevel`,`predBidDate`,`linkmanName`,`linkmanTel`,`dataSource` FROM `pm_project_property_from_sms` WHERE `id` IN (:sourceExecutionIds);

-- C021 | domain=order-lines | source=pm_order_line_from_erp
SELECT `id`,`orderNumber`,`lineNum`,`itemCode`,`itemDesc`,`orderQuantity`,`openQuantity`,`bundleCode`,`warrantyMonth`,`lineType`,`compCode`,`profitCenter`,`realOrderExecNumber`,`source`,`customInfo`,`syncTime` FROM `pm_order_line_from_erp` WHERE `orderNumber`=:orderNumber AND `compCode`=:compCode AND `source`=:erpSource AND `lineType`=:orderType;

-- C022 | domain=plans | source=pm_pb_plan_from_sms
SELECT `id`,`contractNo`,`batchCode`,`basicDataName`,`referenceEventName`,`eventPlanHappenDate`,`afterDaysNum`,`eventActualFinishDate`,`marketingFeedback`,`createBy`,`createTime`,`updateBy`,`updateTime`,`effectiveFrom`,`effectiveTo`,`dataSource` FROM `pm_pb_plan_from_sms` WHERE `contractNo` IN (:contractNumbers);

-- C023 | domain=lease-products | source=pm_project_product_lease_line_from_crm
SELECT `projectCode`,`orderExecNumber`,`productFirstName`,`productName`,`productSubCode`,`productSubModel`,`productSubName`,`num`,`memo`,`leaseDuration`,`dataSource` FROM `pm_project_product_lease_line_from_crm` WHERE `projectCode`=:projectCodeRoot AND BINARY `projectCode`=BINARY :projectCodeRoot;

-- C024 | domain=configured-products | source=pm_project_product_config_level_info_from_crm
SELECT `projectCode`,`orderExecNumber`,`itemGroup`,`itemCode`,`parentCode`,`quantity`,`bomPaths`,`itemModel`,`itemDesc`,`level`,`dataSource` FROM `pm_project_product_config_level_info_from_crm` WHERE `projectCode`=:projectCodeRoot AND BINARY `projectCode`=BINARY :projectCodeRoot;

-- C025 | domain=shipment-facts | source=fb_shipment_barcode
SELECT `id`,`pack_id`,`item`,`barcode`,`com_barcode`,`rma_no`,`isRMA`,`item2`,`barcode2`,`orderNumber`,`lineNum`,`profitCenter`,`soleAgentSuffix`,`warrantyStartDate`,`warrantyMonth`,`rmaBarcode`,`updateTime`,`syncTime`,`uuid` FROM `fb_shipment_barcode` WHERE `id` IN (:sourceShipmentIds);

-- C026 | domain=shipment-packages | source=fb_shipment
SELECT `packlist_id`,`con_id`,`packdate`,`warrantyStartTime`,`warrantyEndTime`,`receiveName`,`emsNum`,`emsCompany` FROM `fb_shipment` WHERE `packlist_id` IN (:packIds);

-- C027 | domain=shipment-logistics | source=view_shipment_ems_4_pm
SELECT `contract_code`,`receiveName`,`emsNum`,`packdate`,`emsCompany`,`packId` FROM `view_shipment_ems_4_pm` WHERE `contract_code` IN (:contractNumbersAndOriginalNormalizedVariants);

-- C028 | domain=shipment-products | source=view_shipment_info_4_pm
SELECT `contract_code`,`itemCode`,`itemModel`,`itemName`,`barcode`,`packId`,`itemCode2`,`itemModel2`,`itemName2`,`barcode2`,`profitCenter` FROM `view_shipment_info_4_pm` WHERE `contract_code` IN (:contractNumbersAndOriginalNormalizedVariants);

-- C029 | domain=warranties | source=view_warranty
SELECT `newId`,`id`,`barcode`,`comBarCode`,`old_warrantyEndTime`,`warrantyEndTime`,`old_diff`,`diff`,`warrantyStartTime`,`old_warrantyStartTime`,`item`,`describe_`,`itemName`,`gradeName`,`gradeCode`,`packdate`,`contract_code`,`contract_type`,`contract_type_name`,`project_name`,`customer_name`,`office_code`,`office_name`,`marketCode`,`marketName`,`systemId`,`systemName`,`warranty`,`warrantyMonth`,`barcode2`,`item2`,`syncTime`,`describe_2`,`itemName2`,`agentName`,`finalCustomerName` FROM `view_warranty` WHERE `contract_code` IN (:contractNumbersAndOriginalNormalizedVariants);

-- C030 | domain=device-relations | source=fb_shipment_barcode_relation
SELECT `id`,`sn1`,`item1`,`sn2`,`item2`,`contract`,`createtime`,`updatetime` FROM `fb_shipment_barcode_relation` WHERE `contract` IN (:originalContractNumbersWithoutC);

-- C031 | domain=factory-versions | source=fb_soft_version
SELECT `serial_number`,`conp`,`cpld`,`boot`,`pcb` FROM `fb_soft_version` WHERE `serial_number` IN (:shipmentAndInstallationSerials);

-- C032 | domain=warranty-services | source=fb_service
SELECT `id`,`con_xb`,`barcode`,`grade`,`begin_date`,`end_date`,`warranty`,`remark`,`isyb`,`state` FROM `fb_service` WHERE `barcode` IN (:shipmentAndInstallationSerials);

-- C033 | domain=warranty-registrations | source=warranty_info
SELECT `id`,`packlistId`,`contractId`,`barCode`,`officeCode`,`projectName`,`contractNo`,`contractType`,`customerName`,`itemCode`,`itemName`,`warrantyLevel`,`warrantyStartTime`,`warrantyEndTime`,`warrantyLimit` FROM `warranty_info` WHERE `barCode` IN (:shipmentAndInstallationSerials);

-- C034 | domain=warranty-changes | source=warranty_change_logs
SELECT `id`,`username`,`updateType`,`barcode`,`warrantyStartTime`,`warrantyEndTime`,`warrantyTimes`,`updateTime` FROM `warranty_change_logs` WHERE `barcode` IN (:shipmentAndInstallationSerials);

-- C035 | domain=shipment-materials | source=fb_items
SELECT `id`,`item`,`describe_`,`itemname` FROM `fb_items` WHERE `item` IN (:primaryAndSecondaryItemCodes);

-- C036 | domain=licenses | source=pm_project_license_info_from_license
SELECT `id`,`sn`,`specModel`,`contract`,`contractType`,`item`,`status` FROM `pm_project_license_info_from_license` WHERE `contract` IN (:contractNumbers);

-- C037 | domain=incidents | source=pm_project_incident_table_from_itr
SELECT `id`,`incidentId`,`ticketNo`,`STATUS`,`statusName`,`caseTopic`,`memo`,`principal`,`principalName`,`accepter`,`accepterName`,`processor`,`processorName`,`supplied`,`questionType`,`questionLevel`,`title`,`acceptTime`,`productType`,`productModel`,`progress`,`questionReason`,`solutionType`,`solutions`,`rmaNo`,`accidentNo`,`caseType`,`reasonFstType`,`reasonSndType`,`projectCode`,`contractNo`,`barcode`,`bulletinNo`,`bugNo`,`productLine`,`customInfo`,`url` FROM `pm_project_incident_table_from_itr` WHERE `projectCode`=:projectCode OR `contractNo` IN (:contractNumbers);

-- C038 | domain=subcontracts | source=pm_subcontract_project_header
SELECT `id`,`subcontractName`,`subcontractNo`,`contractNos`,`projectIds`,`type`,`state`,`callbackState`,`facilitatorId`,`facilitatorName`,`bankInfo`,`bankAccount`,`officeCode`,`profitDepCode`,`isAccrued`,`isInvoiced`,`subcontractAmount`,`reason`,`remark`,`effectiveFrom`,`effectiveTo`,`zrApproveTime`,`createBy`,`createTime`,`updateBy`,`updateTime`,`orgId`,`customInfo` FROM `pm_subcontract_project_header` WHERE FIND_IN_SET(:projectId,`projectIds`)>0;

-- C039 | domain=subcontract-payments | source=pm_subcontract_project_payment
SELECT `id`,`subcontractId`,`ratio`,`amount`,`confirmTime`,`paymentTime`,`remark`,`sseId`,`createTime`,`createBy`,`updateTime`,`updateBy`,`customInfo` FROM `pm_subcontract_project_payment` WHERE `subcontractId` IN (:subcontractIds);

-- C040 | domain=subcontract-prices | source=pm_subcontract_project_price
SELECT `id`,`subcontractId`,`contractNo`,`orderExecNumber`,`projectCode`,`engineeFee`,`objId`,`procType`,`price`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_subcontract_project_price` WHERE `subcontractId` IN (:subcontractIds);

-- C041 | domain=weekly-items | source=pm_project_weekly_content
SELECT `id`,`weeklyId`,`optionDesc001`,`optionDesc002`,`optionType`,`createTime`,`createBy`,`effectiveFrom`,`effectiveTo` FROM `pm_project_weekly_content` WHERE `weeklyId` IN (:weeklyIds);

-- C042 | domain=weekly-feedback | source=pm_project_weekly_feedback
SELECT `id`,`weeklyId`,`feedback`,`feedbacker`,`feedbackTime` FROM `pm_project_weekly_feedback` WHERE `weeklyId` IN (:weeklyIds);

-- C043 | domain=callback-questionnaires | source=pm_cl_callback_quesnaire
SELECT `id`,`callBackId`,`taskId`,`quesnaireId`,`quesnaireVersion`,`quesnaireState`,`createBy`,`createTime`,`updateBy`,`updateTime`,`effectiveFrom`,`effectiveTo` FROM `pm_cl_callback_quesnaire` WHERE `callBackId` IN (:callbackIds);

-- C044 | domain=questionnaires | source=pm_cl_quesnaire_result_header
SELECT `id`,`evaluationHeaderId`,`quesnaireTemplateHeaderId`,`quesMarkScore`,`createdTime`,`createdPerson`,`updatedTime`,`updatedPerson`,`quesMarkResult`,`status` FROM `pm_cl_quesnaire_result_header` WHERE `id` IN (:questionnaireIds);

-- C045 | domain=questionnaire-answers | source=pm_cl_quesnaire_result_line
SELECT `id`,`quesnaireTemplateHeaderId`,`quesnaireTemplateLineId`,`questionTemplateOptId`,`questionAnswer`,`questionScore`,`quesnaireResultHeaderId`,`createdTime`,`createdPerson`,`updatedTime`,`updatedPerson`,`quesTypeForCB`,`quesEvaResult` FROM `pm_cl_quesnaire_result_line` WHERE `quesnaireResultHeaderId` IN (:questionnaireResultHeaderIds);

-- C046 | domain=questionnaire-templates | source=pm_cl_quesnaire_template_header
SELECT `id`,`questionnaireTemplateNum`,`questionnaireTemplateName`,`questionnaireScore`,`questionnairePassScore`,`questionnaireStatus`,`effectiveStartTime`,`effectiveEndTime`,`createdTime`,`updatedTime`,`createdPerson`,`updatedPerson`,`quesType`,`markIndexs` FROM `pm_cl_quesnaire_template_header` WHERE `id` IN (:questionnaireTemplateIds);

-- C047 | domain=questionnaire-questions | source=pm_cl_quesnaire_template_line
SELECT `id`,`questionContent`,`questionType`,`questionScore`,`questionRemark`,`questionNum`,`quesnaireTemplateHeaderId`,`questionStatus`,`effectiveStartTime`,`effectiveEndTime`,`createdTime`,`updatedTime`,`createdPerson`,`updatedPerson`,`questionTypeForCB` FROM `pm_cl_quesnaire_template_line` WHERE `quesnaireTemplateHeaderId` IN (:questionnaireTemplateIds);

-- C048 | domain=questionnaire-options | source=pm_cl_quesnaire_template_options
SELECT `id`,`questionId`,`questionOptionNum`,`questionOptionsContent`,`questionOptionScore`,`quesnaireTemplateHeaderId`,`effectiveStartTime`,`effectiveEndTime`,`createdTime`,`updatedTime`,`createdPerson`,`updatedPerson`,`quesLineType` FROM `pm_cl_quesnaire_template_options` WHERE `questionId` IN (:questionIds);

-- C049 | domain=approvals | source=fnd_act_hi_comment
SELECT `id`,`objId`,`procdefKey`,`taskKey`,`taskId`,`instId`,`assignee`,`assigneeTime`,`nextAssignee`,`nextAssigneeName`,`result`,`message` FROM `fnd_act_hi_comment` WHERE `procdefKey`=:confirmedProcessKey AND `objId` IN (:processSubjectIds);

-- C050 | domain=real-product-upstream | source=pm_project_real_product_line_from_sms
SELECT `projectCode`,`orderExecNumber`,`productFirstName`,`productName`,`productSubCode`,`productSubModel`,`productSubName`,`num`,`memo`,`dataSource` FROM `pm_project_real_product_line_from_sms` WHERE BINARY `projectCode` <=> BINARY :projectCode AND BINARY `orderExecNumber` <=> BINARY :orderExecNumber AND BINARY `productFirstName` <=> BINARY :productFirstName AND BINARY `productName` <=> BINARY :productName AND BINARY `productSubCode` <=> BINARY :productSubCode AND BINARY `productSubModel` <=> BINARY :productSubModel AND BINARY `productSubName` <=> BINARY :productSubName AND `num` <=> :num AND BINARY `memo` <=> BINARY :memo;

-- scripts/dppms_project_license_secure.py：仅在受控密钥就绪后读取以下原码，在内存加密并交AST正式Owner。
SELECT id,licenseCode,sn,specModel,contract,contractType,item,status FROM pm_project_license_info_from_license WHERE id IN (:approvedLicenseIds) ORDER BY id;
