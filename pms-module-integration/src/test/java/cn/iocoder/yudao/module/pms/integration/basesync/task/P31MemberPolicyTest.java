package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncIdentityStore;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.basesync.task.P31MemberContactSyncTask.*;

class P31MemberPolicyTest {
 final LocalDateTime now=LocalDateTime.of(2026,11,2,10,0);
 MemberRow row(){return new MemberRow().setId(1L).setProjectId(10L).setTenantId(1L).setMemberRole("PROJECT_MANAGER").setMemberCode("exact")
  .setCompanyId(7L).setEffectiveFrom(now.minusDays(1)).setCreateTime(now.minusDays(2)).setUpdateTime(now.minusDays(2)).setAuthorizationConfirmed(false).setDeleted(false);}
 @Test void exactAccountNeedsCurrentRoleAndCompanyQualification(){
  var api=mock(ActiveUserSelectionApi.class);var task=new P31MemberContactSyncTask(null,null,null,null,api);
  var account=new SystemUserMigrationApi.MemberIdentity(42L,"exact","REAL-EMP");
  when(api.page(any())).thenReturn(new PageResult<>(List.of(new ActiveUserSelectionApi.User(42L,"exact","name",3L)),1L));
  var row=row();task.bindAccount(new AccountBinding(row,account,now));
  assertEquals(42L,row.getUserId());assertEquals("REAL-EMP",row.getEmployeeNo());assertTrue(row.getAuthorizationConfirmed());assertEquals("COLLABORATOR",row.getAssignmentType());
  verify(api).page(new ActiveUserSelectionApi.Query(1,1,null,java.util.Set.of(42L),"PROJECT_MANAGER",new ActiveUserSelectionApi.Qualification(7L,null,null,"PROJECT_MANAGER")));
  when(api.page(any())).thenReturn(new PageResult<>(List.of(),0L));
  row=row();task.bindAccount(new AccountBinding(row,account,now));assertEquals(42L,row.getUserId());assertFalse(row.getAuthorizationConfirmed());assertNull(row.getAssignmentType());
 }
 @Test void missingExpiredFutureInvertedAndEndUserNeverGrant(){
  var api=mock(ActiveUserSelectionApi.class);var task=new P31MemberContactSyncTask(null,null,null,null,api);var account=new SystemUserMigrationApi.MemberIdentity(42L,"exact","E");
  var missing=row();task.bindAccount(new AccountBinding(missing,null,now));assertNull(missing.getUserId());
  for(var row:List.of(row().setEffectiveTo(now),row().setEffectiveFrom(now.plusDays(1)),row().setEffectiveTo(now.minusDays(2)),row().setEffectiveFrom(null))){task.bindAccount(new AccountBinding(row,account,now));assertEquals(42L,row.getUserId());assertFalse(row.getAuthorizationConfirmed());}
  var contact=row().setMemberRole("END_USER");task.bindAccount(new AccountBinding(contact,account,now));assertNull(contact.getUserId());assertFalse(contact.getAuthorizationConfirmed());verifyNoInteractions(api);
 }
 @Test void confirmationRequiresOriginalAuditTime(){
  var api=mock(ActiveUserSelectionApi.class);var task=new P31MemberContactSyncTask(null,null,null,null,api);
  when(api.page(any())).thenReturn(new PageResult<>(List.of(new ActiveUserSelectionApi.User(42L,"exact","name",3L)),1L));
  assertTrue(assertThrows(IllegalArgumentException.class,()->task.bindAccount(new AccountBinding(row().setCreateTime(null),new SystemUserMigrationApi.MemberIdentity(42L,"exact","E"),now))).getMessage().contains("CONFIRMED_AUDIT_TIME_MISSING"));
 }
 @Test void overlapRejectedButAdjacentOtherProjectsAndEmptyIntervalsAllowed(){
  var old=row().setUserId(42L).setEffectiveTo(now);var next=row().setUserId(42L).setEffectiveFrom(now.minusHours(1));
  assertThrows(IllegalArgumentException.class,()->validateNewInterval(new NewInterval(next,List.of(old))));
  assertThrows(IllegalArgumentException.class,()->validateNewInterval(new NewInterval(next.setMemberRole("SERVICE_MANAGER"),List.of(old.setMemberRole("SERVICE_MANAGER_L1")))));
  assertDoesNotThrow(()->validateNewInterval(new NewInterval(next.setEffectiveFrom(now),List.of(old))));
  assertDoesNotThrow(()->validateNewInterval(new NewInterval(row().setUserId(42L).setProjectId(20L),List.of(old))));
  assertDoesNotThrow(()->validateNewInterval(new NewInterval(row().setUserId(42L).setEffectiveTo(now.minusDays(1)),List.of(old))));
 }
 @Test void legacyAndApprovedContactRepairReplayWithoutRewriting(){
  var old=row().setMemberRole("60");var binding=new SyncIdentityStore.Identity("1",1L,legacyFingerprint(old));
  assertDoesNotThrow(()->verifyReplay(new Replay(row().setMemberRole("END_USER"),old,binding)));
  old.setExternalMobile("manual");assertThrows(IllegalArgumentException.class,()->verifyReplay(new Replay(row().setMemberRole("END_USER"),old,binding)));
  var repaired=row().setMemberRole("END_USER").setPhoneNumber("123").setEmail("e").setExternalMobile("123").setExternalEmail("e");
  var receipt=new SyncIdentityStore.Identity("1",1L,memberFingerprint(repaired));
  assertDoesNotThrow(()->verifyReplay(new Replay(row().setMemberRole("END_USER").setPhoneNumber("123").setEmail("e").setExternalMobile("123").setExternalEmail("e"),repaired,receipt)));
  repaired.setExternalEmail("manual");assertThrows(IllegalArgumentException.class,()->verifyReplay(new Replay(row().setMemberRole("END_USER"),repaired,receipt)));
 }
 @Test void replayPreservesImportedQualificationButRejectsManualAndSourceChanges(){
  var old=row().setUserId(42L).setEmployeeNo("E").setAuthorizationConfirmed(true).setAssignmentType("COLLABORATOR");
  var receipt=new SyncIdentityStore.Identity("1",1L,memberFingerprint(old));
  assertDoesNotThrow(()->verifyReplay(new Replay(row(),old,receipt)));
  assertThrows(IllegalArgumentException.class,()->verifyReplay(new Replay(row().setMemberCode("different"),old,receipt)));
  old.setAuthorizationConfirmed(false);assertThrows(IllegalArgumentException.class,()->verifyReplay(new Replay(row(),old,receipt)));
 }
}
