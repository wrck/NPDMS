package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.*;import org.springframework.test.util.ReflectionTestUtils;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.junit.jupiter.api.Assertions.*;
class DeliverableChecklistNativeDeliveryTest {
 DeliverableChecklistMapper mapper=mock(DeliverableChecklistMapper.class);DeliverableChecklistDeliveryAccess access=mock(DeliverableChecklistDeliveryAccess.class);PlatformDeliveryMaterialApi materials=mock(PlatformDeliveryMaterialApi.class);DeliverableChecklistServiceImpl service=new DeliverableChecklistServiceImpl();DeliverableChecklistDO row;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);ReflectionTestUtils.setField(service,"deliverableChecklistMapper",mapper);ReflectionTestUtils.setField(service,"deliveryAccess",access);ReflectionTestUtils.setField(service,"materials",materials);row=new DeliverableChecklistDO();row.setId(9L);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(1);row.setVersion(4L);row.setName("Checklist");when(mapper.selectOwnerForUpdate(any())).thenReturn(row);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void actualPassRegistersUnrevisionedResultAfterCas(){when(mapper.updateById(any(DeliverableChecklistDO.class))).thenReturn(1);service.passDeliverableChecklist(9L);var order=inOrder(mapper,access,materials);order.verify(mapper).selectOwnerForUpdate(any());order.verify(access).require(row,"audit");order.verify(mapper).updateById(argThat((DeliverableChecklistDO update)->update.getVersion()==4&&update.getStatus()==2&&update.getCheckTime()!=null));order.verify(materials).registerBusinessResultMaterial("ACC","deliverableChecklist",9L,"DELIVERABLE_CHECKLIST","deliverableChecklist","9",null,"Checklist",20L);}
 @Test void staleCasDoesNotPublishBusinessResult(){assertThrows(Exception.class,()->service.passDeliverableChecklist(9L));verifyNoInteractions(materials);}
 @Test void scopeDenialDoesNotAdvanceNativeState(){doThrow(new IllegalStateException("scope denied")).when(access).require(row,"audit");assertThrows(Exception.class,()->service.passDeliverableChecklist(9L));verify(mapper,never()).updateById(any(DeliverableChecklistDO.class));verifyNoInteractions(materials);}
 @Test void wrongNativeStateCannotPublishResult(){row.setStatus(0);assertThrows(Exception.class,()->service.passDeliverableChecklist(9L));verifyNoInteractions(materials);}
}
