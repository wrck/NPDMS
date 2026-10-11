package cn.iocoder.yudao.module.pms.engineering.service.installation;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class InstallationBusinessResultSourceTest {
 final InstallationMapper records=mock(InstallationMapper.class);
 final DeviceQueryApi devices=mock(DeviceQueryApi.class);
 final InstallationBusinessResultSource source=new InstallationBusinessResultSource(records,devices);
 InstallationDO first,last;
 Query query=new Query(1L,9L,InstallationBusinessResultSource.TYPE,"31","31");
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);first=row(30L,7L);last=row(31L,8L);
  when(records.selectById(31L)).thenReturn(last);when(records.selectById(30L)).thenReturn(first);
  when(records.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of(last,first));
  when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(7L,"A",null),new ProjectDeviceInstallationFact(8L,"B",null)));
 }
 @AfterEach void clear(){TenantContextHolder.clear();}
 InstallationDO row(Long id,Long device){var r=new InstallationDO();r.setId(id);r.setEquipmentId(device);r.setTenantId(1L);r.setProjectId(9L);r.setStatus(2);r.setVersion(2L);r.setInstallLocation("已保存地点");r.setEffectiveFrom(LocalDateTime.of(2026,10,9,20,0));return r;}
 @Test void currentIdentityIncludesAllRecordsAndRetainsNativeTime(){
  var result=source.inspect(query).result();assertEquals(Validity.CURRENT,result.validity());assertEquals(last.getEffectiveFrom(),result.formedAt());
  String version=result.observationVersion();first.setVersion(3L);assertNotEquals(version,source.lockAndInspect(query).result().observationVersion());
  assertEquals(Validity.NOT_CURRENT,source.inspect(new Query(1L,9L,InstallationBusinessResultSource.TYPE,"30","30")).result().validity());
 }
 @Test void missingPendingExpiredAndEmptyDevicesCannotFormEvidence(){
  first.setInstallLocation(" ");assertEquals(Status.NOT_FORMED,source.inspect(query).status());first.setInstallLocation("地点");
  first.setStatus(1);assertEquals(Status.NOT_FORMED,source.inspect(query).status());first.setStatus(2);
  first.setEffectiveTo(LocalDateTime.now());assertEquals(Status.NOT_FORMED,source.inspect(query).status());first.setEffectiveTo(null);
  when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of());assertEquals(Status.NOT_FORMED,source.inspect(query).status());
 }
 @Test void scopeIdentityAndInventoryFiltersRemainClosed(){
  assertThrows(IllegalArgumentException.class,()->source.inspect(new Query(2L,9L,InstallationBusinessResultSource.TYPE,"31",null)));
  assertThrows(IllegalArgumentException.class,()->source.inspect(new Query(1L,10L,InstallationBusinessResultSource.TYPE,"31",null)));
  assertThrows(IllegalArgumentException.class,()->source.inspect(new Query(1L,9L,InstallationBusinessResultSource.TYPE,"31","30")));
  assertTrue(source.inventory(new InventoryQuery(1L,9L,InstallationBusinessResultSource.TYPE,false,List.of(),null,10)).observations().isEmpty());
  assertTrue(source.inventory(new InventoryQuery(1L,9L,InstallationBusinessResultSource.TYPE,false,null,"31",10)).observations().isEmpty());
  assertThrows(IllegalArgumentException.class,()->source.inventory(new InventoryQuery(1L,9L,InstallationBusinessResultSource.TYPE,true,null,null,10)));
 }
 @Test void unrelatedAndDraftRecordsDoNotReplaceCurrentProjectEvidence(){
  var unrelated=row(99L,99L);var draft=row(100L,8L);draft.setStatus(0);
  when(records.selectProjectAddressRecordsForUpdate(any())).thenReturn(List.of(draft,unrelated,last,first));
  assertEquals(Validity.CURRENT,source.inspect(query).result().validity());
  assertEquals("31",source.inventory(new InventoryQuery(1L,9L,InstallationBusinessResultSource.TYPE,false,null,null,10)).observations().getFirst().result().resultId());
 }
 @Test void partialRecordCompletionDoesNotDeclareWholeProjectFormation(){
  var event=cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent.create(1L,9L,"OWNER.Installation.CHANGED","test",
    new cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationResult("IMP","INSTALLATION","31",null,2L,"v2","INSTALLATION_COMPLETED",null,false),1L,"test");
  assertTrue(source.declaresFormation(event));first.setStatus(1);assertFalse(source.declaresFormation(event));
 }
 @Test void addedProjectDeviceBlocksPreviouslyCompleteResult(){
  when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(7L,"A",null),new ProjectDeviceInstallationFact(8L,"B",null),new ProjectDeviceInstallationFact(9L,"C","旧地址")));
  assertEquals(Status.NOT_FORMED,source.inspect(query).status());
 }
}
