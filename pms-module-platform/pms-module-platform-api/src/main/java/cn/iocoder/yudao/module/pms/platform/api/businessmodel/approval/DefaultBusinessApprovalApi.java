package cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Shared workflow association for inherited entities. Approval never executes a domain state change. */
public interface DefaultBusinessApprovalApi {
    record Subject(EntityRef entity, Long projectId, Long version, Map<String,Object> values) { }
    record Start(@NotBlank @Size(max=128) String definitionId, @NotNull @PositiveOrZero Long version,
                 @NotBlank @Size(max=64) String idempotencyKey, Map<String,Object> variables,
                 Map<String,List<Long>> selectedApprovers) { }
    record PreviewRequest(@NotBlank @Size(max=128) String definitionId, Map<String,Object> variables) { }
    record Cancel(@NotBlank @Size(max=200) String reason) { }
    record Attempt(Long id, String definitionId, String instanceId, String status, Long submittedVersion,
                   boolean currentContent, String creator, LocalDateTime submittedAt, LocalDateTime decidedAt,
                   String conclusion, Long previousAttemptId) { }
    record Detail(Attempt attempt, DefaultBusinessApprovalProcessApi.Detail process) { }

    List<DefaultBusinessApprovalProcessApi.Definition> definitions(EntityRef entity);
    DefaultBusinessApprovalProcessApi.Preview preview(EntityRef entity, PreviewRequest request);
    List<Attempt> list(EntityRef entity);
    Detail detail(EntityRef entity, Long attemptId);
    Attempt start(EntityRef entity, Start request);
    Attempt cancel(EntityRef entity, Long attemptId, Cancel request);
    /** Internal engine callback only; absent rows during synchronous creation are reconciled by start(). */
    void record(DefaultBusinessApprovalProcessApi.Result result);
}
