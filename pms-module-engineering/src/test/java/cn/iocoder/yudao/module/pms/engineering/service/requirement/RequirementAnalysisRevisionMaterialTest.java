package cn.iocoder.yudao.module.pms.engineering.service.requirement;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import org.junit.jupiter.api.*;import org.springframework.test.util.ReflectionTestUtils;import java.util.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.junit.jupiter.api.Assertions.*;
class RequirementAnalysisRevisionMaterialTest {
 FileArtifactApi fileApi=mock(FileArtifactApi.class);RequirementAnalysisAccess access=mock(RequirementAnalysisAccess.class);EntityFormApi forms=mock(EntityFormApi.class);PlatformDeliveryMaterialApi materials=mock(PlatformDeliveryMaterialApi.class);RequirementAnalysisRevisionFiles service=new RequirementAnalysisRevisionFiles(fileApi,access,forms);
 EntityActor actor=new EntityActor(7L,17L,"save");RevisionRef ref=new RevisionRef(new EntityRef(7L,"SOL","REQUIREMENT_ANALYSIS",9L),11L);
 FileArtifactVersionFact file=new FileArtifactVersionFact(31L,1,"slot","DYNAMIC_FORM_ATTACHMENT","a.txt",3L,"text/plain","sha","AVAILABLE","ACTIVE",null,11L);
 FileReferenceSetFact set=new FileReferenceSetFact(new FileReferenceSetKey("SOL","REQUIREMENT_ANALYSIS_REVISION","11","FORM_FIELD_ATTACHMENT/PROJECT_BACKGROUND__ATTACHMENTS"),11L,List.of(file));
 @BeforeEach void setup(){ReflectionTestUtils.setField(service,"materials",materials);var row=new RequirementAnalysisRevisionDO();row.setTenantId(7L);row.setId(11L);row.setEntityId(9L);when(access.read(11L,actor)).thenReturn(row);}
 @Test void explicitSaveRegistersOptionalFileWithoutTemplateOrFormBinding(){when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(set));when(fileApi.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));service.registerSaved(ref,actor);verify(materials).registerNativeSourceFile(file);verify(forms).layout(EntityDataRef.revision(ref),actor);}
 @Test void saveWithNoFilesDoesNotInventMaterialOrRequirement(){when(fileApi.inspectReferenceSets(any())).thenReturn(List.of());service.registerSaved(ref,actor);verifyNoInteractions(materials);verify(fileApi,never()).lockAndRevalidateReferenceSets(any());}
 @Test void changedFileReferenceRejectedBeforeMaterialRegistration(){when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(set));when(fileApi.lockAndRevalidateReferenceSets(any())).thenThrow(new IllegalStateException("reference version changed"));assertThrows(Exception.class,()->service.registerSaved(ref,actor));verifyNoInteractions(materials);}
 @Test void ownerReadRevocationDeniesBeforeFileAndMaterial(){when(access.read(11L,actor)).thenThrow(new IllegalStateException("read denied"));assertThrows(Exception.class,()->service.registerSaved(ref,actor));verifyNoInteractions(fileApi,materials);}
 @Test void explicitRegistrationFailurePropagatesToBusinessSaveTransaction(){when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(set));when(fileApi.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));when(materials.registerNativeSourceFile(file)).thenThrow(new IllegalStateException("material failed"));assertThrows(Exception.class,()->service.registerSaved(ref,actor));}
}
