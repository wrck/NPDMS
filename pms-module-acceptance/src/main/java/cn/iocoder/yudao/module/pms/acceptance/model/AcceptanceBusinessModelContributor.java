package cn.iocoder.yudao.module.pms.acceptance.model;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancescope.AcceptanceScopeBindingDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.NormalClosureApplicationDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancescope.AcceptanceScopeBindingMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * B3 批次统一目录声明（P12）：
 * 验收域在用业务实体进入统一目录，由统一基类、默认读取与目录页面承载；
 * 验收写路径（提交、审核、归档、收集执行）保留在本域控制器与服务中，
 * 因此声明不开放通用 create/save 操作，也不引入与本域重复的写入入口。
 */
@Component
public class AcceptanceBusinessModelContributor implements BusinessModelContributor {

    private final AcceptanceMapper acceptanceMapper;
    private final AcceptanceActivityMapper acceptanceActivityMapper;
    private final AcceptanceScopeBindingMapper acceptanceScopeBindingMapper;
    private final ArchiveDocumentMapper archiveDocumentMapper;
    private final CompletionCertificateMapper completionCertificateMapper;
    private final DeliverableChecklistMapper deliverableChecklistMapper;
    private final NormalClosureMapper normalClosureMapper;
    private final SatisfactionCollectionTaskMapper satisfactionCollectionTaskMapper;
    private final SatisfactionQuestionnaireMapper satisfactionQuestionnaireMapper;
    private final SatisfactionQuestionnaireTemplateMapper satisfactionQuestionnaireTemplateMapper;

