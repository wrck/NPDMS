package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Common execution owns replay/transaction/outcome; the existing Owner retains every domain rule. */
@Service
@BusinessEntityService(ownerModule="SOL",entityType="siteSurvey",nativeEntityType="SITE_SURVEY")
public class SiteSurveyBusinessApplicationService extends DefaultBusinessApplicationService {
    private final SiteSurveyEntityDomainCommands domain;
    private final SiteSurveyEntityMapper mapper;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final Validator validator;
    private static final Set<String> SAVE_FIELDS = Set.of("projectId", "name", "surveyDate", "surveyorUserId",
            "location", "locationMaintenance", "powerSupply", "cabinet", "networkPort", "fiber", "module", "cable",
            "ground", "constructionResource", "conclusion", "remark", "formRevisionId", "formRevisionVersion",
            "extensionDefinitionRevisionId", "businessValues", "extensionValues", "outsourceRequired",
            "projectEndDateVersion", "projectEndDateChanged");

    public SiteSurveyBusinessApplicationService(BusinessCallerContext callerContext, BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard, OperationExecutionStore store,
            BusinessEventPort events, OperationAuditApi audit, PlatformTransactionManager transactionManager,
            SiteSurveyEntityDomainCommands domain, SiteSurveyEntityMapper mapper, PermissionApi permissions,
            ProjectScopeApi scopes, Validator validator) {
        super(callerContext,catalog,persistence,guard,store,events,audit,new TransactionTemplate(transactionManager));
        this.domain=domain; this.mapper=mapper; this.permissions=permissions; this.scopes=scopes; this.validator=validator;
    }

    /** A single declaration is used by the catalog and Owner validation. No fictional history/approval. */
    public static List<BusinessOperationDescriptor> operations() {
        return List.of(op("create","创建草稿",BusinessOperationDescriptor.StandardOperationKind.CREATE),
                op("save","保存草稿",BusinessOperationDescriptor.StandardOperationKind.UPDATE),
                op("delete","删除草稿",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND),
                op("confirm","确认工勘",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND),
                op("reject","驳回工勘",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND),
                op("archive","归档工勘",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND));
    }
    private static BusinessOperationDescriptor op(String code,String name,BusinessOperationDescriptor.StandardOperationKind kind) {
        return new BusinessOperationDescriptor(code,1,name,kind);
    }

    @Override protected void validateIdentityAndInput(ResolvedCaller caller,BusinessOperationRequest request) {
        operationOf(descriptor(request),request);
        if (request.entryKind()==null || request.idempotencyKey()==null || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length()>128) throw invalid("OPERATION_INPUT_INVALID","入口和幂等键必填");
        if (!"SOL".equals(request.ownerModule()) || !"siteSurvey".equals(request.entityType()))
            throw invalid("ENTITY_IDENTITY_MISMATCH","工勘目录身份不一致");
        if (!operations().stream().anyMatch(op -> op.code().equals(request.operationCode())))
            throw invalid("OPERATION_NOT_DECLARED","工勘未开放该操作");
        boolean create="create".equals(request.operationCode());
        if (create) {
            if (request.targetRef()!=null || request.concurrencyBasis()!=null) throw invalid("OPERATION_INPUT_INVALID","创建不得携带目标和并发依据");
        } else {
            if (request.targetRef()==null || request.targetRef().isRevision() || request.concurrencyBasis()==null || request.concurrencyBasis()<0)
                throw invalid("OPERATION_INPUT_INVALID","工勘操作须携带当前对象与并发依据");
            var ref=request.targetRef().entity();
            if (!caller.tenantId().equals(ref.tenantId()) || !"SOL".equals(ref.ownerModule()) || !"siteSurvey".equals(ref.entityType()))
                throw invalid("ENTITY_IDENTITY_MISMATCH","工勘目标身份不一致");
        }
        Set<String> fields=save(request) ? Set.of("values","execution") : Set.of("execution");
        if (request.input()==null || !fields.containsAll(request.input().keySet())) throw invalid("OPERATION_INPUT_INVALID","不支持的工勘操作字段");
        if (save(request)) {
            if (!(request.input().get("values") instanceof Map<?,?> values) || !SAVE_FIELDS.containsAll(values.keySet()))
                throw invalid("OPERATION_INPUT_INVALID","不支持的工勘保存字段");
            var dto=saveRequest(request);
            if (!validator.validate(dto).isEmpty()) throw invalid("OPERATION_INPUT_INVALID","工勘保存参数无效");
        }
        selection(request);
    }

