package cn.iocoder.yudao.module.pms.engineering.service.requirement;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;import java.util.Set;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.junit.jupiter.api.Assertions.*;
class RequirementAnalysisDeliveryAccessTest {
 RequirementAnalysisMapper mapper=mock(RequirementAnalysisMapper.class);RequirementAnalysisAccess access=mock(RequirementAnalysisAccess.class);ProjectScopeApi scope=mock(ProjectScopeApi.class);RequirementAnalysisDeliveryAccess service=new RequirementAnalysisDeliveryAccess(mapper,access,scope);
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);var row=new RequirementAnalysisRevisionDO();row.setId(11L);row.setTenantId(7L);row.setEntityId(9L);row.setProjectId(3L);when(mapper.selectLatestForEntity(any())).thenReturn(row);when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(3L,5L,Set.of(3L),Set.of()));}
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void draftRevisionOwnerReadsWithoutCurrentRootRow(){assertEquals(5L,service.requireDeliveryAccess(7L,17L,"requirementAnalysis","9",null,false,false,null));verify(access).read(eq(11L),any());}
 @Test void scopeVersionChangeFails(){assertThrows(Exception.class,()->service.requireDeliveryAccess(7L,17L,"requirementAnalysis","9",null,false,false,4L));}
 @Test void nativeRevisionPermissionRevocationFails(){when(access.read(eq(11L),any())).thenThrow(new IllegalStateException("denied"));assertThrows(Exception.class,()->service.requireDeliveryAccess(7L,17L,"requirementAnalysis","9",null,false,false,null));}
 @Test void genericWriteAndUploadDenied(){assertThrows(Exception.class,()->service.requireDeliveryAccess(7L,17L,"requirementAnalysis","9",null,true,false,null));assertThrows(Exception.class,()->service.validateUpload(7L,17L,"requirementAnalysis","9",null,"UPLOAD",false,null));verifyNoInteractions(mapper);}
 @Test void siblingSolOwnersAreNotIntercepted(){assertFalse(service.supportsEntityType("solution"));assertFalse(service.supportsEntityType("briefing"));assertFalse(service.supportsEntityType(null));}
 @Test void projectOutsideReadScopeFails(){when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(3L,5L,Set.of(),Set.of()));assertThrows(Exception.class,()->service.requireDeliveryAccess(7L,17L,"requirementAnalysis","9",null,false,false,null));}
}