    public AcceptanceBusinessModelContributor(AcceptanceMapper acceptanceMapper,
                                              AcceptanceActivityMapper acceptanceActivityMapper,
                                              AcceptanceScopeBindingMapper acceptanceScopeBindingMapper,
                                              ArchiveDocumentMapper archiveDocumentMapper,
                                              CompletionCertificateMapper completionCertificateMapper,
                                              DeliverableChecklistMapper deliverableChecklistMapper,
                                              NormalClosureMapper normalClosureMapper,
                                              SatisfactionCollectionTaskMapper satisfactionCollectionTaskMapper,
                                              SatisfactionQuestionnaireMapper satisfactionQuestionnaireMapper,
                                              SatisfactionQuestionnaireTemplateMapper satisfactionQuestionnaireTemplateMapper) {
        this.acceptanceMapper = acceptanceMapper;
        this.acceptanceActivityMapper = acceptanceActivityMapper;
        this.acceptanceScopeBindingMapper = acceptanceScopeBindingMapper;
        this.archiveDocumentMapper = archiveDocumentMapper;
        this.completionCertificateMapper = completionCertificateMapper;
        this.deliverableChecklistMapper = deliverableChecklistMapper;
        this.normalClosureMapper = normalClosureMapper;
        this.satisfactionCollectionTaskMapper = satisfactionCollectionTaskMapper;
        this.satisfactionQuestionnaireMapper = satisfactionQuestionnaireMapper;
        this.satisfactionQuestionnaireTemplateMapper = satisfactionQuestionnaireTemplateMapper;
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
        BusinessModelDescriptor acceptance = new BusinessModelDescriptor("ACC", "acceptance",
                "ACC_ACCEPTANCE", 1, BusinessModelKind.AGGREGATE_ROOT, "验收记录",
                "pms:acc-acceptance:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        required("code", "验收编码", EntityField.Type.TEXT),
                        required("name", "验收名称", EntityField.Type.TEXT),
                        field("acceptanceType", "验收类型", EntityField.Type.TEXT),
                        field("acceptanceDate", "验收日期", EntityField.Type.DATE),
                        field("applicantUserId", "申请人", EntityField.Type.NUMBER),
                        field("applyTime", "申请时间", EntityField.Type.DATETIME),
                        field("approverUserId", "审批人", EntityField.Type.NUMBER),
                        field("approveTime", "审批时间", EntityField.Type.DATETIME),
                        field("approveOpinion", "审批意见", EntityField.Type.TEXT),
                        field("archiveTime", "归档时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "acc_acceptance_record");
        declarations.add(new BusinessModelDeclaration(acceptance, AcceptanceDO.class, acceptanceMapper, null));
        BusinessModelDescriptor acceptanceActivity = new BusinessModelDescriptor("ACC", "acceptanceActivity",
                "ACC_ACCEPTANCE_ACTIVITY", 1, BusinessModelKind.AGGREGATE_ROOT, "验收活动",
                "pms:acceptance:report:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("projectTaskId", "项目任务", EntityField.Type.NUMBER),
                        field("executionContractId", "实施合同", EntityField.Type.NUMBER),
                        field("deliverableId", "交付件", EntityField.Type.NUMBER),
                        field("acceptanceType", "验收类型", EntityField.Type.TEXT),
                        field("activityStatus", "活动状态", EntityField.Type.TEXT),
                        field("currentReportVersionId", "现行报告版本", EntityField.Type.NUMBER),
                        field("originKind", "来源类型", EntityField.Type.TEXT),
                        field("originKey", "来源键", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY,null,true)), "acc_acceptance");
        declarations.add(new BusinessModelDeclaration(acceptanceActivity, AcceptanceActivityDO.class,
                acceptanceActivityMapper, null));
        BusinessModelDescriptor scopeBinding = new BusinessModelDescriptor("ACC", "acceptanceScopeBinding",
                "ACC_ACCEPTANCE_SCOPE_BINDING", 1, BusinessModelKind.AGGREGATE_ROOT, "验收范围绑定",
                "pms:acc-acceptance:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("projectStageSnapshotId", "阶段快照", EntityField.Type.NUMBER),
                        field("deliveryScopeId", "交付范围", EntityField.Type.NUMBER),
                        field("scopeAllocationVersion", "范围分配版本", EntityField.Type.NUMBER),
                        field("bindingTrigger", "绑定触发方式", EntityField.Type.TEXT),
                        field("bindingStatus", "绑定状态", EntityField.Type.TEXT),
                        field("effectiveFrom", "生效时间", EntityField.Type.DATETIME),
                        field("effectiveTo", "失效时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "acc_acceptance_scope_binding");
        declarations.add(new BusinessModelDeclaration(scopeBinding, AcceptanceScopeBindingDO.class,
                acceptanceScopeBindingMapper, null));
        declarations.add(cn.iocoder.yudao.module.pms.platform.support.model.DefaultBusinessModels.project(
                "ACC", "archiveDocument", "ACC_ARCHIVE_DOCUMENT", "归档文档",
                "pms:acc-archive-document", ArchiveDocumentDO.class, archiveDocumentMapper,
                List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("submit", 1, "提交",
                                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "pms:acc-archive-document:submit"),
                        new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("archive", 1, "归档",
                                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "pms:acc-archive-document:audit"))));
        BusinessModelDescriptor completionCertificate = new BusinessModelDescriptor("ACC", "completionCertificate",
                "ACC_COMPLETION_CERTIFICATE", 1, BusinessModelKind.AGGREGATE_ROOT, "竣工证书",
                "pms:acc-completion-certificate:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        required("code", "证书编码", EntityField.Type.TEXT),
                        required("name", "证书名称", EntityField.Type.TEXT),
                        field("certificateNo", "证书编号", EntityField.Type.TEXT),
                        field("customerId", "客户", EntityField.Type.NUMBER),
                        field("completionDate", "竣工日期", EntityField.Type.DATE),
                        field("customerConfirmTime", "客户确认时间", EntityField.Type.DATETIME),
                        field("archiveTime", "归档时间", EntityField.Type.DATETIME),
                        field("rejectReason", "驳回原因", EntityField.Type.TEXT),
                        field("content", "证书内容", EntityField.Type.TEXT),
                        field("attachmentUrl", "附件地址", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY,null,true)), "acc_completion_certificate");
        declarations.add(new BusinessModelDeclaration(completionCertificate, CompletionCertificateDO.class,
                completionCertificateMapper, null));
        declarations.add(cn.iocoder.yudao.module.pms.platform.support.model.DefaultBusinessModels.project(
                "ACC", "deliverableChecklist", "ACC_DELIVERABLE_CHECKLIST", "交付件核对清单",
                "pms:acc-deliverable-checklist", DeliverableChecklistDO.class, deliverableChecklistMapper,
                List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("submit", 1, "提交",
                                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "pms:acc-deliverable-checklist:submit"),
                        new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("pass", 1, "通过",
                                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "pms:acc-deliverable-checklist:audit"),
                        new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("reject", 1, "驳回",
                                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "pms:acc-deliverable-checklist:audit"))));
        BusinessModelDescriptor normalClosure = new BusinessModelDescriptor("ACC", "normalClosureApplication",
                "ACC_NORMAL_CLOSURE_APPLICATION", 1, BusinessModelKind.AGGREGATE_ROOT, "项目正常关闭申请",
                "pms:acc-project-closure:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("closureType", "关闭类型", EntityField.Type.TEXT),
                        field("ruleRevision", "规则版本", EntityField.Type.NUMBER),
                        field("fromStage", "来源阶段", EntityField.Type.TEXT),
                        field("projectVersion", "项目版本", EntityField.Type.NUMBER),
                        field("treeVersion", "任务树版本", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("applicantUserId", "申请人", EntityField.Type.NUMBER),
                        field("serviceManagerUserId", "服务经理", EntityField.Type.NUMBER),
                        field("reviewerUserId", "评审人", EntityField.Type.NUMBER),
                        field("processDefinitionKey", "流程定义", EntityField.Type.TEXT),
                        field("processInstanceId", "流程实例", EntityField.Type.TEXT),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME),
                        field("decidedAt", "决定时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "acc_project_closure");
        declarations.add(new BusinessModelDeclaration(normalClosure, NormalClosureApplicationDO.class,
                normalClosureMapper, null));
        BusinessModelDescriptor collectionTask = new BusinessModelDescriptor("ACC", "satisfactionCollectionTask",
                "ACC_SATISFACTION_COLLECTION_TASK", 1, BusinessModelKind.AGGREGATE_ROOT, "满意度收集任务",
                "pms:acceptance:satisfaction:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("projectTaskId", "项目任务", EntityField.Type.NUMBER),
                        field("deliverableId", "交付件", EntityField.Type.NUMBER),
                        field("originKind", "来源类型", EntityField.Type.TEXT),
                        field("originKey", "来源键", EntityField.Type.TEXT),
                        field("collectionKey", "收集键", EntityField.Type.TEXT),
                        field("taskRevisionNo", "任务修订号", EntityField.Type.NUMBER),
                        field("assignedToUserId", "指派给", EntityField.Type.NUMBER),
                        field("taskStatus", "任务状态", EntityField.Type.TEXT),
                        field("questionnaireId", "问卷", EntityField.Type.NUMBER),
                        field("resultId", "结果", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY,null,true)), "acc_satisfaction_collection_task");
        declarations.add(new BusinessModelDeclaration(collectionTask, SatisfactionCollectionTaskDO.class,
                satisfactionCollectionTaskMapper, null));
        BusinessModelDescriptor questionnaire = new BusinessModelDescriptor("ACC", "satisfactionQuestionnaire",
                "ACC_SATISFACTION_QUESTIONNAIRE", 1, BusinessModelKind.AGGREGATE_ROOT, "满意度问卷",
                "pms:acceptance:satisfaction:query",
                List.of(required("collectionTaskId", "收集任务", EntityField.Type.NUMBER),
                        field("templateId", "模板", EntityField.Type.NUMBER),
                        field("templateRevisionId", "模板修订", EntityField.Type.NUMBER),
                        field("templateVersion", "模板版本", EntityField.Type.NUMBER),
                        field("frozenQuestionJson", "冻结题目", EntityField.Type.TEXT),
                        field("frozenThreshold", "冻结阈值", EntityField.Type.NUMBER),
                        field("ruleVersion", "规则版本", EntityField.Type.TEXT),
                        field("questionnaireStatus", "问卷状态", EntityField.Type.TEXT),
                        field("accessScopeVersion", "访问范围版本", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "acc_satisfaction_questionnaire");
        declarations.add(new BusinessModelDeclaration(questionnaire, SatisfactionQuestionnaireDO.class,
                satisfactionQuestionnaireMapper, null));
        BusinessModelDescriptor questionnaireTemplate = new BusinessModelDescriptor("ACC",
                "satisfactionQuestionnaireTemplate", "ACC_SATISFACTION_QUESTIONNAIRE_TEMPLATE", 1,
                BusinessModelKind.AGGREGATE_ROOT, "满意度问卷模板", "pms:acceptance:satisfaction:query",
                List.of(required("templateCode", "模板编码", EntityField.Type.TEXT),
                        required("name", "模板名称", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("currentRevisionId", "现行修订", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "acc_satisfaction_questionnaire_template");
        declarations.add(new BusinessModelDeclaration(questionnaireTemplate, SatisfactionQuestionnaireTemplateDO.class,
                satisfactionQuestionnaireTemplateMapper, null));
        return declarations;
    }
}
