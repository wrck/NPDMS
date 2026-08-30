package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionGrantDigestQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SatisfactionResponseReservationService {
    static final String SCOPE = "ACC_SATISFACTION_RESPONSE_RESERVATION";
    private final SatisfactionAccessGrantMapper grantMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation reserveFromToken(Long tenantId, String token, String requestId) {
        SatisfactionAccessGrantDO grant = grantMapper.selectByDigestForUpdate(
                new SatisfactionGrantDigestQuery(tenantId, digest(token)));
        if (grant == null) throw new IllegalStateException("SATISFACTION_GRANT_UNAVAILABLE");
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectByIdForUpdate(
                tenantId, grant.getQuestionnaireId());
        SatisfactionCollectionTaskDO task = questionnaire == null ? null : taskMapper.selectByIdForUpdate(
                tenantId, questionnaire.getCollectionTaskId());
        requireOwnerChain(tenantId, grant, questionnaire, task);
        return executeReservation(tenantId, grant, questionnaire, requestId, true, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation requireReserved(Long tenantId, SatisfactionAccessGrantDO grant,
                                       SatisfactionQuestionnaireDO questionnaire,
                                       SatisfactionCollectionTaskDO task, String requestId,
                                       Long expectedResponseId) {
        requireOwnerChain(tenantId, grant, questionnaire, task);
        return executeReservation(tenantId, grant, questionnaire, requestId, false, expectedResponseId);
    }

    private Reservation executeReservation(Long tenantId, SatisfactionAccessGrantDO grant,
                                           SatisfactionQuestionnaireDO questionnaire,
                                           String requestId, boolean allowNew, Long expectedResponseId) {
        Long issuer = positiveLong(grant.getCreator());
        if (requestId == null || requestId.isBlank() || issuer == null) {
            throw new IllegalArgumentException("SATISFACTION_RESPONSE_RESERVATION_INVALID");
        }
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("tenantId", tenantId);
        identity.put("grantId", grant.getId());
        identity.put("grantVersion", grant.getGrantVersion());
        identity.put("questionnaireId", questionnaire.getId());
        identity.put("requestId", requestId);
        String requestDigest = digest(JsonUtils.toJsonString(identity));
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(tenantId, SCOPE, issuer, requestId),
                requestDigest, Reservation.class,
                () -> new Reservation(IdWorker.getId(), grant.getId(), grant.getGrantVersion(),
                        questionnaire.getId(), issuer, false),
                reservation -> new PlatformCommandExecutionApi.SuccessFacts(
                        "SATISFACTION_RESPONSE_RESERVE", "SatisfactionResponse",
                        String.valueOf(reservation.responseId()), requestId,
                        JsonUtils.toJsonString(identity), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null
                || (!allowNew && execution.decision() == PlatformCommandExecutionApi.Decision.NEW)) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_RESERVATION_CONFLICT");
        }
        Reservation reserved = execution.response();
        if (!grant.getId().equals(reserved.grantId())
                || !grant.getGrantVersion().equals(reserved.grantVersion())
                || !questionnaire.getId().equals(reserved.questionnaireId())
                || (expectedResponseId != null && !expectedResponseId.equals(reserved.responseId()))) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_RESERVATION_CONFLICT");
        }
        return new Reservation(reserved.responseId(), reserved.grantId(), reserved.grantVersion(),
                reserved.questionnaireId(), reserved.grantIssuerUserId(),
                execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED);
    }

    private void requireOwnerChain(Long tenantId, SatisfactionAccessGrantDO grant,
                                   SatisfactionQuestionnaireDO questionnaire,
                                   SatisfactionCollectionTaskDO task) {
        LocalDateTime now = LocalDateTime.now();
        if (grant == null || questionnaire == null || task == null
                || !tenantId.equals(grant.getTenantId()) || !tenantId.equals(questionnaire.getTenantId())
                || !tenantId.equals(task.getTenantId())
                || !grant.getQuestionnaireId().equals(questionnaire.getId())
                || !questionnaire.getCollectionTaskId().equals(task.getId())
                || !"ACTIVE".equals(grant.getGrantStatus()) || now.isBefore(grant.getEffectiveFrom())
                || !now.isBefore(grant.getExpiresAt())
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus())
                || "ASSIGNED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_RESERVATION_OWNER_CONFLICT");
        }
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public record Reservation(Long responseId, Long grantId, Integer grantVersion,
                              Long questionnaireId, Long grantIssuerUserId, boolean replayed) {
    }
}
