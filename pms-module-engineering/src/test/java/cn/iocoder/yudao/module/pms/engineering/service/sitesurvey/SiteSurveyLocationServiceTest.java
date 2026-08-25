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
        when(mapper.insert(any(SiteSurveyDO.class))).thenAnswer(invocation -> {
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

    private LocationMaintenanceCommand emptyMaintenance() {
        return new LocationMaintenanceCommand(null, null, null, null, null, null, null, null);
    }
}
