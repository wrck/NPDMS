package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Source SQL owns scope and normalization; the task protects and writes the formal directory. */
@Component @RequiredArgsConstructor
public class P01OrganizationSyncTask extends AbstractSyncTask<P01OrganizationSyncTask.Source,P01OrganizationSyncTask.Parameters> {
 private final TargetMapper mapper;
 public static final class Parameters {private final List<Source> sources;private final Map<String,Row> current;private final List<String> warnings;
  Parameters(List<Source> sources,Map<String,Row> current,List<String> warnings){this.sources=sources;this.current=current;this.warnings=List.copyOf(warnings);}}
 @Override public String taskCode(){return "BASE_P01";}
 @Override public String taskName(){return "公司与部门独立目录";}
 @Override public Set<String> requiredPermissions(){return Set.of("system:dept:create");}
 @Override public void work(SyncContext<Parameters> c){c.parameters().warnings.forEach(c.runState()::warning);syncData(c.forData("Organization",false,false));}
 @Override protected Parameters createParameters(SyncContext<?> c){
  var query=new SourceQuery<Void>(c.maxRows()+1,c.tenantId(),c.preview(),null);
  var sources=new ArrayList<Source>();
  try(var source=c.source().open(c.connectionId())){
   var m=source.mapper(SourceMapper.class);sources.addAll(m.selectCompanies(query));sources.addAll(m.selectDepartments(query));
  }
  if(sources.size()>c.maxRows())fail("RAW_DIRECTORY_CAPACITY");
  var current=new HashMap<String,Row>();var targetQuery=new CatalogueQuery(c.tenantId(),c.preview());
  var formal=new ArrayList<>(mapper.selectCompanies(targetQuery));formal.addAll(mapper.selectDepartments(targetQuery));
  for(var row:formal)if(row.code!=null && current.putIfAbsent(row.kind+":"+row.code,row)!=null)fail("FORMAL_CODE_AMBIGUOUS");
  var unique=new LinkedHashMap<String,Source>();
  for(var row:sources){validateAndIndex(unique,row);if(row.warning!=null)c.runState().warning(row.warning);}
  return new Parameters(new ArrayList<>(unique.values()),current,c.runState().warnings());
 }
 @Override protected List<Source> syncDataQuery(SyncContext<Parameters> c){return new ArrayList<>(c.parameters().sources);}
 static void validateAndIndex(Map<String,Source> unique,Source s){
  if(s.sourceIssue!=null)fail(s.sourceIssue);
  if(!Set.of("COMPANY","DEPARTMENT").contains(Objects.toString(s.kind,"")) || s.sourceId==null || s.sourceId.isBlank())fail("SOURCE_IDENTITY_INVALID");
  if(s.status==null || s.status!=0&&s.status!=1)fail("STATUS_UNMAPPED");
  if(s.code==null || s.code.isBlank() || s.name==null || s.name.isBlank())fail("ORGANIZATION_REQUIRED_FIELDS");checkTextLength(s.code,64);checkTextLength(s.name,"COMPANY".equals(s.kind)?128:30);
  if(s.historical && (s.createTime==null || s.createTime.getNano()!=0 || s.updateTime!=null && s.updateTime.getNano()!=0))fail("HISTORICAL_AUDIT_INVALID");
  if(unique.putIfAbsent(s.kind+":"+s.code,s)!=null)fail("WHOLE_DEFINITION_SAME_PRIORITY_AMBIGUOUS");
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var byCode=new LinkedHashMap<String,Source>();for(var s:b.rows())byCode.put(s.kind+":"+s.code,s);
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();
  for(var kind:List.of("COMPANY","DEPARTMENT")){var selected=b.rows().stream().filter(s->kind.equals(s.kind)).map(s->s.code).toList();if(!selected.isEmpty())bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query(kind,selected)).entrySet().stream().collect(java.util.stream.Collectors.toMap(e->kind+":"+e.getKey(),Map.Entry::getValue)));}
  var ordered=new ArrayList<Source>();var visited=new HashSet<String>();
  for(var s:b.rows())resolve(new Resolution(s,c,byCode,bindings,visited,new HashSet<>(),ordered));
  b.rows().clear();b.rows().addAll(ordered);
 }
 record Resolution(Source source,SyncContext<Parameters> context,Map<String,Source> sources,Map<String,SyncIdentityStore.Identity> bindings,Set<String> visited,Set<String> visiting,List<Source> ordered){}
 static Row resolve(Resolution q){
  var s=q.source;var c=q.context;String key=s.kind+":"+s.code;if(q.visited.contains(key))return s.target;if(!q.visiting.add(key))fail("DEPARTMENT_CYCLE");
  var old=c.parameters().current.get(key);var binding=q.bindings.get(key);s.action=SyncWriteAction.UNCHANGED;
  if(old!=null){if(binding!=null && (binding.targetId()!=old.id || !binding.fingerprint().equals(fingerprint(old))))fail("FORMAL_DIRECTORY_MAINTAINED");s.target=old;}
  else{
   if(binding!=null)fail("BOUND_DIRECTORY_MISSING");var row=new Row();row.kind=s.kind;row.code=s.code;row.name=s.name;row.status=s.status;row.tenantId=c.tenantId();row.id=c.preview()?null:IdWorker.getId();row.version=0;row.deleted=false;
   row.creator=s.historical?null:Long.toString(c.actorId());row.updater=row.creator;row.createTime=s.historical?s.createTime:c.startedAt().withNano(0);row.updateTime=s.historical?(s.updateTime==null?s.createTime:s.updateTime):row.createTime;
   if("DEPARTMENT".equals(s.kind)){
    if(s.companyCode!=null && !q.sources.containsKey("COMPANY:"+s.companyCode) && !c.parameters().current.containsKey("COMPANY:"+s.companyCode))fail("FORMAL_COMPANY_MISSING");
    row.parentId=0L;row.sort=0;
    if(s.parentCode!=null){String pk="DEPARTMENT:"+s.parentCode;var source=q.sources.get(pk);var parent=source==null?c.parameters().current.get(pk):resolve(new Resolution(source,c,q.sources,q.bindings,q.visited,q.visiting,q.ordered));if(parent==null || parent.status!=0)fail("EXACT_ACTIVE_PARENT_MISSING:"+s.parentCode);row.parentId=parent.id;}
   }
   s.target=row;s.action=SyncWriteAction.INSERT;
  }
  q.visiting.remove(key);q.visited.add(key);q.ordered.add(s);return s.target;
 }
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){
  var companies=b.matching(s->"COMPANY".equals(s.kind)&&s.action==SyncWriteAction.INSERT);var departments=b.matching(s->"DEPARTMENT".equals(s.kind)&&s.action==SyncWriteAction.INSERT);
  if(!companies.rows().isEmpty() && mapper.insertCompanies(companies)!=companies.rows().size())fail("COMPANY_INSERT_CONFLICT");
  if(!departments.rows().isEmpty() && mapper.insertDepartments(departments)!=departments.rows().size())fail("DEPARTMENT_INSERT_CONFLICT");
 }
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save(s.kind,s.code,s.target.id,fingerprint(s.target),null)).toList());}
 static String fingerprint(Row row){return SyncFingerprint.of(List.of(JsonUtils.toJsonString(row)));}
 static void fail(String code){throw new IllegalArgumentException("P01_"+code);}
 @lombok.Data public static class Source {private String kind,code,name,companyCode,parentCode,sourceId,sourceIssue,warning;private Integer status;private boolean historical;private LocalDateTime createTime,updateTime;private Row target;private SyncWriteAction action;}
 @lombok.Data public static class Row {private Long id,tenantId,parentId,leaderUserId;private String kind,code,name,phone,email,creator,updater;private Integer sort,status,version;private LocalDateTime createTime,updateTime;private Boolean deleted;}
 public record CatalogueQuery(long tenantId,boolean preview){}
 public interface SourceMapper {List<Source> selectCompanies(SourceQuery<Void> query);List<Source> selectDepartments(SourceQuery<Void> query);}
 @Mapper public interface TargetMapper {List<Row> selectCompanies(@Param("query") CatalogueQuery query);List<Row> selectDepartments(@Param("query") CatalogueQuery query);int insertCompanies(SyncBatch<Source,Parameters> batch);int insertDepartments(SyncBatch<Source,Parameters> batch);}
}
