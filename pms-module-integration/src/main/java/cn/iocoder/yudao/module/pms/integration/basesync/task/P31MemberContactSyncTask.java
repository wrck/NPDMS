package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Historical unconfirmed members include source role 60; no customer ownership or current grants. */
@Component @RequiredArgsConstructor
public class P31MemberContactSyncTask extends AbstractSyncTask<P31MemberContactSyncTask.Source,P31MemberContactSyncTask.Parameters> {
 private final TargetMapper mapper;private final BaseSyncPublishedProjects publications;private final BaseSyncProjectReferences projects;
 public static final class Parameters {private BaseSyncPublishedProjects.Snapshot publication;private BaseSyncProjectReferences.CurrentManageScope manageScope;}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override public String taskCode(){return "BASE_P31";}
 @Override public String taskName(){return "项目成员及最终客户联系人历史";}
 @Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.parameters().publication=publications.load(new BaseSyncPublishedProjects.Query(c.tenantId(),c.connectionId(),c.maxRows(),c.source(),c.preview()));c.parameters().manageScope=projects.currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(c.tenantId(),c.actorId()));syncData(c.forData("MemberContact",false,true));}
 public record ReadQuery(int limit,List<String> projectKeys){}
 @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){
  var keys=new ArrayList<>(c.parameters().publication.members().keySet());Collections.sort(keys);
  try(var session=c.source().open(c.connectionId())){for(int i=0;i<keys.size();i+=500)syncDataQueryBySql(session.sqlSession(),new MappedCall<>(c,sourceMapperNamespace()+".selectMemberContact",new ReadQuery(c.maxRows()+1,keys.subList(i,Math.min(i+500,keys.size())))),handler);}
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var publication=c.parameters().publication;var selected=new LinkedHashMap<String,BaseSyncPublishedProjects.Member>();
  for(var s:b.rows()){if(s.sourceId==null||s.sourceId<=0||s.originalProjectId==null)fail("SOURCE_IDENTITY_INVALID");var member=publication.members().get(s.originalProjectId.toString());if(member==null||!Objects.equals(member.projectType(),s.projectType))fail("SOURCE_OUTSIDE_PUBLISHED_SCOPE");selected.put(member.sourceKey(),member);}
  var refs=projects.verifyPublished(new BaseSyncProjectReferences.PublishedQuery(c.tenantId(),c.actorId(),List.copyOf(selected.values()),c.preview(),c.parameters().manageScope));var ids=refs.values().stream().map(BaseSyncProjectReferences.Identity::id).distinct().toList();
  var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();var members=c.identities().findByKeys(new SyncIdentityStore.Query("MEMBER_HISTORY",keys));var contacts=c.identities().findByKeys(new SyncIdentityStore.Query("CONTACT_HISTORY",keys));
  // Existing bindings identify the exact history rows; a batch with new sources still checks all project history.
  var boundIds=members.size()==keys.size()?members.values().stream().map(SyncIdentityStore.Identity::targetId).distinct().toList():null;
  var q=new TargetQuery(c.tenantId(),c.connectionId(),ids,c.maxRows()+1,c.preview(),boundIds);var currentMembers=mapper.selectMembers(q);
  if(currentMembers.size()>c.maxRows())fail("FORMAL_HISTORY_CAPACITY");
  var membersById=new HashMap<Long,MemberRow>();var unownedMembers=new HashSet<MemberKey>();
  for(var row:currentMembers){membersById.putIfAbsent(row.id,row);if(!Boolean.TRUE.equals(row.owned))unownedMembers.add(memberKey(row));}
  for(var s:b.rows()){
   if(!Set.of("10","20","30","60").contains(Objects.toString(s.memberRole,""))||!"10".equals(s.projectType))fail("SOURCE_ROLE_OR_PROJECT_TYPE_INVALID");
   if(s.createTime!=null&&s.createTime.getNano()!=0||s.updateTime!=null&&s.updateTime.getNano()!=0)fail("SOURCE_INTERVAL_OR_AUDIT_PRECISION_INVALID");
   var p=refs.get(s.originalProjectId.toString());s.action=SyncWriteAction.INSERT;
   if(contacts.containsKey(s.sourceId.toString()))fail("EXISTING_CONTACT_HISTORY_PROTECTED");
    var row=new MemberRow();row.tenantId=c.tenantId();row.projectId=p.id();row.memberCode=s.memberCode;row.memberName=s.memberName;row.phoneNumber=s.phoneNum;row.email=s.email;row.registrationSource=s.fromFlag;row.memberRole=switch(s.memberRole){case "10"->"SALES_REPRESENTATIVE";case "20"->"SERVICE_MANAGER";case "30"->"PROJECT_MANAGER";case "60"->"60";default->throw new IllegalArgumentException();};row.companyId=p.companyId();row.companyCode=p.companyCode();row.companyName=p.companyName();row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.authorizationConfirmed=false;row.status="ACTIVE";row.version=0;row.deleted=false;
    validateMember(row);var binding=members.get(s.sourceId.toString());var old=binding==null?null:membersById.get(binding.targetId());
    if(binding!=null){if(old==null||Boolean.TRUE.equals(old.deleted))fail("EXISTING_MEMBER_PROTECTED");var oldFingerprint=memberFingerprint(old);if(!binding.fingerprint().equals(oldFingerprint))fail("EXISTING_MEMBER_PROTECTED");row.id=old.id;if(!memberFingerprint(row).equals(oldFingerprint))fail("MEMBER_HISTORY_IMMUTABLE");s.action=SyncWriteAction.UNCHANGED;s.member=old;}
    else{if(unownedMembers.contains(memberKey(row)))fail("ORIGINAL_MEMBER_CANNOT_ADOPT");row.id=c.preview()?null:IdWorker.getId();s.member=row;}
  }
 }
 record MemberKey(Long projectId,String memberCode,String memberRole,LocalDateTime effectiveFrom,LocalDateTime effectiveTo){}
 static MemberKey memberKey(MemberRow row){return new MemberKey(row.projectId,row.memberCode,row.memberRole,row.effectiveFrom,row.effectiveTo);}
 static void validateMember(MemberRow r){checkTextLength(r.employeeNo,64);checkTextLength(r.memberName,128);checkTextLength(r.companyCode,64);checkTextLength(r.companyName,255);checkTextLength(r.departmentCode,64);checkTextLength(r.departmentName,255);checkTextLength(r.memberRole,32);checkTextLength(r.assignmentType,32);checkTextLength(r.responsibility,500);checkTextLength(r.changeReason,500);checkTextLength(r.status,32);checkTextLength(r.creator,64);checkTextLength(r.updater,64);checkTextLength(r.remark,500);checkTextLength(r.endReason,500);checkTextLength(r.memberCode,45);checkTextLength(r.phoneNumber,20);checkTextLength(r.email,45);checkTextLength(r.registrationSource,2);}

 static String memberFingerprint(MemberRow r){return SyncFingerprint.of(Arrays.asList(r.id,r.projectId,r.userId,r.employeeNo,r.memberName,r.companyId,r.companyCode,r.companyName,r.departmentId,r.departmentCode,r.departmentName,r.memberRole,r.assignmentType,r.siteId,r.responsibility,r.changeReason,r.effectiveFrom,r.effectiveTo,r.status,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted,r.deletedTime,r.tenantId,r.remark,r.endReason,r.memberCode,r.phoneNumber,r.email,r.registrationSource,r.authorizationConfirmed));}
 static void fail(String code){throw new IllegalArgumentException("P31_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);if(!fresh.rows().isEmpty()&&mapper.insertMembers(fresh)!=fresh.rows().size())fail("BATCH_INSERT_CONFLICT");}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("MEMBER_HISTORY",s.sourceId.toString(),s.member.id,memberFingerprint(s.member),null)).toList());}
 @lombok.Data public static class Source implements SyncReferences.SourceAudit {private Long sourceId,originalProjectId;private String projectType,memberRole,memberCode,memberName,phoneNum,email,fromFlag,sourceCreator,sourceUpdater,resolvedCreator,resolvedUpdater;private LocalDateTime createTime,updateTime,effectiveFrom,effectiveTo;private MemberRow member;private SyncWriteAction action;public boolean usesAuditCatalogue(){return true;}public boolean requiresAuditReferences(){return true;}public boolean requiresResolvedAuditReferences(){return true;}}
 @lombok.Data public static class MemberRow{private Long id;private Long projectId;private Long userId;private String employeeNo;private String memberName;private Long companyId;private String companyCode;private String companyName;private Long departmentId;private String departmentCode;private String departmentName;private String memberRole;private String assignmentType;private Long siteId;private String responsibility;private String changeReason;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String status;private Integer version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;private LocalDateTime deletedTime;private Long tenantId;private String remark;private String endReason;private String memberCode;private String phoneNumber;private String email;private String registrationSource;private Boolean authorizationConfirmed;private Boolean owned;}

 public record TargetQuery(long tenantId,long connectionId,List<Long> projectIds,int limit,boolean preview,List<Long> boundTargetIds){
  public TargetQuery(long tenantId,long connectionId,List<Long> projectIds,int limit,boolean preview){this(tenantId,connectionId,projectIds,limit,preview,null);}
 }
 public interface SourceMapper{void selectMemberContact(ReadQuery query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper{List<MemberRow> selectMembers(@Param("query") TargetQuery query);int insertMembers(SyncBatch<Source,Parameters> batch);}
}
