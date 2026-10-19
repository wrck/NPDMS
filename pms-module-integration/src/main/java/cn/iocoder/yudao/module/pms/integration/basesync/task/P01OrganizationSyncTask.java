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

/** Complete approved organization snapshot; existing formal definitions remain authoritative. */
@Component @RequiredArgsConstructor
public class P01OrganizationSyncTask extends AbstractSyncTask<P01OrganizationSyncTask.Source,P01OrganizationSyncTask.Parameters> {
 private final TargetMapper mapper;
 private static final Set<String> FND=Set.of("160300","160700002","162028","162029","163120","163140","163220","163230","163240","163320",
            "165030","165040","165050","167060","280700","310500001","310500001001","310500001002","310500001003","310500001004","310500001005",
            "310500001006","310500001007","310500001008","310500002","310500002001","310500002002","999999","163130");
 private static final Set<String> COMPLETION=Set.of("167010","167020","167050","162007003","162012003","162022003","310409001","310422001");
 public static final class Parameters {private final List<Source> sources;private final Map<String,Row> current;private final List<String> warnings;
  Parameters(List<Source> sources,Map<String,Row> current,List<String> warnings){this.sources=sources;this.current=current;this.warnings=List.copyOf(warnings);}}
 @Override public String taskCode(){return "BASE_P01";}
 @Override public String taskName(){return "公司与部门独立目录";}
 @Override public Set<String> requiredPermissions(){return Set.of("system:dept:create");}
 @Override public void work(SyncContext<Parameters> c){c.parameters().warnings.forEach(c.runState()::warning);syncData(c.forData("Organization",false,false));}
 @Override protected Parameters createParameters(SyncContext<?> c){
  var query=new SourceQuery<Void>(Math.min(c.maxRows(),10000)+1,c.tenantId(),c.preview(),null);
  List<Company> companies;List<Department> departments;List<Fnd> fnd;List<CompanyAlias> aliases;
  try(var source=c.source().open(c.connectionId())){
   var m=source.mapper(SourceMapper.class);companies=m.selectCompanies(query);departments=m.selectDepartments(query);fnd=m.selectFnd(query);aliases=m.selectCompanyAliases(query);
  }
  if(companies.size()+departments.size()+fnd.size()+aliases.size()>10000)fail("RAW_DIRECTORY_CAPACITY");
  var current=new HashMap<String,Row>();var targetQuery=new CatalogueQuery(c.tenantId(),c.preview());
  var formal=new ArrayList<>(mapper.selectCompanies(targetQuery));formal.addAll(mapper.selectDepartments(targetQuery));
  for(var row:formal)if(row.code!=null && current.putIfAbsent(row.kind+":"+row.code,row)!=null)fail("FORMAL_CODE_AMBIGUOUS");
  var planned=plan(new Input(companies,departments,fnd,aliases,c));
  return new Parameters(planned,current,c.runState().warnings());
 }
 @Override protected List<Source> syncDataQuery(SyncContext<Parameters> c){return new ArrayList<>(c.parameters().sources);}
 public record Input(List<Company> companies,List<Department> departments,List<Fnd> fnd,List<CompanyAlias> aliases,SyncContext<?> context){}
 static List<Source> plan(Input input){
  var companies=new HashMap<Long,Company>();var departments=new HashMap<Long,Department>();var fnd=new HashMap<String,Fnd>();
  var winners=new LinkedHashMap<String,Source>();var completion=new HashSet<String>();
  for(var c:input.companies){if(c.id==null || companies.putIfAbsent(c.id,c)!=null)fail("COMPANY_SOURCE_DUPLICATE");var s=new Source();s.kind="COMPANY";s.code=trim(c.code);s.name=trim(c.name);s.status=status(c.disabled);s.rank=0;s.sourceId=c.id.toString();choose(winners,s);}
  for(var d:input.departments)if(d.id==null || departments.putIfAbsent(d.id,d)!=null)fail("DEPARTMENT_SOURCE_DUPLICATE");
  for(var f:input.fnd)if(FND.contains(f.code)||Set.of("167010","167020","167050").contains(f.code)){if(fnd.putIfAbsent(f.code,f)!=null)fail("FND_SOURCE_AMBIGUOUS");}
  for(var d:input.departments){
   boolean base=d.grade!=null && d.grade.compareTo("2")<=0 && d.code!=null && d.code.length()==6;
   boolean extra=COMPLETION.contains(d.code);if(!base&&!extra)continue;
   if(extra && !completion.add(d.code))fail("COMPLETION_DUPLICATE");
   var s=new Source();s.kind="DEPARTMENT";s.code=d.code;s.name=d.name;s.status=status(d.disabled);s.rank=base?0:1;s.sourceId=d.id.toString();
   if(d.companyId!=null && d.companyId!=0){var company=companies.get(d.companyId);if(company==null)fail("RAW_COMPANY_REFERENCE_MISSING");s.companyCode=trim(company.code);}
   if(d.parentId!=null && d.parentId!=0){var parent=departments.get(d.parentId);if(parent==null)fail("RAW_PARENT_REFERENCE_MISSING");s.parentCode=parent.code;}
   if(extra){
    for(var value:Arrays.asList(d.property,d.number,d.sortOrder,d.remark,d.custom1,d.custom3,d.custom5))if(value!=null)fail("REVIEWED_EMPTY_FIELD_NOW_PRESENT");
    if(Set.of("167010","167020","167050").contains(d.code)){var f=fnd.get(d.code);if(f==null)fail("FND_HISTORY_REQUIRED");s.name=f.name;s.status=fndStatus(f.status);s.parentCode="160000";s.createTime=f.createTime;s.updateTime=f.updateTime;s.historical=true;}
    if(s.parentCode==null)fail("COMPLETION_PARENT_MISSING");
    if(d.director!=null)input.context.runState().warning("P01_DIRECTOR_ASSOCIATION_PENDING:"+d.code);
   }
   choose(winners,s);
  }
  if(!completion.equals(COMPLETION))fail("EIGHT_COMPLETION_DEPARTMENTS_MISSING");
  for(var code:FND){var f=fnd.get(code);if(f==null)fail("FND_APPROVED_CODE_MISSING:"+code);
   var s=new Source();s.kind="DEPARTMENT";s.code=code;s.name=f.name;s.status=fndStatus(f.status);s.rank=2;s.sourceId=f.id.toString();s.historical=true;s.createTime=f.createTime;s.updateTime=f.updateTime;
   s.parentCode=code.startsWith("16")?"160000":code.startsWith("31")?"310000":"280700".equals(code)?"280000":"999999".equals(code)?null:null;
   var expected=Map.of("160700002",541L,"310500001",391L).get(code);
   if(expected!=null){var d=departments.get(expected);if(d==null || !code.equals(d.code) || !Integer.valueOf(1).equals(d.disabled))fail("APPROVED_DISABLED_EXCEPTION_CHANGED");s.status=1;}
   choose(winners,s);
  }
  // Company aliases only validate the already approved company conversion; no temporary company is made.
  for(var alias:input.aliases){String code=companyCode(alias.code);if(!winners.containsKey("COMPANY:"+code))fail("COMPANY_ALIAS_UNRESOLVED");}
  return new ArrayList<>(winners.values());
 }
 static void choose(Map<String,Source> winners,Source s){
  if(s.code==null || s.code.isBlank() || s.name==null || s.name.isBlank())fail("ORGANIZATION_REQUIRED_FIELDS");checkTextLength(s.code,64);checkTextLength(s.name,"COMPANY".equals(s.kind)?128:30);
  if(s.historical && (s.createTime==null || s.createTime.getNano()!=0 || s.updateTime!=null && s.updateTime.getNano()!=0))fail("HISTORICAL_AUDIT_INVALID");
  var old=winners.get(s.kind+":"+s.code);if(old==null || s.rank<old.rank)winners.put(s.kind+":"+s.code,s);
  else if(s.rank==old.rank && !Objects.equals(s.sourceId,old.sourceId))fail("WHOLE_DEFINITION_SAME_PRIORITY_AMBIGUOUS");
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
 static String trim(String s){return s==null?null:s.trim();}
 static int status(Integer s){if(s==null)return 0;if(s==0||s==1)return s;throw new IllegalArgumentException("P01_STATUS_UNMAPPED");}
 static int fndStatus(Integer s){if(s==null || s!=0&&s!=1)fail("FND_STATUS_UNMAPPED");return s==1?0:1;}
 static String companyCode(String s){if(s==null)fail("COMPANY_ALIAS_NULL");return switch(s){case "01"->"001";case "02"->"002";case "03"->"003";case "04"->"004";default->throw new IllegalArgumentException("P01_COMPANY_ALIAS_UNMAPPED");};}
 static void fail(String code){throw new IllegalArgumentException("P01_"+code);}
 @lombok.Data public static class Source {private String kind,code,name,companyCode,parentCode,sourceId;private int rank,status;private boolean historical;private LocalDateTime createTime,updateTime;private Row target;private SyncWriteAction action;}
 @lombok.Data public static class Row {private Long id,tenantId,parentId,leaderUserId;private String kind,code,name,phone,email,creator,updater;private Integer sort,status,version;private LocalDateTime createTime,updateTime;private Boolean deleted;}
 @lombok.Data public static class Company {private Long id;private String code,name;private Integer disabled;}
 @lombok.Data public static class Department {private Long id,companyId,parentId,director;private String code,name,grade,property,number,sortOrder,remark,custom1,custom3,custom5;private Integer disabled;}
 @lombok.Data public static class Fnd {private Long id;private String code,name;private Integer status;private LocalDateTime createTime,updateTime,effectiveFrom,effectiveTo;}
 @lombok.Data public static class CompanyAlias {private Long id;private String code;}
 public record CatalogueQuery(long tenantId,boolean preview){}
 public interface SourceMapper {List<Company> selectCompanies(SourceQuery<Void> query);List<Department> selectDepartments(SourceQuery<Void> query);List<Fnd> selectFnd(SourceQuery<Void> query);List<CompanyAlias> selectCompanyAliases(SourceQuery<Void> query);}
 @Mapper public interface TargetMapper {List<Row> selectCompanies(@Param("query") CatalogueQuery query);List<Row> selectDepartments(@Param("query") CatalogueQuery query);int insertCompanies(SyncBatch<Source,Parameters> batch);int insertDepartments(SyncBatch<Source,Parameters> batch);}
}
