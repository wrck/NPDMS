package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyEntityWriteEntryTest {
    @ParameterizedTest
    @ValueSource(strings={"create","update","delete","confirm","reject","archive","associate","release"})
    void everyOwnerWriteRejectsUnavailableExecutionBeforeAnyMutation(String action) {
        var mapper = mock(SiteSurveyEntityMapper.class);
        var access = mock(SiteSurveyEntityWriteAccess.class);
        var location = mock(EngineeringLocationFactService.class);
        var forms = mock(SiteSurveyEntityFormService.class);
        var events = mock(EngineeringRuleReevaluationEvents.class);
        var service = new SiteSurveyEntityServiceImpl();
        ReflectionTestUtils.setField(service,"siteSurveyEntityMapper",mapper);
        ReflectionTestUtils.setField(service,"writeAccess",access);
        ReflectionTestUtils.setField(service,"locationFactService",location);
        ReflectionTestUtils.setField(service,"formService",forms);
        ReflectionTestUtils.setField(service,"ruleEvents",events);
        var row = new SiteSurveyEntityDO(); row.setId(11L); row.setProjectId(9L); row.setStatus(0); row.setVersion(1);
        when(mapper.selectById(11L)).thenReturn(row);
        boolean legacyInternal = "associate".equals(action) || "release".equals(action);
        if (legacyInternal) doThrow(new IllegalStateException("node denied")).when(access).lock(any(),any(),any());
        else doThrow(new IllegalStateException("node denied")).when(access).lock(any(),any(),any(),any(),any());
        var selection = new ProjectBusinessExecutionSelection(null,new ProjectStageExecutionContext(9L,1,21L,1,22L,1,23L,24L,1,2,true));
        var request = new SiteSurveyEntitySaveReqVO(); request.setId(11L); request.setProjectId(99L); request.setExecution(selection);
        Runnable write = switch(action) {
            case "create" -> () -> service.createSiteSurveyEntity(request);
            case "update" -> () -> service.updateSiteSurveyEntity(request);
            case "delete" -> () -> service.deleteSiteSurveyEntity(11L,selection);
            case "confirm" -> () -> service.confirmSiteSurveyEntity(11L,selection);
            case "reject" -> () -> service.rejectSiteSurveyEntity(11L,selection);
            case "archive" -> () -> service.archiveSiteSurveyEntity(11L,selection);
            case "associate" -> () -> service.associateOutsourceRequest(11L,9L,30L,selection);
            default -> () -> service.releaseDeletedOutsourceRequest(11L,30L,selection);
        };
        assertEquals("node denied", assertThrows(IllegalStateException.class,write::run).getMessage());
        String permission = "create".equals(action) ? "create" : "delete".equals(action) ? "delete" : "update";
        if (legacyInternal) verify(access).lock(9L,"pms:eng-site-survey:"+permission,selection);
        else verify(access).lock("create".equals(action) ? 99L : 9L,"pms:eng-site-survey:"+permission,selection,
                "SOL.SITE_SURVEY." + action.toUpperCase(java.util.Locale.ROOT), "create".equals(action) ? null : 11L);
        if (!"create".equals(action)) verify(mapper).selectById(11L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(location,forms,events);
        assertEquals(0,row.getStatus()); assertEquals(1,row.getVersion());
    }
}
