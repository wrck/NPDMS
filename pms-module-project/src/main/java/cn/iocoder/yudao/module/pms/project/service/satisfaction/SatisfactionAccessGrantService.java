package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SatisfactionAccessGrantService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionAccessGrantMapper grantMapper;
    private final ProjectScopeApi projectScopeApi;

    @Transactional(rollbackFor = Exception.class)
    public CreatedGrant create(Long tenantId, Long actorUserId, Long taskId, LocalDateTime expiresAt) {
        SatisfactionCollectionTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !tenantId.equals(task.getTenantId()) || task.getQuestionnaireId() == null
                || !("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("SATISFACTION_TASK_NOT_GRANTABLE");
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_EDIT));
        if (scope == null || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw new IllegalStateException("SATISFACTION_PROJECT_SCOPE_FORBIDDEN");
        }
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectByIdForUpdate(
                tenantId, task.getQuestionnaireId());
        LocalDateTime now = LocalDateTime.now();
        if (questionnaire == null || !tenantId.equals(questionnaire.getTenantId())
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())
                || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("SATISFACTION_GRANT_INPUT_INVALID");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SatisfactionAccessGrantDO row = new SatisfactionAccessGrantDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setQuestionnaireId(questionnaire.getId());
        row.setGrantVersion(grantMapper.selectNextVersion(tenantId, questionnaire.getId()));
        row.setTokenDigest(digest(token));
        row.setEffectiveFrom(now);
        row.setExpiresAt(expiresAt);
        row.setGrantStatus("ACTIVE");
        row.setVersion(0);
        row.setCreator(String.valueOf(actorUserId));
        row.setUpdater(String.valueOf(actorUserId));
        if (grantMapper.insert(row) != 1) throw new IllegalStateException("SATISFACTION_GRANT_CREATE_FAILED");
        return new CreatedGrant(row.getId(), row.getGrantVersion(), token, expiresAt);
    }

    @Transactional(readOnly = true)
    public PublicQuestionnaire inspect(Long tenantId, String token) {
        SatisfactionAccessGrantDO grant = grantMapper.selectByDigest(new SatisfactionGrantDigestQuery(
                tenantId, digest(token)));
        LocalDateTime now = LocalDateTime.now();
        if (grant == null || !"ACTIVE".equals(grant.getGrantStatus())
                || now.isBefore(grant.getEffectiveFrom()) || !now.isBefore(grant.getExpiresAt())) {
            throw new IllegalStateException("SATISFACTION_GRANT_UNAVAILABLE");
        }
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectById(grant.getQuestionnaireId());
        if (questionnaire == null || !tenantId.equals(questionnaire.getTenantId())
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())) {
            throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_UNAVAILABLE");
        }
        return new PublicQuestionnaire(questionnaire.getId(), questionnaire.getVersion(),
                publicQuestionnaireJson(questionnaire.getFrozenQuestionJson()), grant.getExpiresAt());
    }

    private String publicQuestionnaireJson(String frozenJson) {
        Map<?, ?> root = JsonUtils.parseObjectQuietly(frozenJson, Map.class);
        if (root == null || !(root.get("questions") instanceof List<?> questions)) {
            throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_CONFIG_INVALID");
        }
        List<Map<String, Object>> publicQuestions = questions.stream().map(item -> {
            if (!(item instanceof Map<?, ?> question)) {
                throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_CONFIG_INVALID");
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String field : List.of("code", "title", "type", "required", "minSelections",
                    "maxSelections", "minLength", "maxLength")) {
                if (question.containsKey(field)) projected.put(field, question.get(field));
            }
            if (question.get("options") instanceof List<?> options) {
                projected.put("options", options.stream().map(option -> {
                    if (!(option instanceof Map<?, ?> value)) {
                        throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_CONFIG_INVALID");
                    }
                    Map<String, Object> projectedOption = new LinkedHashMap<>();
                    projectedOption.put("code", value.get("code"));
                    projectedOption.put("label", value.get("label"));
                    return projectedOption;
                }).toList());
            }
            return projected;
        }).toList();
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("schemaVersion", root.get("schemaVersion"));
        projected.put("questions", publicQuestions);
        return JsonUtils.toJsonString(projected);
    }

    private String digest(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token required");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record CreatedGrant(Long grantId, Integer grantVersion, String token, LocalDateTime expiresAt) {}
    public record PublicQuestionnaire(Long questionnaireId, Integer version, String frozenQuestions,
                                      LocalDateTime expiresAt) {}
}
