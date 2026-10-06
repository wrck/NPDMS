package cn.iocoder.yudao.module.pms.engineering.service.requirement;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisDO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RequirementAnalysisDeliveryEvidenceProviderTest {
 final RequirementAnalysisMapper mapper=mock(RequirementAnalysisMapper.class);
 final RequirementAnalysisBusinessResultSource source=new RequirementAnalysisBusinessResultSource(mapper);
 final RequirementAnalysisDeliveryEvidenceProvider provider=new RequirementAnalysisDeliveryEvidenceProvider(mapper,source);
 RequirementAnalysisRevisionDO row;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);row=new RequirementAnalysisRevisionDO();row.setId(11L);row.setEntityId(9L);row.setTenantId(7L);row.setProjectId(20L);row.setRevisionNo(3);row.setVersion(2L);row.setRevisionState("FROZEN");row.setStatusCode("COMPLETED");row.setEffectiveMarker(1);row.setFrozenAt(LocalDateTime.of(2026,10,5,11,0));when(mapper.selectRevision(any())).thenReturn(row);when(mapper.lockRevision(any())).thenReturn(row);when(mapper.lockCurrent(any())).thenReturn(new RequirementAnalysisDO());}
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void nativeAndTemplateSourcesUseSameTrueRootAndRevision(){var id=provider.identity(7L,20L,"11",3L);var templated=source.deliveryIdentity(new BusinessResultSource.Query(7L,20L,RequirementAnalysisBusinessResultSource.TYPE,"9","11"));assertEquals("SOL",id.ownerModule());assertEquals("requirementAnalysis",id.entityType());assertEquals(9L,id.entityId());assertEquals("11",id.businessObjectId());assertEquals(3L,id.businessRevisionNo());assertEquals(id.businessObjectType(),templated.businessObjectType());assertEquals(id.businessObjectId(),templated.businessObjectId());assertEquals(id.businessRevisionNo(),templated.businessRevisionNo());}
 @Test void historicalCompositeAliasesPreserveFrozenTimeAndActualRevision(){var aliases=provider.aliases(7L,20L,"11",3L);assertEquals(2,aliases.size());assertTrue(aliases.getFirst().businessObjectId().contains("|9|11|2026-10-05T11:00:00"));assertEquals(3L,aliases.getFirst().businessRevisionNo());assertNull(aliases.getLast().businessRevisionNo());}
 @Test void draftNeverBecomesCompletedResult(){row.setRevisionState("DRAFT");assertThrows(Exception.class,()->provider.identity(7L,20L,"11",3L));}
 @Test void frozenButInactiveRevisionIsNotCurrent(){row.setEffectiveMarker(null);assertThrows(Exception.class,()->provider.identity(7L,20L,"11",3L));}
 @Test void wrongBusinessRevisionCannotSilentlyAdoptCurrent(){assertThrows(Exception.class,()->provider.identity(7L,20L,"11",2L));}
 @Test void crossProjectCannotReadCompletedRevision(){assertThrows(Exception.class,()->provider.identity(7L,21L,"11",3L));verify(mapper,never()).lockCurrent(any());}
 @Test void crossTenantCannotReadCompletedRevision(){assertThrows(Exception.class,()->provider.identity(8L,20L,"11",3L));verify(mapper,never()).lockCurrent(any());}
 @Test void nonCompletedFrozenRevisionIsUnavailable(){row.setStatusCode("IN_PROGRESS");assertThrows(Exception.class,()->provider.identity(7L,20L,"11",3L));}
}
