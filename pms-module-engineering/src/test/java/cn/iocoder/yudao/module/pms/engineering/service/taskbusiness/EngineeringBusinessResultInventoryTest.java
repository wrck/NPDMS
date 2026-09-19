package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisBusinessResultSource;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyBusinessResultSource;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineeringBusinessResultInventoryTest {
    private final RequirementAnalysisMapper mapper=mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisBusinessResultSource source=new RequirementAnalysisBusinessResultSource(mapper);
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void after(){TenantContextHolder.clear();}
    @Test void pageUsesTheOriginalFactReaderAndDoesNotReadTheLookaheadResult(){
        when(mapper.selectResultInventory(any())).thenReturn(List.of("40","41","42"));
        when(mapper.selectRevision(any())).thenAnswer(call->{var row=new RequirementAnalysisRevisionDO();
            row.setId(call.getArgument(0,RequirementRevisionQuery.class).revisionId());row.setTenantId(1L);row.setProjectId(3L);row.setEntityId(100L);
            row.setRevisionNo(1);row.setVersion(2);row.setRevisionState("FROZEN");row.setStatusCode("COMPLETED");row.setFrozenAt(LocalDateTime.now());return row;});
        var page=source.inventory(new InventoryQuery(1L,3L,RequirementAnalysisBusinessResultSource.TYPE,true,List.of("100"),"10",2));
        assertEquals("41",page.nextCursor());assertFalse(page.complete());assertEquals(2,page.observations().size());
        verify(mapper).selectResultInventory(new RequirementResultInventoryQuery(1L,3L,List.of(100L),10L,3,true));
        verify(mapper,never()).selectRevision(new RequirementRevisionQuery(1L,42L));
        verify(mapper,never()).selectEffective(any());
    }
    @Test void unavailableExactRowsRemainExplicitRatherThanBecomingSuccessfulInventory(){
        when(mapper.selectResultInventory(any())).thenReturn(List.of("40"));
        var page=source.inventory(new InventoryQuery(1L,3L,RequirementAnalysisBusinessResultSource.TYPE,true,null,null,2));
        assertNull(page.observations().getFirst().result());assertTrue(page.complete());
    }
    @Test void invalidIdsAndUnsupportedSurveyHistoryStopBeforeAnyMapperCall(){
        assertThrows(IllegalArgumentException.class,()->source.inventory(new InventoryQuery(1L,3L,RequirementAnalysisBusinessResultSource.TYPE,false,List.of("100.5"),null,2)));
        assertThrows(IllegalArgumentException.class,()->source.inventory(new InventoryQuery(1L,3L,RequirementAnalysisBusinessResultSource.TYPE,false,null,"2.5",2)));
        var surveys=mock(SiteSurveyEntityMapper.class);var survey=new SiteSurveyBusinessResultSource(surveys);
        assertThrows(IllegalArgumentException.class,()->survey.inventory(new InventoryQuery(1L,3L,SiteSurveyBusinessResultSource.TYPE,true,null,null,2)));
        verifyNoInteractions(mapper,surveys);
    }
}
