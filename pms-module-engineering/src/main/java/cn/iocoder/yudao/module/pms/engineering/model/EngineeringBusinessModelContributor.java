package cn.iocoder.yudao.module.pms.engineering.model;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement.AnnouncementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.authorization.AuthorizationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate.DocTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.ExternalProcurementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.forminstance.FormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate.FormTemplateDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.issue.IssueDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.MaterialRequisitionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.resource.ResourceReadyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.risk.RiskDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.schedulebackward.ScheduleBackwardDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.announcement.AnnouncementMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.authorization.AuthorizationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.doctemplate.DocTemplateMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.externalprocurement.ExternalProcurementMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.forminstance.FormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.formtemplate.FormTemplateMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.issue.IssueMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition.MaterialRequisitionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.resource.ResourceReadyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.risk.RiskMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.schedulebackward.ScheduleBackwardMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanBatchMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * B1/B2 批次统一目录声明（P12）：
 * 设计与实施在用业务实体进入统一目录，由统一基类、默认读取与目录页面承载；
 * 专业写路径（状态机、审批、外发、归档）保留在本域控制器与服务中，
 * 因此声明不开放通用 create/save 操作，也不引入与本域重复的写入入口。
 */
@Component
public class EngineeringBusinessModelContributor implements BusinessModelContributor {

    private final RequirementMapper requirementMapper;
    private final RequirementAnalysisMapper requirementAnalysisMapper;
    private final SiteSurveyMapper siteSurveyMapper;
    private final SolutionMapper solutionMapper;
    private final ConstructionPlanMapper constructionPlanMapper;
    private final StagePlanBatchMapper stagePlanBatchMapper;
    private final TrainingMapper trainingMapper;
    private final ArrivalMapper arrivalMapper;
    private final ArrivalAcceptanceMapper arrivalAcceptanceMapper;
    private final DeliveryEvidenceMapper deliveryEvidenceMapper;
    private final InstallationMapper installationMapper;
    private final JointTestMapper jointTestMapper;
    private final DeliverableMapper deliverableMapper;
    private final MaterialExchangeMapper materialExchangeMapper;
    private final MaterialRequisitionMapper materialRequisitionMapper;
    private final ExternalProcurementMapper externalProcurementMapper;
    private final OutsourceRequestMapper outsourceRequestMapper;
    private final AuthorizationMapper authorizationMapper;
    private final BriefingMapper briefingMapper;
    private final ConfigurationMapper configurationMapper;
    private final IssueMapper issueMapper;
    private final RiskMapper riskMapper;
    private final ResourceReadyMapper resourceReadyMapper;
    private final ScheduleBackwardMapper scheduleBackwardMapper;
    private final DocTemplateMapper docTemplateMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final AnnouncementMapper announcementMapper;

