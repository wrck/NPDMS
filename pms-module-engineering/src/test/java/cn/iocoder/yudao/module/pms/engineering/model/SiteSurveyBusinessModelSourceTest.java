package cn.iocoder.yudao.module.pms.engineering.model;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SiteSurveyBusinessModelSourceTest {
    @Test
    void catalogReadsTheCurrentSurveyRatherThanTheLegacyImportSource() throws Exception {
        var constructor = EngineeringBusinessModelContributor.class.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(type -> mock(type)).toArray();
        var contributor = (EngineeringBusinessModelContributor) constructor.newInstance(dependencies);
        BusinessModelDeclaration survey = contributor.declarations().stream()
                .filter(d -> d.descriptor().ownerModule().equals("SOL") && d.descriptor().entityType().equals("siteSurvey"))
                .findFirst().orElseThrow();
        assertSame(SiteSurveyEntityDO.class, survey.entityClass());
        assertEquals("sol_site_survey", survey.descriptor().viewCode());
        assertEquals(cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyBusinessApplicationService.operations(),survey.descriptor().operations());
        assertEquals(cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityProvider.modelFields(),survey.descriptor().fields());
        assertTrue(survey.descriptor().capabilities().stream().anyMatch(cap->cap.enabled() && cap.type()==cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.DYNAMIC_FORM));
        assertTrue(survey.descriptor().capabilities().stream().noneMatch(cap->cap.enabled() && (cap.type()==cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.CONTENT_HISTORY || cap.type()==cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType.APPROVAL)));
        assertFalse(survey.descriptor().fields().stream().filter(field->java.util.Set.of("projectId","code","status").contains(field.code())).anyMatch(field->field.writable()));
    }
}
