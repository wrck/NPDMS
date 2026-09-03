package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.projection.AcceptanceReportFileScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceReportFileBusinessObjectPolicyProviderTest {

    @Mock AcceptanceReportVersionMapper reportVersionMapper;
    @Mock ProjectScopeApi projectScopeApi;

    @Test
    void resolvesDownloadFromReportProjectAndReturnsTreeVersion() {
        when(reportVersionMapper.selectFileScope(any())).thenReturn(
                new AcceptanceReportFileScope(900L, 700L, 100L, 200L, "EFFECTIVE"));
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 8L, Set.of(100L), Set.of()));
        var provider = new AcceptanceReportFileBusinessObjectPolicyProvider(reportVersionMapper, projectScopeApi);

        var fact = provider.inspect(query("900", "ACCEPTANCE_REPORT_ATTACHMENT", FileActionCodes.DOWNLOAD));

        assertTrue(fact.allowed());
        assertEquals(8L, fact.scopeVersion());
        assertEquals("IMMUTABLE", fact.referenceMutability());
        assertEquals(Set.of("ACCEPTANCE_REPORT_ATTACHMENT"), fact.allowedCategoryCodes());
    }

    @Test
    void rejectsWriteToEffectiveReportBeforeProjectScopeLookup() {
        when(reportVersionMapper.selectFileScope(any())).thenReturn(
                new AcceptanceReportFileScope(900L, 700L, 100L, 200L, "EFFECTIVE"));
        var provider = new AcceptanceReportFileBusinessObjectPolicyProvider(reportVersionMapper, projectScopeApi);

        var fact = provider.inspect(query("900", "ACCEPTANCE_REPORT_ATTACHMENT", FileActionCodes.REPLACE));

        assertFalse(fact.allowed());
        verify(projectScopeApi, never()).resolveCurrent(any());
    }

    @Test
    void lockRejectsChangedProjectTreeVersion() {
        when(reportVersionMapper.selectFileScope(any())).thenReturn(
                new AcceptanceReportFileScope(900L, 700L, 100L, 200L, "DRAFT"));
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(100L, 9L, Set.of(100L), Set.of()));
        var provider = new AcceptanceReportFileBusinessObjectPolicyProvider(reportVersionMapper, projectScopeApi);

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                7L, 55L, "ACC", "ACCEPTANCE_REPORT_VERSION", "900",
                "ACCEPTANCE_REPORT_ATTACHMENT", "7a5d9177-2f67-4bb5-a211-b0b612e72e5f",
                FileActionCodes.REFERENCE, 8L));

        assertFalse(fact.allowed());
    }

    private FileBusinessObjectPolicyQuery query(String objectId, String purpose, String action) {
        return new FileBusinessObjectPolicyQuery(7L, 55L, "ACC", "ACCEPTANCE_REPORT_VERSION", objectId,
                purpose, "7a5d9177-2f67-4bb5-a211-b0b612e72e5f", action);
    }
}
