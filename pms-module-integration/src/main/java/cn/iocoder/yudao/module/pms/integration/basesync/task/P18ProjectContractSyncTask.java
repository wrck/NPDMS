package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Raw contract numbers are carried first; exact contract references are filled once after the batch insert. */
@Component @RequiredArgsConstructor
public class P18ProjectContractSyncTask extends AbstractSyncTask<P18ProjectContractSyncTask.Source,P18ProjectContractSyncTask.Parameters> {
 private final TargetMapper mapper;
 private final cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects projectAssignments;
 public static final class Parameters {private long contractGroupMissing,contractMembersMissing,contractMemberProjectMissing;}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override public String taskCode(){return "BASE_P18";}
 @Override public String taskName(){return "项目合同关系历史";}
 @Override public Set<String> requiredPermissions(){return Set.of("pms:commerce:contract:relate");}
 @Override public void work(SyncContext<Parameters> c){
  syncData(c.forData("ProjectContract",false,false));
  projectAssignments.refresh(c);
  var p=c.parameters();
  if(p.contractGroupMissing>0)c.runState().warning("P18_BRIDGE_CONTRACT_GROUP_MISSING:"+p.contractGroupMissing);
  if(p.contractMembersMissing>0)c.runState().warning("P18_BRIDGE_CONTRACT_MEMBERS_MISSING:"+p.contractMembersMissing);
  if(p.contractMemberProjectMissing>0)c.runState().warning("P18_BRIDGE_CONTRACT_MEMBER_PROJECT_MISSING:"+p.contractMemberProjectMissing);
 }
 @Override protected void syncDataBefore(SyncContext<Parameters> c){super.syncDataBefore(c);if(mapper.oneSourcePerTargetIndex()>0){if(!c.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");c.runState().warning("P18_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}}
 @Override protected List<Source> syncDataQuery(SyncContext<Parameters> c){
  try(var session=c.source().open(c.connectionId())){
   var source=session.mapper(SourceMapper.class);var q=new SourceQuery<Parameters>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters());
   var contracts=source.selectContracts(q);var groups=source.selectGroups(q);var members=source.selectMembers(q);var projects=source.selectProjects(q);
   if(contracts.size()>c.maxRows()||groups.size()>c.maxRows()||members.size()>c.maxRows()||projects.size()>c.maxRows())fail("RAW_SOURCE_CAPACITY");
   var byGroup=new HashMap<String,Source>();for(var group:groups)if(byGroup.putIfAbsent(group.groupProjectGroupCode,group)!=null)fail("RAW_GROUP_CODE_AMBIGUOUS");
   var byMemberGroup=new HashMap<String,List<Source>>();for(var member:members)byMemberGroup.computeIfAbsent(member.memberProjectGroupCode,k->new ArrayList<>()).add(member);
   var byProject=new HashMap<String,Source>();for(var project:projects)if(byProject.putIfAbsent(project.originalProjectCode,project)!=null)fail("CANONICAL_PROJECT_CODE_AMBIGUOUS");
   var rows=new ArrayList<Source>();
   for(var contract:contracts){var group=byGroup.get(contract.projectGroupCode);if(group==null){c.parameters().contractGroupMissing++;continue;}
    var groupMembers=byMemberGroup.getOrDefault(contract.projectGroupCode,List.of());if(groupMembers.isEmpty())c.parameters().contractMembersMissing++;
    for(var member:groupMembers){if(member.memberProjectCode==null)fail("RAW_MEMBER_CODE_NULL");var project=byProject.get(member.memberProjectCode.replace("-his",""));if(project==null){c.parameters().contractMemberProjectMissing++;continue;}
     var row=new Source();org.springframework.beans.BeanUtils.copyProperties(contract,row);copyNonNull(new Copy(member,row));copyNonNull(new Copy(group,row));copyNonNull(new Copy(project,row));rows.add(row);if(rows.size()>c.maxRows())fail("JOINED_SOURCE_CAPACITY");
    }
   }canonicalize(rows);return rows;
  }
 }
 record Copy(Source from,Source into){}
 static void copyNonNull(Copy q){var from=new org.springframework.beans.BeanWrapperImpl(q.from);var into=new org.springframework.beans.BeanWrapperImpl(q.into);for(var field:from.getPropertyDescriptors())if(!field.getName().equals("class")&&into.isWritableProperty(field.getName())){var value=from.getPropertyValue(field.getName());if(value!=null)into.setPropertyValue(field.getName(),value);}}

