package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.normalize;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Source history: exact internal identities and qualified grants; END_USER remains display-only. */
@Component @RequiredArgsConstructor
public class P31MemberContactSyncTask extends AbstractSyncTask<P31MemberContactSyncTask.Source,P31MemberContactSyncTask.Parameters> {
 private final TargetMapper mapper;private final BaseSyncPublishedProjects publications;private final BaseSyncProjectReferences projects;private final SystemUserMigrationApi accounts;private final ActiveUserSelectionApi qualification;
 public static final class Parameters {private BaseSyncPublishedProjects.Snapshot publication;private BaseSyncProjectReferences.CurrentManageScope manageScope;private final Map<IntervalKey,List<MemberRow>> previewMembers=new HashMap<>();}
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
  var accountByCode=loadAccounts(b.matching(s->!members.containsKey(s.sourceId.toString())));
  var intervals=new HashMap<IntervalKey,List<MemberRow>>();
  for(var row:currentMembers)if(row.userId!=null)intervals.computeIfAbsent(intervalKey(row),key->new ArrayList<>()).add(row);
  var previewKeys=new HashSet<IntervalKey>();
  for(var s:b.rows()){
   if(!Set.of("10","20","30","60").contains(Objects.toString(s.memberRole,""))||!"10".equals(s.projectType))fail("SOURCE_ROLE_OR_PROJECT_TYPE_INVALID");
   if(s.createTime!=null&&s.createTime.getNano()!=0||s.updateTime!=null&&s.updateTime.getNano()!=0)fail("SOURCE_INTERVAL_OR_AUDIT_PRECISION_INVALID");
   var p=refs.get(s.originalProjectId.toString());s.action=SyncWriteAction.INSERT;
   if(contacts.containsKey(s.sourceId.toString()))fail("EXISTING_CONTACT_HISTORY_PROTECTED");
    var row=new MemberRow();row.tenantId=c.tenantId();row.projectId=p.id();row.memberCode=s.memberCode;row.memberName=s.memberName;row.phoneNumber=s.phoneNum;row.email=s.email;row.registrationSource=s.fromFlag;row.memberRole=switch(s.memberRole){case "10"->"SALES_REPRESENTATIVE";case "20"->"SERVICE_MANAGER";case "30"->"PROJECT_MANAGER";case "60"->"END_USER";default->throw new IllegalArgumentException();};row.companyId=p.companyId();row.companyCode=p.companyCode();row.companyName=p.companyName();row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;row.creator=s.resolvedCreator;row.updater=s.resolvedUpdater;row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;row.authorizationConfirmed=false;row.status="ACTIVE";row.version=0;row.deleted=false;
    if("60".equals(s.memberRole)){row.externalMobile=s.phoneNum;row.externalEmail=s.email;}
    var binding=members.get(s.sourceId.toString());var old=binding==null?null:membersById.get(binding.targetId());
    if(binding!=null){verifyReplay(new Replay(row,old,binding,s.originalEffectiveTo));s.action=SyncWriteAction.UNCHANGED;s.member=old;}
    else{
     bindAccount(new AccountBinding(row,accountByCode.get(s.memberCode),c.startedAt()));
     validateMember(row);
     if(unownedMembers.contains(memberKey(row)))fail("ORIGINAL_MEMBER_CANNOT_ADOPT");
     if(row.userId!=null){
      var key=intervalKey(row);var sameSubject=intervals.computeIfAbsent(key,k->new ArrayList<>());
      if(c.preview()&&previewKeys.add(key))sameSubject.addAll(c.parameters().previewMembers.getOrDefault(key,List.of()));
      validateNewInterval(new NewInterval(row,sameSubject));sameSubject.add(row);
      if(c.preview())c.parameters().previewMembers.computeIfAbsent(key,k->new ArrayList<>()).add(row);
     }
     row.id=c.preview()?null:IdWorker.getId();s.member=row;
    }
  }
 }
 private Map<String,SystemUserMigrationApi.MemberIdentity> loadAccounts(SyncBatch<Source,Parameters> batch){
  var names=batch.rows().stream().filter(s->!"60".equals(s.memberRole)).map(Source::getMemberCode).filter(Objects::nonNull).filter(v->!v.isEmpty()).distinct().toList();
  var result=new HashMap<String,SystemUserMigrationApi.MemberIdentity>();
  for(int i=0;i<names.size();i+=200){var part=Set.copyOf(names.subList(i,Math.min(i+200,names.size())));
   for(var account:accounts.getHistoricalMemberIdentities(batch.context().tenantId(),part))
    if(!part.contains(account.username())||account.id()==null||account.id()<=0||result.putIfAbsent(account.username(),account)!=null)fail("ACCOUNT_AMBIGUOUS");
  }
  return result;
 }
 record AccountBinding(MemberRow row,SystemUserMigrationApi.MemberIdentity account,LocalDateTime now){}
 void bindAccount(AccountBinding binding){
  var row=binding.row();var account=binding.account();
  if("END_USER".equals(row.memberRole)||account==null)return;
  row.userId=account.id();row.employeeNo=account.employeeNo();
  if(row.effectiveFrom==null||row.effectiveFrom.isAfter(binding.now())||row.effectiveTo!=null&&!row.effectiveTo.isAfter(binding.now()))return;
  var scope="PROJECT_MANAGER".equals(row.memberRole)?new ActiveUserSelectionApi.Qualification(row.companyId,null,null,"PROJECT_MANAGER"):null;
  var page=qualification.page(new ActiveUserSelectionApi.Query(1,1,null,Set.of(account.id()),row.memberRole,scope));
  if(page.getTotal()==1&&page.getList().size()==1&&Objects.equals(page.getList().getFirst().id(),account.id())){
   if(row.createTime==null||row.updateTime==null)fail("CONFIRMED_AUDIT_TIME_MISSING");
   row.authorizationConfirmed=true;row.assignmentType="COLLABORATOR";
  }
 }
 record IntervalKey(Long projectId,Long userId,String role){}
 static IntervalKey intervalKey(MemberRow row){return new IntervalKey(row.projectId,row.userId,normalize(row.memberRole));}
 record NewInterval(MemberRow row,List<MemberRow> existing){}
 static void validateNewInterval(NewInterval query){
  var row=query.row();if(row.userId==null||row.effectiveFrom==null||row.effectiveTo!=null&&!row.effectiveTo.isAfter(row.effectiveFrom))return;
  for(var old:query.existing())if(!Boolean.TRUE.equals(old.deleted)&&Objects.equals(row.projectId,old.projectId)&&Objects.equals(row.userId,old.userId)
   &&Objects.equals(normalize(row.memberRole),normalize(old.memberRole))&&old.effectiveFrom!=null&&(old.effectiveTo==null||old.effectiveTo.isAfter(old.effectiveFrom))
   &&(row.effectiveTo==null||old.effectiveFrom.isBefore(row.effectiveTo))&&(old.effectiveTo==null||row.effectiveFrom.isBefore(old.effectiveTo)))fail("SOURCE_INTERVAL_OVERLAP");
 }
 record Replay(MemberRow expected,MemberRow actual,SyncIdentityStore.Identity binding,LocalDateTime originalEffectiveTo){
  Replay(MemberRow expected,MemberRow actual,SyncIdentityStore.Identity binding){this(expected,actual,binding,null);}
 }
 static void verifyReplay(Replay replay){
  var row=replay.expected();var old=replay.actual();
  if(old==null||Boolean.TRUE.equals(old.deleted))fail("EXISTING_MEMBER_PROTECTED");
  boolean legacy=Objects.equals(replay.binding().fingerprint(),legacyFingerprint(old));
  if(!legacy&&!Objects.equals(replay.binding().fingerprint(),memberFingerprint(old)))fail("EXISTING_MEMBER_PROTECTED");
  row.id=old.id;
  if(legacy){
   // Old bindings did not cover display contacts: only their original NULL values are admissible.
   if(old.externalMobile!=null||old.externalEmail!=null)fail("EXISTING_MEMBER_PROTECTED");
   if("END_USER".equals(row.memberRole)){row.memberRole="60";row.externalMobile=null;row.externalEmail=null;}
  }else if(!"END_USER".equals(row.memberRole)){
   // Qualification is an import-time fact; never recalculate it or refresh identity during replay.
   row.userId=old.userId;row.employeeNo=old.employeeNo;row.authorizationConfirmed=old.authorizationConfirmed;row.assignmentType=old.assignmentType;
  }
  if(memberFingerprint(row).equals(memberFingerprint(old)))return;
  // Earlier approved imports retained raw reversed ends. Verify and preserve those exact historical values.
  if(row.effectiveFrom!=null&&replay.originalEffectiveTo()!=null&&replay.originalEffectiveTo().isBefore(row.effectiveFrom)){row.effectiveTo=replay.originalEffectiveTo();if(memberFingerprint(row).equals(memberFingerprint(old)))return;}
  fail("MEMBER_HISTORY_IMMUTABLE");
 }
 record MemberKey(Long projectId,String memberCode,String memberRole,LocalDateTime effectiveFrom,LocalDateTime effectiveTo){}
 static MemberKey memberKey(MemberRow row){return new MemberKey(row.projectId,row.memberCode,"60".equals(row.memberRole)?"END_USER":row.memberRole,row.effectiveFrom,row.effectiveTo);}
 static void validateMember(MemberRow r){checkTextLength(r.externalMobile,32);checkTextLength(r.externalEmail,128);checkTextLength(r.employeeNo,64);checkTextLength(r.memberName,128);checkTextLength(r.companyCode,64);checkTextLength(r.companyName,255);checkTextLength(r.departmentCode,64);checkTextLength(r.departmentName,255);checkTextLength(r.memberRole,32);checkTextLength(r.assignmentType,32);checkTextLength(r.responsibility,500);checkTextLength(r.changeReason,500);checkTextLength(r.status,32);checkTextLength(r.creator,64);checkTextLength(r.updater,64);checkTextLength(r.remark,500);checkTextLength(r.endReason,500);checkTextLength(r.memberCode,45);checkTextLength(r.phoneNumber,20);checkTextLength(r.email,45);checkTextLength(r.registrationSource,2);}

 static List<Object> fingerprintFields(MemberRow r){return Arrays.asList(r.id,r.projectId,r.userId,r.employeeNo,r.memberName,r.companyId,r.companyCode,r.companyName,r.departmentId,r.departmentCode,r.departmentName,r.memberRole,r.assignmentType,r.siteId,r.responsibility,r.changeReason,r.effectiveFrom,r.effectiveTo,r.status,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted,r.deletedTime,r.tenantId,r.remark,r.endReason,r.memberCode,r.phoneNumber,r.email,r.registrationSource,r.authorizationConfirmed);}
 static String legacyFingerprint(MemberRow r){return SyncFingerprint.of(fingerprintFields(r));}
 static String memberFingerprint(MemberRow r){var fields=new ArrayList<>(fingerprintFields(r));fields.add(r.externalMobile);fields.add(r.externalEmail);return SyncFingerprint.of(fields);}
 static void fail(String code){throw new IllegalArgumentException("P31_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);if(!fresh.rows().isEmpty()&&mapper.insertMembers(fresh)!=fresh.rows().size())fail("BATCH_INSERT_CONFLICT");}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("MEMBER_HISTORY",s.sourceId.toString(),s.member.id,memberFingerprint(s.member),null)).toList());}
 @lombok.Data public static class Source implements SyncReferences.SourceAudit {private Long sourceId,originalProjectId;private String projectType,memberRole,memberCode,memberName,phoneNum,email,fromFlag,sourceCreator,sourceUpdater,resolvedCreator,resolvedUpdater;private LocalDateTime createTime,updateTime,effectiveFrom,effectiveTo,originalEffectiveTo;private MemberRow member;private SyncWriteAction action;public boolean usesAuditCatalogue(){return true;}public boolean requiresAuditReferences(){return true;}public boolean requiresResolvedAuditReferences(){return true;}}
 @lombok.Data public static class MemberRow{private Long id;private Long projectId;private Long userId;private String employeeNo;private String memberName;private Long companyId;private String companyCode;private String companyName;private Long departmentId;private String departmentCode;private String departmentName;private String memberRole;private String assignmentType;private Long siteId;private String responsibility;private String changeReason;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String status;private Integer version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;private LocalDateTime deletedTime;private Long tenantId;private String remark;private String endReason;private String memberCode;private String phoneNumber;private String email;private String registrationSource;private Boolean authorizationConfirmed;private String externalMobile,externalEmail;private Boolean owned;}

 public record TargetQuery(long tenantId,long connectionId,List<Long> projectIds,int limit,boolean preview,List<Long> boundTargetIds){
  public TargetQuery(long tenantId,long connectionId,List<Long> projectIds,int limit,boolean preview){this(tenantId,connectionId,projectIds,limit,preview,null);}
 }
 public interface SourceMapper{void selectMemberContact(ReadQuery query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper{List<MemberRow> selectMembers(@Param("query") TargetQuery query);int insertMembers(SyncBatch<Source,Parameters> batch);}
}
