package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SatisfactionResultFilePolicyProvider implements FileBusinessObjectPolicyProvider {
    static final String OWNER = "ACC";
    static final String TYPE = "SATISFACTION_RESULT";
    static final String PURPOSE = "SATISFACTION_RESULT_DOCUMENT";
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionResponseMapper responseMapper;
    private final SatisfactionResultMapper resultMapper;
    private final ProjectScopeApi projectScopeApi;

    @Override public String ownerContext() { return OWNER; }
    @Override public String objectType() { return TYPE; }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) { return denied(); }
    @Override public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return denied();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidateGeneratedBusinessFile(
            GeneratedBusinessFilePolicyRevalidationQuery query) {
        requireShape(query);
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(query.tenantId(), query.collectionTaskId());
        if (task == null || !query.questionnaireId().equals(task.getQuestionnaireId())
                || !"PENDING_DECISION".equals(task.getTaskStatus())
                || !query.expectedTaskVersion().equals(task.getVersion())
                || !query.actorUserId().equals(task.getAssignedToUserId()) || task.getResultId() != null) {
            throw new IllegalStateException("SATISFACTION_RESULT_FILE_TASK_CONFLICT");
        }
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectByIdForUpdate(
                query.tenantId(), query.questionnaireId());
        if (questionnaire == null || !task.getId().equals(questionnaire.getCollectionTaskId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_FILE_QUESTIONNAIRE_CONFLICT");
        }
        SatisfactionResponseDO response = responseMapper.selectByIdForUpdate(query.tenantId(), query.responseId());
        if (response == null || !questionnaire.getId().equals(response.getQuestionnaireId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_FILE_RESPONSE_CONFLICT");
        }
        if (resultMapper.selectByIdForUpdate(query.tenantId(), query.resultId()) != null) {
            throw new IllegalStateException("SATISFACTION_RESULT_FILE_RESULT_OCCUPIED");
        }
        var scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(),
                query.actorUserId(), task.getProjectId(), ProjectScopeApi.ACTION_EDIT, query.expectedScopeVersion()));
        if (scope == null || !query.expectedScopeVersion().equals(scope.treeVersion())
                || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_FILE_SCOPE_CONFLICT");
        }
        return new FileBusinessObjectPolicyFact(true, scope.treeVersion(), "IMMUTABLE", "SINGLE",
                Set.of(PURPOSE), Set.of("application/pdf"), 5_242_880L, "INTERNAL");
    }

    private void requireShape(GeneratedBusinessFilePolicyRevalidationQuery query) {
        if (query == null || query.tenantId() == null || query.actorUserId() == null || query.resultId() == null
                || query.collectionTaskId() == null || query.questionnaireId() == null || query.responseId() == null
                || query.expectedTaskVersion() == null || query.expectedTaskVersion() < 0
                || !OWNER.equals(query.ownerContext()) || !TYPE.equals(query.objectType())
                || !PURPOSE.equals(query.purposeCode()) || !FileActionCodes.UPLOAD.equals(query.requiredAction())
                || !(("satisfaction-result-" + query.resultId()).equals(query.referenceKey()))
                || query.expectedScopeVersion() == null || query.expectedScopeVersion() < 0) {
            throw new IllegalArgumentException("SATISFACTION_RESULT_FILE_POLICY_INVALID");
        }
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, "IMMUTABLE", "SINGLE",
                Set.of(), Set.of(), 0L, "INTERNAL");
    }
}
