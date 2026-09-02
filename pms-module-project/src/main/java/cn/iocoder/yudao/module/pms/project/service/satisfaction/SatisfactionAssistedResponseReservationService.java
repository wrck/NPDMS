package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SatisfactionAssistedResponseReservationService {
    static final String SCOPE = "ACC_SATISFACTION_ASSISTED_RESPONSE_RESERVATION";

    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation reserve(Long tenantId, Long actorUserId, Long taskId, String requestId) {
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(tenantId, taskId);
        SatisfactionQuestionnaireDO questionnaire = task == null ? null
                : questionnaireMapper.selectByIdForUpdate(tenantId, task.getQuestionnaireId());
        requireOwner(tenantId, actorUserId, taskId, task, questionnaire, false);
        return execute(tenantId, actorUserId, task, questionnaire, requestId, null, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation requireReserved(Long tenantId, Long actorUserId,
                                       SatisfactionCollectionTaskDO task,
                                       SatisfactionQuestionnaireDO questionnaire,
                                       String requestId, Long expectedResponseId) {
        requireOwner(tenantId, actorUserId, task == null ? null : task.getId(), task, questionnaire, true);
        return execute(tenantId, actorUserId, task, questionnaire, requestId, expectedResponseId, false);
    }

    private Reservation execute(Long tenantId, Long actorUserId, SatisfactionCollectionTaskDO task,
                                SatisfactionQuestionnaireDO questionnaire, String requestId,
                                Long expectedResponseId, boolean allowNew) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("SATISFACTION_ASSISTED_RESERVATION_INVALID");
        }
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("submitChannel", "ASSISTED");
        identity.put("tenantId", tenantId);
        identity.put("taskId", task.getId());
        identity.put("questionnaireId", questionnaire.getId());
        identity.put("actorUserId", actorUserId);
        identity.put("requestId", requestId);
        String requestDigest = SatisfactionResponseReservationService.digest(JsonUtils.toJsonString(identity));
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(tenantId, SCOPE, actorUserId, requestId),
                requestDigest, Reservation.class,
                () -> new Reservation(IdWorker.getId(), task.getId(), questionnaire.getId(), actorUserId, false),
                reservation -> new PlatformCommandExecutionApi.SuccessFacts(
                        "SATISFACTION_ASSISTED_RESPONSE_RESERVE", "SatisfactionResponse",
                        String.valueOf(reservation.responseId()), requestId,
                        JsonUtils.toJsonString(identity), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null
                || (!allowNew && execution.decision() == PlatformCommandExecutionApi.Decision.NEW)) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESERVATION_CONFLICT");
        }
        Reservation reserved = execution.response();
        if (!task.getId().equals(reserved.taskId())
                || !questionnaire.getId().equals(reserved.questionnaireId())
                || !actorUserId.equals(reserved.actorUserId())
                || (expectedResponseId != null && !expectedResponseId.equals(reserved.responseId()))) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESERVATION_CONFLICT");
        }
        return new Reservation(reserved.responseId(), reserved.taskId(), reserved.questionnaireId(),
                reserved.actorUserId(), execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED);
    }

    private void requireOwner(Long tenantId, Long actorUserId, Long taskId,
                              SatisfactionCollectionTaskDO task, SatisfactionQuestionnaireDO questionnaire,
                              boolean allowPendingDecision) {
        if (tenantId == null || actorUserId == null || actorUserId <= 0 || taskId == null
                || task == null || questionnaire == null || !tenantId.equals(task.getTenantId())
                || !tenantId.equals(questionnaire.getTenantId()) || !taskId.equals(task.getId())
                || !task.getQuestionnaireId().equals(questionnaire.getId())
                || !task.getId().equals(questionnaire.getCollectionTaskId())
                || !actorUserId.equals(task.getAssignedToUserId())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus())
                || (allowPendingDecision && "PENDING_DECISION".equals(task.getTaskStatus())))
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESERVATION_OWNER_CONFLICT");
        }
    }

    public record Reservation(Long responseId, Long taskId, Long questionnaireId,
                              Long actorUserId, boolean replayed) {
    }
}
