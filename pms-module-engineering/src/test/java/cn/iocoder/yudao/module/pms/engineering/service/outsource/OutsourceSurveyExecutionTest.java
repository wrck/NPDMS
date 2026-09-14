package cn.iocoder.yudao.module.pms.engineering.service.outsource;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.SiteSurveyService;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutsourceSurveyExecutionTest {
    final OutsourceRequestMapper mapper = mock(OutsourceRequestMapper.class);
    final SiteSurveyService surveys = mock(SiteSurveyService.class);
    final OutsourceRequestServiceImpl service = new OutsourceRequestServiceImpl();
    final ProjectBusinessExecutionSelection selection = new ProjectBusinessExecutionSelection(null,
            new ProjectStageExecutionContext(9L,1,11L,1,12L,1,21L,31L,1,2,true));
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"outsourceRequestMapper",mapper);
        ReflectionTestUtils.setField(service,"siteSurveyService",surveys);
        when(mapper.insert(any(OutsourceRequestDO.class))).thenAnswer(call -> { ((OutsourceRequestDO)call.getArgument(0)).setId(41L); return 1; });
    }
    OutsourceRequestSaveReqVO request() {
        var request = new OutsourceRequestSaveReqVO(); request.setProjectId(9L); request.setTriggerSource("SITE_SURVEY");
        request.setTriggerRefId(51L); request.setSiteSurveyExecution(selection); return request;
    }
    @Test void creationPassesExactExecutionToSurveyAssociationAndDoesNotSubmitApproval() {
        assertEquals(41L,service.createOutsourceRequest(request()));
        verify(surveys).associateOutsourceRequest(51L,9L,41L,selection);
        verify(mapper).insert(argThat((OutsourceRequestDO row) -> row.getStatus() == 0 && row.getBpmProcessInstanceId() == null));
        verifyNoMoreInteractions(mapper,surveys);
    }
    @Test void invalidExecutionFailurePropagatesToTheExistingCreationTransaction() {
        doThrow(new IllegalStateException("stale stage")).when(surveys).associateOutsourceRequest(51L,9L,41L,selection);
        assertThrows(IllegalStateException.class, () -> service.createOutsourceRequest(request()));
    }
    @Test void deletionRevalidatesTheSameExecutionBeforeRemovingTheRequest() {
        var row = new OutsourceRequestDO(); row.setId(41L); row.setTriggerSource("SITE_SURVEY"); row.setTriggerRefId(51L); row.setStatus(0);
        when(mapper.selectById(41L)).thenReturn(row);
        service.deleteOutsourceRequest(41L,selection);
        var order = inOrder(mapper,surveys);
        order.verify(mapper).selectById(41L);
        order.verify(surveys).releaseDeletedOutsourceRequest(51L,41L,selection);
        order.verify(mapper).deleteById(41L);
    }
    @Test void staleExecutionPreventsDeletionAndPreservesTheSourceRelationship() {
        var row = new OutsourceRequestDO(); row.setId(41L); row.setTriggerSource("SITE_SURVEY"); row.setTriggerRefId(51L); row.setStatus(0);
        when(mapper.selectById(41L)).thenReturn(row);
        doThrow(new IllegalStateException("stale stage")).when(surveys).releaseDeletedOutsourceRequest(51L,41L,selection);
        assertThrows(IllegalStateException.class, () -> service.deleteOutsourceRequest(41L,selection));
        verify(mapper,never()).deleteById(anyLong());
    }
}
