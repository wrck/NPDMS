package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.*;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Approved historical roots and original aliases, never adoption, restoration, current assignment or tree replacement. */
@Component @RequiredArgsConstructor
public class P16ProjectSyncTask extends AbstractSyncTask<P16ProjectSyncTask.Source,P16ProjectSyncTask.Parameters> {
 private final TargetMapper mapper;private final PermissionCommonApi permissions;private final OrganizationScopeApi organization;private final PlatformBusinessEventApi events;
 public static final class Parameters {
  private SyncSource.Session source;private final Map<String,Directory> directory=new HashMap<>();private final Map<Long,String> companies=new HashMap<>();
  private final Map<OrganizationKey,Boolean> scopes=new HashMap<>();
  private boolean masterPreviewVerified;private long pendingAliases;
 }
 public record OrganizationKey(Long companyId,Long departmentId){}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override public String taskCode(){return "BASE_P16";}
 @Override public String taskName(){return "项目主档及独立来源身份";}
 @Override public Set<String> requiredPermissions(){return Set.of("pms:project:create");}
 @Override public void work(SyncContext<Parameters> c){
  if(mapper.oneSourcePerTargetIndex()>0){if(!c.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");c.runState().warning("P16_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}
  var p=c.parameters();
  for(var d:mapper.selectDirectory(new TenantQuery(c.tenantId(),c.preview())))if(d.code!=null&&!d.code.isBlank()&&p.directory.putIfAbsent(d.kind+":"+d.code,d)!=null)fail("FORMAL_DIRECTORY_AMBIGUOUS");
  try(var session=c.source().open(c.connectionId())){
   p.source=session;var q=new SourceQuery<Parameters>(10001,c.tenantId(),c.preview(),p);var source=session.mapper(SourceMapper.class);
   var companies=source.selectCompanies(q);
   if(companies.size()>10000)fail("REFERENCE_CATALOGUE_CAPACITY");
   for(var company:companies)if(p.companies.putIfAbsent(company.id,company.code)!=null)fail("RAW_COMPANY_AMBIGUOUS");
   syncData(c.forData("ProjectMaster",false,true));p.masterPreviewVerified=true;syncData(c.forData("ProjectAlias",false,true));
   if(p.pendingAliases>0)c.runState().warning("P16_ALIASES_REQUIRE_NEW_MASTERS:"+p.pendingAliases);
  }finally{p.source=null;}
 }
 @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){
  syncDataQueryBySql(c.parameters().source.sqlSession(),new MappedCall<>(c,sourceMapperNamespace()+".select"+c.dataName(),new SourceQuery<>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters())),handler);
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){if(b.context().dataName().equals("ProjectAlias")){prepareAliases(b);return;}
  var c=b.context();var keys=b.rows().stream().map(s->s.projectId.toString()).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_MASTER",keys));var originals=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_ORIGINAL",keys));
  var ids=new ArrayList<>(bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList());ids.addAll(b.rows().stream().map(Source::getProjectId).toList());var current=new HashMap<Long,Row>();var byCode=new HashMap<String,Row>();
  for(var old:mapper.selectProjects(new ProjectsQuery(c.tenantId(),b.rows().stream().map(s->s.projectCode).toList(),ids,c.preview()))){if(!ids.contains(old.id)&&b.rows().stream().noneMatch(s->Objects.equals(s.projectCode,old.projectCode)))fail("PROJECT_CODE_COLLATION_COLLISION");if(current.putIfAbsent(old.id,old)!=null||byCode.putIfAbsent(old.projectCode,old)!=null)fail("FORMAL_PROJECT_IDENTITY_AMBIGUOUS");}
  var sourceCodes=new HashSet<String>();for(var s:b.rows())if(!sourceCodes.add(s.projectCode.toLowerCase(Locale.ROOT)))fail("PROJECT_CODE_CASE_COLLISION");
  var partyBindings=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_FINAL_CUSTOMER",keys));var currentParties=new HashMap<String,PartyRow>();
  for(var row:mapper.selectParties(new PartyQuery(c.tenantId(),keys,c.preview())))if(currentParties.putIfAbsent(row.sourceRecordKey,row)!=null)fail("FINAL_CUSTOMER_IDENTITY_AMBIGUOUS");
  for(var s:b.rows()){
   validate(s);var binding=bindings.get(s.projectId.toString());var original=originals.get(s.projectId.toString());s.masterPrevious=binding==null?null:binding.fingerprint();s.originalPrevious=original==null?null:original.fingerprint();var old=binding==null?null:current.get(binding.targetId());s.action=SyncWriteAction.UNCHANGED;
   if(binding!=null&&binding.targetId()!=s.projectId.longValue())fail("BOUND_PROJECT_ID_DIFFERS_FROM_SOURCE");
   if(old!=null){if(Boolean.TRUE.equals(old.deleted)||binding==null||!binding.fingerprint().equals(fingerprint(old))||original==null||original.targetId()!=old.id||!original.fingerprint().equals(originalFingerprint(s)))fail("EXISTING_PROJECT_OR_SOURCE_PROTECTED");s.target=old;}
   else{
    if(current.containsKey(s.projectId))fail("PROJECT_ID_ALREADY_OWNED");
    if(binding!=null||original!=null||byCode.containsKey(s.projectCode))fail("PROJECT_CODE_ALREADY_OWNED");
    var row=convert(new Conversion(s,c));row.id=s.projectId;row.codeRootId=row.id;row.rootId=row.id;s.target=row;s.action=SyncWriteAction.INSERT;
    s.treeVersionId=c.preview()?null:IdWorker.getId();s.pathId=c.preview()?null:IdWorker.getId();
   }
   var oldParty=currentParties.get(s.projectId.toString());var pb=partyBindings.get(s.projectId.toString());
   if(oldParty!=null){if(pb==null||pb.targetId()!=oldParty.id||!pb.fingerprint().equals(partyFingerprint(oldParty))||!Objects.equals(oldParty.projectId,s.target.id)||!Objects.equals(oldParty.partyName,s.column013))fail("EXISTING_FINAL_CUSTOMER_PROTECTED");s.party=oldParty;}
   else if(pb!=null)fail("BOUND_FINAL_CUSTOMER_MISSING");
   else if(s.column013!=null&&!s.column013.isBlank()){
    if(old!=null)fail("BOUND_FINAL_CUSTOMER_MISSING");
    var party=new PartyRow();party.id=c.preview()?null:IdWorker.getId();party.tenantId=c.tenantId();party.projectId=s.target.id;party.partyRole="FINAL_CUSTOMER";party.partyName=s.column013;party.sourceSystem="DPPMS";party.sourceTable="pm_project_header";party.sourceRecordKey=s.projectId.toString();party.effectiveFrom=s.effectiveFrom;party.effectiveTo=s.effectiveTo;party.status="ACTIVE";party.version=0L;party.deleted=0;party.creator=Long.toString(c.actorId());party.updater=party.creator;party.createTime=c.startedAt();party.updateTime=c.startedAt();s.party=party;s.newParty=true;
   }
  }
 }
 void prepareAliases(SyncBatch<Source,Parameters> b){var c=b.context();var canonical=b.rows().stream().map(s->s.canonicalProjectId.toString()).distinct().toList();var masters=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_MASTER",canonical));var aliases=c.identities().findByKeys(new SyncIdentityStore.Query("PROJECT_ALIAS",b.rows().stream().map(s->s.projectId.toString()).toList()));
  var ids=masters.values().stream().map(SyncIdentityStore.Identity::targetId).toList();var current=new HashMap<Long,Row>();if(!ids.isEmpty())for(var row:mapper.selectProjects(new ProjectsQuery(c.tenantId(),List.of(),ids,c.preview())))current.put(row.id,row);
  for(var s:b.rows()){
   var master=masters.get(s.canonicalProjectId.toString());var old=aliases.get(s.projectId.toString());s.action=SyncWriteAction.UNCHANGED;
   if(master==null){if(!c.preview()||!c.parameters().masterPreviewVerified||old!=null)fail("CANONICAL_MASTER_MISSING");c.parameters().pendingAliases++;continue;}
   s.target=current.get(master.targetId());if(s.target==null||!master.fingerprint().equals(fingerprint(s.target))||!Objects.equals(s.target.projectType,s.projectType))fail("CANONICAL_MASTER_CHANGED");
   if(old!=null&&(old.targetId()!=s.target.id||!old.fingerprint().equals(aliasFingerprint(s))))fail("ORIGINAL_ALIAS_CHANGED");s.newAlias=old==null;s.aliasPrevious=old==null?null:old.fingerprint();
  }
 }
 record Conversion(Source source,SyncContext<Parameters> context){}
 Row convert(Conversion q){var s=q.source;var c=q.context;var p=c.parameters();var row=new Row();row.tenantId=c.tenantId();row.projectCode=s.projectCode;row.projectName=s.projectName==null||s.projectName.isBlank()?s.projectCode:s.projectName;row.projectType=s.projectType;
  row.projectSequence=0L;row.codeRuleVersion="V1";row.treePath="";row.treeDepth=0L;row.treeSort=0L;row.locationResolutionStatus="UNRESOLVED";row.sourceType="MIGRATION";row.assignmentStatus="UNASSIGNED";row.progress=java.math.BigDecimal.ZERO;row.taskTreeVersion=0L;row.taskProgressVersion=0L;row.version=0L;row.deleted=false;
  if(s.compId!=null){var raw=p.companies.get(s.compId);var code=DppmsCompanyCodeConverter.fromSource(raw);var company=p.directory.get("COMPANY:"+code);if(company==null)fail("FORMAL_COMPANY_MISSING:"+code);row.companyId=company.id;row.companyCode=company.code;row.companyName=company.name;}
  String code=s.column001;if(code!=null&&code.matches("31120[2-9]|311210"))code="1621"+code.substring(4);
  if(code!=null){var department=p.directory.get("DEPARTMENT:"+code);if(department==null)fail("FORMAL_DEPARTMENT_MISSING:"+code);row.departmentId=department.id;row.departmentCode=department.code;row.departmentName=department.name;}
  if(s.compId==null||s.column001==null||!p.scopes.computeIfAbsent(new OrganizationKey(row.companyId,row.departmentId),key->organization.hasScope(c.actorId(),key.companyId,key.departmentId))){if(!permissions.hasAnyRoles(c.actorId(),cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode()))throw new org.springframework.security.access.AccessDeniedException("P16_CROSS_ORGANIZATION_IMPORT_FORBIDDEN");}
  row.projectCategory=mapCategory(s.column010);row.signingMethod=mapSigning(s.column011);row.implementationMode=mapImplementation((s.column012==null||s.column012.isEmpty())&&s.columno12_readonly!=null&&s.columno12_readonly!=-1?s.columno12_readonly.toString():s.column012);
  row.customerProjectName=s.customerProjectName;row.salesType=s.salesType;row.majorProjectLevel=s.majorProjectLevel;row.marketName=s.column004;row.systemName=s.column005;row.expendName=s.column006;row.industryName=s.column007;row.notTrackReason=s.column008;
  if("20".equals(s.projectState)&&row.notTrackReason==null)row.notTrackReason="不予跟踪";
  row.projectStartTime=s.projectStartTime;row.projectRefreshTime=s.projectRefreshTime;row.projectCloseTime=s.projectCloseTime;row.orderCreateTime=s.column009;row.customInfo=s.customInfo;row.disabled=s.disabled;row.effectiveFrom=s.effectiveFrom;row.effectiveTo=s.effectiveTo;
  row.creator=s.createBy;row.updater=s.updateBy;checkTextLength(row.creator,64);checkTextLength(row.updater,64);row.createTime=s.createTime;row.updateTime=s.updateTime==null?s.createTime:s.updateTime;
  var state=historicalState(new Historical(s.projectType,s.projectState,s.projectCloseTime));row.lifecycleStatus=state.lifecycleStatus;row.currentStage=state.stage;row.closedAt=state.closedAt;row.status=state.lifecycleStatus;
  return row;
 }
 record Historical(String type,String state,LocalDateTime closedAt){} record State(String lifecycleStatus,String stage,LocalDateTime closedAt){}
 static State historicalState(Historical q){return switch(Objects.toString(q.state,"")){case "100"->new State("NORMAL_CLOSED","S6",q.closedAt);case "20"->{if(!"10".equals(q.type))fail("SOURCE_STATE_UNMAPPED");yield new State("EXCEPTION_CLOSED","S0",q.closedAt);}case "30","31","32"->{if(!"10".equals(q.type))fail("SOURCE_STATE_UNMAPPED");yield new State("ACTIVE","S0",null);}default->throw new IllegalArgumentException("P16_SOURCE_STATE_UNMAPPED");};}
 static String mapCategory(String v){if(v==null)return null;return switch(v){case "10"->"GENERAL";case "20"->"ENGINEERING";default->throw new IllegalArgumentException("P16_CATEGORY_UNMAPPED");};}
 static String mapSigning(String v){if(v==null)return null;return switch(v){case "10"->"DIRECT_SIGN";case "20"->"CHANNEL_SIGN";default->throw new IllegalArgumentException("P16_SIGNING_UNMAPPED");};}
 static String mapImplementation(String v){if(v==null)return null;return switch(v){case "0"->"DIRECT_SERVICE";case "1"->"SUPERVISION";case "2"->"DIRECT_SUBCONTRACT";case "3"->"AGENT_SELF_SERVICE";case "4"->"FACTORY_INTEGRATION";default->throw new IllegalArgumentException("P16_IMPLEMENTATION_UNMAPPED");};}
 static void validate(Source s){if(s.projectId==null||s.projectId<=0||!"10".equals(s.projectType)||s.projectCode==null||s.projectCode.isBlank()||s.createTime==null)fail("SOURCE_HEADER_REQUIRED_FIELDS");if(s.customConfig!=null)fail("NONEMPTY_CUSTOM_CONFIG_REQUIRES_FORMAL_MAPPING");checkTextLength(s.projectCode,64);checkTextLength(s.projectName,200);checkTextLength(s.column013,1024);if(s.effectiveFrom!=null&&s.effectiveTo!=null&&s.effectiveTo.isBefore(s.effectiveFrom))fail("SOURCE_VALIDITY_INTERVAL_INVALID");if(s.createTime.getNano()!=0||s.updateTime!=null&&s.updateTime.getNano()!=0)fail("AUDIT_PRECISION_INVALID");if(s.customInfo!=null)cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(s.customInfo);}
 static String originalFingerprint(Source s){return SyncFingerprint.of(Arrays.asList(s.projectId,s.projectType,s.projectCode,s.projectName,s.projectState,s.isback,s.column001,s.column002,s.column003,s.column004,s.column005,s.column006,s.column007,s.column008,s.column009,s.column010,s.column011,s.column012,s.columno12_readonly,s.column013,s.column014,s.customerProjectName,s.salesType,s.majorProjectLevel,s.compId,s.createTime,s.createBy,s.updateTime,s.updateBy,s.effectiveFrom,s.effectiveTo,s.disabled,s.projectStartTime,s.projectRefreshTime,s.projectCloseTime,s.customInfo,s.customConfig));}
 static String aliasFingerprint(Source s){return SyncFingerprint.of(Arrays.asList(s.projectId,s.canonicalProjectId,s.projectType,s.projectCode,s.target.id));}
 static String fingerprint(Row r){return SyncFingerprint.of(Arrays.asList(r.id,r.projectCode,r.codeRootId,r.projectSequence,r.codeRuleVersion,r.projectName,r.parentId,r.rootId,r.treePath,r.treeDepth,r.treeSort,r.businessLevelCode,r.businessLevelName,r.customerId,r.customerCode,r.customerName,r.managerId,r.managerEmployeeNo,r.managerName,r.companyId,r.companyCode,r.companyName,r.departmentId,r.departmentCode,r.departmentName,r.projectType,r.signingMethod,r.projectCategory,r.implementationMode,r.majorProjectLevel,r.marketCode,r.marketName,r.systemCode,r.systemName,r.expendCode,r.expendName,r.industryCode,r.industryName,r.customerProjectName,r.salesType,r.businessType,r.serviceLevelCode,r.notTrackReason,r.contractNo,r.implementationLocation,r.locationResolutionStatus,r.creationReason,r.lifecycleTemplateId,r.lifecycleTemplateRevisionNo,r.lifecycleTemplateRevisionId,r.templateLoadMethod,r.processDefinitionKey,r.processDefinitionVersion,r.projectStartTime,r.projectRefreshTime,r.projectCloseTime,r.sourceType,r.status,r.progress==null?null:r.progress.stripTrailingZeros(),r.aggregationWeight==null?null:r.aggregationWeight.stripTrailingZeros(),r.weightSource,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted,r.deletedTime,r.tenantId,r.lifecycleStatus,r.currentStage,r.assignmentStatus,r.taskTreeVersion,r.taskProgressVersion,r.projectEndDate,BaseSyncJson.canonicalJson(r.closurePolicySnapshot),r.closureType,r.closedFromStage,r.closedAt,r.activePlanVersionId,BaseSyncJson.canonicalJson(r.customInfo),r.orderCreateTime,r.disabled,r.effectiveFrom,r.effectiveTo));}
 static String partyFingerprint(PartyRow r){return SyncFingerprint.of(Arrays.asList(r.id,r.tenantId,r.projectId,r.partyRole,r.partyCode,r.partyName,r.contactName,r.phone,r.sourceSystem,r.sourceTable,r.sourceRecordKey,r.effectiveFrom,r.effectiveTo,r.status,r.version,r.creator,r.createTime,r.updater,r.updateTime,r.deleted));}
 static void fail(String code){throw new IllegalArgumentException("P16_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){if(b.context().dataName().equals("ProjectAlias"))return;var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);super.syncDataInsert(fresh);
  if(!fresh.rows().isEmpty()){if(mapper.insertTreeVersions(fresh)!=fresh.rows().size()||mapper.insertTreePaths(fresh)!=fresh.rows().size()||mapper.activateTrees(fresh)!=fresh.rows().size())fail("ROOT_TREE_WRITE_CONFLICT");
   for(var s:fresh.rows()){var payload=ProjectRuleReevaluationRequested.create(b.context().tenantId(),s.target.id,null,"PROJECT_TREE:BASE_PROJECT_CREATE:"+s.target.id);events.append("Project",s.target.id.toString(),new BusinessEvent(payload.eventId(),ProjectRuleReevaluationRequested.EVENT_TYPE,cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(payload)));}}
  var parties=b.matching(s->s.newParty);if(!parties.rows().isEmpty()&&mapper.insertParties(parties)!=parties.rows().size())fail("FINAL_CUSTOMER_INSERT_CONFLICT");
 }
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var saves=new ArrayList<SyncIdentityStore.Save>();boolean alias=b.context().dataName().equals("ProjectAlias");for(var s:b.rows()){
  if(alias){saves.add(new SyncIdentityStore.Save("PROJECT_ALIAS",s.projectId.toString(),s.target.id,aliasFingerprint(s),s.aliasPrevious));continue;}
  saves.add(new SyncIdentityStore.Save("PROJECT_MASTER",s.projectId.toString(),s.target.id,fingerprint(s.target),s.masterPrevious));saves.add(new SyncIdentityStore.Save("PROJECT_ORIGINAL",s.projectId.toString(),s.target.id,originalFingerprint(s),s.originalPrevious));
  if(s.newParty)saves.add(new SyncIdentityStore.Save("PROJECT_FINAL_CUSTOMER",s.projectId.toString(),s.party.id,partyFingerprint(s.party),null));
 }b.context().identities().saveAll(saves);}
 @lombok.Data public static class Source {private Long projectId;private String projectType;private String projectCode;private String projectName;private String projectState;private String isback;private String column001;private String column002;private String column003;private String column004;private String column005;private String column006;private String column007;private String column008;private LocalDateTime column009;private String column010;private String column011;private String column012;private Long columno12_readonly;private String column013;private String column014;private String customerProjectName;private String salesType;private String majorProjectLevel;private Long compId;private LocalDateTime createTime;private String createBy;private LocalDateTime updateTime;private String updateBy;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private Boolean disabled;private LocalDateTime projectStartTime;private LocalDateTime projectRefreshTime;private LocalDateTime projectCloseTime;private String customInfo;private String customConfig; private Long canonicalProjectId,treeVersionId,pathId;private Row target;private PartyRow party;private SyncWriteAction action;private boolean newParty,newAlias;private String masterPrevious,originalPrevious,aliasPrevious;}
 @lombok.Data public static class Row {private Long id;private String projectCode;private Long codeRootId;private Long projectSequence;private String codeRuleVersion;private String projectName;private Long parentId;private Long rootId;private String treePath;private Long treeDepth;private Long treeSort;private String businessLevelCode;private String businessLevelName;private Long customerId;private String customerCode;private String customerName;private Long managerId;private String managerEmployeeNo;private String managerName;private Long companyId;private String companyCode;private String companyName;private Long departmentId;private String departmentCode;private String departmentName;private String projectType;private String signingMethod;private String projectCategory;private String implementationMode;private String majorProjectLevel;private String marketCode;private String marketName;private String systemCode;private String systemName;private String expendCode;private String expendName;private String industryCode;private String industryName;private String customerProjectName;private String salesType;private String businessType;private String serviceLevelCode;private String notTrackReason;private String contractNo;private String implementationLocation;private String locationResolutionStatus;private String creationReason;private Long lifecycleTemplateId;private Long lifecycleTemplateRevisionNo;private Long lifecycleTemplateRevisionId;private String templateLoadMethod;private String processDefinitionKey;private String processDefinitionVersion;private LocalDateTime projectStartTime;private LocalDateTime projectRefreshTime;private LocalDateTime projectCloseTime;private String sourceType;private String status;private java.math.BigDecimal progress;private java.math.BigDecimal aggregationWeight;private String weightSource;private Long version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;private LocalDateTime deletedTime;private Long tenantId;private String lifecycleStatus;private String currentStage;private String assignmentStatus;private Long taskTreeVersion;private Long taskProgressVersion;private java.time.LocalDate projectEndDate;private String closurePolicySnapshot;private String closureType;private String closedFromStage;private LocalDateTime closedAt;private Long activePlanVersionId;private String customInfo;private LocalDateTime orderCreateTime;private Boolean disabled;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;}
 @lombok.Data public static class PartyRow {private Long id;private Long tenantId;private Long projectId;private String partyRole;private String partyCode;private String partyName;private String contactName;private String phone;private String sourceSystem;private String sourceTable;private String sourceRecordKey;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String status;private Long version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Integer deleted;}
 @lombok.Data public static class Directory {private Long id;private String kind,code,name;}
 @lombok.Data public static class Company {private Long id;private String code;}
 @lombok.Data public static class Account {private Long id;private String username;}
 @lombok.Data public static class AccountBinding {private Long id;private String kind,sourceKey,username;}
 public record TenantQuery(long tenantId,boolean preview){}public record AccountsQuery(long tenantId,long connectionId){}
 public record ProjectsQuery(long tenantId,List<String> codes,List<Long> ids,boolean preview){}public record PartyQuery(long tenantId,List<String> keys,boolean preview){}
 public interface SourceMapper {List<Company> selectCompanies(SourceQuery<Parameters> query);List<Account> selectFndAccounts(SourceQuery<Parameters> query);List<Account> selectLegacyAccounts(SourceQuery<Parameters> query);void selectProjectMaster(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);void selectProjectAlias(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper {List<Directory> selectDirectory(@Param("query") TenantQuery query);List<AccountBinding> selectAccountBindings(@Param("query") AccountsQuery query);List<Row> selectProjects(@Param("query") ProjectsQuery query);List<PartyRow> selectParties(@Param("query") PartyQuery query);int insertProjectMaster(SyncBatch<Source,Parameters> batch);int insertTreeVersions(SyncBatch<Source,Parameters> batch);int insertTreePaths(SyncBatch<Source,Parameters> batch);int activateTrees(SyncBatch<Source,Parameters> batch);int insertParties(SyncBatch<Source,Parameters> batch);@com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true",dataPermission="true") int oneSourcePerTargetIndex();}
}
