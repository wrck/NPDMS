package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P04AccountBaseSyncTest extends RemainingTaskMySqlSupport {
 org.springframework.security.crypto.password.PasswordEncoder encoder;
 cn.iocoder.yudao.framework.tenant.config.TenantProperties tenants;
 P04AccountSyncTask.TargetMapper mapper;
 P04AccountSyncTask task;
 @BeforeEach void accountTask(){
  mapper=mock(P04AccountSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(session.getMapper(P04AccountSyncTask.TargetMapper.class)));doReturn(0).when(mapper).oneSourcePerTargetIndex();encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);when(encoder.encode(any())).thenReturn("fixture-random-secret-digest");tenants=mock(cn.iocoder.yudao.framework.tenant.config.TenantProperties.class);
  task=spy(new P04AccountSyncTask(mock(jakarta.validation.Validator.class),mapper,encoder,tenants,mock(org.springframework.cache.CacheManager.class)));
  doAnswer(call->{seed(call.getArgument(0));return null;}).when(task).load(any());
  jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,password,status) VALUES(1,1,'admin','admin','unchanged-original-credential',0)");
  jdbc.update("INSERT INTO system_role(id,tenant_id,name,code,sort,status,type,data_scope,data_scope_dept_ids) VALUES(20,1,'user','user',0,0,2,5,'[]')");
 }
 void seed(SyncContext<P04AccountSyncTask.Parameters> c){
  var p=c.parameters();var a=new P04AccountSyncTask.Account().setId(100L).setUsername("w00001").setRealName("source person").setStatus(1).setFnd(true).setRoleIds(";3;").setCreator("admin").setCreateTime(at).setUpdateTime(at);
  var admin=new P04AccountSyncTask.Account().setId(81L).setUsername("admin").setFnd(true);p.accounts=new ArrayList<>(List.of(a,admin));p.names.put(a.getUsername(),List.of(a));p.fnd.put(100L,a);p.employments=new ArrayList<>();p.roles=List.of(new P04AccountSyncTask.RawRole().setId(3L).setFnd(true).setStatus(1));p.memberships=List.of();p.powers=List.of();p.rawCount=3;
  for(var u:mapper.selectUsers(new P04AccountSyncTask.TenantQuery(1,c.preview()))){p.formalUsers.put(u.getId(),u);p.usersByName.put(u.getUsername().toLowerCase(Locale.ROOT),u);}
  p.directory.put("COMPANY:001",new P04AccountSyncTask.Directory().setId(10L).setCode("001").setName("one"));p.directory.put("COMPANY:002",new P04AccountSyncTask.Directory().setId(11L).setCode("002").setName("two"));
 }
 @Test void previewPlansNewAccountsMembershipAndScopesWithoutCredentialsOrAnyFormalWrite(){
  assertEquals(4,run(task,true).inserted());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_users",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));verifyNoInteractions(encoder);verify(identities,never()).saveAll(any());
 }
 @Test void nativeBatchInsertsUseRealPlannedParentIdsAndPreserveOriginalAdminThenReplay(){
  assertEquals(4,run(task,false).inserted());long user=jdbc.queryForObject("SELECT id FROM system_users WHERE username='w00001'",Long.class);assertEquals(user,jdbc.queryForObject("SELECT user_id FROM system_user_role",Long.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope WHERE user_id=?",Integer.class,user));assertEquals("unchanged-original-credential",jdbc.queryForObject("SELECT password FROM system_users WHERE id=1",String.class));verify(encoder,times(1)).encode(any());assertEquals(1L,saved.get("USER_FND:81").targetId());assertFalse(saved.containsKey("ACCOUNT:AUDIT_ADMIN"));
  assertEquals(0,run(task,true).inserted());assertEquals(6,run(task,true).unchanged());
 }
 @Test void laterMissingScopeDepartmentRollsBackAccountAndMembershipWrites(){
  doAnswer(call->{SyncContext<P04AccountSyncTask.Parameters> c=call.getArgument(0);seed(c);c.parameters().powers=List.of(new P04AccountSyncTask.Power().setId(50L).setFndUserId(100L).setAreapower("UNAPPROVED").setCreateTime(at));return null;}).when(task).load(any());
  assertThrows(IllegalArgumentException.class,()->run(task,false));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_users",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));
 }
 @Test void historicalEmploymentUsesAlreadyApprovedJsonAndKeepsUnresolvedPersonnelReferences(){
  var c=context(true).forParameters(new P04AccountSyncTask.Parameters());seed(c);var p=c.parameters();var a=p.fnd.get(100L);a.setFnd(false).setIsSysUser(1).setUserCustom4(77L);p.fnd.clear();p.legacy.put(100L,a);p.accounts.add(new P04AccountSyncTask.Account().setId(0L).setUsername("admin").setFnd(false));p.roles=List.of();p.rawCompanies.put(1L,new P04AccountSyncTask.Directory().setId(1L).setCode("01"));p.rawDepartments.put(88L,new P04AccountSyncTask.Directory().setId(88L).setCode("160000"));p.directory.put("DEPARTMENT:160000",new P04AccountSyncTask.Directory().setId(500L).setCode("160000"));p.employees.put(77L,new P04AccountSyncTask.Employee().setId(77L).setWorkNo("00001").setName("source person"));
  var employment=new P04AccountSyncTask.Employment().setId(10L).setUserId(100L).setWorkNo("00001").setRealName("source person").setCompID(1L).setDepID(391L).setReportTo(99L).setSex(0).setEmpStatus(2L).setCustom4("10;afss");p.details.put(100L,List.of(employment));task.planUsers(c);
  var row=p.plannedUsers.getFirst().getUser();var json=JsonUtils.parseTree(row.getCustomInfo()).path("employmentAssignments").get(0);assertEquals(391L,json.path("sourceDepartmentId").asLong());assertEquals(500L,json.path("departmentId").asLong());assertEquals(99L,json.path("reportingEmployeeReference").path("sourceEmployeeId").asLong());assertTrue(json.path("reportingEmployeeReference").path("userId").isNull());assertEquals("10;afss",json.path("projectTypeRestrictions").asString());assertEquals(2,row.getSex());assertEquals(1,JsonUtils.parseTree(row.getCustomInfo()).path("systemPresetUser").asInt());
  assertFalse(JsonUtils.toJsonString(row).contains("initialPasswordHash"));verifyNoInteractions(encoder);
 }
 @Test void fndPrimaryDepartmentUsesApprovedDirectoryAlias(){var c=context(true).forParameters(new P04AccountSyncTask.Parameters());seed(c);c.parameters().fnd.get(100L).setDpNo("311202");c.parameters().directory.put("DEPARTMENT:162102",new P04AccountSyncTask.Directory().setId(200L).setCode("162102"));task.planUsers(c);assertEquals(200L,c.parameters().plannedUsers.getFirst().getUser().getDeptId());}
 @Test void oldMetadataIndexBlocksWritesButNotPreview(){doReturn(1).when(mapper).oneSourcePerTargetIndex();assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("METADATA_MULTI_SOURCE_INDEX_REQUIRED"));assertDoesNotThrow(()->run(task,true));verifyNoInteractions(encoder);}
 @Test void existingUnboundAccountIsProtectedAndTenantQuotaIsEnforced(){
  jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,password,status) VALUES(9,1,'w00001','manual','manual-original',0)");assertThrows(IllegalArgumentException.class,()->run(task,true));assertEquals("manual",jdbc.queryForObject("SELECT nickname FROM system_users WHERE id=9",String.class));
  jdbc.update("DELETE FROM system_users WHERE id=9");when(tenants.getEnable()).thenReturn(true);jdbc.update("INSERT INTO system_tenant(id,name,contact_name,contact_mobile,status,package_id,expire_time,account_count) VALUES(1,'fixture','fixture','1',0,1,'2099-01-01',1)");assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("TENANT_ACCOUNT_CAPACITY"));
 }
}
