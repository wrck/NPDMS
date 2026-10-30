package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.*;
import org.junit.jupiter.api.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class AcceptanceReportDraftDeliveryTest {
 final AcceptanceActivityMapper activities=mock(AcceptanceActivityMapper.class);final AcceptanceReportVersionMapper reports=mock(AcceptanceReportVersionMapper.class);final AcceptanceReportAttachmentMapper attachments=mock(AcceptanceReportAttachmentMapper.class);
 final FileArtifactApi files=mock(FileArtifactApi.class);final PlatformDeliveryMaterialApi materials=mock(PlatformDeliveryMaterialApi.class);final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
 final AcceptanceReportCommandService service=new AcceptanceReportCommandService(materials,activities,reports,attachments,files,mock(PlatformCommandExecutionApi.class),scopes,mock(ProjectAcceptanceContextApi.class));
 final AcceptanceReportCommands.Actor actor=new AcceptanceReportCommands.Actor(7L,19L,"draft");
 final AcceptanceReportCommands.UpdateDraftCommand command=new AcceptanceReportCommands.UpdateDraftCommand(100L,300L,0L,1,new AcceptanceReportCommands.DraftContent(null,null,"optional draft",null));
 AcceptanceReportVersionDO draft;AcceptanceActivityDO activity;
 final FileArtifactVersionFact file=new FileArtifactVersionFact(11L,1,"reference","ACCEPTANCE_REPORT_ATTACHMENT","report.pdf",10L,"application/pdf","a".repeat(64),"AVAILABLE","ACTIVE",new FileFactVersion(1,1,1),3L);
 @BeforeEach void before(){TenantContextHolder.setTenantId(7L);activity=new AcceptanceActivityDO();activity.setId(100L);activity.setTenantId(7L);activity.setProjectId(20L);activity.setVersion(0L);activity.setActivityStatus("PENDING");activity.setOriginKind("TEMPLATE");draft=new AcceptanceReportVersionDO();draft.setId(300L);draft.setTenantId(7L);draft.setAcceptanceId(100L);draft.setReportVersionNo(1);draft.setReportStatus("DRAFT");when(activities.selectById(100L)).thenReturn(activity);when(activities.selectByIdForUpdate(any())).thenReturn(activity);when(reports.selectByIdForUpdate(any())).thenReturn(draft);when(reports.updateById(draft)).thenReturn(1);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));}
 @AfterEach void clear(){TenantContextHolder.clear();}
 FileReferenceSetFact fileSet(){return new FileReferenceSetFact(new FileReferenceSetKey("ACC","ACCEPTANCE_REPORT_VERSION","300","ACCEPTANCE_REPORT_ATTACHMENT"),3L,List.of(file));}
 @Test void explicitDraftSaveWithoutAttachmentsIsValidAndNeverPublishes(){when(files.inspectReferenceSets(any())).thenReturn(List.of());var result=service.updateDraft(command,actor);assertEquals("DRAFT",result.reportStatus());assertNull(result.changeType());assertNull(activity.getCurrentReportVersionId());verifyNoInteractions(materials,attachments);}
 @Test void optionalDraftFileIsRegisteredAfterLockedFileFactsWithoutPublishing(){var set=fileSet();when(files.inspectReferenceSets(any())).thenReturn(List.of(set));when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));var result=service.updateDraft(command,actor);verify(materials).registerNativeSourceFile(file);assertEquals("DRAFT",result.reportStatus());assertNull(draft.getPublisherUserId());assertNull(draft.getEffectiveFrom());verify(activities,never()).updateById(any(AcceptanceActivityDO.class));verifyNoInteractions(attachments);}
 @Test void changedFileSetAbortsWithoutRegistering(){when(files.inspectReferenceSets(any())).thenReturn(List.of(fileSet()));when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of());assertThrows(Exception.class,()->service.updateDraft(command,actor));verifyNoInteractions(materials);}
 @Test void registrationFailurePropagatesToTransactionalDraftBoundary(){var set=fileSet();when(files.inspectReferenceSets(any())).thenReturn(List.of(set));when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));when(materials.registerNativeSourceFile(file)).thenThrow(new IllegalStateException("material failure"));assertThrows(Exception.class,()->service.updateDraft(command,actor));assertEquals("DRAFT",draft.getReportStatus());assertNull(activity.getCurrentReportVersionId());}
 @Test void staleActivityFailsBeforeFileAndMaterial(){activity.setVersion(1L);assertThrows(Exception.class,()->service.updateDraft(command,actor));verifyNoInteractions(files,materials);}
 @Test void revokedProjectScopeFailsBeforeDraftUpdateAndMaterial(){when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(),Set.of(20L)));assertThrows(Exception.class,()->service.updateDraft(command,actor));verify(reports,never()).updateById(any(AcceptanceReportVersionDO.class));verifyNoInteractions(files,materials);}
 @Test void effectiveReportCannotBeSavedAsDraft(){draft.setReportStatus("EFFECTIVE");assertThrows(Exception.class,()->service.updateDraft(command,actor));verifyNoInteractions(files,materials);}
}
