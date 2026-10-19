package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Full additional-product list, with raw quantities and unique upstream facts; no device allocation. */
@Component @RequiredArgsConstructor
public class P20RealProductSyncTask extends AbstractSyncTask<P20RealProductSyncTask.Source,Void> {
 private final TargetMapper mapper;private final BaseSyncProjectReferences projects;
 @Override public String taskCode(){return "BASE_P20";}
 @Override public String taskName(){return "项目真实产品历史清单";}
 @Override public void work(SyncContext<Void> c){if(c.preview())c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");syncData(c.forData("RealProduct",false,false));}
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Void> b){
  var c=b.context();var references=new HashMap<String,BaseSyncProjectReferences.Identity>();
  var canonical=b.rows().stream().map(s->Objects.toString(s.canonicalProjectId,null)).distinct().toList();
  for(int i=0;i<canonical.size();i+=1000)references.putAll(projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),canonical.subList(i,Math.min(i+1000,canonical.size())),c.preview())));
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();var sourceKeys=b.rows().stream().map(s->s.sourceId.toString()).toList();
  for(int i=0;i<sourceKeys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("REAL_PRODUCT",sourceKeys.subList(i,Math.min(i+500,sourceKeys.size())))));
  var ids=references.values().stream().map(BaseSyncProjectReferences.Identity::id).distinct().toList();var current=new HashMap<Long,Row>();var occupied=new HashSet<Long>();
  for(int i=0;i<ids.size();i+=500)for(var row:mapper.selectProjects(new ProjectsQuery(c.tenantId(),ids.subList(i,Math.min(i+500,ids.size())),c.preview()))){if(current.putIfAbsent(row.id,row)!=null || current.size()>c.maxRows())fail("TARGET_SCOPE_TOO_LARGE_OR_DUPLICATE");occupied.add(row.projectId);}
  var sourceBusiness=new HashSet<String>();var sourceIds=new HashSet<Long>();
  for(var s:b.rows()){
   validate(s);if(!sourceIds.add(s.sourceId))fail("SOURCE_ID_DUPLICATE");var p=references.get(s.canonicalProjectId.toString());
   if(!s.projectType.equals(p.projectType()))fail("PROJECT_TYPE_CHANGED");
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   var row=convert(s);row.projectId=p.id();row.tenantId=c.tenantId();
   if(!sourceBusiness.add(business(row)))fail("SOURCE_BUSINESS_DUPLICATE");
   var binding=bindings.get(s.sourceId.toString());s.action=SyncWriteAction.UNCHANGED;
   if(binding!=null){var old=current.get(binding.targetId());if(old==null || Boolean.TRUE.equals(old.deleted) || !binding.fingerprint().equals(fingerprint(old)) || !business(old).equals(business(row)))fail("EXISTING_IMMUTABLE_LIST_CHANGED");s.target=old;continue;}
   if(occupied.contains(p.id()))fail("EXISTING_PROJECT_LIST_CANNOT_APPEND");
   row.id=c.preview()?null:IdWorker.getId();row.creator=Long.toString(c.actorId());row.updater=row.creator;row.createTime=c.startedAt();row.updateTime=c.startedAt();row.deleted=false;
   s.target=row;s.action=SyncWriteAction.INSERT;
  }
 }
 static void validate(Source s){
  if(s.sourceId==null || s.sourceId<=0 || s.originalProjectId==null || s.canonicalProjectId==null || !"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");
  if(!Integer.valueOf(1).equals(s.upstreamMatchCount) || !Arrays.asList(s.projectCode,s.executionNo,s.productCategoryName,s.productName,s.productSubCode,s.productSubModel,s.productSubName,s.quantity,s.memo).equals(Arrays.asList(s.upstreamProjectCode,s.upstreamExecutionNo,s.upstreamProductCategoryName,s.upstreamProductName,s.upstreamProductSubCode,s.upstreamProductSubModel,s.upstreamProductSubName,s.upstreamQuantity,s.upstreamMemo)))fail("UPSTREAM_NOT_EXACTLY_ONE");
  for(var value:Arrays.asList(s.contractNo,s.projectCode,s.executionNo,s.productSubCode,s.productSubModel,s.productSubName))if(value==null)fail("REQUIRED_VALUE_NULL");
  if(s.quantity!=null)s.quantity=new java.math.BigDecimal(s.quantity.intValueExact());
  for(var value:Arrays.asList(s.contractNo,s.projectCode,s.executionNo,s.productCategoryName,s.productName,s.productSubCode,s.productSubModel,s.productSubName,s.memo))checkTextCapacity(value);
  checkTextLength(s.dataSource,25);
 }
 static Row convert(Source s){var r=new Row();r.contractNo=s.contractNo;r.projectCode=s.projectCode;r.executionNo=s.executionNo;r.productCategoryName=s.productCategoryName;r.productName=s.productName;r.productSubCode=s.productSubCode;r.productSubModel=s.productSubModel;r.productSubName=s.productSubName;r.quantity=s.quantity;r.memo=s.memo;r.dataSource=s.dataSource; return r;}
 static String business(Row r){return JsonUtils.toJsonString(Arrays.asList(r.tenantId,r.projectId,r.contractNo,r.projectCode,r.executionNo,r.productCategoryName,r.productName,r.productSubCode,r.productSubModel,r.productSubName,r.quantity==null?null:r.quantity.stripTrailingZeros(),r.memo,r.dataSource));}
 static String fingerprint(Row r){return SyncFingerprint.of(Arrays.asList(r.getId(),r.getTenantId(),r.getProjectId(),r.getContractNo(),r.getProjectCode(),r.getExecutionNo(),r.getProductCategoryName(),r.getProductName(),r.getProductSubCode(),r.getProductSubModel(),r.getProductSubName(),r.getQuantity()==null?null:r.getQuantity().stripTrailingZeros(),r.getMemo(),r.getDataSource(),r.getCreator(),r.getCreateTime(),r.getUpdater(),r.getUpdateTime(),r.getDeleted()));}
 static void fail(String code){throw new IllegalArgumentException("P20_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Void> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Void> b){super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Void> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save("REAL_PRODUCT",s.sourceId.toString(),s.target.id,fingerprint(s.target),null)).toList());}
 @lombok.Data public static class Source { private Row target;private SyncWriteAction action;
private Long sourceId;
private Long originalProjectId;
private Long canonicalProjectId;
private String originalProjectCode;
private String projectType;
private String contractNo;
private String projectCode;
private String executionNo;
private String productCategoryName;
private String productName;
private String productSubCode;
private String productSubModel;
private String productSubName;
private java.math.BigDecimal quantity;
private String memo;
private String dataSource;
private Integer upstreamMatchCount;
private String upstreamProjectCode;
private String upstreamExecutionNo;
private String upstreamProductCategoryName;
private String upstreamProductName;
private String upstreamProductSubCode;
private String upstreamProductSubModel;
private String upstreamProductSubName;
private java.math.BigDecimal upstreamQuantity;
private String upstreamMemo;
 }
 @lombok.Data public static class Row {private Long id,tenantId,projectId;private String creator,updater;private LocalDateTime createTime,updateTime;private Boolean deleted;
private String contractNo;
private String projectCode;
private String executionNo;
private String productCategoryName;
private String productName;
private String productSubCode;
private String productSubModel;
private String productSubName;
private java.math.BigDecimal quantity;
private String memo;
private String dataSource;
 }
 public record ProjectsQuery(long tenantId,List<Long> ids,boolean preview){}
 public interface SourceMapper {List<Source> selectRealProduct(SourceQuery<Void> query);}
 @Mapper public interface TargetMapper {List<Row> selectProjects(@Param("query") ProjectsQuery query);int insertRealProduct(SyncBatch<Source,Void> batch);}
}