 static void canonicalize(List<Source> rows){
  var groups=new LinkedHashMap<BusinessKey,List<Source>>();var identities=new HashSet<String>();
  for(var s:rows){
   if(s.contractSourceId==null || s.contractSourceId<=0 || s.memberId==null || s.memberId<=0 || s.originalProjectId==null || !"10".equals(s.projectType) || s.contractNo==null || s.contractNo.isBlank() || !Objects.equals(s.projectGroupCode,s.memberProjectGroupCode) || !Objects.equals(s.projectGroupCode,s.groupProjectGroupCode) || !s.memberProjectCode.replace("-his","").equals(s.originalProjectCode) || !identities.add(s.sourceKey()))fail("SOURCE_BRIDGE_IDENTITY_INVALID");
   groups.computeIfAbsent(new BusinessKey(s.originalProjectId,s.contractNo),k->new ArrayList<>()).add(s);
  }
  for(var group:groups.values()){
   boolean active=group.stream().anyMatch(s->!s.memberProjectCode.endsWith("-his"));var candidates=group.stream().filter(s->active!=s.memberProjectCode.endsWith("-his")).toList();
   var created=Comparator.comparing(Source::createdAt,Comparator.nullsFirst(LocalDateTime::compareTo)).thenComparing(s->s.contractSourceId).thenComparing(s->s.memberId);
   var updated=Comparator.comparing(Source::updatedAt,Comparator.nullsFirst(LocalDateTime::compareTo)).thenComparing(s->s.contractSourceId).thenComparing(s->s.memberId);
   var first=candidates.stream().min(created).orElseThrow();var last=candidates.stream().max(updated).orElseThrow();
   for(var s:candidates){if(Objects.equals(s.createdAt(),first.createdAt())&&!Objects.equals(s.createdBy(),first.createdBy()) || Objects.equals(s.updatedAt(),last.updatedAt())&&!Objects.equals(s.updatedBy(),last.updatedBy()))fail("AUDIT_TIME_TIE_WITH_DIFFERENT_ACTOR");}
   if(first.createdAt()==null)fail("ORIGINAL_CREATE_TIME_MISSING");
   for(var s:group){s.canonical=first;s.formalStatus=active?"ACTIVE":"HISTORICAL";s.createdAt=first.createdAt();s.updatedAt=last.updatedAt()==null?first.createdAt():last.updatedAt();s.creatorCode=first.createdBy();s.updaterCode=last.updatedBy();}
  }
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();
  for(var s:b.rows()){s.formalProjectId=null;s.formalContractId=null;s.target=null;s.action=null;s.previousBusinessFingerprint=null;}
  var business=new LinkedHashMap<String,Source>();for(var s:b.rows())business.putIfAbsent(s.businessKey(),s);
  var sourceKeys=b.rows().stream().map(Source::sourceKey).toList();var originals=new HashMap<String,SyncIdentityStore.Identity>();
  for(int i=0;i<sourceKeys.size();i+=500)originals.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("RELATION_ORIGINAL",sourceKeys.subList(i,Math.min(i+500,sourceKeys.size())))));
  var businessKeys=new ArrayList<>(business.keySet());var bindings=new HashMap<String,SyncIdentityStore.Identity>();
  for(int i=0;i<businessKeys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("RELATION_BUSINESS",businessKeys.subList(i,Math.min(i+500,businessKeys.size())))));
  long missingCompanies=0;
  for(var s:business.values()){s.companyCode=s.originalCompanyCode==null?null:cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter.fromSource(s.originalCompanyCode);
   if(s.companyCode==null)missingCompanies++;}
  var targetKeys=new ArrayList<RelationKey>();
  for(var s:business.values()){s.formalProjectId=s.originalProjectId;targetKeys.add(new RelationKey(s.originalProjectId,s.contractNo));}
  var current=new HashMap<RelationKey,Row>();
  for(int i=0;i<targetKeys.size();i+=80)for(var row:mapper.selectRelations(new RelationsQuery(c.tenantId(),targetKeys.subList(i,Math.min(i+80,targetKeys.size())),c.preview()))){if(current.putIfAbsent(new RelationKey(row.projectId,row.contractNo),row)!=null)fail("FORMAL_RELATION_AMBIGUOUS");}
  for(var s:business.values()){
   var old=current.get(new RelationKey(s.formalProjectId,s.contractNo));var binding=bindings.get(s.businessKey());var next=convert(new Plan(s,c));s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){next.contractId=old.contractId;if(binding==null || binding.targetId()!=old.id || !binding.fingerprint().equals(fingerprint(old)) || old.deleted!=0 || !sameFacts(old,next))fail("EXISTING_RELATION_PROTECTED");s.target=old;s.formalContractId=old.contractId;s.previousBusinessFingerprint=binding.fingerprint();}
   else{if(binding!=null)fail("BOUND_RELATION_MISSING");next.id=c.preview()?null:IdWorker.getId();s.target=next;s.action=SyncWriteAction.INSERT;}
  }
  for(var s:b.rows()){
   var chosen=business.get(s.businessKey());
   if(s!=chosen)s.action=SyncWriteAction.UNCHANGED;s.target=chosen.target;
   var previous=originals.get(s.sourceKey());if(previous!=null && (previous.targetId()!=s.target.id || !previous.fingerprint().equals(originalFingerprint(s))))fail("ORIGINAL_RELATION_CHANGED");s.newOriginal=previous==null;
  }
  if(missingCompanies>0)c.runState().warning("P18_COMPANY_SOURCE_PENDING:"+missingCompanies);
  if(c.preview()&&business.values().stream().anyMatch(s->s.target!=null&&s.target.contractId==null))c.runState().warning("P18_CONTRACT_POSTFILL_NOT_EXECUTED");
 }
 record Plan(Source source,SyncContext<Parameters> context){}
 static Row convert(Plan q){var s=q.source;var c=q.context;var r=new Row();r.tenantId=c.tenantId();r.projectId=s.formalProjectId;r.contractId=null;r.contractNo=s.contractNo;r.relationRole="RELATED";r.sourceSystem="DPPMS";r.sourceTable="pm_project_contract";r.sourceRecordKey=s.canonical.sourceKey();r.effectiveFrom=s.createdAt;r.status=s.formalStatus;r.version=0;r.creator=s.resolvedCreator;r.updater=s.resolvedUpdater;r.createTime=s.createdAt;r.updateTime=s.updatedAt;r.deleted=0;return r;}
 static boolean sameFacts(Row a,Row b){return Arrays.asList(a.tenantId,a.projectId,a.contractId,a.contractNo,a.relationRole,a.sourceSystem,a.sourceTable,a.sourceRecordKey,a.effectiveFrom,a.effectiveTo,a.status,a.version,a.creator,a.createTime,a.updater,a.updateTime,a.deleted).equals(Arrays.asList(b.tenantId,b.projectId,b.contractId,b.contractNo,b.relationRole,b.sourceSystem,b.sourceTable,b.sourceRecordKey,b.effectiveFrom,b.effectiveTo,b.status,b.version,b.creator,b.createTime,b.updater,b.updateTime,b.deleted));}
 static String originalFingerprint(Source s){return SyncFingerprint.of(Arrays.asList(s.target.id,s.contractSourceId,s.contractNo,s.projectGroupCode,s.contractCreateTime,s.contractCreateBy,s.contractUpdateTime,s.contractUpdateBy,s.memberId,s.memberProjectGroupCode,s.memberProjectCode,s.mergeBranchMark,s.smsProjectCode,s.memberCreateTime,s.memberCreateBy,s.memberUpdateTime,s.memberUpdateBy,s.groupId,s.groupProjectGroupCode,s.groupProjectGroupName,s.projectType,s.groupCreateTime,s.groupCreateBy,s.groupUpdateTime,s.groupUpdateBy,s.originalProjectId,s.originalProjectCode));}
 static String fingerprint(Row r){return SyncFingerprint.of(Arrays.asList(r.id,r.tenantId,r.projectId,r.contractId,r.contractNo,r.relationRole,r.sourceSystem,r.sourceTable,r.sourceRecordKey,r.effectiveFrom,r.effectiveTo,r.status,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted));}
 static void fail(String code){throw new IllegalArgumentException("P18_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){
  var business=new LinkedHashMap<String,Source>();for(var s:b.rows())business.putIfAbsent(s.businessKey(),s);
  if(business.isEmpty())return;
  var targets=new LinkedHashMap<Long,Source>();for(var s:business.values())if(targets.putIfAbsent(s.target.id,s)!=null)fail("FORMAL_RELATION_AMBIGUOUS");
  var companyTargets=new LinkedHashMap<String,List<Long>>();for(var s:business.values())if(s.companyCode!=null)companyTargets.computeIfAbsent(s.companyCode,k->new ArrayList<>()).add(s.target.id);
  var companies=companyTargets.entrySet().stream().map(e->new CompanyRelationIds(e.getKey(),List.copyOf(e.getValue()))).toList();
  var query=new ContractReferencesQuery(b.context().tenantId(),List.copyOf(targets.keySet()),companies);
  // The raw rows already exist. Fill only their missing references once; never overwrite original text or audit times.
  int filled=mapper.fillContractReferences(query);
  var references=new HashMap<Long,Long>();for(var reference:mapper.selectContractReferences(query)){
   if(!targets.containsKey(reference.id())||references.containsKey(reference.id()))fail("POSTFILL_RELATION_IDENTITY_INVALID");
   references.put(reference.id(),reference.contractId());
  }
  if(references.size()!=targets.size())fail("POSTFILL_RELATION_MISSING");
  for(var s:business.values()){var id=references.get(s.target.id);if(s.target.contractId!=null&&!Objects.equals(s.target.contractId,id))fail("EXISTING_RELATION_PROTECTED");s.target.contractId=id;}
  var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:business.values()){var next=fingerprint(s.target);if(!Objects.equals(next,s.previousBusinessFingerprint))saves.add(new SyncIdentityStore.Save("RELATION_BUSINESS",s.businessKey(),s.target.id,next,s.previousBusinessFingerprint));}
  for(var s:b.rows()){s.formalContractId=s.target.contractId;if(s.newOriginal)saves.add(new SyncIdentityStore.Save("RELATION_ORIGINAL",s.sourceKey(),s.target.id,originalFingerprint(s),null));}
  if(!saves.isEmpty())b.context().identities().saveAll(saves);
  if(filled>0){b.context().runState().add(new SyncCounts(0,0,filled,0,0));b.context().runState().warning("P18_CONTRACT_REFERENCE_FILLED:"+filled);}
  long pending=business.values().stream().filter(s->s.target.contractId==null).count();if(pending>0)b.context().runState().warning("P18_CONTRACT_REFERENCE_PENDING:"+pending);
 }
 @lombok.Data public static class Source implements SyncReferences.SourceAudit {
private Long contractSourceId;
private String contractNo;
private String projectGroupCode;
private LocalDateTime contractCreateTime;
private String contractCreateBy;
private LocalDateTime contractUpdateTime;
private String contractUpdateBy;
private Long memberId;
private String memberProjectGroupCode;
private String memberProjectCode;
private String mergeBranchMark;
private String smsProjectCode;
private LocalDateTime memberCreateTime;
private String memberCreateBy;
private LocalDateTime memberUpdateTime;
private String memberUpdateBy;
private Long groupId;
private String groupProjectGroupCode;
private String groupProjectGroupName;
private String projectType;
private LocalDateTime groupCreateTime;
private String groupCreateBy;
private LocalDateTime groupUpdateTime;
private String groupUpdateBy;
private Long originalProjectId;
private String originalProjectCode;
private String originalCompanyCode,companyCode;
  private Source canonical;private String formalStatus,creatorCode,updaterCode,resolvedCreator,resolvedUpdater,previousBusinessFingerprint;private LocalDateTime createdAt,updatedAt;private Long formalProjectId,formalContractId;private Row target;private SyncWriteAction action;private boolean newOriginal;
  String sourceKey(){return contractSourceId+":"+memberId;}
  String businessKey(){return SyncFingerprint.of(Arrays.asList(originalProjectId,contractNo));}
  LocalDateTime createdAt(){return contractCreateTime==null?memberCreateTime:contractCreateTime;}
  LocalDateTime updatedAt(){return contractUpdateTime==null?memberUpdateTime:contractUpdateTime;}
  String createdBy(){return contractCreateBy==null?memberCreateBy:contractCreateBy;}
  String updatedBy(){return contractUpdateBy==null?memberUpdateBy:contractUpdateBy;}
  public String getSourceCreator(){return creatorCode;}public String getSourceUpdater(){return updaterCode;}
  public Source setResolvedCreator(String value){resolvedCreator=value;return this;}public Source setResolvedUpdater(String value){resolvedUpdater=value;return this;}
  public boolean requiresResolvedAuditReferences(){return true;}public boolean usesAuditCatalogue(){return true;}
 }
 @lombok.Data public static class Row {private Long id;private Long tenantId;private Long projectId;private Long contractId;private String contractNo;private String relationRole;private String sourceSystem;private String sourceTable;private String sourceRecordKey;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String status;private Integer version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Integer deleted;}
 public record BusinessKey(Long sourceProjectId,String contractNo){}
 public record RelationKey(Long projectId,String contractNo){}
 public record RelationsQuery(long tenantId,List<RelationKey> keys,boolean preview){}
 public record CompanyRelationIds(String companyCode,List<Long> targetIds){}
 public record ContractReferencesQuery(long tenantId,List<Long> targetIds,List<CompanyRelationIds> sourceCompanies){}
 public record ContractReference(Long id,Long contractId){}
 public interface SourceMapper {List<Source> selectContracts(SourceQuery<Parameters> query);List<Source> selectGroups(SourceQuery<Parameters> query);List<Source> selectMembers(SourceQuery<Parameters> query);List<Source> selectProjects(SourceQuery<Parameters> query);}
 @Mapper public interface TargetMapper {List<Row> selectRelations(@Param("query") RelationsQuery query);int insertProjectContract(SyncBatch<Source,Parameters> batch);int fillContractReferences(@Param("query") ContractReferencesQuery query);List<ContractReference> selectContractReferences(@Param("query") ContractReferencesQuery query);@com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true",dataPermission="true") int oneSourcePerTargetIndex();}
}
