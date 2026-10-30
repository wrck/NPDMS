package cn.iocoder.yudao.module.bpm.api.durationchange;

/** BPM owns the completed approval task and its opinion; SOL owns the duration change. */
public interface DurationChangeBpmApi {

    /**
     * Read the single completed service-manager approval after the BPM command has flushed,
     * within the original transaction. A missing or mismatched task is an error; an approval
     * without an optional opinion legitimately returns null or an empty string.
     * The caller must authorize the business change and supply its frozen associations.
     */
    String getApprovalOpinion(ApprovalOpinionQuery query);

    record ApprovalOpinionQuery(Long tenantId, String processInstanceId,
                                String processDefinitionKey, String businessKey,
                                Long approverUserId) { }
}
