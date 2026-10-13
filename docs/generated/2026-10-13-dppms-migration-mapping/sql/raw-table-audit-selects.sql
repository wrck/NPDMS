-- 按已审查原结构逐列列示的全表审查SELECT；仅供审查，不是已配置迁移任务SQL。
-- view_warranty当前连接缺失：保留原契约，不能直接运行，也不自动改为view_warranty_source。
-- password列被明确排除；licenseCode不出现在普通审查SELECT，受控加密采集另有正式入口。

-- ehr_department | 原字段24 | 状态：已实际承接（批准范围）
SELECT `depID`,`depCode`,`depName`,`depAbbr`,`compID`,`adminID`,`depGrade`,`depType`,`depProperty`,`depCost`,`director`,`director2`,`depEmp`,`depNum`,`effectDate`,`xOrder`,`isDisabled`,`disabledDate`,`remark`,`depCustom1`,`depCustom2`,`depCustom3`,`depCustom4`,`depCustom5` FROM `ehr_department`;

-- ehr_employee | 原字段31 | 状态：引用核对；不独立迁移整表
SELECT `empID`,`workNo`,`name`,`eName`,`compID`,`depID`,`jobID`,`reportTo`,`wfreportTo`,`empStatus`,`jobStatus`,`empType`,`joinDate`,`workBeginDate`,`jobBeginDate`,`pracBeginDate`,`pracEndDate`,`probBeginDate`,`probEndDate`,`leaveDate`,`gender`,`email`,`mobile`,`officePhone`,`remark`,`disabled`,`empCustom1`,`empCustom2`,`empCustom3`,`empCustom4`,`empCustom5` FROM `ehr_employee`;

-- ehr_login | 原字段7 | 状态：引用核对；不独立迁移整表
SELECT `id`,`title`,`account`,`empID`,`workNo`,`name`,`isDisabled` FROM `ehr_login`;

-- fb_contract | 原字段12 | 状态：已实际承接（批准范围）
SELECT `contract_id`,`contract_code`,`office_code`,`contract_type`,`customer_name`,`project_name`,`warranty`,`marketCode`,`marketName`,`systemId`,`systemName`,`remark` FROM `fb_contract`;

-- fb_items | 原字段4 | 状态：已完成可核实原记录承接；缺项另列
SELECT `id`,`item`,`describe_`,`itemname` FROM `fb_items`;

-- fb_service | 原字段10 | 状态：未完成正式承接
SELECT `id`,`con_xb`,`barcode`,`grade`,`begin_date`,`end_date`,`warranty`,`remark`,`isyb`,`state` FROM `fb_service`;

-- fb_shipment | 原字段8 | 状态：已承接10项目；全历史未执行
SELECT `packlist_id`,`con_id`,`packdate`,`warrantyStartTime`,`warrantyEndTime`,`receiveName`,`emsNum`,`emsCompany` FROM `fb_shipment`;

-- fb_shipment_barcode | 原字段19 | 状态：已承接10项目；全历史未执行
SELECT `id`,`pack_id`,`item`,`barcode`,`com_barcode`,`rma_no`,`isRMA`,`item2`,`barcode2`,`orderNumber`,`lineNum`,`profitCenter`,`soleAgentSuffix`,`warrantyStartDate`,`warrantyMonth`,`rmaBarcode`,`updateTime`,`syncTime`,`uuid` FROM `fb_shipment_barcode`;

-- fb_shipment_barcode_relation | 原字段8 | 状态：已实际承接（批准范围）
SELECT `id`,`sn1`,`item1`,`sn2`,`item2`,`contract`,`createtime`,`updatetime` FROM `fb_shipment_barcode_relation`;

-- fb_soft_version | 原字段5 | 状态：已承接10项目；全历史未执行
SELECT `serial_number`,`conp`,`cpld`,`boot`,`pcb` FROM `fb_soft_version`;

-- fnd_act_hi_comment | 原字段12 | 状态：按裁决暂缓
SELECT `id`,`objId`,`procdefKey`,`taskKey`,`taskId`,`instId`,`assignee`,`assigneeTime`,`nextAssignee`,`nextAssigneeName`,`result`,`message` FROM `fnd_act_hi_comment`;

-- fnd_basic_data | 原字段12 | 状态：已实际承接（批准范围）
SELECT `id`,`dataTypeCode`,`basicDataId`,`basicDataName`,`basicDataAttri1`,`sortId`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `fnd_basic_data`;

