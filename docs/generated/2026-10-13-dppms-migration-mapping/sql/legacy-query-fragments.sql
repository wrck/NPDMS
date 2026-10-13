-- 原查询引用的SQL片段，保留原文；按出处及refid配套查询，不独立执行。

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:42 | fragment=Base_Column_List
`id`, `projectId`, `projectType`, `projectCode`, `projectName`, `contractNo`, `officeCode`, 
    `type`, `category`, `subCategory`, `processTime`, `processDesc`, `processStep`, `remainProblem`, 
    `customerInteraction`, `transitHour`, `processHour`, `itemModel`, `softVersion`, 
    `enabledFeatures`, `customTos`, `customCcs`, `projectExecutionState`, `hasReport`, 
    `quesnaireId`, `deliverFileIds`, `remark`, `isReported`, `qualityFactor`, `status`, 
    `disabled`, `createTime`, `createBy`, `updateTime`, `updateBy`,
    `customInfo`

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:462 | fragment=sql_where_selective
<if test="id != null">
		dr.`id` = #{id, jdbcType=INTEGER} AND
	</if>
	<if test="projectId != null">
		dr.`projectId` = #{projectId, jdbcType=INTEGER} AND
	</if>
	<if test="projectType != null and '' != projectType">
		dr.`projectType` = #{projectType, jdbcType=VARCHAR} AND
	</if>
	<if test="projectCode != null and '' != projectCode">
		dr.`projectCode` = #{projectCode, jdbcType=VARCHAR} AND
	</if>
	<if test="projectName != null and '' != projectName">
		dr.`projectName` = #{projectName, jdbcType=VARCHAR} AND
	</if>
	<if test="contractNo != null and '' != contractNo">
		dr.`contractNo` = #{contractNo, jdbcType=VARCHAR} AND
	</if>
	<if test="officeCode != null and '' != officeCode">
		dr.`officeCode` = #{officeCode, jdbcType=VARCHAR} AND
	</if>
	<if test="type != null and '' != type">
		dr.`type` = #{type, jdbcType=VARCHAR} AND
	</if>
	<if test="category != null and '' != category">
		dr.`category` = #{category, jdbcType=VARCHAR} AND
	</if>
	<if test="subCategory != null and '' != subCategory">
		dr.`subCategory` = #{subCategory, jdbcType=VARCHAR} AND
	</if>
	<if test="processTime != null">
		dr.`processTime` = #{processTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="processDesc != null and '' != processDesc">
		dr.`processDesc` = #{processDesc, jdbcType=VARCHAR} AND
	</if>
	<if test="processStep != null and '' != processStep">
		dr.`processStep` = #{processStep, jdbcType=VARCHAR} AND
	</if>
	<if test="remainProblem != null and '' != remainProblem">
		dr.`remainProblem` = #{remainProblem, jdbcType=VARCHAR} AND
	</if>
	<if test="customerInteraction != null and '' != customerInteraction">
		dr.`customerInteraction` = #{customerInteraction, jdbcType=VARCHAR} AND
	</if>
	<if test="transitHour != null">
		dr.`transitHour` = #{transitHour, jdbcType=REAL} AND
	</if>
	<if test="processHour != null">
		dr.`processHour` = #{processHour, jdbcType=REAL} AND
	</if>
	<if test="itemModel != null and '' != itemModel">
		dr.`itemModel` = #{itemModel, jdbcType=VARCHAR} AND
	</if>
	<if test="softVersion != null and '' != softVersion">
		dr.`softVersion` = #{softVersion, jdbcType=VARCHAR} AND
	</if>
	<if test="enabledFeatures != null and '' != enabledFeatures">
		dr.`enabledFeatures` = #{enabledFeatures, jdbcType=VARCHAR} AND
	</if>
	<if test="customTos != null and '' != customTos">
		dr.`customTos` = #{customTos, jdbcType=VARCHAR} AND
	</if>
	<if test="customCcs != null and '' != customCcs">
		dr.`customCcs` = #{customCcs, jdbcType=VARCHAR} AND
	</if>
	<if test="projectExecutionState != null and '' != projectExecutionState">
		dr.`projectExecutionState` = #{projectExecutionState, jdbcType=VARCHAR} AND
	</if>
	<if test="hasReport != null">
		dr.`hasReport` = #{hasReport, jdbcType=BIT} AND
	</if>
	<if test="quesnaireId != null">
		dr.`quesnaireId` = #{quesnaireId, jdbcType=INTEGER} AND
	</if>
	<if test="deliverFileIds != null and '' != deliverFileIds">
		dr.`deliverFileIds` = #{deliverFileIds, jdbcType=VARCHAR} AND
	</if>
	<if test="remark != null and '' != remark">
		dr.`remark` = #{remark, jdbcType=VARCHAR} AND
	</if>
	<if test="isReported != null">
		dr.`isReported` = #{isReported, jdbcType=BIT} AND
	</if>
	<if test="qualityFactor != null">
		dr.`qualityFactor` = #{qualityFactor, jdbcType=REAL} AND
	</if>
	<if test="status != null and '' != status">
		dr.`status` = #{status, jdbcType=VARCHAR} AND
	</if>
	<if test="disabled != null">
		dr.`disabled` = #{disabled, jdbcType=BIT} AND
	</if>
	<if test="createTime != null">
		dr.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="createBy != null and '' != createBy">
		dr.`createBy` = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		dr.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		dr.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="customInfo != null and '' != customInfo">
		<!-- dr.`customInfo` = #{customInfo, jdbcType=JSON} AND -->
		<foreach collection="customInfo" item="value" index="key">
		   dr.`customInfo` -> '$.${key}' like concat("%", #{value}, "%") AND
	    </foreach>
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:575 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.id != null">
			dr.`id` = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.projectId != null">
			dr.`projectId` = #{model.projectId, jdbcType=INTEGER} AND
		</if>
		<if test="model.projectType != null and '' != model.projectType">
			dr.`projectType` = #{model.projectType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectCode != null and '' != model.projectCode">
			dr.`projectCode` like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.projectName != null and '' != model.projectName">
			dr.`projectName` like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.contractNo != null and '' != model.contractNo">
			dr.`contractNo` like concat("%", #{model.contractNo, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.officeCode != null and '' != model.officeCode">
			dr.`officeCode` = #{model.officeCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.type != null and '' != model.type">
			dr.`type` = #{model.type, jdbcType=VARCHAR} AND
		</if>
		<if test="model.category != null and '' != model.category">
			dr.`category` = #{model.category, jdbcType=VARCHAR} AND
		</if>
		<if test="model.subCategory != null and '' != model.subCategory">
			dr.`subCategory` = #{model.subCategory, jdbcType=VARCHAR} AND
		</if>
		<if test="model.processTime != null">
			dr.`processTime` = #{model.processTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.processDesc != null and '' != model.processDesc">
			dr.`processDesc` = #{model.processDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.processStep != null and '' != model.processStep">
			dr.`processStep` = #{model.processStep, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remainProblem != null and '' != model.remainProblem">
			dr.`remainProblem` = #{model.remainProblem, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customerInteraction != null and '' != model.customerInteraction">
			dr.`customerInteraction` = #{model.customerInteraction, jdbcType=VARCHAR} AND
		</if>
		<if test="model.transitHour != null">
			dr.`transitHour` = #{model.transitHour, jdbcType=REAL} AND
		</if>
		<if test="model.processHour != null">
			dr.`processHour` = #{model.processHour, jdbcType=REAL} AND
		</if>
		<if test="model.itemModel != null and '' != model.itemModel">
			dr.`itemModel` like concat("%", #{model.itemModel, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.softVersion != null and '' != model.softVersion">
			dr.`softVersion` = #{model.softVersion, jdbcType=VARCHAR} AND
		</if>
		<if test="model.enabledFeatures != null and '' != model.enabledFeatures">
			dr.`enabledFeatures` = #{model.enabledFeatures, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customTos != null and '' != model.customTos">
			dr.`customTos` = #{model.customTos, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customCcs != null and '' != model.customCcs">
			dr.`customCcs` = #{model.customCcs, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectExecutionState != null and '' != model.projectExecutionState">
			dr.`projectExecutionState` = #{model.projectExecutionState, jdbcType=VARCHAR} AND
		</if>
		<if test="model.hasReport != null">
			dr.`hasReport` = #{model.hasReport, jdbcType=BIT} AND
		</if>
		<if test="model.quesnaireId != null">
			dr.`quesnaireId` = #{model.quesnaireId, jdbcType=INTEGER} AND
		</if>
		<if test="model.deliverFileIds != null and '' != model.deliverFileIds">
			dr.`deliverFileIds` = #{model.deliverFileIds, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			dr.`remark` = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.isReported != null">
			dr.`isReported` = #{model.isReported, jdbcType=BIT} AND
		</if>
		<if test="model.qualityFactor != null">
			dr.`qualityFactor` = #{model.qualityFactor, jdbcType=REAL} AND
		</if>
		<if test="model.status != null and '' != model.status">
			dr.`status` = #{model.status, jdbcType=VARCHAR} AND
		</if>
		<if test="model.disabled != null">
			dr.`disabled` = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.createTime != null">
			dr.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			dr.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			dr.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			dr.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customInfo != null and '' != model.customInfo">
			<!-- dr.`customInfo` = #{model.customInfo, jdbcType=JSON} AND -->
			<foreach collection="model.customInfo" item="value" index="key">
			   dr.`customInfo` -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.processStartTime != null">
			dr.`processTime` >= #{model.processStartTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.processEndTime != null">
			<![CDATA[ dr.`processTime` <= #{model.processEndTime, jdbcType=TIMESTAMP} AND ]]>
		</if>
		<!-- <if test="model.projectTypes != null">
			FIND_IN_SET(dr.`projectType`, CONCAT(#{model.projectTypes, jdbcType=VARCHAR}, ",30,40")) AND 
		</if>
		<if test="model.officeCodes != null">
			FIND_IN_SET(dr.`officeCode`, #{model.officeCodes, jdbcType=VARCHAR}) AND 
		</if> -->
		<if test="model.ids != null">
			<foreach collection="model.ids" item="item" open="dr.`id` in (" close=") AND" separator=",">
				 #{item}
			</foreach>
		</if>
		<if test="model.categorys != null">
			<foreach collection="model.categorys" item="item" open="dr.`category` in (" close=") AND" separator=",">
				 #{item}
			</foreach>
		</if>
		<if test="model.subCategorys != null">
			<foreach collection="model.subCategorys" item="item" open="dr.`subCategory` in (" close=") AND" separator=",">
				 #{item}
			</foreach>
		</if>
		<if test="model.transferProjectFlag == null">
			dr.customInfo ->> "$.transferProjectFlag" is null AND
		</if>
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:720 | fragment=sql_fuzzy_search
<if test="fuzzySearch == true and fuzzy != ''">
		(
			dr.`projectType` like CONCAT("%", #{fuzzy}, "%") or
			dr.`projectCode` like CONCAT("%", #{fuzzy}, "%") or
			dr.`projectName` like CONCAT("%", #{fuzzy}, "%") or
			dr.`contractNo` like CONCAT("%", #{fuzzy}, "%") or
			dr.`officeCode` like CONCAT("%", #{fuzzy}, "%") or
			dr.`type` like CONCAT("%", #{fuzzy}, "%") or
			dr.`category` like CONCAT("%", #{fuzzy}, "%") or
			dr.`subCategory` like CONCAT("%", #{fuzzy}, "%") or
			dr.`processDesc` like CONCAT("%", #{fuzzy}, "%") or
			dr.`processStep` like CONCAT("%", #{fuzzy}, "%") or
			dr.`remainProblem` like CONCAT("%", #{fuzzy}, "%") or
			dr.`customerInteraction` like CONCAT("%", #{fuzzy}, "%") or
			dr.`itemModel` like CONCAT("%", #{fuzzy}, "%") or
			dr.`softVersion` like CONCAT("%", #{fuzzy}, "%") or
			dr.`enabledFeatures` like CONCAT("%", #{fuzzy}, "%") or
			dr.`status` like CONCAT("%", #{fuzzy}, "%") or
			dr.`createBy` like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:743 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:750 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
			<include refid="sql_fuzzy_search" />
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:758 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:48 | fragment=Base_Column_List
`id`, `dispatchName`, `dispatchNo`, `dispatchSeq`, `contractNos`, `projectIds`, `type`, 
    `state`, `peopleNum`, `callbackState`, `facilitatorId`, `facilitatorCode`, `facilitatorName`, 
    `bankInfo`, `bankAccount`, `officeCode`, `profitDepCode`, `dutyPerson`, `officeDutyPerson`, 
    `isAccrued`, `isInvoiced`, `dispatchAmount`, `prepaidInfo`, `prepaidRule`, `acceptanceInfo`, 
    `reason`, `remark`, `dispatchTime`, `smsProjectCode`, `smsSubmitTime`, `smsProjectAmount`, 
    `smsAfProjectAmount`, `effectiveFrom`, `effectiveTo`, `disabled`, `dispatched`, `settled`, 
    `createBy`, `createTime`, `updateBy`, `updateTime`,
    `customInfo`

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1033 | fragment=selectDispatchVOWithAmount
select 
	    JSON_SET(h.customInfo, '$.projectProgress', IFNULL(ph.customInfo ->> "$.projectProgress", 0), '$.projectStateName', IFNULL(phsd.`basicDataName`, "")) as customInfo,
	    h.*, d.`departmentName` as officeName, d1.`departmentName` as profitDepName, bd.`basicDataName` as stateName, bd1.`basicDataName` as callbackStateName, bd2.basicDataName as typeName, concat(h.createBy, "-", ui.`realName`) as createName, 
	    sum(sc.`collected_money_amount`) as collectedAmount,
	    sum(sc.`delivered_money_amount`) as deliveredAmount,
	    sum(sc.`contract_money_amount`) as contractAmount,
	    group_concat(sc.`contract_num`) as collectContractNos,
	    ROUND(sum(sc.`collected_money_amount`) / sum(sc.`contract_money_amount`), 4) as collectedRatio, 
	    ds.settledAmount
	    <!-- ,ph.customInfo ->> "$.projectProgress" as `customInfo.projectProgress`,
	    phsd.`basicDataName` as `customInfo.projectStateName` -->
	    ,ROUND(ds.settledAmount / replace(h.dispatchAmount, ",","") * 100, 2) as settledRatio
	from
	    <!-- <![CDATA[
	    (select 
	        h.*, substring_index(
	            substring_index(h.contractNos, ',', b.id + 1), ',', - 1
	        ) as contractNo 
	    from
	        `pm_dispatch_project_header` h 
	        left join FIND_IN_SET_HELP b 
	            on b.id < (
	                length(h.contractNos) - length(replace(h.contractNos, ',', '')) + 1
	            )) h 
        ]]>
	    left join `sms_ofst_contract_head_sap` sc 
	        on h.`smsProjectCode` = sc.`projectCode` 
	        on sc.`contract_num` = h.`contractNo`  -->
	    pm_dispatch_project_header h
    	left join `sms_ofst_contract_head_sap` sc 
        	on FIND_IN_SET(sc.`contract_num`, h.`contractNos`)
       	left join `pm_project` ph
	    	on ph.projectId = h.projectIds
	    left join 
	        (select 
	            dispatchId, sum(replace(ds.`amount`, ",", "")) as settledAmount 
	        from
	            `pm_dispatch_project_settlement` ds 
	        where ds.`disabled` = false 
	        group by ds.`dispatchId`) ds 
	        on ds.`dispatchId` = h.`id` 
	    left join fnd_department d 
	        on h.`officeCode` = d.`departmentNum` 
	    left join fnd_department d1 
	        on h.`profitDepCode` = d1.`departmentNum` 
	    left join `fnd_basic_data` bd 
	        on h.`state` = bd.`basicDataId` 
	        and bd.`dataTypeCode` = 'dispatchState' 
	    left join `fnd_basic_data` bd1 
	        on h.`callbackState` = bd1.`basicDataId` 
	        and bd1.`dataTypeCode` = 'dispatchCbState' 
	    left join `fnd_basic_data` bd2 
	        on h.`type` = bd2.`basicDataId` 
	        and bd2.`dataTypeCode` = 'dispatchType' 
		LEFT JOIN fnd_basic_data phsd 
	        ON phsd.`dataTypeCode` = concat(ph.`projectType`, "_projectState") 
	        AND phsd.`basicDataId` = ph.`projectState` 
	    left join t_user u 
	        on u.`user_name` = h.`createBy` 
	    left join t_user_info ui 
	        on ui.`user_id` = u.`user_id`

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1097 | fragment=pageWhereSql
<where>
		<trim prefixOverrides="and |or" suffixOverrides="and |or">
			<if test="model != null ">
				<if test="model.id != null and '' != model.id">
					h.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.dispatchName != null and '' != model.dispatchName">
					h.dispatchName like concat("%", #{model.dispatchName, jdbcType=VARCHAR}, "%") AND
				</if>
				<if test="model.dispatchNo != null and '' != model.dispatchNo">
					h.dispatchNo like concat("%", #{model.dispatchNo, jdbcType=VARCHAR}, "%") AND
				</if>
				<if test="model.dispatchSeq != null and '' != model.dispatchSeq">
					h.dispatchSeq like concat("%", #{model.dispatchSeq, jdbcType=VARCHAR}, "%") AND
				</if>
				<if test="model.contractNos != null and '' != model.contractNos">
					(h.contractNos like concat("%", #{model.contractNos}, "%") or h.dispatchNo like concat("%", #{model.contractNos}, "%")) AND
				</if>
				<if test="model.projectIds != null and '' != model.projectIds">
					h.projectIds = #{model.projectIds, jdbcType=VARCHAR} AND
				</if>
				<if test="model.type != null and '' != model.type">
					h.type = #{model.type, jdbcType=VARCHAR} AND
				</if>
				<if test="model.state != null and '' != model.state">
					h.state = #{model.state, jdbcType=INTEGER} AND
				</if>
				<if test="model.peopleNum != null and '' != model.peopleNum">
					h.peopleNum = #{model.peopleNum, jdbcType=INTEGER} AND
				</if>
				<if test="model.callbackState != null and '' != model.callbackState">
					h.callbackState = #{model.callbackState, jdbcType=INTEGER} AND
				</if>
				<if test="model.facilitatorId != null and '' != model.facilitatorId">
					h.facilitatorId = #{model.facilitatorId, jdbcType=INTEGER} AND
				</if>
				<if test="model.facilitatorCode != null and '' != model.facilitatorCode">
					h.facilitatorCode = #{model.facilitatorCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.facilitatorName != null and '' != model.facilitatorName">
					h.facilitatorName = #{model.facilitatorName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.bankInfo != null and '' != model.bankInfo">
					h.bankInfo = #{model.bankInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.bankAccount != null and '' != model.bankAccount">
					h.bankAccount = #{model.bankAccount, jdbcType=VARCHAR} AND
				</if>
				<if test="model.officeCode != null and '' != model.officeCode">
					h.officeCode = #{model.officeCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.profitDepCode != null and '' != model.profitDepCode">
					h.profitDepCode = #{model.profitDepCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.dutyPerson != null and '' != model.dutyPerson">
					h.dutyPerson = #{model.dutyPerson, jdbcType=VARCHAR} AND
				</if>
				<if test="model.officeDutyPerson != null and '' != model.officeDutyPerson">
					h.officeDutyPerson = #{model.officeDutyPerson, jdbcType=VARCHAR} AND
				</if>
				<if test="model.isAccrued != null">
					h.isAccrued = #{model.isAccrued, jdbcType=BIT} AND
				</if>
				<if test="model.isInvoiced != null">
					h.isInvoiced = #{model.isInvoiced, jdbcType=BIT} AND
				</if>
				<if test="model.dispatchAmount != null and '' != model.dispatchAmount">
					h.dispatchAmount = #{model.dispatchAmount, jdbcType=VARCHAR} AND
				</if>
				<if test="model.prepaidInfo != null and '' != model.prepaidInfo">
					h.prepaidInfo = #{model.prepaidInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.prepaidRule != null and '' != model.prepaidRule">
					h.prepaidRule = #{model.prepaidRule, jdbcType=VARCHAR} AND
				</if>
				<if test="model.acceptanceInfo != null and '' != model.acceptanceInfo">
					h.acceptanceInfo = #{model.acceptanceInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.reason != null and '' != model.reason">
					h.reason = #{model.reason, jdbcType=VARCHAR} AND
				</if>
				<if test="model.remark != null and '' != model.remark">
					h.remark = #{model.remark, jdbcType=VARCHAR} AND
				</if>
				<if test="model.dispatchTime != null">
					h.dispatchTime = #{model.dispatchTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.smsProjectCode != null and '' != model.smsProjectCode">
					<!-- h.smsProjectCode = #{model.smsProjectCode, jdbcType=VARCHAR} AND -->
					 h.smsProjectCode LIKE CONCAT("%", #{model.smsProjectCode}, "%") AND
				</if>
				<if test="model.smsSubmitTime != null">
					h.smsSubmitTime = #{model.smsSubmitTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.smsProjectAmount != null and '' != model.smsProjectAmount">
					h.smsProjectAmount = #{model.smsProjectAmount, jdbcType=VARCHAR} AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ h.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
				<if test="model.effectiveTo != null">
					<!-- h.effectiveTo = #{model.effectiveTo, jdbcType=TIMESTAMP} AND -->
					<![CDATA[ (h.`effectiveTo` is null or h.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.disabled != null">
					h.disabled = #{model.disabled, jdbcType=BIT} AND
				</if>
				<if test="model.dispatched != null">
					h.dispatched = #{model.dispatched, jdbcType=BIT} AND
				</if>
				<if test="model.settled != null">
					h.settled = #{model.settled, jdbcType=BIT} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					h.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					h.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					h.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					h.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.customInfo != null">
		            <!-- h.customInfo = #{model.customInfo, jdbcType=JSON} AND -->
		            <foreach collection="model.customInfo" item="value" index="key">
		                h.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
		            </foreach>
		        </if>
				<if test="model.projectId != null and '' != model.projectId">
					FIND_IN_SET(#{model.projectId}, h.projectIds) AND
				</if>
				<if test="model.officeCodes != null">
					FIND_IN_SET(h.officeCode, #{model.officeCodes}) AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					h.dispatchName like CONCAT("%", #{fuzzy}, "%") or
					h.dispatchNo like CONCAT("%", #{fuzzy}, "%") or
					h.dispatchSeq like CONCAT("%", #{fuzzy}, "%") or
					h.contractNos like CONCAT("%", #{fuzzy}, "%") or
					h.facilitatorName like CONCAT("%", #{fuzzy}, "%") or
					h.smsProjectCode like CONCAT("%", #{fuzzy}, "%") or
					h.smsProjectAmount like CONCAT("%", #{fuzzy}, "%") or
					h.customInfo like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1252 | fragment=pageLimitSql
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1264 | fragment=baseWhereSql
<where>
		<trim prefixOverrides="and |or" suffixOverrides="and |or">
			<if test="id != null">
				h.`id` = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="dispatchName != null and '' != dispatchName">
				h.`dispatchName` = #{dispatchName, jdbcType=VARCHAR} AND
			</if>
			<if test="dispatchNo != null and '' != dispatchNo">
				h.`dispatchNo` = #{dispatchNo, jdbcType=VARCHAR} AND
			</if>
			<if test="dispatchSeq != null and '' != dispatchSeq">
				h.`dispatchSeq` = #{dispatchSeq, jdbcType=VARCHAR} AND
			</if>
			<if test="contractNos != null and '' != contractNos">
				h.`contractNos` = #{contractNos, jdbcType=VARCHAR} AND
			</if>
			<if test="projectIds != null and '' != projectIds">
				h.`projectIds` = #{projectIds, jdbcType=VARCHAR} AND
			</if>
			<if test="type != null and '' != type">
				h.`type` = #{type, jdbcType=VARCHAR} AND
			</if>
			<if test="state != null">
				h.`state` = #{state, jdbcType=INTEGER} AND
			</if>
			<if test="peopleNum != null">
				h.`peopleNum` = #{peopleNum, jdbcType=INTEGER} AND
			</if>
			<if test="callbackState != null">
				h.`callbackState` = #{callbackState, jdbcType=INTEGER} AND
			</if>
			<if test="facilitatorId != null">
				h.`facilitatorId` = #{facilitatorId, jdbcType=INTEGER} AND
			</if>
			<if test="facilitatorCode != null and '' != facilitatorCode">
				h.`facilitatorCode` = #{facilitatorCode, jdbcType=VARCHAR} AND
			</if>
			<if test="facilitatorName != null and '' != facilitatorName">
				h.`facilitatorName` = #{facilitatorName, jdbcType=VARCHAR} AND
			</if>
			<if test="bankInfo != null and '' != bankInfo">
				h.`bankInfo` = #{bankInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="bankAccount != null and '' != bankAccount">
				h.`bankAccount` = #{bankAccount, jdbcType=VARCHAR} AND
			</if>
			<if test="officeCode != null and '' != officeCode">
				h.`officeCode` = #{officeCode, jdbcType=VARCHAR} AND
			</if>
			<if test="profitDepCode != null and '' != profitDepCode">
				h.`profitDepCode` = #{profitDepCode, jdbcType=VARCHAR} AND
			</if>
			<if test="dutyPerson != null and '' != dutyPerson">
				h.`dutyPerson` = #{dutyPerson, jdbcType=VARCHAR} AND
			</if>
			<if test="officeDutyPerson != null and '' != officeDutyPerson">
				h.`officeDutyPerson` = #{officeDutyPerson, jdbcType=VARCHAR} AND
			</if>
			<if test="isAccrued != null">
				h.`isAccrued` = #{isAccrued, jdbcType=BIT} AND
			</if>
			<if test="isInvoiced != null">
				h.`isInvoiced` = #{isInvoiced, jdbcType=BIT} AND
			</if>
			<if test="dispatchAmount != null and '' != dispatchAmount">
				h.`dispatchAmount` = #{dispatchAmount, jdbcType=VARCHAR} AND
			</if>
			<if test="prepaidInfo != null and '' != prepaidInfo">
				h.`prepaidInfo` = #{prepaidInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="prepaidRule != null and '' != prepaidRule">
				h.`prepaidRule` = #{prepaidRule, jdbcType=VARCHAR} AND
			</if>
			<if test="acceptanceInfo != null and '' != acceptanceInfo">
				h.`acceptanceInfo` = #{acceptanceInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="reason != null and '' != reason">
				h.`reason` = #{reason, jdbcType=VARCHAR} AND
			</if>
			<if test="remark != null and '' != remark">
				h.`remark` = #{remark, jdbcType=VARCHAR} AND
			</if>
			<if test="dispatchTime != null">
				h.`dispatchTime` = #{dispatchTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="dispatchYear != null">
				#{dispatchYear, jdbcType=INTEGER} = YEAR(h.`dispatchTime`) AND
			</if>
			<if test="smsProjectCode != null and '' != smsProjectCode">
				h.`smsProjectCode` = #{smsProjectCode, jdbcType=VARCHAR} AND
			</if>
			<if test="smsSubmitTime != null">
				h.`smsSubmitTime` = #{smsSubmitTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="smsProjectAmount != null and '' != smsProjectAmount">
				h.`smsProjectAmount` = #{smsProjectAmount, jdbcType=VARCHAR} AND
			</if>
			<if test="smsAfProjectAmount != null and '' != smsAfProjectAmount">
				h.`smsAfProjectAmount` = #{smsAfProjectAmount, jdbcType=VARCHAR} AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ h.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
			<if test="effectiveTo != null">
				<!-- h.effectiveTo = #{effectiveTo, jdbcType=TIMESTAMP} AND -->
				<![CDATA[ (h.`effectiveTo` is null or h.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="disabled != null">
				h.`disabled` = #{disabled, jdbcType=BIT} AND
			</if>
			<if test="dispatched != null">
				h.`dispatched` = #{dispatched, jdbcType=BIT} AND
			</if>
			<if test="settled != null">
				h.`settled` = #{settled, jdbcType=BIT} AND
			</if>
			<if test="createBy != null and '' != createBy">
				h.`createBy` = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				h.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				h.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				h.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="customInfo != null">
                <!-- h.customInfo = #{customInfo, jdbcType=JSON} AND -->
                <foreach collection="customInfo" item="value" index="key">
                    h.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
                </foreach>
            </if>
            <if test="projectId != null and '' != projectId">
                FIND_IN_SET(#{projectId}, h.projectIds) AND
            </if>
			<if test="officeCodes != null">
				FIND_IN_SET(h.officeCode, #{officeCodes}) AND
			</if>
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1410 | fragment=sql_where_selective
<if test="id != null">
        h.`id` = #{id, jdbcType=INTEGER} AND
    </if>
    <if test="dispatchName != null and '' != dispatchName">
        h.`dispatchName` = #{dispatchName, jdbcType=VARCHAR} AND
    </if>
    <if test="dispatchNo != null and '' != dispatchNo">
        h.`dispatchNo` = #{dispatchNo, jdbcType=VARCHAR} AND
    </if>
    <if test="dispatchSeq != null and '' != dispatchSeq">
        h.`dispatchSeq` = #{dispatchSeq, jdbcType=VARCHAR} AND
    </if>
    <if test="contractNos != null and '' != contractNos">
        h.`contractNos` = #{contractNos, jdbcType=VARCHAR} AND
    </if>
    <if test="projectIds != null and '' != projectIds">
        h.`projectIds` = #{projectIds, jdbcType=VARCHAR} AND
    </if>
    <if test="type != null and '' != type">
        h.`type` = #{type, jdbcType=VARCHAR} AND
    </if>
    <if test="state != null">
        h.`state` = #{state, jdbcType=INTEGER} AND
    </if>
    <if test="peopleNum != null">
        h.`peopleNum` = #{peopleNum, jdbcType=INTEGER} AND
    </if>
    <if test="callbackState != null">
        h.`callbackState` = #{callbackState, jdbcType=INTEGER} AND
    </if>
    <if test="facilitatorId != null">
        h.`facilitatorId` = #{facilitatorId, jdbcType=INTEGER} AND
    </if>
    <if test="facilitatorCode != null and '' != facilitatorCode">
        h.`facilitatorCode` = #{facilitatorCode, jdbcType=VARCHAR} AND
    </if>
    <if test="facilitatorName != null and '' != facilitatorName">
        h.`facilitatorName` = #{facilitatorName, jdbcType=VARCHAR} AND
    </if>
    <if test="bankInfo != null and '' != bankInfo">
        h.`bankInfo` = #{bankInfo, jdbcType=VARCHAR} AND
    </if>
    <if test="bankAccount != null and '' != bankAccount">
        h.`bankAccount` = #{bankAccount, jdbcType=VARCHAR} AND
    </if>
    <if test="officeCode != null and '' != officeCode">
        h.`officeCode` = #{officeCode, jdbcType=VARCHAR} AND
    </if>
    <if test="profitDepCode != null and '' != profitDepCode">
        h.`profitDepCode` = #{profitDepCode, jdbcType=VARCHAR} AND
    </if>
    <if test="dutyPerson != null and '' != dutyPerson">
        h.`dutyPerson` = #{dutyPerson, jdbcType=VARCHAR} AND
    </if>
    <if test="officeDutyPerson != null and '' != officeDutyPerson">
        h.`officeDutyPerson` = #{officeDutyPerson, jdbcType=VARCHAR} AND
    </if>
    <if test="isAccrued != null">
        h.`isAccrued` = #{isAccrued, jdbcType=BIT} AND
    </if>
    <if test="isInvoiced != null">
        h.`isInvoiced` = #{isInvoiced, jdbcType=BIT} AND
    </if>
    <if test="dispatchAmount != null and '' != dispatchAmount">
        h.`dispatchAmount` = #{dispatchAmount, jdbcType=VARCHAR} AND
    </if>
    <if test="prepaidInfo != null and '' != prepaidInfo">
        h.`prepaidInfo` = #{prepaidInfo, jdbcType=VARCHAR} AND
    </if>
    <if test="prepaidRule != null and '' != prepaidRule">
        h.`prepaidRule` = #{prepaidRule, jdbcType=VARCHAR} AND
    </if>
    <if test="acceptanceInfo != null and '' != acceptanceInfo">
        h.`acceptanceInfo` = #{acceptanceInfo, jdbcType=VARCHAR} AND
    </if>
    <if test="reason != null and '' != reason">
        h.`reason` = #{reason, jdbcType=VARCHAR} AND
    </if>
    <if test="remark != null and '' != remark">
        h.`remark` = #{remark, jdbcType=VARCHAR} AND
    </if>
    <if test="dispatchTime != null">
        h.`dispatchTime` = #{dispatchTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="dispatchYear != null">
        #{dispatchYear, jdbcType=INTEGER} = YEAR(h.`dispatchTime`) AND
    </if>
    <if test="smsProjectCode != null and '' != smsProjectCode">
        h.`smsProjectCode` = #{smsProjectCode, jdbcType=VARCHAR} AND
    </if>
    <if test="smsSubmitTime != null">
        h.`smsSubmitTime` = #{smsSubmitTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="smsProjectAmount != null and '' != smsProjectAmount">
        h.`smsProjectAmount` = #{smsProjectAmount, jdbcType=VARCHAR} AND
    </if>
    <if test="smsAfProjectAmount != null and '' != smsAfProjectAmount">
        h.`smsAfProjectAmount` = #{smsAfProjectAmount, jdbcType=VARCHAR} AND
    </if>
    <if test="effectiveFrom != null">
        <![CDATA[ h.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
    </if>
    <if test="effectiveTo != null">
        <!-- h.effectiveTo = #{effectiveTo, jdbcType=TIMESTAMP} AND -->
        <![CDATA[ (h.`effectiveTo` is null or h.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
    </if>
    <if test="disabled != null">
        h.`disabled` = #{disabled, jdbcType=BIT} AND
    </if>
    <if test="dispatched != null">
        h.`dispatched` = #{dispatched, jdbcType=BIT} AND
    </if>
    <if test="settled != null">
        h.`settled` = #{settled, jdbcType=BIT} AND
    </if>
    <if test="createBy != null and '' != createBy">
        h.`createBy` = #{createBy, jdbcType=VARCHAR} AND
    </if>
    <if test="createTime != null">
        h.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="updateBy != null and '' != updateBy">
        h.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
    </if>
    <if test="updateTime != null">
        h.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="customInfo != null">
        <!-- h.customInfo = #{customInfo, jdbcType=JSON} AND -->
        <foreach collection="customInfo" item="value" index="key">
            h.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
        </foreach>
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1545 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.id != null">
			h.`id` = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.dispatchName != null and '' != model.dispatchName">
			h.`dispatchName` = #{model.dispatchName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dispatchNo != null and '' != model.dispatchNo">
			h.`dispatchNo` = #{model.dispatchNo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dispatchSeq != null and '' != model.dispatchSeq">
			h.`dispatchSeq` = #{model.dispatchSeq, jdbcType=VARCHAR} AND
		</if>
		<if test="model.contractNos != null and '' != model.contractNos">
			h.`contractNos` = #{model.contractNos, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectIds != null and '' != model.projectIds">
			h.`projectIds` = #{model.projectIds, jdbcType=VARCHAR} AND
		</if>
		<if test="model.type != null and '' != model.type">
			h.`type` = #{model.type, jdbcType=VARCHAR} AND
		</if>
		<if test="model.state != null">
			h.`state` = #{model.state, jdbcType=INTEGER} AND
		</if>
		<if test="model.peopleNum != null">
			h.`peopleNum` = #{model.peopleNum, jdbcType=INTEGER} AND
		</if>
		<if test="model.callbackState != null">
			h.`callbackState` = #{model.callbackState, jdbcType=INTEGER} AND
		</if>
		<if test="model.facilitatorId != null">
			h.`facilitatorId` = #{model.facilitatorId, jdbcType=INTEGER} AND
		</if>
		<if test="model.facilitatorCode != null and '' != model.facilitatorCode">
			h.`facilitatorCode` = #{model.facilitatorCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.facilitatorName != null and '' != model.facilitatorName">
			h.`facilitatorName` = #{model.facilitatorName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.bankInfo != null and '' != model.bankInfo">
			h.`bankInfo` = #{model.bankInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.bankAccount != null and '' != model.bankAccount">
			h.`bankAccount` = #{model.bankAccount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.officeCode != null and '' != model.officeCode">
			h.`officeCode` = #{model.officeCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.profitDepCode != null and '' != model.profitDepCode">
			h.`profitDepCode` = #{model.profitDepCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dutyPerson != null and '' != model.dutyPerson">
			h.`dutyPerson` = #{model.dutyPerson, jdbcType=VARCHAR} AND
		</if>
		<if test="model.officeDutyPerson != null and '' != model.officeDutyPerson">
			h.`officeDutyPerson` = #{model.officeDutyPerson, jdbcType=VARCHAR} AND
		</if>
		<if test="model.isAccrued != null">
			h.`isAccrued` = #{model.isAccrued, jdbcType=BIT} AND
		</if>
		<if test="model.isInvoiced != null">
			h.`isInvoiced` = #{model.isInvoiced, jdbcType=BIT} AND
		</if>
		<if test="model.dispatchAmount != null and '' != model.dispatchAmount">
			h.`dispatchAmount` = #{model.dispatchAmount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.prepaidInfo != null and '' != model.prepaidInfo">
			h.`prepaidInfo` = #{model.prepaidInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.prepaidRule != null and '' != model.prepaidRule">
			h.`prepaidRule` = #{model.prepaidRule, jdbcType=VARCHAR} AND
		</if>
		<if test="model.acceptanceInfo != null and '' != model.acceptanceInfo">
			h.`acceptanceInfo` = #{model.acceptanceInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.reason != null and '' != model.reason">
			h.`reason` = #{model.reason, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			h.`remark` = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dispatchTime != null">
			h.`dispatchTime` = #{model.dispatchTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.dispatchYear != null">
			#{model.dispatchYear, jdbcType=INTEGER} = YEAR(h.`dispatchTime`) AND
		</if>
		<if test="model.smsProjectCode != null and '' != model.smsProjectCode">
			h.`smsProjectCode` = #{model.smsProjectCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.smsSubmitTime != null">
			h.`smsSubmitTime` = #{model.smsSubmitTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.smsProjectAmount != null and '' != model.smsProjectAmount">
			h.`smsProjectAmount` = #{model.smsProjectAmount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.smsAfProjectAmount != null and '' != model.smsAfProjectAmount">
			h.`smsAfProjectAmount` = #{model.smsAfProjectAmount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.effectiveFrom != null">
            <![CDATA[ h.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
	    </if>
	    <if test="model.effectiveTo != null">
			<!-- h.`effectiveTo` = #{model.effectiveTo, jdbcType=TIMESTAMP} AND -->
	        <![CDATA[ (h.`effectiveTo` is null or h.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
	    </if>
		<if test="model.disabled != null">
			h.`disabled` = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.dispatched != null">
			h.`dispatched` = #{model.dispatched, jdbcType=BIT} AND
		</if>
		<if test="model.settled != null">
			h.`settled` = #{model.settled, jdbcType=BIT} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			h.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.createTime != null">
			h.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			h.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			h.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.customInfo != null">
            <!-- h.customInfo = #{model.customInfo, jdbcType=JSON} AND -->
            <foreach collection="model.customInfo" item="value" index="key">
                h.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
            </foreach>
        </if>
	</if>
	<if test="fuzzySearch == true and fuzzy != ''">
		(
			h.`dispatchName` like CONCAT("%", #{fuzzy}, "%") or
			h.`dispatchNo` like CONCAT("%", #{fuzzy}, "%") or
			h.`dispatchSeq` like CONCAT("%", #{fuzzy}, "%") or
			h.`contractNos` like CONCAT("%", #{fuzzy}, "%") or
			h.`projectIds` like CONCAT("%", #{fuzzy}, "%") or
			h.`type` like CONCAT("%", #{fuzzy}, "%") or
			h.`facilitatorCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`facilitatorName` like CONCAT("%", #{fuzzy}, "%") or
			h.`bankInfo` like CONCAT("%", #{fuzzy}, "%") or
			h.`bankAccount` like CONCAT("%", #{fuzzy}, "%") or
			h.`officeCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`profitDepCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`dutyPerson` like CONCAT("%", #{fuzzy}, "%") or
			h.`officeDutyPerson` like CONCAT("%", #{fuzzy}, "%") or
			h.`dispatchAmount` like CONCAT("%", #{fuzzy}, "%") or
			h.`prepaidInfo` like CONCAT("%", #{fuzzy}, "%") or
			h.`prepaidRule` like CONCAT("%", #{fuzzy}, "%") or
			h.`acceptanceInfo` like CONCAT("%", #{fuzzy}, "%") or
			h.`reason` like CONCAT("%", #{fuzzy}, "%") or
			h.`remark` like CONCAT("%", #{fuzzy}, "%") or
			h.`smsProjectCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`smsProjectAmount` like CONCAT("%", #{fuzzy}, "%") or
			h.`smsAfProjectAmount` like CONCAT("%", #{fuzzy}, "%") or
			h.`createBy` like CONCAT("%", #{fuzzy}, "%") or
			h.`updateBy` like CONCAT("%", #{fuzzy}, "%") or
			h.`customInfo` like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1712 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1719 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1726 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchSettlementMapper.xml:32 | fragment=Base_Column_List
id, settleSeq, dispatchId, dispatchSeq, progressDesc, progressRatio, acceptanceDesc, 
    acceptanceRatio, ratio, amount, memo, confirmTime, paymentTime, remark, state, sseId, 
    year, quarter, month, customInfo, createTime, createBy, updateTime, updateBy, disabled, settled

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchSettlementMapper.xml:815 | fragment=sql_model_where
<if test="model != null ">
		<if test="model.id != null and '' != model.id">
			s.id = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.settleSeq != null and '' != model.settleSeq">
			s.settleSeq like concat("%", #{model.settleSeq, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.dispatchId != null and '' != model.dispatchId">
			s.dispatchId = #{model.dispatchId, jdbcType=INTEGER} AND
		</if>
		<if test="model.dispatchSeq != null and '' != model.dispatchSeq">
			s.dispatchSeq like concat("%", #{model.dispatchSeq, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.progressDesc != null and '' != model.progressDesc">
			s.progressDesc = #{model.progressDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.progressRatio != null and '' != model.progressRatio">
			s.progressRatio = #{model.progressRatio, jdbcType=REAL} AND
		</if>
		<if test="model.acceptanceDesc != null and '' != model.acceptanceDesc">
			s.acceptanceDesc = #{model.acceptanceDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.acceptanceRatio != null and '' != model.acceptanceRatio">
			s.acceptanceRatio = #{model.acceptanceRatio, jdbcType=VARCHAR} AND
		</if>
		<if test="model.ratio != null and '' != model.ratio">
			s.ratio = #{model.ratio, jdbcType=VARCHAR} AND
		</if>
		<if test="model.amount != null and '' != model.amount">
			s.amount = #{model.amount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.memo != null and '' != model.memo">
			s.memo = #{model.memo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.confirmTime != null and '' != model.confirmTime">
			s.confirmTime = #{model.confirmTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.paymentTime != null and '' != model.paymentTime">
			s.paymentTime = #{model.paymentTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			s.remark = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.state != null and '' != model.state">
			s.state = #{model.state, jdbcType=INTEGER} AND
		</if>
		<if test="model.sseId != null and '' != model.sseId">
			s.sseId = #{model.sseId, jdbcType=INTEGER} AND
		</if>
		<if test="model.year != null and '' != model.year">
			s.year = #{model.year, jdbcType=INTEGER} AND
		</if>
		<if test="model.quarter != null and '' != model.quarter">
			s.quarter = #{model.quarter, jdbcType=INTEGER} AND
		</if>
		<if test="model.month != null and '' != model.month">
			s.month = #{model.month, jdbcType=INTEGER} AND
		</if>
		<if test="model.customInfo != null and '' != model.customInfo">
			<!-- s.customInfo = #{model.customInfo, jdbcType=OTHER} AND -->
			<foreach collection="model.customInfo" item="value" index="key">
                s.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
            </foreach>
		</if>
		<if test="model.createTime != null">
			s.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			s.createBy = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			s.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			s.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.disabled != null">
			s.disabled = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.settled != null">
            s.settled = #{model.settled, jdbcType=BIT} AND
        </if>
		<if test="model.projectId != null">
			find_in_set(#{model.projectId}, h.projectIds)
		</if>
		<if test="model.dateStartTime != null">
            <choose>
                <when test="model.dateType = 'confirmTime'"> s.confirmTime >= #{model.dateStartTime, jdbcType=TIMESTAMP} AND </when>
                <when test="model.dateType = 'paymentTime'"> s.paymentTime >= #{model.dateStartTime, jdbcType=TIMESTAMP} AND </when>
            </choose>
        </if>
        <if test="model.dateEndTime != null">
            <choose>
                <when test="model.dateType = 'confirmTime'"> <![CDATA[ s.confirmTime <= #{model.dateEndTime, jdbcType=TIMESTAMP} AND ]]> </when>
                <when test="model.dateType = 'paymentTime'"> <![CDATA[ s.paymentTime <= #{model.dateEndTime, jdbcType=TIMESTAMP} AND ]]> </when>
            </choose>
        </if>
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchSettlementMapper.xml:915 | fragment=sql_model_dispatch_where
h.disabled = false AND
    <if test="model != null">
        <if test="model.dispatched != null"> h.`dispatched` = #{model.dispatched} AND </if>
        <if test="model.dispatch != null">
            <if test="model.dispatch.dispatchNo != null and '' != model.dispatch.dispatchNo">
                h.dispatchNo like concat("%", #{model.dispatch.dispatchNo, jdbcType=VARCHAR}, "%") AND
            </if>
            <if test="model.dispatch.dispatchSeq != null and '' != model.dispatch.dispatchSeq">
                h.dispatchSeq like concat("%", #{model.dispatch.dispatchSeq, jdbcType=VARCHAR}, "%") AND
            </if>
            <if test="model.dispatch.dispatchName != null and '' != model.dispatch.dispatchName">
                h.dispatchName like concat("%", #{model.dispatch.dispatchName, jdbcType=VARCHAR}, "%") AND
            </if>
            <if test="model.dispatch.smsProjectCode != null and '' != model.dispatch.smsProjectCode">
                h.smsProjectCode like concat("%", #{model.dispatch.smsProjectCode, jdbcType=VARCHAR}, "%") AND
            </if>
            <if test="model.dispatch.contractNos != null and '' != model.dispatch.contractNos">
                h.contractNos like concat("%", #{model.dispatch.contractNos, jdbcType=VARCHAR}, "%") AND
            </if>
            <if test="model.dispatch.type != null and '' != model.dispatch.type">
                h.type like concat("%", #{model.dispatch.type, jdbcType=VARCHAR}, "%") AND
            </if>
            <if test="model.dispatch.facilitatorName != null and '' != model.dispatch.facilitatorName">
                h.facilitatorName like concat("%", #{model.dispatch.facilitatorName, jdbcType=VARCHAR}, "%") AND
            </if>
        </if>
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:29 | fragment=Base_Column_List
`id`, `code`, `account`, `name`, `type`, `bankInfo`, `bankAccount`, `cnapsCode`, 
    `contacts`, `tel`, `email`, `state`, `needApprove`, `approveStatus`, `deliveryIds`, 
    `relateType`, `effectiveFrom`, `effectiveTo`, `createBy`, `createTime`, `updateBy`, 
    `updateTime`, `customInfo`

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:306 | fragment=sql_where_selective
<if test="id != null">
        f.`id` = #{id, jdbcType=INTEGER} AND
    </if>
    <if test="code != null and '' != code">
        f.`code` = #{code, jdbcType=VARCHAR} AND
    </if>
    <if test="account != null and '' != account">
        f.`account` = #{account, jdbcType=VARCHAR} AND
    </if>
    <if test="name != null and '' != name">
        f.`name` = #{name, jdbcType=VARCHAR} AND
    </if>
    <if test="type != null and '' != type">
        f.`type` = #{type, jdbcType=VARCHAR} AND
    </if>
    <if test="bankInfo != null and '' != bankInfo">
        f.`bankInfo` = #{bankInfo, jdbcType=VARCHAR} AND
    </if>
    <if test="bankAccount != null and '' != bankAccount">
        f.`bankAccount` = #{bankAccount, jdbcType=VARCHAR} AND
    </if>
    <if test="cnapsCode != null and '' != cnapsCode">
        f.`cnapsCode` = #{cnapsCode, jdbcType=VARCHAR} AND
    </if>
    <if test="contacts != null and '' != contacts">
        f.`contacts` = #{contacts, jdbcType=VARCHAR} AND
    </if>
    <if test="tel != null and '' != tel">
        f.`tel` = #{tel, jdbcType=VARCHAR} AND
    </if>
    <if test="email != null and '' != email">
        f.`email` = #{email, jdbcType=VARCHAR} AND
    </if>
    <if test="state != null">
        f.`state` = #{state, jdbcType=BIT} AND
    </if>
    <if test="needApprove != null">
        f.`needApprove` = #{needApprove, jdbcType=BIT} AND
    </if>
    <if test="approveStatus != null">
        f.`approveStatus` = #{approveStatus, jdbcType=INTEGER} AND
    </if>
    <if test="deliveryIds != null and '' != deliveryIds">
        f.`deliveryIds` = #{deliveryIds, jdbcType=VARCHAR} AND
    </if>
    <if test="relateType != null and '' != relateType">
        f.`relateType` = #{relateType, jdbcType=VARCHAR} AND
    </if>
    <if test="effectiveFrom != null">
        <![CDATA[ f.`effectiveFrom` <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
    </if>
    <if test="effectiveTo != null">
        <![CDATA[ (f.`effectiveTo` is null or f.`effectiveTo` >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
    </if>
    <if test="createBy != null and '' != createBy">
        f.`createBy` = #{createBy, jdbcType=VARCHAR} AND
    </if>
    <if test="createTime != null">
        f.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="updateBy != null and '' != updateBy">
        f.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
    </if>
    <if test="updateTime != null">
        f.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="customInfo != null">
        f.`customInfo` = #{customInfo, jdbcType=JSON} AND
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:378 | fragment=sql_model_where_selective
<if test="model != null ">
        <if test="model.id != null">
            f.`id` = #{model.id, jdbcType=INTEGER} AND
        </if>
        <if test="model.code != null and '' != model.code">
            f.`code` = #{model.code, jdbcType=VARCHAR} AND
        </if>
        <if test="model.account != null and '' != model.account">
            f.`account` = #{model.account, jdbcType=VARCHAR} AND
        </if>
        <if test="model.name != null and '' != model.name">
            f.`name` = #{model.name, jdbcType=VARCHAR} AND
        </if>
        <if test="model.type != null and '' != model.type">
            f.`type` = #{model.type, jdbcType=VARCHAR} AND
        </if>
        <if test="model.bankInfo != null and '' != model.bankInfo">
            f.`bankInfo` = #{model.bankInfo, jdbcType=VARCHAR} AND
        </if>
        <if test="model.bankAccount != null and '' != model.bankAccount">
            f.`bankAccount` = #{model.bankAccount, jdbcType=VARCHAR} AND
        </if>
        <if test="model.cnapsCode != null and '' != model.cnapsCode">
            f.`cnapsCode` = #{model.cnapsCode, jdbcType=VARCHAR} AND
        </if>
        <if test="model.contacts != null and '' != model.contacts">
            f.`contacts` = #{model.contacts, jdbcType=VARCHAR} AND
        </if>
        <if test="model.tel != null and '' != model.tel">
            f.`tel` = #{model.tel, jdbcType=VARCHAR} AND
        </if>
        <if test="model.email != null and '' != model.email">
            f.`email` = #{model.email, jdbcType=VARCHAR} AND
        </if>
        <if test="model.state != null">
            f.`state` = #{model.state, jdbcType=BIT} AND
        </if>
        <if test="model.needApprove != null">
            f.`needApprove` = #{model.needApprove, jdbcType=BIT} AND
        </if>
        <if test="model.approveStatus != null">
            f.`approveStatus` = #{model.approveStatus, jdbcType=INTEGER} AND
        </if>
        <if test="model.deliveryIds != null and '' != model.deliveryIds">
            f.`deliveryIds` = #{model.deliveryIds, jdbcType=VARCHAR} AND
        </if>
        <if test="model.relateType != null and '' != model.relateType">
            f.`relateType` = #{model.relateType, jdbcType=VARCHAR} AND
        </if>
        <if test="model.effectiveFrom != null">
            <![CDATA[ f.`effectiveFrom` <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
        </if>
        <if test="model.effectiveTo != null">
            <![CDATA[ (f.`effectiveTo` is null or f.`effectiveTo` >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
        </if>
        <if test="model.createBy != null and '' != model.createBy">
            f.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
        </if>
        <if test="model.createTime != null">
            f.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
        </if>
        <if test="model.updateBy != null and '' != model.updateBy">
            f.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
        </if>
        <if test="model.updateTime != null">
            f.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
        </if>
        <if test="model.customInfo != null">
            f.`customInfo` = #{model.customInfo, jdbcType=JSON} AND
        </if>
    </if>
    <if test="fuzzySearch == true and fuzzy != ''">
        (
            f.`code` like CONCAT("%", #{fuzzy}, "%") or
            f.`account` like CONCAT("%", #{fuzzy}, "%") or
            f.`name` like CONCAT("%", #{fuzzy}, "%") or
            f.`type` like CONCAT("%", #{fuzzy}, "%") or
            f.`bankInfo` like CONCAT("%", #{fuzzy}, "%") or
            f.`bankAccount` like CONCAT("%", #{fuzzy}, "%") or
            f.`cnapsCode` like CONCAT("%", #{fuzzy}, "%") or
            f.`contacts` like CONCAT("%", #{fuzzy}, "%") or
            f.`tel` like CONCAT("%", #{fuzzy}, "%") or
            f.`email` like CONCAT("%", #{fuzzy}, "%") or
            f.`deliveryIds` like CONCAT("%", #{fuzzy}, "%") or
            f.`relateType` like CONCAT("%", #{fuzzy}, "%") or
            f.`createBy` like CONCAT("%", #{fuzzy}, "%") or
            f.`updateBy` like CONCAT("%", #{fuzzy}, "%")
        )
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:470 | fragment=sql_where
<where>
        <trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
            <include refid="sql_where_selective" />
        </trim>
    </where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:477 | fragment=sql_model_where
<where>
        <trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
            <include refid="sql_model_where_selective" />
        </trim>
    </where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:484 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:41 | fragment=Base_Column_List
projectId, projectType, projectCode, projectName, projectState, isback, column001, 
    column002, column003, column004, column005, column006, column007, column008, column009, 
    column010, column011, column012, columno12_readonly, column013, customerProjectName, 
    salesType, compId, createTime, createBy, updateTime, updateBy, effectiveFrom, effectiveTo, 
    projectStartTime, projectRefreshTime, projectCloseTime, customInfo, customConfig,
    column014

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:481 | fragment=sql_where_selective
<if test="projectId != null">
		ph.projectId = #{projectId, jdbcType=INTEGER} AND
	</if>
	<if test="projectType != null and '' != projectType">
		ph.projectType = #{projectType, jdbcType=VARCHAR} AND
	</if>
	<if test="projectCode != null and '' != projectCode">
		ph.projectCode = #{projectCode, jdbcType=VARCHAR} AND
	</if>
	<if test="projectName != null and '' != projectName">
		ph.projectName = #{projectName, jdbcType=VARCHAR} AND
	</if>
	<if test="projectState != null and '' != projectState">
		ph.projectState = #{projectState, jdbcType=VARCHAR} AND
	</if>
	<if test="isback != null and '' != isback">
		ph.isback = #{isback, jdbcType=VARCHAR} AND
	</if>
	<if test="column001 != null and '' != column001">
		ph.column001 = #{column001, jdbcType=VARCHAR} AND
	</if>
	<if test="column002 != null and '' != column002">
		ph.column002 = #{column002, jdbcType=VARCHAR} AND
	</if>
	<if test="column003 != null and '' != column003">
		ph.column003 = #{column003, jdbcType=VARCHAR} AND
	</if>
	<if test="column004 != null and '' != column004">
		ph.column004 = #{column004, jdbcType=VARCHAR} AND
	</if>
	<if test="column005 != null and '' != column005">
		ph.column005 = #{column005, jdbcType=VARCHAR} AND
	</if>
	<if test="column006 != null and '' != column006">
		ph.column006 = #{column006, jdbcType=VARCHAR} AND
	</if>
	<if test="column007 != null and '' != column007">
		ph.column007 = #{column007, jdbcType=VARCHAR} AND
	</if>
	<if test="column008 != null and '' != column008">
		ph.column008 = #{column008, jdbcType=VARCHAR} AND
	</if>
	<if test="column009 != null">
		ph.column009 = #{column009, jdbcType=TIMESTAMP} AND
	</if>
	<if test="column010 != null and '' != column010">
		ph.column010 = #{column010, jdbcType=VARCHAR} AND
	</if>
	<if test="column011 != null and '' != column011">
		ph.column011 = #{column011, jdbcType=VARCHAR} AND
	</if>
	<if test="column012 != null and '' != column012">
		ph.column012 = #{column012, jdbcType=VARCHAR} AND
	</if>
	<if test="columno12_readonly != null">
		ph.columno12_readonly = #{columno12_readonly, jdbcType=INTEGER} AND
	</if>
	<if test="column013 != null and '' != column013">
		ph.column013 = #{column013, jdbcType=VARCHAR} AND
	</if>
	<if test="customerProjectName != null and '' != customerProjectName">
		ph.customerProjectName = #{customerProjectName, jdbcType=VARCHAR} AND
	</if>
	<if test="salesType != null and '' != salesType">
		ph.salesType = #{salesType, jdbcType=VARCHAR} AND
	</if>
	<if test="compId != null">
		ph.compId = #{compId, jdbcType=INTEGER} AND
	</if>
	<if test="createTime != null">
		ph.createTime = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="createBy != null and '' != createBy">
		ph.createBy = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		ph.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		ph.updateBy = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="effectiveFrom != null">
		<![CDATA[ ph.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
	</if>
	<if test="effectiveTo != null">
		<![CDATA[ (ph.effectiveTo is null or ph.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
	</if>
	<if test="disabled != null">
		ph.disabled = #{disabled, jdbcType=BIT} AND
	</if>
	<if test="projectStartTime != null">
		ph.projectStartTime = #{projectStartTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="projectRefreshTime != null">
		ph.projectRefreshTime = #{projectRefreshTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="projectCloseTime != null">
		ph.projectCloseTime = #{projectCloseTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="customInfo != null">
		ph.customInfo = #{customInfo, jdbcType=JSON} AND
	</if>
	<if test="customConfig != null">
		ph.customConfig = #{customConfig, jdbcType=JSON} AND
	</if>
	<if test="column014 != null and '' != column014">
		ph.column014 = #{column014, jdbcType=LONGVARCHAR} AND
	</if>
	<if test="projectTypes != null">
		FIND_IN_SET(ph.`projectType`, #{projectTypes}) AND 
	</if>
	<if test="officeCodes != null">
		FIND_IN_SET(ph.`column001`, #{officeCodes}) AND 
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:598 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.projectId != null">
			ph.projectId = #{model.projectId, jdbcType=INTEGER} AND
		</if>
		<if test="model.projectType != null and '' != model.projectType">
			ph.projectType = #{model.projectType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectCode != null and '' != model.projectCode">
			ph.projectCode like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.projectName != null and '' != model.projectName">
			(ph.projectName like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%") OR
			 ph.customInfo -> '$.projectAlias' like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%")
			) AND
		</if>
		<if test="model.projectState != null and '' != model.projectState">
			ph.projectState = #{model.projectState, jdbcType=VARCHAR} AND
		</if>
		<if test="model.isback != null and '' != model.isback">
			ph.isback = #{model.isback, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column001 != null and '' != model.column001">
			ph.column001 = #{model.column001, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column002 != null and '' != model.column002">
			ph.column002 = #{model.column002, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column003 != null and '' != model.column003">
			ph.column003 like concat("%", #{model.column003, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column004 != null and '' != model.column004">
			ph.column004 like concat("%", #{model.column004, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column005 != null and '' != model.column005">
			ph.column005 like concat("%", #{model.column005, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column006 != null and '' != model.column006">
			ph.column006 like concat("%", #{model.column006, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column007 != null and '' != model.column007">
			ph.column007 like concat("%", #{model.column007, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column008 != null and '' != model.column008">
			ph.column008 = #{model.column008, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column009 != null">
			ph.column009 = #{model.column009, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.column010 != null and '' != model.column010">
			ph.column010 = #{model.column010, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column011 != null and '' != model.column011">
			ph.column011 = #{model.column011, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column012 != null and '' != model.column012">
			ph.column012 = #{model.column012, jdbcType=VARCHAR} AND
		</if>
		<if test="model.columno12_readonly != null">
			ph.columno12_readonly = #{model.columno12_readonly, jdbcType=INTEGER} AND
		</if>
		<if test="model.column013 != null and '' != model.column013">
			ph.column013 like concat("%", #{model.column013, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.customerProjectName != null and '' != model.customerProjectName">
			ph.customerProjectName = #{model.customerProjectName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.salesType != null and '' != model.salesType">
			ph.salesType = #{model.salesType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.compId != null">
			ph.compId = #{model.compId, jdbcType=INTEGER} AND
		</if>
		<if test="model.createTime != null">
			ph.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			ph.createBy = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			ph.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			ph.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.effectiveFrom != null">
			<![CDATA[ ph.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
		</if>
		<if test="model.effectiveTo != null">
			<![CDATA[ (ph.effectiveTo is null or ph.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
		</if>
		<if test="model.disabled != null">
			ph.disabled = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.projectStartTime != null">
			ph.projectStartTime = #{model.projectStartTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.projectRefreshTime != null">
			ph.projectRefreshTime = #{model.projectRefreshTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.projectCloseTime != null">
			ph.projectCloseTime = #{model.projectCloseTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.customInfo != null">
			<!-- ph.customInfo = #{model.customInfo, jdbcType=JSON} AND -->
			<foreach collection="model.customInfo" item="value" index="key">
		    	ph.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.customConfig != null">
			<!-- ph.customConfig = #{model.customConfig, jdbcType=JSON} AND -->
			<foreach collection="model.customConfig" item="value" index="key">
			   ph.customConfig -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.column014 != null and '' != model.column014">
			ph.column014 = #{model.column014, jdbcType=LONGVARCHAR} AND
		</if>
		<if test="model.smsPredBidStartDate != null">
			ph.customInfo -> '$.smsPredBidDate' >= #{model.smsPredBidStartDate, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.smsPredBidEndDate != null">
			<![CDATA[ ph.customInfo -> '$.smsPredBidDate' <= #{model.smsPredBidEndDate, jdbcType=TIMESTAMP} AND ]]>
		</if>
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:725 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:733 | fragment=sql_fuzzy_search
<if test="fuzzySearch == true and fuzzy != ''">
		(
			ph.projectType like CONCAT("%", #{fuzzy}, "%") or
			ph.projectCode like CONCAT("%", #{fuzzy}, "%") or
			ph.projectName like CONCAT("%", #{fuzzy}, "%") or
			ph.projectState like CONCAT("%", #{fuzzy}, "%") or
			ph.isback like CONCAT("%", #{fuzzy}, "%") or
			ph.column001 like CONCAT("%", #{fuzzy}, "%") or
			ph.column002 like CONCAT("%", #{fuzzy}, "%") or
			ph.column003 like CONCAT("%", #{fuzzy}, "%") or
			ph.column004 like CONCAT("%", #{fuzzy}, "%") or
			ph.column005 like CONCAT("%", #{fuzzy}, "%") or
			ph.column006 like CONCAT("%", #{fuzzy}, "%") or
			ph.column007 like CONCAT("%", #{fuzzy}, "%") or
			ph.column008 like CONCAT("%", #{fuzzy}, "%") or
			ph.column010 like CONCAT("%", #{fuzzy}, "%") or
			ph.column011 like CONCAT("%", #{fuzzy}, "%") or
			ph.column012 like CONCAT("%", #{fuzzy}, "%") or
			ph.column013 like CONCAT("%", #{fuzzy}, "%") or
			ph.customerProjectName like CONCAT("%", #{fuzzy}, "%") or
			ph.salesType like CONCAT("%", #{fuzzy}, "%") or
			ph.customInfo like CONCAT("%", #{fuzzy}, "%") or
			ph.customConfig like CONCAT("%", #{fuzzy}, "%") or
			ph.column014 like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:762 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
			<include refid="sql_fuzzy_search" />
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:770 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by ph.projectId desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:930 | fragment=uncreateProjectListSql
select distinct null as 
	    `projectId`, pp.projectCode as `projectCode`, null as `rank`, 
	    ifnull(pp.projectName, p.projectName) as `projectName`, 
	    <!-- `fd`.`basicDataName` as `projectStateName`, `fd`.`basicDataId` as `projectState`,  -->
	    ifnull(`p`.`contractNo`, pp.projectCode) as `contractNo`, 
	    ifnull(`p`.`orderExecNumber`, pp.orderExecNumber) as `smsOrderExecNumber`, 
	    ifnull(c1.id, c2.id) as compId, `pp`.`officeCode` as `officeCode`, 
	    ifnull(c1.compAbbr, c2.compAbbr) as compAbbr, ifnull(c1.compName, c2.compName) as compName,
	    `d`.`departmentName` as `officeName`, `pp`.`salesManCode` as `salesManCode`, 
	    concat(`pp`.`salesManCode`, "-", `pp`.`salesManName`) as `salesManName`, null as `projectStartTime`, 
	    null as projectCreateTime, `pp`.`systemName` as `systemName`, 
	    null as `serviceManager`, null as `serviceManagerName`, null as `projectManager`, 
	    null as `projectManagerName`, null as shipmentStateName, null as shipmentState, 
	    null as planStateName, null as projectPlanState, null as executionStateName, 
	    null as executionState, null as closeProcessStateName, null as closeProcessState, 
	    null as projectRefreshTime, null as projectCloseTime, null ssfs, null ssfsName, 
	    null partnerChannel, pp.projectType, pp.predBidDate as smsPredBidDate 
	from
	    `pm_project_property_af_from_sms` `pp` 
	    left join `pm_order_data_from_sap` `p` 
	        on `p`.`orderExecNumber` = `pp`.`orderExecNumber` 
	    left join `view_distinct_contract` `v` 
	        on `v`.`id` = `p`.`id` 
	    left join `fnd_department` `d` 
	        on `pp`.`officeCode` = `d`.`departmentNum` 
	    <!-- left join `fnd_basic_data` `fd` 
	        on `fd`.`basicDataId` = '10' 
	        and `fd`.`dataTypeCode` = concat(pp.projectType, "_projectState")  -->
  	<!-- <![CDATA[
	        and `fd`.`effectiveFrom` < now() 
	        and (
	            `fd`.`effectiveTo` > now() 
	            or isnull(`fd`.`effectiveTo`)
	        ) 
    ]]> -->
	    left join t_company c1 
	        on c1.compCode = p.compCode 
	    left join t_company c2 
	        on c2.`compCode` = pp.corporationCode 
	where
	<trim prefixOverrides="and | or" suffixOverrides="and | or">
		pp.`projectType` = 'afss' 
		<if test="model != null ">
			<if test="model.projectType != null and model.projectType !=''"> 
				and pp.projectType = #{model.projectType} 
			</if>
			<if test="model.projectCode != null and '' != model.projectCode">
				AND pp.projectCode like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.projectName != null and '' != model.projectName">
				AND IFNULL(pp.projectName, p.projectName) like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.contractNo != null and '' != model.contractNo">
				AND p.contractNo like concat("%", #{model.contractNo, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.smsOrderExecNumber != null and '' != model.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.smsOrderExecNumber != null and '' != model.customInfo.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.customInfo.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.salesManName != null and '' != model.salesManName">
				AND pp.salesManName like concat("%", #{model.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.salesManName != null and '' != model.customInfo.salesManName">
				AND pp.salesManName like concat("%", #{model.customInfo.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column001 != null and '' != model.column001">
				AND pp.officeCode = #{model.column001, jdbcType=VARCHAR}
			</if>
			<if test="model.column004 != null and '' != model.column004">
				AND pp.marketName like concat("%", #{model.column004, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column005 != null and '' != model.column005">
				AND pp.systemName like concat("%", #{model.column005, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column006 != null and '' != model.column006">
				AND pp.expendName like concat("%", #{model.column006, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column007 != null and '' != model.column007">
				AND pp.industryName like concat("%", #{model.column007, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.compId != null and '' != model.compId">
				AND #{model.compId, jdbcType=INTEGER} = IFNULL(c1.id, c2.id)
			</if>
			<if test="model.projectTypes != null">
				AND FIND_IN_SET(pp.`projectType`, #{model.projectTypes})
			</if>
			<if test="model.officeCodes != null">
				AND FIND_IN_SET(pp.`officeCode`, #{model.officeCodes})
			</if>
			<if test="model.smsPredBidStartDate != null">
				AND pp.predBidDate >= #{model.smsPredBidStartDate, jdbcType=TIMESTAMP}
			</if>
			<if test="model.smsPredBidEndDate != null">
				<![CDATA[ AND pp.predBidDate <= #{model.smsPredBidEndDate, jdbcType=TIMESTAMP} ]]>
			</if>
		</if>
		<if test="fuzzySearch == true and fuzzy != ''">
			AND (
				pp.projectCode like CONCAT("%", #{fuzzy}, "%") or
				pp.projectName like CONCAT("%", #{fuzzy}, "%") or
				p.contractNo like CONCAT("%", #{fuzzy}, "%")
			)
		</if>
	    and not exists 
	    (select 
	        1 
	    from
	        `pm_project_contract` `t1` 
	        join `pm_project_group_relationship` `t2` 
	            on `t1`.`projectGroupCode` = `t2`.`projectGroupCode` 
	        join `pm_project` `t3` 
	            on (
	                <!-- `t2`.`projectCode` = `t3`.`projectCode` 
	                or  -->
	                `t3`.`projectCode` = replace(`t2`.`projectCode`, "-his", "")
	            ) 
	<![CDATA[
	            and `t3`.`effectiveFrom` <= now() 
	            and (
	                `t3`.`effectiveTo` > now() 
	                or isnull(`t3`.`effectiveTo`)
	            ) 
    ]]>
	    where t3.`projectType` = pp.`projectType` 
	        and (
	            (
	                <!-- `v`.`contractNo` = `t1`.`contractNo` 
	                and v.contractNo is not null -->
	                <!-- 执行单已下单，但是没有签合同的项目 -->
	                `t1`.`contractNo` = IFNULL(`v`.`contractNo`, pp.`projectCode`)
	            )
	        )) 
	    and (
		    v.`contractNo` is not null 
		    <!-- 执行单已下单，但是没有签合同的项目 -->
		    OR NOT EXISTS 
            (SELECT 
                * 
            FROM
                `pm_project_property_af_from_sms` `t1` 
                INNER JOIN `pm_order_data_from_sap` `t2` 
                    ON `t1`.`orderExecNumber` = `t2`.`orderExecNumber` 
            WHERE t1.`projectCode` = pp.`projectCode`)
		)
    </trim>
	union
	select distinct null as 
	    `projectId`, pp.projectCode as `projectCode`, null as `rank`,
	    ifnull(pp.projectName, p.projectName)  as `projectName`, 
	    <!-- `fd`.`basicDataName` as `projectStateName`, `fd`.`basicDataId` as `projectState`,  -->
	    ifnull(`p`.`contractNo`, pp.projectCode) as `contractNo`, 
	    ifnull(`p`.`orderExecNumber`, pp.orderExecNumber) as `smsOrderExecNumber`, 
	    ifnull(c1.id, c2.id) as compId, `pp`.`officeCode` as `officeCode`, 
	    ifnull(c1.compAbbr, c2.compAbbr) as compAbbr, ifnull(c1.compName, c2.compName) as compName,
	    `d`.`departmentName` as `officeName`, `pp`.`salesManCode` as `salesManCode`, 
	    concat(`pp`.`salesManCode`, "-", `pp`.`salesManName`) as `salesManName`, null as `projectStartTime`, 
	    null as projectCreateTime, `pp`.`systemName` as `systemName`, 
	    null as `serviceManager`, null as `serviceManagerName`, null as `projectManager`, 
	    null as `projectManagerName`, null as shipmentStateName, null as shipmentState, 
	    null as planStateName, null as projectPlanState, null as executionStateName, 
	    null as executionState, null as closeProcessStateName, null as closeProcessState, 
	    null as projectRefreshTime, null as projectCloseTime, null ssfs, null ssfsName, 
	    null partnerChannel, pp.projectType, pp.predBidDate as smsPredBidDate 
	from
	    `pm_project_property_af_from_sms` `pp` 
	    left join `pm_order_data_from_sap` `p` 
	        on `p`.`orderExecNumber` = `pp`.`orderExecNumber` 
	    <!-- left join `view_distinct_contract` `v` 
	        on `v`.`id` = `p`.`id`  -->
	    left join `fnd_department` `d` 
	        on `pp`.`officeCode` = `d`.`departmentNum` 
	    <!-- left join `fnd_basic_data` `fd` 
	        on `fd`.`basicDataId` = '10' 
	        and `fd`.`dataTypeCode` = concat(pp.projectType, "_projectState")  -->
   <!-- <![CDATA[
	        and `fd`.`effectiveFrom` < now() 
	        and (
	            `fd`.`effectiveTo` > now() 
	            or isnull(`fd`.`effectiveTo`)
	        ) 
    ]]> -->
	    left join t_company c1 
	        on c1.compCode = p.compCode 
	    left join t_company c2 
	        on c2.`compCode` = pp.corporationCode 
	where 
	<trim prefixOverrides="and |or" suffixOverrides="and |or">
		pp.`projectType` = 'afxx' 
		<if test="model != null ">
			<if test="model.projectType != null and model.projectType !=''"> 
				and pp.projectType = #{model.projectType} 
			</if>
			<if test="model.projectCode != null and '' != model.projectCode">
				AND pp.projectCode like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.projectName != null and '' != model.projectName">
				AND IFNULL(pp.projectName, p.projectName) like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.contractNo != null and '' != model.contractNo">
				AND p.contractNo like concat("%", #{model.contractNo, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.smsOrderExecNumber != null and '' != model.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.smsOrderExecNumber != null and '' != model.customInfo.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.customInfo.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.salesManName != null and '' != model.salesManName">
				AND pp.salesManName like concat("%", #{model.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.salesManName != null and '' != model.customInfo.salesManName">
				AND pp.salesManName like concat("%", #{model.customInfo.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column001 != null and '' != model.column001">
				AND pp.officeCode = #{model.column001, jdbcType=VARCHAR}
			</if>
			<if test="model.column004 != null and '' != model.column004">
				AND pp.marketName like concat("%", #{model.column004, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column005 != null and '' != model.column005">
				AND pp.systemName like concat("%", #{model.column005, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column006 != null and '' != model.column006">
				AND pp.expendName like concat("%", #{model.column006, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column007 != null and '' != model.column007">
				AND pp.industryName like concat("%", #{model.column007, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.compId != null and '' != model.compId">
				AND #{model.compId, jdbcType=INTEGER} = IFNULL(c1.id, c2.id)
			</if>
			<if test="model.projectTypes != null">
				AND FIND_IN_SET(pp.`projectType`, #{model.projectTypes})
			</if>
			<if test="model.officeCodes != null">
				AND FIND_IN_SET(pp.`officeCode`, #{model.officeCodes})
			</if>
			<if test="model.smsPredBidStartDate != null">
				AND pp.predBidDate >= #{model.smsPredBidStartDate, jdbcType=TIMESTAMP}
			</if>
			<if test="model.smsPredBidEndDate != null">
				<![CDATA[ AND pp.predBidDate <= #{model.smsPredBidEndDate, jdbcType=TIMESTAMP} ]]>
			</if>
		</if>
		<if test="fuzzySearch == true and fuzzy != ''">
			AND (
				pp.projectCode like CONCAT("%", #{fuzzy}, "%") or
				pp.projectName like CONCAT("%", #{fuzzy}, "%") or
				p.contractNo like CONCAT("%", #{fuzzy}, "%")
			)
		</if>
	    and not exists 
	    (select 
	        1 
	    from
	        `pm_project_contract` `t1` 
	        join `pm_project_group_relationship` `t2` 
	            on `t1`.`projectGroupCode` = `t2`.`projectGroupCode` 
	        join `pm_project` `t3` 
	            on (
	               <!--  `t2`.`projectCode` = `t3`.`projectCode` 
	                or  -->
	                `t3`.`projectCode` = replace(`t2`.`projectCode`, "-his", "")
	            ) 
	<![CDATA[
	            and `t3`.`effectiveFrom` <= now() 
	            and (
	                `t3`.`effectiveTo` > now() 
	                or isnull(`t3`.`effectiveTo`)
	            ) 
    ]]>
	    where t3.`projectType` = pp.`projectType` 
	        <!-- and pp.`orderExecNumber` = t1.`contractNo` -->
	         and pp.`projectCode` = t1.`contractNo`
	        ) 
	</trim>
	union all
	SELECT  
	    ph.`projectId`, `projectCode`, NULL AS `rank`, `projectName`, 
	    <!-- ph.customInfo ->> "$.projectStateName" AS `projectStateName`, `fd`.`basicDataId` AS `projectState`,  -->
	    ph.customInfo ->> "$.contractNo" AS `contractNo`, 
	    ph.customInfo ->> "$.smsOrderExecNumber" AS `smsOrderExecNumber`, 
	    ph.compId, ph.`column001` AS `officeCode`, 
	    c1.compAbbr as compAbbr, c1.compName as compName,
	    ph.customInfo ->> "$.officeName" AS `officeName`, ph.customInfo ->> "$.salesManCode" AS `salesManCode`, 
	    ph.customInfo ->> "$.salesManName" AS `salesManName`, ph.`projectStartTime`, ph.createTime AS projectCreateTime, 
	    ph.`column005` AS `systemName`, 
	    ph.customInfo ->> "$.serviceManagerCode" AS `serviceManager`, ph.customInfo ->> "$.serviceManagerforjson" AS `serviceManagerName`, 
	    ph.customInfo ->> "$.programManagerCode" AS `projectManager`, ph.customInfo ->> "$.programManagerCodeforjson" AS `projectManagerName`, 
	    NULL AS shipmentStateName, NULL AS shipmentState, NULL AS planStateName, NULL AS projectPlanState, 
	    NULL AS executionStateName, NULL AS executionState, NULL AS closeProcessStateName, 
	    NULL AS closeProcessState, NULL AS projectRefreshTime, NULL AS projectCloseTime, 
	    NULL ssfs, NULL ssfsName, NULL partnerChannel, ph.projectType, 
	    ph.customInfo ->> "$.smsPredBidDate" AS smsPredBidDate 
	FROM pm_project ph
	   <!--  LEFT JOIN `fnd_basic_data` `fd` 
		ON `fd`.`basicDataId` = '10' 
		AND `fd`.`dataTypeCode` = CONCAT(ph.projectType, "_projectState") -->
		left join t_company c1 
	        on c1.id = ph.compId
	<include refid="sql_model_where"></include>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:34 | fragment=Base_Column_List
taskId, projectId, projectType, contractNo, taskTypeCode, taskTypeId, taskName, eventPlanHappenDate, 
    eventPlanHappenDateENG, planStartTime, planEndTime, actualStartTime, eventActualFinishDate, 
    priority, progress, progressDesc, status, parentId, createTime, createBy, updateTime, 
    updateBy, effectiveFrom, effectiveTo, visibleFlag, deliverFileIds, customInfo,
    remark

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:386 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="taskId != null">
				t.taskId = #{taskId, jdbcType=INTEGER} AND
			</if>
			<if test="projectId != null">
				t.projectId = #{projectId, jdbcType=INTEGER} AND
			</if>
			<if test="projectType != null and '' != projectType">
				t.projectType = #{projectType, jdbcType=VARCHAR} AND
			</if>
			<if test="contractNo != null and '' != contractNo">
				t.contractNo = #{contractNo, jdbcType=VARCHAR} AND
			</if>
			<if test="taskTypeCode != null and '' != taskTypeCode">
				t.taskTypeCode = #{taskTypeCode, jdbcType=VARCHAR} AND
			</if>
			<if test="taskTypeId != null and '' != taskTypeId">
				t.taskTypeId = #{taskTypeId, jdbcType=VARCHAR} AND
			</if>
			<if test="taskName != null and '' != taskName">
				t.taskName = #{taskName, jdbcType=VARCHAR} AND
			</if>
			<if test="eventPlanHappenDate != null">
				t.eventPlanHappenDate = #{eventPlanHappenDate, jdbcType=TIMESTAMP} AND
			</if>
			<if test="eventPlanHappenDateENG != null">
				t.eventPlanHappenDateENG = #{eventPlanHappenDateENG, jdbcType=TIMESTAMP} AND
			</if>
			<if test="planStartTime != null">
				t.planStartTime = #{planStartTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="planEndTime != null">
				t.planEndTime = #{planEndTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="actualStartTime != null">
				t.actualStartTime = #{actualStartTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="eventActualFinishDate != null">
				t.eventActualFinishDate = #{eventActualFinishDate, jdbcType=TIMESTAMP} AND
			</if>
			<if test="priority != null and '' != priority">
				t.priority = #{priority, jdbcType=VARCHAR} AND
			</if>
			<if test="progress != null">
				t.progress = #{progress, jdbcType=INTEGER} AND
			</if>
			<if test="progressDesc != null and '' != progressDesc">
				t.progressDesc = #{progressDesc, jdbcType=VARCHAR} AND
			</if>
			<if test="status != null and '' != status">
				t.status = #{status, jdbcType=VARCHAR} AND
			</if>
			<if test="parentId != null">
				t.parentId = #{parentId, jdbcType=INTEGER} AND
			</if>
			<if test="createTime != null">
				t.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="createBy != null and '' != createBy">
				t.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				t.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				t.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ t.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
			<if test="effectiveTo != null">
				<![CDATA[ (t.effectiveTo is null or t.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="visibleFlag != null and '' != visibleFlag">
				t.visibleFlag = #{visibleFlag, jdbcType=VARCHAR} AND
			</if>
			<if test="deliverFileIds != null and '' != deliverFileIds">
				t.deliverFileIds = #{deliverFileIds, jdbcType=VARCHAR} AND
			</if>
			<if test="customInfo != null">
				t.customInfo = #{customInfo, jdbcType=JSON} AND
			</if>
			<if test="remark != null and '' != remark">
				t.remark = #{remark, jdbcType=LONGVARCHAR} AND
			</if>
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:476 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.taskId != null">
					t.taskId = #{model.taskId, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectId != null">
					t.projectId = #{model.projectId, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectType != null and '' != model.projectType">
					t.projectType = #{model.projectType, jdbcType=VARCHAR} AND
				</if>
				<if test="model.contractNo != null and '' != model.contractNo">
					t.contractNo = #{model.contractNo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskTypeCode != null and '' != model.taskTypeCode">
					t.taskTypeCode = #{model.taskTypeCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskTypeId != null and '' != model.taskTypeId">
					t.taskTypeId = #{model.taskTypeId, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskName != null and '' != model.taskName">
					t.taskName = #{model.taskName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.eventPlanHappenDate != null">
					t.eventPlanHappenDate = #{model.eventPlanHappenDate, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.eventPlanHappenDateENG != null">
					t.eventPlanHappenDateENG = #{model.eventPlanHappenDateENG, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.planStartTime != null">
					t.planStartTime = #{model.planStartTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.planEndTime != null">
					t.planEndTime = #{model.planEndTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.actualStartTime != null">
					t.actualStartTime = #{model.actualStartTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.eventActualFinishDate != null">
					t.eventActualFinishDate = #{model.eventActualFinishDate, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.priority != null and '' != model.priority">
					t.priority = #{model.priority, jdbcType=VARCHAR} AND
				</if>
				<if test="model.progress != null">
					t.progress = #{model.progress, jdbcType=INTEGER} AND
				</if>
				<if test="model.progressDesc != null and '' != model.progressDesc">
					t.progressDesc = #{model.progressDesc, jdbcType=VARCHAR} AND
				</if>
				<if test="model.status != null and '' != model.status">
					t.status = #{model.status, jdbcType=VARCHAR} AND
				</if>
				<if test="model.parentId != null">
					t.parentId = #{model.parentId, jdbcType=INTEGER} AND
				</if>
				<if test="model.createTime != null">
					t.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					t.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					t.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					t.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ t.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
				<if test="model.effectiveTo != null">
					<![CDATA[ (t.effectiveTo is null or t.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.visibleFlag != null and '' != model.visibleFlag">
					t.visibleFlag = #{model.visibleFlag, jdbcType=VARCHAR} AND
				</if>
				<if test="model.deliverFileIds != null and '' != model.deliverFileIds">
					t.deliverFileIds = #{model.deliverFileIds, jdbcType=VARCHAR} AND
				</if>
				<if test="model.customInfo != null">
					t.customInfo = #{model.customInfo, jdbcType=JSON} AND
				</if>
				<if test="model.remark != null and '' != model.remark">
					t.remark = #{model.remark, jdbcType=LONGVARCHAR} AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					t.projectType like CONCAT("%", #{fuzzy}, "%") or
					t.contractNo like CONCAT("%", #{fuzzy}, "%") or
					t.taskTypeCode like CONCAT("%", #{fuzzy}, "%") or
					t.taskTypeId like CONCAT("%", #{fuzzy}, "%") or
					t.taskName like CONCAT("%", #{fuzzy}, "%") or
					t.priority like CONCAT("%", #{fuzzy}, "%") or
					t.progressDesc like CONCAT("%", #{fuzzy}, "%") or
					t.status like CONCAT("%", #{fuzzy}, "%") or
					t.createBy like CONCAT("%", #{fuzzy}, "%") or
					t.updateBy like CONCAT("%", #{fuzzy}, "%") or
					t.visibleFlag like CONCAT("%", #{fuzzy}, "%") or
					t.deliverFileIds like CONCAT("%", #{fuzzy}, "%") or
					t.remark like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:585 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by taskId desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-maintenance-config.xml:1511 | fragment=selectProjectWarrantyState
SELECT 
        ph.`projectId`, ph.`projectCode`, ph.`projectName`, pg.projectType,
        ph.`column001` as officeCode, fd.`departmentName` as officeName, c.name as companyName, c.abbr as companyAbbr,
        ph.column004 AS marketName, ph.column005 AS systemName, 
	    ph.column006 AS expendName, ph.column007 AS industryName,
	    ph.column013 AS finalCustomerName, ph.compId,
        group_concat(DISTINCT pc.contractNo) as contractNos,
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))) warrantyStatus, 
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
            )
        ) AS warrantyStatusName,
        MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
        MAX(wcs.`warrantyGrade`) warrantyGrade, 
        IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
            )
        ) AS warrantyGradeName,
        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`warrantyGradeStartTime`) AND MAX(wcs.`warrantyGradeEndTime`) >= DATE(NOW()), "true", "false") AS warrantyGradeEnable,
        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
        IFNULL(MAX(wcs.`wafService`), 0) wafService,
        IF(MAX(wcs.`wafService`) = 1, "策略调优服务", NULL) AS  wafServiceName,
        MIN(wcs.`wafServiceStartTime`) wafServiceStartTime,
        MAX(wcs.`wafServiceEndTime`) wafServiceEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`wafServiceStartTime`) AND MAX(wcs.`wafServiceEndTime`) >= DATE(NOW()), "true", "false") AS wafServiceEnable,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.itemDesc, "(", DATE(wcs.`wafServiceStartTime`), "~", DATE(wcs.`wafServiceEndTime`), ")") SEPARATOR "\r\n") AS warrantyServiceDesc
    FROM
        `pm_project_header` ph 
        LEFT JOIN `pm_project_group_relationship` pgr 
            on ph.`projectCode` = pgr.`projectCode` 
        LEFT JOIN `pm_project_group` pg
            on pg.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `pm_project_contract` pc 
            on pc.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `view_warranty_contract_state` wcs 
            on wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
        LEFT JOIN fnd_department fd
            on fd.`departmentNum` = ph.`column001`
        LEFT JOIN fnd_company c force key(primary)
            ON c.id = ph.compId
    WHERE ph.`effectiveTo` is null

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-maintenance-config.xml:1799 | fragment=sqlProjectMaintenanceServiceDeliveryWhere
<isNotEmpty property="serviceTypes">
        <!-- LEFT JOIN (
            <iterate property="serviceTypes" conjunction=" UNION ALL ">
                SELECT #serviceTypes[]# as serviceType
            </iterate>
        ) st
        ON 1 = 1 -->
        LEFT JOIN (SELECT 
                <!-- dataTypeCode, --> basicDataId as serviceType, basicDataAttri1 as deliverId 
	        FROM
	            `fnd_basic_data` 
	        WHERE dataTypeCode = 'projectServiceDeliveryType') st
            ON 1 = 1 <!-- st.dataTypeCode = 'projectServiceDeliveryType'  -->
    </isNotEmpty>
    <isNotEmpty property="hasQuarterDeliveried">
        LEFT JOIN (<include refid="sqlProjectServiceDeliver" />) sd
        ON sd.projectId = wcs.projectId AND sd.serviceType = st.serviceType
    </isNotEmpty>
    <isNotEmpty property="joinProjectMembers">
        LEFT JOIN pm_project_member sm 
            ON wcs.`projectId` = sm.`projectId` 
            AND wcs.`projectType` = sm.`projectType` 
            AND sm.`memberRole` = '20' 
            AND sm.`effectiveTo` IS NULL 
            AND sm.`fromFlag` = 1 
        LEFT JOIN pm_project_member pm 
            ON wcs.`projectId` = pm.`projectId` 
            AND wcs.`projectType` = pm.`projectType` 
            AND pm.`memberRole` = '30' 
            AND pm.`effectiveTo` IS NULL 
            AND pm.`fromFlag` = 1 
        LEFT JOIN pm_project_member pm2 
            ON wcs.`projectId` = pm2.`projectId` 
            AND wcs.`projectType` = pm2.`projectType` 
            AND pm2.`memberRole` = '30' 
            AND pm2.`effectiveTo` IS NULL 
            AND pm2.`fromFlag` = 2 
        LEFT JOIN `pm_project_member` s 
            ON s.`projectId` = wcs.`projectId` 
            AND s.`projectType` = wcs.`projectType` 
            AND s.`memberRole` = "10" 
            AND s.`effectiveTo` IS NULL 
    </isNotEmpty>
    <dynamic prepend="where">
        <isNotEmpty prepend="AND" property="serviceTypes">
            <iterate property="serviceTypes" open="(" close=")" conjunction=" or ">
                <isNotEqual property="serviceQuarter" compareValue="true">
	                <isNotEmpty property="serviceDate" >
	                (
	                    #serviceDate# between wcs.`$serviceTypes[]$StartTime` 
	                    and wcs.`$serviceTypes[]$EndTime` 
	                    and st.serviceType = #serviceTypes[]#
	                )
	                </isNotEmpty>
                </isNotEqual>
                <isEqual property="serviceQuarter" compareValue="true">
                    <isNotEmpty property="serviceDate" >
		                <!-- <![CDATA[
		                (
		                    #serviceQuarter# >= CONCAT(YEAR(wcs.`$serviceTypes[]$StartTime`), QUARTER(wcs.`$serviceTypes[]$StartTime`))
		                    AND #serviceQuarter# <= CONCAT(YEAR(wcs.`$serviceTypes[]$EndTime`), QUARTER(wcs.`$serviceTypes[]$EndTime`))
		                    AND st.serviceType = #serviceTypes[]#
		                )
		                ]]> -->
		                <![CDATA[
                        (
                            CONCAT(YEAR(#serviceDate#), QUARTER(#serviceDate#)) >= CONCAT(YEAR(wcs.`$serviceTypes[]$StartTime`), QUARTER(wcs.`$serviceTypes[]$StartTime`))
                            AND CONCAT(YEAR(#serviceDate#), QUARTER(#serviceDate#)) <= CONCAT(YEAR(wcs.`$serviceTypes[]$EndTime`), QUARTER(wcs.`$serviceTypes[]$EndTime`))
                            AND st.serviceType = #serviceTypes[]#
                        )
                        ]]>
	                </isNotEmpty>
                </isEqual>
            </iterate>
            <isNotEmpty prepend="AND" property="serviceType"> st.serviceType = #serviceType# </isNotEmpty>
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="warrantyGradeDate">
            DATE(#warrantyGradeDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="wafServiceDate">
            DATE(#wafServiceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`
        </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="projectId"> wcs.projectId = #projectId# </isNotEmpty>
        <isNotEmpty prepend="AND" property="projectCode"> wcs.projectCode like concat("%", #projectCode#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="projectName"> wcs.projectName like concat("%", #projectName#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="contractNo"> wcs.contractNos like concat("%", #contractNo#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="officeCode"> wcs.officeCode = #officeCode# </isNotEmpty>
        <isNotEmpty prepend="AND" property="compId"> wcs.compId = #compId# </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="serviceManager"> sm.memberCode = #serviceManager# </isNotEmpty>
        <isNotEmpty prepend="AND" property="programManager"> (pm.memberCode = #programManager# or pm2.memberCode = #programManager#) </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="userPower" open="(" close=")">
            (sm.memberCode = #userPower# 
                or pm.memberCode = #userPower# 
                or pm2.memberCode = #userPower#
                <isEqual prepend="or" property="checkServicePower" compareValue="true">
                    exists (
                       select 
                            1 
                        from
                            serviceManagerPower_$userPower$ ep 
                        where ep.serviceCode = #userPower#
                            AND ep.programCode = wcs.`createBy`
                    )
                </isEqual>
            )
            <isNotEmpty prepend="OR" property="areaPower"> FIND_IN_SET(wcs.officeCode, #areaPower#)</isNotEmpty>
        </isNotEmpty>
        <isEmpty property="userPower">
            <isNotEmpty prepend="AND" property="areaPower"> FIND_IN_SET(wcs.officeCode, #areaPower#)</isNotEmpty>
        </isEmpty>
        
        <isNotEmpty prepend="AND" property="queryWarrantyStatus"> wcs.warrantyStatus = #queryWarrantyStatus# </isNotEmpty>
        <isNotEmpty prepend="AND" property="queryWarrantyGrade"> wcs.warrantyGrade = #queryWarrantyGrade# </isNotEmpty>
        <isNotEmpty prepend="AND" property="queryWafService"> wcs.wafService = #queryWafService# </isNotEmpty>
        <isNotEmpty prepend="AND" property="hasQuarterDeliveried"> #hasQuarterDeliveried# = IFNULL(sd.hasQuarterDeliveried, FALSE) </isNotEmpty>
        
        <!-- <isEmpty  prepend="AND" property="serviceTypes">
            <isEmpty property="serviceType" >
                <isNotEmpty prepend="AND" property="serviceDate">
                (
                    (DATE(#serviceDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`)
                    or
                    (DATE(#serviceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`)
                </isNotEmpty>
            </isEmpty>
            <isEqual property="serviceType" compareValue="warrantyGrade">
                <isNotEmpty prepend="AND" property="serviceDate">
                    DATE(#serviceDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`
                </isNotEmpty>
            </isEqual>
            <isEqual property="serviceType" compareValue="wafService">
                <isNotEmpty prepend="AND" property="serviceDate">
                    DATE(#serviceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`
                </isNotEmpty>
            </isEqual>
        </isEmpty> -->
    </dynamic>
    <!-- <isNotEmpty property="joinProjectMembers">
        group by wcs.projectType, wcs.projectId, wcs.serviceType
    </isNotEmpty> -->

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-prob-config.xml:32 | fragment=sqlWhereIterateSoftVersionList
<!-- <isNotEmpty property="prob.softVersionParserList">
           <isNotEmpty prepend="AND" property="prob.affectedType"> affectedType in (#prob.affectedType#, 0) </isNotEmpty>
           <iterate prepend="AND" property="prob.softVersionParserList" open="(" close=")" conjunction="OR" >
               #prob.softVersionParserList[].mark# between 
               <iterate property="prob.softVersionParserList[].markAllPartsSet">REPLACE(</iterate> 
               markStart
               <iterate property="prob.softVersionParserList[].markAllPartsSet">
                   , #prob.softVersionParserList[].markAllPartsSet[].key#, #prob.softVersionParserList[].markAllPartsSet[].value#)
               </iterate>
               and 
               <iterate property="prob.softVersionParserList[].markAllPartsSet">REPLACE(</iterate>
               markEnd
               <iterate property="prob.softVersionParserList[].markAllPartsSet">
                   , #prob.softVersionParserList[].markAllPartsSet[].key#, #prob.softVersionParserList[].markAllPartsSet[].value#)
               </iterate>
           </iterate>
       </isNotEmpty> -->
       <isPropertyAvailable property="prob">
	       <isNotEmpty property="prob.softVersionParserList">
	           <isNotEmpty prepend="AND" property="prob.affectedType"> affectedType in (#prob.affectedType#, 0) </isNotEmpty>
	           <iterate prepend="AND" property="prob.softVersionParserList" open="(" close=")" conjunction="OR" >
	               '$prob.softVersionParserList[].mark$' between 
	               <iterate property="prob.softVersionParserList[].markAllPartsSet">REPLACE(</iterate> 
	               markStart
	               <iterate property="prob.softVersionParserList[].markAllPartsSet">
	                   , '$prob.softVersionParserList[].markAllPartsSet[].key$', '$prob.softVersionParserList[].markAllPartsSet[].value$')
	               </iterate>
	               and 
	               <iterate property="prob.softVersionParserList[].markAllPartsSet">REPLACE(</iterate>
	               markEnd
	               <iterate property="prob.softVersionParserList[].markAllPartsSet">
	                   , '$prob.softVersionParserList[].markAllPartsSet[].key$', '$prob.softVersionParserList[].markAllPartsSet[].value$')
	               </iterate>
	           </iterate>
	       </isNotEmpty>
       </isPropertyAvailable>
       <isNotPropertyAvailable property="prob">
           <isNotEmpty property="softVersionParserList">
	           <isNotEmpty prepend="AND" property="affectedType"> affectedType in (#affectedType#, 0) </isNotEmpty>
	           <iterate prepend="AND" property="softVersionParserList" open="(" close=")" conjunction="OR" >
	               '$softVersionParserList[].mark$' between 
	               <iterate property="softVersionParserList[].markAllPartsSet">REPLACE(</iterate> 
	               markStart
	               <iterate property="softVersionParserList[].markAllPartsSet">
	                   , '$softVersionParserList[].markAllPartsSet[].key$', '$softVersionParserList[].markAllPartsSet[].value$')
	               </iterate>
	               and 
	               <iterate property="softVersionParserList[].markAllPartsSet">REPLACE(</iterate>
	               markEnd
	               <iterate property="softVersionParserList[].markAllPartsSet">
	                   , '$softVersionParserList[].markAllPartsSet[].key$', '$softVersionParserList[].markAllPartsSet[].value$')
	               </iterate>
	           </iterate>
	       </isNotEmpty>
       </isNotPropertyAvailable>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-prob-config.xml:659 | fragment=sqlFromProbAffectedProjectSoftVersion
<![CDATA[
        FROM prob_softwares sw
                INNER JOIN pm_project_soft_version sv
                ON sv.datastate = sw.datastate
                    AND sv.conpType <=> sw.entryType
                    AND sv.conpSeries <=> sw.entrySeries
                    AND sv.conpMark BETWEEN sw.markStart AND sw.markEnd
                INNER JOIN prob_product pp ON pp.probId = sw.probId AND pp.itemCode = sv.itemCode AND pp.status = 1
                INNER JOIN prob_main pm ON pm.id = sw.probId 
                INNER JOIN pm_project_header ph
                ON sv.projectId = ph.projectId
                    AND ((column012 IS NOT NULL
                            AND column012 <> 3)
                        OR (column012 IS NULL
                            AND `columno12_readonly` <> 3))
                    AND ph.effectiveTo IS NULL
                INNER JOIN pm_project_group_relationship pgr ON pgr.`projectCode` = ph.`projectCode`
                INNER JOIN pm_project_contract pc ON pc.`projectGroupCode` = pgr.`projectGroupCode` AND pc.contractNo = sv.contractNo
                LEFT JOIN fnd_department fdp ON ph.`column001` = fdp.`departmentNum`
                LEFT JOIN fb_items fi ON fi.item = sv.itemCode
            WHERE sv.datastate = 1 AND pc.`contractNo` IS NOT NULL 
                AND ph.`projectCode` NOT LIKE '%his' 
        ]]>
        <isNotEmpty property="probRestore.probId">
            <isNotEqual prepend="and" property="probRestore.probId" compareValue="0">
            sw.probId = #probRestore.probId#
            AND NOT EXISTS (SELECT * FROM prob_restore WHERE serialNum = sv.barcode AND probId = #probRestore.probId#) 
            </isNotEqual>
        </isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.serialNum"><![CDATA[ sv.barcode like concat(#probRestore.serialNum#, '%')]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.itemModel"><![CDATA[ fi.itemname like concat(#probRestore.itemModel#, '%')]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.projectCode"><![CDATA[ ph.projectCode like concat(#probRestore.projectCode#, '%') ]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.projectName"><![CDATA[ ph.projectName like concat('%', #probRestore.projectName#, '%') ]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.contractNo"><![CDATA[ pc.contractNo = #probRestore.contractNo#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.officeCode"><![CDATA[ ph.column001 = #probRestore.officeCode#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.marketCode"><![CDATA[ ph.column004 = #probRestore.marketCode#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.systemCode"><![CDATA[ ph.column005 = #probRestore.systemCode#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.expendCode"><![CDATA[ ph.column006 = #probRestore.expendCode#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.industryCode"><![CDATA[ ph.column007 = #probRestore.industryCode#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="probRestore.areapower"><![CDATA[ ph.column001 in ($probRestore.areapower$) ]]></isNotEmpty>
        
        <isNotEmpty property="probRestore.prob">
            <isNotEmpty prepend="and" property="probRestore.prob.probNum"><![CDATA[ pm.probNum = #probRestore.prob.probNum#]]></isNotEmpty>
        </isNotEmpty>
        <isNotEmpty property="probRestore.version">
            <isNotEmpty property="probRestore.version.conp"> 
	            <!-- <isNotEmpty prepend="AND" property="probRestore.version.entryType"> sv.conpType = #probRestore.version.entryType# </isNotEmpty>
	            <isNotEmpty prepend="AND" property="probRestore.version.entrySeries"> sv.conpSeries = #probRestore.version.entrySeries# </isNotEmpty> -->
	            <isNotEmpty prepend="AND" property="probRestore.version.entryType"> sw.entryType = #probRestore.version.entryType# </isNotEmpty>
                <isNotEmpty prepend="AND" property="probRestore.version.entrySeries"> sw.entrySeries = #probRestore.version.entrySeries# </isNotEmpty>
                AND ( sv.conp = #probRestore.version.conp# 
                <isNotEmpty prepend="OR" property="probRestore.version.conpMark"> (#probRestore.version.conpMark# BETWEEN sw.markStart AND sw.markEnd) </isNotEmpty>
                )
            </isNotEmpty>
            
            <isNotEmpty prepend="AND" property="probRestore.version.boot"> sv.boot = #probRestore.version.boot# </isNotEmpty>
            <isNotEmpty prepend="AND" property="probRestore.version.cpld"> sv.cpld = #probRestore.version.cpld# </isNotEmpty>
            <isNotEmpty prepend="AND" property="probRestore.version.pcb">  sv.pcb  = #probRestore.version.pcb# </isNotEmpty>
        </isNotEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-prob-config.xml:721 | fragment=sqlQueryProbAffectedProjectSoftVersion
<include refid="sqlColumnProbAffectedProjectSoftVersion"/>
	    <include refid="sqlFromProbAffectedProjectSoftVersion"/>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-prob-config.xml:1835 | fragment=sql_contract_shipment_soft_list
SELECT DISTINCT IFNULL(ph.projectId, 0) AS projectId, ph.projectCode, ph.projectName
            , ph.officeCode AS `officeCode`, ph.officeName AS `officeName`
            , ph.marketCode as marketCode, ph.marketName as marketName
            , ph.systemCode as systemCode, ph.systemName as systemName
            , ph.expendCode as expendCode, ph.expendName as expendName
            , ph.industryCode as industryCode, ph.industryName as industryName
            
            , ph.projectStateName AS `projectStateName`
            , ph.serviceManagerCode AS `serviceManagerCode`, ph.serviceManagerCodeforjson AS `serviceManagerCodeforjson`
            , ph.programManagerCodeA AS `programManagerCodeA`, ph.programManagerCodeforjson AS `programManagerCodeforjson`
            , ph.programManagerCodeB AS `programManagerCodeB`, ph.programManagerCodeforjsonB AS `programManagerCodeforjsonB`
            
            , vsip.contract_code AS contractNo, vsip.barcode AS barCode, vsip.itemCode AS itemCode, vsip.itemModel AS itemModel, vsip.itemName AS itemName
            , CASE WHEN t3.conp IS NULL THEN t.`conp` ELSE t3.conp END AS conp, t3.conpBak, IFNULL(t3.conpChange, 0) conpChange
            , CASE WHEN t3.boot IS NULL THEN t.`boot` ELSE t3.boot END AS boot, t3.bootBak, IFNULL(t3.bootChange, 0) bootChange
            , CASE WHEN t3.cpld IS NULL THEN t.`cpld` ELSE t3.cpld END AS cpld, t3.cpldBak, IFNULL(t3.cpldChange, 0) cpldChange
            , CASE WHEN t3.pcb  IS NULL THEN t.`pcb`  ELSE t3.pcb  END AS pcb,  t3.pcbBak,  IFNULL(t3.pcbChange, 0) pcbChange
            , vsip.barcode2 AS barCode2, vsip.itemCode2 AS itemCode2
            , vsip.itemModel2 AS itemModel2
            , vsip.itemName2 AS itemName2
        FROM view_shipment_info_4_pm vsip
            LEFT JOIN view_shipment_ems_4_pm vsep
            ON vsep.contract_code = vsip.contract_code
                AND vsep.packId = vsip.packId
            LEFT JOIN fb_soft_version t ON vsip.`barcode` = t.`serial_number`
            LEFT JOIN fb_items fi ON fi.item = vsip.itemCode
            LEFT JOIN pm_project_contract ppc ON ppc.contractno = vsep.contract_code
            LEFT JOIN pm_project_group_relationship ppgr ON ppgr.projectGroupCode = ppc.projectGroupCode
            LEFT JOIN projectTempTable ph ON ph.projectCode = ppgr.projectCode
            LEFT JOIN pm_project_soft_version t3
            ON vsip.`barcode` = t3.`barcode`
                AND t3.`datastate` = 1
                AND ph.`projectId` = t3.`projectId`
        WHERE (ph.checkProfitCenter IS NULL or ph.officeCode = IFNULL(vsip.profitCenter, ph.officeCode))
            <isNotEmpty prepend="and" property="probStatistic.projectCode"> (ph.projectCode like CONCAT("%", #probStatistic.projectCode#, "%") or ph.contractNo like CONCAT("%", #probStatistic.projectCode#, "%"))</isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.projectName"> ph.projectName like CONCAT("%", #probStatistic.projectName#, "%")</isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.officeCode"><![CDATA[ ph.officeCode = #probStatistic.officeCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.marketCode"><![CDATA[ ph.marketCode = #probStatistic.marketCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.systemCode"><![CDATA[ ph.systemCode = #probStatistic.systemCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.expendCode"><![CDATA[ ph.expendCode = #probStatistic.expendCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.industryCode"><![CDATA[ ph.industryCode = #probStatistic.industryCode#]]></isNotEmpty>
            
            <isNotEmpty prepend="and" property="probStatistic.programManagerCode"> (ph.programManagerCodeA = #probStatistic.programManagerCode# or ph.programManagerCodeB = #probStatistic.programManagerCode#) </isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.serviceManagerCode"> ph.serviceManagerCode = #probStatistic.serviceManagerCode#</isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.contractNo"> vsip.contract_code = #probStatistic.contractNo# </isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.barCode"> (vsip.barCode = #probStatistic.barCode# or vsip.barcode2 = #probStatistic.barCode#) </isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.itemCode"> (vsip.itemCode = #probStatistic.itemCode# or vsip.itemCode2 = #probStatistic.itemCode#) </isNotEmpty>
            <isNotEmpty prepend="and" property="probStatistic.itemModel"> (vsip.itemModel like CONCAT(#probStatistic.itemModel#, "%") or vsip.itemModel2 like CONCAT(#probStatistic.itemModel#, "%")) </isNotEmpty>
            
            <isNotNull property="probStatistic.version">
                <isNotEmpty prepend="AND" property="probStatistic.version.conp"> t3.conp = #probStatistic.version.conp# </isNotEmpty>
                <isNotEmpty prepend="AND" property="probStatistic.version.boot"> t3.boot = #probStatistic.version.boot# </isNotEmpty>
                <isNotEmpty prepend="AND" property="probStatistic.version.cpld"> t3.cpld = #probStatistic.version.cpld# </isNotEmpty>
                <isNotEmpty prepend="AND" property="probStatistic.version.pcb"> t3.pcb = #probStatistic.version.pcb# </isNotEmpty>
            </isNotNull>
            <isNotEmpty prepend="AND" property="itemFilters">
                <iterate property="itemFilters" conjunction="OR" open="(" close=")">
                    <include refid="sqlItemFilters"/>
                </iterate>
            </isNotEmpty>
        <dynamic prepend="Having">
	        <isNotNull property="probStatistic.version">
	            <isNotEmpty prepend="AND" property="probStatistic.version.conp"> conp = #probStatistic.version.conp# </isNotEmpty>
	            <isNotEmpty prepend="AND" property="probStatistic.version.boot"> boot = #probStatistic.version.boot# </isNotEmpty>
	            <isNotEmpty prepend="AND" property="probStatistic.version.cpld"> cpld = #probStatistic.version.cpld# </isNotEmpty>
	            <isNotEmpty prepend="AND" property="probStatistic.version.pcb"> pcb = #probStatistic.version.pcb# </isNotEmpty>
	        </isNotNull>
        </dynamic>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-project-config.xml:43 | fragment=sql_query_project_by_power_table
<isNotEqual compareValue="10" property="projectState">
           <![CDATA[
                SELECT 
                    `ph`.`projectId` AS `projectId`, `ph`.`projectCode` AS `projectCode`, `rank`.`basicDataName` AS `rank`, (
                        CASE
                            WHEN (
                                `pr`.`mergeBranchMark` IS NOT NULL
                            ) 
                            THEN CONCAT(
                                `ph`.`projectName`, '-', `pr`.`mergeBranchMark`
                            ) 
                            ELSE `ph`.`projectName` 
                        END
                    ) AS `projectName`, majorProjectLevel,
                    `fd`.`basicDataName` AS `projectStateName`, `ph`.`projectState` AS `projectState`, 
                    GROUP_CONCAT(DISTINCT `pc`.`contractNo` SEPARATOR ',') AS `contractNo`, 
                    `ph`.`column005` AS `systemName`, 
                    `ph`.compId, `ph`.`column001` AS `officeCode`, `d`.`departmentName` AS `officeName`, 
                    `pm`.`memberCode` AS `salesManCode`, `pm`.`memberName` AS `salesManName`,
                    `pm2`.`memberCode` AS `serviceManager`, `pm2`.`memberName` AS `serviceManagerName`, 
                    `pm3`.`memberCode` AS `projectManager`, `pm3`.`memberName` AS `projectManagerName`, 
                    `gm`.`memberCode`  AS `groupMember`, `gm`.`memberName` AS `groupMemberName`, 
                    GROUP_CONCAT(DISTINCT `gm`.`memberCode`) AS teamMemberCodes,
                    GROUP_CONCAT(DISTINCT `gm`.`memberName`) AS teamMemberNames,
                     shipmentState.`basicDataName` AS shipmentStateName, state.`shipmentState`, 
                     planState.`basicDataName` AS planStateName, state.`projectPlanState`, 
                     executionState.`basicDataName` AS executionStateName, state.executionState, 
                     closeProcessState.`basicDataName` AS closeProcessStateName, state.closeProcessState, 
                     ph.projectStartTime, ph.createTime AS projectCreateTime, ph.projectRefreshTime, ph.projectCloseTime, 
                    CASE
                        WHEN ph.column012 IS NULL 
                        AND ph.columno12_readonly = - 1 
                        THEN ph.column012 
                        WHEN ph.column012 IS NOT NULL 
                        THEN ph.column012 
                        ELSE ph.columno12_readonly 
                    END AS ssfs, 
                    CASE
                        WHEN ph.column012 IS NULL 
                        AND ph.columno12_readonly = - 1 
                        THEN '未指定' 
                        WHEN ph.column012 IS NOT NULL 
                        THEN ssfs1.basicDataName 
                        ELSE ssfs2.basicDataName 
                    END AS ssfsName, 
             ]]>
                    <!-- CASE
                        WHEN 
                        CASE
                            WHEN ph.column012 IS NULL 
                            AND ph.columno12_readonly = - 1 
                            THEN ph.column012 
                            WHEN ph.column012 IS NOT NULL 
                            THEN ph.column012 
                            ELSE ph.columno12_readonly 
                        END IN (0, 4) 
                        THEN prp1.partyName 
                        ELSE 
                        CASE
                            WHEN 
                            CASE
                                WHEN ph.column012 IS NULL 
                                AND ph.columno12_readonly = - 1 
                                THEN ph.column012 
                                WHEN ph.column012 IS NOT NULL 
                                THEN ph.column012 
                                ELSE ph.columno12_readonly 
                            END IN (1, 3) 
                            THEN prp2.partyName 
                            ELSE NULL 
                        END 
                    END AS partnerChannel, -->
                    CONCAT_WS(",", 
                        IF(prp1.partyName = '', NULL, prp1.partyName), 
                        IF(prp2.partyName = '', NULL, prp2.partyName)
                    ) partnerChannel,
                    prp1.partyName as serviceChannel, 
                    prp2.partyName as agentChannel,
                    ph.customInfo,
             <isEqual property="checkWarranty" compareValue="true">
                    wcs.warrantyStatus, wcs.warrantyStatusName,
                    wcs.warrantyGrade, wcs.warrantyGradeName,
                    wcs.wafService, wcs.wafServiceName
             </isEqual>
             <isNotEqual property="checkWarranty" compareValue="true">
                    NULL AS warrantyStatus, NULL AS warrantyStatusName,
                    NULL AS warrantyGrade, NULL AS warrantyGradeName,
                    NULL AS wafService, NULL AS wafServiceName
             </isNotEqual>
                FROM
                    `pm_project_header` `ph` 
                    LEFT JOIN `pm_project_group_relationship` `pr` 
                        ON `ph`.`projectCode` = `pr`.`projectCode` 
                    LEFT JOIN `pm_project_contract` `pc` 
                        ON `pr`.`projectGroupCode` = `pc`.`projectGroupCode` 
             <isNotEmpty property="itemModel">
                    INNER JOIN temp_tb_projectId_filter_itemModel 
                        ON filteredProjectId = ph.projectId
             </isNotEmpty>
             <isNotEmpty property="barCode">
                 <![CDATA[ 
                    INNER JOIN 
                        (SELECT 
                            `c`.`contract_code` AS `contractNo`, profitCenter
                        FROM
                            `fb_shipment_barcode` `sb` 
                            LEFT JOIN `fb_shipment` `s` 
                                ON `s`.`packlist_id` = `sb`.`pack_id` 
                            LEFT JOIN `fb_contract` `c` 
                                ON `c`.`contract_id` = `s`.`con_id` 
                        WHERE (barcode = #barCode# or barcode2 = #barCode#)
                            AND (
                                ISNULL(`sb`.`rma_no`) 
                                OR `sb`.`rma_no` = '' 
                                OR `sb`.`rma_no` = 'null'
                            )) sc 
                 ]]> 
                        ON sc.contractNo = REPLACE(REPLACE(pc.`contractNo`, "-L", ""), "-C", "") 
                        AND CASE WHEN ph.salesType = '14' THEN sc.profitCenter = ph.column001 ELSE 1 = 1 END
                 <!-- ON pc.`contractNo` = sc.contractNo -->
             </isNotEmpty>
             <isEqual property="checkWarranty" compareValue="true">
                   INNER JOIN temp_tb_project_contractWarrantyState wcs
                        ON wcs.projectId = ph.projectId
                        <isNotEmpty prepend="AND" property="warrantyStatus"> wcs.warrantyStatus = #warrantyStatus# </isNotEmpty>
                        <isNotEmpty prepend="AND" property="warrantyGrade"> wcs.warrantyGrade = #warrantyGrade# </isNotEmpty>
                        <isNotEmpty prepend="AND" property="wafService"> wcs.wafService = #wafService# </isNotEmpty>
             </isEqual>
             <![CDATA[ 
                    LEFT JOIN `pm_project_group` `pg` 
                        ON `pr`.`projectGroupCode` = `pg`.`projectGroupCode` 
                        AND `pg`.`projectType` = '10' 
                    LEFT JOIN `fnd_basic_data` `fd` 
                        ON `fd`.`dataTypeCode` = '02' 
                        AND `fd`.`basicDataId` = `ph`.`projectState` 
                        AND `fd`.`effectiveFrom` < NOW() 
                        AND (
                            `fd`.`effectiveTo` > NOW() 
                            OR ISNULL(`fd`.`effectiveTo`)
                        ) 
                    LEFT JOIN `fnd_basic_data` `rank` 
                        ON `rank`.`dataTypeCode` = '05' 
                        AND `rank`.`basicDataId` = `ph`.`column010`
                        AND `rank`.`effectiveFrom` <= NOW() 
                        AND (
                            `rank`.`effectiveTo` > NOW() 
                            OR ISNULL(`rank`.`effectiveTo`)
                        ) 
                    LEFT JOIN `fnd_department` `d` 
                        ON `d`.`departmentNum` = `ph`.`column001` 
                    LEFT JOIN `pm_project_member` `pm` 
                        ON `pm`.`memberRole` = '10' 
                        AND `ph`.`projectId` = `pm`.`projectId` 
                        AND `pm`.`effectiveFrom` < NOW() 
                        AND (
                            `pm`.`effectiveTo` > NOW() 
                            OR ISNULL(`pm`.`effectiveTo`)
                        ) 
                    LEFT JOIN `pm_project_member` `pm2` 
                        ON `pm2`.`memberRole` = '20' 
                        AND `ph`.`projectId` = `pm2`.`projectId` 
                        AND `pm2`.`effectiveFrom` < NOW() 
                        AND (
                            `pm2`.`effectiveTo` > NOW() 
                            OR ISNULL(`pm2`.`effectiveTo`)
                        ) 
                    LEFT JOIN `pm_project_member` `pm3` 
                        ON `pm3`.`memberRole` = '30'
                        AND `ph`.`projectId` = `pm3`.`projectId` 
                        AND `pm3`.`effectiveFrom` < NOW()
                        AND (
                            `pm3`.`effectiveTo` > NOW() 
                            OR ISNULL(`pm3`.`effectiveTo`)
                        ) 
                    LEFT JOIN `pm_project_member` `gm` 
                        ON `gm`.`memberRole` in ('10', '20', '30', '40')
                        AND `ph`.`projectId` = `gm`.`projectId` 
                        AND `gm`.`effectiveFrom` < NOW()
                        AND (
                            `gm`.`effectiveTo` > NOW() 
                            OR ISNULL(`gm`.`effectiveTo`)
                        ) 
                    LEFT JOIN pm_project_state state 
                        ON state.`projectId` = ph.projectId 
                    LEFT JOIN pm_project_related_party prp1 
                        ON prp1.partyRole = "20" 
                        AND prp1.projectId = ph.projectId 
                        AND prp1.effectiveTo IS NULL 
                    LEFT JOIN pm_project_related_party prp2 
                        ON prp2.partyRole = "30" 
                        AND prp2.projectId = ph.projectId 
                        AND prp2.effectiveTo IS NULL 
                    LEFT JOIN fnd_basic_data shipmentState 
                        ON shipmentState.`dataTypeCode` = '20'  
                        AND shipmentState.`basicDataId` = state.`shipmentState` 
                        AND shipmentState.`effectiveTo` IS NULL 
                    LEFT JOIN fnd_basic_data planState 
                        ON planState.`dataTypeCode` = '22' 
                        AND planState.`basicDataId` = state.`projectPlanState` 
                        AND planState.`effectiveTo` IS NULL 
                    LEFT JOIN fnd_basic_data executionState 
                        ON executionState.`dataTypeCode` = 'projectExecutionState' 
                        AND executionState.`basicDataId` = state.`executionState` 
                        AND executionState.`effectiveTo` IS NULL 
                    LEFT JOIN fnd_basic_data closeProcessState 
                        ON closeProcessState.`dataTypeCode` = 'projectCloseProcessState' 
                        AND closeProcessState.`basicDataId` = state.`closeProcessState` 
                        AND closeProcessState.`effectiveTo` IS NULL 
                    LEFT JOIN fnd_basic_data ssfs1 
                        ON ssfs1.dataTypeCode = '15'
                        AND ssfs1.basicDataId = ph.column012 
                        AND ssfs1.effectiveTo IS NULL 
                    LEFT JOIN fnd_basic_data ssfs2 
                        ON ssfs2.dataTypeCode = '15' 
                        AND ssfs2.basicDataId = ph.columno12_readonly 
                        AND ssfs2.effectiveTo IS NULL 
                WHERE ISNULL(`ph`.`effectiveTo`) 
            ]]>
            <isNotEqual prepend="AND" property="projectId" compareValue="0"> ph.projectId = #projectId# </isNotEqual>
            <isNotEmpty prepend="AND" property="memberCode" open="(" close=")"> 
                gm.memberCode = #memberCode# 
	           <isNotEmpty property="serviceManagerCode">
	                <isNotEmpty property="officeCodes">
                        OR (
                            ph.column001 IN ($officeCodes$) 
                            AND (
                                ph.projectState = 20 
                                OR ph.projectState = 100
                            )
                        )
	                </isNotEmpty>
	            </isNotEmpty>
	            <isNotEmpty property="programManagerCode">
                    <isNotEmpty property="officeCodes">
                        OR (
                            ph.column001 IN ($officeCodes$) 
                            AND (
                                ph.projectState = 20 
                                OR ph.projectState = 100
                            )
                        )
                    </isNotEmpty>
	            </isNotEmpty>
            </isNotEmpty>
            <isEmpty property="memberCode"> 
	            <isNotEmpty prepend="and" property="serviceManagerCode">
	                <![CDATA[ 
	                (
	                    (pm2.memberCode  = #serviceManagerCode# or pm3.`memberCode` = #serviceManagerCode#)
	                ]]>
	                <isNotEmpty property="officeCodes">
	                        OR (
	                            ph.column001 IN ($officeCodes$) 
	                            AND (
	                                ph.projectState = 20 
	                                OR ph.projectState = 100
	                            )
	                        )
	                </isNotEmpty>
	                )
	            </isNotEmpty>
	            <isNotEmpty prepend="and" property="programManagerCode">
	                <![CDATA[ 
	                    (
	                        pm3.`memberCode` = #programManagerCode# 
	                ]]>
	                    <isNotEmpty property="officeCodes">
	                        OR (
	                            ph.column001 IN ($officeCodes$) 
	                            AND (
	                                ph.projectState = 20 
	                                OR ph.projectState = 100
	                            )
	                        )
	                    </isNotEmpty>
	                    )
	            </isNotEmpty>
            </isEmpty>
            <!-- <isEmpty property="serviceManagerCode">
                <isEmpty property="programManagerCode">
                    <isNotEmpty prepend="and" property="officeCodes"> ph.column001 IN ($officeCodes$) </isNotEmpty>
                </isEmpty>
            </isEmpty> -->
            <isNotNull property="customInfo.isProjectViewer">
	            <isNotEmpty prepend="and" property="officeCodes"> ph.column001 IN ($officeCodes$) </isNotEmpty>
	        </isNotNull>
            
            <!-- old*Code 传递页面查询框的数据，避免无法筛选已闭环和不予跟踪的项目经理和服务经理 -->
	        <isNotEmpty prepend="and" property="oldServiceManagerCode">
	            (pm2.memberCode = #oldServiceManagerCode# or pm3.memberCode = #oldServiceManagerCode#)
	        </isNotEmpty>
	        <isNotEmpty prepend="and" property="oldProgramManagerCode"> pm3.memberCode = #oldProgramManagerCode# </isNotEmpty>
            <isNotEmpty prepend="and" property="projectState"><![CDATA[ FIND_IN_SET(ph.projectState, #projectState# )]]></isNotEmpty>
            <isNotEmpty prepend="and" property="projectPlanState"><![CDATA[ state.projectPlanState = #projectPlanState# ]]></isNotEmpty>
            <isNotEqual compareValue="0" prepend="and" property="shipmentState"><![CDATA[ state.shipmentState = #shipmentState#]]></isNotEqual>
            <isNotEmpty prepend="and" property="executionState"><![CDATA[ state.executionState = #executionState#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="closeProcessState"><![CDATA[ state.closeProcessState = #closeProcessState#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="column001"><![CDATA[ ph.column001 = #column001# ]]></isNotEmpty>      
            <isNotEmpty prepend="and" property="column004"><![CDATA[ ph.column004 = #column004# ]]></isNotEmpty>
            <isNotEmpty prepend="and" property="column005"><![CDATA[ ph.column005 = #column005# ]]></isNotEmpty>      
            <isNotEmpty prepend="and" property="column006"><![CDATA[ ph.column006 = #column006# ]]></isNotEmpty>      
            <isNotEmpty prepend="and" property="column007"><![CDATA[ ph.column007 = #column007# ]]></isNotEmpty>  
            <isNotEmpty prepend="and" property="column011"><![CDATA[ ph.column011 = #column011#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="majorProjectLevel"><![CDATA[ ph.majorProjectLevel = #majorProjectLevel#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="compId"><![CDATA[ ph.compId = #compId# ]]></isNotEmpty>   
<!--                 GROUP BY `pc`.`projectGroupCode`,`ph`.`projectCode`  -->
            <![CDATA[
                GROUP BY `ph`.`projectCode`
            ]]> 
        </isNotEqual>
        <isEqual compareValue="10" property="projectState">
            <![CDATA[
            SELECT DISTINCT
              NULL                    AS `projectId`,
              NULL                    AS `projectCode`,
              NULL                    AS `rank`,
              IFNULL(IF(p.salesType = '01', p.projectName, pp.projectName), p.projectName)       AS `projectName`,
              pp.majorProjectLevel    AS `majorProjectLevel`,
              `fd`.`basicDataName`    AS `projectStateName`,
              `fd`.`basicDataId`      AS `projectState`,
              `p`.`contractNo`        AS `contractNo`,
              IFNULL(c1.id, c2.id) AS compId,
              `pp`.`officeCode`       AS `officeCode`,
              `d`.`departmentName`    AS `officeName`,
              `pp`.`salesManCode`     AS `salesManCode`,
              `pp`.`salesManName`     AS `salesManName`,
              NULL  AS `projectStartTime`,
              NULL as projectCreateTime,
              `pp`.`systemName`       AS `systemName`,
              NULL                    AS `serviceManager`,
              NULL                    AS `serviceManagerName`,
              NULL                    AS `projectManager`,
              NULL                    AS `projectManagerName`,
              NULL                    AS `groupMember`,
              NULL                    AS `groupMemberName`,
              NULL                    AS teamMemberCodes,
              NULL                    AS teamMemberNames,
              NULL  as shipmentStateName,
              NULL  as shipmentState,
              NULL as planStateName,
              NULL as projectPlanState,
              NULL AS executionStateName, NULL as executionState, 
              NULL AS closeProcessStateName, NULL as closeProcessState, 
              NULL as projectRefreshTime, NULL as projectCloseTime,
              null ssfs ,
              null ssfsName,
              null partnerChannel,
              null serviceChannel,
              null agentChannel,
              null customInfo,
              null AS warrantyStatus, null AS warrantyStatusName,
              null AS warrantyGrade, null AS warrantyGradeName,
              null AS wafService, null AS wafServiceName
            FROM `view_distinct_contract` `v`
               LEFT JOIN `pm_order_data_from_sap` `p`
                ON ((`v`.`id` = `p`.`id`))
                    LEFT JOIN `pm_project_property_from_sms` `pp`
                      ON ((`p`.`orderExecNumber` = `pp`.`orderExecNumber`))
                       LEFT JOIN `fnd_department` `d`
                     ON ((`pp`.`officeCode` = `d`.`departmentNum`))
                      LEFT JOIN `fnd_basic_data` `fd`
                    ON (`fd`.`basicDataId` = '10')
                         AND (`fd`.`dataTypeCode` = '02')
                         AND (`fd`.`effectiveFrom` < NOW())
                         AND ((`fd`.`effectiveTo` > NOW())
                           OR ISNULL(`fd`.`effectiveTo`))
                LEFT JOIN fnd_company c1 
                    ON c1.code = p.compCode 
                LEFT JOIN fnd_company c2 
                    ON c2.`code` = pp.corporationCode 
                WHERE NOT EXISTS(SELECT
                        1
                      FROM (`pm_project_contract` `t1`
                          JOIN `pm_project_group_relationship` `t2`
                        ON ((`t1`.`projectGroupCode` = `t2`.`projectGroupCode`)))
                         JOIN `pm_project_header` `t3`
            ]]>
                           ON (
<!--                            `t2`.`projectCode` = `t3`.`projectCode` -->
<!--                                 OR  -->
                                `t3`.`projectCode` = REPLACE(`t2`.`projectCode`, "-his", "")
                            )
            <![CDATA[
                            AND (`t3`.`effectiveFrom` <= NOW())
                            AND ((`t3`.`effectiveTo` > NOW())
                              OR ISNULL(`t3`.`effectiveTo`))
                      WHERE (`p`.`contractNo` = `t1`.`contractNo`)) AND v.contractNo IS NOT NULL;
            ]]>
        </isEqual>
        <isEmpty prepend="UNION ALL" property="projectState">
        <isEqual property="projectId" compareValue="0">
                <![CDATA[
            SELECT DISTINCT
              NULL                    AS `projectId`,
              NULL                    AS `projectCode`,
              NULL                    AS `rank`,
              IFNULL(IF(p.salesType = '01', p.projectName, pp.projectName), p.projectName)       AS `projectName`,
              pp.majorProjectLevel    AS `majorProjectLevel`,
              `fd`.`basicDataName`    AS `projectStateName`,
              `fd`.`basicDataId`      AS `projectState`,
              `p`.`contractNo`        AS `contractNo`,
              IFNULL(c1.id, c2.id) AS compId,
              `pp`.`officeCode`       AS `officeCode`,
              `d`.`departmentName`    AS `officeName`,
              `pp`.`salesManCode`     AS `salesManCode`,
              `pp`.`salesManName`     AS `salesManName`,
              NULL  AS `projectStartTime`,
              NULL as projectCreateTime,
              `pp`.`systemName`       AS `systemName`,
              NULL                    AS `serviceManager`,
              NULL                    AS `serviceManagerName`,
              NULL                    AS `projectManager`,
              NULL                    AS `projectManagerName`,
              NULL                    AS `groupMember`,
              NULL                    AS `groupMemberName`,
              NULL                    AS teamMemberCodes,
              NULL                    AS teamMemberNames,
              NULL  as shipmentStateName,
              NULL  as shipmentState,
              NULL as planStateName,
              NULL as projectPlanState,
              NULL AS executionStateName, NULL as executionState, 
              NULL AS closeProcessStateName, NULL as closeProcessState, 
              NULL as projectRefreshTime,NULL as projectCloseTime,
              null ssfs ,
              null ssfsName,
              null partnerChannel,
              null serviceChannel,
              null agentChannel,
              null customInfo,
              null AS warrantyStatus, null AS warrantyStatusName,
              null AS warrantyGrade, null AS warrantyGradeName,
              null AS wafService, null AS wafServiceName
            FROM `view_distinct_contract` `v`
               LEFT JOIN `pm_order_data_from_sap` `p`
                    ON ((`v`.`id` = `p`.`id`))
                    LEFT JOIN `pm_project_property_from_sms` `pp`
                      ON ((`p`.`orderExecNumber` = `pp`.`orderExecNumber`))
                       LEFT JOIN `fnd_department` `d`
                     ON ((`pp`.`officeCode` = `d`.`departmentNum`))
                      LEFT JOIN `fnd_basic_data` `fd`
                    ON (`fd`.`basicDataId` = '10')
                         AND (`fd`.`dataTypeCode` = '02')
                         AND (`fd`.`effectiveFrom` < NOW())
                         AND ((`fd`.`effectiveTo` > NOW())
                           OR ISNULL(`fd`.`effectiveTo`))
                LEFT JOIN fnd_company c1 
                    ON c1.code = p.compCode 
                LEFT JOIN fnd_company c2 
                    ON c2.`code` = pp.corporationCode 
                WHERE NOT EXISTS(SELECT
                        1
                      FROM (`pm_project_contract` `t1`
                          JOIN `pm_project_group_relationship` `t2`
                        ON ((`t1`.`projectGroupCode` = `t2`.`projectGroupCode`)))
                         JOIN `pm_project_header` `t3`
            ]]>
                           ON (
<!--                            `t2`.`projectCode` = `t3`.`projectCode` -->
<!--                                 OR  -->
                                `t3`.`projectCode` = REPLACE(`t2`.`projectCode`, "-his", "")
                            )
            <![CDATA[
                            AND (`t3`.`effectiveFrom` <= NOW())
                            AND ((`t3`.`effectiveTo` > NOW())
                              OR ISNULL(`t3`.`effectiveTo`))
                      WHERE (`p`.`contractNo` = `t1`.`contractNo`)) AND v.contractNo IS NOT NULL;
            ]]>
        </isEqual>
        </isEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-project-config.xml:516 | fragment=sql_query_project_by_power_where
<![CDATA[
            where projectId > 0 
        ]]>
        <isNotEmpty prepend="and" property="customInfo.fullProjectName"><![CDATA[ projectName = #projectName# ]]></isNotEmpty>
        <isNotEmpty prepend="and" property="projectName"><![CDATA[ projectName like CONCAT('%', #projectName#, '%')]]></isNotEmpty>
        <isNotEmpty prepend="and" property="projectState"><![CDATA[ FIND_IN_SET(projectState, #projectState#) ]]></isNotEmpty>
        <isNotEmpty prepend="and" property="projectPlanState"><![CDATA[ projectPlanState = #projectPlanState# ]]></isNotEmpty>
        <isNotEmpty prepend="and" property="column001"><![CDATA[ officeCode = #column001#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="contractNo"><![CDATA[ (contractNo like CONCAT('%', #contractNo#, '%') or projectCode like CONCAT('%', #contractNo#, '%'))]]></isNotEmpty>
        <isNotEmpty prepend="and" property="salesManCode"><![CDATA[ salesManCode like CONCAT('%', #salesManCode#)]]></isNotEmpty>
        <!-- <isNotEmpty prepend="and" property="serviceManagerCode"><![CDATA[ (serviceManager = #serviceManagerCode# or projectManager = #serviceManagerCode#)]]></isNotEmpty> -->
        <isNotEmpty prepend="AND" property="memberCode" open="(" close=")"> 
            groupMember = #memberCode# 
            <isNotEmpty property="serviceManagerCode">
                <isNotEmpty property="officeCodes">
                    OR (
                        officeCode IN ($officeCodes$) 
                        AND (
                            projectState = 20 
                            or projectState = 100
                        )
                    )
                </isNotEmpty>
            </isNotEmpty>
            <isNotEmpty property="programManagerCode">
                <isNotEmpty property="officeCodes">
                    OR (
                        officeCode IN ($officeCodes$) 
                        AND (
                            projectState = 20 
                            or projectState = 100
                        )
                    )
                </isNotEmpty>
            </isNotEmpty>
        </isNotEmpty>
        <isEmpty property="memberCode"> 
	        <isNotEmpty prepend="and" property="serviceManagerCode">
	            <![CDATA[ 
	                (
	                    (serviceManager = #serviceManagerCode# 
	                        or 
	                        projectManager = #serviceManagerCode#)
	            ]]>
	                    <isNotEmpty property="officeCodes">
	                        OR (
	                            officeCode IN ($officeCodes$) 
	                            AND (
	                                projectState = 20 
	                                or projectState = 100
	                            )
	                        )
	                    </isNotEmpty>
	                )
	        </isNotEmpty>
	        <!-- <isNotEmpty prepend="and" property="programManagerCode"><![CDATA[ projectManager = #programManagerCode#]]></isNotEmpty> -->
	        <isNotEmpty prepend="and" property="programManagerCode">
	            <![CDATA[ 
	                (
	                    projectManager = #programManagerCode# 
	            ]]>
	                <isNotEmpty property="officeCodes">
	                    OR (
	                        officeCode IN ($officeCodes$) 
	                        AND (
	                            projectState = 20 
	                            or projectState = 100
	                        )
	                    )
	                </isNotEmpty>
	                )
	        </isNotEmpty>
        </isEmpty>
        <!-- <isEmpty property="serviceManagerCode">
            <isEmpty property="programManagerCode">
                <isNotEmpty prepend="and" property="officeCodes"> t1.officeCode IN ($officeCodes$) </isNotEmpty>
            </isEmpty>
        </isEmpty> -->
        <isNotNull property="customInfo.isProjectViewer">
            <isNotEmpty prepend="and" property="officeCodes"> t1.officeCode IN ($officeCodes$) </isNotEmpty>
        </isNotNull>
        
        <!-- old*Code 传递页面查询框的数据，避免无法筛选已闭环和不予跟踪的项目经理和服务经理 -->
        <isNotEmpty prepend="and" property="oldServiceManagerCode">
            (serviceManager = #oldServiceManagerCode# or projectManager = #oldServiceManagerCode#)
        </isNotEmpty>
        <isNotEmpty prepend="and" property="oldProgramManagerCode"> projectManager = #oldProgramManagerCode# </isNotEmpty>
        <isNotEmpty prepend="and" property="column010"><![CDATA[ rank = #column010#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="majorProjectLevel"><![CDATA[ majorProjectLevel = #majorProjectLevel#]]></isNotEmpty>
        <isNotEqual compareValue="0" prepend="and" property="shipmentState"><![CDATA[ shipmentState = #shipmentState#]]></isNotEqual>
        <isNotEmpty prepend="and" property="createStartTime"><![CDATA[ projectCreateTime > #createStartTime#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="createEndTime"><![CDATA[ projectCreateTime < #createEndTime#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="refreshStartTime"><![CDATA[ projectRefreshTime > #refreshStartTime#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="refreshEndTime"><![CDATA[ projectRefreshTime < #refreshEndTime#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="closeStartTime"><![CDATA[ projectCloseTime > #closeStartTime#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="closeEndTime"><![CDATA[ projectCloseTime < #closeEndTime#]]></isNotEmpty>
        <isNotEmpty prepend="and" property="column012"><![CDATA[ ssfs = #column012#]]></isNotEmpty>
        
        <!-- <isNotEmpty prepend="and" property="itemModel"><![CDATA[ filteredProjectId is not null]]></isNotEmpty> -->
        <isNotEmpty prepend="and" property="compId"><![CDATA[ compId = #compId# ]]></isNotEmpty>
        <isNotEmpty prepend="and" property="partnerChannel"><![CDATA[ partnerChannel like CONCAT("%", #partnerChannel#, "%") ]]></isNotEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-project-config.xml:2214 | fragment=sqlShipmentInfoByContractNo
SELECT DISTINCT vsip.contract_code contractNo, vsip.barcode barCode, vsip.itemCode itemCode, vsip.itemModel, vsip.itemName itemName, 
                vsep.receiveName receiveName, vsep.emsNum emsNum, vsep.packdate packdate, vsep.emsCompany emsCompany,NULL AS installAddress,
                NULL AS chProjectId, NULL AS chContractNo, NULL AS transferProjectId, NULL AS transferContractNo, -1 AS transferFlag,
                vsip.barcode2 barCode2, vsip.itemCode2 itemCode2, vsip.itemModel2 AS itemModel2, vsip.itemName2 itemName2
            FROM view_shipment_info_4_pm vsip 
                LEFT JOIN view_shipment_ems_4_pm vsep ON vsep.contract_code = vsip.contract_code AND vsep.packId = vsip.packId
                WHERE vsip.contract_code in ($contractNo$) AND NOT EXISTS(SELECT 1 FROM pm_project_shipment pps1 WHERE vsip.barcode = pps1.barcode
                    and contractNo in ($contractNo$) and projectId = #projectId#
                )
                <isNotEmpty prepend="and" property="profitCenter"> vsip.profitCenter = #profitCenter# </isNotEmpty>
            UNION ALL 
            SELECT DISTINCT pps.`contractNo`,pps.`barcode`,pps.`itemCode`, fi0.itemname AS itemModel, pps.`itemName`,pps.`receiveName`,pps.`emsNum`,pps.`packdate`,pps.`emsCompany`,pps.`installAddress`, 
                pps.chProjectId, pps.chContractNo, pps.transferProjectId, pps.transferContractNo, pps.transferFlag,
                sbr.`sn2` AS barCode2, sbr.`item2` AS itemCode2, fi.itemname AS itemModel2, fi.describe_ AS itemName2
            FROM pm_project_shipment pps 
                LEFT JOIN `fb_shipment_barcode_relation` sbr 
                    ON sbr.contract = REPLACE(pps.contractNo, '-C', '')
                    AND pps.barcode = sbr.sn1 
                LEFT JOIN fb_items fi0 
                    ON fi0.`item` = pps.itemCode
                LEFT JOIN fb_items fi 
                    ON fi.`item` = sbr.`item2` 
            WHERE contractNo in ($contractNo$) and projectId = #projectId#
                 <isEqual prepend="AND" property="excludeTransferOut" compareValue="">transferFlag != 1</isEqual>
                 AND EXISTS 
                (SELECT 
                    1 
                FROM
                    view_shipment_info_4_pm vsip 
                    LEFT JOIN view_shipment_ems_4_pm vsep 
                        ON vsep.contract_code = vsip.contract_code 
                        AND vsep.packId = vsip.packId 
                WHERE vsip.contract_code IN ($sourceContractNo$)
                <!-- WHERE FIND_IN_SET(vsip.contract_code, REPLACE(REPLACE(#contractNo#, '-C', ''), "'",'')) -->
                    AND vsip.barcode = pps.barcode)

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-project-config.xml:2498 | fragment=sqlProjectSoftVersionList
SELECT 
            sv.id, sv.projectId, sv.logId
        <isNotEqual property="checkShipment" compareValue="true">
            , sv.contractNo, sv.itemCode, sv.datastate
        </isNotEqual>
        <isEqual property="checkShipment" compareValue="true">
            , IFNULL(sv.contractNo, fc.contract_code) AS contractNo
            , TRIM(IFNULL(sv.itemCode, fsb.item)) AS itemCode
            , COUNT(fc.contract_code) > 0 AS datastate
        </isEqual>
            , sv.barCode, sv.conp, sv.conpType, sv.conpSeries, sv.conpMark
            , sv.conpBak, sv.conpChange, sv.cpld, sv.cpldBak, sv.cpldChange
            , sv.boot, sv.bootBak, sv.bootChange, sv.pcb, sv.pcbBak
            , sv.pcbChange, sv.executeTime, sv.createTime, sv.createBy
            , sv.updateTime, sv.updateBy, sv.customInfo
            
            , fi.itemname as itemModel
            , fi.describe_ as itemDesc
            
            , ph.column001 as officeCode, fdp.departmentName as officeName
            , ph.column004 as marketCode, ph.column004 as marketName
            , ph.column005 as systemCode, ph.column005 as systemName
            , ph.column006 as expendCode, ph.column006 as expendName
            , ph.column007 as industryCode, ph.column007 as industryName
       
            
            , MAX(sbr.`item2`) AS itemCode2, MAX(sbr.`sn2`) AS barCode2 
        FROM
            pm_project_soft_version sv
            LEFT JOIN `pm_project_header` ph 
                ON sv.`projectId` = ph.`projectId` 
            LEFT JOIN `pm_project_group_relationship` pgr 
                ON pgr.`projectCode` = ph.`projectCode` 
            LEFT JOIN `pm_project_contract` pc 
                ON pc.`projectGroupCode` = pgr.`projectGroupCode` 
            LEFT JOIN `fb_shipment_barcode_relation` sbr 
                ON sbr.contract = REPLACE(pc.contractNo, '-C', '')
                AND sv.barcode = sbr.sn1 
            LEFT JOIN fnd_department fdp ON ph.`column001` = fdp.`departmentNum`
            LEFT JOIN fb_items fi ON fi.item = sv.itemCode
            
        <!-- <isEqual property="checkShipment" compareValue="true">
            INNER JOIN fb_contract fc ON fc.contract_code = pc.contractNo 
            INNER JOIN fb_shipment fs ON fc.`contract_id` = fs.`con_id` 
            LEFT JOIN fb_shipment_barcode fsb ## FORCE key(barcode_pack_rma_IDX)
                ON   fsb.`barcode` = sv.barcode 
                AND fs.`packlist_id` = fsb.`pack_id` AND(
                    ISNULL(`fsb`.`rma_no`) 
                    OR (`fsb`.`rma_no` = '') 
                    OR (`fsb`.`rma_no` = 'null')
                ) 
        </isEqual> -->
        <isEqual property="checkShipment" compareValue="true">
	        LEFT JOIN fb_shipment_barcode fsb <!-- ## FORCE key(barcode_pack_rma_IDX) -->
		        ON   fsb.`barcode` = sv.barcode 
		        AND (
		            ISNULL(`fsb`.`rma_no`) 
		            OR (`fsb`.`rma_no` = '') 
		            OR (`fsb`.`rma_no` = 'null')
		        ) 
		    LEFT JOIN fb_shipment fs ON  fs.`packlist_id` = fsb.`pack_id` 
		    LEFT JOIN fb_contract fc ON fc.`contract_id` = fs.`con_id` AND fc.contract_code = pc.contractNo 
	    </isEqual>
        <dynamic prepend="WHERE">
            <isNotEmpty prepend="and" property="projectId"><![CDATA[ ph.projectId = #projectId#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="logId"><![CDATA[ sv.logId = #logId#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="contractNo"><![CDATA[ sv.contractNo = #contractNo#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="itemCode"><![CDATA[ sv.itemCode = #itemCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="barCode"><![CDATA[ sv.barCode like concat(#barCode#, '%')]]></isNotEmpty>
            <isNotEmpty prepend="and" property="datastate"><![CDATA[ sv.datastate = #datastate#]]></isNotEmpty>
            
            <isNotEmpty prepend="and" property="itemModel"><![CDATA[ fi.itemname like concat(#itemModel#, '%')]]></isNotEmpty>
            <isNotEmpty prepend="and" property="projectName"><![CDATA[ ph.projectName like concat('%', #projectName#, '%')]]></isNotEmpty>
            <!-- <isNotEmpty prepend="and" property="contractNo"><![CDATA[ pc.contractNo = #contractNo#]]></isNotEmpty> -->
            <isNotEmpty prepend="and" property="officeCode"><![CDATA[ ph.column001 = #officeCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="marketCode"><![CDATA[ ph.column004 = #marketCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="systemCode"><![CDATA[ ph.column005 = #systemCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="expendCode"><![CDATA[ ph.column006 = #expendCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="industryCode"><![CDATA[ ph.column007 = #industryCode#]]></isNotEmpty>
            
            <isEqual prepend="AND" property="checkHasConp" compareValue="true"> sv.conp > '' </isEqual>
            <isEqual prepend="AND" property="checkNullConpMark" compareValue="true"> 
                sv.conpType IS NULL
				AND sv.conpSeries IS null
				AND sv.conpMark IS NULL
            </isEqual>
        </dynamic>
        GROUP BY sv.`id`
        <isEqual property="updateInvalidShipment" compareValue="true"> 
        HAVING datastate = 0
        </isEqual>
        <isEmpty property="logId">
        ORDER BY NULL
        </isEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:37 | fragment=SubcontractProject_List
id, subcontractName, contractNos, projectIds, type, state, callbackState, facilitatorId, facilitatorName, bankInfo, bankAccount, officeCode, profitDepCode, 
    subcontractNo, isAccrued, isInvoiced, subcontractAmount, reason, remark, effectiveFrom, effectiveTo, zrApproveTime, createBy, createTime, 
    updateBy, updateTime, customInfo, orgId

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:267 | fragment=sqlLeftJoinLatestPayment
LEFT JOIN 
		    (
		    SELECT 
		        t.`subcontractId`, t.confirmTime, t.paymentTime, t.remark, maxPay.paidAmount, t.customInfo
		    FROM
		        `pm_subcontract_project_payment` t 
		        INNER JOIN 
		            (SELECT 
		                <!-- MAX(IF(confirmTime IS NOT NULL, id, NULL)) AS maxPayId, -->
		                MAX(id) AS maxPayId,
		                SUM(
		                    IF(
		                        paymentTime IS NOT NULL, CONVERT(
		                            REPLACE(IFNULL(customInfo ->> '$$.paidAmount', amount), ",", ""), DECIMAL (25, 2)
		                        ), 0
		                    )
		                ) AS paidAmount 
		            FROM
		                `pm_subcontract_project_payment` 
		            GROUP BY subcontractId) maxPay 
		            ON t.id = maxPay.maxPayId
		) spp
		    ON spp.subcontractId = h.`id`

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:294 | fragment=sqlWhereLatestPayment
<isNotEmpty prepend="AND" property="customInfo.paymentStatus"> #customInfo.paymentStatus# = spp.`customInfo` ->> '$$.status' </isNotEmpty>
        <isNotEmpty prepend="AND" property="customInfo.paid"> #customInfo.paid# = spp.`customInfo` ->> '$$.paid' </isNotEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:1220 | fragment=SubcontractPayment_Column_List
id, subcontractId, ratio, amount, confirmTime, paymentTime, remark, sseId, createTime, createBy, 
    updateTime, updateBy, customInfo

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:1592 | fragment=SubcontractPrice_List
id, subcontractName, contractNos, projectIds, type, state, callbackState, facilitatorId, facilitatorName, bankInfo, bankAccount, officeCode, profitDepCode,
    subcontractNo, isAccrued, subcontractAmount, reason, remark, effectiveFrom, effectiveTo, createBy, createTime,
    updateBy, updateTime

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:234 | fragment=sql_projectWarrantyCallback_where_selective
<isNotEmpty prepend="AND" property="id"> pwc.`id` = #id# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectId"> pwc.`projectId` = #projectId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="officeCode"> pwc.`officeCode` = #officeCode# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectIds"> pwc.`projectIds` = #projectIds# </isNotEmpty>
	<isNotEmpty prepend="AND" property="serviceImpl"> pwc.`serviceImpl` = #serviceImpl# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectCode"> pwc.`projectCode` like concat("%", #projectCode#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="contractNos"> pwc.`contractNos` like concat("%", #contractNos#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectName"> pwc.`projectName` like concat("%", #projectName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="industryName"> pwc.`industryName` like concat("%", #industryName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="agentChannel"> pwc.`agentChannel` like concat("%", #agentChannel#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="finalCustomerName"> pwc.`finalCustomerName` like concat("%", #finalCustomerName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customer1"> pwc.`customer1` like concat("%", #customer1#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customerContact1"> pwc.`customerContact1` like concat("%", #customerContact1#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customer2"> pwc.`customer2` like concat("%", #customer2#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customerContact2"> pwc.`customerContact2` like concat("%", #customerContact2#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyStartTime"> pwc.`warrantyStartTime` = #warrantyStartTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyEndTime"> pwc.`warrantyEndTime` = #warrantyEndTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="renewalIntention"> pwc.`renewalIntention` = #renewalIntention# </isNotEmpty>
	<isNotEmpty prepend="AND" property="callbackTime"> pwc.`callbackTime` = #callbackTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="nextCallbackTime"> pwc.`nextCallbackTime` = #nextCallbackTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="taskId"> pwc.`taskId` = #taskId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="quesnaireId"> pwc.`quesnaireId` = #quesnaireId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="quesnaireVersion"> pwc.`quesnaireVersion` = #quesnaireVersion# </isNotEmpty>
	<isNotEmpty prepend="AND" property="quesnaireState"> pwc.`quesnaireState` = #quesnaireState# </isNotEmpty>
	<isNotEmpty prepend="AND" property="isDelete"> pwc.`isDelete` = #isDelete# </isNotEmpty>
	<isNotEmpty prepend="AND" property="remark"> pwc.`remark` = #remark# </isNotEmpty>
	<isNotEmpty prepend="AND" property="compId"> pwc.`compId` = #compId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="createBy"> pwc.`createBy` = #createBy# </isNotEmpty>
	<isNotEmpty prepend="AND" property="createTime"> pwc.`createTime` = #createTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="updateBy"> pwc.`updateBy` = #updateBy# </isNotEmpty>
	<isNotEmpty prepend="AND" property="updateTime"> pwc.`updateTime` = #updateTime# </isNotEmpty>
	<!-- <isNotEmpty prepend="AND" property="customInfo"> pwc.`customInfo` = #customInfo# </isNotEmpty> -->
	<isNotEmpty property="customInfo">
        <iterate property="customInfo.iterator" >
            <isNotEmpty prepend="AND" property="customInfo.iterator[].value">
                #customInfo.iterator[].value# = pwc.customInfo ->> '$$.$customInfo.iterator[].key$'
            </isNotEmpty>
        </iterate>
        <!-- <isNotEmpty prepend="AND" property="customInfo.phoneAnswerState"> 
            <isEqual property="customInfo.phoneAnswerState" compareValue="0"> pwc.customInfo ->> '$$.phoneAnswerState' is null </isEqual>
            <isNotEqual property="customInfo.phoneAnswerState" compareValue="0"> #customInfo.phoneAnswerState# = pwc.customInfo ->> '$$.phoneAnswerState' </isNotEqual>
        </isNotEmpty> -->
    </isNotEmpty>
	
	<isNotEmpty prepend="AND" property="warrantyEndTimeStart"> pwc.warrantyEndTime >= #warrantyEndTimeStart# </isNotEmpty>
   	<isNotEmpty prepend="AND" property="warrantyEndTimeEnd"> #warrantyEndTimeEnd# >= pwc.warrantyEndTime </isNotEmpty>
   	<isNotEmpty prepend="AND" property="processStartTime"> pwc.callbackTime >= #processStartTime# </isNotEmpty>
   	<isNotEmpty prepend="AND" property="processEndTime"> #processEndTime# >= pwc.callbackTime </isNotEmpty>
   	<isNotEmpty prepend="AND" property="callbackTimeStart"> pwc.callbackTime >= #callbackTimeStart# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="callbackTimeEnd"> #callbackTimeEnd# >= pwc.callbackTime </isNotEmpty>
 	<isNotEmpty prepend="AND" property="nextCallbackTimeStart"> pwc.nextCallbackTime >= #nextCallbackTimeStart# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="nextCallbackTimeEnd"> #nextCallbackTimeEnd# >= pwc.nextCallbackTime </isNotEmpty>
 	<isNotEmpty prepend="AND" property="customerSearch">
 		(
			pwc.customer1 like concat("%", #customerSearch#, "%") 
			or pwc.customer2 like concat("%", #customerSearch#, "%") 
			or pwc.customerContact1 like concat("%", #customerSearch#, "%") 
			or pwc.customerContact2 like concat("%", #customerSearch#, "%")
		)
 	</isNotEmpty>
 	<isNotEmpty prepend="AND" property="customerContactSearch">
		(
			pwc.customerContact1 like concat("%", #customerContactSearch#, "%") 
			or pwc.customerContact2 like concat("%", #customerContactSearch#, "%") 
		)
	</isNotEmpty>
	
	<isNotEmpty prepend="AND" property="customerContactSearch">
        (
            pwc.customerContact1 like concat("%", #customerContactSearch#, "%") 
            or pwc.customerContact2 like concat("%", #customerContactSearch#, "%") 
        )
    </isNotEmpty>
    <isNotEmpty prepend="AND" property="salerSearch">
        (
            slm.memberCode = #salerSearch#
            or slm.memberName like concat("%", #salerSearch#, "%") 
            or slm.phoneNum like concat("%", #salerSearch#, "%") 
        )
    </isNotEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:316 | fragment=sql_projectWarrantyCallback_model_where_selective
<isNotEmpty property="model">
		<isNotEmpty prepend="AND" property="model.id"> pwc.`id` = #model.id# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectId"> pwc.`projectId` = #model.projectId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.officeCode"> pwc.`officeCode` = #model.officeCode# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectIds"> pwc.`projectIds` = #model.projectIds# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.serviceImpl"> pwc.`serviceImpl` = #model.serviceImpl# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectCode"> pwc.`projectCode` like concat("%", #model.projectCode#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.contractNos"> pwc.`contractNos` like concat("%", #model.contractNos#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectName"> pwc.`projectName` like concat("%", #model.projectName#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.industryName"> pwc.`industryName` like concat("%", #model.industryName#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.agentChannel"> pwc.`agentChannel` like concat("%", #model.agentChannel#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.finalCustomerName"> pwc.`finalCustomerName` like concat("%", #model.finalCustomerName#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customer1"> pwc.`customer1` like concat("%", #model.customer1#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customerContact1"> pwc.`customerContact1` like concat("%", #model.customerContact1#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customer2"> pwc.`customer2` like concat("%", #model.customer2#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customerContact2"> pwc.`customerContact2` like concat("%", #model.customerContact2#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.warrantyStartTime"> pwc.`warrantyStartTime` = #model.warrantyStartTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.warrantyEndTime"> pwc.`warrantyEndTime` = #model.warrantyEndTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.renewalIntention"> pwc.`renewalIntention` = #model.renewalIntention# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.callbackTime"> pwc.`callbackTime` = #model.callbackTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.nextCallbackTime"> pwc.`nextCallbackTime` = #model.nextCallbackTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.taskId"> pwc.`taskId` = #model.taskId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.quesnaireId"> pwc.`quesnaireId` = #model.quesnaireId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.quesnaireVersion"> pwc.`quesnaireVersion` = #model.quesnaireVersion# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.quesnaireState"> pwc.`quesnaireState` = #model.quesnaireState# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.isDelete"> pwc.`isDelete` = #model.isDelete# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.remark"> pwc.`remark` = #model.remark# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.compId"> pwc.`compId` = #model.compId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.createBy"> pwc.`createBy` = #model.createBy# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.createTime"> pwc.`createTime` = #model.createTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.updateBy"> pwc.`updateBy` = #model.updateBy# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.updateTime"> pwc.`updateTime` = #model.updateTime# </isNotEmpty>
		<!-- <isNotEmpty prepend="AND" property="model.customInfo"> pwc.`customInfo` = #model.customInfo# </isNotEmpty> -->
		<isNotEmpty property="model.customInfo">
		    <iterate property="model.customInfo.iterator" >
	            <isNotEmpty prepend="AND" property="model.customInfo.iterator[].value">
	                #model.customInfo.iterator[].value# = pwc.customInfo ->> '$$.$model.customInfo.iterator[].key$'
	            </isNotEmpty>
	        </iterate>
            <!-- <isNotEmpty prepend="AND" property="model.customInfo.phoneAnswerState"> 
                <isEqual property="model.customInfo.phoneAnswerState" compareValue="0"> pwc.customInfo ->> '$$.phoneAnswerState' is null </isEqual>
                <isNotEqual property="model.customInfo.phoneAnswerState" compareValue="0"> #model.customInfo.phoneAnswerState# = pwc.customInfo ->> '$$.phoneAnswerState' </isNotEqual>
            </isNotEmpty> -->
        </isNotEmpty>
        
		<isNotEmpty prepend="AND" property="model.warrantyEndTimeStart"> pwc.warrantyEndTime >= #model.warrantyEndTimeStart# </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.warrantyEndTimeEnd"> #model.warrantyEndTimeEnd# >= pwc.warrantyEndTime </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.processStartTime"> pwc.callbackTime >= #model.processStartTime# </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.processEndTime"> #model.processEndTime# >= pwc.callbackTime </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.callbackTimeStart"> pwc.callbackTime >= #model.callbackTimeStart# </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.callbackTimeEnd"> #model.callbackTimeEnd# >= pwc.callbackTime </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.nextCallbackTimeStart"> pwc.nextCallbackTime >= #model.nextCallbackTimeStart# </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.nextCallbackTimeEnd"> #model.nextCallbackTimeEnd# >= pwc.nextCallbackTime </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.customerSearch">
	 		(
				pwc.customer1 like concat("%", #model.customerSearch#, "%") 
				or pwc.customer2 like concat("%", #model.customerSearch#, "%") 
				or pwc.customerContact1 like concat("%", #model.customerSearch#, "%") 
				or pwc.customerContact2 like concat("%", #model.customerSearch#, "%") 
			)
 		</isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.customerContactSearch">
	 		(
				pwc.customerContact1 like concat("%", #model.customerContactSearch#, "%") 
				or pwc.customerContact2 like concat("%", #model.customerContactSearch#, "%") 
			)
 		</isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.salerSearch">
	        (
	            slm.memberCode = #model.salerSearch#
	            or slm.memberName like concat("%", #model.salerSearch#, "%") 
	            or slm.phoneNum like concat("%", #model.salerSearch#, "%") 
	        )
	    </isNotEmpty>
	</isNotEmpty>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:403 | fragment=sql_projectWarrantyCallback_pageable_limit
<isNotNull property="displayParam">
	    <isNotEmpty property="displayParam.sort">
	        order by $displayParam.sort$
	    </isNotEmpty>
	    <isGreaterThan property="displayParam.pagesize" compareValue="-1">
	        limit #displayParam.offset#, #displayParam.pagesize# 
	    </isGreaterThan>
    </isNotNull>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:473 | fragment=sql_select_projectWarrantyCallbackMapList
select pwc.*, d.`departmentName` AS officeName, c.name AS companyName, c.abbr as companyAbbr,
    	IFNULL(ssfs1.basicDataName, '未指定') AS serviceName
    	<isNotEqual property="hideQuesnaire" compareValue="true" prepend=",">
	        qrt.* 
	    </isNotEqual>
	    <isNotEqual property="hideWarranty" compareValue="true" prepend=",">
	        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))) warrantyStatus, 
	        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
	            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
	                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
	            )
	        ) AS warrantyStatusName,
<!-- 	    MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
	        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime, -->
	        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
	        MAX(wcs.`warrantyGrade`) warrantyGrade, 
	        IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
	            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
	                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
	            )
	        ) AS warrantyGradeName,
	        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
	        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
	        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
	        IFNULL(MAX(wcs.hasRenewal), 0) hasRenewal,
	        GROUP_CONCAT(DISTINCT wcs.renewalDesc SEPARATOR "\r\n") AS renewalDesc,
	        IFNULL(MAX(wcs.hasLiscense), 0) hasLiscense,
            GROUP_CONCAT(DISTINCT wcs.liscenseDesc SEPARATOR "\r\n") AS liscenseDesc
	    </isNotEqual>
	    , slm.`memberCode` as salerCode 
        , CONCAT(slm.`memberCode`, "-", REPLACE(slm.`memberName`, CONCAT(slm.`memberCode`, "-"), "")) AS salerName
        , slm.`phoneNum` as salerContact  
    from pm_project_warranty_callback AS `pwc`
    <isNotEqual property="hideQuesnaire" compareValue="true">
	    LEFT JOIN projectWarrantyCallback_tempQuesnaireResultTable qrt 
	        ON qrt.quesnaireResultHeaderId = pwc.`quesnaireId` 
    </isNotEqual>
    <isNotEqual property="hideWarranty" compareValue="true">
        LEFT JOIN pm_project_header ph
	        ON ph.projectId = pwc.projectId
	    LEFT JOIN `pm_project_group_relationship` pgr 
	        ON ph.`projectCode` = pgr.`projectCode` 
	    LEFT JOIN `pm_project_contract` pc 
	        ON pc.`projectGroupCode` = pgr.`projectGroupCode` 
	    LEFT JOIN `view_warranty_contract_state` wcs 
	        ON wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
    </isNotEqual>
    LEFT JOIN pm_project_member slm
	        ON slm.`projectId` = pwc.`projectId`
	            AND slm.projectType = "10"
	            AND slm.`memberRole` = "10"
	            AND slm.`effectiveTo` is null
    LEFT JOIN fnd_basic_data ssfs1 
            ON ssfs1.dataTypeCode = '15'
                AND ssfs1.basicDataId = pwc.serviceImpl
                AND ssfs1.effectiveTo IS NULL 
    LEFT JOIN fnd_department d 
	        ON d.`departmentNum` = pwc.`officeCode`
	LEFT JOIN fnd_company c 
            ON c.id = pwc.compId
	<dynamic prepend="where">
		<include refid="sql_projectWarrantyCallback_model_where_selective" />
	</dynamic>
	<isNotEqual property="hideWarranty" compareValue="true">
		GROUP BY pwc.id
	</isNotEqual>
	<dynamic prepend="having">
		<isNotEqual property="hideWarranty" compareValue="true">
			<isNotEmpty property="model">
				<isNotEmpty prepend="AND" property="model.hasRenewal"> hasRenewal = #model.hasRenewal# </isNotEmpty>
				<isNotEmpty prepend="AND" property="model.hasLiscense"> hasLiscense = #model.hasLiscense# </isNotEmpty>
			</isNotEmpty>
		</isNotEqual>
	</dynamic>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:561 | fragment=sql_select_projectWarranty
SELECT 
        ph.`projectId`, ph.`projectCode`, ph.`projectName`, ph.projectType,
        ph.`column001` as officeCode, fd.`departmentName` as officeName, c.name as companyName, c.abbr as companyAbbr,
        <!-- ph.column004 AS marketName, ph.column005 AS systemName, 
	    ph.column006 AS expendName, ph.column007 AS industryName, -->
	    ph.column005 AS industryName,
	    ph.column013 AS finalCustomerName, ph.compId,
        GROUP_CONCAT(DISTINCT pm.`memberName` order by pm.phoneNum) as customer1, GROUP_CONCAT(DISTINCT pm.`phoneNum` order by pm.phoneNum) as customerContact1,
		GROUP_CONCAT(DISTINCT ppfs.linkmanName order by ppfs.linkmanTel) as customer2, GROUP_CONCAT(DISTINCT ppfs.`linkmanTel` order by ppfs.linkmanTel) as customerContact2,
		
		GROUP_CONCAT(DISTINCT slm.`memberCode` order by slm.memberCode) as salerCode, 
		GROUP_CONCAT(DISTINCT CONCAT(slm.`memberCode`, "-", REPLACE(slm.`memberName`, CONCAT(slm.`memberCode`, "-"), "")) order by slm.memberCode) AS salerName,
		GROUP_CONCAT(DISTINCT slm.`phoneNum` order by slm.memberCode) as salerContact,
		
		prp.`partyName` as agentChannel,
		IFNULL(ph.column012, ph.columno12_readonly) AS serviceImpl, 
		IFNULL(IFNULL(ssfs1.basicDataName, ssfs2.basicDataName), '未指定') AS serviceName,
        GROUP_CONCAT(DISTINCT pc.contractNo) as contractNos,
        IFNULL(IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))), 1) warrantyStatus, 
        IFNULL(IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
            )
        ), '维保外') AS warrantyStatusName,
        MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
        IFNULL(MAX(wcs.`warrantyGrade`), 0) warrantyGrade, 
        IFNULL(IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
            )
        ), '无维保') AS warrantyGradeName,
        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
        <!--
        IF(DATE(NOW()) >= MIN(wcs.`warrantyGradeStartTime`) AND MAX(wcs.`warrantyGradeEndTime`) >= DATE(NOW()), "true", "false") AS warrantyGradeEnable,
        IFNULL(MAX(wcs.`wafService`), 0) wafService,
        IF(MAX(wcs.`wafService`) = 1, "策略调优服务", NULL) AS  wafServiceName,
        MIN(wcs.`wafServiceStartTime`) wafServiceStartTime,
        MAX(wcs.`wafServiceEndTime`) wafServiceEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`wafServiceStartTime`) AND MAX(wcs.`wafServiceEndTime`) >= DATE(NOW()), "true", "false") AS wafServiceEnable,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.itemDesc, "(", DATE(wcs.`wafServiceStartTime`), "~", DATE(wcs.`wafServiceEndTime`), ")") SEPARATOR "\r\n") AS warrantyServiceDesc, -->
    	pwc.id AS latestId,
    	pwc.`renewalIntention` as latestRenewalIntention,
    	pwc.`customInfo` ->> '$$.phoneAnswerState' AS latestPhoneAnswerState,
    	pwc.`customInfo` ->> '$$.phoneAnswerStateName' AS latestPhoneAnswerStateName,
    	pwc.`callbackTime` as latestCallbackTime,
    	pwc.`nextCallbackTime` as latestNextCallbackTime,
    	pwc.`remark` as latestRemark,
    	pwc.customInfo AS customInfo,
    	IFNULL(mpwc.callbackCount, 0) callbackCount,
    	IFNULL(mpwc.renewalIntentionCount, 0) renewalIntentionCount,
    	IFNULL(MAX(wcs.hasRenewal), 0) hasRenewal,
		GROUP_CONCAT(DISTINCT renewalDesc SEPARATOR "\r\n") AS renewalDesc,
		IFNULL(MAX(wcs.hasLiscense), 0) hasLiscense,
        GROUP_CONCAT(DISTINCT liscenseDesc SEPARATOR "\r\n") AS liscenseDesc
    FROM
        `pm_project_header` ph 
        LEFT JOIN `pm_project_group_relationship` pgr 
            ON ph.`projectCode` = pgr.`projectCode` 
        <!-- LEFT JOIN `pm_project_group` pg
            ON pg.`projectGroupCode` = pgr.`projectGroupCode`  -->
        LEFT JOIN `pm_project_contract` pc 
            ON pc.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `view_warranty_contract_state` wcs 
            ON wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
        LEFT JOIN 
	        (SELECT 
	            projectId, max(id) as id, count(id) as callbackCount, count(IF(renewalIntention = 1, 1, null)) as renewalIntentionCount 
	        FROM
	            pm_project_warranty_callback 
	        GROUP BY projectId) mpwc 
	        ON mpwc.projectId = ph.projectId 
	    LEFT JOIN `pm_project_warranty_callback` pwc 
	        ON pwc.`projectId` = ph.`projectId` 
	        AND mpwc.id = pwc.id 
        LEFT JOIN pm_project_member pm
		    ON pm.`projectId` = ph.`projectId`
		        AND pm.projectType = ph.projectType
				AND pm.`memberRole` = "60"
				AND pm.`effectiveTo` is null
		LEFT JOIN pm_project_member slm
            ON slm.`projectId` = ph.`projectId`
                AND slm.projectType = ph.projectType
                AND slm.`memberRole` = "10"
                AND slm.`effectiveTo` is null
		LEFT JOIN `pm_project_related_party` prp
		    ON prp.`projectId` = ph.`projectId`
				AND prp.`partyRole` = "30"
				AND prp.`effectiveTo` is null
		LEFT JOIN `pm_project_property_from_sms` ppfs
	     	ON ppfs.`projectCode` = pgr.`smsProjectCode`
		LEFT JOIN fnd_basic_data ssfs1 
            ON ssfs1.dataTypeCode = '15'
                AND ssfs1.basicDataId = ph.column012 
                AND ssfs1.effectiveTo IS NULL 
        LEFT JOIN fnd_basic_data ssfs2 
            ON ssfs2.dataTypeCode = '15' 
                AND ssfs2.basicDataId = ph.columno12_readonly 
                AND ssfs2.effectiveTo IS NULL 
        LEFT JOIN fnd_department fd
            ON fd.`departmentNum` = ph.`column001`
        LEFT JOIN fnd_company c force key(primary)
            ON c.id = ph.compId
    WHERE ph.`effectiveTo` is null

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:671 | fragment=sql_projectWarranty_where
<isNotEmpty prepend="AND" property="projectId"> ph.projectId = #projectId# </isNotEmpty>
    <isNotEmpty prepend="AND" property="projectCode"> ph.projectCode like concat("%", #projectCode#, "%") </isNotEmpty>
    <isNotEmpty prepend="AND" property="projectName"> ph.projectName like concat("%", #projectName#, "%") </isNotEmpty>
    <isNotEmpty prepend="AND" property="officeCode"> ph.column001 = #officeCode# </isNotEmpty>
    <isNotEmpty prepend="AND" property="industryName"> ph.column005 like concat("%", #industryName#, "%") </isNotEmpty>
    <isNotEmpty prepend="AND" property="agentChannel"> prp.partyName like concat("%", #agentChannel#, "%") </isNotEmpty>
    <isNotEmpty prepend="AND" property="finalCustomerName"> ph.column013 like concat("%", #finalCustomerName#, "%") </isNotEmpty>
    <isNotNull prepend="AND" property="customerNameNotFuzzy"> #customerNameNotFuzzy# = TRIM(IFNULL(ph.column013, "")) </isNotNull>
    <isNotEmpty prepend="AND" property="serviceImpl"> #serviceImpl# = IFNULL(ph.column012, ph.columno12_readonly) </isNotEmpty>
    <isNotEmpty prepend="AND" property="renewalIntentionInt"> 
        <isEqual property="renewalIntentionInt" compareValue="-1"> pwc.renewalIntention is null </isEqual>
        <isNotEqual property="renewalIntentionInt" compareValue="-1"> pwc.renewalIntention = #renewalIntentionInt# </isNotEqual>
    </isNotEmpty>
    <isNotEmpty property="customInfo">
        <iterate property="customInfo.iterator" >
            <isNotEmpty prepend="AND" property="customInfo.iterator[].value">
	            #customInfo.iterator[].value# = pwc.customInfo ->> '$$.$customInfo.iterator[].key$'
            </isNotEmpty>
        </iterate>
	    <!-- <isNotEmpty prepend="AND" property="customInfo.phoneAnswerState"> 
	        <isEqual property="customInfo.phoneAnswerState" compareValue="0"> pwc.customInfo ->> '$$.phoneAnswerState' is null </isEqual>
	        <isNotEqual property="customInfo.phoneAnswerState" compareValue="0"> #customInfo.phoneAnswerState# = pwc.customInfo ->> '$$.phoneAnswerState' </isNotEqual>
	    </isNotEmpty> -->
    </isNotEmpty>
    <isNotEmpty prepend="AND" property="compId"> ph.compId = #compId# </isNotEmpty>
    <isNotEmpty prepend="AND" property="warrantyEndTimeStart"> wcs.warrantyEndTime >= #warrantyEndTimeStart# </isNotEmpty>
    <isNotEmpty prepend="AND" property="warrantyEndTimeEnd"> #warrantyEndTimeEnd# >= wcs.warrantyEndTime </isNotEmpty>
    <isNotEmpty prepend="AND" property="processStartTime"> pwc.callbackTime >= #processStartTime# </isNotEmpty>
    <isNotEmpty prepend="AND" property="processEndTime"> #processEndTime# >= pwc.callbackTime </isNotEmpty>
    <isNotEmpty prepend="AND" property="callbackTimeStart"> pwc.callbackTime >= #callbackTimeStart# </isNotEmpty>
    <isNotEmpty prepend="AND" property="callbackTimeEnd"> #callbackTimeEnd# >= pwc.callbackTime </isNotEmpty>
    <isNotEmpty prepend="AND" property="nextCallbackTimeStart"> pwc.nextCallbackTime >= #nextCallbackTimeStart# </isNotEmpty>
    <isNotEmpty prepend="AND" property="nextCallbackTimeEnd"> #nextCallbackTimeEnd# >= pwc.nextCallbackTime </isNotEmpty>
    <isNotEmpty prepend="AND" property="salerSearch">
        (
            slm.memberCode = #salerSearch#
            or slm.memberName like concat("%", #salerSearch#, "%") 
            or slm.phoneNum like concat("%", #salerSearch#, "%") 
        )
    </isNotEmpty>
    GROUP BY ph.projectCode
    <dynamic prepend="having">
        <isNotEmpty prepend="AND" property="contractNos"> contractNos like concat("%", #contractNos#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="customerSearch">
        (
            customer1 like concat("%", #customerSearch#, "%") 
            or customer2 like concat("%", #customerSearch#, "%") 
            or customerContact1 like concat("%", #customerSearch#, "%") 
            or customerContact2 like concat("%", #customerSearch#, "%")
        )
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="customerContactSearch">
        (
            customerContact1 like concat("%", #customerContactSearch#, "%") 
            or customerContact2 like concat("%", #customerContactSearch#, "%") 
        )
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="hasCallback"> callbackCount > 0 </isNotEmpty>
        <isNotEmpty prepend="AND" property="hasRenewal"> hasRenewal = #hasRenewal# </isNotEmpty>
        <isNotEmpty prepend="AND" property="hasLiscense"> hasLiscense = #hasLiscense# </isNotEmpty>
    </dynamic>

-- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-work-config.xml:528 | fragment=sqlFromSelfHistoryTask
FROM (
            SELECT a.`projectCode`, a.`projectName`, '项目闭环' AS `NAME_`, '项目人员回访' AS taskName, c.evaluationPeopleId AS `applyBy`
                , c.evaluationPeopleName AS applyName, a.evaluationPeopleName AS assigneeName, a.evaluationTime AS assigneeTime
                , a.evaluationResult AS RESULT, a.projectId
                , t1.column001 AS officeCode
            FROM pm_cl_evaluation_header a
                LEFT JOIN pm_cl_evaluation_header c ON a.applyHeaderId = c.id
                LEFT JOIN pm_project_header t1 ON a.projectId = t1.projectId
            WHERE a.evaluationPeopleId = #assignee# AND a.status = 1
            <isNotEmpty prepend="and" property="taskQueryParam.projectName"><![CDATA[ t1.projectName like CONCAT('%', #taskQueryParam.projectName#, '%')]]></isNotEmpty>
            <isNotEmpty prepend="and" property="taskQueryParam.officeCode"><![CDATA[ t1.column001 = #taskQueryParam.officeCode#]]></isNotEmpty>
            UNION ALL
            <!-- 回访流程已办理任务 -->
            SELECT t3.`projectCode` ,t3.`projectName` ,t4.`NAME_` ,'项目人员回访' AS taskName,t1.`applyBy` , 
                t5.`realName` AS applyName, t6.`realName`  AS assigneeName,t2.`assigneeTime` , t2.`result` ,t1.`projectId`
                , t3.column001 AS officeCode
            FROM fnd_act_hi_comment t2
            LEFT JOIN pm_cl_callback t1 ON t1.`instId` = t2.`instId`
            LEFT JOIN pm_project_header t3 ON t1.`projectId` = t3.`projectId`
            LEFT JOIN act_re_procdef t4 ON t2.`procdefKey` = t4.`KEY_` 
            LEFT JOIN fnd_user_info t5 ON t1.`applyBy` = t5.`username` AND t5.`effectiveTo` IS NULL 
            LEFT JOIN fnd_user_info t6 ON t2.`assignee` = t6.`username` AND t6.`effectiveTo` IS NULL
            WHERE t2.`assignee` = #assignee# and t2.procdefKey = 'CallBack'
            <isNotEmpty prepend="and" property="taskQueryParam.projectName"><![CDATA[ t3.projectName like CONCAT('%', #taskQueryParam.projectName#, '%')]]></isNotEmpty>
            <isNotEmpty prepend="and" property="taskQueryParam.officeCode"><![CDATA[ t3.column001 = #taskQueryParam.officeCode#]]></isNotEmpty>
        ) t
        LEFT JOIN fnd_department t2 ON t.officeCode = t2.departmentNum AND t2.`status` = 1
        <!-- 最终客户 -->
        LEFT JOIN ( 
            SELECT m.projectId
                , REPLACE(REPLACE(REPLACE(GROUP_CONCAT(CONCAT_WS('-', trim(m.memberName), trim(m.phoneNum)) ORDER BY id), '\t', ''), '\r', ''), '\n', '') memberName
                <!-- , GROUP_CONCAT(m.memberName ORDER BY id) AS memberName, GROUP_CONCAT(m.phoneNum ORDER BY id) AS memberPhone -->
            FROM pm_project_member m
            WHERE m.effectiveTo IS NULL
                AND m.memberRole = '60'
            GROUP BY m.projectId
        ) fc ON t.projectId = fc.projectId
        <dynamic prepend="WHERE">
            <isNotEmpty prepend="and" property="taskQueryParam.projectName"><![CDATA[ t.projectName like CONCAT('%', #taskQueryParam.projectName#, '%')]]></isNotEmpty>
            <isNotEmpty prepend="and" property="taskQueryParam.officeCode"><![CDATA[ t.officeCode = #taskQueryParam.officeCode#]]></isNotEmpty>
            <isNotEmpty prepend="and" property="taskQueryParam.projectCustomer"><![CDATA[ fc.memberName like CONCAT('%', #taskQueryParam.projectCustomer#, '%') ]]> </isNotEmpty>
        </dynamic>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/ehr/mapping/EHRLoginAccountMapper.xml:13 | fragment=Base_Column_List
id, title, account, empID, workNo, name, isDisabled

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/ehr/mapping/EhrDepartmentMapper.xml:30 | fragment=Base_Column_List
depID, depCode, depName, depAbbr, compID, adminID, depGrade, depType, depProperty, 
    depCost, director, director2, depEmp, depNum, effectDate, xOrder, isDisabled, disabledDate, 
    remark, depCustom1, depCustom2, depCustom3, depCustom4, depCustom5

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/ehr/mapping/EmployeeMapper.xml:37 | fragment=Base_Column_List
empID, workNo, name, eName, compID, depID, jobID, reportTo, wfreportTo, empStatus, 
    jobStatus, empType, joinDate, workBeginDate, jobBeginDate, pracBeginDate, pracEndDate, 
    probBeginDate, probEndDate, leaveDate, gender, email, mobile, officePhone, remark, 
    disabled, empCustom1, empCustom2, empCustom3, empCustom4, empCustom5

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:42 | fragment=Base_Column_List
`id`, `projectId`, `projectType`, `projectCode`, `projectName`, `contractNo`, `officeCode`, 
    `type`, `category`, `subCategory`, `processTime`, `processDesc`, `processStep`, `remainProblem`, 
    `customerInteraction`, `transitHour`, `processHour`, `itemModel`, `softVersion`, 
    `enabledFeatures`, `customTos`, `customCcs`, `projectExecutionState`, `hasReport`, 
    `quesnaireId`, `deliverFileIds`, `remark`, `isReported`, `qualityFactor`, `status`, 
    `disabled`, `createTime`, `createBy`, `updateTime`, `updateBy`,
    `customInfo`

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:462 | fragment=sql_where_selective
<if test="id != null">
		dr.`id` = #{id, jdbcType=INTEGER} AND
	</if>
	<if test="projectId != null">
		dr.`projectId` = #{projectId, jdbcType=INTEGER} AND
	</if>
	<if test="projectType != null and '' != projectType">
		dr.`projectType` = #{projectType, jdbcType=VARCHAR} AND
	</if>
	<if test="projectCode != null and '' != projectCode">
		dr.`projectCode` = #{projectCode, jdbcType=VARCHAR} AND
	</if>
	<if test="projectName != null and '' != projectName">
		dr.`projectName` = #{projectName, jdbcType=VARCHAR} AND
	</if>
	<if test="contractNo != null and '' != contractNo">
		dr.`contractNo` = #{contractNo, jdbcType=VARCHAR} AND
	</if>
	<if test="officeCode != null and '' != officeCode">
		dr.`officeCode` = #{officeCode, jdbcType=VARCHAR} AND
	</if>
	<if test="type != null and '' != type">
		dr.`type` = #{type, jdbcType=VARCHAR} AND
	</if>
	<if test="category != null and '' != category">
		dr.`category` = #{category, jdbcType=VARCHAR} AND
	</if>
	<if test="subCategory != null and '' != subCategory">
		dr.`subCategory` = #{subCategory, jdbcType=VARCHAR} AND
	</if>
	<if test="processTime != null">
		dr.`processTime` = #{processTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="processDesc != null and '' != processDesc">
		dr.`processDesc` = #{processDesc, jdbcType=VARCHAR} AND
	</if>
	<if test="processStep != null and '' != processStep">
		dr.`processStep` = #{processStep, jdbcType=VARCHAR} AND
	</if>
	<if test="remainProblem != null and '' != remainProblem">
		dr.`remainProblem` = #{remainProblem, jdbcType=VARCHAR} AND
	</if>
	<if test="customerInteraction != null and '' != customerInteraction">
		dr.`customerInteraction` = #{customerInteraction, jdbcType=VARCHAR} AND
	</if>
	<if test="transitHour != null">
		dr.`transitHour` = #{transitHour, jdbcType=REAL} AND
	</if>
	<if test="processHour != null">
		dr.`processHour` = #{processHour, jdbcType=REAL} AND
	</if>
	<if test="itemModel != null and '' != itemModel">
		dr.`itemModel` = #{itemModel, jdbcType=VARCHAR} AND
	</if>
	<if test="softVersion != null and '' != softVersion">
		dr.`softVersion` = #{softVersion, jdbcType=VARCHAR} AND
	</if>
	<if test="enabledFeatures != null and '' != enabledFeatures">
		dr.`enabledFeatures` = #{enabledFeatures, jdbcType=VARCHAR} AND
	</if>
	<if test="customTos != null and '' != customTos">
		dr.`customTos` = #{customTos, jdbcType=VARCHAR} AND
	</if>
	<if test="customCcs != null and '' != customCcs">
		dr.`customCcs` = #{customCcs, jdbcType=VARCHAR} AND
	</if>
	<if test="projectExecutionState != null and '' != projectExecutionState">
		dr.`projectExecutionState` = #{projectExecutionState, jdbcType=VARCHAR} AND
	</if>
	<if test="hasReport != null">
		dr.`hasReport` = #{hasReport, jdbcType=BIT} AND
	</if>
	<if test="quesnaireId != null">
		dr.`quesnaireId` = #{quesnaireId, jdbcType=INTEGER} AND
	</if>
	<if test="deliverFileIds != null and '' != deliverFileIds">
		dr.`deliverFileIds` = #{deliverFileIds, jdbcType=VARCHAR} AND
	</if>
	<if test="remark != null and '' != remark">
		dr.`remark` = #{remark, jdbcType=VARCHAR} AND
	</if>
	<if test="isReported != null">
		dr.`isReported` = #{isReported, jdbcType=BIT} AND
	</if>
	<if test="qualityFactor != null">
		dr.`qualityFactor` = #{qualityFactor, jdbcType=REAL} AND
	</if>
	<if test="status != null and '' != status">
		dr.`status` = #{status, jdbcType=VARCHAR} AND
	</if>
	<if test="disabled != null">
		dr.`disabled` = #{disabled, jdbcType=BIT} AND
	</if>
	<if test="createTime != null">
		dr.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="createBy != null and '' != createBy">
		dr.`createBy` = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		dr.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		dr.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="customInfo != null and '' != customInfo">
		<!-- dr.`customInfo` = #{customInfo, jdbcType=JSON} AND -->
		<foreach collection="customInfo" item="value" index="key">
		   dr.`customInfo` -> '$.${key}' like concat("%", #{value}, "%") AND
	    </foreach>
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:575 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.id != null">
			dr.`id` = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.projectId != null">
			dr.`projectId` = #{model.projectId, jdbcType=INTEGER} AND
		</if>
		<if test="model.projectType != null and '' != model.projectType">
			dr.`projectType` = #{model.projectType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectCode != null and '' != model.projectCode">
			dr.`projectCode` like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.projectName != null and '' != model.projectName">
			dr.`projectName` like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.contractNo != null and '' != model.contractNo">
			dr.`contractNo` like concat("%", #{model.contractNo, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.officeCode != null and '' != model.officeCode">
			dr.`officeCode` = #{model.officeCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.type != null and '' != model.type">
			dr.`type` = #{model.type, jdbcType=VARCHAR} AND
		</if>
		<if test="model.category != null and '' != model.category">
			dr.`category` = #{model.category, jdbcType=VARCHAR} AND
		</if>
		<if test="model.subCategory != null and '' != model.subCategory">
			dr.`subCategory` = #{model.subCategory, jdbcType=VARCHAR} AND
		</if>
		<if test="model.processTime != null">
			dr.`processTime` = #{model.processTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.processDesc != null and '' != model.processDesc">
			dr.`processDesc` = #{model.processDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.processStep != null and '' != model.processStep">
			dr.`processStep` = #{model.processStep, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remainProblem != null and '' != model.remainProblem">
			dr.`remainProblem` = #{model.remainProblem, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customerInteraction != null and '' != model.customerInteraction">
			dr.`customerInteraction` = #{model.customerInteraction, jdbcType=VARCHAR} AND
		</if>
		<if test="model.transitHour != null">
			dr.`transitHour` = #{model.transitHour, jdbcType=REAL} AND
		</if>
		<if test="model.processHour != null">
			dr.`processHour` = #{model.processHour, jdbcType=REAL} AND
		</if>
		<if test="model.itemModel != null and '' != model.itemModel">
			dr.`itemModel` like concat("%", #{model.itemModel, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.softVersion != null and '' != model.softVersion">
			dr.`softVersion` = #{model.softVersion, jdbcType=VARCHAR} AND
		</if>
		<if test="model.enabledFeatures != null and '' != model.enabledFeatures">
			dr.`enabledFeatures` = #{model.enabledFeatures, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customTos != null and '' != model.customTos">
			dr.`customTos` = #{model.customTos, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customCcs != null and '' != model.customCcs">
			dr.`customCcs` = #{model.customCcs, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectExecutionState != null and '' != model.projectExecutionState">
			dr.`projectExecutionState` = #{model.projectExecutionState, jdbcType=VARCHAR} AND
		</if>
		<if test="model.hasReport != null">
			dr.`hasReport` = #{model.hasReport, jdbcType=BIT} AND
		</if>
		<if test="model.quesnaireId != null">
			dr.`quesnaireId` = #{model.quesnaireId, jdbcType=INTEGER} AND
		</if>
		<if test="model.deliverFileIds != null and '' != model.deliverFileIds">
			dr.`deliverFileIds` = #{model.deliverFileIds, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			dr.`remark` = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.isReported != null">
			dr.`isReported` = #{model.isReported, jdbcType=BIT} AND
		</if>
		<if test="model.qualityFactor != null">
			dr.`qualityFactor` = #{model.qualityFactor, jdbcType=REAL} AND
		</if>
		<if test="model.status != null and '' != model.status">
			dr.`status` = #{model.status, jdbcType=VARCHAR} AND
		</if>
		<if test="model.disabled != null">
			dr.`disabled` = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.createTime != null">
			dr.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			dr.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			dr.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			dr.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customInfo != null and '' != model.customInfo">
			<!-- dr.`customInfo` = #{model.customInfo, jdbcType=JSON} AND -->
			<foreach collection="model.customInfo" item="value" index="key">
			   dr.`customInfo` -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.processStartTime != null">
			dr.`processTime` >= #{model.processStartTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.processEndTime != null">
			<![CDATA[ dr.`processTime` <= #{model.processEndTime, jdbcType=TIMESTAMP} AND ]]>
		</if>
		<!-- <if test="model.projectTypes != null">
			FIND_IN_SET(dr.`projectType`, CONCAT(#{model.projectTypes, jdbcType=VARCHAR}, ",30,40")) AND 
		</if>
		<if test="model.officeCodes != null">
			FIND_IN_SET(dr.`officeCode`, #{model.officeCodes, jdbcType=VARCHAR}) AND 
		</if> -->
		<if test="model.ids != null">
			<foreach collection="model.ids" item="item" open="dr.`id` in (" close=") AND" separator=",">
				 #{item}
			</foreach>
		</if>
		<if test="model.categorys != null">
			<foreach collection="model.categorys" item="item" open="dr.`category` in (" close=") AND" separator=",">
				 #{item}
			</foreach>
		</if>
		<if test="model.subCategorys != null">
			<foreach collection="model.subCategorys" item="item" open="dr.`subCategory` in (" close=") AND" separator=",">
				 #{item}
			</foreach>
		</if>
		<if test="model.transferProjectFlag == null">
			dr.customInfo ->> "$.transferProjectFlag" is null AND
		</if>
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:720 | fragment=sql_fuzzy_search
<if test="fuzzySearch == true and fuzzy != ''">
		(
			dr.`projectType` like CONCAT("%", #{fuzzy}, "%") or
			dr.`projectCode` like CONCAT("%", #{fuzzy}, "%") or
			dr.`projectName` like CONCAT("%", #{fuzzy}, "%") or
			dr.`contractNo` like CONCAT("%", #{fuzzy}, "%") or
			dr.`officeCode` like CONCAT("%", #{fuzzy}, "%") or
			dr.`type` like CONCAT("%", #{fuzzy}, "%") or
			dr.`category` like CONCAT("%", #{fuzzy}, "%") or
			dr.`subCategory` like CONCAT("%", #{fuzzy}, "%") or
			dr.`processDesc` like CONCAT("%", #{fuzzy}, "%") or
			dr.`processStep` like CONCAT("%", #{fuzzy}, "%") or
			dr.`remainProblem` like CONCAT("%", #{fuzzy}, "%") or
			dr.`customerInteraction` like CONCAT("%", #{fuzzy}, "%") or
			dr.`itemModel` like CONCAT("%", #{fuzzy}, "%") or
			dr.`softVersion` like CONCAT("%", #{fuzzy}, "%") or
			dr.`enabledFeatures` like CONCAT("%", #{fuzzy}, "%") or
			dr.`status` like CONCAT("%", #{fuzzy}, "%") or
			dr.`createBy` like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:743 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:750 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
			<include refid="sql_fuzzy_search" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DailyReportMapper.xml:758 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:48 | fragment=Base_Column_List
`id`, `dispatchName`, `dispatchNo`, `dispatchSeq`, `contractNos`, `projectIds`, `type`, 
    `state`, `peopleNum`, `callbackState`, `facilitatorId`, `facilitatorCode`, `facilitatorName`, 
    `bankInfo`, `bankAccount`, `officeCode`, `profitDepCode`, `dutyPerson`, `officeDutyPerson`, 
    `isAccrued`, `isInvoiced`, `dispatchAmount`, `prepaidInfo`, `prepaidRule`, `acceptanceInfo`, 
    `reason`, `remark`, `dispatchTime`, `smsProjectCode`, `smsSubmitTime`, `smsProjectAmount`, 
    `smsAfProjectAmount`, `effectiveFrom`, `effectiveTo`, `disabled`, `dispatched`, `settled`, 
    `createBy`, `createTime`, `updateBy`, `updateTime`,
    `customInfo`

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1126 | fragment=selectDispatchVOWithAmount
select 
	    JSON_SET(h.customInfo, '$.projectProgress', IFNULL(ph.customInfo ->> "$.projectProgress", 0), '$.projectStateName', IFNULL(phsd.`basicDataName`, "")) as customInfo,
	    h.*, d.`departmentName` as officeName, d1.`departmentName` as profitDepName, bd.`basicDataName` as stateName, bd1.`basicDataName` as callbackStateName, bd2.basicDataName as typeName, concat(h.createBy, "-", ui.`realName`) as createName, 
	    sum(sc.`collected_money_amount`) as collectedAmount,
	    sum(sc.`delivered_money_amount`) as deliveredAmount,
	    sum(sc.`contract_money_amount`) as contractAmount,
	    group_concat(sc.`contract_num`) as collectContractNos,
	    ROUND(sum(sc.`collected_money_amount`) / sum(sc.`contract_money_amount`), 4) as collectedRatio, 
	    ds.settledAmount
	    <!-- ,ph.customInfo ->> "$.projectProgress" as `customInfo.projectProgress`,
	    phsd.`basicDataName` as `customInfo.projectStateName` -->
	    ,ROUND(ds.settledAmount / replace(h.dispatchAmount, ",","") * 100, 2) as settledRatio
	from
	    <!-- <![CDATA[
	    (select 
	        h.*, substring_index(
	            substring_index(h.contractNos, ',', b.id + 1), ',', - 1
	        ) as contractNo 
	    from
	        `pm_dispatch_project_header` h 
	        left join FIND_IN_SET_HELP b 
	            on b.id < (
	                length(h.contractNos) - length(replace(h.contractNos, ',', '')) + 1
	            )) h 
        ]]>
	    left join `sms_ofst_contract_head_sap` sc 
	        on h.`smsProjectCode` = sc.`projectCode` 
	        on sc.`contract_num` = h.`contractNo`  -->
	    pm_dispatch_project_header h
    	left join `sms_ofst_contract_head_sap` sc 
        	on FIND_IN_SET(sc.`contract_num`, h.`contractNos`)
       	left join `pm_project` ph
	    	on ph.projectId = h.projectIds
	    left join 
	        (select 
	            dispatchId, sum(replace(ds.`amount`, ",", "")) as settledAmount 
	        from
	            `pm_dispatch_project_settlement` ds 
	        where ds.`disabled` = false 
	        group by ds.`dispatchId`) ds 
	        on ds.`dispatchId` = h.`id` 
	    left join fnd_department d 
	        on h.`officeCode` = d.`departmentNum` 
	    left join fnd_department d1 
	        on h.`profitDepCode` = d1.`departmentNum` 
	    left join `fnd_basic_data` bd 
	        on h.`state` = bd.`basicDataId` 
	        and bd.`dataTypeCode` = 'dispatchState' 
	    left join `fnd_basic_data` bd1 
	        on h.`callbackState` = bd1.`basicDataId` 
	        and bd1.`dataTypeCode` = 'dispatchCbState' 
	    left join `fnd_basic_data` bd2 
	        on h.`type` = bd2.`basicDataId` 
	        and bd2.`dataTypeCode` = 'dispatchType' 
		LEFT JOIN fnd_basic_data phsd 
	        ON phsd.`dataTypeCode` = concat(ph.`projectType`, "_projectState") 
	        AND phsd.`basicDataId` = ph.`projectState` 
	    left join t_user u 
	        on u.`user_name` = h.`createBy` 
	    left join t_user_info ui 
	        on ui.`user_id` = u.`user_id`

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1190 | fragment=pageWhereSql
<where>
		<trim prefixOverrides="and |or" suffixOverrides="and |or">
			<if test="model != null ">
				<if test="model.id != null and '' != model.id">
					h.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.dispatchName != null and '' != model.dispatchName">
					h.dispatchName like concat("%", #{model.dispatchName, jdbcType=VARCHAR}, "%") AND
				</if>
				<if test="model.dispatchNo != null and '' != model.dispatchNo">
					h.dispatchNo like concat("%", #{model.dispatchNo, jdbcType=VARCHAR}, "%") AND
				</if>
				<if test="model.dispatchSeq != null and '' != model.dispatchSeq">
					h.dispatchSeq like concat("%", #{model.dispatchSeq, jdbcType=VARCHAR}, "%") AND
				</if>
				<if test="model.contractNos != null and '' != model.contractNos">
					(h.contractNos like concat("%", #{model.contractNos}, "%") or h.dispatchNo like concat("%", #{model.contractNos}, "%")) AND
				</if>
				<if test="model.projectIds != null and '' != model.projectIds">
					h.projectIds = #{model.projectIds, jdbcType=VARCHAR} AND
				</if>
				<if test="model.type != null and '' != model.type">
					h.type = #{model.type, jdbcType=VARCHAR} AND
				</if>
				<if test="model.state != null and '' != model.state">
					h.state = #{model.state, jdbcType=INTEGER} AND
				</if>
				<if test="model.peopleNum != null and '' != model.peopleNum">
					h.peopleNum = #{model.peopleNum, jdbcType=INTEGER} AND
				</if>
				<if test="model.callbackState != null and '' != model.callbackState">
					h.callbackState = #{model.callbackState, jdbcType=INTEGER} AND
				</if>
				<if test="model.facilitatorId != null and '' != model.facilitatorId">
					h.facilitatorId = #{model.facilitatorId, jdbcType=INTEGER} AND
				</if>
				<if test="model.facilitatorCode != null and '' != model.facilitatorCode">
					h.facilitatorCode = #{model.facilitatorCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.facilitatorName != null and '' != model.facilitatorName">
					h.facilitatorName = #{model.facilitatorName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.bankInfo != null and '' != model.bankInfo">
					h.bankInfo = #{model.bankInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.bankAccount != null and '' != model.bankAccount">
					h.bankAccount = #{model.bankAccount, jdbcType=VARCHAR} AND
				</if>
				<if test="model.officeCode != null and '' != model.officeCode">
					h.officeCode = #{model.officeCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.profitDepCode != null and '' != model.profitDepCode">
					h.profitDepCode = #{model.profitDepCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.dutyPerson != null and '' != model.dutyPerson">
					h.dutyPerson = #{model.dutyPerson, jdbcType=VARCHAR} AND
				</if>
				<if test="model.officeDutyPerson != null and '' != model.officeDutyPerson">
					h.officeDutyPerson = #{model.officeDutyPerson, jdbcType=VARCHAR} AND
				</if>
				<if test="model.isAccrued != null">
					h.isAccrued = #{model.isAccrued, jdbcType=BIT} AND
				</if>
				<if test="model.isInvoiced != null">
					h.isInvoiced = #{model.isInvoiced, jdbcType=BIT} AND
				</if>
				<if test="model.dispatchAmount != null and '' != model.dispatchAmount">
					h.dispatchAmount = #{model.dispatchAmount, jdbcType=VARCHAR} AND
				</if>
				<if test="model.prepaidInfo != null and '' != model.prepaidInfo">
					h.prepaidInfo = #{model.prepaidInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.prepaidRule != null and '' != model.prepaidRule">
					h.prepaidRule = #{model.prepaidRule, jdbcType=VARCHAR} AND
				</if>
				<if test="model.acceptanceInfo != null and '' != model.acceptanceInfo">
					h.acceptanceInfo = #{model.acceptanceInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.reason != null and '' != model.reason">
					h.reason = #{model.reason, jdbcType=VARCHAR} AND
				</if>
				<if test="model.remark != null and '' != model.remark">
					h.remark = #{model.remark, jdbcType=VARCHAR} AND
				</if>
				<if test="model.dispatchTime != null">
					h.dispatchTime = #{model.dispatchTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.smsProjectCode != null and '' != model.smsProjectCode">
					<!-- h.smsProjectCode = #{model.smsProjectCode, jdbcType=VARCHAR} AND -->
					 h.smsProjectCode LIKE CONCAT("%", #{model.smsProjectCode}, "%") AND
				</if>
				<if test="model.smsSubmitTime != null">
					h.smsSubmitTime = #{model.smsSubmitTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.smsProjectAmount != null and '' != model.smsProjectAmount">
					h.smsProjectAmount = #{model.smsProjectAmount, jdbcType=VARCHAR} AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ h.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
				<if test="model.effectiveTo != null">
					<!-- h.effectiveTo = #{model.effectiveTo, jdbcType=TIMESTAMP} AND -->
					<![CDATA[ (h.`effectiveTo` is null or h.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.disabled != null">
					h.disabled = #{model.disabled, jdbcType=BIT} AND
				</if>
				<if test="model.dispatched != null">
					h.dispatched = #{model.dispatched, jdbcType=BIT} AND
				</if>
				<if test="model.settled != null">
					h.settled = #{model.settled, jdbcType=BIT} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					h.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					h.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					h.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					h.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.customInfo != null and '' != model.customInfo">
					h.customInfo = #{model.customInfo, jdbcType=OTHER} AND
				</if>
				<if test="model.projectId != null and '' != model.projectId">
					FIND_IN_SET(#{model.projectId}, h.projectIds) AND
				</if>
				<if test="model.projectId != null and '' != model.projectId">
					FIND_IN_SET(#{model.projectId}, h.projectIds) AND
				</if>
				<if test="model.officeCodes != null">
					FIND_IN_SET(h.officeCode, #{model.officeCodes}) AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					h.dispatchName like CONCAT("%", #{fuzzy}, "%") or
					h.dispatchNo like CONCAT("%", #{fuzzy}, "%") or
					h.dispatchSeq like CONCAT("%", #{fuzzy}, "%") or
					h.contractNos like CONCAT("%", #{fuzzy}, "%") or
					h.facilitatorName like CONCAT("%", #{fuzzy}, "%") or
					h.smsProjectCode like CONCAT("%", #{fuzzy}, "%") or
					h.smsProjectAmount like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1344 | fragment=pageLimitSql
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1356 | fragment=baseWhereSql
<where>
		<trim prefixOverrides="and |or" suffixOverrides="and |or">
			<if test="id != null">
				h.`id` = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="dispatchName != null and '' != dispatchName">
				h.`dispatchName` = #{dispatchName, jdbcType=VARCHAR} AND
			</if>
			<if test="dispatchNo != null and '' != dispatchNo">
				h.`dispatchNo` = #{dispatchNo, jdbcType=VARCHAR} AND
			</if>
			<if test="dispatchSeq != null and '' != dispatchSeq">
				h.`dispatchSeq` = #{dispatchSeq, jdbcType=VARCHAR} AND
			</if>
			<if test="contractNos != null and '' != contractNos">
				h.`contractNos` = #{contractNos, jdbcType=VARCHAR} AND
			</if>
			<if test="projectIds != null and '' != projectIds">
				h.`projectIds` = #{projectIds, jdbcType=VARCHAR} AND
			</if>
			<if test="type != null and '' != type">
				h.`type` = #{type, jdbcType=VARCHAR} AND
			</if>
			<if test="state != null">
				h.`state` = #{state, jdbcType=INTEGER} AND
			</if>
			<if test="peopleNum != null">
				h.`peopleNum` = #{peopleNum, jdbcType=INTEGER} AND
			</if>
			<if test="callbackState != null">
				h.`callbackState` = #{callbackState, jdbcType=INTEGER} AND
			</if>
			<if test="facilitatorId != null">
				h.`facilitatorId` = #{facilitatorId, jdbcType=INTEGER} AND
			</if>
			<if test="facilitatorCode != null and '' != facilitatorCode">
				h.`facilitatorCode` = #{facilitatorCode, jdbcType=VARCHAR} AND
			</if>
			<if test="facilitatorName != null and '' != facilitatorName">
				h.`facilitatorName` = #{facilitatorName, jdbcType=VARCHAR} AND
			</if>
			<if test="bankInfo != null and '' != bankInfo">
				h.`bankInfo` = #{bankInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="bankAccount != null and '' != bankAccount">
				h.`bankAccount` = #{bankAccount, jdbcType=VARCHAR} AND
			</if>
			<if test="officeCode != null and '' != officeCode">
				h.`officeCode` = #{officeCode, jdbcType=VARCHAR} AND
			</if>
			<if test="profitDepCode != null and '' != profitDepCode">
				h.`profitDepCode` = #{profitDepCode, jdbcType=VARCHAR} AND
			</if>
			<if test="dutyPerson != null and '' != dutyPerson">
				h.`dutyPerson` = #{dutyPerson, jdbcType=VARCHAR} AND
			</if>
			<if test="officeDutyPerson != null and '' != officeDutyPerson">
				h.`officeDutyPerson` = #{officeDutyPerson, jdbcType=VARCHAR} AND
			</if>
			<if test="isAccrued != null">
				h.`isAccrued` = #{isAccrued, jdbcType=BIT} AND
			</if>
			<if test="isInvoiced != null">
				h.`isInvoiced` = #{isInvoiced, jdbcType=BIT} AND
			</if>
			<if test="dispatchAmount != null and '' != dispatchAmount">
				h.`dispatchAmount` = #{dispatchAmount, jdbcType=VARCHAR} AND
			</if>
			<if test="prepaidInfo != null and '' != prepaidInfo">
				h.`prepaidInfo` = #{prepaidInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="prepaidRule != null and '' != prepaidRule">
				h.`prepaidRule` = #{prepaidRule, jdbcType=VARCHAR} AND
			</if>
			<if test="acceptanceInfo != null and '' != acceptanceInfo">
				h.`acceptanceInfo` = #{acceptanceInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="reason != null and '' != reason">
				h.`reason` = #{reason, jdbcType=VARCHAR} AND
			</if>
			<if test="remark != null and '' != remark">
				h.`remark` = #{remark, jdbcType=VARCHAR} AND
			</if>
			<if test="dispatchTime != null">
				h.`dispatchTime` = #{dispatchTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="dispatchYear != null">
				#{dispatchYear, jdbcType=INTEGER} = YEAR(h.`dispatchTime`) AND
			</if>
			<if test="smsProjectCode != null and '' != smsProjectCode">
				h.`smsProjectCode` = #{smsProjectCode, jdbcType=VARCHAR} AND
			</if>
			<if test="smsSubmitTime != null">
				h.`smsSubmitTime` = #{smsSubmitTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="smsProjectAmount != null and '' != smsProjectAmount">
				h.`smsProjectAmount` = #{smsProjectAmount, jdbcType=VARCHAR} AND
			</if>
			<if test="smsAfProjectAmount != null and '' != smsAfProjectAmount">
				h.`smsAfProjectAmount` = #{smsAfProjectAmount, jdbcType=VARCHAR} AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ h.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
			<if test="effectiveTo != null">
				<!-- h.effectiveTo = #{effectiveTo, jdbcType=TIMESTAMP} AND -->
				<![CDATA[ (h.`effectiveTo` is null or h.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="disabled != null">
				h.`disabled` = #{disabled, jdbcType=BIT} AND
			</if>
			<if test="dispatched != null">
				h.`dispatched` = #{dispatched, jdbcType=BIT} AND
			</if>
			<if test="settled != null">
				h.`settled` = #{settled, jdbcType=BIT} AND
			</if>
			<if test="createBy != null and '' != createBy">
				h.`createBy` = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				h.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				h.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				h.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="customInfo != null and '' != customInfo">
				h.`customInfo` = #{customInfo, jdbcType=OTHER} AND
			</if>
			<if test="officeCodes != null">
				FIND_IN_SET(h.officeCode, #{officeCodes}) AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1496 | fragment=sql_where_selective
<if test="id != null">
		h.`id` = #{id, jdbcType=INTEGER} AND
	</if>
	<if test="dispatchName != null and '' != dispatchName">
		h.`dispatchName` = #{dispatchName, jdbcType=VARCHAR} AND
	</if>
	<if test="dispatchNo != null and '' != dispatchNo">
		h.`dispatchNo` = #{dispatchNo, jdbcType=VARCHAR} AND
	</if>
	<if test="dispatchSeq != null and '' != dispatchSeq">
		h.`dispatchSeq` = #{dispatchSeq, jdbcType=VARCHAR} AND
	</if>
	<if test="contractNos != null and '' != contractNos">
		h.`contractNos` = #{contractNos, jdbcType=VARCHAR} AND
	</if>
	<if test="projectIds != null and '' != projectIds">
		h.`projectIds` = #{projectIds, jdbcType=VARCHAR} AND
	</if>
	<if test="type != null and '' != type">
		h.`type` = #{type, jdbcType=VARCHAR} AND
	</if>
	<if test="state != null">
		h.`state` = #{state, jdbcType=INTEGER} AND
	</if>
	<if test="peopleNum != null">
		h.`peopleNum` = #{peopleNum, jdbcType=INTEGER} AND
	</if>
	<if test="callbackState != null">
		h.`callbackState` = #{callbackState, jdbcType=INTEGER} AND
	</if>
	<if test="facilitatorId != null">
		h.`facilitatorId` = #{facilitatorId, jdbcType=INTEGER} AND
	</if>
	<if test="facilitatorCode != null and '' != facilitatorCode">
		h.`facilitatorCode` = #{facilitatorCode, jdbcType=VARCHAR} AND
	</if>
	<if test="facilitatorName != null and '' != facilitatorName">
		h.`facilitatorName` = #{facilitatorName, jdbcType=VARCHAR} AND
	</if>
	<if test="bankInfo != null and '' != bankInfo">
		h.`bankInfo` = #{bankInfo, jdbcType=VARCHAR} AND
	</if>
	<if test="bankAccount != null and '' != bankAccount">
		h.`bankAccount` = #{bankAccount, jdbcType=VARCHAR} AND
	</if>
	<if test="officeCode != null and '' != officeCode">
		h.`officeCode` = #{officeCode, jdbcType=VARCHAR} AND
	</if>
	<if test="profitDepCode != null and '' != profitDepCode">
		h.`profitDepCode` = #{profitDepCode, jdbcType=VARCHAR} AND
	</if>
	<if test="dutyPerson != null and '' != dutyPerson">
		h.`dutyPerson` = #{dutyPerson, jdbcType=VARCHAR} AND
	</if>
	<if test="officeDutyPerson != null and '' != officeDutyPerson">
		h.`officeDutyPerson` = #{officeDutyPerson, jdbcType=VARCHAR} AND
	</if>
	<if test="isAccrued != null">
		h.`isAccrued` = #{isAccrued, jdbcType=BIT} AND
	</if>
	<if test="isInvoiced != null">
		h.`isInvoiced` = #{isInvoiced, jdbcType=BIT} AND
	</if>
	<if test="dispatchAmount != null and '' != dispatchAmount">
		h.`dispatchAmount` = #{dispatchAmount, jdbcType=VARCHAR} AND
	</if>
	<if test="prepaidInfo != null and '' != prepaidInfo">
		h.`prepaidInfo` = #{prepaidInfo, jdbcType=VARCHAR} AND
	</if>
	<if test="prepaidRule != null and '' != prepaidRule">
		h.`prepaidRule` = #{prepaidRule, jdbcType=VARCHAR} AND
	</if>
	<if test="acceptanceInfo != null and '' != acceptanceInfo">
		h.`acceptanceInfo` = #{acceptanceInfo, jdbcType=VARCHAR} AND
	</if>
	<if test="reason != null and '' != reason">
		h.`reason` = #{reason, jdbcType=VARCHAR} AND
	</if>
	<if test="remark != null and '' != remark">
		h.`remark` = #{remark, jdbcType=VARCHAR} AND
	</if>
	<if test="dispatchTime != null">
		h.`dispatchTime` = #{dispatchTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="dispatchYear != null">
		#{dispatchYear, jdbcType=INTEGER} = YEAR(h.`dispatchTime`) AND
	</if>
	<if test="smsProjectCode != null and '' != smsProjectCode">
		h.`smsProjectCode` = #{smsProjectCode, jdbcType=VARCHAR} AND
	</if>
	<if test="smsSubmitTime != null">
		h.`smsSubmitTime` = #{smsSubmitTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="smsProjectAmount != null and '' != smsProjectAmount">
		h.`smsProjectAmount` = #{smsProjectAmount, jdbcType=VARCHAR} AND
	</if>
	<if test="smsAfProjectAmount != null and '' != smsAfProjectAmount">
		h.`smsAfProjectAmount` = #{smsAfProjectAmount, jdbcType=VARCHAR} AND
	</if>
	<if test="effectiveFrom != null">
		h.`effectiveFrom` = #{effectiveFrom, jdbcType=TIMESTAMP} AND
	</if>
	<if test="effectiveTo != null">
		h.`effectiveTo` = #{effectiveTo, jdbcType=TIMESTAMP} AND
	</if>
	<if test="disabled != null">
		h.`disabled` = #{disabled, jdbcType=BIT} AND
	</if>
	<if test="dispatched != null">
		h.`dispatched` = #{dispatched, jdbcType=BIT} AND
	</if>
	<if test="settled != null">
		h.`settled` = #{settled, jdbcType=BIT} AND
	</if>
	<if test="createBy != null and '' != createBy">
		h.`createBy` = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="createTime != null">
		h.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		h.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		h.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="customInfo != null and '' != customInfo">
		h.`customInfo` = #{customInfo, jdbcType=OTHER} AND
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1628 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.id != null">
			h.`id` = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.dispatchName != null and '' != model.dispatchName">
			h.`dispatchName` = #{model.dispatchName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dispatchNo != null and '' != model.dispatchNo">
			h.`dispatchNo` = #{model.dispatchNo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dispatchSeq != null and '' != model.dispatchSeq">
			h.`dispatchSeq` = #{model.dispatchSeq, jdbcType=VARCHAR} AND
		</if>
		<if test="model.contractNos != null and '' != model.contractNos">
			h.`contractNos` = #{model.contractNos, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectIds != null and '' != model.projectIds">
			h.`projectIds` = #{model.projectIds, jdbcType=VARCHAR} AND
		</if>
		<if test="model.type != null and '' != model.type">
			h.`type` = #{model.type, jdbcType=VARCHAR} AND
		</if>
		<if test="model.state != null">
			h.`state` = #{model.state, jdbcType=INTEGER} AND
		</if>
		<if test="model.peopleNum != null">
			h.`peopleNum` = #{model.peopleNum, jdbcType=INTEGER} AND
		</if>
		<if test="model.callbackState != null">
			h.`callbackState` = #{model.callbackState, jdbcType=INTEGER} AND
		</if>
		<if test="model.facilitatorId != null">
			h.`facilitatorId` = #{model.facilitatorId, jdbcType=INTEGER} AND
		</if>
		<if test="model.facilitatorCode != null and '' != model.facilitatorCode">
			h.`facilitatorCode` = #{model.facilitatorCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.facilitatorName != null and '' != model.facilitatorName">
			h.`facilitatorName` = #{model.facilitatorName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.bankInfo != null and '' != model.bankInfo">
			h.`bankInfo` = #{model.bankInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.bankAccount != null and '' != model.bankAccount">
			h.`bankAccount` = #{model.bankAccount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.officeCode != null and '' != model.officeCode">
			h.`officeCode` = #{model.officeCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.profitDepCode != null and '' != model.profitDepCode">
			h.`profitDepCode` = #{model.profitDepCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dutyPerson != null and '' != model.dutyPerson">
			h.`dutyPerson` = #{model.dutyPerson, jdbcType=VARCHAR} AND
		</if>
		<if test="model.officeDutyPerson != null and '' != model.officeDutyPerson">
			h.`officeDutyPerson` = #{model.officeDutyPerson, jdbcType=VARCHAR} AND
		</if>
		<if test="model.isAccrued != null">
			h.`isAccrued` = #{model.isAccrued, jdbcType=BIT} AND
		</if>
		<if test="model.isInvoiced != null">
			h.`isInvoiced` = #{model.isInvoiced, jdbcType=BIT} AND
		</if>
		<if test="model.dispatchAmount != null and '' != model.dispatchAmount">
			h.`dispatchAmount` = #{model.dispatchAmount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.prepaidInfo != null and '' != model.prepaidInfo">
			h.`prepaidInfo` = #{model.prepaidInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.prepaidRule != null and '' != model.prepaidRule">
			h.`prepaidRule` = #{model.prepaidRule, jdbcType=VARCHAR} AND
		</if>
		<if test="model.acceptanceInfo != null and '' != model.acceptanceInfo">
			h.`acceptanceInfo` = #{model.acceptanceInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.reason != null and '' != model.reason">
			h.`reason` = #{model.reason, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			h.`remark` = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.dispatchTime != null">
			h.`dispatchTime` = #{model.dispatchTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.dispatchYear != null">
			#{model.dispatchYear, jdbcType=INTEGER} = YEAR(h.`dispatchTime`) AND
		</if>
		<if test="model.smsProjectCode != null and '' != model.smsProjectCode">
			h.`smsProjectCode` = #{model.smsProjectCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.smsSubmitTime != null">
			h.`smsSubmitTime` = #{model.smsSubmitTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.smsProjectAmount != null and '' != model.smsProjectAmount">
			h.`smsProjectAmount` = #{model.smsProjectAmount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.smsAfProjectAmount != null and '' != model.smsAfProjectAmount">
			h.`smsAfProjectAmount` = #{model.smsAfProjectAmount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.effectiveFrom != null">
			h.`effectiveFrom` = #{model.effectiveFrom, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.effectiveTo != null">
			h.`effectiveTo` = #{model.effectiveTo, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.disabled != null">
			h.`disabled` = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.dispatched != null">
			h.`dispatched` = #{model.dispatched, jdbcType=BIT} AND
		</if>
		<if test="model.settled != null">
			h.`settled` = #{model.settled, jdbcType=BIT} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			h.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.createTime != null">
			h.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			h.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			h.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.customInfo != null and '' != model.customInfo">
			h.`customInfo` = #{model.customInfo, jdbcType=OTHER} AND
		</if>
	</if>
	<if test="fuzzySearch == true and fuzzy != ''">
		(
			h.`dispatchName` like CONCAT("%", #{fuzzy}, "%") or
			h.`dispatchNo` like CONCAT("%", #{fuzzy}, "%") or
			h.`dispatchSeq` like CONCAT("%", #{fuzzy}, "%") or
			h.`contractNos` like CONCAT("%", #{fuzzy}, "%") or
			h.`projectIds` like CONCAT("%", #{fuzzy}, "%") or
			h.`type` like CONCAT("%", #{fuzzy}, "%") or
			h.`facilitatorCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`facilitatorName` like CONCAT("%", #{fuzzy}, "%") or
			h.`bankInfo` like CONCAT("%", #{fuzzy}, "%") or
			h.`bankAccount` like CONCAT("%", #{fuzzy}, "%") or
			h.`officeCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`profitDepCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`dutyPerson` like CONCAT("%", #{fuzzy}, "%") or
			h.`officeDutyPerson` like CONCAT("%", #{fuzzy}, "%") or
			h.`dispatchAmount` like CONCAT("%", #{fuzzy}, "%") or
			h.`prepaidInfo` like CONCAT("%", #{fuzzy}, "%") or
			h.`prepaidRule` like CONCAT("%", #{fuzzy}, "%") or
			h.`acceptanceInfo` like CONCAT("%", #{fuzzy}, "%") or
			h.`reason` like CONCAT("%", #{fuzzy}, "%") or
			h.`remark` like CONCAT("%", #{fuzzy}, "%") or
			h.`smsProjectCode` like CONCAT("%", #{fuzzy}, "%") or
			h.`smsProjectAmount` like CONCAT("%", #{fuzzy}, "%") or
			h.`smsAfProjectAmount` like CONCAT("%", #{fuzzy}, "%") or
			h.`createBy` like CONCAT("%", #{fuzzy}, "%") or
			h.`updateBy` like CONCAT("%", #{fuzzy}, "%") or
			h.`customInfo` like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1791 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1798 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchProjectMapper.xml:1805 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchSettlementMapper.xml:31 | fragment=Base_Column_List
id, settleSeq, dispatchId, dispatchSeq, progressDesc, progressRatio, acceptanceDesc, 
    acceptanceRatio, ratio, amount, memo, confirmTime, paymentTime, remark, state, sseId, 
    year, quarter, month, customInfo, createTime, createBy, updateTime, updateBy, disabled

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/DispatchSettlementMapper.xml:731 | fragment=sql_model_where
<if test="model != null ">
		<if test="model.id != null and '' != model.id">
			s.id = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.settleSeq != null and '' != model.settleSeq">
			s.settleSeq like concat("%", #{model.settleSeq, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.dispatchId != null and '' != model.dispatchId">
			s.dispatchId = #{model.dispatchId, jdbcType=INTEGER} AND
		</if>
		<if test="model.dispatchSeq != null and '' != model.dispatchSeq">
			s.dispatchSeq like concat("%", #{model.dispatchSeq, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.progressDesc != null and '' != model.progressDesc">
			s.progressDesc = #{model.progressDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.progressRatio != null and '' != model.progressRatio">
			s.progressRatio = #{model.progressRatio, jdbcType=REAL} AND
		</if>
		<if test="model.acceptanceDesc != null and '' != model.acceptanceDesc">
			s.acceptanceDesc = #{model.acceptanceDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.acceptanceRatio != null and '' != model.acceptanceRatio">
			s.acceptanceRatio = #{model.acceptanceRatio, jdbcType=VARCHAR} AND
		</if>
		<if test="model.ratio != null and '' != model.ratio">
			s.ratio = #{model.ratio, jdbcType=VARCHAR} AND
		</if>
		<if test="model.amount != null and '' != model.amount">
			s.amount = #{model.amount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.memo != null and '' != model.memo">
			s.memo = #{model.memo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.confirmTime != null and '' != model.confirmTime">
			s.confirmTime = #{model.confirmTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.paymentTime != null and '' != model.paymentTime">
			s.paymentTime = #{model.paymentTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			s.remark = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.state != null and '' != model.state">
			s.state = #{model.state, jdbcType=INTEGER} AND
		</if>
		<if test="model.sseId != null and '' != model.sseId">
			s.sseId = #{model.sseId, jdbcType=INTEGER} AND
		</if>
		<if test="model.year != null and '' != model.year">
			s.year = #{model.year, jdbcType=INTEGER} AND
		</if>
		<if test="model.quarter != null and '' != model.quarter">
			s.quarter = #{model.quarter, jdbcType=INTEGER} AND
		</if>
		<if test="model.month != null and '' != model.month">
			s.month = #{model.month, jdbcType=INTEGER} AND
		</if>
		<if test="model.customInfo != null and '' != model.customInfo">
			s.customInfo = #{model.customInfo, jdbcType=OTHER} AND
		</if>
		<if test="model.createTime != null">
			s.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			s.createBy = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			s.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			s.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.disabled != null">
			s.disabled = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.projectId != null">
			find_in_set(#{model.projectId}, h.projectIds)
		</if>
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:27 | fragment=Base_Column_List
`id`, `code`, `name`, `type`, `bankInfo`, `bankAccount`, `cnapsCode`, `contacts`, 
    `tel`, `email`, `state`, `needApprove`, `approveStatus`, `deliveryIds`, `relateType`, 
    `effectiveFrom`, `effectiveTo`, `createBy`, `createTime`, `updateBy`, `updateTime`

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:281 | fragment=sql_where_selective
<if test="id != null">
		f.`id` = #{id, jdbcType=INTEGER} AND
	</if>
	<if test="code != null and '' != code">
		f.`code` = #{code, jdbcType=VARCHAR} AND
	</if>
	<if test="name != null and '' != name">
		f.`name` = #{name, jdbcType=VARCHAR} AND
	</if>
	<if test="type != null and '' != type">
		f.`type` = #{type, jdbcType=VARCHAR} AND
	</if>
	<if test="bankInfo != null and '' != bankInfo">
		f.`bankInfo` = #{bankInfo, jdbcType=VARCHAR} AND
	</if>
	<if test="bankAccount != null and '' != bankAccount">
		f.`bankAccount` = #{bankAccount, jdbcType=VARCHAR} AND
	</if>
	<if test="cnapsCode != null and '' != cnapsCode">
		f.`cnapsCode` = #{cnapsCode, jdbcType=VARCHAR} AND
	</if>
	<if test="contacts != null and '' != contacts">
		f.`contacts` = #{contacts, jdbcType=VARCHAR} AND
	</if>
	<if test="tel != null and '' != tel">
		f.`tel` = #{tel, jdbcType=VARCHAR} AND
	</if>
	<if test="email != null and '' != email">
		f.`email` = #{email, jdbcType=VARCHAR} AND
	</if>
	<if test="state != null">
		f.`state` = #{state, jdbcType=BIT} AND
	</if>
	<if test="needApprove != null">
		f.`needApprove` = #{needApprove, jdbcType=BIT} AND
	</if>
	<if test="approveStatus != null">
		f.`approveStatus` = #{approveStatus, jdbcType=INTEGER} AND
	</if>
	<if test="deliveryIds != null and '' != deliveryIds">
		f.`deliveryIds` = #{deliveryIds, jdbcType=VARCHAR} AND
	</if>
	<if test="relateType != null and '' != relateType">
		f.`relateType` = #{relateType, jdbcType=VARCHAR} AND
	</if>
	<if test="effectiveFrom != null">
		f.`effectiveFrom` = #{effectiveFrom, jdbcType=TIMESTAMP} AND
	</if>
	<if test="effectiveTo != null">
		f.`effectiveTo` = #{effectiveTo, jdbcType=TIMESTAMP} AND
	</if>
	<if test="createBy != null and '' != createBy">
		f.`createBy` = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="createTime != null">
		f.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		f.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		f.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:347 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.id != null">
			f.`id` = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.code != null and '' != model.code">
			f.`code` = #{model.code, jdbcType=VARCHAR} AND
		</if>
		<if test="model.name != null and '' != model.name">
			f.`name` = #{model.name, jdbcType=VARCHAR} AND
		</if>
		<if test="model.type != null and '' != model.type">
			f.`type` = #{model.type, jdbcType=VARCHAR} AND
		</if>
		<if test="model.bankInfo != null and '' != model.bankInfo">
			f.`bankInfo` = #{model.bankInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.bankAccount != null and '' != model.bankAccount">
			f.`bankAccount` = #{model.bankAccount, jdbcType=VARCHAR} AND
		</if>
		<if test="model.cnapsCode != null and '' != model.cnapsCode">
			f.`cnapsCode` = #{model.cnapsCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.contacts != null and '' != model.contacts">
			f.`contacts` = #{model.contacts, jdbcType=VARCHAR} AND
		</if>
		<if test="model.tel != null and '' != model.tel">
			f.`tel` = #{model.tel, jdbcType=VARCHAR} AND
		</if>
		<if test="model.email != null and '' != model.email">
			f.`email` = #{model.email, jdbcType=VARCHAR} AND
		</if>
		<if test="model.state != null">
			f.`state` = #{model.state, jdbcType=BIT} AND
		</if>
		<if test="model.needApprove != null">
			f.`needApprove` = #{model.needApprove, jdbcType=BIT} AND
		</if>
		<if test="model.approveStatus != null">
			f.`approveStatus` = #{model.approveStatus, jdbcType=INTEGER} AND
		</if>
		<if test="model.deliveryIds != null and '' != model.deliveryIds">
			f.`deliveryIds` = #{model.deliveryIds, jdbcType=VARCHAR} AND
		</if>
		<if test="model.relateType != null and '' != model.relateType">
			f.`relateType` = #{model.relateType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.effectiveFrom != null">
			f.`effectiveFrom` = #{model.effectiveFrom, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.effectiveTo != null">
			f.`effectiveTo` = #{model.effectiveTo, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			f.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.createTime != null">
			f.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			f.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			f.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
	</if>
	<if test="fuzzySearch == true and fuzzy != ''">
		(
			f.`code` like CONCAT("%", #{fuzzy}, "%") or
			f.`name` like CONCAT("%", #{fuzzy}, "%") or
			f.`type` like CONCAT("%", #{fuzzy}, "%") or
			f.`bankInfo` like CONCAT("%", #{fuzzy}, "%") or
			f.`bankAccount` like CONCAT("%", #{fuzzy}, "%") or
			f.`cnapsCode` like CONCAT("%", #{fuzzy}, "%") or
			f.`contacts` like CONCAT("%", #{fuzzy}, "%") or
			f.`tel` like CONCAT("%", #{fuzzy}, "%") or
			f.`email` like CONCAT("%", #{fuzzy}, "%") or
			f.`deliveryIds` like CONCAT("%", #{fuzzy}, "%") or
			f.`relateType` like CONCAT("%", #{fuzzy}, "%") or
			f.`createBy` like CONCAT("%", #{fuzzy}, "%") or
			f.`updateBy` like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:432 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:439 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/FacilitatorMapper.xml:446 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetLeakRelationMapper.xml:17 | fragment=Base_Column_List
id, projectId, assetId, leakId, effectiveFrom, effectiveTo, disabled, createBy, createTime, 
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetLeakRelationMapper.xml:164 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="id != null">
				alr.id = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="projectId != null">
				alr.projectId = #{projectId, jdbcType=INTEGER} AND
			</if>
			<if test="assetId != null">
				alr.assetId = #{assetId, jdbcType=INTEGER} AND
			</if>
			<if test="leakId != null">
				alr.leakId = #{leakId, jdbcType=INTEGER} AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ alr.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
			<if test="effectiveTo != null">
				<![CDATA[ (alr.effectiveTo is null or alr.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="disabled != null">
				alr.disabled = #{disabled, jdbcType=BIT} AND
			</if>
			<if test="createBy != null and '' != createBy">
				alr.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				alr.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				alr.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				alr.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetLeakRelationMapper.xml:203 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.id != null">
					alr.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectId != null">
					alr.projectId = #{model.projectId, jdbcType=INTEGER} AND
				</if>
				<if test="model.assetId != null">
					alr.assetId = #{model.assetId, jdbcType=INTEGER} AND
				</if>
				<if test="model.leakId != null">
					alr.leakId = #{model.leakId, jdbcType=INTEGER} AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ alr.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
				<if test="model.effectiveTo != null">
					<![CDATA[ (alr.effectiveTo is null or alr.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.disabled != null">
					alr.disabled = #{model.disabled, jdbcType=BIT} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					alr.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					alr.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					alr.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					alr.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					alr.createBy like CONCAT("%", #{fuzzy}, "%") or
					alr.updateBy like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetLeakRelationMapper.xml:250 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetMapper.xml:37 | fragment=Base_Column_List
id, assetNum, assetName, assetCategory, assetType, assetHost, assetOpenPorts, assetDeployInfo, 
    assetUsage, customerName, industryCode, assetAS, assetASVersion, assetASIdentify, 
    assetASFramework, middlewareName, middlewareVersion, developerBrand, assetOS, assetOSVersion, 
    assetDB, assetDBVersion, customInfo, status, trackStatus, trackedTime, disabled, 
    createBy, createTime, updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetMapper.xml:421 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="id != null">
				ia.id = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="assetNum != null and '' != assetNum">
				ia.assetNum = #{assetNum, jdbcType=VARCHAR} AND
			</if>
			<if test="assetName != null and '' != assetName">
				ia.assetName = #{assetName, jdbcType=VARCHAR} AND
			</if>
			<if test="assetCategory != null and '' != assetCategory">
				ia.assetCategory = #{assetCategory, jdbcType=VARCHAR} AND
			</if>
			<if test="assetType != null and '' != assetType">
				ia.assetType = #{assetType, jdbcType=VARCHAR} AND
			</if>
			<if test="assetHost != null and '' != assetHost">
				ia.assetHost = #{assetHost, jdbcType=VARCHAR} AND
			</if>
			<if test="assetOpenPorts != null and '' != assetOpenPorts">
				ia.assetOpenPorts = #{assetOpenPorts, jdbcType=VARCHAR} AND
			</if>
			<if test="assetDeployInfo != null and '' != assetDeployInfo">
				ia.assetDeployInfo = #{assetDeployInfo, jdbcType=VARCHAR} AND
			</if>
			<if test="assetUsage != null and '' != assetUsage">
				ia.assetUsage = #{assetUsage, jdbcType=VARCHAR} AND
			</if>
			<if test="customerName != null and '' != customerName">
				ia.customerName = #{customerName, jdbcType=VARCHAR} AND
			</if>
			<if test="industryCode != null and '' != industryCode">
				ia.industryCode = #{industryCode, jdbcType=VARCHAR} AND
			</if>
			<if test="assetAS != null and '' != assetAS">
				ia.assetAS = #{assetAS, jdbcType=VARCHAR} AND
			</if>
			<if test="assetASVersion != null and '' != assetASVersion">
				ia.assetASVersion = #{assetASVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="assetASIdentify != null and '' != assetASIdentify">
				ia.assetASIdentify = #{assetASIdentify, jdbcType=VARCHAR} AND
			</if>
			<if test="assetASFramework != null and '' != assetASFramework">
				ia.assetASFramework = #{assetASFramework, jdbcType=VARCHAR} AND
			</if>
			<if test="middlewareName != null and '' != middlewareName">
				ia.middlewareName = #{middlewareName, jdbcType=VARCHAR} AND
			</if>
			<if test="middlewareVersion != null and '' != middlewareVersion">
				ia.middlewareVersion = #{middlewareVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="developerBrand != null and '' != developerBrand">
				ia.developerBrand = #{developerBrand, jdbcType=VARCHAR} AND
			</if>
			<if test="assetOS != null and '' != assetOS">
				ia.assetOS = #{assetOS, jdbcType=VARCHAR} AND
			</if>
			<if test="assetOSVersion != null and '' != assetOSVersion">
				ia.assetOSVersion = #{assetOSVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="assetDB != null and '' != assetDB">
				ia.assetDB = #{assetDB, jdbcType=VARCHAR} AND
			</if>
			<if test="assetDBVersion != null and '' != assetDBVersion">
				ia.assetDBVersion = #{assetDBVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="customInfo != null">
				ia.customInfo = #{customInfo, jdbcType=JSON} AND
			</if>
			<if test="status != null and '' != status">
				ia.status = #{status, jdbcType=VARCHAR} AND
			</if>
			<if test="trackStatus != null">
				ia.trackStatus = #{trackStatus, jdbcType=INTEGER} AND
			</if>
			<if test="trackedTime != null">
				ia.trackedTime = #{trackedTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="disabled != null">
				ia.disabled = #{disabled, jdbcType=BIT} AND
			</if>
			<if test="createBy != null and '' != createBy">
				ia.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				ia.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				ia.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				ia.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="checkProject == true">
				(
					(
					<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
						1 = 1 AND
						<if test="projectTypes != null">
							FIND_IN_SET(ph.`projectType`, #{projectTypes}) AND 
						</if>
						<if test="officeCodes != null">
							FIND_IN_SET(ph.`column001`, #{officeCodes}) AND
						</if>
					</trim>
					)
					<if test="memberCode != null and memberCode != ''">
						<!-- OR pm.memberCode like concat("%", #{memberCode}, "%") -->
						OR EXISTS 
				        (SELECT 
				            1 
				        FROM
				            pm_project_member pm 
				        WHERE pm.projectId = ph.`projectId` 
				            AND pm.projectType = ph.`projectType` 
				            AND IFNULL(pm.effectiveTo, "9999-12-31") > NOW()
				            AND pm.memberCode = #{memberCode})
					</if>
				) AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetMapper.xml:547 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.id != null">
					ia.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.assetNum != null and '' != model.assetNum">
					ia.assetNum = #{model.assetNum, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetName != null and '' != model.assetName">
					ia.assetName = #{model.assetName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetCategory != null and '' != model.assetCategory">
					ia.assetCategory = #{model.assetCategory, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetType != null and '' != model.assetType">
					ia.assetType = #{model.assetType, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetHost != null and '' != model.assetHost">
					ia.assetHost = #{model.assetHost, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetOpenPorts != null and '' != model.assetOpenPorts">
					ia.assetOpenPorts = #{model.assetOpenPorts, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetDeployInfo != null and '' != model.assetDeployInfo">
					ia.assetDeployInfo = #{model.assetDeployInfo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetUsage != null and '' != model.assetUsage">
					ia.assetUsage = #{model.assetUsage, jdbcType=VARCHAR} AND
				</if>
				<if test="model.customerName != null and '' != model.customerName">
					ia.customerName = #{model.customerName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.industryCode != null and '' != model.industryCode">
					ia.industryCode = #{model.industryCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetAS != null and '' != model.assetAS">
					ia.assetAS = #{model.assetAS, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetASVersion != null and '' != model.assetASVersion">
					ia.assetASVersion = #{model.assetASVersion, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetASIdentify != null and '' != model.assetASIdentify">
					ia.assetASIdentify = #{model.assetASIdentify, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetASFramework != null and '' != model.assetASFramework">
					ia.assetASFramework = #{model.assetASFramework, jdbcType=VARCHAR} AND
				</if>
				<if test="model.middlewareName != null and '' != model.middlewareName">
					ia.middlewareName = #{model.middlewareName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.middlewareVersion != null and '' != model.middlewareVersion">
					ia.middlewareVersion = #{model.middlewareVersion, jdbcType=VARCHAR} AND
				</if>
				<if test="model.developerBrand != null and '' != model.developerBrand">
					ia.developerBrand = #{model.developerBrand, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetOS != null and '' != model.assetOS">
					ia.assetOS = #{model.assetOS, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetOSVersion != null and '' != model.assetOSVersion">
					ia.assetOSVersion = #{model.assetOSVersion, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetDB != null and '' != model.assetDB">
					ia.assetDB = #{model.assetDB, jdbcType=VARCHAR} AND
				</if>
				<if test="model.assetDBVersion != null and '' != model.assetDBVersion">
					ia.assetDBVersion = #{model.assetDBVersion, jdbcType=VARCHAR} AND
				</if>
				<if test="model.customInfo != null">
					ia.customInfo = #{model.customInfo, jdbcType=JSON} AND
				</if>
				<if test="model.status != null and '' != model.status">
					ia.status = #{model.status, jdbcType=VARCHAR} AND
				</if>
				<if test="model.trackStatus != null">
					ia.trackStatus = #{model.trackStatus, jdbcType=INTEGER} AND
				</if>
				<if test="model.trackedTime != null">
					ia.trackedTime = #{model.trackedTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.disabled != null">
					ia.disabled = #{model.disabled, jdbcType=BIT} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					ia.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					ia.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					ia.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					ia.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.checkProject == true">
					(
						(
						<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
							1 = 1 AND
							<if test="model.projectTypes != null">
								FIND_IN_SET(ph.`projectType`, #{model.projectTypes}) AND 
							</if>
							<if test="model.officeCodes != null">
								FIND_IN_SET(ph.`column001`, #{model.officeCodes}) AND
							</if>
						</trim>
						)
						<if test="model.memberCode != null and model.memberCode != ''">
							<!-- OR pm.memberCode like concat("%", #{model.memberCode}, "%") -->
							OR EXISTS 
					        (SELECT 
					            1 
					        FROM
					            pm_project_member pm 
					        WHERE pm.projectId = ph.`projectId` 
					            AND pm.projectType = ph.`projectType` 
					            AND IFNULL(pm.effectiveTo, "9999-12-31") > NOW()
					            AND pm.memberCode = #{model.memberCode})
						</if>
					) AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					ia.assetNum like CONCAT("%", #{fuzzy}, "%") or
					ia.assetName like CONCAT("%", #{fuzzy}, "%") or
					ia.assetCategory like CONCAT("%", #{fuzzy}, "%") or
					ia.assetType like CONCAT("%", #{fuzzy}, "%") or
					ia.assetHost like CONCAT("%", #{fuzzy}, "%") or
					ia.assetOpenPorts like CONCAT("%", #{fuzzy}, "%") or
					ia.assetDeployInfo like CONCAT("%", #{fuzzy}, "%") or
					ia.assetUsage like CONCAT("%", #{fuzzy}, "%") or
					ia.customerName like CONCAT("%", #{fuzzy}, "%") or
					ia.industryCode like CONCAT("%", #{fuzzy}, "%") or
					ia.assetAS like CONCAT("%", #{fuzzy}, "%") or
					ia.assetASVersion like CONCAT("%", #{fuzzy}, "%") or
					ia.assetASIdentify like CONCAT("%", #{fuzzy}, "%") or
					ia.assetASFramework like CONCAT("%", #{fuzzy}, "%") or
					ia.middlewareName like CONCAT("%", #{fuzzy}, "%") or
					ia.middlewareVersion like CONCAT("%", #{fuzzy}, "%") or
					ia.developerBrand like CONCAT("%", #{fuzzy}, "%") or
					ia.assetOS like CONCAT("%", #{fuzzy}, "%") or
					ia.assetOSVersion like CONCAT("%", #{fuzzy}, "%") or
					ia.assetDB like CONCAT("%", #{fuzzy}, "%") or
					ia.assetDBVersion like CONCAT("%", #{fuzzy}, "%") or
					ia.status like CONCAT("%", #{fuzzy}, "%") or
					ia.createBy like CONCAT("%", #{fuzzy}, "%") or
					ia.updateBy like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetMapper.xml:703 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetProjectRelationMapper.xml:16 | fragment=Base_Column_List
id, projectId, assetId, effectiveFrom, effectiveTo, disabled, createBy, createTime, 
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetProjectRelationMapper.xml:153 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="id != null">
				apr.id = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="projectId != null">
				apr.projectId = #{projectId, jdbcType=INTEGER} AND
			</if>
			<if test="assetId != null">
				apr.assetId = #{assetId, jdbcType=INTEGER} AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ apr.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
			<if test="effectiveTo != null">
				<![CDATA[ (apr.effectiveTo is null or apr.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="disabled != null">
				apr.disabled = #{disabled, jdbcType=BIT} AND
			</if>
			<if test="createBy != null and '' != createBy">
				apr.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				apr.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				apr.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				apr.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetProjectRelationMapper.xml:189 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.id != null">
					apr.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectId != null">
					apr.projectId = #{model.projectId, jdbcType=INTEGER} AND
				</if>
				<if test="model.assetId != null">
					apr.assetId = #{model.assetId, jdbcType=INTEGER} AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ apr.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
				<if test="model.effectiveTo != null">
					<![CDATA[ (apr.effectiveTo is null or apr.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.disabled != null">
					apr.disabled = #{model.disabled, jdbcType=BIT} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					apr.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					apr.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					apr.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					apr.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					apr.createBy like CONCAT("%", #{fuzzy}, "%") or
					apr.updateBy like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryAssetProjectRelationMapper.xml:233 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakMapper.xml:25 | fragment=Base_Column_List
`id`, `leakCode`, `leakName`, `leakType`, `leakLevel`, `leakDesc`, `industryCode`, 
    `leakSourceInfo`, `remark`, `status`, `trackStatus`, `trackedTime`, `disabled`, `assetIds`, 
    `customInfo`, `createBy`, `createTime`, `updateBy`, `updateTime`

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakMapper.xml:273 | fragment=sql_where_selective
<if test="id != null">
		l.`id` = #{id, jdbcType=INTEGER} AND
	</if>
	<if test="leakCode != null and '' != leakCode">
		l.`leakCode` = #{leakCode, jdbcType=VARCHAR} AND
	</if>
	<if test="leakName != null and '' != leakName">
		l.`leakName` = #{leakName, jdbcType=VARCHAR} AND
	</if>
	<if test="leakType != null and '' != leakType">
		l.`leakType` = #{leakType, jdbcType=VARCHAR} AND
	</if>
	<if test="leakLevel != null and '' != leakLevel">
		l.`leakLevel` = #{leakLevel, jdbcType=VARCHAR} AND
	</if>
	<if test="leakDesc != null and '' != leakDesc">
		l.`leakDesc` = #{leakDesc, jdbcType=VARCHAR} AND
	</if>
	<if test="industryCode != null and '' != industryCode">
		l.`industryCode` = #{industryCode, jdbcType=VARCHAR} AND
	</if>
	<if test="leakSourceInfo != null and '' != leakSourceInfo">
		l.`leakSourceInfo` = #{leakSourceInfo, jdbcType=VARCHAR} AND
	</if>
	<if test="remark != null and '' != remark">
		l.`remark` = #{remark, jdbcType=VARCHAR} AND
	</if>
	<if test="status != null and '' != status">
		l.`status` = #{status, jdbcType=VARCHAR} AND
	</if>
	<if test="trackStatus != null">
		l.`trackStatus` = #{trackStatus, jdbcType=INTEGER} AND
	</if>
	<if test="trackedTime != null">
		l.`trackedTime` = #{trackedTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="disabled != null">
		l.`disabled` = #{disabled, jdbcType=BIT} AND
	</if>
	<if test="assetIds != null and '' != assetIds">
		l.`assetIds` = #{assetIds, jdbcType=VARCHAR} AND
	</if>
	<if test="customInfo != null">
		<!-- l.`customInfo` = #{customInfo, jdbcType=JSON} AND -->
		<foreach collection="customInfo" item="value" index="key">
		   l.`customInfo` -> '$.${key}' like concat("%", #{value}, "%") AND
	    </foreach>
	</if>
	<if test="createBy != null and '' != createBy">
		l.`createBy` = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="createTime != null">
		l.`createTime` = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		l.`updateBy` = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		l.`updateTime` = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakMapper.xml:335 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.id != null">
			l.`id` = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.leakCode != null and '' != model.leakCode">
			l.`leakCode` = #{model.leakCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.leakName != null and '' != model.leakName">
			l.`leakName` = #{model.leakName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.leakType != null and '' != model.leakType">
			l.`leakType` = #{model.leakType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.leakLevel != null and '' != model.leakLevel">
			l.`leakLevel` = #{model.leakLevel, jdbcType=VARCHAR} AND
		</if>
		<if test="model.leakDesc != null and '' != model.leakDesc">
			l.`leakDesc` = #{model.leakDesc, jdbcType=VARCHAR} AND
		</if>
		<if test="model.industryCode != null and '' != model.industryCode">
			l.`industryCode` = #{model.industryCode, jdbcType=VARCHAR} AND
		</if>
		<if test="model.leakSourceInfo != null and '' != model.leakSourceInfo">
			l.`leakSourceInfo` = #{model.leakSourceInfo, jdbcType=VARCHAR} AND
		</if>
		<if test="model.remark != null and '' != model.remark">
			l.`remark` = #{model.remark, jdbcType=VARCHAR} AND
		</if>
		<if test="model.status != null and '' != model.status">
			l.`status` = #{model.status, jdbcType=VARCHAR} AND
		</if>
		<if test="model.trackStatus != null">
			l.`trackStatus` = #{model.trackStatus, jdbcType=INTEGER} AND
		</if>
		<if test="model.trackedTime != null">
			l.`trackedTime` = #{model.trackedTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.disabled != null">
			l.`disabled` = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.assetIds != null and '' != model.assetIds">
			l.`assetIds` = #{model.assetIds, jdbcType=VARCHAR} AND
		</if>
		<if test="model.customInfo != null">
			<!-- l.`customInfo` = #{model.customInfo, jdbcType=JSON} AND -->
			<foreach collection="model.customInfo" item="value" index="key">
			   l.`customInfo` -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			l.`createBy` = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.createTime != null">
			l.`createTime` = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			l.`updateBy` = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			l.`updateTime` = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
	</if>
	<if test="fuzzySearch == true and fuzzy != ''">
		(
			l.`leakCode` like CONCAT("%", #{fuzzy}, "%") or
			l.`leakName` like CONCAT("%", #{fuzzy}, "%") or
			l.`leakType` like CONCAT("%", #{fuzzy}, "%") or
			l.`leakLevel` like CONCAT("%", #{fuzzy}, "%") or
			l.`leakDesc` like CONCAT("%", #{fuzzy}, "%") or
			l.`industryCode` like CONCAT("%", #{fuzzy}, "%") or
			l.`leakSourceInfo` like CONCAT("%", #{fuzzy}, "%") or
			l.`remark` like CONCAT("%", #{fuzzy}, "%") or
			l.`status` like CONCAT("%", #{fuzzy}, "%") or
			l.`assetIds` like CONCAT("%", #{fuzzy}, "%") or
			l.`createBy` like CONCAT("%", #{fuzzy}, "%") or
			l.`updateBy` like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakMapper.xml:416 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakMapper.xml:423 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakMapper.xml:430 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakWarningMapper.xml:29 | fragment=Base_Column_List
id, leakName, assetAS, assetASVersion, assetASIdentify, assetASFramework, middlewareName, 
    middlewareVersion, developerBrand, assetOS, assetOSVersion, assetDB, assetDBVersion, 
    ports, customInfo, status, trackStatus, trackedTime, disabled, createBy, createTime, 
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakWarningMapper.xml:320 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="id != null">
				lw.id = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="leakName != null and '' != leakName">
				lw.leakName = #{leakName, jdbcType=VARCHAR} AND
			</if>
			<if test="assetAS != null and '' != assetAS">
				lw.assetAS = #{assetAS, jdbcType=VARCHAR} AND
			</if>
			<if test="assetASVersion != null and '' != assetASVersion">
				lw.assetASVersion = #{assetASVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="assetASIdentify != null and '' != assetASIdentify">
				lw.assetASIdentify = #{assetASIdentify, jdbcType=VARCHAR} AND
			</if>
			<if test="assetASFramework != null and '' != assetASFramework">
				lw.assetASFramework = #{assetASFramework, jdbcType=VARCHAR} AND
			</if>
			<if test="middlewareName != null and '' != middlewareName">
				lw.middlewareName = #{middlewareName, jdbcType=VARCHAR} AND
			</if>
			<if test="middlewareVersion != null and '' != middlewareVersion">
				lw.middlewareVersion = #{middlewareVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="developerBrand != null and '' != developerBrand">
				lw.developerBrand = #{developerBrand, jdbcType=VARCHAR} AND
			</if>
			<if test="assetOS != null and '' != assetOS">
				lw.assetOS = #{assetOS, jdbcType=VARCHAR} AND
			</if>
			<if test="assetOSVersion != null and '' != assetOSVersion">
				lw.assetOSVersion = #{assetOSVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="assetDB != null and '' != assetDB">
				lw.assetDB = #{assetDB, jdbcType=VARCHAR} AND
			</if>
			<if test="assetDBVersion != null and '' != assetDBVersion">
				lw.assetDBVersion = #{assetDBVersion, jdbcType=VARCHAR} AND
			</if>
			<if test="ports != null and '' != ports">
				lw.ports = #{ports, jdbcType=VARCHAR} AND
			</if>
			<if test="customInfo != null">
				lw.customInfo = #{customInfo, jdbcType=JSON} AND
			</if>
			<if test="status != null">
				lw.status = #{status, jdbcType=INTEGER} AND
			</if>
			<if test="trackStatus != null">
				lw.trackStatus = #{trackStatus, jdbcType=INTEGER} AND
			</if>
			<if test="trackedTime != null">
				lw.trackedTime = #{trackedTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="disabled != null">
				lw.disabled = #{disabled, jdbcType=BIT} AND
			</if>
			<if test="createBy != null and '' != createBy">
				lw.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				lw.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				lw.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				lw.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakWarningMapper.xml:466 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_condition"></include>
			<include refid="sql_pageable_fuzzySearch"></include>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakWarningMapper.xml:474 | fragment=sql_model_where_condition
<if test="model != null ">
		<if test="model.id != null">
			lw.id = #{model.id, jdbcType=INTEGER} AND
		</if>
		<if test="model.leakName != null and '' != model.leakName">
			lw.leakName like concat("%", #{model.leakName, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetAS != null and '' != model.assetAS">
			lw.assetAS like concat("%", #{model.assetAS, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetASVersion != null and '' != model.assetASVersion">
			lw.assetASVersion like concat("%", #{model.assetASVersion, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetASIdentify != null and '' != model.assetASIdentify">
			lw.assetASIdentify like concat("%", #{model.assetASIdentify, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetASFramework != null and '' != model.assetASFramework">
			lw.assetASFramework like concat("%", #{model.assetASFramework, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.middlewareName != null and '' != model.middlewareName">
			lw.middlewareName like concat("%", #{model.middlewareName, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.middlewareVersion != null and '' != model.middlewareVersion">
			lw.middlewareVersion like concat("%", #{model.middlewareVersion, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.developerBrand != null and '' != model.developerBrand">
			lw.developerBrand like concat("%", #{model.developerBrand, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetOS != null and '' != model.assetOS">
			lw.assetOS like concat("%", #{model.assetOS, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetOSVersion != null and '' != model.assetOSVersion">
			lw.assetOSVersion like concat("%", #{model.assetOSVersion, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetDB != null and '' != model.assetDB">
			lw.assetDB like concat("%", #{model.assetDB, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.assetDBVersion != null and '' != model.assetDBVersion">
			lw.assetDBVersion like concat("%", #{model.assetDBVersion, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.ports != null and '' != model.ports">
			lw.ports like concat("%", #{model.ports, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.customInfo != null">
			lw.customInfo = #{model.customInfo, jdbcType=JSON} AND
		</if>
		<if test="model.status != null">
			lw.status = #{model.status, jdbcType=INTEGER} AND
		</if>
		<if test="model.trackStatus != null">
			lw.trackStatus = #{model.trackStatus, jdbcType=INTEGER} AND
		</if>
		<if test="model.trackedTime != null">
			lw.trackedTime = #{model.trackedTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.disabled != null">
			lw.disabled = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			lw.createBy = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.createTime != null">
			lw.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			lw.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			lw.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakWarningMapper.xml:547 | fragment=sql_pageable_fuzzySearch
<if test="fuzzySearch == true and fuzzy != ''">
		(
			lw.leakName like CONCAT("%", #{fuzzy}, "%") or
			lw.assetAS like CONCAT("%", #{fuzzy}, "%") or
			lw.assetASVersion like CONCAT("%", #{fuzzy}, "%") or
			lw.assetASIdentify like CONCAT("%", #{fuzzy}, "%") or
			lw.assetASFramework like CONCAT("%", #{fuzzy}, "%") or
			lw.middlewareName like CONCAT("%", #{fuzzy}, "%") or
			lw.middlewareVersion like CONCAT("%", #{fuzzy}, "%") or
			lw.developerBrand like CONCAT("%", #{fuzzy}, "%") or
			lw.assetOS like CONCAT("%", #{fuzzy}, "%") or
			lw.assetOSVersion like CONCAT("%", #{fuzzy}, "%") or
			lw.assetDB like CONCAT("%", #{fuzzy}, "%") or
			lw.assetDBVersion like CONCAT("%", #{fuzzy}, "%") or
			lw.ports like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/IndustryLeakWarningMapper.xml:566 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/PmWorkFlowMapper.xml:27 | fragment=Base_Column_List
id, processKey, taskKey, applyTime, beginTime, endTime, dueTime, procInstId, message, 
    status, userId, objType, objId, dataType, dataId, createBy, createTime, updateBy, 
    updateTime, orgId, customInfo

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/PmWorkFlowMapper.xml:295 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="id != null">
				wf.id = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="processKey != null and '' != processKey">
				wf.processKey = #{processKey, jdbcType=VARCHAR} AND
			</if>
			<if test="taskKey != null and '' != taskKey">
				wf.taskKey = #{taskKey, jdbcType=VARCHAR} AND
			</if>
			<if test="applyTime != null">
				wf.applyTime = #{applyTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="beginTime != null">
				wf.beginTime = #{beginTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="endTime != null">
				wf.endTime = #{endTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="dueTime != null">
				wf.dueTime = #{dueTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="procInstId != null and '' != procInstId">
				wf.procInstId = #{procInstId, jdbcType=VARCHAR} AND
			</if>
			<if test="message != null and '' != message">
				wf.message = #{message, jdbcType=VARCHAR} AND
			</if>
			<if test="status != null and '' != status">
				wf.status = #{status, jdbcType=VARCHAR} AND
			</if>
			<if test="userId != null">
				wf.userId = #{userId, jdbcType=INTEGER} AND
			</if>
			<if test="objType != null and '' != objType">
				wf.objType = #{objType, jdbcType=VARCHAR} AND
			</if>
			<if test="objId != null">
				wf.objId = #{objId, jdbcType=INTEGER} AND
			</if>
			<if test="dataType != null and '' != dataType">
				wf.dataType = #{dataType, jdbcType=VARCHAR} AND
			</if>
			<if test="dataId != null">
				wf.dataId = #{dataId, jdbcType=INTEGER} AND
			</if>
			<if test="createBy != null and '' != createBy">
				wf.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				wf.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				wf.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				wf.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="orgId != null">
				wf.orgId = #{orgId, jdbcType=INTEGER} AND
			</if>
			<if test="customInfo != null">
				<!-- wf.customInfo = #{customInfo, jdbcType=JSON} AND -->
				<foreach collection="customInfo" item="value" index="key">
				   wf.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
			    </foreach>
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/PmWorkFlowMapper.xml:367 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.id != null">
					wf.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.processKey != null and '' != model.processKey">
					wf.processKey = #{model.processKey, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskKey != null and '' != model.taskKey">
					wf.taskKey = #{model.taskKey, jdbcType=VARCHAR} AND
				</if>
				<if test="model.applyTime != null">
					wf.applyTime = #{model.applyTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.beginTime != null">
					wf.beginTime = #{model.beginTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.endTime != null">
					wf.endTime = #{model.endTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.dueTime != null">
					wf.dueTime = #{model.dueTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.procInstId != null and '' != model.procInstId">
					wf.procInstId = #{model.procInstId, jdbcType=VARCHAR} AND
				</if>
				<if test="model.message != null and '' != model.message">
					wf.message = #{model.message, jdbcType=VARCHAR} AND
				</if>
				<if test="model.status != null and '' != model.status">
					wf.status = #{model.status, jdbcType=VARCHAR} AND
				</if>
				<if test="model.userId != null">
					wf.userId = #{model.userId, jdbcType=INTEGER} AND
				</if>
				<if test="model.objType != null and '' != model.objType">
					wf.objType = #{model.objType, jdbcType=VARCHAR} AND
				</if>
				<if test="model.objId != null">
					wf.objId = #{model.objId, jdbcType=INTEGER} AND
				</if>
				<if test="model.dataType != null and '' != model.dataType">
					wf.dataType = #{model.dataType, jdbcType=VARCHAR} AND
				</if>
				<if test="model.dataId != null">
					wf.dataId = #{model.dataId, jdbcType=INTEGER} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					wf.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					wf.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					wf.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					wf.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.orgId != null">
					wf.orgId = #{model.orgId, jdbcType=INTEGER} AND
				</if>
				<if test="model.customInfo != null">
					<!-- wf.customInfo = #{model.customInfo, jdbcType=JSON} AND -->
					<foreach collection="model.customInfo" item="value" index="key">
					   wf.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
				    </foreach>
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					wf.processKey like CONCAT("%", #{fuzzy}, "%") or
					wf.taskKey like CONCAT("%", #{fuzzy}, "%") or
					wf.procInstId like CONCAT("%", #{fuzzy}, "%") or
					wf.message like CONCAT("%", #{fuzzy}, "%") or
					wf.status like CONCAT("%", #{fuzzy}, "%") or
					wf.objType like CONCAT("%", #{fuzzy}, "%") or
					wf.dataType like CONCAT("%", #{fuzzy}, "%") or
					wf.createBy like CONCAT("%", #{fuzzy}, "%") or
					wf.updateBy like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/PmWorkFlowMapper.xml:454 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:41 | fragment=Base_Column_List
projectId, projectType, projectCode, projectName, projectState, isback, column001, 
    column002, column003, column004, column005, column006, column007, column008, column009, 
    column010, column011, column012, columno12_readonly, column013, customerProjectName, 
    salesType, compId, createTime, createBy, updateTime, updateBy, effectiveFrom, effectiveTo, 
    projectStartTime, projectRefreshTime, projectCloseTime, customInfo, customConfig,
    column014

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:481 | fragment=sql_where_selective
<if test="projectId != null">
		ph.projectId = #{projectId, jdbcType=INTEGER} AND
	</if>
	<if test="projectType != null and '' != projectType">
		ph.projectType = #{projectType, jdbcType=VARCHAR} AND
	</if>
	<if test="projectCode != null and '' != projectCode">
		ph.projectCode = #{projectCode, jdbcType=VARCHAR} AND
	</if>
	<if test="projectName != null and '' != projectName">
		ph.projectName = #{projectName, jdbcType=VARCHAR} AND
	</if>
	<if test="projectState != null and '' != projectState">
		ph.projectState = #{projectState, jdbcType=VARCHAR} AND
	</if>
	<if test="isback != null and '' != isback">
		ph.isback = #{isback, jdbcType=VARCHAR} AND
	</if>
	<if test="column001 != null and '' != column001">
		ph.column001 = #{column001, jdbcType=VARCHAR} AND
	</if>
	<if test="column002 != null and '' != column002">
		ph.column002 = #{column002, jdbcType=VARCHAR} AND
	</if>
	<if test="column003 != null and '' != column003">
		ph.column003 = #{column003, jdbcType=VARCHAR} AND
	</if>
	<if test="column004 != null and '' != column004">
		ph.column004 = #{column004, jdbcType=VARCHAR} AND
	</if>
	<if test="column005 != null and '' != column005">
		ph.column005 = #{column005, jdbcType=VARCHAR} AND
	</if>
	<if test="column006 != null and '' != column006">
		ph.column006 = #{column006, jdbcType=VARCHAR} AND
	</if>
	<if test="column007 != null and '' != column007">
		ph.column007 = #{column007, jdbcType=VARCHAR} AND
	</if>
	<if test="column008 != null and '' != column008">
		ph.column008 = #{column008, jdbcType=VARCHAR} AND
	</if>
	<if test="column009 != null">
		ph.column009 = #{column009, jdbcType=TIMESTAMP} AND
	</if>
	<if test="column010 != null and '' != column010">
		ph.column010 = #{column010, jdbcType=VARCHAR} AND
	</if>
	<if test="column011 != null and '' != column011">
		ph.column011 = #{column011, jdbcType=VARCHAR} AND
	</if>
	<if test="column012 != null and '' != column012">
		ph.column012 = #{column012, jdbcType=VARCHAR} AND
	</if>
	<if test="columno12_readonly != null">
		ph.columno12_readonly = #{columno12_readonly, jdbcType=INTEGER} AND
	</if>
	<if test="column013 != null and '' != column013">
		ph.column013 = #{column013, jdbcType=VARCHAR} AND
	</if>
	<if test="customerProjectName != null and '' != customerProjectName">
		ph.customerProjectName = #{customerProjectName, jdbcType=VARCHAR} AND
	</if>
	<if test="salesType != null and '' != salesType">
		ph.salesType = #{salesType, jdbcType=VARCHAR} AND
	</if>
	<if test="compId != null">
		ph.compId = #{compId, jdbcType=INTEGER} AND
	</if>
	<if test="createTime != null">
		ph.createTime = #{createTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="createBy != null and '' != createBy">
		ph.createBy = #{createBy, jdbcType=VARCHAR} AND
	</if>
	<if test="updateTime != null">
		ph.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="updateBy != null and '' != updateBy">
		ph.updateBy = #{updateBy, jdbcType=VARCHAR} AND
	</if>
	<if test="effectiveFrom != null">
		<![CDATA[ ph.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
	</if>
	<if test="effectiveTo != null">
		<![CDATA[ (ph.effectiveTo is null or ph.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
	</if>
	<if test="disabled != null">
		ph.disabled = #{disabled, jdbcType=BIT} AND
	</if>
	<if test="projectStartTime != null">
		ph.projectStartTime = #{projectStartTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="projectRefreshTime != null">
		ph.projectRefreshTime = #{projectRefreshTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="projectCloseTime != null">
		ph.projectCloseTime = #{projectCloseTime, jdbcType=TIMESTAMP} AND
	</if>
	<if test="customInfo != null">
		ph.customInfo = #{customInfo, jdbcType=JSON} AND
	</if>
	<if test="customConfig != null">
		ph.customConfig = #{customConfig, jdbcType=JSON} AND
	</if>
	<if test="column014 != null and '' != column014">
		ph.column014 = #{column014, jdbcType=LONGVARCHAR} AND
	</if>
	<if test="projectTypes != null">
		FIND_IN_SET(ph.`projectType`, #{projectTypes}) AND 
	</if>
	<if test="officeCodes != null">
		FIND_IN_SET(ph.`column001`, #{officeCodes}) AND 
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:598 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.projectId != null">
			ph.projectId = #{model.projectId, jdbcType=INTEGER} AND
		</if>
		<if test="model.projectType != null and '' != model.projectType">
			ph.projectType = #{model.projectType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.projectCode != null and '' != model.projectCode">
			ph.projectCode like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.projectName != null and '' != model.projectName">
			(ph.projectName like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%") OR
			 ph.customInfo -> '$.projectAlias' like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%")
			) AND
		</if>
		<if test="model.projectState != null and '' != model.projectState">
			ph.projectState = #{model.projectState, jdbcType=VARCHAR} AND
		</if>
		<if test="model.isback != null and '' != model.isback">
			ph.isback = #{model.isback, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column001 != null and '' != model.column001">
			ph.column001 = #{model.column001, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column002 != null and '' != model.column002">
			ph.column002 = #{model.column002, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column003 != null and '' != model.column003">
			ph.column003 like concat("%", #{model.column003, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column004 != null and '' != model.column004">
			ph.column004 like concat("%", #{model.column004, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column005 != null and '' != model.column005">
			ph.column005 like concat("%", #{model.column005, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column006 != null and '' != model.column006">
			ph.column006 like concat("%", #{model.column006, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column007 != null and '' != model.column007">
			ph.column007 like concat("%", #{model.column007, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.column008 != null and '' != model.column008">
			ph.column008 = #{model.column008, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column009 != null">
			ph.column009 = #{model.column009, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.column010 != null and '' != model.column010">
			ph.column010 = #{model.column010, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column011 != null and '' != model.column011">
			ph.column011 = #{model.column011, jdbcType=VARCHAR} AND
		</if>
		<if test="model.column012 != null and '' != model.column012">
			ph.column012 = #{model.column012, jdbcType=VARCHAR} AND
		</if>
		<if test="model.columno12_readonly != null">
			ph.columno12_readonly = #{model.columno12_readonly, jdbcType=INTEGER} AND
		</if>
		<if test="model.column013 != null and '' != model.column013">
			ph.column013 like concat("%", #{model.column013, jdbcType=VARCHAR}, "%") AND
		</if>
		<if test="model.customerProjectName != null and '' != model.customerProjectName">
			ph.customerProjectName = #{model.customerProjectName, jdbcType=VARCHAR} AND
		</if>
		<if test="model.salesType != null and '' != model.salesType">
			ph.salesType = #{model.salesType, jdbcType=VARCHAR} AND
		</if>
		<if test="model.compId != null">
			ph.compId = #{model.compId, jdbcType=INTEGER} AND
		</if>
		<if test="model.createTime != null">
			ph.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.createBy != null and '' != model.createBy">
			ph.createBy = #{model.createBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.updateTime != null">
			ph.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.updateBy != null and '' != model.updateBy">
			ph.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
		</if>
		<if test="model.effectiveFrom != null">
			<![CDATA[ ph.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
		</if>
		<if test="model.effectiveTo != null">
			<![CDATA[ (ph.effectiveTo is null or ph.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
		</if>
		<if test="model.disabled != null">
			ph.disabled = #{model.disabled, jdbcType=BIT} AND
		</if>
		<if test="model.projectStartTime != null">
			ph.projectStartTime = #{model.projectStartTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.projectRefreshTime != null">
			ph.projectRefreshTime = #{model.projectRefreshTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.projectCloseTime != null">
			ph.projectCloseTime = #{model.projectCloseTime, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.customInfo != null">
			<!-- ph.customInfo = #{model.customInfo, jdbcType=JSON} AND -->
			<foreach collection="model.customInfo" item="value" index="key">
		    	ph.customInfo -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.customConfig != null">
			<!-- ph.customConfig = #{model.customConfig, jdbcType=JSON} AND -->
			<foreach collection="model.customConfig" item="value" index="key">
			   ph.customConfig -> '$.${key}' like concat("%", #{value}, "%") AND
		    </foreach>
		</if>
		<if test="model.column014 != null and '' != model.column014">
			ph.column014 = #{model.column014, jdbcType=LONGVARCHAR} AND
		</if>
		<if test="model.smsPredBidStartDate != null">
			ph.customInfo -> '$.smsPredBidDate' >= #{model.smsPredBidStartDate, jdbcType=TIMESTAMP} AND
		</if>
		<if test="model.smsPredBidEndDate != null">
			<![CDATA[ ph.customInfo -> '$.smsPredBidDate' <= #{model.smsPredBidEndDate, jdbcType=TIMESTAMP} AND ]]>
		</if>
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:725 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:733 | fragment=sql_fuzzy_search
<if test="fuzzySearch == true and fuzzy != ''">
		(
			ph.projectType like CONCAT("%", #{fuzzy}, "%") or
			ph.projectCode like CONCAT("%", #{fuzzy}, "%") or
			ph.projectName like CONCAT("%", #{fuzzy}, "%") or
			ph.projectState like CONCAT("%", #{fuzzy}, "%") or
			ph.isback like CONCAT("%", #{fuzzy}, "%") or
			ph.column001 like CONCAT("%", #{fuzzy}, "%") or
			ph.column002 like CONCAT("%", #{fuzzy}, "%") or
			ph.column003 like CONCAT("%", #{fuzzy}, "%") or
			ph.column004 like CONCAT("%", #{fuzzy}, "%") or
			ph.column005 like CONCAT("%", #{fuzzy}, "%") or
			ph.column006 like CONCAT("%", #{fuzzy}, "%") or
			ph.column007 like CONCAT("%", #{fuzzy}, "%") or
			ph.column008 like CONCAT("%", #{fuzzy}, "%") or
			ph.column010 like CONCAT("%", #{fuzzy}, "%") or
			ph.column011 like CONCAT("%", #{fuzzy}, "%") or
			ph.column012 like CONCAT("%", #{fuzzy}, "%") or
			ph.column013 like CONCAT("%", #{fuzzy}, "%") or
			ph.customerProjectName like CONCAT("%", #{fuzzy}, "%") or
			ph.salesType like CONCAT("%", #{fuzzy}, "%") or
			ph.customInfo like CONCAT("%", #{fuzzy}, "%") or
			ph.customConfig like CONCAT("%", #{fuzzy}, "%") or
			ph.column014 like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:762 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
			<include refid="sql_fuzzy_search" />
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:770 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by ph.projectId desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectHeaderMapper.xml:930 | fragment=uncreateProjectListSql
select distinct null as 
	    `projectId`, pp.projectCode as `projectCode`, null as `rank`, 
	    ifnull(pp.projectName, p.projectName) as `projectName`, 
	    <!-- `fd`.`basicDataName` as `projectStateName`, `fd`.`basicDataId` as `projectState`,  -->
	    ifnull(`p`.`contractNo`, pp.projectCode) as `contractNo`, 
	    ifnull(`p`.`orderExecNumber`, pp.orderExecNumber) as `smsOrderExecNumber`, 
	    ifnull(c1.id, c2.id) as compId, `pp`.`officeCode` as `officeCode`, 
	    ifnull(c1.compAbbr, c2.compAbbr) as compAbbr, ifnull(c1.compName, c2.compName) as compName,
	    `d`.`departmentName` as `officeName`, `pp`.`salesManCode` as `salesManCode`, 
	    concat(`pp`.`salesManCode`, "-", `pp`.`salesManName`) as `salesManName`, null as `projectStartTime`, 
	    null as projectCreateTime, `pp`.`systemName` as `systemName`, 
	    null as `serviceManager`, null as `serviceManagerName`, null as `projectManager`, 
	    null as `projectManagerName`, null as shipmentStateName, null as shipmentState, 
	    null as planStateName, null as projectPlanState, null as executionStateName, 
	    null as executionState, null as closeProcessStateName, null as closeProcessState, 
	    null as projectRefreshTime, null as projectCloseTime, null ssfs, null ssfsName, 
	    null partnerChannel, pp.projectType, pp.predBidDate as smsPredBidDate 
	from
	    `pm_project_property_af_from_sms` `pp` 
	    left join `pm_order_data_from_sap` `p` 
	        on `p`.`orderExecNumber` = `pp`.`orderExecNumber` 
	    left join `view_distinct_contract` `v` 
	        on `v`.`id` = `p`.`id` 
	    left join `fnd_department` `d` 
	        on `pp`.`officeCode` = `d`.`departmentNum` 
	    <!-- left join `fnd_basic_data` `fd` 
	        on `fd`.`basicDataId` = '10' 
	        and `fd`.`dataTypeCode` = concat(pp.projectType, "_projectState")  -->
  	<!-- <![CDATA[
	        and `fd`.`effectiveFrom` < now() 
	        and (
	            `fd`.`effectiveTo` > now() 
	            or isnull(`fd`.`effectiveTo`)
	        ) 
    ]]> -->
	    left join t_company c1 
	        on c1.compCode = p.compCode 
	    left join t_company c2 
	        on c2.`compCode` = pp.corporationCode 
	where
	<trim prefixOverrides="and | or" suffixOverrides="and | or">
		pp.`projectType` = 'afss' 
		<if test="model != null ">
			<if test="model.projectType != null and model.projectType !=''"> 
				and pp.projectType = #{model.projectType} 
			</if>
			<if test="model.projectCode != null and '' != model.projectCode">
				AND pp.projectCode like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.projectName != null and '' != model.projectName">
				AND IFNULL(pp.projectName, p.projectName) like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.contractNo != null and '' != model.contractNo">
				AND p.contractNo like concat("%", #{model.contractNo, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.smsOrderExecNumber != null and '' != model.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.smsOrderExecNumber != null and '' != model.customInfo.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.customInfo.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.salesManName != null and '' != model.salesManName">
				AND pp.salesManName like concat("%", #{model.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.salesManName != null and '' != model.customInfo.salesManName">
				AND pp.salesManName like concat("%", #{model.customInfo.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column001 != null and '' != model.column001">
				AND pp.officeCode = #{model.column001, jdbcType=VARCHAR}
			</if>
			<if test="model.column004 != null and '' != model.column004">
				AND pp.marketName like concat("%", #{model.column004, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column005 != null and '' != model.column005">
				AND pp.systemName like concat("%", #{model.column005, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column006 != null and '' != model.column006">
				AND pp.expendName like concat("%", #{model.column006, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column007 != null and '' != model.column007">
				AND pp.industryName like concat("%", #{model.column007, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.compId != null and '' != model.compId">
				AND #{model.compId, jdbcType=INTEGER} = IFNULL(c1.id, c2.id)
			</if>
			<if test="model.projectTypes != null">
				AND FIND_IN_SET(pp.`projectType`, #{model.projectTypes})
			</if>
			<if test="model.officeCodes != null">
				AND FIND_IN_SET(pp.`officeCode`, #{model.officeCodes})
			</if>
			<if test="model.smsPredBidStartDate != null">
				AND pp.predBidDate >= #{model.smsPredBidStartDate, jdbcType=TIMESTAMP}
			</if>
			<if test="model.smsPredBidEndDate != null">
				<![CDATA[ AND pp.predBidDate <= #{model.smsPredBidEndDate, jdbcType=TIMESTAMP} ]]>
			</if>
		</if>
		<if test="fuzzySearch == true and fuzzy != ''">
			AND (
				pp.projectCode like CONCAT("%", #{fuzzy}, "%") or
				pp.projectName like CONCAT("%", #{fuzzy}, "%") or
				p.contractNo like CONCAT("%", #{fuzzy}, "%")
			)
		</if>
	    and not exists 
	    (select 
	        1 
	    from
	        `pm_project_contract` `t1` 
	        join `pm_project_group_relationship` `t2` 
	            on `t1`.`projectGroupCode` = `t2`.`projectGroupCode` 
	        join `pm_project` `t3` 
	            on (
	                <!-- `t2`.`projectCode` = `t3`.`projectCode` 
	                or  -->
	                `t3`.`projectCode` = replace(`t2`.`projectCode`, "-his", "")
	            ) 
	<![CDATA[
	            and `t3`.`effectiveFrom` <= now() 
	            and (
	                `t3`.`effectiveTo` > now() 
	                or isnull(`t3`.`effectiveTo`)
	            ) 
    ]]>
	    where t3.`projectType` = pp.`projectType` 
	        and (
	            (
	                <!-- `v`.`contractNo` = `t1`.`contractNo` 
	                and v.contractNo is not null -->
	                <!-- 执行单已下单，但是没有签合同的项目 -->
	                `t1`.`contractNo` = IFNULL(`v`.`contractNo`, pp.`projectCode`)
	            )
	        )) 
	    and (
		    v.`contractNo` is not null 
		    <!-- 执行单已下单，但是没有签合同的项目 -->
		    OR NOT EXISTS 
            (SELECT 
                * 
            FROM
                `pm_project_property_af_from_sms` `t1` 
                INNER JOIN `pm_order_data_from_sap` `t2` 
                    ON `t1`.`orderExecNumber` = `t2`.`orderExecNumber` 
            WHERE t1.`projectCode` = pp.`projectCode`)
		)
    </trim>
	union
	select distinct null as 
	    `projectId`, pp.projectCode as `projectCode`, null as `rank`,
	    ifnull(pp.projectName, p.projectName)  as `projectName`, 
	    <!-- `fd`.`basicDataName` as `projectStateName`, `fd`.`basicDataId` as `projectState`,  -->
	    ifnull(`p`.`contractNo`, pp.projectCode) as `contractNo`, 
	    ifnull(`p`.`orderExecNumber`, pp.orderExecNumber) as `smsOrderExecNumber`, 
	    ifnull(c1.id, c2.id) as compId, `pp`.`officeCode` as `officeCode`, 
	    ifnull(c1.compAbbr, c2.compAbbr) as compAbbr, ifnull(c1.compName, c2.compName) as compName,
	    `d`.`departmentName` as `officeName`, `pp`.`salesManCode` as `salesManCode`, 
	    concat(`pp`.`salesManCode`, "-", `pp`.`salesManName`) as `salesManName`, null as `projectStartTime`, 
	    null as projectCreateTime, `pp`.`systemName` as `systemName`, 
	    null as `serviceManager`, null as `serviceManagerName`, null as `projectManager`, 
	    null as `projectManagerName`, null as shipmentStateName, null as shipmentState, 
	    null as planStateName, null as projectPlanState, null as executionStateName, 
	    null as executionState, null as closeProcessStateName, null as closeProcessState, 
	    null as projectRefreshTime, null as projectCloseTime, null ssfs, null ssfsName, 
	    null partnerChannel, pp.projectType, pp.predBidDate as smsPredBidDate 
	from
	    `pm_project_property_af_from_sms` `pp` 
	    left join `pm_order_data_from_sap` `p` 
	        on `p`.`orderExecNumber` = `pp`.`orderExecNumber` 
	    <!-- left join `view_distinct_contract` `v` 
	        on `v`.`id` = `p`.`id`  -->
	    left join `fnd_department` `d` 
	        on `pp`.`officeCode` = `d`.`departmentNum` 
	    <!-- left join `fnd_basic_data` `fd` 
	        on `fd`.`basicDataId` = '10' 
	        and `fd`.`dataTypeCode` = concat(pp.projectType, "_projectState")  -->
   <!-- <![CDATA[
	        and `fd`.`effectiveFrom` < now() 
	        and (
	            `fd`.`effectiveTo` > now() 
	            or isnull(`fd`.`effectiveTo`)
	        ) 
    ]]> -->
	    left join t_company c1 
	        on c1.compCode = p.compCode 
	    left join t_company c2 
	        on c2.`compCode` = pp.corporationCode 
	where 
	<trim prefixOverrides="and |or" suffixOverrides="and |or">
		pp.`projectType` = 'afxx' 
		<if test="model != null ">
			<if test="model.projectType != null and model.projectType !=''"> 
				and pp.projectType = #{model.projectType} 
			</if>
			<if test="model.projectCode != null and '' != model.projectCode">
				AND pp.projectCode like concat("%", #{model.projectCode, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.projectName != null and '' != model.projectName">
				AND IFNULL(pp.projectName, p.projectName) like concat("%", #{model.projectName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.contractNo != null and '' != model.contractNo">
				AND p.contractNo like concat("%", #{model.contractNo, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.smsOrderExecNumber != null and '' != model.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.smsOrderExecNumber != null and '' != model.customInfo.smsOrderExecNumber">
				AND pp.orderExecNumber like concat("%", #{model.customInfo.smsOrderExecNumber, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.salesManName != null and '' != model.salesManName">
				AND pp.salesManName like concat("%", #{model.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.customInfo != null and model.customInfo.salesManName != null and '' != model.customInfo.salesManName">
				AND pp.salesManName like concat("%", #{model.customInfo.salesManName, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column001 != null and '' != model.column001">
				AND pp.officeCode = #{model.column001, jdbcType=VARCHAR}
			</if>
			<if test="model.column004 != null and '' != model.column004">
				AND pp.marketName like concat("%", #{model.column004, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column005 != null and '' != model.column005">
				AND pp.systemName like concat("%", #{model.column005, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column006 != null and '' != model.column006">
				AND pp.expendName like concat("%", #{model.column006, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.column007 != null and '' != model.column007">
				AND pp.industryName like concat("%", #{model.column007, jdbcType=VARCHAR}, "%")
			</if>
			<if test="model.compId != null and '' != model.compId">
				AND #{model.compId, jdbcType=INTEGER} = IFNULL(c1.id, c2.id)
			</if>
			<if test="model.projectTypes != null">
				AND FIND_IN_SET(pp.`projectType`, #{model.projectTypes})
			</if>
			<if test="model.officeCodes != null">
				AND FIND_IN_SET(pp.`officeCode`, #{model.officeCodes})
			</if>
			<if test="model.smsPredBidStartDate != null">
				AND pp.predBidDate >= #{model.smsPredBidStartDate, jdbcType=TIMESTAMP}
			</if>
			<if test="model.smsPredBidEndDate != null">
				<![CDATA[ AND pp.predBidDate <= #{model.smsPredBidEndDate, jdbcType=TIMESTAMP} ]]>
			</if>
		</if>
		<if test="fuzzySearch == true and fuzzy != ''">
			AND (
				pp.projectCode like CONCAT("%", #{fuzzy}, "%") or
				pp.projectName like CONCAT("%", #{fuzzy}, "%") or
				p.contractNo like CONCAT("%", #{fuzzy}, "%")
			)
		</if>
	    and not exists 
	    (select 
	        1 
	    from
	        `pm_project_contract` `t1` 
	        join `pm_project_group_relationship` `t2` 
	            on `t1`.`projectGroupCode` = `t2`.`projectGroupCode` 
	        join `pm_project` `t3` 
	            on (
	               <!--  `t2`.`projectCode` = `t3`.`projectCode` 
	                or  -->
	                `t3`.`projectCode` = replace(`t2`.`projectCode`, "-his", "")
	            ) 
	<![CDATA[
	            and `t3`.`effectiveFrom` <= now() 
	            and (
	                `t3`.`effectiveTo` > now() 
	                or isnull(`t3`.`effectiveTo`)
	            ) 
    ]]>
	    where t3.`projectType` = pp.`projectType` 
	        <!-- and pp.`orderExecNumber` = t1.`contractNo` -->
	         and pp.`projectCode` = t1.`contractNo`
	        ) 
	</trim>
	union all
	SELECT  
	    ph.`projectId`, `projectCode`, NULL AS `rank`, `projectName`, 
	    <!-- ph.customInfo ->> "$.projectStateName" AS `projectStateName`, `fd`.`basicDataId` AS `projectState`,  -->
	    ph.customInfo ->> "$.contractNo" AS `contractNo`, 
	    ph.customInfo ->> "$.smsOrderExecNumber" AS `smsOrderExecNumber`, 
	    ph.compId, ph.`column001` AS `officeCode`, 
	    c1.compAbbr as compAbbr, c1.compName as compName,
	    ph.customInfo ->> "$.officeName" AS `officeName`, ph.customInfo ->> "$.salesManCode" AS `salesManCode`, 
	    ph.customInfo ->> "$.salesManName" AS `salesManName`, ph.`projectStartTime`, ph.createTime AS projectCreateTime, 
	    ph.`column005` AS `systemName`, 
	    ph.customInfo ->> "$.serviceManagerCode" AS `serviceManager`, ph.customInfo ->> "$.serviceManagerforjson" AS `serviceManagerName`, 
	    ph.customInfo ->> "$.programManagerCode" AS `projectManager`, ph.customInfo ->> "$.programManagerCodeforjson" AS `projectManagerName`, 
	    NULL AS shipmentStateName, NULL AS shipmentState, NULL AS planStateName, NULL AS projectPlanState, 
	    NULL AS executionStateName, NULL AS executionState, NULL AS closeProcessStateName, 
	    NULL AS closeProcessState, NULL AS projectRefreshTime, NULL AS projectCloseTime, 
	    NULL ssfs, NULL ssfsName, NULL partnerChannel, ph.projectType, 
	    ph.customInfo ->> "$.smsPredBidDate" AS smsPredBidDate 
	FROM pm_project ph
	   <!--  LEFT JOIN `fnd_basic_data` `fd` 
		ON `fd`.`basicDataId` = '10' 
		AND `fd`.`dataTypeCode` = CONCAT(ph.projectType, "_projectState") -->
		left join t_company c1 
	        on c1.id = ph.compId
	<include refid="sql_model_where"></include>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectManageUserMapper.xml:26 | fragment=sql_where_selective
<if test="userId != null and '' != userId">
        u.user_id = #{userId, jdbcType=INTEGER} AND
    </if>
    <if test="userName != null and '' != userName">
        u.user_name like CONCAT("%", #{userName}, "%") AND
    </if>
    <if test="password != null and '' != password">
        u.password = #{password, jdbcType=VARCHAR} AND
    </if>
    <if test="createBy != null and '' != createBy">
        u.create_by = #{createBy, jdbcType=VARCHAR} AND
    </if>
    <if test="createTime != null">
        u.create_time = #{createTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="updateBy != null and '' != updateBy">
        u.update_by = #{updateBy, jdbcType=VARCHAR} AND
    </if>
    <if test="updateTime != null">
        u.update_time = #{updateTime, jdbcType=TIMESTAMP} AND
    </if>
    <if test="status != null">
        u.status = #{status, jdbcType=SMALLINT} AND
    </if>
    <if test="needChangePwd != null and '' != needChangePwd">
        u.needChangePwd = #{needChangePwd, jdbcType=BIT} AND
    </if>
    <if test="isSysUser != null and '' != isSysUser">
        u.isSysUser = #{isSysUser, jdbcType=SMALLINT} AND
    </if>
    <if test="loginErrorCount != null and '' != loginErrorCount">
        u.loginErrorCount = #{loginErrorCount, jdbcType=INTEGER} AND
    </if>
    <if test="userCustom1 != null and '' != userCustom1">
        u.userCustom1 = #{userCustom1, jdbcType=VARCHAR} AND
    </if>
    <if test="userCustom2 != null and '' != userCustom2">
        u.userCustom2 = #{userCustom2, jdbcType=VARCHAR} AND
    </if>
    <if test="userCustom3 != null and '' != userCustom3">
        u.userCustom3 = #{userCustom3, jdbcType=VARCHAR} AND
    </if>
    <if test="userCustom4 != null and '' != userCustom4">
        u.userCustom4 = #{userCustom4, jdbcType=INTEGER} AND
    </if>
    <if test="userCustom5 != null and '' != userCustom5">
        u.userCustom5 = #{userCustom5, jdbcType=INTEGER} AND
    </if>
    <if test="email != null">
     	ui.email like CONCAT("%", #{email}, "%") AND
	</if>
	<if test="realName != null">
	    ui.realName like CONCAT("%", #{realName}, "%") AND
	</if>
	<if test="sex != null">
	    ui.sex = #{sex} AND
	</if>
	<if test="compID != null and compID != -1">
        ui.compID = #{compID} AND
    </if>
	<if test="roleId != null">
	    ur.role_id = #{roleId} AND
	</if>
	<if test="roles != null">
	    FIND_IN_SET(r.role_name, #{roles}) AND
	</if>
	<if test="projectTypes != null and projectTypes != ''">
		exists 
	    (select 
	        * 
	    from
	        (select 
	            substring_index(
	                substring_index(#{projectTypes}, ',', b.id + 1), ',', - 1
	            ) as projectType 
	        from
	            FIND_IN_SET_HELP b 
	        where (
	                length(#{projectTypes}) - length(replace(#{projectTypes}, ',', '')) + 1
	            ) > b.id) t 
	    where FIND_IN_SET(t.projectType, ui.custom4)) AND
	</if>
	<if test="officeCodes != null and officeCodes != ''">
		exists 
	    (select 
	        * 
	    from
	        (select 
	            substring_index(
	                substring_index(#{officeCodes}, ',', b.id + 1), ',', - 1
	            ) as officeCode 
	        from
	            FIND_IN_SET_HELP b 
	        where (
	                length(#{officeCodes}) - length(replace(#{officeCodes}, ',', '')) + 1
	            ) > b.id) t 
	    where FIND_IN_SET(t.officeCode, ui.custom5)) AND
	</if>
	<if test="custom4 != null and custom4 != ''">
		exists 
	    (select 
	        * 
	    from
	        (select 
	            substring_index(
	                substring_index(#{custom4}, ',', b.id + 1), ',', - 1
	            ) as projectType 
	        from
	            FIND_IN_SET_HELP b 
	        where (
	                length(#{custom4}) - length(replace(#{custom4}, ',', '')) + 1
	            ) > b.id) t 
	    where find_in_set(t.projectType, ui.custom4)) AND
	</if>
	<if test="custom5 != null and custom5 != ''">
		exists 
	    (select 
	        * 
	    from
	        (select 
	            substring_index(
	                substring_index(#{custom5}, ',', b.id + 1), ',', - 1
	            ) as officeCode 
	        from
	            FIND_IN_SET_HELP b 
	        where (
	                length(#{custom5}) - length(replace(#{custom5}, ',', '')) + 1
	            ) > b.id) t 
	    where find_in_set(t.officeCode, ui.custom5)) AND
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectManageUserMapper.xml:158 | fragment=sql_model_where_selective
<if test="model != null ">
		<if test="model.userId != null and '' != model.userId">
            u.user_id = #{model.userId, jdbcType=INTEGER} AND
        </if>
        <if test="model.userName != null and '' != model.userName">
            u.user_name like CONCAT("%", #{model.userName}, "%") AND
        </if>
        <if test="model.password != null and '' != model.password">
            u.password = #{model.password, jdbcType=VARCHAR} AND
        </if>
        <if test="model.createBy != null and '' != model.createBy">
            u.create_by = #{model.createBy, jdbcType=VARCHAR} AND
        </if>
        <if test="model.createTime != null">
            u.create_time = #{model.createTime, jdbcType=TIMESTAMP} AND
        </if>
        <if test="model.updateBy != null and '' != model.updateBy">
            u.update_by = #{model.updateBy, jdbcType=VARCHAR} AND
        </if>
        <if test="model.updateTime != null">
            u.update_time = #{model.updateTime, jdbcType=TIMESTAMP} AND
        </if>
        <if test="model.status != null">
            u.status = #{model.status, jdbcType=SMALLINT} AND
        </if>
        <if test="model.needChangePwd != null and '' != model.needChangePwd">
            u.needChangePwd = #{model.needChangePwd, jdbcType=BIT} AND
        </if>
        <if test="model.isSysUser != null and '' != model.isSysUser">
            u.isSysUser = #{model.isSysUser, jdbcType=SMALLINT} AND
        </if>
        <if test="model.loginErrorCount != null and '' != model.loginErrorCount">
            u.loginErrorCount = #{model.loginErrorCount, jdbcType=INTEGER} AND
        </if>
        <if test="model.userCustom1 != null and '' != model.userCustom1">
            u.userCustom1 = #{model.userCustom1, jdbcType=VARCHAR} AND
        </if>
        <if test="model.userCustom2 != null and '' != model.userCustom2">
            u.userCustom2 = #{model.userCustom2, jdbcType=VARCHAR} AND
        </if>
        <if test="model.userCustom3 != null and '' != model.userCustom3">
            u.userCustom3 = #{model.userCustom3, jdbcType=VARCHAR} AND
        </if>
        <if test="model.userCustom4 != null and '' != model.userCustom4">
            u.userCustom4 = #{model.userCustom4, jdbcType=INTEGER} AND
        </if>
        <if test="model.userCustom5 != null and '' != model.userCustom5">
            u.userCustom5 = #{model.userCustom5, jdbcType=INTEGER} AND
        </if>
        <if test="model.email != null">
         	ui.email like CONCAT("%", #{model.email}, "%") AND
		</if>
		<if test="model.realName != null">
		    ui.realName like CONCAT("%", #{model.realName}, "%") AND
		</if>
		<if test="model.sex != null">
		    ui.sex = #{model.sex} AND
		</if>
		<if test="model.compID != null and model.compID != -1">
		    ui.compID = #{model.compID} AND
		</if>
		<if test="model.roleId != null">
		    ur.role_id = #{model.roleId} AND
		</if>
		<if test="model.roles != null">
		    FIND_IN_SET(r.role_name, #{model.roles}) AND
		</if>
		<if test="model.projectTypes != null and model.projectTypes != ''">
			exists 
		    (select 
		        * 
		    from
		        (select 
		            substring_index(
		                substring_index(#{model.projectTypes}, ',', b.id + 1), ',', - 1
		            ) as projectType 
		        from
		            FIND_IN_SET_HELP b 
		        where (
		                length(#{model.projectTypes}) - length(replace(#{model.projectTypes}, ',', '')) + 1
		            ) > b.id) t 
		    where FIND_IN_SET(t.projectType, ui.custom4)) AND
		</if>
		<if test="model.officeCodes != null and model.officeCodes != ''">
			exists 
		    (select 
		        * 
		    from
		        (select 
		            substring_index(
		                substring_index(#{model.officeCodes}, ',', b.id + 1), ',', - 1
		            ) as officeCode 
		        from
		            FIND_IN_SET_HELP b 
		        where (
		                length(#{model.officeCodes}) - length(replace(#{model.officeCodes}, ',', '')) + 1
		            ) > b.id) t 
		    where FIND_IN_SET(t.officeCode, ui.custom5)) AND
		</if>
		<if test="model.custom4 != null and model.custom4 != ''">
			exists 
		    (select 
		        * 
		    from
		        (select 
		            substring_index(
		                substring_index(#{model.custom4}, ',', b.id + 1), ',', - 1
		            ) as projectType 
		        from
		            FIND_IN_SET_HELP b 
		        where (
		                length(#{model.custom4}) - length(replace(#{model.custom4}, ',', '')) + 1
		            ) > b.id) t 
		    where FIND_IN_SET(t.projectType, ui.custom4)) AND
		</if>
		<if test="model.custom5 != null and model.custom5 != ''">
			exists 
		    (select 
		        * 
		    from
		        (select 
		            substring_index(
		                substring_index(#{model.custom5}, ',', b.id + 1), ',', - 1
		            ) as officeCode 
		        from
		            FIND_IN_SET_HELP b 
		        where (
		                length(#{model.custom5}) - length(replace(#{model.custom5}, ',', '')) + 1
		            ) > b.id) t 
		    where FIND_IN_SET(t.officeCode, ui.custom5)) AND
		</if>
	</if>
	<if test="fuzzySearch == true and fuzzy != ''">
		(
			u.user_name like CONCAT("%", #{fuzzy}, "%") or
            u.password like CONCAT("%", #{fuzzy}, "%") or
            u.create_by like CONCAT("%", #{fuzzy}, "%") or
            u.update_by like CONCAT("%", #{fuzzy}, "%") or
            u.user_name like CONCAT("%", #{fuzzy}, "%") or
            u.userCustom1 like CONCAT("%", #{fuzzy}, "%") or
            u.userCustom2 like CONCAT("%", #{fuzzy}, "%") or
            u.userCustom3 like CONCAT("%", #{fuzzy}, "%") or
		    ui.email like CONCAT("%", #{fuzzy}, "%") or
		    ui.realName like CONCAT("%", #{fuzzy}, "%") or
		    ui.custom3 like CONCAT("%", #{fuzzy}, "%") or
		    ui.custom4 like CONCAT("%", #{fuzzy}, "%") or
		    ui.custom5 like CONCAT("%", #{fuzzy}, "%") or
		    r.role_name like CONCAT("%", #{fuzzy}, "%") or
		    r.role_name_zn like CONCAT("%", #{fuzzy}, "%")
		)
	</if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectManageUserMapper.xml:312 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_where_selective" />
		</trim>
	</where>
	GROUP BY u.user_id

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectManageUserMapper.xml:320 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<include refid="sql_model_where_selective" />
		</trim>
	</where>
	GROUP BY u.user_id

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectManageUserMapper.xml:328 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by u.user_id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectMapper.xml:39 | fragment=Base_Column_List
id, projectCode, projectName, projectState, officeCode, customerCode, customerName, 
    marketName, systemName, expendName, industryName, notGrantTailCause, orderCreateTime, 
    projectType, projectCategory, implType, implReadonly, finalCustomerName, backReason, 
    customerProjectName, salesType, compId, createTime, createBy, updateTime, updateBy, 
    effectiveFrom, effectiveTo, projectStartTime, projectEndTime, projectRefreshTime, 
    customInfo, customConfig

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectMemberMapper.xml:21 | fragment=Base_Column_List
id, projectId, projectType, memberRole, memberCode, memberName, phoneNum, email, 
    fromFlag, createTime, createBy, updateTime, updateBy, effectiveTo, effectiveFrom

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectMemberMapper.xml:216 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="id != null">
				m.id = #{id, jdbcType=INTEGER} AND
			</if>
			<if test="projectId != null">
				m.projectId = #{projectId, jdbcType=INTEGER} AND
			</if>
			<if test="projectType != null and '' != projectType">
				m.projectType = #{projectType, jdbcType=VARCHAR} AND
			</if>
			<if test="memberRole != null and '' != memberRole">
				m.memberRole = #{memberRole, jdbcType=VARCHAR} AND
			</if>
			<if test="memberCode != null and '' != memberCode">
				m.memberCode = #{memberCode, jdbcType=VARCHAR} AND
			</if>
			<if test="memberName != null and '' != memberName">
				m.memberName = #{memberName, jdbcType=VARCHAR} AND
			</if>
			<if test="phoneNum != null and '' != phoneNum">
				m.phoneNum = #{phoneNum, jdbcType=VARCHAR} AND
			</if>
			<if test="email != null and '' != email">
				m.email = #{email, jdbcType=VARCHAR} AND
			</if>
			<if test="fromFlag != null and '' != fromFlag">
				m.fromFlag = #{fromFlag, jdbcType=VARCHAR} AND
			</if>
			<if test="createTime != null">
				m.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="createBy != null and '' != createBy">
				m.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				m.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				m.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="effectiveTo != null">
				<![CDATA[ (m.effectiveTo is null or m.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ m.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectMemberMapper.xml:267 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.id != null">
					m.id = #{model.id, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectId != null">
					m.projectId = #{model.projectId, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectType != null and '' != model.projectType">
					m.projectType = #{model.projectType, jdbcType=VARCHAR} AND
				</if>
				<if test="model.memberRole != null and '' != model.memberRole">
					m.memberRole = #{model.memberRole, jdbcType=VARCHAR} AND
				</if>
				<if test="model.memberCode != null and '' != model.memberCode">
					m.memberCode = #{model.memberCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.memberName != null and '' != model.memberName">
					m.memberName = #{model.memberName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.phoneNum != null and '' != model.phoneNum">
					m.phoneNum = #{model.phoneNum, jdbcType=VARCHAR} AND
				</if>
				<if test="model.email != null and '' != model.email">
					m.email = #{model.email, jdbcType=VARCHAR} AND
				</if>
				<if test="model.fromFlag != null and '' != model.fromFlag">
					m.fromFlag = #{model.fromFlag, jdbcType=VARCHAR} AND
				</if>
				<if test="model.createTime != null">
					m.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					m.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					m.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					m.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.effectiveTo != null">
					<![CDATA[ (m.effectiveTo is null or m.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ m.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					m.projectType like CONCAT("%", #{fuzzy}, "%") or
					m.memberRole like CONCAT("%", #{fuzzy}, "%") or
					m.memberCode like CONCAT("%", #{fuzzy}, "%") or
					m.memberName like CONCAT("%", #{fuzzy}, "%") or
					m.phoneNum like CONCAT("%", #{fuzzy}, "%") or
					m.email like CONCAT("%", #{fuzzy}, "%") or
					m.fromFlag like CONCAT("%", #{fuzzy}, "%") or
					m.createBy like CONCAT("%", #{fuzzy}, "%") or
					m.updateBy like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectMemberMapper.xml:333 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by id desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:34 | fragment=Base_Column_List
taskId, projectId, projectType, contractNo, taskTypeCode, taskTypeId, taskName, eventPlanHappenDate, 
    eventPlanHappenDateENG, planStartTime, planEndTime, actualStartTime, eventActualFinishDate, 
    priority, progress, progressDesc, status, parentId, createTime, createBy, updateTime, 
    updateBy, effectiveFrom, effectiveTo, visibleFlag, deliverFileIds, customInfo,
    remark

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:386 | fragment=sql_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="taskId != null">
				t.taskId = #{taskId, jdbcType=INTEGER} AND
			</if>
			<if test="projectId != null">
				t.projectId = #{projectId, jdbcType=INTEGER} AND
			</if>
			<if test="projectType != null and '' != projectType">
				t.projectType = #{projectType, jdbcType=VARCHAR} AND
			</if>
			<if test="contractNo != null and '' != contractNo">
				t.contractNo = #{contractNo, jdbcType=VARCHAR} AND
			</if>
			<if test="taskTypeCode != null and '' != taskTypeCode">
				t.taskTypeCode = #{taskTypeCode, jdbcType=VARCHAR} AND
			</if>
			<if test="taskTypeId != null and '' != taskTypeId">
				t.taskTypeId = #{taskTypeId, jdbcType=VARCHAR} AND
			</if>
			<if test="taskName != null and '' != taskName">
				t.taskName = #{taskName, jdbcType=VARCHAR} AND
			</if>
			<if test="eventPlanHappenDate != null">
				t.eventPlanHappenDate = #{eventPlanHappenDate, jdbcType=TIMESTAMP} AND
			</if>
			<if test="eventPlanHappenDateENG != null">
				t.eventPlanHappenDateENG = #{eventPlanHappenDateENG, jdbcType=TIMESTAMP} AND
			</if>
			<if test="planStartTime != null">
				t.planStartTime = #{planStartTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="planEndTime != null">
				t.planEndTime = #{planEndTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="actualStartTime != null">
				t.actualStartTime = #{actualStartTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="eventActualFinishDate != null">
				t.eventActualFinishDate = #{eventActualFinishDate, jdbcType=TIMESTAMP} AND
			</if>
			<if test="priority != null and '' != priority">
				t.priority = #{priority, jdbcType=VARCHAR} AND
			</if>
			<if test="progress != null">
				t.progress = #{progress, jdbcType=INTEGER} AND
			</if>
			<if test="progressDesc != null and '' != progressDesc">
				t.progressDesc = #{progressDesc, jdbcType=VARCHAR} AND
			</if>
			<if test="status != null and '' != status">
				t.status = #{status, jdbcType=VARCHAR} AND
			</if>
			<if test="parentId != null">
				t.parentId = #{parentId, jdbcType=INTEGER} AND
			</if>
			<if test="createTime != null">
				t.createTime = #{createTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="createBy != null and '' != createBy">
				t.createBy = #{createBy, jdbcType=VARCHAR} AND
			</if>
			<if test="updateTime != null">
				t.updateTime = #{updateTime, jdbcType=TIMESTAMP} AND
			</if>
			<if test="updateBy != null and '' != updateBy">
				t.updateBy = #{updateBy, jdbcType=VARCHAR} AND
			</if>
			<if test="effectiveFrom != null">
				<![CDATA[ t.effectiveFrom <= #{effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
			</if>
			<if test="effectiveTo != null">
				<![CDATA[ (t.effectiveTo is null or t.effectiveTo >= #{effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
			</if>
			<if test="visibleFlag != null and '' != visibleFlag">
				t.visibleFlag = #{visibleFlag, jdbcType=VARCHAR} AND
			</if>
			<if test="deliverFileIds != null and '' != deliverFileIds">
				t.deliverFileIds = #{deliverFileIds, jdbcType=VARCHAR} AND
			</if>
			<if test="customInfo != null">
				t.customInfo = #{customInfo, jdbcType=JSON} AND
			</if>
			<if test="remark != null and '' != remark">
				t.remark = #{remark, jdbcType=LONGVARCHAR} AND
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:476 | fragment=sql_model_where
<where>
		<trim prefixOverrides=" and | or " suffixOverrides=" and | or ">
			<if test="model != null ">
				<if test="model.taskId != null">
					t.taskId = #{model.taskId, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectId != null">
					t.projectId = #{model.projectId, jdbcType=INTEGER} AND
				</if>
				<if test="model.projectType != null and '' != model.projectType">
					t.projectType = #{model.projectType, jdbcType=VARCHAR} AND
				</if>
				<if test="model.contractNo != null and '' != model.contractNo">
					t.contractNo = #{model.contractNo, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskTypeCode != null and '' != model.taskTypeCode">
					t.taskTypeCode = #{model.taskTypeCode, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskTypeId != null and '' != model.taskTypeId">
					t.taskTypeId = #{model.taskTypeId, jdbcType=VARCHAR} AND
				</if>
				<if test="model.taskName != null and '' != model.taskName">
					t.taskName = #{model.taskName, jdbcType=VARCHAR} AND
				</if>
				<if test="model.eventPlanHappenDate != null">
					t.eventPlanHappenDate = #{model.eventPlanHappenDate, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.eventPlanHappenDateENG != null">
					t.eventPlanHappenDateENG = #{model.eventPlanHappenDateENG, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.planStartTime != null">
					t.planStartTime = #{model.planStartTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.planEndTime != null">
					t.planEndTime = #{model.planEndTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.actualStartTime != null">
					t.actualStartTime = #{model.actualStartTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.eventActualFinishDate != null">
					t.eventActualFinishDate = #{model.eventActualFinishDate, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.priority != null and '' != model.priority">
					t.priority = #{model.priority, jdbcType=VARCHAR} AND
				</if>
				<if test="model.progress != null">
					t.progress = #{model.progress, jdbcType=INTEGER} AND
				</if>
				<if test="model.progressDesc != null and '' != model.progressDesc">
					t.progressDesc = #{model.progressDesc, jdbcType=VARCHAR} AND
				</if>
				<if test="model.status != null and '' != model.status">
					t.status = #{model.status, jdbcType=VARCHAR} AND
				</if>
				<if test="model.parentId != null">
					t.parentId = #{model.parentId, jdbcType=INTEGER} AND
				</if>
				<if test="model.createTime != null">
					t.createTime = #{model.createTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.createBy != null and '' != model.createBy">
					t.createBy = #{model.createBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.updateTime != null">
					t.updateTime = #{model.updateTime, jdbcType=TIMESTAMP} AND
				</if>
				<if test="model.updateBy != null and '' != model.updateBy">
					t.updateBy = #{model.updateBy, jdbcType=VARCHAR} AND
				</if>
				<if test="model.effectiveFrom != null">
					<![CDATA[ t.effectiveFrom <= #{model.effectiveFrom, jdbcType=TIMESTAMP} ]]> AND
				</if>
				<if test="model.effectiveTo != null">
					<![CDATA[ (t.effectiveTo is null or t.effectiveTo >= #{model.effectiveTo, jdbcType=TIMESTAMP}) ]]> AND
				</if>
				<if test="model.visibleFlag != null and '' != model.visibleFlag">
					t.visibleFlag = #{model.visibleFlag, jdbcType=VARCHAR} AND
				</if>
				<if test="model.deliverFileIds != null and '' != model.deliverFileIds">
					t.deliverFileIds = #{model.deliverFileIds, jdbcType=VARCHAR} AND
				</if>
				<if test="model.customInfo != null">
					t.customInfo = #{model.customInfo, jdbcType=JSON} AND
				</if>
				<if test="model.remark != null and '' != model.remark">
					t.remark = #{model.remark, jdbcType=LONGVARCHAR} AND
				</if>
			</if>
			<if test="fuzzySearch == true and fuzzy != ''">
				(
					t.projectType like CONCAT("%", #{fuzzy}, "%") or
					t.contractNo like CONCAT("%", #{fuzzy}, "%") or
					t.taskTypeCode like CONCAT("%", #{fuzzy}, "%") or
					t.taskTypeId like CONCAT("%", #{fuzzy}, "%") or
					t.taskName like CONCAT("%", #{fuzzy}, "%") or
					t.priority like CONCAT("%", #{fuzzy}, "%") or
					t.progressDesc like CONCAT("%", #{fuzzy}, "%") or
					t.status like CONCAT("%", #{fuzzy}, "%") or
					t.createBy like CONCAT("%", #{fuzzy}, "%") or
					t.updateBy like CONCAT("%", #{fuzzy}, "%") or
					t.visibleFlag like CONCAT("%", #{fuzzy}, "%") or
					t.deliverFileIds like CONCAT("%", #{fuzzy}, "%") or
					t.remark like CONCAT("%", #{fuzzy}, "%")
				)
			</if>
		</trim>
	</where>

-- D:/IDEAWorkspace/PMS/PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/mapping/ProjectTaskMapper.xml:585 | fragment=sql_pageable_limit
<if test="orderBy != null">
        order by ${orderBy}
    </if>
    <if test="orderBy == null">
        order by taskId desc
    </if>
    <if test="start != null and pageSize != -1">
        limit #{start}, #{pageSize}
    </if>

-- D:/IDEAWorkspace/PMS/PMS-struts/bin/sql-map-maintenance-config.xml:1419 | fragment=selectProjectWarrantyState
SELECT 
        ph.`projectId`, ph.`projectCode`, ph.`projectName`, pg.projectType,
        ph.`column001` as officeCode, fd.`departmentName` as officeName, 
        ph.column004 AS marketName, ph.column005 AS systemName, 
	    ph.column006 AS expendName, ph.column007 AS industryName,
	    ph.column013 AS finalCustomerName,
        group_concat(pc.contractNo) as contractNos,
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))) warrantyStatus, 
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
            )
        ) AS warrantyStatusName,
        MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
        MAX(wcs.`warrantyGrade`) warrantyGrade, 
        IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
            )
        ) AS warrantyGradeName,
        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`warrantyGradeStartTime`) AND MAX(wcs.`warrantyGradeEndTime`) >= DATE(NOW()), "true", "false") AS warrantyGradeEnable,
        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
        IFNULL(MAX(wcs.`wafService`), 0) wafService,
        IF(MAX(wcs.`wafService`) = 1, "策略调优服务", NULL) AS  wafServiceName,
        MIN(wcs.`wafServiceStartTime`) wafServiceStartTime,
        MAX(wcs.`wafServiceEndTime`) wafServiceEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`wafServiceStartTime`) AND MAX(wcs.`wafServiceEndTime`) >= DATE(NOW()), "true", "false") AS wafServiceEnable,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.itemDesc, "(", DATE(wcs.`wafServiceStartTime`), "~", DATE(wcs.`wafServiceEndTime`), ")") SEPARATOR "\r\n") AS warrantyServiceDesc
    FROM
        `pm_project_header` ph 
        LEFT JOIN `pm_project_group_relationship` pgr 
            on ph.`projectCode` = pgr.`projectCode` 
        LEFT JOIN `pm_project_group` pg
            on pg.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `pm_project_contract` pc 
            on pc.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `view_warranty_contract_state` wcs 
            on wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
        LEFT JOIN fnd_department fd
            on fd.`departmentNum` = ph.`column001`
    WHERE ph.`effectiveTo` is null

-- D:/IDEAWorkspace/PMS/PMS-struts/bin/sql-map-maintenance-config.xml:1705 | fragment=sqlProjectMaintenanceServiceDeliveryWhere
<isNotEmpty property="serviceTypes">
        <!-- LEFT JOIN (
            <iterate property="serviceTypes" conjunction=" UNION ALL ">
                SELECT #serviceTypes[]# as serviceType
            </iterate>
        ) st
        ON 1 = 1 -->
        LEFT JOIN (SELECT 
                <!-- dataTypeCode, --> basicDataId as serviceType, basicDataAttri1 as deliverId 
	        FROM
	            `fnd_basic_data` 
	        WHERE dataTypeCode = 'projectServiceDeliveryType') st
            ON 1 = 1 <!-- st.dataTypeCode = 'projectServiceDeliveryType'  -->
    </isNotEmpty>
    <isNotEmpty property="hasQuarterDeliveried">
        LEFT JOIN (<include refid="sqlProjectServiceDeliver" />) sd
        ON sd.projectId = wcs.projectId AND sd.serviceType = st.serviceType
    </isNotEmpty>
    <isNotEmpty property="joinProjectMembers">
        LEFT JOIN pm_project_member sm 
            ON wcs.`projectId` = sm.`projectId` 
            AND wcs.`projectType` = sm.`projectType` 
            AND sm.`memberRole` = '20' 
            AND sm.`effectiveTo` IS NULL 
            AND sm.`fromFlag` = 1 
        LEFT JOIN pm_project_member pm 
            ON wcs.`projectId` = pm.`projectId` 
            AND wcs.`projectType` = pm.`projectType` 
            AND pm.`memberRole` = '30' 
            AND pm.`effectiveTo` IS NULL 
            AND pm.`fromFlag` = 1 
        LEFT JOIN pm_project_member pm2 
            ON wcs.`projectId` = pm2.`projectId` 
            AND wcs.`projectType` = pm2.`projectType` 
            AND pm2.`memberRole` = '30' 
            AND pm2.`effectiveTo` IS NULL 
            AND pm2.`fromFlag` = 2 
        LEFT JOIN `pm_project_member` s 
            ON s.`projectId` = wcs.`projectId` 
            AND s.`projectType` = wcs.`projectType` 
            AND s.`memberRole` = "10" 
            AND s.`effectiveTo` IS NULL 
    </isNotEmpty>
    <dynamic prepend="where">
        <isNotEmpty prepend="AND" property="serviceTypes">
            <iterate property="serviceTypes" open="(" close=")" conjunction=" or ">
                <isNotEqual property="serviceQuarter" compareValue="true">
	                <isNotEmpty property="serviceDate" >
	                (
	                    #serviceDate# between wcs.`$serviceTypes[]$StartTime` 
	                    and wcs.`$serviceTypes[]$EndTime` 
	                    and st.serviceType = #serviceTypes[]#
	                )
	                </isNotEmpty>
                </isNotEqual>
                <isEqual property="serviceQuarter" compareValue="true">
                    <isNotEmpty property="serviceDate" >
		                <!-- <![CDATA[
		                (
		                    #serviceQuarter# >= CONCAT(YEAR(wcs.`$serviceTypes[]$StartTime`), QUARTER(wcs.`$serviceTypes[]$StartTime`))
		                    AND #serviceQuarter# <= CONCAT(YEAR(wcs.`$serviceTypes[]$EndTime`), QUARTER(wcs.`$serviceTypes[]$EndTime`))
		                    AND st.serviceType = #serviceTypes[]#
		                )
		                ]]> -->
		                <![CDATA[
                        (
                            CONCAT(YEAR(#serviceDate#), QUARTER(#serviceDate#)) >= CONCAT(YEAR(wcs.`$serviceTypes[]$StartTime`), QUARTER(wcs.`$serviceTypes[]$StartTime`))
                            AND CONCAT(YEAR(#serviceDate#), QUARTER(#serviceDate#)) <= CONCAT(YEAR(wcs.`$serviceTypes[]$EndTime`), QUARTER(wcs.`$serviceTypes[]$EndTime`))
                            AND st.serviceType = #serviceTypes[]#
                        )
                        ]]>
	                </isNotEmpty>
                </isEqual>
            </iterate>
            <isNotEmpty prepend="AND" property="serviceType"> st.serviceType = #serviceType# </isNotEmpty>
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="warrantyGradeDate">
            DATE(#warrantyGradeDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="wafServiceDate">
            DATE(#wafServiceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`
        </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="projectId"> wcs.projectId = #projectId# </isNotEmpty>
        <isNotEmpty prepend="AND" property="projectCode"> wcs.projectCode like concat("%", #projectCode#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="projectName"> wcs.projectName like concat("%", #projectName#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="contractNo"> wcs.contractNos like concat("%", #contractNo#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="officeCode"> wcs.officeCode = #officeCode# </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="serviceManager"> sm.memberCode = #serviceManager# </isNotEmpty>
        <isNotEmpty prepend="AND" property="programManager"> (pm.memberCode = #programManager# or pm2.memberCode = #programManager#) </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="userPower" open="(" close=")">
            (sm.memberCode = #userPower# 
                or pm.memberCode = #userPower# 
                or pm2.memberCode = #userPower#
                <isEqual prepend="or" property="checkServicePower" compareValue="true">
                    exists (
                       select 
                            1 
                        from
                            serviceManagerPower_$userPower$ ep 
                        where ep.serviceCode = #userPower#
                            AND ep.programCode = wcs.`createBy`
                    )
                </isEqual>
            )
            <isNotEmpty prepend="OR" property="areaPower"> FIND_IN_SET(wcs.officeCode, #areaPower#)</isNotEmpty>
        </isNotEmpty>
        <isEmpty property="userPower">
            <isNotEmpty prepend="AND" property="areaPower"> FIND_IN_SET(wcs.officeCode, #areaPower#)</isNotEmpty>
        </isEmpty>
        
        <isNotEmpty prepend="AND" property="queryWarrantyStatus"> wcs.warrantyStatus = #queryWarrantyStatus# </isNotEmpty>
        <isNotEmpty prepend="AND" property="queryWarrantyGrade"> wcs.warrantyGrade = #queryWarrantyGrade# </isNotEmpty>
        <isNotEmpty prepend="AND" property="queryWafService"> wcs.wafService = #queryWafService# </isNotEmpty>
        <isNotEmpty prepend="AND" property="hasQuarterDeliveried"> #hasQuarterDeliveried# = IFNULL(sd.hasQuarterDeliveried, FALSE) </isNotEmpty>
        
        <!-- <isEmpty  prepend="AND" property="serviceTypes">
            <isEmpty property="serviceType" >
                <isNotEmpty prepend="AND" property="serviceDate">
                (
                    (DATE(#serviceDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`)
                    or
                    (DATE(#serviceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`)
                </isNotEmpty>
            </isEmpty>
            <isEqual property="serviceType" compareValue="warrantyGrade">
                <isNotEmpty prepend="AND" property="serviceDate">
                    DATE(#serviceDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`
                </isNotEmpty>
            </isEqual>
            <isEqual property="serviceType" compareValue="wafService">
                <isNotEmpty prepend="AND" property="serviceDate">
                    DATE(#serviceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`
                </isNotEmpty>
            </isEqual>
        </isEmpty> -->
    </dynamic>
    <!-- <isNotEmpty property="joinProjectMembers">
        group by wcs.projectType, wcs.projectId, wcs.serviceType
    </isNotEmpty> -->

-- D:/IDEAWorkspace/PMS/PMS-struts/bin/sql-map-subcontract-config.xml:35 | fragment=SubcontractProject_List
id, subcontractName, contractNos, projectIds, type, state, callbackState, facilitatorId, facilitatorName, bankInfo, bankAccount, officeCode, profitDepCode, 
    subcontractNo, isAccrued, isInvoiced, subcontractAmount, reason, remark, effectiveForm, effectiveTo, zrApproveTime, createBy, createTime, 
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-struts/bin/sql-map-subcontract-config.xml:1103 | fragment=SubcontractPayment_Column_List
id, subcontractId, ratio, amount, confirmTime, paymentTime, remark, sseId, createTime, createBy, 
    updateTime, updateBy

-- D:/IDEAWorkspace/PMS/PMS-struts/bin/sql-map-subcontract-config.xml:1419 | fragment=SubcontractPrice_List
id, subcontractName, contractNos, projectIds, type, state, callbackState, facilitatorId, facilitatorName, bankInfo, bankAccount, officeCode, profitDepCode,
    subcontractNo, isAccrued, subcontractAmount, reason, remark, effectiveForm, effectiveTo, createBy, createTime,
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-maintenance-config.xml:1447 | fragment=selectProjectWarrantyState
SELECT 
        ph.`projectId`, ph.`projectCode`, ph.`projectName`, pg.projectType,
        ph.`column001` as officeCode, fd.`departmentName` as officeName, c.name as companyName, c.abbr as companyAbbr,
        ph.column004 AS marketName, ph.column005 AS systemName, 
	    ph.column006 AS expendName, ph.column007 AS industryName,
	    ph.column013 AS finalCustomerName, ph.compId,
        group_concat(pc.contractNo) as contractNos,
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))) warrantyStatus, 
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
            )
        ) AS warrantyStatusName,
        MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
        MAX(wcs.`warrantyGrade`) warrantyGrade, 
        IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
            )
        ) AS warrantyGradeName,
        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`warrantyGradeStartTime`) AND MAX(wcs.`warrantyGradeEndTime`) >= DATE(NOW()), "true", "false") AS warrantyGradeEnable,
        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
        IFNULL(MAX(wcs.`wafService`), 0) wafService,
        IF(MAX(wcs.`wafService`) = 1, "策略调优服务", NULL) AS  wafServiceName,
        MIN(wcs.`wafServiceStartTime`) wafServiceStartTime,
        MAX(wcs.`wafServiceEndTime`) wafServiceEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`wafServiceStartTime`) AND MAX(wcs.`wafServiceEndTime`) >= DATE(NOW()), "true", "false") AS wafServiceEnable,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.itemDesc, "(", DATE(wcs.`wafServiceStartTime`), "~", DATE(wcs.`wafServiceEndTime`), ")") SEPARATOR "\r\n") AS warrantyServiceDesc
    FROM
        `pm_project_header` ph 
        LEFT JOIN `pm_project_group_relationship` pgr 
            on ph.`projectCode` = pgr.`projectCode` 
        LEFT JOIN `pm_project_group` pg
            on pg.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `pm_project_contract` pc 
            on pc.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `view_warranty_contract_state` wcs 
            on wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
        LEFT JOIN fnd_department fd
            on fd.`departmentNum` = ph.`column001`
        LEFT JOIN fnd_company c force key(primary)
            ON c.id = ph.compId
    WHERE ph.`effectiveTo` is null

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-maintenance-config.xml:1735 | fragment=sqlProjectMaintenanceServiceDeliveryWhere
<isNotEmpty property="serviceTypes">
        <!-- LEFT JOIN (
            <iterate property="serviceTypes" conjunction=" UNION ALL ">
                SELECT #serviceTypes[]# as serviceType
            </iterate>
        ) st
        ON 1 = 1 -->
        LEFT JOIN (SELECT 
                <!-- dataTypeCode, --> basicDataId as serviceType, basicDataAttri1 as deliverId 
	        FROM
	            `fnd_basic_data` 
	        WHERE dataTypeCode = 'projectServiceDeliveryType') st
            ON 1 = 1 <!-- st.dataTypeCode = 'projectServiceDeliveryType'  -->
    </isNotEmpty>
    <isNotEmpty property="hasQuarterDeliveried">
        LEFT JOIN (<include refid="sqlProjectServiceDeliver" />) sd
        ON sd.projectId = wcs.projectId AND sd.serviceType = st.serviceType
    </isNotEmpty>
    <isNotEmpty property="joinProjectMembers">
        LEFT JOIN pm_project_member sm 
            ON wcs.`projectId` = sm.`projectId` 
            AND wcs.`projectType` = sm.`projectType` 
            AND sm.`memberRole` = '20' 
            AND sm.`effectiveTo` IS NULL 
            AND sm.`fromFlag` = 1 
        LEFT JOIN pm_project_member pm 
            ON wcs.`projectId` = pm.`projectId` 
            AND wcs.`projectType` = pm.`projectType` 
            AND pm.`memberRole` = '30' 
            AND pm.`effectiveTo` IS NULL 
            AND pm.`fromFlag` = 1 
        LEFT JOIN pm_project_member pm2 
            ON wcs.`projectId` = pm2.`projectId` 
            AND wcs.`projectType` = pm2.`projectType` 
            AND pm2.`memberRole` = '30' 
            AND pm2.`effectiveTo` IS NULL 
            AND pm2.`fromFlag` = 2 
        LEFT JOIN `pm_project_member` s 
            ON s.`projectId` = wcs.`projectId` 
            AND s.`projectType` = wcs.`projectType` 
            AND s.`memberRole` = "10" 
            AND s.`effectiveTo` IS NULL 
    </isNotEmpty>
    <dynamic prepend="where">
        <isNotEmpty prepend="AND" property="serviceTypes">
            <iterate property="serviceTypes" open="(" close=")" conjunction=" or ">
                <isNotEqual property="serviceQuarter" compareValue="true">
	                <isNotEmpty property="serviceDate" >
	                (
	                    #serviceDate# between wcs.`$serviceTypes[]$StartTime` 
	                    and wcs.`$serviceTypes[]$EndTime` 
	                    and st.serviceType = #serviceTypes[]#
	                )
	                </isNotEmpty>
                </isNotEqual>
                <isEqual property="serviceQuarter" compareValue="true">
                    <isNotEmpty property="serviceDate" >
		                <!-- <![CDATA[
		                (
		                    #serviceQuarter# >= CONCAT(YEAR(wcs.`$serviceTypes[]$StartTime`), QUARTER(wcs.`$serviceTypes[]$StartTime`))
		                    AND #serviceQuarter# <= CONCAT(YEAR(wcs.`$serviceTypes[]$EndTime`), QUARTER(wcs.`$serviceTypes[]$EndTime`))
		                    AND st.serviceType = #serviceTypes[]#
		                )
		                ]]> -->
		                <![CDATA[
                        (
                            CONCAT(YEAR(#serviceDate#), QUARTER(#serviceDate#)) >= CONCAT(YEAR(wcs.`$serviceTypes[]$StartTime`), QUARTER(wcs.`$serviceTypes[]$StartTime`))
                            AND CONCAT(YEAR(#serviceDate#), QUARTER(#serviceDate#)) <= CONCAT(YEAR(wcs.`$serviceTypes[]$EndTime`), QUARTER(wcs.`$serviceTypes[]$EndTime`))
                            AND st.serviceType = #serviceTypes[]#
                        )
                        ]]>
	                </isNotEmpty>
                </isEqual>
            </iterate>
            <isNotEmpty prepend="AND" property="serviceType"> st.serviceType = #serviceType# </isNotEmpty>
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="warrantyGradeDate">
            DATE(#warrantyGradeDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`
        </isNotEmpty>
        <isNotEmpty prepend="AND" property="wafServiceDate">
            DATE(#wafServiceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`
        </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="projectId"> wcs.projectId = #projectId# </isNotEmpty>
        <isNotEmpty prepend="AND" property="projectCode"> wcs.projectCode like concat("%", #projectCode#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="projectName"> wcs.projectName like concat("%", #projectName#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="contractNo"> wcs.contractNos like concat("%", #contractNo#, "%") </isNotEmpty>
        <isNotEmpty prepend="AND" property="officeCode"> wcs.officeCode = #officeCode# </isNotEmpty>
        <isNotEmpty prepend="AND" property="compId"> wcs.compId = #compId# </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="serviceManager"> sm.memberCode = #serviceManager# </isNotEmpty>
        <isNotEmpty prepend="AND" property="programManager"> (pm.memberCode = #programManager# or pm2.memberCode = #programManager#) </isNotEmpty>
        
        <isNotEmpty prepend="AND" property="userPower" open="(" close=")">
            (sm.memberCode = #userPower# 
                or pm.memberCode = #userPower# 
                or pm2.memberCode = #userPower#
                <isEqual prepend="or" property="checkServicePower" compareValue="true">
                    exists (
                       select 
                            1 
                        from
                            serviceManagerPower_$userPower$ ep 
                        where ep.serviceCode = #userPower#
                            AND ep.programCode = wcs.`createBy`
                    )
                </isEqual>
            )
            <isNotEmpty prepend="OR" property="areaPower"> FIND_IN_SET(wcs.officeCode, #areaPower#)</isNotEmpty>
        </isNotEmpty>
        <isEmpty property="userPower">
            <isNotEmpty prepend="AND" property="areaPower"> FIND_IN_SET(wcs.officeCode, #areaPower#)</isNotEmpty>
        </isEmpty>
        
        <isNotEmpty prepend="AND" property="queryWarrantyStatus"> wcs.warrantyStatus = #queryWarrantyStatus# </isNotEmpty>
        <isNotEmpty prepend="AND" property="queryWarrantyGrade"> wcs.warrantyGrade = #queryWarrantyGrade# </isNotEmpty>
        <isNotEmpty prepend="AND" property="queryWafService"> wcs.wafService = #queryWafService# </isNotEmpty>
        <isNotEmpty prepend="AND" property="hasQuarterDeliveried"> #hasQuarterDeliveried# = IFNULL(sd.hasQuarterDeliveried, FALSE) </isNotEmpty>
        
        <!-- <isEmpty  prepend="AND" property="serviceTypes">
            <isEmpty property="serviceType" >
                <isNotEmpty prepend="AND" property="serviceDate">
                (
                    (DATE(#serviceDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`)
                    or
                    (DATE(#serviceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`)
                </isNotEmpty>
            </isEmpty>
            <isEqual property="serviceType" compareValue="warrantyGrade">
                <isNotEmpty prepend="AND" property="serviceDate">
                    DATE(#serviceDate#) BETWEEN wcs.`warrantyGradeStartTime` AND wcs.`warrantyGradeEndTime`
                </isNotEmpty>
            </isEqual>
            <isEqual property="serviceType" compareValue="wafService">
                <isNotEmpty prepend="AND" property="serviceDate">
                    DATE(#serviceDate#) BETWEEN wcs.`wafServiceStartTime` AND wcs.`wafServiceEndTime`
                </isNotEmpty>
            </isEqual>
        </isEmpty> -->
    </dynamic>
    <!-- <isNotEmpty property="joinProjectMembers">
        group by wcs.projectType, wcs.projectId, wcs.serviceType
    </isNotEmpty> -->

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:35 | fragment=SubcontractProject_List
id, subcontractName, contractNos, projectIds, type, state, callbackState, facilitatorId, facilitatorName, bankInfo, bankAccount, officeCode, profitDepCode, 
    subcontractNo, isAccrued, isInvoiced, subcontractAmount, reason, remark, effectiveFrom, effectiveTo, zrApproveTime, createBy, createTime, 
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:1103 | fragment=SubcontractPayment_Column_List
id, subcontractId, ratio, amount, confirmTime, paymentTime, remark, sseId, createTime, createBy, 
    updateTime, updateBy

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-subcontract-config.xml:1419 | fragment=SubcontractPrice_List
id, subcontractName, contractNos, projectIds, type, state, callbackState, facilitatorId, facilitatorName, bankInfo, bankAccount, officeCode, profitDepCode,
    subcontractNo, isAccrued, subcontractAmount, reason, remark, effectiveFrom, effectiveTo, createBy, createTime,
    updateBy, updateTime

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:230 | fragment=sql_projectWarrantyCallback_where_selective
<isNotEmpty prepend="AND" property="id"> pwc.`id` = #id# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectId"> pwc.`projectId` = #projectId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="officeCode"> pwc.`officeCode` = #officeCode# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectIds"> pwc.`projectIds` = #projectIds# </isNotEmpty>
	<isNotEmpty prepend="AND" property="serviceImpl"> pwc.`serviceImpl` = #serviceImpl# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectCode"> pwc.`projectCode` like concat("%", #projectCode#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="contractNos"> pwc.`contractNos` like concat("%", #contractNos#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectName"> pwc.`projectName` like concat("%", #projectName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="industryName"> pwc.`industryName` like concat("%", #industryName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="agentChannel"> pwc.`agentChannel` like concat("%", #agentChannel#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="finalCustomerName"> pwc.`finalCustomerName` like concat("%", #finalCustomerName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customer1"> pwc.`customer1` like concat("%", #customer1#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customerContact1"> pwc.`customerContact1` like concat("%", #customerContact1#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customer2"> pwc.`customer2` like concat("%", #customer2#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="customerContact2"> pwc.`customerContact2` like concat("%", #customerContact2#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyStartTime"> pwc.`warrantyStartTime` = #warrantyStartTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyEndTime"> pwc.`warrantyEndTime` = #warrantyEndTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="renewalIntention"> pwc.`renewalIntention` = #renewalIntention# </isNotEmpty>
	<isNotEmpty prepend="AND" property="callbackTime"> pwc.`callbackTime` = #callbackTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="nextCallbackTime"> pwc.`nextCallbackTime` = #nextCallbackTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="taskId"> pwc.`taskId` = #taskId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="quesnaireId"> pwc.`quesnaireId` = #quesnaireId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="quesnaireVersion"> pwc.`quesnaireVersion` = #quesnaireVersion# </isNotEmpty>
	<isNotEmpty prepend="AND" property="quesnaireState"> pwc.`quesnaireState` = #quesnaireState# </isNotEmpty>
	<isNotEmpty prepend="AND" property="isDelete"> pwc.`isDelete` = #isDelete# </isNotEmpty>
	<isNotEmpty prepend="AND" property="remark"> pwc.`remark` = #remark# </isNotEmpty>
	<isNotEmpty prepend="AND" property="compId"> pwc.`compId` = #compId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="createBy"> pwc.`createBy` = #createBy# </isNotEmpty>
	<isNotEmpty prepend="AND" property="createTime"> pwc.`createTime` = #createTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="updateBy"> pwc.`updateBy` = #updateBy# </isNotEmpty>
	<isNotEmpty prepend="AND" property="updateTime"> pwc.`updateTime` = #updateTime# </isNotEmpty>
	<isNotEmpty prepend="AND" property="customInfo"> pwc.`customInfo` = #customInfo# </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyEndTimeStart"> pwc.warrantyEndTime >= #warrantyEndTimeStart# </isNotEmpty>
   	<isNotEmpty prepend="AND" property="warrantyEndTimeEnd"> #warrantyEndTimeEnd# >= pwc.warrantyEndTime </isNotEmpty>
   	<isNotEmpty prepend="AND" property="processStartTime"> pwc.callbackTime >= #processStartTime# </isNotEmpty>
   	<isNotEmpty prepend="AND" property="processEndTime"> #processEndTime# >= pwc.callbackTime </isNotEmpty>
   	<isNotEmpty prepend="AND" property="callbackTimeStart"> pwc.callbackTime >= #callbackTimeStart# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="callbackTimeEnd"> #callbackTimeEnd# >= pwc.callbackTime </isNotEmpty>
 	<isNotEmpty prepend="AND" property="nextCallbackTimeStart"> pwc.nextCallbackTime >= #nextCallbackTimeStart# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="nextCallbackTimeEnd"> #nextCallbackTimeEnd# >= pwc.nextCallbackTime </isNotEmpty>
 	<isNotEmpty prepend="AND" property="customerSearch">
 		(
			pwc.customer1 like concat("%", #customerSearch#, "%") 
			or pwc.customer2 like concat("%", #customerSearch#, "%") 
			or pwc.customerContact1 like concat("%", #customerSearch#, "%") 
			or pwc.customerContact2 like concat("%", #customerSearch#, "%")
		)
 	</isNotEmpty>
 	<isNotEmpty prepend="AND" property="customerContactSearch">
		(
			pwc.customerContact1 like concat("%", #customerContactSearch#, "%") 
			or pwc.customerContact2 like concat("%", #customerContactSearch#, "%") 
		)
	</isNotEmpty>

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:286 | fragment=sql_projectWarrantyCallback_model_where_selective
<isNotEmpty property="model">
		<isNotEmpty prepend="AND" property="model.id"> pwc.`id` = #model.id# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectId"> pwc.`projectId` = #model.projectId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.officeCode"> pwc.`officeCode` = #model.officeCode# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectIds"> pwc.`projectIds` = #model.projectIds# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.serviceImpl"> pwc.`serviceImpl` = #model.serviceImpl# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectCode"> pwc.`projectCode` like concat("%", #model.projectCode#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.contractNos"> pwc.`contractNos` like concat("%", #model.contractNos#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.projectName"> pwc.`projectName` like concat("%", #model.projectName#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.industryName"> pwc.`industryName` like concat("%", #model.industryName#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.agentChannel"> pwc.`agentChannel` like concat("%", #model.agentChannel#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.finalCustomerName"> pwc.`finalCustomerName` like concat("%", #model.finalCustomerName#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customer1"> pwc.`customer1` like concat("%", #model.customer1#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customerContact1"> pwc.`customerContact1` like concat("%", #model.customerContact1#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customer2"> pwc.`customer2` like concat("%", #model.customer2#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customerContact2"> pwc.`customerContact2` like concat("%", #model.customerContact2#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.warrantyStartTime"> pwc.`warrantyStartTime` = #model.warrantyStartTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.warrantyEndTime"> pwc.`warrantyEndTime` = #model.warrantyEndTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.renewalIntention"> pwc.`renewalIntention` = #model.renewalIntention# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.callbackTime"> pwc.`callbackTime` = #model.callbackTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.nextCallbackTime"> pwc.`nextCallbackTime` = #model.nextCallbackTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.taskId"> pwc.`taskId` = #model.taskId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.quesnaireId"> pwc.`quesnaireId` = #model.quesnaireId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.quesnaireVersion"> pwc.`quesnaireVersion` = #model.quesnaireVersion# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.quesnaireState"> pwc.`quesnaireState` = #model.quesnaireState# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.isDelete"> pwc.`isDelete` = #model.isDelete# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.remark"> pwc.`remark` = #model.remark# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.compId"> pwc.`compId` = #model.compId# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.createBy"> pwc.`createBy` = #model.createBy# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.createTime"> pwc.`createTime` = #model.createTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.updateBy"> pwc.`updateBy` = #model.updateBy# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.updateTime"> pwc.`updateTime` = #model.updateTime# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.customInfo"> pwc.`customInfo` = #model.customInfo# </isNotEmpty>
		<isNotEmpty prepend="AND" property="model.warrantyEndTimeStart"> pwc.warrantyEndTime >= #model.warrantyEndTimeStart# </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.warrantyEndTimeEnd"> #model.warrantyEndTimeEnd# >= pwc.warrantyEndTime </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.processStartTime"> pwc.callbackTime >= #model.processStartTime# </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.processEndTime"> #model.processEndTime# >= pwc.callbackTime </isNotEmpty>
   		<isNotEmpty prepend="AND" property="model.callbackTimeStart"> pwc.callbackTime >= #model.callbackTimeStart# </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.callbackTimeEnd"> #model.callbackTimeEnd# >= pwc.callbackTime </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.nextCallbackTimeStart"> pwc.nextCallbackTime >= #model.nextCallbackTimeStart# </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.nextCallbackTimeEnd"> #model.nextCallbackTimeEnd# >= pwc.nextCallbackTime </isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.customerSearch">
	 		(
				pwc.customer1 like concat("%", #model.customerSearch#, "%") 
				or pwc.customer2 like concat("%", #model.customerSearch#, "%") 
				or pwc.customerContact1 like concat("%", #model.customerSearch#, "%") 
				or pwc.customerContact2 like concat("%", #model.customerSearch#, "%") 
			)
 		</isNotEmpty>
 		<isNotEmpty prepend="AND" property="model.customerContactSearch">
	 		(
				pwc.customerContact1 like concat("%", #model.customerContactSearch#, "%") 
				or pwc.customerContact2 like concat("%", #model.customerContactSearch#, "%") 
			)
 		</isNotEmpty>
	</isNotEmpty>

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:354 | fragment=sql_projectWarrantyCallback_pageable_limit
<isNotNull property="displayParam">
	    <isNotEmpty property="displayParam.sort">
	        order by $displayParam.sort$
	    </isNotEmpty>
	    <isGreaterThan property="displayParam.pagesize" compareValue="-1">
	        limit #displayParam.offset#, #displayParam.pagesize# 
	    </isGreaterThan>
    </isNotNull>

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:392 | fragment=sql_select_projectWarrantyCallbackMapList
select pwc.*, d.`departmentName` AS officeName, c.name AS companyName, c.abbr as companyAbbr,
    	IFNULL(ssfs1.basicDataName, '未指定') AS serviceName
    	<isNotEqual property="hideQuesnaire" compareValue="true" prepend=",">
	        qrt.* 
	    </isNotEqual>
	    <isNotEqual property="hideWarranty" compareValue="true" prepend=",">
	        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))) warrantyStatus, 
	        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
	            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
	                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
	            )
	        ) AS warrantyStatusName,
<!-- 	    MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
	        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime, -->
	        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
	        MAX(wcs.`warrantyGrade`) warrantyGrade, 
	        IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
	            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
	                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
	            )
	        ) AS warrantyGradeName,
	        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
	        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
	        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
	        IFNULL(MAX(wcs.hasRenewal), 0) hasRenewal,
	        GROUP_CONCAT(DISTINCT wcs.renewalDesc SEPARATOR "\r\n") AS renewalDesc
	    </isNotEqual>
    from pm_project_warranty_callback AS `pwc`
    <isNotEqual property="hideQuesnaire" compareValue="true">
	    LEFT JOIN projectWarrantyCallback_tempQuesnaireResultTable qrt 
	        ON qrt.quesnaireResultHeaderId = pwc.`quesnaireId` 
    </isNotEqual>
    <isNotEqual property="hideWarranty" compareValue="true">
        LEFT JOIN pm_project_header ph
	        ON ph.projectId = pwc.projectId
	    LEFT JOIN `pm_project_group_relationship` pgr 
	        ON ph.`projectCode` = pgr.`projectCode` 
	    LEFT JOIN `pm_project_contract` pc 
	        ON pc.`projectGroupCode` = pgr.`projectGroupCode` 
	    LEFT JOIN `view_warranty_contract_state` wcs 
	        ON wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
    </isNotEqual>
    LEFT JOIN fnd_basic_data ssfs1 
            ON ssfs1.dataTypeCode = '15'
                AND ssfs1.basicDataId = pwc.serviceImpl
                AND ssfs1.effectiveTo IS NULL 
    LEFT JOIN fnd_department d 
	        ON d.`departmentNum` = pwc.`officeCode`
	LEFT JOIN fnd_company c 
            ON c.id = pwc.compId
	<dynamic prepend="where">
		<include refid="sql_projectWarrantyCallback_model_where_selective" />
	</dynamic>
	<isNotEqual property="hideWarranty" compareValue="true">
		GROUP BY pwc.id
	</isNotEqual>
	<dynamic prepend="having">
		<isNotEqual property="hideWarranty" compareValue="true">
			<isNotEmpty property="model">
				<isNotEmpty prepend="AND" property="model.hasRenewal"> hasRenewal = #model.hasRenewal# </isNotEmpty>
			</isNotEmpty>
		</isNotEqual>
	</dynamic>

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:469 | fragment=sql_select_projectWarranty
SELECT 
        ph.`projectId`, ph.`projectCode`, ph.`projectName`, ph.projectType,
        ph.`column001` as officeCode, fd.`departmentName` as officeName, c.name as companyName, c.abbr as companyAbbr,
        <!-- ph.column004 AS marketName, ph.column005 AS systemName, 
	    ph.column006 AS expendName, ph.column007 AS industryName, -->
	    ph.column005 AS industryName,
	    ph.column013 AS finalCustomerName, ph.compId,
        GROUP_CONCAT(DISTINCT pm.`memberName` order by pm.phoneNum) as customer1, GROUP_CONCAT(DISTINCT pm.`phoneNum` order by pm.phoneNum) as customerContact1,
		GROUP_CONCAT(DISTINCT ppfs.linkmanName order by ppfs.linkmanTel) as customer2, GROUP_CONCAT(DISTINCT ppfs.`linkmanTel` order by ppfs.linkmanTel) as customerContact2,
		prp.`partyName` as agentChannel,
		IFNULL(ph.column012, ph.columno12_readonly) AS serviceImpl, 
		IFNULL(IFNULL(ssfs1.basicDataName, ssfs2.basicDataName), '未指定') AS serviceName,
        GROUP_CONCAT(DISTINCT pc.contractNo) as contractNos,
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, 0, SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`))) warrantyStatus, 
        IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = -1, '维保内',
            IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 0 OR SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) > 1, '部分保外',
                IF(SUM(DISTINCT IF(wcs.`diff` = 0, 99, wcs.`diff`)) = 1, '维保外', NULL)
            )
        ) AS warrantyStatusName,
        MIN(wcs.`warrantyStartTime`) AS warrantyStartTime,
        MAX(wcs.`warrantyEndTime`) AS warrantyEndTime,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.contractNo, ":", wcs.warrantyStatusName, "(", DATE(wcs.`warrantyStartTime`), "~", DATE(wcs.`warrantyEndTime`), ")") SEPARATOR "\r\n") AS warrantyStatusDesc,
        MAX(wcs.`warrantyGrade`) warrantyGrade, 
        IF(MAX(wcs.`warrantyGrade`) = 1, '基本维保',
            IF(MAX(wcs.`warrantyGrade`) = 2, '中级维保',
                IF(MAX(wcs.`warrantyGrade`) = 3, '高级维保', NULL)
            )
        ) AS warrantyGradeName,
        MIN(wcs.`warrantyGradeStartTime`) warrantyGradeStartTime,
        MAX(wcs.`warrantyGradeEndTime`) warrantyGradeEndTime,
        GROUP_CONCAT(DISTINCT wcs.gradedesc SEPARATOR "\r\n") AS warrantyGradeDesc,
        <!--
        IF(DATE(NOW()) >= MIN(wcs.`warrantyGradeStartTime`) AND MAX(wcs.`warrantyGradeEndTime`) >= DATE(NOW()), "true", "false") AS warrantyGradeEnable,
        IFNULL(MAX(wcs.`wafService`), 0) wafService,
        IF(MAX(wcs.`wafService`) = 1, "策略调优服务", NULL) AS  wafServiceName,
        MIN(wcs.`wafServiceStartTime`) wafServiceStartTime,
        MAX(wcs.`wafServiceEndTime`) wafServiceEndTime,
        IF(DATE(NOW()) >= MIN(wcs.`wafServiceStartTime`) AND MAX(wcs.`wafServiceEndTime`) >= DATE(NOW()), "true", "false") AS wafServiceEnable,
        GROUP_CONCAT(DISTINCT CONCAT(wcs.itemDesc, "(", DATE(wcs.`wafServiceStartTime`), "~", DATE(wcs.`wafServiceEndTime`), ")") SEPARATOR "\r\n") AS warrantyServiceDesc, -->
    	pwc.`renewalIntention` as latestRenewalIntention,
    	pwc.`callbackTime` as latestCallbackTime,
    	pwc.`nextCallbackTime` as latestNextCallbackTime,
    	pwc.`remark` as latestRemark,
    	IFNULL(mpwc.callbackCount, 0) callbackCount,
    	IFNULL(mpwc.renewalIntentionCount, 0) renewalIntentionCount,
    	IFNULL(MAX(wcs.hasRenewal), 0) hasRenewal,
		GROUP_CONCAT(DISTINCT renewalDesc SEPARATOR "\r\n") AS renewalDesc
    FROM
        `pm_project_header` ph 
        LEFT JOIN `pm_project_group_relationship` pgr 
            ON ph.`projectCode` = pgr.`projectCode` 
        <!-- LEFT JOIN `pm_project_group` pg
            ON pg.`projectGroupCode` = pgr.`projectGroupCode`  -->
        LEFT JOIN `pm_project_contract` pc 
            ON pc.`projectGroupCode` = pgr.`projectGroupCode` 
        LEFT JOIN `view_warranty_contract_state` wcs 
            ON wcs.`contractNo` = REPLACE(REPLACE(pc.`contractNo`, "-C", ""), "-L", "")
        LEFT JOIN 
	        (SELECT 
	            projectId, max(id) as id, count(id) as callbackCount, count(IF(renewalIntention = 1, 1, null)) as renewalIntentionCount 
	        FROM
	            pm_project_warranty_callback 
	        GROUP BY projectId) mpwc 
	        ON mpwc.projectId = ph.projectId 
	    LEFT JOIN `pm_project_warranty_callback` pwc 
	        ON pwc.`projectId` = ph.`projectId` 
	        AND mpwc.id = pwc.id 
        LEFT JOIN pm_project_member pm
		    ON pm.`projectId` = ph.`projectId`
				AND pm.`memberRole` = "60"
				AND pm.`effectiveTo` is null
		LEFT JOIN `pm_project_related_party` prp
		    ON prp.`projectId` = ph.`projectId`
				AND prp.`partyRole` = "30"
				AND prp.`effectiveTo` is null
		LEFT JOIN `pm_project_property_from_sms` ppfs
	     	ON ppfs.`projectCode` = pgr.`smsProjectCode`
		LEFT JOIN fnd_basic_data ssfs1 
            ON ssfs1.dataTypeCode = '15'
                AND ssfs1.basicDataId = ph.column012 
                AND ssfs1.effectiveTo IS NULL 
        LEFT JOIN fnd_basic_data ssfs2 
            ON ssfs2.dataTypeCode = '15' 
                AND ssfs2.basicDataId = ph.columno12_readonly 
                AND ssfs2.effectiveTo IS NULL 
        LEFT JOIN fnd_department fd
            ON fd.`departmentNum` = ph.`column001`
        LEFT JOIN fnd_company c force key(primary)
            ON c.id = ph.compId
    WHERE ph.`effectiveTo` is null

-- D:/IDEAWorkspace/PMS/PMS-struts/config-ibaits/sql-map-warrantyCallback-config.xml:578 | fragment=sql_projectWarranty_where
<isNotEmpty prepend="AND" property="projectId"> ph.projectId = #projectId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectCode"> ph.projectCode like concat("%", #projectCode#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="projectName"> ph.projectName like concat("%", #projectName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="officeCode"> ph.column001 = #officeCode# </isNotEmpty>
	<isNotEmpty prepend="AND" property="industryName"> ph.column005 like concat("%", #industryName#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="agentChannel"> prp.partyName like concat("%", #agentChannel#, "%") </isNotEmpty>
	<isNotEmpty prepend="AND" property="finalCustomerName"> ph.column013 like concat("%", #finalCustomerName#, "%") </isNotEmpty>
	<isNotNull prepend="AND" property="customerNameNotFuzzy"> #customerNameNotFuzzy# = TRIM(IFNULL(ph.column013, "")) </isNotNull>
	<isNotEmpty prepend="AND" property="serviceImpl"> #serviceImpl# = IFNULL(ph.column012, ph.columno12_readonly) </isNotEmpty>
	<isNotEmpty prepend="AND" property="renewalIntentionInt"> 
		<isEqual property="renewalIntentionInt" compareValue="-1"> pwc.renewalIntention is null </isEqual>
		<isNotEqual property="renewalIntentionInt" compareValue="-1"> pwc.renewalIntention = #renewalIntentionInt# </isNotEqual>
	</isNotEmpty>
	<isNotEmpty prepend="AND" property="compId"> ph.compId = #compId# </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyEndTimeStart"> wcs.warrantyEndTime >= #warrantyEndTimeStart# </isNotEmpty>
	<isNotEmpty prepend="AND" property="warrantyEndTimeEnd"> #warrantyEndTimeEnd# >= wcs.warrantyEndTime </isNotEmpty>
	<isNotEmpty prepend="AND" property="processStartTime"> pwc.callbackTime >= #processStartTime# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="processEndTime"> #processEndTime# >= pwc.callbackTime </isNotEmpty>
 	<isNotEmpty prepend="AND" property="callbackTimeStart"> pwc.callbackTime >= #callbackTimeStart# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="callbackTimeEnd"> #callbackTimeEnd# >= pwc.callbackTime </isNotEmpty>
 	<isNotEmpty prepend="AND" property="nextCallbackTimeStart"> pwc.nextCallbackTime >= #nextCallbackTimeStart# </isNotEmpty>
 	<isNotEmpty prepend="AND" property="nextCallbackTimeEnd"> #nextCallbackTimeEnd# >= pwc.nextCallbackTime </isNotEmpty>
	GROUP BY ph.projectCode
	<dynamic prepend="having">
		<isNotEmpty prepend="AND" property="contractNos"> contractNos like concat("%", #contractNos#, "%") </isNotEmpty>
		<isNotEmpty prepend="AND" property="customerSearch">
		(
			customer1 like concat("%", #customerSearch#, "%") 
			or customer2 like concat("%", #customerSearch#, "%") 
			or customerContact1 like concat("%", #customerSearch#, "%") 
			or customerContact2 like concat("%", #customerSearch#, "%")
		)
		</isNotEmpty>
		<isNotEmpty prepend="AND" property="customerContactSearch">
		(
			customerContact1 like concat("%", #customerContactSearch#, "%") 
			or customerContact2 like concat("%", #customerContactSearch#, "%") 
		)
		</isNotEmpty>
		<isNotEmpty prepend="AND" property="hasCallback"> callbackCount > 0 </isNotEmpty>
		<isNotEmpty prepend="AND" property="hasRenewal"> hasRenewal = #hasRenewal# </isNotEmpty>
	</dynamic>

-- D:/IDEAWorkspace/PMS/core/src/main/java/com/dp/plat/core/mapping/RoleMapper.xml:16 | fragment=Base_Column_List
role_id, role_name, role_name_zn, home_page, priority, status, create_by, create_time, update_by, 
    update_time, remark

-- D:/IDEAWorkspace/PMS/core/src/main/java/com/dp/plat/core/mapping/UserInfoMapper.xml:32 | fragment=Base_Column_List
id, workNo, realName, eName, compID, depID, jobID, reportTo, wfreportTo, empStatus, 
    jobStatus, empType, sex, birthday, email, mobile, telphone, avatar, remark, state, 
    user_id, custom1, custom2, custom3, custom4, custom5

-- D:/IDEAWorkspace/PMS/core/src/main/java/com/dp/plat/core/mapping/UserMapper.xml:22 | fragment=Base_Column_List
user_id, user_name, password, create_by, create_time, update_by, update_time, status, 
    needChangePwd, loginErrorCount, isSysUser, userCustom1, userCustom2, userCustom3, userCustom4, 
    userCustom5

-- D:/IDEAWorkspace/PMS/core/src/main/java/com/dp/plat/core/mapping/UserRoleMapper.xml:10 | fragment=Base_Column_List
id, user_id, role_id, comp_id