    @Override protected void authorizeAndCheckState(ResolvedCaller caller,BusinessOperationRequest request) {
        Long projectId="create".equals(request.operationCode()) ? saveRequest(request).getProjectId() : identity(caller,request).getProjectId();
        if (!permissions.hasAnyPermissions(caller.userId(),permission(request.operationCode()))) throw exception(FORBIDDEN);
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(caller.tenantId(),caller.userId(),projectId,ProjectScopeApi.ACTION_MANAGE));
        if (scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        if ("save".equals(request.operationCode()) && !projectId.equals(saveRequest(request).getProjectId()))
            throw invalid("ENTITY_IDENTITY_MISMATCH","不得更换工勘所属项目");
        ownerScope(caller,request,projectId,()->null);
        // Mutable state and expected version are checked after replay; authorization always precedes it.
    }

    @Override protected LockedAggregate<BaseBusinessEntity> lockAggregate(ResolvedCaller caller,BusinessOperationRequest request) {
        try { reserveExecution(caller,request); }
        catch(ReplayedOperation replay) {
            requireCurrentReceiptAccess(caller,request,replay.receipt());
            throw replay;
        }
        if ("create".equals(request.operationCode())) return new LockedAggregate<>(null,null);
        var observed=identity(caller,request);
        var locked=mapper.selectTaskObjectForUpdate(new SiteSurveyEntityTaskObjectQuery(caller.tenantId(),observed.getProjectId(),observed.getId()));
        if (locked==null || !caller.tenantId().equals(locked.getTenantId()) || !Objects.equals(observed.getProjectId(),locked.getProjectId())
                || !Objects.equals(locked.getId(),request.targetRef().entity().entityId())) throw invalid("ENTITY_NOT_FOUND","当前工勘不存在");
        if (!Objects.equals(locked.getVersion(),request.concurrencyBasis())) throw invalid("CONCURRENCY_CONFLICT","工勘并发依据过期");
        return new LockedAggregate<>(locked,locked.getVersion());
    }