-- fnd_basic_data_type | 原字段10 | 状态：已实际承接（批准范围）
SELECT `id`,`dataTypeCode`,`dataTypeName`,`status`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `fnd_basic_data_type`;

-- fnd_department | 原字段9 | 状态：已实际承接（批准范围）
SELECT `id`,`departmentNum`,`departmentName`,`isparam`,`status`,`createTime`,`effectiveFrom`,`effectiveTo`,`updateTime` FROM `fnd_department`;

-- fnd_roles | 原字段11 | 状态：已实际承接（批准范围）
SELECT `id`,`roleName`,`defaultPage`,`status`,`effectiveFrom`,`effectiveTo`,`createBy`,`createTime`,`updateBy`,`updateTime`,`roleRemark` FROM `fnd_roles`;

-- fnd_user_info | 原字段18 | 状态：已实际承接（批准范围）
SELECT `id`,`username`,`email`,`dpNo`,`realName`,`roleIds`,`isemail`,`status`,`defaultPage`,`pwdoverdue`,`createBy`,`createTime`,`updateBy`,`updateTime`,`effectiveFrom`,`effectiveTo`,`customInfo` FROM `fnd_user_info`;

-- fnd_user_power | 原字段10 | 状态：已实际承接（批准范围）
SELECT `id`,`fndUserId`,`username`,`areapower`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `fnd_user_power`;

-- pm_cl_callback | 原字段13 | 状态：按裁决暂缓
SELECT `id`,`projectId`,`instId`,`remark`,`applyState`,`applyBy`,`applyTime`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `pm_cl_callback`;

-- pm_cl_callback_quesnaire | 原字段12 | 状态：按裁决暂缓
SELECT `id`,`callBackId`,`taskId`,`quesnaireId`,`quesnaireVersion`,`quesnaireState`,`createBy`,`createTime`,`updateBy`,`updateTime`,`effectiveFrom`,`effectiveTo` FROM `pm_cl_callback_quesnaire`;

-- pm_cl_quesnaire_result_header | 原字段10 | 状态：按裁决暂缓
SELECT `id`,`evaluationHeaderId`,`quesnaireTemplateHeaderId`,`quesMarkScore`,`createdTime`,`createdPerson`,`updatedTime`,`updatedPerson`,`quesMarkResult`,`status` FROM `pm_cl_quesnaire_result_header`;

-- pm_cl_quesnaire_result_line | 原字段13 | 状态：按裁决暂缓
SELECT `id`,`quesnaireTemplateHeaderId`,`quesnaireTemplateLineId`,`questionTemplateOptId`,`questionAnswer`,`questionScore`,`quesnaireResultHeaderId`,`createdTime`,`createdPerson`,`updatedTime`,`updatedPerson`,`quesTypeForCB`,`quesEvaResult` FROM `pm_cl_quesnaire_result_line`;

-- pm_cl_quesnaire_template_header | 原字段14 | 状态：按裁决暂缓
SELECT `id`,`questionnaireTemplateNum`,`questionnaireTemplateName`,`questionnaireScore`,`questionnairePassScore`,`questionnaireStatus`,`effectiveStartTime`,`effectiveEndTime`,`createdTime`,`updatedTime`,`createdPerson`,`updatedPerson`,`quesType`,`markIndexs` FROM `pm_cl_quesnaire_template_header`;

-- pm_cl_quesnaire_template_line | 原字段15 | 状态：按裁决暂缓
SELECT `id`,`questionContent`,`questionType`,`questionScore`,`questionRemark`,`questionNum`,`quesnaireTemplateHeaderId`,`questionStatus`,`effectiveStartTime`,`effectiveEndTime`,`createdTime`,`updatedTime`,`createdPerson`,`updatedPerson`,`questionTypeForCB` FROM `pm_cl_quesnaire_template_line`;

-- pm_cl_quesnaire_template_options | 原字段13 | 状态：按裁决暂缓
SELECT `id`,`questionId`,`questionOptionNum`,`questionOptionsContent`,`questionOptionScore`,`quesnaireTemplateHeaderId`,`effectiveStartTime`,`effectiveEndTime`,`createdTime`,`updatedTime`,`createdPerson`,`updatedPerson`,`quesLineType` FROM `pm_cl_quesnaire_template_options`;

