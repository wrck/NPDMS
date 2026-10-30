package cn.iocoder.yudao.module.pms.engineering.service.businessview;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateBusinessPageProviderTest {
    @Test
    void surveyRouteUsesTheExistingComponentWithoutConsultingPermissionsOrExecutingCommands() {
        var permissions = mock(PermissionApi.class);
        var provider = new SiteSurveyBusinessViewProvider(permissions);
        assertEquals(Set.of("/pms/delivery-business/site-survey"), provider.pagePaths());
        assertEquals("SOL_SITE_SURVEY", provider.component().componentKey());
        assertEquals("SITE_SURVEY", provider.component().entityType());
        assertEquals("SOL", provider.component().ownerContext());
        verifyNoInteractions(permissions);
    }
    @Test
    void requirementRouteRetainsTheExistingVersionPanelAndOwnerIdentity() {
        var permissions = mock(PermissionApi.class);
        var provider = new RequirementAnalysisBusinessViewProvider(permissions);
        assertEquals(Set.of("/pms/delivery-business/requirement-analysis"), provider.pagePaths());
        assertEquals("PROJ_REQUIREMENT_ANALYSIS", provider.component().componentKey());
        assertEquals("REQUIREMENT_ANALYSIS", provider.component().entityType());
        assertEquals("SOL", provider.component().ownerContext());
        verifyNoInteractions(permissions);
    }
}
