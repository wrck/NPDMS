package cn.iocoder.yudao.module.pms.cutover.model;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare.CutoverSpareApplicationReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.CutoverSpareApplicationReferenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
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
 * B3 批次统一目录声明（P12）：
 * 割接域在用业务实体进入统一目录，由统一基类、默认读取与目录页面承载；
 * 割接写路径（任务推进、评估、计划、审批、核对、收尾、配置发布、备件外发）
 * 保留在本域控制器与服务中，因此声明不开放通用 create/save 操作，
 * 也不引入与本域重复的写入入口。
 */
@Component
public class CutoverBusinessModelContributor implements BusinessModelContributor {

    private final CutoverTaskMapper cutoverTaskMapper;
    private final CutoverAssessmentMapper cutoverAssessmentMapper;
    private final CutoverPlanRevisionMapper cutoverPlanRevisionMapper;
    private final CutoverApprovalInstanceMapper cutoverApprovalInstanceMapper;
    private final CutoverChecklistMapper cutoverChecklistMapper;
    private final CutoverClosureMapper cutoverClosureMapper;
    private final CutoverConfigurationRevisionMapper cutoverConfigurationRevisionMapper;
    private final CutoverSpareApplicationReferenceMapper cutoverSpareApplicationReferenceMapper;

    public CutoverBusinessModelContributor(CutoverTaskMapper cutoverTaskMapper,
                                           CutoverAssessmentMapper cutoverAssessmentMapper,
                                           CutoverPlanRevisionMapper cutoverPlanRevisionMapper,
                                           CutoverApprovalInstanceMapper cutoverApprovalInstanceMapper,
                                           CutoverChecklistMapper cutoverChecklistMapper,
                                           CutoverClosureMapper cutoverClosureMapper,
                                           CutoverConfigurationRevisionMapper cutoverConfigurationRevisionMapper,
                                           CutoverSpareApplicationReferenceMapper cutoverSpareApplicationReferenceMapper) {
        this.cutoverTaskMapper = cutoverTaskMapper;
        this.cutoverAssessmentMapper = cutoverAssessmentMapper;
        this.cutoverPlanRevisionMapper = cutoverPlanRevisionMapper;
        this.cutoverApprovalInstanceMapper = cutoverApprovalInstanceMapper;
        this.cutoverChecklistMapper = cutoverChecklistMapper;
        this.cutoverClosureMapper = cutoverClosureMapper;
        this.cutoverConfigurationRevisionMapper = cutoverConfigurationRevisionMapper;
        this.cutoverSpareApplicationReferenceMapper = cutoverSpareApplicationReferenceMapper;
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
        BusinessModelDescriptor cutoverTask = new BusinessModelDescriptor("CUT", "cutoverTask",
                "CUT_TASK", 1, BusinessModelKind.AGGREGATE_ROOT, "割接任务",
                "pms:cutover-task:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        required("taskNo", "任务编号", EntityField.Type.TEXT),
                        required("taskName", "任务名称", EntityField.Type.TEXT),
                        field("background", "背景", EntityField.Type.TEXT),
                        field("cutoverType", "割接类型", EntityField.Type.TEXT),
                        field("networkMode", "组网模式", EntityField.Type.TEXT),
                        field("scheduledTime", "计划时间", EntityField.Type.DATETIME),
                        field("taskOrigin", "任务来源", EntityField.Type.TEXT),
                        field("intakeSourceType", "接入来源类型", EntityField.Type.TEXT),
                        field("sourceSystem", "来源系统", EntityField.Type.TEXT),
                        field("sourceBusinessNo", "来源业务编号", EntityField.Type.TEXT),
                        field("businessEventId", "业务事件", EntityField.Type.TEXT),
                        field("currentStage", "当前阶段", EntityField.Type.TEXT),
                        field("taskStatus", "任务状态", EntityField.Type.TEXT),
                        field("ownerUserId", "负责人", EntityField.Type.NUMBER),
                        field("customerId", "客户", EntityField.Type.NUMBER),
                        field("manualGrade", "人工定级", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cut_task");
        declarations.add(new BusinessModelDeclaration(cutoverTask, CutoverTaskDO.class, cutoverTaskMapper, null));
        BusinessModelDescriptor cutoverAssessment = new BusinessModelDescriptor("CUT", "cutoverAssessment",
                "CUT_ASSESSMENT", 1, BusinessModelKind.AGGREGATE_ROOT, "割接评估",
                "pms:cutover-task:query",
                List.of(required("cutoverTaskId", "割接任务", EntityField.Type.NUMBER),
                        field("assessmentStatus", "评估状态", EntityField.Type.TEXT),
                        field("questionnaireTemplateCode", "问卷模板编码", EntityField.Type.TEXT),
                        field("questionnaireTemplateVersion", "问卷模板版本", EntityField.Type.NUMBER),
                        field("answerSnapshot", "答卷快照", EntityField.Type.TEXT),
                        field("manualGrade", "人工定级", EntityField.Type.TEXT),
                        field("simpleFlow", "简易流程", EntityField.Type.BOOLEAN),
                        field("submittedBy", "提交人", EntityField.Type.NUMBER),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "cut_assessment");
        declarations.add(new BusinessModelDeclaration(cutoverAssessment, CutoverAssessmentDO.class,
                cutoverAssessmentMapper, null));
        BusinessModelDescriptor planRevision = new BusinessModelDescriptor("CUT", "cutoverPlanRevision",
                "CUT_PLAN_REVISION", 1, BusinessModelKind.AGGREGATE_ROOT, "割接计划版本",
                "pms:cutover-task:query-plan",
                List.of(required("cutoverTaskId", "割接任务", EntityField.Type.NUMBER),
                        field("revisionNo", "计划修订号", EntityField.Type.NUMBER),
                        field("originCode", "来源", EntityField.Type.TEXT),
                        field("editModeCode", "编辑模式", EntityField.Type.TEXT),
                        field("gradeCode", "定级", EntityField.Type.TEXT),
                        field("statusCode", "状态", EntityField.Type.TEXT),
                        field("currentMarker", "现行标记", EntityField.Type.NUMBER),
                        field("submittedBy", "提交人", EntityField.Type.NUMBER),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME),
                        field("revisionReasonCode", "修订原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cut_plan_revision");
        declarations.add(new BusinessModelDeclaration(planRevision, CutoverPlanRevisionDO.class,
                cutoverPlanRevisionMapper, null));
        BusinessModelDescriptor approvalInstance = new BusinessModelDescriptor("CUT", "cutoverApprovalInstance",
                "CUT_APPROVAL_INSTANCE", 1, BusinessModelKind.AGGREGATE_ROOT, "割接审批实例",
                "pms:cutover-task:query-approval",
                List.of(required("taskId", "割接任务", EntityField.Type.NUMBER),
                        required("projectId", "项目", EntityField.Type.NUMBER),
                        field("planRevisionId", "计划版本", EntityField.Type.NUMBER),
                        field("gradeCode", "定级", EntityField.Type.TEXT),
                        field("initiatorUserId", "发起人", EntityField.Type.NUMBER),
                        field("statusCode", "状态", EntityField.Type.TEXT),
                        field("holdReasonCode", "挂起原因", EntityField.Type.TEXT),
                        field("currentNodeNo", "当前节点", EntityField.Type.NUMBER),
                        field("decisionAt", "决定时间", EntityField.Type.DATETIME),
                        field("rejectionReason", "驳回原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cut_approval_instance");
        declarations.add(new BusinessModelDeclaration(approvalInstance, CutoverApprovalInstanceDO.class,
                cutoverApprovalInstanceMapper, null));
        BusinessModelDescriptor checklist = new BusinessModelDescriptor("CUT", "cutoverChecklist",
                "CUT_CHECKLIST", 1, BusinessModelKind.AGGREGATE_ROOT, "割接核对清单",
                "pms:cutover-task:query",
                List.of(required("cutoverTaskId", "割接任务", EntityField.Type.NUMBER),
                        field("assessmentId", "割接评估", EntityField.Type.NUMBER),
                        field("statusCode", "状态", EntityField.Type.TEXT),
                        field("inputSnapshotHash", "录入快照摘要", EntityField.Type.TEXT),
                        field("configRevisionSnapshot", "配置修订快照", EntityField.Type.TEXT),
                        field("submittedBy", "提交人", EntityField.Type.NUMBER),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME),
                        field("currentMarker", "现行标记", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "cut_cutover_checklist");
        declarations.add(new BusinessModelDeclaration(checklist, CutoverChecklistDO.class,
                cutoverChecklistMapper, null));
        BusinessModelDescriptor closure = new BusinessModelDescriptor("CUT", "cutoverClosure",
                "CUT_CLOSURE", 1, BusinessModelKind.AGGREGATE_ROOT, "割接收尾",
                "pms:cutover-task:query-closure",
                List.of(required("taskId", "割接任务", EntityField.Type.NUMBER),
                        required("projectId", "项目", EntityField.Type.NUMBER),
                        field("approvalInstanceId", "审批实例", EntityField.Type.NUMBER),
                        field("statusCode", "状态", EntityField.Type.TEXT),
                        field("preCheckNormal", "预检正常", EntityField.Type.BOOLEAN),
                        field("executionNormal", "执行正常", EntityField.Type.BOOLEAN),
                        field("testNormal", "验证正常", EntityField.Type.BOOLEAN),
                        field("rollbackOccurred", "是否回退", EntityField.Type.BOOLEAN),
                        field("rollbackSuccessful", "回退成功", EntityField.Type.BOOLEAN),
                        field("finalResultCode", "最终结果", EntityField.Type.TEXT),
                        field("resultRef", "结果引用", EntityField.Type.TEXT),
                        field("submittedBy", "提交人", EntityField.Type.NUMBER),
                        field("submittedAt", "提交时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "cut_cutover_closure");
        declarations.add(new BusinessModelDeclaration(closure, CutoverClosureDO.class, cutoverClosureMapper, null));
        BusinessModelDescriptor configurationRevision = new BusinessModelDescriptor("CUT",
                "cutoverConfigurationRevision", "CUT_CONFIGURATION_REVISION", 1, BusinessModelKind.AGGREGATE_ROOT,
                "割接配置修订", "pms:cutover-config:query",
                List.of(required("configurationCode", "配置编码", EntityField.Type.TEXT),
                        required("configurationName", "配置名称", EntityField.Type.TEXT),
                        field("revisionNo", "修订号", EntityField.Type.NUMBER),
                        field("statusCode", "状态", EntityField.Type.TEXT),
                        field("effectiveFrom", "生效时间", EntityField.Type.DATETIME),
                        field("effectiveTo", "失效时间", EntityField.Type.DATETIME),
                        field("changeSummary", "变更摘要", EntityField.Type.TEXT),
                        field("publishedBy", "发布人", EntityField.Type.NUMBER),
                        field("publishedAt", "发布时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "cut_cutover_configuration_revision");
        declarations.add(new BusinessModelDeclaration(configurationRevision, CutoverConfigurationRevisionDO.class,
                cutoverConfigurationRevisionMapper, null));
        BusinessModelDescriptor spareReference = new BusinessModelDescriptor("CUT", "cutoverSpareApplicationReference",
                "CUT_SPARE_APPLICATION_REFERENCE", 1, BusinessModelKind.AGGREGATE_ROOT, "备件申请引用",
                "pms:cutover-task:query",
                List.of(required("cutoverTaskId", "割接任务", EntityField.Type.NUMBER),
                        required("projectId", "项目", EntityField.Type.NUMBER),
                        field("platformRequestId", "平台请求", EntityField.Type.TEXT),
                        field("integrationStatus", "集成状态", EntityField.Type.TEXT),
                        field("externalSystemCode", "外部系统", EntityField.Type.TEXT),
                        field("externalApplicationNo", "外部申请编号", EntityField.Type.TEXT),
                        field("launchUrl", "外发地址", EntityField.Type.TEXT),
                        field("retryCount", "重试次数", EntityField.Type.NUMBER),
                        field("lastFailureCode", "最近失败码", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "cut_spare_application_reference");
        declarations.add(new BusinessModelDeclaration(spareReference, CutoverSpareApplicationReferenceDO.class,
                cutoverSpareApplicationReferenceMapper, null));
        return declarations;
    }
}