-- pm_order_data_from_erp | 原字段16 | 状态：已完成可核实原记录承接；缺项另列
SELECT `id`,`orderNumber`,`contractNo`,`orderExecNumber`,`orderCreateTime`,`customerRequireTime`,`customerCode`,`customerName`,`projectName`,`orderComment`,`orderType`,`compCode`,`salesType`,`source`,`customInfo`,`syncTime` FROM `pm_order_data_from_erp`;

-- pm_order_line_from_erp | 原字段16 | 状态：已承接10项目；全历史未执行
SELECT `id`,`orderNumber`,`lineNum`,`itemCode`,`itemDesc`,`orderQuantity`,`openQuantity`,`bundleCode`,`warrantyMonth`,`lineType`,`compCode`,`profitCenter`,`realOrderExecNumber`,`source`,`customInfo`,`syncTime` FROM `pm_order_line_from_erp`;

-- pm_pb_plan_from_sms | 原字段16 | 状态：原试点完成；续办扩大写入待范围处置
SELECT `id`,`contractNo`,`batchCode`,`basicDataName`,`referenceEventName`,`eventPlanHappenDate`,`afterDaysNum`,`eventActualFinishDate`,`marketingFeedback`,`createBy`,`createTime`,`updateBy`,`updateTime`,`effectiveFrom`,`effectiveTo`,`dataSource` FROM `pm_pb_plan_from_sms`;

-- pm_project | 原字段37 | 状态：原试点完成；续办扩大写入待范围处置
SELECT `projectId`,`projectType`,`projectCode`,`projectName`,`projectState`,`isback`,`column001`,`column002`,`column003`,`column004`,`column005`,`column006`,`column007`,`column008`,`column009`,`column010`,`column011`,`column012`,`columno12_readonly`,`column013`,`column014`,`customerProjectName`,`salesType`,`majorProjectLevel`,`compId`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo`,`disabled`,`projectStartTime`,`projectRefreshTime`,`projectCloseTime`,`customInfo`,`customConfig` FROM `pm_project`;

-- pm_project_contract | 原字段7 | 状态：原试点完成；续办扩大写入待范围处置
SELECT `id`,`contractNo`,`projectGroupCode`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_contract`;

-- pm_project_group | 原字段8 | 状态：原试点完成；续办扩大写入待范围处置
SELECT `id`,`projectGroupCode`,`projectGroupName`,`projectType`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_group`;

-- pm_project_group_relationship | 原字段9 | 状态：原试点完成；续办扩大写入待范围处置
SELECT `id`,`projectGroupCode`,`projectCode`,`mergeBranchMark`,`smsProjectCode`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_group_relationship`;

-- pm_project_header | 原字段37 | 状态：原试点完成；续办扩大写入待范围处置
SELECT `projectId`,`projectType`,`projectCode`,`projectName`,`projectState`,`isback`,`column001`,`column002`,`column003`,`column004`,`column005`,`column006`,`column007`,`column008`,`column009`,`column010`,`column011`,`column012`,`columno12_readonly`,`column013`,`column014`,`customerProjectName`,`salesType`,`majorProjectLevel`,`compId`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo`,`disabled`,`projectStartTime`,`projectRefreshTime`,`projectCloseTime`,`customInfo`,`customConfig` FROM `pm_project_header`;

-- pm_project_incident_table_from_itr | 原字段37 | 状态：按裁决暂缓
SELECT `id`,`incidentId`,`ticketNo`,`STATUS`,`statusName`,`caseTopic`,`memo`,`principal`,`principalName`,`accepter`,`accepterName`,`processor`,`processorName`,`supplied`,`questionType`,`questionLevel`,`title`,`acceptTime`,`productType`,`productModel`,`progress`,`questionReason`,`solutionType`,`solutions`,`rmaNo`,`accidentNo`,`caseType`,`reasonFstType`,`reasonSndType`,`projectCode`,`contractNo`,`barcode`,`bulletinNo`,`bugNo`,`productLine`,`customInfo`,`url` FROM `pm_project_incident_table_from_itr`;

-- pm_project_instruction | 原字段11 | 状态：按裁决暂缓
SELECT `id`,`projectId`,`instructionsInfo`,`instructionsTime`,`instructionsUser`,`dataType`,`instructionsId`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_instruction`;

-- pm_project_license_info_from_license | 原字段8 | 状态：已承接10项目；全历史未执行
SELECT `id`,`sn`,`specModel`,`contract`,`contractType`,`item`,`status` FROM `pm_project_license_info_from_license`;

