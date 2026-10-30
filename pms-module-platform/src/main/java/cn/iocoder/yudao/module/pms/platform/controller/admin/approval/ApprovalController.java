package cn.iocoder.yudao.module.pms.platform.controller.admin.approval;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalEffectDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalOpinionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalEffectMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalOpinionMapper;
import cn.iocoder.yudao.module.pms.platform.service.approval.ApprovalAssociationService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 统一审批公共入口：任意实体按能力声明发起/撤回/重提/决定/生效，
 * 无逐实体审批 Adapter；引擎原生对象不在此暴露。
 */
@RestController
@RequestMapping("/api/v1/pms/approvals")
@Tag(name = "管理后台 - PMS 统一审批关联")
@Validated
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalAssociationService associationService;
    private final ApprovalOpinionMapper opinionMapper;
    private final ApprovalEffectMapper effectMapper;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityAccessPort accessPort;
    private final BusinessOperationDispatcher dispatcher;

    @PostMapping("/submissions")
    @PreAuthorize("@ss.hasPermission('pms:approval:operate')")
    public CommonResult<List<AttemptVO>> submit(@Valid @RequestBody SubmissionReqVO request) {
        Long actor = requireActor();
        List<EntityRef> subjects = new ArrayList<>();
        if (request.getSubjects() != null && !request.getSubjects().isEmpty()) {
            request.getSubjects().forEach(subject -> subjects.add(new EntityRef(
                    TenantContextHolder.getRequiredTenantId(), subject.getOwnerModule(),
                    subject.getEntityType(), subject.getEntityId())));
        } else {
            subjects.add(new EntityRef(TenantContextHolder.getRequiredTenantId(),
                    request.getOwnerModule(), request.getEntityType(), request.getEntityId()));
        }
        List<ApprovalAttemptDO> rows = associationService.submitBatch(subjects, request.getPurpose(),
                request.getAttemptId(), request.getSubmissionBasis(), request.getNeutralProcessRef(),
                actor);
        return success(rows.stream().map(ApprovalController::toView).toList());
    }

    @GetMapping("/attempts")
    @PreAuthorize("@ss.hasPermission('pms:approval:query')")
    public CommonResult<List<AttemptVO>> listAttempts(@RequestParam String ownerModule,
                                                      @RequestParam String entityType,
                                                      @RequestParam @Positive Long entityId,
                                                      @RequestParam(required = false) String purpose) {
        return success(associationService.listAttempts(ownerModule, entityType, entityId, purpose)
                .stream().map(ApprovalController::toView).toList());
    }

    @GetMapping("/attempts/{id}")
    @PreAuthorize("@ss.hasPermission('pms:approval:query')")
    public CommonResult<AttemptDetailVO> detail(@PathVariable Long id) {
        ApprovalAttemptDO row = associationService.requireAttempt(id);
        AttemptDetailVO vo = new AttemptDetailVO();
        vo.setAttempt(toView(row));
        vo.setOpinions(opinionMapper.selectByAttemptRow(id).stream()
                .map(ApprovalController::toView).toList());
        vo.setEffects(effectMapper.selectByAttemptRow(id).stream()
                .map(ApprovalController::toView).toList());
        return success(vo);
    }

    @PostMapping("/attempts/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('pms:approval:operate')")
    public CommonResult<AttemptVO> withdraw(@PathVariable Long id,
                                            @Valid @RequestBody WithdrawReqVO request) {
        return success(toView(associationService.withdraw(id, request.getReason(), requireActor())));
    }

    @PostMapping("/attempts/{id}/resubmit")
    @PreAuthorize("@ss.hasPermission('pms:approval:operate')")
    public CommonResult<AttemptVO> resubmit(@PathVariable Long id,
                                            @Valid @RequestBody ResubmitReqVO request) {
        return success(toView(associationService.resubmit(id, request.getAttemptId(),
                request.getSubmissionBasis(), requireActor())));
    }

    @PostMapping("/attempts/{id}/decide")
    @PreAuthorize("@ss.hasPermission('pms:approval:operate')")
    public CommonResult<AttemptVO> decide(@PathVariable Long id,
                                          @Valid @RequestBody DecideReqVO request) {
        return success(toView(associationService.decide(id, request.isApproved(), request.getComment(),
                requireActor())));
    }

    @PostMapping("/attempts/{id}/effects")
    @PreAuthorize("@ss.hasPermission('pms:approval:operate')")
    public CommonResult<EffectVO> executeEffect(@PathVariable Long id,
                                                @Valid @RequestBody EffectReqVO request) {
        Long actor = requireActor();
        return success(toView(associationService.executeEffect(id, request.getOperationCode(),
                request.getIdempotencyKey(), request.getInput(), request.getConcurrencyBasis(), actor)));
    }

    private Long requireActor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessContractException("CALLER_UNRESOLVED", "缺少可信操作者身份");
        }
        return userId;
    }

    public static AttemptVO toView(ApprovalAttemptDO row) {
        AttemptVO vo = new AttemptVO();
        vo.setId(row.getId());
        vo.setOwnerModule(row.getOwnerModule());
        vo.setEntityType(row.getEntityType());
        vo.setEntityId(row.getEntityId());
        vo.setPurpose(row.getPurpose());
        vo.setAttemptId(row.getAttemptId());
        vo.setSubmissionBasis(row.getSubmissionBasis());
        vo.setSubmissionConcurrencyBasis(row.getSubmissionConcurrencyBasis());
        vo.setNeutralProcessRef(row.getNeutralProcessRef());
        vo.setBackendId(row.getBackendId());
        vo.setInstanceRef(row.getInstanceRef());
        vo.setStatus(row.getStatus());
        vo.setConclusionBasis(row.getConclusionBasis());
        vo.setPreviousAttemptId(row.getPreviousAttemptId());
        vo.setBatchGroupRef(row.getBatchGroupRef());
        return vo;
    }

    private static OpinionVO toView(ApprovalOpinionDO row) {
        OpinionVO vo = new OpinionVO();
        vo.setId(row.getId());
        vo.setAttemptRowId(row.getAttemptRowId());
        vo.setAction(row.getAction());
        vo.setComment(row.getComment());
        vo.setActorUserId(row.getActorUserId());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private static EffectVO toView(ApprovalEffectDO row) {
        EffectVO vo = new EffectVO();
        vo.setId(row.getId());
        vo.setAttemptRowId(row.getAttemptRowId());
        vo.setOperationCode(row.getOperationCode());
        vo.setIdempotencyKey(row.getIdempotencyKey());
        vo.setStatus(row.getStatus());
        vo.setReceiptOutcome(row.getReceiptOutcome());
        vo.setConcurrencyBasis(row.getConcurrencyBasis());
        vo.setDetail(row.getDetail());
        return vo;
    }

    /** 发起请求：单主体直接给 owner/entity 字段；批次给 subjects。 */
    @Data
    public static class SubmissionReqVO {
        private String ownerModule;
        private String entityType;
        private Long entityId;
        private List<SubjectVO> subjects;
        @NotBlank @Size(max = 64)
        private String purpose;
        @NotBlank @Size(max = 64)
        private String attemptId;
        @NotBlank @Size(max = 255)
        private String submissionBasis;
        @NotBlank @Size(max = 128)
        private String neutralProcessRef;
    }

    @Data
    public static class SubjectVO {
        @NotBlank
        private String ownerModule;
        @NotBlank
        private String entityType;
        @Positive
        private Long entityId;
    }

    @Data
    public static class WithdrawReqVO {
        @NotBlank @Size(max = 255)
        private String reason;
    }

    @Data
    public static class ResubmitReqVO {
        @NotBlank @Size(max = 64)
        private String attemptId;
        @NotBlank @Size(max = 255)
        private String submissionBasis;
    }

    @Data
    public static class DecideReqVO {
        private boolean approved;
        @Size(max = 255)
        private String comment;
    }

    @Data
    public static class EffectReqVO {
        @NotBlank @Size(max = 64)
        private String operationCode;
        @NotBlank @Size(max = 64)
        private String idempotencyKey;
        private Long concurrencyBasis;
        private Map<String, Object> input;
    }

    @Data
    public static class AttemptVO {
        private Long id;
        private String ownerModule;
        private String entityType;
        private Long entityId;
        private String purpose;
        private String attemptId;
        private String submissionBasis;
        private Integer submissionConcurrencyBasis;
        private String neutralProcessRef;
        private String backendId;
        private String instanceRef;
        private String status;
        private String conclusionBasis;
        private Long previousAttemptId;
        private String batchGroupRef;
    }

    @Data
    public static class AttemptDetailVO {
        private AttemptVO attempt;
        private List<OpinionVO> opinions;
        private List<EffectVO> effects;
    }

    @Data
    public static class OpinionVO {
        private Long id;
        private Long attemptRowId;
        private String action;
        private String comment;
        private Long actorUserId;
        private Object createTime;
    }

    @Data
    public static class EffectVO {
        private Long id;
        private Long attemptRowId;
        private String operationCode;
        private String idempotencyKey;
        private String status;
        private String receiptOutcome;
        private Integer concurrencyBasis;
        private String detail;
    }
}