    @Override protected void requireReceiptOwnerAccess(ResolvedCaller caller,BusinessOperationRequest request,
                                                       BusinessOperationReceipt receipt) {
        // The native identity query includes soft-deleted targets for immutable delete/replay receipts.
        var row=mapper.selectOperationIdentity(new SiteSurveyOperationIdentityQuery(caller.tenantId(),receipt.entityRef().entityId()));
        if(row==null || !caller.tenantId().equals(row.getTenantId()) || !receipt.entityRef().entityId().equals(row.getId()))
            throw invalid("ENTITY_NOT_FOUND","回执工勘不存在");
        if(!permissions.hasAnyPermissions(caller.userId(),permission(request.operationCode()))) throw exception(FORBIDDEN);
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(caller.tenantId(),caller.userId(),row.getProjectId(),ProjectScopeApi.ACTION_MANAGE));
        if(scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(row.getProjectId())) throw exception(FORBIDDEN);
    }

    @Override protected BusinessOperationReceipt domainCommand(ResolvedCaller caller,BusinessOperationRequest request,LockedAggregate<BaseBusinessEntity> locked) {
        Long projectId="create".equals(request.operationCode()) ? saveRequest(request).getProjectId() : ((SiteSurveyEntityDO)locked.aggregate()).getProjectId();
        return ownerScope(caller,request,projectId,()->{
            Long id=request.targetRef()==null ? null : request.targetRef().entity().entityId();
            var execution=selection(request);
            switch(request.operationCode()) {
                case "create" -> id=domain.createSiteSurveyEntity(saveRequest(request));
                case "save" -> domain.updateSiteSurveyEntity(saveRequest(request));
                case "delete" -> domain.deleteSiteSurveyEntity(id,execution);
                case "confirm" -> domain.confirmSiteSurveyEntity(id,execution);
                case "reject" -> domain.rejectSiteSurveyEntity(id,execution);
                case "archive" -> domain.archiveSiteSurveyEntity(id,execution);
                default -> throw invalid("OPERATION_NOT_DECLARED","工勘未开放该操作");
            }
            boolean deleted="delete".equals(request.operationCode());
            var actual=deleted ? null : mapper.selectById(id);
            if (!deleted && (actual==null || !caller.tenantId().equals(actual.getTenantId()) || !projectId.equals(actual.getProjectId())))
                throw invalid("ENTITY_IDENTITY_MISMATCH","工勘写入结果身份不一致");
            Long version=deleted ? Math.addExact(request.concurrencyBasis(),1L) : actual.getVersion();
            String state=deleted ? "DELETED" : String.valueOf(actual.getStatus());
            var ref=new EntityRef(caller.tenantId(),"SOL","siteSurvey",id);
            var payload=new Result(id.toString(),projectId.toString(),version,state,deleted);
            return new BusinessOperationReceipt("confirm".equals(request.operationCode()) || "archive".equals(request.operationCode())
                    ? ReceiptOutcome.EFFECTED : ReceiptOutcome.SAVED,ref,version,
                    List.of(new ResultReference(ResultReference.Kind.COMMAND,"SOL",JsonUtils.toJsonString(payload))),null,null,
                    request.operationCode(),request.operationVersion());
        });
    }

    @Override protected String requestDigest(BusinessOperationRequest request) {
        @SuppressWarnings("unchecked") Map<String,Object> input=(Map<String,Object>) canonical(request.input());
        return OperationRequestDigest.of(new BusinessOperationRequest(request.operationCode(),request.operationVersion(),
                request.targetRef(),request.ownerModule(),request.entityType(),input,request.idempotencyKey(),
                request.concurrencyBasis(),request.entryKind(),request.entryCorrelationId()));
    }
    private static Object canonical(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Object> result=new TreeMap<>();map.forEach((key,item) -> result.put(String.valueOf(key),canonical(item)));return result;
        }
        if (value instanceof List<?> list) return list.stream().map(SiteSurveyBusinessApplicationService::canonical).toList();
        return value;
    }

    private SiteSurveyEntityDO identity(ResolvedCaller caller,BusinessOperationRequest request) {
        var row=mapper.selectOperationIdentity(new SiteSurveyOperationIdentityQuery(caller.tenantId(),request.targetRef().entity().entityId()));
        if (row==null || !caller.tenantId().equals(row.getTenantId()) || !request.targetRef().entity().entityId().equals(row.getId()))
            throw invalid("ENTITY_NOT_FOUND","工勘不存在");
        return row;
    }
    private <T> T ownerScope(ResolvedCaller caller,BusinessOperationRequest request,Long projectId,java.util.function.Supplier<T> work) {
        return ProjectOwnerOperationScope.call("SOL","SITE_SURVEY",()->new ProjectOwnerOperationScope.Declaration(
                caller.tenantId(),caller.userId(),projectId,"SOL","SITE_SURVEY",ownerOperation(request.operationCode()),1,
                request.targetRef()==null ? null : request.targetRef().entity().entityId().toString(),selection(request)),work);
    }
    private static boolean save(BusinessOperationRequest request) { return Set.of("create","save").contains(request.operationCode()); }
    private SiteSurveyEntitySaveReqVO saveRequest(BusinessOperationRequest request) {
        SiteSurveyEntitySaveReqVO dto=decode(request.input().get("values"),SiteSurveyEntitySaveReqVO.class);
        if (dto==null) throw invalid("OPERATION_INPUT_INVALID","保存内容必填");
        dto.setId(request.targetRef()==null ? null : request.targetRef().entity().entityId());
        dto.setVersion(request.concurrencyBasis()); dto.setExecution(selection(request));
        return dto;
    }
    private ProjectBusinessExecutionSelection selection(BusinessOperationRequest request) { return decode(request.input().get("execution"),ProjectBusinessExecutionSelection.class); }
    private static <T> T decode(Object input,Class<T> type) {
        if (input==null) return null;
        try { return JsonUtils.parseObject(JsonUtils.toJsonString(input),type); }
        catch(RuntimeException invalid) { throw invalid("OPERATION_INPUT_INVALID","工勘操作参数无效"); }
    }
    static String permission(String code) { return "pms:sol-site-survey:"+("create".equals(code) ? "create" : "delete".equals(code) ? "delete" : "update"); }
    static String ownerOperation(String code) { return "SOL.SITE_SURVEY."+("save".equals(code) ? "UPDATE" : code.toUpperCase(Locale.ROOT)); }
    public static Result result(BusinessOperationReceipt receipt) {
        return receipt.references().stream().filter(r -> r.kind()==ResultReference.Kind.COMMAND && "SOL".equals(r.ownerModule()))
                .map(r -> JsonUtils.parseObject(r.value(),Result.class)).findFirst().orElseThrow(()->invalid("RECEIPT_INVALID","缺少工勘结果回执"));
    }
    public record Result(String id,String projectId,Long version,String state,boolean deleted) {}
    private static BusinessContractException invalid(String code,String message) { return new BusinessContractException(code,message); }
}
