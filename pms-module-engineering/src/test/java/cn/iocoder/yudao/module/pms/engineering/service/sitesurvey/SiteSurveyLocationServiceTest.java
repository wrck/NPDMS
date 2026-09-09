package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteSurveyLocationServiceTest {

    @Mock private SiteSurveyMapper mapper;
    @Mock private EngineeringLocationFactService locationFactService;
    private SiteSurveyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SiteSurveyServiceImpl();
        ReflectionTestUtils.setField(service, "siteSurveyMapper", mapper);
        ReflectionTestUtils.setField(service, "locationFactService", locationFactService);
        lenient().when(mapper.insert(any(SiteSurveyDO.class))).thenAnswer(invocation -> {
            SiteSurveyDO survey = invocation.getArgument(0);
            survey.setId(101L);
            survey.setVersion(0);
            return 1;
        });
        lenient().when(mapper.updateById(any(SiteSurveyDO.class))).thenReturn(1);
    }

    @Test
    void createsStructuredLocationWithoutChangingEquipmentFact() {
        EngineeringLocationFactService.LocationFact fact = new EngineeringLocationFactService.LocationFact(
                11L, 1, 21L, 2, 31L, 3, "RESOLVED", "address", "location");
        when(locationFactService.maintain(eq(1L), eq("SITE_SURVEY"), eq(101L), eq(0),
                eq("核心机房"), any())).thenReturn(fact);

        SiteSurveySaveReqVO request = request("核心机房");
        request.setLocationMaintenance(emptyMaintenance());
        assertEquals(101L, service.createSiteSurvey(request));

        verify(mapper).updateById(argThat((SiteSurveyDO value) -> value.getSiteLocationId().equals(31L)
                && "RESOLVED".equals(value.getLocationResolutionStatus())));
        verify(locationFactService, times(1)).maintain(anyLong(), anyString(), anyLong(), anyInt(), any(), any());
    }

    @Test
    void requiresFallbackWhenNoStructuredLocationIsSupplied() {
        SiteSurveySaveReqVO request = request("临时机房描述");
        service.createSiteSurvey(request);
        verify(mapper).updateById(argThat((SiteSurveyDO value) ->
                "UNRESOLVED".equals(value.getLocationResolutionStatus())));
        verifyNoInteractions(locationFactService);

        SiteSurveySaveReqVO invalid = request(" ");
        assertThrows(ServiceException.class, () -> service.createSiteSurvey(invalid));
    }

    @Test
    void rejectsUnresolvedStructuredLocation() {
        when(locationFactService.maintain(anyLong(), eq("SITE_SURVEY"), eq(101L), eq(0),
                anyString(), any())).thenReturn(new EngineeringLocationFactService.LocationFact(
                null, null, null, null, null, null, "UNRESOLVED", null, null));
        SiteSurveySaveReqVO request = request("核心机房");
        request.setLocationMaintenance(emptyMaintenance());

        assertThrows(ServiceException.class, () -> service.createSiteSurvey(request));
    }

    private SiteSurveySaveReqVO request(String location) {
        SiteSurveySaveReqVO request = new SiteSurveySaveReqVO();
        request.setProjectId(1L);
        request.setCode("SUR-1");
        request.setName("工勘");
        request.setLocation(location);
        return request;
    }

    @Test
    void stateCommandsKeepOldVersionForTheOptimisticLockerAndRejectLostUpdates() {
        SiteSurveyDO row = new SiteSurveyDO();
        row.setId(101L); row.setProjectId(1L); row.setVersion(7); row.setStatus(0);
        when(mapper.selectById(101L)).thenReturn(row);
        when(mapper.updateById(any(SiteSurveyDO.class))).thenAnswer(invocation -> {
            SiteSurveyDO changed = invocation.getArgument(0);
            assertEquals(7, changed.getVersion());
            assertEquals(1, changed.getStatus());
            return 0;
        });
        assertThrows(ServiceException.class, () -> service.confirmSiteSurvey(101L));
    }

    @Test
    void newEntityIgnoresClientLifecycleAndIdentity() {
        SiteSurveySaveReqVO request = request("旧实体专项地点");
        request.setId(500L); request.setStatus(3); request.setVersion(99);
        service.createSiteSurvey(request);
        verify(mapper).insert(argThat((SiteSurveyDO row) -> row.getStatus() == 0 && row.getVersion() == 0));
    }

    @Test
    void completedEntitiesCannotBeEditedOrDeleted() {
        SiteSurveyDO row = new SiteSurveyDO();
        row.setId(101L); row.setProjectId(1L); row.setStatus(1); row.setVersion(7);
        when(mapper.selectById(101L)).thenReturn(row);
        SiteSurveySaveReqVO request = request("不可覆盖");
        request.setId(101L); request.setVersion(7);
        assertThrows(ServiceException.class, () -> service.updateSiteSurvey(request));
        assertThrows(ServiceException.class, () -> service.deleteSiteSurvey(101L));
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
        verify(mapper, never()).deleteDraft(any());
    }

    @Test
    void staleEditsFailBeforeAnyLocationWrite() {
        SiteSurveyDO row = new SiteSurveyDO();
        row.setId(101L); row.setProjectId(1L); row.setCode("SUR-1"); row.setStatus(0); row.setVersion(7);
        when(mapper.selectById(101L)).thenReturn(row);
        SiteSurveySaveReqVO request = request("不可覆盖"); request.setId(101L); request.setVersion(6);
        assertThrows(ServiceException.class, () -> service.updateSiteSurvey(request));
        verifyNoInteractions(locationFactService);
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
    }

    @Test
    void associatesOneRequestWithoutChangingSurveyStateAndRejectsWrongProjectOrDuplicates() {
        SiteSurveyDO row = new SiteSurveyDO();
        row.setId(101L); row.setProjectId(1L); row.setStatus(0); row.setVersion(4); row.setOutsourceRequired(true);
        when(mapper.selectById(101L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.associateOutsourceRequest(101L, 2L, 500L));
        service.associateOutsourceRequest(101L, 1L, 500L);
        assertEquals(500L, row.getOutsourceRequestId());
        assertEquals(0, row.getStatus());
        verify(mapper).updateById(argThat((SiteSurveyDO value) -> value.getVersion() == 4 && value.getOutsourceRequestId() == 500L));
        assertThrows(ServiceException.class, () -> service.associateOutsourceRequest(101L, 1L, 501L));
        row.setOutsourceRequestId(null); row.setOutsourceRequired(false);
        assertThrows(ServiceException.class, () -> service.associateOutsourceRequest(101L, 1L, 502L));
    }

    @Test
    void deletingCurrentOutsourceDraftReleasesOnlyTheEditableCurrentLink() {
        SiteSurveyDO row = new SiteSurveyDO();
        row.setId(101L); row.setStatus(0); row.setVersion(4); row.setOutsourceRequired(true); row.setOutsourceRequestId(500L);
        when(mapper.selectById(101L)).thenReturn(row);
        service.releaseDeletedOutsourceRequest(101L, 499L);
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
        service.releaseDeletedOutsourceRequest(101L, 500L);
        assertNull(row.getOutsourceRequestId());
        assertTrue(row.getOutsourceRequired());
        row.setOutsourceRequestId(501L); row.setStatus(1);
        assertThrows(ServiceException.class, () -> service.releaseDeletedOutsourceRequest(101L, 501L));
        assertEquals(501L, row.getOutsourceRequestId());
    }

    private LocationMaintenanceCommand emptyMaintenance() {
        return new LocationMaintenanceCommand(null, null, null, null, null, null, null, null);
    }
}
