package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.SITE_SURVEY_LOCATION_REQUIRED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyEntityLocationPolicyTest {
    @Test void savedLocationCanConfirmThroughTheExistingStateTransition() {
        var service = new SiteSurveyEntityDomainCommands();
        var mapper = mock(SiteSurveyEntityMapper.class);
        var forms = mock(SiteSurveyEntityFormService.class);
        ReflectionTestUtils.setField(service, "siteSurveyEntityMapper", mapper);
        ReflectionTestUtils.setField(service, "formService", forms);
        ReflectionTestUtils.setField(service, "writeAccess", mock(SiteSurveyEntityWriteAccess.class));
        ReflectionTestUtils.setField(service, "ruleEvents", mock(
                cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents.class));
        var survey = new SiteSurveyEntityDO();
        survey.setId(12L); survey.setProjectId(7L); survey.setStatus(0); survey.setVersion(0L);
        survey.setLocation("已保存现场地址"); survey.setLocationResolutionStatus("UNRESOLVED");
        when(mapper.selectById(12L)).thenReturn(survey);
        when(mapper.updateById(any(SiteSurveyEntityDO.class))).thenReturn(1);
        service.confirmSiteSurveyEntity(12L, null);
        verify(forms).validate(survey, false);
        assertEquals(3, survey.getStatus());
    }
    @Test void draftMayHaveNoLocation() {
        var service = new SiteSurveyEntityDomainCommands();
        var survey = new SiteSurveyEntityDO();
        ReflectionTestUtils.invokeMethod(service, "applyLocation", survey, "", null, 0L);
        assertEquals("UNRESOLVED", survey.getLocationResolutionStatus());
    }

    @Test void confirmRejectsBlankSavedLocationBeforeFormValidationOrStatusWrite() {
        var service = new SiteSurveyEntityDomainCommands();
        var mapper = mock(SiteSurveyEntityMapper.class);
        var forms = mock(SiteSurveyEntityFormService.class);
        ReflectionTestUtils.setField(service, "siteSurveyEntityMapper", mapper);
        ReflectionTestUtils.setField(service, "formService", forms);
        ReflectionTestUtils.setField(service, "writeAccess", mock(SiteSurveyEntityWriteAccess.class));
        var survey = new SiteSurveyEntityDO();
        survey.setId(12L); survey.setProjectId(7L); survey.setStatus(0);
        survey.setLocation("  "); survey.setLocationResolutionStatus("UNRESOLVED");
        when(mapper.selectById(12L)).thenReturn(survey);
        var failure = assertThrows(ServiceException.class, () -> service.confirmSiteSurveyEntity(12L, null));
        assertEquals(SITE_SURVEY_LOCATION_REQUIRED.getCode(), failure.getCode());
        assertEquals(0, survey.getStatus());
        verifyNoInteractions(forms);
        verify(mapper, never()).updateById(any(SiteSurveyEntityDO.class));
    }
}
