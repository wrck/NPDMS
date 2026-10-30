package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationControlScope;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SiteSurveyOperationEntryPolicyTest {
    @Test void onlyTheSixAuditedCommandsUseProjectEntryOnlyControl() {
        var policy = new SiteSurveyOperationProvider().controlScopes();
        assertEquals(Set.of("SOL.SITE_SURVEY.CREATE","SOL.SITE_SURVEY.UPDATE","SOL.SITE_SURVEY.DELETE",
                "SOL.SITE_SURVEY.CONFIRM","SOL.SITE_SURVEY.REJECT","SOL.SITE_SURVEY.ARCHIVE"),policy.keySet());
        assertTrue(policy.values().stream().allMatch(value -> value == ProjectOperationControlScope.PROJECT_ENTRY_ONLY));
        assertThrows(UnsupportedOperationException.class,policy::clear);
    }
}
