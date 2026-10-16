package cn.iocoder.yudao.module.pms.platform.service.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.query.DefaultApprovalQuery;
import cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalProcessApi.*;

/** The entity's inherited authorization and BPM's native authorization are both required. */
@Service @RequiredArgsConstructor
public class DefaultBusinessApprovalService implements DefaultBusinessApprovalApi {
    private static final String PURPOSE="DEFAULT_BUSINESS_REVIEW";
    private final DirectBusinessOwners owners;
    private final BusinessCallerContext callers;
    private final ApprovalAttemptMapper attempts;
    private final ApprovalOpinionMapper opinions;
    private final DefaultBusinessApprovalProcessApi processes;
    private final BusinessEventPort events;

    @Override public List<Definition> definitions(EntityRef entity) {
        subject(entity,true,false);return processes.definitions(callers.require().userId());
    }
    @Override public Preview preview(EntityRef entity,PreviewRequest request) {
        var subject=subject(entity,true,false);
        return processes.preview(new Command(context(subject,"preview"),request.definitionId(),subject.values(),request.variables(),Map.of()));
    }
    @Override public List<Attempt> list(EntityRef entity) {
        var subject=subject(entity,false,false);
        return attempts.selectDefaultAttempts(query(entity,null)).stream().map(row->view(row,subject.version())).toList();
    }
    @Override public Detail detail(EntityRef entity,Long attemptId) {
        var subject=subject(entity,false,false);var row=owned(entity,attemptId);
        return new Detail(view(row,subject.version()),processes.detail(context(subject,row),row.getInstanceRef()));
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public Attempt start(EntityRef entity,Start request) {
        var subject=subject(entity,true,true);
        if(request==null || request.version()==null || request.version()<0 || request.definitionId()==null || request.definitionId().isBlank()
                || request.idempotencyKey()==null || !request.idempotencyKey().matches("[A-Za-z0-9_.:-]{1,64}"))
            throw invalid("APPROVAL_REQUEST_INVALID","审批请求缺少流程、版本或幂等键");
        String basis=digest(Arrays.asList(callers.require().userId(),entity,request));
        var prior=attempts.lockDefaultAttempt(query(entity,request.idempotencyKey()));
        if(prior!=null){
            if(!basis.equals(prior.getSubmissionBasis()))throw invalid("IDEMPOTENCY_DIGEST_CONFLICT","幂等键已用于另一审批请求");
            return view(prior,subject.version());
        }
        var history=attempts.selectDefaultAttempts(query(entity,null));
        if(!Objects.equals(subject.version(),request.version()))throw invalid("CONCURRENCY_CONFLICT","业务内容已更新，请重新读取后发起审批");
        if(history.stream().anyMatch(row->ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus())))
            throw invalid("APPROVAL_ATTEMPT_STILL_PENDING","上一轮审批仍在进行中");
        var command=new Command(context(subject,request.idempotencyKey()),request.definitionId(),subject.values(),request.variables(),request.selectedApprovers());
        var result=processes.start(command);
        var row=new ApprovalAttemptDO();row.setTenantId(entity.tenantId());row.setOwnerModule(entity.ownerModule());
        row.setEntityType(entity.entityType());row.setEntityId(entity.entityId());row.setPurpose(PURPOSE);row.setBackendId(BACKEND);
        row.setAttemptId(request.idempotencyKey());row.setSubmissionBasis(basis);row.setSubmissionConcurrencyBasis(Math.toIntExact(subject.version()));
        row.setNeutralProcessRef(request.definitionId());row.setInstanceRef(result.instanceId());row.setStatus(ApprovalAttemptDO.STATUS_PENDING);
        row.setCreator(String.valueOf(callers.require().userId()));row.setVersion(0);
        if(!history.isEmpty())row.setPreviousAttemptId(history.getFirst().getId());
        attempts.insert(row);opinion(row,ApprovalOpinionDO.ACTION_SUBMIT,"提交业务版本 "+subject.version(),callers.require().userId());
        // A flow may finish inside start(). Its early callback has no association yet; the returned fact closes that gap.
        apply(row,result);
        return view(row,subject.version());
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public Attempt cancel(EntityRef entity,Long attemptId,Cancel request) {
        var subject=subject(entity,true,true);var observed=owned(entity,attemptId);
        var row=attempts.lockDefaultAttempt(query(entity,observed.getAttemptId()));
        if(!ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus()))throw invalid("APPROVAL_NOT_PENDING","该审批已结束");
        var result=processes.cancel(context(subject,row),row.getInstanceRef(),request.reason(),callers.require().userId());
        // Native cancellation can synchronously invoke record(); re-read the locked version before reconciling.
        row=attempts.lockDefaultAttempt(query(entity,observed.getAttemptId()));apply(row,result);
        return view(row,subject.version());
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public void record(Result result) {
        if(result==null || result.context()==null || result.context().entity()==null
                || !Objects.equals(TenantContextHolder.getRequiredTenantId(),result.context().entity().tenantId()))
            throw invalid("APPROVAL_RESULT_SCOPE_INVALID","审批回调租户不一致");
        var row=attempts.lockDefaultAttempt(query(result.context().entity(),result.context().attemptId()));
        if(row!=null)apply(row,result);
    }
    private void apply(ApprovalAttemptDO row,Result result) {
        var context=result.context();var entity=context.entity();
        if(!Objects.equals(row.getTenantId(),entity.tenantId()) || !Objects.equals(row.getOwnerModule(),entity.ownerModule())
                || !Objects.equals(row.getEntityType(),entity.entityType()) || !Objects.equals(row.getEntityId(),entity.entityId())
                || !Objects.equals(row.getAttemptId(),context.attemptId()) || !Objects.equals(row.getCreator(),String.valueOf(context.actorId()))
                || !Objects.equals(row.getNeutralProcessRef(),result.definitionId()) || !Objects.equals(row.getInstanceRef(),result.instanceId())
                || !Objects.equals(row.getSubmissionConcurrencyBasis().longValue(),context.version())
                || !Set.of("PENDING","APPROVED","REJECTED","WITHDRAWN").contains(result.status()))
            throw invalid("APPROVAL_RESULT_SCOPE_INVALID","流程结果与原提交不一致");
        if(Objects.equals(row.getStatus(),result.status()))return;
        if(!ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus()))throw invalid("APPROVAL_RESULT_CONFLICT","历史审批结论不能覆盖");
        row.setStatus(result.status());row.setDecidedTime(LocalDateTime.now());
        String reason=result.reason();row.setConclusionBasis(reason==null?null:reason.substring(0,Math.min(255,reason.length())));
        if(attempts.updateById(row)!=1)throw invalid("CONCURRENCY_CONFLICT","审批结论已变化");
        String action=switch(result.status()){case "APPROVED"->"APPROVE";case "REJECTED"->"REJECT";default->"WITHDRAW";};
        opinion(row,action,row.getConclusionBasis(),0L);
        events.append(new BusinessEventRecord(UUID.randomUUID().toString(),entity,BusinessEventKind.CHANGED,
                row.getVersion().toString(),row.getAttemptId(),Map.of("operation","approval-result","attemptId",row.getId(),"status",row.getStatus()),
                0,new BusinessEventRecord.ProjectChange(context.projectId(),context.actorId())));
    }
    private Subject subject(EntityRef entity,boolean write,boolean lock) {
        var caller=callers.require();
        if(entity==null || !Objects.equals(entity.tenantId(),caller.tenantId()))throw invalid("ACCESS_DENIED","审批主体租户不一致");
        var service=owners.byIdentity(entity.ownerModule(),entity.entityType()).orElseThrow(()->invalid("ACCESS_DENIED","业务类型不可用"));
        return service.approvalSubject(entity.entityId(),write,lock);
    }
    private ApprovalAttemptDO owned(EntityRef entity,Long id) {
        var row=attempts.selectById(id);
        if(row==null || !Objects.equals(row.getTenantId(),entity.tenantId()) || !Objects.equals(row.getOwnerModule(),entity.ownerModule())
                || !Objects.equals(row.getEntityType(),entity.entityType()) || !Objects.equals(row.getEntityId(),entity.entityId())
                || !PURPOSE.equals(row.getPurpose()) || !BACKEND.equals(row.getBackendId()))throw invalid("ACCESS_DENIED","审批记录不属于当前业务");
        return row;
    }
    private Context context(Subject subject,String attemptId){return new Context(subject.entity(),subject.projectId(),subject.version(),attemptId,callers.require().userId());}
    private Context context(Subject subject,ApprovalAttemptDO row){return new Context(subject.entity(),subject.projectId(),row.getSubmissionConcurrencyBasis().longValue(),row.getAttemptId(),Long.valueOf(row.getCreator()));}
    private Attempt view(ApprovalAttemptDO row,Long currentVersion){return new Attempt(row.getId(),row.getNeutralProcessRef(),row.getInstanceRef(),row.getStatus(),row.getSubmissionConcurrencyBasis().longValue(),Objects.equals(currentVersion,row.getSubmissionConcurrencyBasis().longValue()),row.getCreator(),row.getCreateTime(),row.getDecidedTime(),row.getConclusionBasis(),row.getPreviousAttemptId());}
    private DefaultApprovalQuery query(EntityRef entity,String attempt){return new DefaultApprovalQuery(entity.tenantId(),entity.ownerModule(),entity.entityType(),entity.entityId(),PURPOSE,BACKEND,attempt);}
    private void opinion(ApprovalAttemptDO row,String action,String comment,Long actor){var opinion=new ApprovalOpinionDO();opinion.setTenantId(row.getTenantId());opinion.setAttemptRowId(row.getId());opinion.setAction(action);opinion.setComment(comment);opinion.setActorUserId(actor);opinions.insert(opinion);}
    private String digest(Object value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private BusinessContractException invalid(String code,String message){return new BusinessContractException(code,message);}
}