-- pm_project_maintenance | 原字段44 | 状态：按裁决暂缓
SELECT `id`,`projectId`,`projectCode`,`projectName`,`projectType`,`projectExecutionState`,`contractNo`,`officeCode`,`compId`,`type`,`category`,`subCategory`,`processTime`,`processDesc`,`processStep`,`remainProblem`,`transitHour`,`processHour`,`itemModel`,`softVersion`,`enabledFeatures`,`customTos`,`customCcs`,`hasReport`,`quesnaireId`,`deliverFileIds`,`warrantyStatus`,`industryName`,`userOffice`,`year`,`quarter`,`month`,`wsCount`,`wafCount`,`wsYearCount`,`wafYearCount`,`warrantyInfo`,`serviceInfo`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy`,`customInfo` FROM `pm_project_maintenance`;

-- pm_project_member | 原字段15 | 状态：已承接10项目；全历史未执行
SELECT `id`,`projectId`,`projectType`,`memberRole`,`memberCode`,`memberName`,`phoneNum`,`email`,`fromFlag`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveTo`,`effectiveFrom` FROM `pm_project_member`;

-- pm_project_notification | 原字段6 | 状态：按裁决暂缓
SELECT `id`,`notifySubject`,`notifyContent`,`projectId`,`createTime`,`createBy` FROM `pm_project_notification`;

-- pm_project_product_config_level_info_from_crm | 原字段11 | 状态：已实现；试点原数据0条
SELECT `projectCode`,`orderExecNumber`,`itemGroup`,`itemCode`,`parentCode`,`quantity`,`bomPaths`,`itemModel`,`itemDesc`,`level`,`dataSource` FROM `pm_project_product_config_level_info_from_crm`;

-- pm_project_product_lease_line_from_crm | 原字段11 | 状态：已实现；试点原数据0条
SELECT `projectCode`,`orderExecNumber`,`productFirstName`,`productName`,`productSubCode`,`productSubModel`,`productSubName`,`num`,`memo`,`leaseDuration`,`dataSource` FROM `pm_project_product_lease_line_from_crm`;

-- pm_project_product_line | 原字段11 | 状态：已承接10项目；全历史未执行
SELECT `id`,`projectId`,`contractNo`,`itemCode`,`itemName`,`projectQuantity`,`orderQuantity`,`deliverQuantity`,`openQuantity`,`orderNumber`,`lineNum` FROM `pm_project_product_line`;

-- pm_project_product_line_real | 原字段12 | 状态：已实现；试点原数据0条
SELECT `id`,`projectId`,`contractNo`,`projectCode`,`orderExecNumber`,`productFirstName`,`productName`,`productSubCode`,`productSubModel`,`productSubName`,`num`,`memo` FROM `pm_project_product_line_real`;

-- pm_project_property_from_sms | 原字段32 | 状态：已完成可核实原记录承接；缺项另列
SELECT `id`,`orderExecNumber`,`projectCode`,`projectName`,`salesManCode`,`salesManName`,`marketCode`,`marketName`,`systemId`,`systemName`,`expendId`,`expendName`,`industryId`,`industryName`,`officeCode`,`officeName`,`serviceTypeName`,`channelName`,`engineeFee`,`objId`,`applyType`,`corporationCode`,`customerProjectName`,`finalCustomerName`,`agentName`,`projectMoney`,`submitTime`,`majorProjectLevel`,`predBidDate`,`linkmanName`,`linkmanTel`,`dataSource` FROM `pm_project_property_from_sms`;

-- pm_project_real_product_line_from_sms | 原字段10 | 状态：已实现；试点原数据0条
SELECT `projectCode`,`orderExecNumber`,`productFirstName`,`productName`,`productSubCode`,`productSubModel`,`productSubName`,`num`,`memo`,`dataSource` FROM `pm_project_real_product_line_from_sms`;

-- pm_project_related_party | 原字段11 | 状态：未完成正式承接
SELECT `id`,`projectId`,`partyRole`,`partyCode`,`partyName`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo` FROM `pm_project_related_party`;

-- pm_project_shipment | 原字段21 | 状态：已承接10项目；全历史未执行
SELECT `id`,`projectId`,`barcode`,`itemCode`,`itemModel`,`itemName`,`receiveName`,`emsNum`,`emsCompany`,`packdate`,`contractNo`,`installAddress`,`chProjectId`,`chContractNo`,`transferProjectId`,`transferContractNo`,`transferFlag`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_shipment`;

