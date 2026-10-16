package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.business;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.business.DurationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.business.DurationBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.*;
import cn.iocoder.yudao.module.pms.engineering.domain.constructionplan.DurationRules;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.*;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.patch.DurationChangePatch;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.deadline.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_NOT_EXISTS;

/** The current root uses inherited CRUD; duration history is append-only and changes retain BPM authorization. */
@Service @RequiredArgsConstructor
public class DurationBusinessService extends DefaultProjectBusinessService<DurationBusinessMapper,DurationBusinessDO>{
    private final ConstructionPlanMapper roots;
    private final ConstructionPlanRevisionMapper revisions;
    private final ConstructionPlanChangeMapper changeRows;
    private final ConstructionPlanQueryService queries;
    private final DurationChangeApplicationService changes;
    private final ProjectParticipantFactApi participants;
    private final ProjectEndDateApi deadlines;
    private final PermissionApi permissions;
    private final ProjectScopeApi projectScopes;
    private static final String MANAGE=ConstructionPlanApplicationService.PERMISSION_MANAGE;
    @Override protected int initialPageSize(){return 1;}
    @Override protected List<Long> initialFormIds(List<DurationBusinessDO> rows,Long selected){
        return selected!=null?List.of(selected):rows.isEmpty()?List.of():List.of(rows.getFirst().getId());
    }
    @Override protected List<BusinessOperationDescriptor> configureOperations(List<BusinessOperationDescriptor> operations){return operations.stream().filter(op->!"delete".equals(op.code())).map(op->new BusinessOperationDescriptor(op.code(),op.version(),op.name(),op.kind(),"configure-fields".equals(op.code())?op.authorizationPolicyRef():MANAGE)).toList();}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(
            new BusinessOperationDescriptor("change-create",1,"新建工期变更",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,MANAGE),
            new BusinessOperationDescriptor("change-save",1,"保存变更草稿",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,MANAGE),
            new BusinessOperationDescriptor("change-submit",1,"提交工期变更",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,MANAGE));}
    @Override protected void authorizeModelRead(EntityActor caller,String scene){if(!permissions.hasAnyPermissions(caller.userId(),ConstructionPlanQueryService.PERMISSION_QUERY,MANAGE))throw invalid("无权读取项目工期");}
    @Override protected void beforeCreate(DurationBusinessDO row){if(roots.selectByProjectId(actor().tenantId(),row.getProjectId())!=null)throw invalid("项目已经录入工期，请新建工期变更");row.setPlanRecalculationStatusCode(ConstructionPlanDO.RECALCULATION_PENDING);}
    public record InitialContext(Long expectedProjectVersion){ }
    @Override protected void beforeFormWrite(DurationBusinessDO before,DurationBusinessDO row,Map<String,Object> body){
        if(body.keySet().stream().anyMatch(key->!"expectedProjectVersion".equals(key)))throw invalid("工期命令字段无效");
        if(before!=null)return;
        var request=JsonUtils.parseObject(JsonUtils.toJsonString(body),InitialContext.class);
        if(request.expectedProjectVersion()==null || request.expectedProjectVersion()<0)throw invalid("缺少项目版本，请重新读取项目");
        participants.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(row.getProjectId(),actor().userId(),request.expectedProjectVersion(),"ACTIVE",null,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        var duration=DurationRules.resolve(row.getCalculationBasis(),row.getStartDate(),row.getEndDate(),row.getDurationDays());
        deadlines.validatePlanningEndDate(new ProjectEndDateCommand(actor().tenantId(),actor().userId(),row.getProjectId(),request.expectedProjectVersion(),duration.endDate()));
        row.setCalculationBasis(duration.calculationBasisCode());row.setStartDate(duration.startDate());row.setEndDate(duration.endDate());row.setDurationDays(duration.durationDays());row.setValidatedInitial(true);
    }
    @Override protected void validateBusiness(DurationBusinessDO row){if(row.getCurrentDurationRevisionId()==null && !row.isValidatedInitial())throw invalid("首次工期必须校验项目资格与日期");}
    @Override protected void beforeUpdate(DurationBusinessDO before,DurationBusinessDO row){
        if(!Objects.equals(before.getProjectId(),row.getProjectId()) || !Objects.equals(before.getCalculationBasis(),row.getCalculationBasis()) || !Objects.equals(before.getStartDate(),row.getStartDate()) || !Objects.equals(before.getEndDate(),row.getEndDate()) || !Objects.equals(before.getDurationDays(),row.getDurationDays()))throw invalid("生效工期不可覆盖，请新建工期变更");
    }
    @Override protected void afterBodyPersisted(String operation,DurationBusinessDO before,DurationBusinessDO row){
        if(!"create".equals(operation))return;
        var revision=new ConstructionPlanRevisionDO();revision.setTenantId(actor().tenantId());revision.setPlanId(row.getId());revision.setRevisionNo(1);
        revision.setCalculationBasisCode(row.getCalculationBasis());revision.setStartDate(row.getStartDate());revision.setEndDate(row.getEndDate());revision.setDurationDays(row.getDurationDays());
        var now=LocalDateTime.now();revision.setFrozenAt(now);revision.setEffectiveAt(now);revision.setCreatedAt(now);revision.setCreatedBy(actor().userId());revision.setVersion(0);
        if(revisions.insert(revision)!=1 || revision.getId()==null)throw invalid("工期首个版本创建失败");
        if(roots.updateVersionIfMatch(new ConstructionPlanVersionUpdate(actor().tenantId(),row.getId(),row.getVersion(),revision.getId(),null,ConstructionPlanDO.RECALCULATION_PENDING,revision.getId(),actor().userId().toString()))!=1)throw invalid("工期版本已变化");
    }
    @Override protected void afterRead(DurationBusinessDO row){
        if(row.getCurrentDurationRevisionId()==null)return;
        var current=revisions.selectById(new ConstructionPlanRevisionLockQuery(actor().tenantId(),row.getId(),row.getCurrentDurationRevisionId()));
        if(current==null)throw invalid("当前工期版本不存在");row.setCurrentRead(current);row.setCalculationBasis(current.getCalculationBasisCode());row.setStartDate(current.getStartDate());row.setEndDate(current.getEndDate());row.setDurationDays(current.getDurationDays());
    }
    @Override protected void beforeDelete(DurationBusinessDO row){throw invalid("工期及批准历史不能删除");}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","DURATION_EFFECTIVE","项目工期已生效");}
    @Override protected RuntimeResult runtimeResult(DurationBusinessDO row){
        var current=row.getCurrentRead()!=null?row.getCurrentRead():row.getCurrentDurationRevisionId()==null?null:revisions.selectById(new ConstructionPlanRevisionLockQuery(row.getTenantId(),row.getId(),row.getCurrentDurationRevisionId()));
        boolean effective=current!=null && current.getFrozenAt()!=null;
        return new RuntimeResult(Map.of("BUSINESS_RECORD_SAVED",true,"DURATION_EFFECTIVE",effective),effective,current==null?row.getUpdateTime():current.getFrozenAt());
    }
    @Override protected Map<String,Object> formContext(DurationBusinessDO row){
        return Map.of("duration",durationView(row,actor()));
    }
    /** Mirrors the native query presentation using the root already authorized by form(). */
    private ConstructionPlanRespVO durationView(DurationBusinessDO plan,EntityActor caller){
        var current=plan.getCurrentRead()!=null?plan.getCurrentRead():revisions.selectById(new ConstructionPlanRevisionLockQuery(caller.tenantId(),plan.getId(),plan.getCurrentDurationRevisionId()));
        if(current==null)throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        var pending=plan.getPendingChangeId()==null?null:changeRows.selectById(new ConstructionPlanChangeLockQuery(caller.tenantId(),plan.getId(),plan.getPendingChangeId()));
        if(plan.getPendingChangeId()!=null && pending==null)throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        var candidate=pending==null?null:revisions.selectById(new ConstructionPlanRevisionLockQuery(caller.tenantId(),plan.getId(),pending.getCandidateRevisionId()));
        if(pending!=null && candidate==null)throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        var view=new ConstructionPlanRespVO();view.setPlanId(plan.getId());view.setProjectId(plan.getProjectId());
        view.setCurrentRevision(revisionView(current,plan.getCurrentDurationRevisionId()));
        if(pending!=null){
            var summary=changeView(pending);summary.setCandidateRevision(revisionView(candidate,plan.getCurrentDurationRevisionId()));view.setPendingChangeSummary(summary);
        }
        view.setPlanRecalculationStatus(plan.getPlanRecalculationStatusCode());view.setPlanRecalculationSourceRevisionId(plan.getPlanRecalculationSourceRevisionId());
        view.setPlanVersion(plan.getVersion());view.setAllowedActions(durationActions(plan,caller));return view;
    }
    private List<String> durationActions(DurationBusinessDO plan,EntityActor caller){
        if(plan.getPendingChangeId()!=null || !permissions.hasAnyPermissions(caller.userId(),MANAGE))return List.of();
        try{
            var scope=projectScopes.resolveCurrent(new ProjectCurrentScopeQuery(caller.tenantId(),caller.userId(),plan.getProjectId(),ProjectScopeApi.ACTION_MANAGE));
            if(!scope.fullProjectIds().contains(plan.getProjectId()))throw exception(FORBIDDEN);
            var fact=participants.inspect(new ProjectParticipantFactQuery(plan.getProjectId(),caller.userId(),Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),LocalDateTime.now()));
            return "ACTIVE".equals(fact.lifecycleStatus())?List.of("CREATE_CHANGE"):List.of();
        }catch(ServiceException denied){return List.of();}
    }
    private ConstructionPlanRevisionRespVO revisionView(ConstructionPlanRevisionDO row,Long currentId){
        var view=new ConstructionPlanRevisionRespVO();view.setRevisionId(row.getId());view.setRevisionNo(row.getRevisionNo());
        view.setCalculationBasis(row.getCalculationBasisCode());view.setStartDate(row.getStartDate());view.setEndDate(row.getEndDate());view.setDurationDays(row.getDurationDays());
        view.setSourceChangeId(row.getSourceChangeId());view.setFrozenAt(row.getFrozenAt());view.setEffectiveAt(row.getEffectiveAt());
        view.setCreatedBy(row.getCreatedBy());view.setCreatedAt(row.getCreatedAt());view.setVersion(row.getVersion());view.setCurrent(row.getId().equals(currentId));return view;
    }
    private ConstructionPlanChangeRespVO changeView(ConstructionPlanChangeDO row){
        var view=new ConstructionPlanChangeRespVO();view.setChangeId(row.getId());view.setBaseRevisionId(row.getBaseRevisionId());view.setCandidateRevisionId(row.getCandidateRevisionId());
        view.setStatus(row.getStatusCode());view.setReasonType(row.getReasonTypeCode());view.setReasonDetail(row.getReasonDetail());view.setCustomerEvidenceRequired(row.getCustomerEvidenceRequired());
        view.setCustomerEvidenceFileId(row.getCustomerEvidenceFileId());view.setCustomerEvidenceFileVersion(row.getCustomerEvidenceFileVersion());view.setCustomerEvidenceReferenceKey(row.getCustomerEvidenceReferenceKey());
        view.setProcessDefinitionKey(row.getProcessDefinitionKey());view.setProcessInstanceId(row.getProcessInstanceId());view.setSubmittedAt(row.getSubmittedAt());
        view.setApplicantUserId(row.getApplicantUserId());view.setApproverUserId(row.getApproverUserId());view.setApprovedAt(row.getApprovedAt());view.setApprovalOpinion(row.getApprovalOpinion());
        view.setCreatedAt(row.getCreatedAt());view.setVersion(row.getVersion());return view;
    }
    public ConstructionPlanCursorPageRespVO<ConstructionPlanRevisionRespVO> revisions(Long id,ConstructionPlanRevisionPageReqVO page){get(id);return queries.getRevisions(id,page,queryActor());}
    public ConstructionPlanCursorPageRespVO<ConstructionPlanChangeRespVO> changes(Long id,ConstructionPlanChangePageReqVO page){get(id);return queries.getChanges(id,page,queryActor());}
    public BusinessOperationReceipt createChange(Long id,Long version,String key,DurationChangeCreateReqVO input){
        return businessAction("change-create",id,version,key,Map.of("content",input),ReceiptOutcome.SAVED,row->{
            changes.createDraft(new CreateDurationChangeCommand(id,Math.toIntExact(version),input.getExpectedProjectVersion(),input.getCalculationBasis(),input.getStartDate(),input.getEndDate(),input.getDurationDays(),input.getReasonType(),input.getReasonDetail(),input.getCustomerEvidenceFileId(),input.getCustomerEvidenceFileVersion(),input.getCustomerEvidenceReferenceKey(),key,digest(input)),commandActor());
            persistBusinessChange(row,copy(row),"change-create");return mapper.selectById(id);
        });
    }
    public BusinessOperationReceipt saveChange(Long id,Long version,String key,Long changeId,Integer changeVersion,DurationChangePatchReqVO input){
        return businessAction("change-save",id,version,key,Map.of("changeId",changeId,"changeVersion",changeVersion,"content",input,"fields",input.getSubmittedFields()),ReceiptOutcome.SAVED,row->{
            var patch=new DurationChangePatch(input.getCalculationBasis(),input.getStartDate(),input.getEndDate(),input.getDurationDays(),input.getReasonType(),input.getReasonDetail(),input.getCustomerEvidenceFileId(),input.getCustomerEvidenceFileVersion(),input.getCustomerEvidenceReferenceKey(),input.getSubmittedFields());
            changes.patchDraft(new PatchDurationChangeCommand(id,changeId,changeVersion,input.getExpectedProjectVersion(),patch),commandActor());persistBusinessChange(row,copy(row),"change-save");return mapper.selectById(id);
        });
    }
    public BusinessOperationReceipt submitChange(Long id,Long version,String key,Long changeId,Integer changeVersion,Long projectVersion){
        var intent=Map.<String,Object>of("changeId",changeId,"changeVersion",changeVersion,"projectVersion",projectVersion);
        return businessAction("change-submit",id,version,key,intent,ReceiptOutcome.APPROVAL_PENDING,row->{changes.submit(new SubmitDurationChangeCommand(id,changeId,changeVersion,projectVersion,key,digest(intent)),commandActor());return mapper.selectById(id);});
    }
    private ConstructionPlanQueryService.Actor queryActor(){return new ConstructionPlanQueryService.Actor(actor().tenantId(),actor().userId());}
    private ConstructionPlanApplicationService.Actor commandActor(){
        var caller=actor();String correlation=caller.correlationId();
        return new ConstructionPlanApplicationService.Actor(caller.tenantId(),caller.userId(),correlation==null||correlation.isBlank()?UUID.randomUUID().toString():correlation);
    }
    private String digest(Object value){return cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(value));}
    private BusinessContractException invalid(String reason){return new BusinessContractException("DURATION_INVALID",reason);}
}