    public EngineeringBusinessModelContributor(RequirementMapper requirementMapper,
                                               RequirementAnalysisMapper requirementAnalysisMapper,
                                               SiteSurveyMapper siteSurveyMapper,
                                               SolutionMapper solutionMapper,
                                               ConstructionPlanMapper constructionPlanMapper,
                                               StagePlanBatchMapper stagePlanBatchMapper,
                                               TrainingMapper trainingMapper,
                                               ArrivalMapper arrivalMapper,
                                               ArrivalAcceptanceMapper arrivalAcceptanceMapper,
                                               DeliveryEvidenceMapper deliveryEvidenceMapper,
                                               InstallationMapper installationMapper,
                                               JointTestMapper jointTestMapper,
                                               DeliverableMapper deliverableMapper,
                                               MaterialExchangeMapper materialExchangeMapper,
                                               MaterialRequisitionMapper materialRequisitionMapper,
                                               ExternalProcurementMapper externalProcurementMapper,
                                               OutsourceRequestMapper outsourceRequestMapper,
                                               AuthorizationMapper authorizationMapper,
                                               BriefingMapper briefingMapper,
                                               ConfigurationMapper configurationMapper,
                                               IssueMapper issueMapper,
                                               RiskMapper riskMapper,
                                               ResourceReadyMapper resourceReadyMapper,
                                               ScheduleBackwardMapper scheduleBackwardMapper,
                                               DocTemplateMapper docTemplateMapper,
                                               FormTemplateMapper formTemplateMapper,
                                               FormInstanceMapper formInstanceMapper,
                                               AnnouncementMapper announcementMapper) {
        this.requirementMapper = requirementMapper;
        this.requirementAnalysisMapper = requirementAnalysisMapper;
        this.siteSurveyMapper = siteSurveyMapper;
        this.solutionMapper = solutionMapper;
        this.constructionPlanMapper = constructionPlanMapper;
        this.stagePlanBatchMapper = stagePlanBatchMapper;
        this.trainingMapper = trainingMapper;
        this.arrivalMapper = arrivalMapper;
        this.arrivalAcceptanceMapper = arrivalAcceptanceMapper;
        this.deliveryEvidenceMapper = deliveryEvidenceMapper;
        this.installationMapper = installationMapper;
        this.jointTestMapper = jointTestMapper;
        this.deliverableMapper = deliverableMapper;
        this.materialExchangeMapper = materialExchangeMapper;
        this.materialRequisitionMapper = materialRequisitionMapper;
        this.externalProcurementMapper = externalProcurementMapper;
        this.outsourceRequestMapper = outsourceRequestMapper;
        this.authorizationMapper = authorizationMapper;
        this.briefingMapper = briefingMapper;
        this.configurationMapper = configurationMapper;
        this.issueMapper = issueMapper;
        this.riskMapper = riskMapper;
        this.resourceReadyMapper = resourceReadyMapper;
        this.scheduleBackwardMapper = scheduleBackwardMapper;
        this.docTemplateMapper = docTemplateMapper;
        this.formTemplateMapper = formTemplateMapper;
        this.formInstanceMapper = formInstanceMapper;
        this.announcementMapper = announcementMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        List<BusinessModelDeclaration> declarations = new ArrayList<>();
        BusinessModelDescriptor requirement = new BusinessModelDescriptor("SOL", "requirement",
                "SOL_REQUIREMENT", 1, BusinessModelKind.AGGREGATE_ROOT, "设计需求记录",
                "pms:sol-requirement:query",
                List.of(required("code", "需求编码", EntityField.Type.TEXT),
                        required("name", "需求名称", EntityField.Type.TEXT),
                        field("requirementType", "需求类型", EntityField.Type.TEXT),
                        field("background", "项目背景", EntityField.Type.TEXT),
                        field("topology", "网络拓扑", EntityField.Type.TEXT),
                        field("transmission", "传输需求", EntityField.Type.TEXT),
                        field("traffic", "流量需求", EntityField.Type.TEXT),
                        field("business", "业务需求", EntityField.Type.TEXT),
                        field("ipPlan", "IP 规划", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "sol_eng_requirement");
        declarations.add(new BusinessModelDeclaration(requirement, RequirementDO.class, requirementMapper, null));
        BusinessModelDescriptor requirementAnalysis = new BusinessModelDescriptor("SOL", "requirementAnalysis",
                "SOL_REQUIREMENT_ANALYSIS", 1, BusinessModelKind.AGGREGATE_ROOT, "需求分析",
                "pms:requirement-analysis:query",
                List.of(required("projectBackground", "项目背景", EntityField.Type.TEXT),
                        required("projectObjective", "项目目标", EntityField.Type.TEXT),
                        required("networkTopology", "组网拓扑", EntityField.Type.TEXT),
                        field("transmissionRequirement", "传输需求", EntityField.Type.TEXT),
                        field("trafficRequirement", "流量需求", EntityField.Type.TEXT),
                        field("businessRequirement", "业务需求", EntityField.Type.TEXT),
                        field("ipPlanning", "IP 规划", EntityField.Type.TEXT),
                        field("redundancyRequirement", "冗余需求", EntityField.Type.TEXT),
                        field("securityProtection", "安全防护", EntityField.Type.TEXT),
                        field("operationsRequirement", "运维需求", EntityField.Type.TEXT),
                        field("loggingRequirement", "日志需求", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "sol_requirement_analysis");
        declarations.add(new BusinessModelDeclaration(requirementAnalysis, RequirementAnalysisDO.class,
                requirementAnalysisMapper, null));
        BusinessModelDescriptor siteSurvey = new BusinessModelDescriptor("SOL", "siteSurvey",
                "SOL_SITE_SURVEY", 1, BusinessModelKind.AGGREGATE_ROOT, "现场工勘",
                "pms:sol-site-survey:query",
                List.of(required("code", "工勘编码", EntityField.Type.TEXT),
                        required("name", "工勘名称", EntityField.Type.TEXT),
                        field("surveyDate", "勘察日期", EntityField.Type.DATE),
                        field("location", "位置", EntityField.Type.TEXT),
                        field("powerSupply", "供电", EntityField.Type.TEXT),
                        field("cabinet", "机柜", EntityField.Type.TEXT),
                        field("networkPort", "网络端口", EntityField.Type.TEXT),
                        field("fiber", "光纤", EntityField.Type.TEXT),
                        field("conclusion", "工勘结论", EntityField.Type.TEXT),
                        field("outsourceRequired", "是否委外", EntityField.Type.BOOLEAN),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "sol_eng_site_survey");
        declarations.add(new BusinessModelDeclaration(siteSurvey, SiteSurveyDO.class, siteSurveyMapper, null));
        BusinessModelDescriptor solution = new BusinessModelDescriptor("SOL", "solution",
                "SOL_SOLUTION", 1, BusinessModelKind.AGGREGATE_ROOT, "实施方案",
                "pms:sol-solution:query",
                List.of(required("code", "方案编码", EntityField.Type.TEXT),
                        required("name", "方案名称", EntityField.Type.TEXT),
                        field("solutionType", "方案类型", EntityField.Type.TEXT),
                        field("background", "项目背景", EntityField.Type.TEXT),
                        field("target", "建设目标", EntityField.Type.TEXT),
                        field("plan", "实施方案", EntityField.Type.TEXT),
                        field("topology", "网络拓扑", EntityField.Type.TEXT),
                        field("reviewLevel", "评审层级", EntityField.Type.NUMBER),
                        field("baselineVersion", "基线版本", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "sol_eng_solution");
        declarations.add(new BusinessModelDeclaration(solution, SolutionDO.class, solutionMapper, null));
        BusinessModelDescriptor constructionPlan = new BusinessModelDescriptor("SOL", "constructionPlan",
                "SOL_CONSTRUCTION_PLAN", 1, BusinessModelKind.AGGREGATE_ROOT, "施工计划",
                "pms:construction-plan:query",
                List.of(field("currentDurationRevisionId", "现行工期版本", EntityField.Type.NUMBER),
                        field("pendingChangeId", "在途变更", EntityField.Type.NUMBER),
                        field("planRecalculationStatusCode", "重算状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "sol_construction_plan");
        declarations.add(new BusinessModelDeclaration(constructionPlan, ConstructionPlanDO.class, constructionPlanMapper, null));
        BusinessModelDescriptor stagePlanBatch = new BusinessModelDescriptor("SOL", "stagePlanBatch",
                "SOL_STAGE_PLAN_BATCH", 1, BusinessModelKind.AGGREGATE_ROOT, "阶段计划批次",
                "pms:imp-stage-plan:query",
                List.of(field("status", "状态", EntityField.Type.NUMBER),
                        field("durationRevisionId", "工期版本", EntityField.Type.NUMBER),
                        field("taskPlansJson", "任务计划", EntityField.Type.OBJECT_LIST),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME),
                        field("effectiveAt", "生效时间", EntityField.Type.DATETIME),
                        field("rejectReason", "驳回原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "sol_stage_plan_batch");
        declarations.add(new BusinessModelDeclaration(stagePlanBatch, StagePlanBatchDO.class, stagePlanBatchMapper, null));
        BusinessModelDescriptor training = new BusinessModelDescriptor("IMP", "training",
                "IMP_TRAINING", 1, BusinessModelKind.AGGREGATE_ROOT, "现场培训",
                "pms:imp-training:query",
                List.of(required("code", "培训编码", EntityField.Type.TEXT),
                        required("name", "培训名称", EntityField.Type.TEXT),
                        field("trainingTypes", "培训类型", EntityField.Type.TEXT),
                        field("trainingTime", "培训时间", EntityField.Type.DATE),
                        field("trainerName", "培训工程师", EntityField.Type.TEXT),
                        field("traineeCount", "参训人数", EntityField.Type.NUMBER),
                        field("contactName", "客户联系人", EntityField.Type.TEXT),
                        field("satisfactionRating", "满意度评价", EntityField.Type.TEXT),
                        field("signConfirmerName", "客户签字人", EntityField.Type.TEXT),
                        field("signTime", "确认时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "imp_eng_training");
        declarations.add(new BusinessModelDeclaration(training, TrainingDO.class, trainingMapper, null));

        // ===== B2（工程实施其余实体）=====
        BusinessModelDescriptor arrival = new BusinessModelDescriptor("IMP", "arrival",
                "IMP_ARRIVAL", 1, BusinessModelKind.AGGREGATE_ROOT, "设备到货签收",
                "pms:imp-arrival:query",
                List.of(required("code", "签收编码", EntityField.Type.TEXT),
                        field("arrivalTime", "到货时间", EntityField.Type.DATETIME),
                        field("receiverUserId", "签收人", EntityField.Type.NUMBER),
                        field("equipmentId", "设备编号", EntityField.Type.NUMBER),
                        field("quantity", "到货数量", EntityField.Type.NUMBER),
                        field("inspectionResult", "检查结果", EntityField.Type.TEXT),
                        field("exceptionRecord", "异常记录", EntityField.Type.TEXT),
                        field("attachmentUrl", "签收单附件", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_eng_arrival");
        declarations.add(new BusinessModelDeclaration(arrival, ArrivalDO.class, arrivalMapper, null));
        BusinessModelDescriptor arrivalAcceptance = new BusinessModelDescriptor("IMP", "arrivalAcceptance",
                "IMP_ARRIVAL_ACCEPTANCE", 1, BusinessModelKind.AGGREGATE_ROOT, "到货验收批次",
                "pms:arrival-acceptance:query",
                List.of(required("batchCode", "批次编号", EntityField.Type.TEXT),
                        field("logisticsNo", "物流单号", EntityField.Type.TEXT),
                        field("arrivedAt", "到货时间", EntityField.Type.DATETIME),
                        field("signerSnapshot", "签收人快照", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("scopeWatermark", "范围水印", EntityField.Type.TEXT),
                        field("migrationResolutionStatus", "迁移处置状态", EntityField.Type.TEXT),
                        field("evidenceId", "证据编号", EntityField.Type.NUMBER),
                        field("predecessorAcceptanceId", "前序批次", EntityField.Type.NUMBER),
                        field("successorReason", "后继原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_arrival_acceptance");
        declarations.add(new BusinessModelDeclaration(arrivalAcceptance, ArrivalAcceptanceDO.class,
                arrivalAcceptanceMapper, null));
        BusinessModelDescriptor deliveryEvidence = new BusinessModelDescriptor("IMP", "deliveryEvidence",
                "IMP_DELIVERY_EVIDENCE", 1, BusinessModelKind.AGGREGATE_ROOT, "交付证据",
                "pms:arrival-acceptance:query",
                List.of(field("sourceRequirement", "来源要求", EntityField.Type.TEXT),
                        field("sourceObjectType", "来源对象类型", EntityField.Type.TEXT),
                        field("sourceObjectId", "来源对象编号", EntityField.Type.NUMBER),
                        field("currentRevisionNo", "当前修订号", EntityField.Type.NUMBER),
                        field("accSyncStatus", "ACC 同步状态", EntityField.Type.TEXT),
                        field("accLastPublishedAt", "最近发布时间", EntityField.Type.DATETIME),
                        field("accNextRetryAt", "下次重试时间", EntityField.Type.DATETIME),
                        field("accRetryCount", "重试次数", EntityField.Type.NUMBER),
                        field("accAcceptedRecordId", "ACC 受理记录", EntityField.Type.TEXT),
                        field("accArchivedRecordId", "ACC 归档记录", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_delivery_evidence");
        declarations.add(new BusinessModelDeclaration(deliveryEvidence, DeliveryEvidenceDO.class,
                deliveryEvidenceMapper, null));
        BusinessModelDescriptor installation = new BusinessModelDescriptor("IMP", "installation",
                "IMP_INSTALLATION", 1, BusinessModelKind.AGGREGATE_ROOT, "硬件安装",
                "pms:imp-installation:query",
                List.of(required("code", "安装编码", EntityField.Type.TEXT),
                        field("equipmentId", "设备编号", EntityField.Type.NUMBER),
                        field("installLocation", "安装位置", EntityField.Type.TEXT),
                        field("locationResolutionStatus", "位置解析状态", EntityField.Type.TEXT),
                        field("installTime", "安装时间", EntityField.Type.DATETIME),
                        field("installerUserId", "安装人", EntityField.Type.NUMBER),
                        field("environmentCheck", "环境检查", EntityField.Type.TEXT),
                        field("specCheck", "安装规范检查", EntityField.Type.TEXT),
                        field("photoUrl", "安装照片", EntityField.Type.TEXT),
                        field("result", "安装结果", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "imp_eng_installation");
        declarations.add(new BusinessModelDeclaration(installation, InstallationDO.class, installationMapper, null));
        BusinessModelDescriptor jointTest = new BusinessModelDescriptor("IMP", "jointTest",
                "IMP_JOINT_TEST", 1, BusinessModelKind.AGGREGATE_ROOT, "配置调试联调",
                "pms:imp-joint-test:query",
                List.of(required("code", "联调编码", EntityField.Type.TEXT),
                        field("testCase", "联调用例", EntityField.Type.TEXT),
                        field("equipmentId", "设备编号", EntityField.Type.NUMBER),
                        field("participants", "参与方", EntityField.Type.TEXT),
                        field("testTime", "联调时间", EntityField.Type.DATETIME),
                        field("testerUserId", "联调人", EntityField.Type.NUMBER),
                        field("result", "联调结果", EntityField.Type.TEXT),
                        field("exceptionRecord", "异常记录", EntityField.Type.TEXT),
                        field("evidenceUrl", "证据附件", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_eng_joint_test");
        declarations.add(new BusinessModelDeclaration(jointTest, JointTestDO.class, jointTestMapper, null));
        BusinessModelDescriptor deliverable = new BusinessModelDescriptor("IMP", "deliverable",
                "IMP_DELIVERABLE", 1, BusinessModelKind.AGGREGATE_ROOT, "工程交付件",
                "pms:imp-deliverable:query",
                List.of(required("code", "交付件编码", EntityField.Type.TEXT),
                        required("name", "交付件名称", EntityField.Type.TEXT),
                        field("phaseId", "阶段编号", EntityField.Type.NUMBER),
                        field("deliverableType", "交付件类型", EntityField.Type.TEXT),
                        field("sourceType", "来源业务类型", EntityField.Type.TEXT),
                        field("sourceId", "来源业务编号", EntityField.Type.NUMBER),
                        field("fileUrl", "文件地址", EntityField.Type.TEXT),
                        field("fileSize", "文件大小", EntityField.Type.NUMBER),
                        field("fileChecksum", "文件校验值", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("archivedTime", "归集时间", EntityField.Type.DATETIME),
                        field("archivedBy", "归集人", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "imp_eng_deliverable");
        declarations.add(new BusinessModelDeclaration(deliverable, DeliverableDO.class, deliverableMapper, null));
        BusinessModelDescriptor materialExchange = new BusinessModelDescriptor("IMP", "materialExchange",
                "IMP_MATERIAL_EXCHANGE", 1, BusinessModelKind.AGGREGATE_ROOT, "设备换货",
                "pms:imp-material-exch:query",
                List.of(required("code", "换货单号", EntityField.Type.TEXT),
                        required("name", "换货名称", EntityField.Type.TEXT),
                        field("exchangeType", "换货类型", EntityField.Type.TEXT),
                        field("deviceId", "设备编号", EntityField.Type.NUMBER),
                        field("productName", "产品名称", EntityField.Type.TEXT),
                        field("productModel", "产品型号", EntityField.Type.TEXT),
                        field("quantity", "数量", EntityField.Type.NUMBER),
                        field("unit", "单位", EntityField.Type.TEXT),
                        field("reason", "换货原因", EntityField.Type.TEXT),
                        field("crmPushStatus", "CRM 推送状态", EntityField.Type.TEXT),
                        field("crmOrderNo", "CRM 工单号", EntityField.Type.TEXT),
                        field("newDeviceId", "新设备编号", EntityField.Type.NUMBER),
                        field("exchangeProgress", "换货进度", EntityField.Type.TEXT),
                        field("applyTime", "申请时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "imp_eng_material_exchange");
        declarations.add(new BusinessModelDeclaration(materialExchange, MaterialExchangeDO.class,
                materialExchangeMapper, null));
        BusinessModelDescriptor materialRequisition = new BusinessModelDescriptor("IMP", "materialRequisition",
                "IMP_MATERIAL_REQUISITION", 1, BusinessModelKind.AGGREGATE_ROOT, "材料领用",
                "pms:imp-material-req:query",
                List.of(required("code", "领料单号", EntityField.Type.TEXT),
                        required("name", "领料名称", EntityField.Type.TEXT),
                        field("requisitionType", "领用类型", EntityField.Type.TEXT),
                        field("equipmentId", "设备编号", EntityField.Type.NUMBER),
                        field("productName", "产品名称/物料名称", EntityField.Type.TEXT),
                        field("productCode", "产品编码/物料编码", EntityField.Type.TEXT),
                        field("specification", "规格型号", EntityField.Type.TEXT),
                        field("quantity", "数量", EntityField.Type.NUMBER),
                        field("unit", "单位", EntityField.Type.TEXT),
                        field("neededDate", "需求日期", EntityField.Type.DATE),
                        field("warehouseName", "仓库名称", EntityField.Type.TEXT),
                        field("triggerSource", "触发来源", EntityField.Type.TEXT),
                        field("applyTime", "申请时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "imp_eng_material_requisition");
        declarations.add(new BusinessModelDeclaration(materialRequisition, MaterialRequisitionDO.class,
                materialRequisitionMapper, null));
        BusinessModelDescriptor externalProcurement = new BusinessModelDescriptor("IMP", "externalProcurement",
                "IMP_EXTERNAL_PROCUREMENT", 1, BusinessModelKind.AGGREGATE_ROOT, "外部采购",
                "pms:imp-ext-proc:query",
                List.of(required("code", "外采单号", EntityField.Type.TEXT),
                        required("name", "外采名称", EntityField.Type.TEXT),
                        field("procurementType", "外采类型", EntityField.Type.TEXT),
                        field("productName", "产品名称/物料名称", EntityField.Type.TEXT),
                        field("productCode", "产品编码/物料编码", EntityField.Type.TEXT),
                        field("brand", "品牌", EntityField.Type.TEXT),
                        field("productModel", "产品型号/物料型号", EntityField.Type.TEXT),
                        field("quantity", "数量", EntityField.Type.NUMBER),
                        field("unitPrice", "单价", EntityField.Type.NUMBER),
                        field("totalPrice", "总价", EntityField.Type.NUMBER),
                        field("currency", "币种", EntityField.Type.TEXT),
                        field("supplierName", "供应商名称", EntityField.Type.TEXT),
                        field("neededDate", "需求日期", EntityField.Type.DATE),
                        field("triggerSource", "触发来源", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_eng_external_procurement");
        declarations.add(new BusinessModelDeclaration(externalProcurement, ExternalProcurementDO.class,
                externalProcurementMapper, null));
        BusinessModelDescriptor outsourceRequest = new BusinessModelDescriptor("RES", "outsourceRequest",
                "RES_OUTSOURCE_REQUEST", 1, BusinessModelKind.AGGREGATE_ROOT, "外包申请",
                "pms:res-outsource:query",
                List.of(required("code", "外包单号", EntityField.Type.TEXT),
                        required("name", "外包名称", EntityField.Type.TEXT),
                        field("outsourceType", "外包类型", EntityField.Type.TEXT),
                        field("workContent", "工作内容", EntityField.Type.TEXT),
                        field("workQuantity", "工作量", EntityField.Type.NUMBER),
                        field("workUnit", "工作量单位", EntityField.Type.TEXT),
                        field("plannedStartDate", "计划开始日期", EntityField.Type.DATE),
                        field("plannedEndDate", "计划结束日期", EntityField.Type.DATE),
                        field("estimatedCost", "预估成本", EntityField.Type.NUMBER),
                        field("actualCost", "实际成本", EntityField.Type.NUMBER),
                        field("vendorName", "供应商名称", EntityField.Type.TEXT),
                        field("triggerSource", "触发来源", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "res_outsource_request");
        declarations.add(new BusinessModelDeclaration(outsourceRequest, OutsourceRequestDO.class,
                outsourceRequestMapper, null));
        BusinessModelDescriptor authorization = new BusinessModelDescriptor("PLT", "authorization",
                "PLT_AUTHORIZATION", 1, BusinessModelKind.AGGREGATE_ROOT, "设备授权",
                "pms:plt-authorization:query",
                List.of(required("code", "授权编号", EntityField.Type.TEXT),
                        required("name", "授权名称", EntityField.Type.TEXT),
                        field("authorizationType", "授权类型", EntityField.Type.TEXT),
                        field("deviceId", "设备编号", EntityField.Type.NUMBER),
                        field("deviceSerial", "设备序列号", EntityField.Type.TEXT),
                        field("deviceModel", "设备型号", EntityField.Type.TEXT),
                        field("licenseType", "授权类型描述", EntityField.Type.TEXT),
                        field("applyStartDate", "申请开始日期", EntityField.Type.DATE),
                        field("applyEndDate", "申请结束日期", EntityField.Type.DATE),
                        field("actualEndDate", "实际结束日期", EntityField.Type.DATE),
                        field("usageLimit", "使用次数限制", EntityField.Type.NUMBER),
                        field("usedCount", "已使用次数", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "plt_authorization");
        declarations.add(new BusinessModelDeclaration(authorization, AuthorizationDO.class, authorizationMapper, null));
        BusinessModelDescriptor briefing = new BusinessModelDescriptor("SOL", "briefing",
                "SOL_BRIEFING", 1, BusinessModelKind.AGGREGATE_ROOT, "工程交底书",
                "pms:sol-briefing:query",
                List.of(required("code", "交底书编号", EntityField.Type.TEXT),
                        required("name", "交底书名称", EntityField.Type.TEXT),
                        field("briefingType", "交底类型", EntityField.Type.TEXT),
                        field("templateId", "模板编号", EntityField.Type.NUMBER),
                        field("content", "交底内容", EntityField.Type.TEXT),
                        field("fileUrl", "文件地址", EntityField.Type.TEXT),
                        field("fileName", "文件名", EntityField.Type.TEXT),
                        field("fileSize", "文件大小", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("generateTime", "生成时间", EntityField.Type.DATETIME),
                        field("publishTime", "发布时间", EntityField.Type.DATETIME),
                        field("approverUserId", "审核人", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "sol_eng_briefing");
        declarations.add(new BusinessModelDeclaration(briefing, BriefingDO.class, briefingMapper, null));
        BusinessModelDescriptor configuration = new BusinessModelDescriptor("IMP", "configuration",
                "IMP_CONFIGURATION", 1, BusinessModelKind.AGGREGATE_ROOT, "配置调试",
                "pms:imp-configuration:query",
                List.of(required("code", "配置编码", EntityField.Type.TEXT),
                        field("equipmentId", "设备编号", EntityField.Type.NUMBER),
                        field("configLogUrl", "配置 Log 文件", EntityField.Type.TEXT),
                        field("debugResult", "调试结果", EntityField.Type.TEXT),
                        field("debuggerUserId", "调试人", EntityField.Type.NUMBER),
                        field("debugTime", "调试时间", EntityField.Type.DATETIME),
                        field("configSnapshot", "配置档案快照", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_eng_configuration");
        declarations.add(new BusinessModelDeclaration(configuration, ConfigurationDO.class, configurationMapper, null));
        BusinessModelDescriptor issue = new BusinessModelDescriptor("IMP", "issue",
                "IMP_ISSUE", 1, BusinessModelKind.AGGREGATE_ROOT, "实施问题",
                "pms:imp-issue:query",
                List.of(required("code", "问题编码", EntityField.Type.TEXT),
                        required("name", "问题名称", EntityField.Type.TEXT),
                        field("description", "问题描述", EntityField.Type.TEXT),
                        field("source", "问题来源", EntityField.Type.TEXT),
                        field("severity", "严重等级", EntityField.Type.NUMBER),
                        field("ownerUserId", "责任人", EntityField.Type.NUMBER),
                        field("deadline", "整改时限", EntityField.Type.DATETIME),
                        field("solution", "整改方案", EntityField.Type.TEXT),
                        field("verificationStandard", "验证标准", EntityField.Type.TEXT),
                        field("verifyResult", "复测结果", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "imp_eng_issue");
        declarations.add(new BusinessModelDeclaration(issue, IssueDO.class, issueMapper, null));
        BusinessModelDescriptor risk = new BusinessModelDescriptor("IMP", "risk",
                "IMP_RISK", 1, BusinessModelKind.AGGREGATE_ROOT, "实施风险",
                "pms:imp-risk:query",
                List.of(required("code", "风险编号", EntityField.Type.TEXT),
                        required("name", "风险名称", EntityField.Type.TEXT),
                        field("riskType", "风险类型", EntityField.Type.TEXT),
                        field("deviceId", "设备编号", EntityField.Type.NUMBER),
                        field("deviceSerial", "设备序列号", EntityField.Type.TEXT),
                        field("deviceModel", "设备型号", EntityField.Type.TEXT),
                        field("scenario", "风险场景描述", EntityField.Type.TEXT),
                        field("riskLevel", "风险等级", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("crmSynced", "是否已同步CRM", EntityField.Type.BOOLEAN),
                        field("handlerUserId", "处理人", EntityField.Type.NUMBER),
                        field("handleOpinion", "处理意见", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "imp_eng_risk");
        declarations.add(new BusinessModelDeclaration(risk, RiskDO.class, riskMapper, null));
        BusinessModelDescriptor resourceReady = new BusinessModelDescriptor("SOL", "resourceReady",
                "SOL_RESOURCE_READY", 1, BusinessModelKind.AGGREGATE_ROOT, "资源就绪",
                "pms:sol-resource:query",
                List.of(required("code", "就绪编码", EntityField.Type.TEXT),
                        required("name", "资源名称", EntityField.Type.TEXT),
                        field("resourceType", "资源类型", EntityField.Type.TEXT),
                        field("equipmentId", "设备编号", EntityField.Type.NUMBER),
                        field("quantity", "数量", EntityField.Type.NUMBER),
                        field("readyStatus", "就绪状态", EntityField.Type.NUMBER),
                        field("readyTime", "就绪时间", EntityField.Type.DATETIME),
                        field("readyUserId", "就绪确认人", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "sol_eng_resource_ready");
        declarations.add(new BusinessModelDeclaration(resourceReady, ResourceReadyDO.class, resourceReadyMapper, null));
        BusinessModelDescriptor scheduleBackward = new BusinessModelDescriptor("SOL", "scheduleBackward",
                "SOL_SCHEDULE_BACKWARD", 1, BusinessModelKind.AGGREGATE_ROOT, "工期倒排",
                "pms:sol-schedule-backward:query",
                List.of(field("targetDate", "目标完工日期", EntityField.Type.DATE),
                        field("projectType", "项目类型", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("conflictSummary", "冲突汇总", EntityField.Type.TEXT),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "sol_schedule_backward");
        declarations.add(new BusinessModelDeclaration(scheduleBackward, ScheduleBackwardDO.class,
                scheduleBackwardMapper, null));
        BusinessModelDescriptor docTemplate = new BusinessModelDescriptor("IMP", "docTemplate",
                "IMP_DOC_TEMPLATE", 1, BusinessModelKind.AGGREGATE_ROOT, "文档模板",
                "pms:imp-doc-template:query",
                List.of(required("code", "模板编号", EntityField.Type.TEXT),
                        required("name", "模板名称", EntityField.Type.TEXT),
                        field("docCategory", "文档类别", EntityField.Type.TEXT),
                        field("parentTemplateId", "父模板编号", EntityField.Type.NUMBER),
                        field("applicability", "适用范围", EntityField.Type.TEXT),
                        field("description", "模板说明", EntityField.Type.TEXT),
                        field("currentVersionId", "当前版本", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "imp_eng_doc_template");
        declarations.add(new BusinessModelDeclaration(docTemplate, DocTemplateDO.class, docTemplateMapper, null));
        BusinessModelDescriptor formTemplate = new BusinessModelDescriptor("PLT", "formTemplate",
                "PLT_FORM_TEMPLATE", 1, BusinessModelKind.AGGREGATE_ROOT, "动态表单模板",
                "pms:plt-form-template:query",
                List.of(required("code", "模板编号", EntityField.Type.TEXT),
                        required("name", "模板名称", EntityField.Type.TEXT),
                        field("productType", "产品类型", EntityField.Type.TEXT),
                        field("description", "模板说明", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "plt_form_template");
        declarations.add(new BusinessModelDeclaration(formTemplate, FormTemplateDO.class, formTemplateMapper, null));
        BusinessModelDescriptor formInstance = new BusinessModelDescriptor("PLT", "formInstance",
                "PLT_FORM_INSTANCE", 1, BusinessModelKind.AGGREGATE_ROOT, "动态表单实例",
                "pms:plt-form-instance:query",
                List.of(required("code", "实例编号", EntityField.Type.TEXT),
                        field("projectId", "项目编号", EntityField.Type.NUMBER),
                        field("templateId", "模板编号", EntityField.Type.NUMBER),
                        field("name", "实例名称", EntityField.Type.TEXT),
                        field("formData", "填报数据", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("submitTime", "提交时间", EntityField.Type.DATETIME),
                        field("approverUserId", "审核人", EntityField.Type.NUMBER),
                        field("approveOpinion", "审核意见", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "plt_form_instance");
        declarations.add(new BusinessModelDeclaration(formInstance, FormInstanceDO.class, formInstanceMapper, null));
        BusinessModelDescriptor announcement = new BusinessModelDescriptor("KNO", "announcement",
                "KNO_ANNOUNCEMENT", 1, BusinessModelKind.AGGREGATE_ROOT, "技术公告",
                "pms:kno-announcement:query",
                List.of(required("code", "公告编号", EntityField.Type.TEXT),
                        required("title", "公告标题", EntityField.Type.TEXT),
                        field("announcementType", "公告类型", EntityField.Type.TEXT),
                        field("productModel", "适用设备型号", EntityField.Type.TEXT),
                        field("affectedVersions", "影响版本范围", EntityField.Type.TEXT),
                        field("publishDate", "发布日期", EntityField.Type.DATE),
                        field("effectiveDate", "生效日期", EntityField.Type.DATE),
                        field("expireDate", "失效日期", EntityField.Type.DATE),
                        field("severity", "严重等级", EntityField.Type.TEXT),
                        field("content", "公告内容", EntityField.Type.TEXT),
                        field("handlingSuggestion", "处置建议", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "kno_announcement");
        declarations.add(new BusinessModelDeclaration(announcement, AnnouncementDO.class, announcementMapper, null));
        return List.copyOf(declarations);
    }
}
