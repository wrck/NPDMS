package cn.iocoder.yudao.module.pms.acceptance.service.archivedocument;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class ArchiveDocumentNativeDeliveryTest {
 @Mock ArchiveDocumentMapper mapper; @Mock PlatformDeliveryMaterialApi materials; @Mock cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess nativeAccess;
 @InjectMocks ArchiveDocumentServiceImpl service;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);org.springframework.test.util.ReflectionTestUtils.setField(service,"archiveDocumentMapper",mapper);org.springframework.test.util.ReflectionTestUtils.setField(service,"deliveryMaterials",materials);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 ArchiveDocumentDO owner(int status){var row=new ArchiveDocumentDO();row.setId(9L);row.setTenantId(7L);row.setProjectId(20L);row.setName("Archive");row.setStatus(status);row.setVersion(4L);return row;}
 ArchiveDocumentSaveReqVO request(){var req=new ArchiveDocumentSaveReqVO();req.setId(9L);req.setProjectId(20L);req.setName("Archive");req.setVersion(4);req.setDocumentUrl("/history/a.pdf");req.setVersionNo("v2.3");return req;}
 void locked(ArchiveDocumentDO row){when(mapper.selectDeliveryOwnerForUpdate(new ArchiveDocumentDeliveryLockQuery(7L,9L))).thenReturn(row);}
 @Test void staleDraftWriteIsReportedAndNeverPublishesResult(){locked(owner(0));assertThrows(Exception.class,()->service.updateArchiveDocument(request()));verifyNoInteractions(materials);}
 @Test void missingOptimisticVersionDoesNotExecuteUpdate(){locked(owner(0));var req=request();req.setVersion(null);assertThrows(Exception.class,()->service.updateArchiveDocument(req));verify(mapper,never()).updateById(any(ArchiveDocumentDO.class));}
 @Test void archivedMetadataCannotBeOverwritten(){locked(owner(2));assertThrows(Exception.class,()->service.updateArchiveDocument(request()));verify(mapper,never()).updateById(any(ArchiveDocumentDO.class));}
 @Test void draftRoundTripKeepsHistoryAddressAndBusinessVersion(){locked(owner(0));when(mapper.updateById(any(ArchiveDocumentDO.class))).thenReturn(1);service.updateArchiveDocument(request());var cap=ArgumentCaptor.forClass(ArchiveDocumentDO.class);verify(mapper).updateById(cap.capture());assertEquals("/history/a.pdf",cap.getValue().getDocumentUrl());assertEquals("v2.3",cap.getValue().getVersionNo());assertEquals(4L,cap.getValue().getVersion());}
 @Test void pointerFromOtherNativeRootIsRejected(){locked(owner(0));var req=request();req.setDocumentUrl("/api/v1/pms/archive-documents/99/files/12");assertThrows(Exception.class,()->service.updateArchiveDocument(req));verify(mapper,never()).updateById(any(ArchiveDocumentDO.class));verifyNoInteractions(materials);}
 @Test void unavailableUnifiedPointerCannotBeSaved(){locked(owner(0));var req=request();req.setDocumentUrl("/api/v1/pms/archive-documents/9/files/12");when(materials.listByEntityAndType("ACC","archiveDocument",9L,"ARCHIVE_DOCUMENT")).thenReturn(java.util.List.of());assertThrows(Exception.class,()->service.updateArchiveDocument(req));verify(mapper,never()).updateById(any(ArchiveDocumentDO.class));}
 @Test void nativeArchivePublishesOnlyAfterRealStateTransition(){locked(owner(1));when(mapper.updateById(any(ArchiveDocumentDO.class))).thenReturn(1);service.archiveArchiveDocument(9L);var order=inOrder(mapper,materials);order.verify(mapper).selectDeliveryOwnerForUpdate(any());order.verify(mapper).updateById(any(ArchiveDocumentDO.class));order.verify(materials).registerBusinessResultMaterial("ACC","archiveDocument",9L,"ARCHIVE_DOCUMENT","archiveDocument","9",null,"Archive",20L);}
 @Test void staleArchiveDoesNotPublishResult(){locked(owner(1));assertThrows(Exception.class,()->service.archiveArchiveDocument(9L));verifyNoInteractions(materials);}

 @Test void createCannotSkipNativeArchiveStateMachine(){var req=request();req.setId(null);req.setStatus(2);assertThrows(Exception.class,()->service.createArchiveDocument(req));verify(mapper,never()).insert(any(ArchiveDocumentDO.class));}
 @Test void createCannotReuseOtherRootPointer(){var req=request();req.setId(null);req.setDocumentUrl("/api/v1/pms/archive-documents/9/files/12");assertThrows(Exception.class,()->service.createArchiveDocument(req));verify(mapper,never()).insert(any(ArchiveDocumentDO.class));}

 @Test void registeredSourceCannotBeReparentedIntoAnotherProject(){locked(owner(0));var req=request();req.setProjectId(99L);when(materials.listByEntity("ACC","archiveDocument",9L)).thenReturn(java.util.Collections.singletonList(null));assertThrows(Exception.class,()->service.updateArchiveDocument(req));verify(mapper,never()).updateById(any(ArchiveDocumentDO.class));}
}