-- pm_project_soft_change_logs | 原字段9 | 状态：已承接10项目；全历史未执行
SELECT `id`,`projectId`,`changeVersion`,`changeRemark`,`latest`,`createBy`,`createTime`,`updateBy`,`updateTime` FROM `pm_project_soft_change_logs`;

-- pm_project_soft_version | 原字段28 | 状态：已承接10项目；全历史未执行
SELECT `id`,`projectId`,`logId`,`contractNo`,`itemCode`,`barCode`,`conp`,`conpType`,`conpSeries`,`conpMark`,`conpBak`,`conpChange`,`cpld`,`cpldBak`,`cpldChange`,`boot`,`bootBak`,`bootChange`,`pcb`,`pcbBak`,`pcbChange`,`executeTime`,`datastate`,`createTime`,`createBy`,`updateTime`,`updateBy`,`customInfo` FROM `pm_project_soft_version`;

-- pm_project_state | 原字段9 | 状态：未完成正式承接
SELECT `projectId`,`projectPlanState`,`projectplanTime`,`shipmentState`,`shipmentTime`,`executionState`,`executionStateTime`,`closeProcessState`,`closeProcessStateTime` FROM `pm_project_state`;

-- pm_project_supervision | 原字段17 | 状态：按裁决暂缓
SELECT `id`,`projectId`,`projectCode`,`projectName`,`channel`,`officeCode`,`type`,`processTime`,`state`,`isDelete`,`quesnaireId`,`deliverFileIds`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_project_supervision`;

-- pm_project_task | 原字段28 | 状态：未完成正式承接
SELECT `taskId`,`projectId`,`projectType`,`contractNo`,`taskTypeCode`,`taskTypeId`,`taskName`,`eventPlanHappenDate`,`eventPlanHappenDateENG`,`planStartTime`,`planEndTime`,`actualStartTime`,`eventActualFinishDate`,`priority`,`progress`,`progressDesc`,`status`,`parentId`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy`,`effectiveFrom`,`effectiveTo`,`visibleFlag`,`deliverFileIds`,`customInfo` FROM `pm_project_task`;

-- pm_project_weekly | 原字段14 | 状态：按裁决暂缓
SELECT `weeklyId`,`projectId`,`currentTask`,`taskStartTime`,`taskEndTime`,`taskDeviation`,`remark`,`createTime`,`createBy`,`updateTime`,`updateBy`,`weeklyStartTime`,`weeklyEndTime`,`weeklyState` FROM `pm_project_weekly`;

-- pm_project_weekly_content | 原字段9 | 状态：按裁决暂缓
SELECT `id`,`weeklyId`,`optionDesc001`,`optionDesc002`,`optionType`,`createTime`,`createBy`,`effectiveFrom`,`effectiveTo` FROM `pm_project_weekly_content`;

-- pm_project_weekly_feedback | 原字段5 | 状态：按裁决暂缓
SELECT `id`,`weeklyId`,`feedback`,`feedbacker`,`feedbackTime` FROM `pm_project_weekly_feedback`;

-- pm_subcontract_project_header | 原字段28 | 状态：按裁决暂缓
SELECT `id`,`subcontractName`,`subcontractNo`,`contractNos`,`projectIds`,`type`,`state`,`callbackState`,`facilitatorId`,`facilitatorName`,`bankInfo`,`bankAccount`,`officeCode`,`profitDepCode`,`isAccrued`,`isInvoiced`,`subcontractAmount`,`reason`,`remark`,`effectiveFrom`,`effectiveTo`,`zrApproveTime`,`createBy`,`createTime`,`updateBy`,`updateTime`,`orgId`,`customInfo` FROM `pm_subcontract_project_header`;

-- pm_subcontract_project_payment | 原字段13 | 状态：按裁决暂缓
SELECT `id`,`subcontractId`,`ratio`,`amount`,`confirmTime`,`paymentTime`,`remark`,`sseId`,`createTime`,`createBy`,`updateTime`,`updateBy`,`customInfo` FROM `pm_subcontract_project_payment`;

