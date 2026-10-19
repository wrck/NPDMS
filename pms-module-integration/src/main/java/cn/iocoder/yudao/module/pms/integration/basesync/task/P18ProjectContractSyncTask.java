package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Flat raw relation read; canonical creation/update facts are selected once in the full source snapshot. */
@Component @RequiredArgsConstructor
public class P18ProjectContractSyncTask extends AbstractSyncTask<P18ProjectContractSyncTask.Source,P18ProjectContractSyncTask.Parameters> {
 private final TargetMapper mapper;private final BaseSyncProjectReferences projects;private final OrganizationScopeApi organization;
 public record Parameters(Set<String> companies){}
 @Override protected Parameters createParameters(SyncContext<?> c){var scopes=organization.getActiveScopes(c.actorId());if(scopes==null || scopes.isEmpty())fail("AUTHORIZED_COMPANIES_EMPTY");return new Parameters(scopes.stream().filter(s->s.getId()!=null&&s.getVersion()!=null).map(s->s.getCompanyCode()).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));}
 @Override public String taskCode(){return "BASE_P18";}
 @Override public String taskName(){return "项目合同关系历史";}
 @Override public Set<String> requiredPermissions(){return Set.of("pms:commerce:contract:relate");}
 @Override public void work(SyncContext<Parameters> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");syncData(c.forData("ProjectContract",false,false));}
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
   for(var contract:contracts){var group=byGroup.get(contract.projectGroupCode);if(group==null)continue;
    for(var member:byMemberGroup.getOrDefault(contract.projectGroupCode,List.of())){if(member.memberProjectCode==null)fail("RAW_MEMBER_CODE_NULL");var project=byProject.get(member.memberProjectCode.replace("-his",""));if(project==null)continue;
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
  var c=b.context();var canonical=b.rows().stream().map(s->s.originalProjectId.toString()).distinct().toList();var refs=new HashMap<String,BaseSyncProjectReferences.Identity>();
  for(int i=0;i<canonical.size();i+=1000)refs.putAll(projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),canonical.subList(i,Math.min(i+1000,canonical.size())),c.preview())));
  var business=new LinkedHashMap<String,Source>();for(var s:b.rows())business.putIfAbsent(s.businessKey(),s);
  var sourceKeys=b.rows().stream().map(Source::sourceKey).toList();var originals=new HashMap<String,SyncIdentityStore.Identity>();
  for(int i=0;i<sourceKeys.size();i+=500)originals.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("RELATION_ORIGINAL",sourceKeys.subList(i,Math.min(i+500,sourceKeys.size())))));
  var businessKeys=new ArrayList<>(business.keySet());var bindings=new HashMap<String,SyncIdentityStore.Identity>();
  for(int i=0;i<businessKeys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("RELATION_BUSINESS",businessKeys.subList(i,Math.min(i+500,businessKeys.size())))));
  var contractKeys=new ArrayList<ContractKey>();
  for(var s:business.values()){var p=refs.get(s.originalProjectId.toString());s.companyCode=s.originalCompanyCode==null?p.companyCode():cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter.fromSource(s.originalCompanyCode);
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   if(p.companyCode()!=null&&!Objects.equals(p.companyCode(),s.companyCode))fail("PROJECT_COMPANY_DIFFERS_FROM_SOURCE");
   if(s.companyCode==null || !c.parameters().companies.contains(s.companyCode))fail("PROJECT_COMPANY_NOT_AUTHORIZED");contractKeys.add(new ContractKey(s.companyCode,s.contractNo));}
  var contracts=new HashMap<ContractKey,Contract>();
  for(int i=0;i<contractKeys.size();i+=60)for(var contract:mapper.selectContracts(new ContractsQuery(c.tenantId(),contractKeys.subList(i,Math.min(i+60,contractKeys.size())),c.preview()))){var key=new ContractKey(contract.companyCode,contract.contractNo);if(!contractKeys.contains(key) || contract.deleted!=0 || contracts.putIfAbsent(key,contract)!=null)fail("CONTRACT_NOT_EXACTLY_UNIQUE");}
  var targetKeys=new ArrayList<RelationKey>();
  for(var s:business.values()){var p=refs.get(s.originalProjectId.toString());var contract=contracts.get(new ContractKey(s.companyCode,s.contractNo));if(contract==null)fail("FORMAL_CONTRACT_MISSING:"+s.contractNo);s.formalProjectId=p.id();s.formalContractId=contract.id;targetKeys.add(new RelationKey(p.id(),contract.id));}
  var current=new HashMap<RelationKey,Row>();
  for(int i=0;i<targetKeys.size();i+=80)for(var row:mapper.selectRelations(new RelationsQuery(c.tenantId(),targetKeys.subList(i,Math.min(i+80,targetKeys.size())),c.preview()))){if(current.putIfAbsent(new RelationKey(row.projectId,row.contractId),row)!=null)fail("FORMAL_RELATION_AMBIGUOUS");}
  for(var s:business.values()){
   var old=current.get(new RelationKey(s.formalProjectId,s.formalContractId));var binding=bindings.get(s.businessKey());var next=convert(new Plan(s,c));s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){if(binding==null || binding.targetId()!=old.id || !binding.fingerprint().equals(fingerprint(old)) || old.deleted!=0 || !sameFacts(old,next))fail("EXISTING_RELATION_PROTECTED");s.target=old;}
   else{if(binding!=null)fail("BOUND_RELATION_MISSING");next.id=c.preview()?null:IdWorker.getId();s.target=next;s.action=SyncWriteAction.INSERT;}
  }
  for(var s:b.rows()){
   var chosen=business.get(s.businessKey());if(s!=chosen)s.action=SyncWriteAction.UNCHANGED;s.target=chosen.target;
   var previous=originals.get(s.sourceKey());if(previous!=null && (previous.targetId()!=s.target.id || !previous.fingerprint().equals(originalFingerprint(s))))fail("ORIGINAL_RELATION_CHANGED");s.newOriginal=previous==null;
  }
 }
 record Plan(Source source,SyncContext<Parameters> context){}
 static Row convert(Plan q){var s=q.source;var c=q.context;var r=new Row();r.tenantId=c.tenantId();r.projectId=s.formalProjectId;r.contractId=s.formalContractId;r.relationRole="RELATED";r.sourceSystem="DPPMS";r.sourceTable="pm_project_contract";r.sourceRecordKey=s.canonical.sourceKey();r.effectiveFrom=s.createdAt;r.status=s.formalStatus;r.version=0;r.creator=s.resolvedCreator;r.updater=s.resolvedUpdater;r.createTime=s.createdAt;r.updateTime=s.updatedAt;r.deleted=0;return r;}
 static boolean sameFacts(Row a,Row b){return Arrays.asList(a.tenantId,a.projectId,a.contractId,a.relationRole,a.sourceSystem,a.sourceTable,a.sourceRecordKey,a.effectiveFrom,a.effectiveTo,a.status,a.version,a.creator,a.createTime,a.updater,a.updateTime,a.deleted).equals(Arrays.asList(b.tenantId,b.projectId,b.contractId,b.relationRole,b.sourceSystem,b.sourceTable,b.sourceRecordKey,b.effectiveFrom,b.effectiveTo,b.status,b.version,b.creator,b.createTime,b.updater,b.updateTime,b.deleted));}
 static String originalFingerprint(Source s){return SyncFingerprint.of(Arrays.asList(s.target.id,s.contractSourceId,s.contractNo,s.projectGroupCode,s.contractCreateTime,s.contractCreateBy,s.contractUpdateTime,s.contractUpdateBy,s.memberId,s.memberProjectGroupCode,s.memberProjectCode,s.mergeBranchMark,s.smsProjectCode,s.memberCreateTime,s.memberCreateBy,s.memberUpdateTime,s.memberUpdateBy,s.groupId,s.groupProjectGroupCode,s.groupProjectGroupName,s.projectType,s.groupCreateTime,s.groupCreateBy,s.groupUpdateTime,s.groupUpdateBy,s.originalProjectId,s.originalProjectCode));}
 static String fingerprint(Row r){return SyncFingerprint.of(Arrays.asList(r.id,r.tenantId,r.projectId,r.contractId,r.relationRole,r.sourceSystem,r.sourceTable,r.sourceRecordKey,r.effectiveFrom,r.effectiveTo,r.status,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted));}
 static void fail(String code){throw new IllegalArgumentException("P18_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();for(var s:b.rows()){if(s.action==SyncWriteAction.INSERT)saves.add(new SyncIdentityStore.Save("RELATION_BUSINESS",s.businessKey(),s.target.id,fingerprint(s.target),null));if(s.newOriginal)saves.add(new SyncIdentityStore.Save("RELATION_ORIGINAL",s.sourceKey(),s.target.id,originalFingerprint(s),null));}b.context().identities().saveAll(saves);}
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
  private Source canonical;private String formalStatus,creatorCode,updaterCode,resolvedCreator,resolvedUpdater;private LocalDateTime createdAt,updatedAt;private Long formalProjectId,formalContractId;private Row target;private SyncWriteAction action;private boolean newOriginal;
  String sourceKey(){return contractSourceId+":"+memberId;}
  String businessKey(){return SyncFingerprint.of(Arrays.asList(originalProjectId,contractNo));}
  LocalDateTime createdAt(){return contractCreateTime==null?memberCreateTime:contractCreateTime;}
  LocalDateTime updatedAt(){return contractUpdateTime==null?memberUpdateTime:contractUpdateTime;}
  String createdBy(){return nonempty(contractCreateBy)==null?nonempty(memberCreateBy):contractCreateBy;}
  String updatedBy(){return nonempty(contractUpdateBy)==null?nonempty(memberUpdateBy):contractUpdateBy;}
  static String nonempty(String value){return value==null||value.isEmpty()?null:value;}
  static boolean exempt(String value){return Set.of("sys","异常数据处理").contains(Objects.toString(value,""));}
  public String getSourceCreator(){return exempt(creatorCode)?null:creatorCode;}public String getSourceUpdater(){return exempt(updaterCode)?null:updaterCode;}
  public Source setResolvedCreator(String value){resolvedCreator=exempt(creatorCode)?creatorCode:value;return this;}public Source setResolvedUpdater(String value){resolvedUpdater=exempt(updaterCode)?updaterCode:value;return this;}
  public boolean requiresResolvedAuditReferences(){return true;}public boolean usesAuditCatalogue(){return true;}
 }
 @lombok.Data public static class Row {private Long id;private Long tenantId;private Long projectId;private Long contractId;private String relationRole;private String sourceSystem;private String sourceTable;private String sourceRecordKey;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String status;private Integer version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Integer deleted;}
 @lombok.Data public static class Contract {private Long id;private String companyCode,contractNo;private Integer deleted;}
 public record BusinessKey(Long sourceProjectId,String contractNo){}
 public record ContractKey(String companyCode,String contractNo){}
 public record RelationKey(Long projectId,Long contractId){}
 public record ContractsQuery(long tenantId,List<ContractKey> keys,boolean preview){}
 public record RelationsQuery(long tenantId,List<RelationKey> keys,boolean preview){}
 public interface SourceMapper {List<Source> selectContracts(SourceQuery<Parameters> query);List<Source> selectGroups(SourceQuery<Parameters> query);List<Source> selectMembers(SourceQuery<Parameters> query);List<Source> selectProjects(SourceQuery<Parameters> query);}
 @Mapper public interface TargetMapper {List<Contract> selectContracts(@Param("query") ContractsQuery query);List<Row> selectRelations(@Param("query") RelationsQuery query);int insertProjectContract(SyncBatch<Source,Parameters> batch);@com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true",dataPermission="true") int oneSourcePerTargetIndex();}
}