-- pm_subcontract_project_price | 原字段13 | 状态：按裁决暂缓
SELECT `id`,`subcontractId`,`contractNo`,`orderExecNumber`,`projectCode`,`engineeFee`,`objId`,`procType`,`price`,`createTime`,`createBy`,`updateTime`,`updateBy` FROM `pm_subcontract_project_price`;

-- sms_ofst_contract_head_sap | 原字段39 | 状态：未完成正式承接
SELECT `id`,`contract_num`,`batch_code`,`project_name`,`order_num`,`client_supplier_code`,`client_supplier_name`,`contract_money_amount`,`delivered_money_amount`,`collected_money_amount`,`collected_money_ratio`,`receivables_money_amount`,`over_due_money_amount`,`maketing_department_name`,`office_name`,`industry_name`,`marketing_representative_name`,`currency_name`,`create_by`,`create_time`,`update_by`,`update_time`,`effective_from`,`effective_to`,`import_batch_num`,`contract_create_date`,`projectCode`,`marketCode`,`systemId`,`industryId`,`officeCode`,`expendId`,`usernamec`,`latest_ship_date`,`usernamec2`,`systemid_o`,`expendid_o`,`industry_name_o`,`dataSource` FROM `sms_ofst_contract_head_sap`;

-- t_role | 原字段11 | 状态：已实际承接（批准范围）
SELECT `role_id`,`role_name`,`role_name_zn`,`home_page`,`priority`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark` FROM `t_role`;

-- t_user | 原字段16 | 状态：已实际承接（批准范围）
SELECT `user_id`,`user_name`,`create_by`,`create_time`,`update_by`,`update_time`,`status`,`needChangePwd`,`loginErrorCount`,`isSysUser`,`userCustom1`,`userCustom2`,`userCustom3`,`userCustom4`,`userCustom5` FROM `t_user`;

-- t_user_info | 原字段26 | 状态：已实际承接（批准范围）
SELECT `id`,`workNo`,`realName`,`eName`,`compID`,`depID`,`jobID`,`reportTo`,`wfreportTo`,`empStatus`,`jobStatus`,`empType`,`sex`,`birthday`,`email`,`mobile`,`telphone`,`avatar`,`remark`,`state`,`user_id`,`custom1`,`custom2`,`custom3`,`custom4`,`custom5` FROM `t_user_info`;

-- t_user_role | 原字段4 | 状态：已实际承接（批准范围）
SELECT `id`,`user_id`,`role_id`,`comp_id` FROM `t_user_role`;

-- view_shipment_ems_4_pm | 原字段6 | 状态：已承接10项目；全历史未执行
SELECT `contract_code`,`receiveName`,`emsNum`,`packdate`,`emsCompany`,`packId` FROM `view_shipment_ems_4_pm`;

-- view_shipment_info_4_pm | 原字段11 | 状态：已承接10项目；全历史未执行
SELECT `contract_code`,`itemCode`,`itemModel`,`itemName`,`barcode`,`packId`,`itemCode2`,`itemModel2`,`itemName2`,`barcode2`,`profitCenter` FROM `view_shipment_info_4_pm`;

-- view_warranty | 原字段36 | 状态：现存保留；不独立同步
SELECT `newId`,`id`,`barcode`,`comBarCode`,`old_warrantyEndTime`,`warrantyEndTime`,`old_diff`,`diff`,`warrantyStartTime`,`old_warrantyStartTime`,`item`,`describe_`,`itemName`,`gradeName`,`gradeCode`,`packdate`,`contract_code`,`contract_type`,`contract_type_name`,`project_name`,`customer_name`,`office_code`,`office_name`,`marketCode`,`marketName`,`systemId`,`systemName`,`warranty`,`warrantyMonth`,`barcode2`,`item2`,`syncTime`,`describe_2`,`itemName2`,`agentName`,`finalCustomerName` FROM `view_warranty`;

-- warranty_change_logs | 原字段8 | 状态：未完成正式承接
SELECT `id`,`username`,`updateType`,`barcode`,`warrantyStartTime`,`warrantyEndTime`,`warrantyTimes`,`updateTime` FROM `warranty_change_logs`;

-- warranty_info | 原字段15 | 状态：未完成正式承接
SELECT `id`,`packlistId`,`contractId`,`barCode`,`officeCode`,`projectName`,`contractNo`,`contractType`,`customerName`,`itemCode`,`itemName`,`warrantyLevel`,`warrantyStartTime`,`warrantyEndTime`,`warrantyLimit` FROM `warranty_info`;
