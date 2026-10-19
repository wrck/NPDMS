package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Raw source IDs retain individual provenance; only the reviewed final revision supplies the header. */
@Component @RequiredArgsConstructor
public class P13ExecutionOrderSyncTask extends AbstractSyncTask<P13ExecutionOrderSyncTask.Source,P13ExecutionOrderSyncTask.Parameters> {
 private final TargetMapper mapper;
 public static final class Parameters {long excluded;}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){
  try(var session=c.source().open(c.connectionId())){
   var query=new SourceQuery<>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters());
   var prefixes=session.mapper(SourceMapper.class).selectProjectPrefixes(query);
   if(prefixes.size()>c.maxRows())fail("PROJECT_REFERENCE_CAPACITY");
   var scope=new HashSet<>(prefixes);
   this.<Source,SourceQuery<Parameters>>syncDataQueryBySql(session.sqlSession(),new MappedCall<>(c,sourceMapperNamespace()+".selectExecutionOrder",query),result->{
    if(scope.contains(result.getResultObject().getRawProjectCode()))handler.handleResult(result);else c.parameters().excluded++;
   });
  }
 }
 @Override public String taskCode(){return "BASE_P13";}
 @Override public String taskName(){return "项目执行单头";}
 @Override public void work(SyncContext<Parameters> context){syncData(context.forData("ExecutionOrder",false,true));if(context.parameters().excluded>0)context.runState().warning("P13_OUTSIDE_REVIEWED_PROJECT_SCOPE:"+context.parameters().excluded);if(context.preview())context.runState().warning("P13_PREVIEW_SOURCE_VALIDATED;WRITE_COUNTS_DEFERRED_UNTIL_EXECUTION");}
 @Override protected void syncDataBefore(SyncContext<Parameters> context){super.syncDataBefore(context);if(mapper.oneSourcePerTargetIndex()>0){if(!context.preview())fail("METADATA_MULTI_SOURCE_INDEX_REQUIRED:uk_int_base_sync_binding_target");context.runState().warning("P13_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target");}}
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> batch){
  var c=batch.context();var keys=batch.rows().stream().map(Source::businessKey).distinct().toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("EXECUTION_HEADER",keys));
  var originals=c.identities().findByKeys(new SyncIdentityStore.Query("EXECUTION_ORIGINAL",batch.rows().stream().map(s->s.sourceId.toString()).toList()));
  var oldByKey=new HashMap<String,Row>();
  // Small bounded OR groups also keep the production parser's serialized tree within its stack budget.
  for(int i=0;i<batch.rows().size();i+=80){
   var chosen=batch.rows().subList(i,Math.min(i+80,batch.rows().size())).stream().map(s->new Key(s.sourceSystem,s.executionNo)).distinct().toList();
   for(var old:mapper.selectExisting(new ExistingQuery(c.tenantId(),chosen,c.preview()))){
    if(old.getTenantId()!=c.tenantId() || !chosen.contains(new Key(old.getSourceSystem(),old.getExecutionNo())))fail("BUSINESS_IDENTITY_DIFFER");
    String key=SyncFingerprint.of(Arrays.asList(old.getSourceSystem(),old.getExecutionNo()));var prior=oldByKey.putIfAbsent(key,old);
    if(prior!=null && !prior.equals(old))fail("BUSINESS_IDENTITY_AMBIGUOUS");
   }
  }
  var staged=new HashMap<String,Row>();
  for(var s:batch.rows()){
   s.action=SyncWriteAction.UNCHANGED;
   if(s.sourceId==null || s.sourceId<=0 || s.canonicalSourceId==null || s.canonicalSourceId<s.sourceId)fail("REVISION_INVALID");
   var incoming=convert(s);var key=s.businessKey();var binding=bindings.get(key);var old=oldByKey.get(key);var original=originals.get(s.sourceId.toString());
   if(old!=null && (Boolean.TRUE.equals(old.getDeleted()) || binding==null || binding.targetId()!=old.getId() || !binding.fingerprint().equals(fingerprint(old))))fail("EXISTING_HEADER_PROTECTED");
   if(old==null && binding!=null || original!=null && (old==null || original.targetId()!=old.getId() || !original.fingerprint().equals(identityFingerprint(old))))fail("SOURCE_BINDING_CHANGED");
   s.previous=binding==null?null:binding.fingerprint();s.originalPrevious=original==null?null:original.fingerprint();
   Row next=staged.get(key);
   if(next==null){
    next=incoming;
    if(old!=null){
     next=new Row();org.springframework.beans.BeanUtils.copyProperties(old,next);
     var from=new org.springframework.beans.BeanWrapperImpl(incoming);var into=new org.springframework.beans.BeanWrapperImpl(next);
     for(var field:SOURCE_FIELDS){var value=from.getPropertyValue(field);if(value!=null)into.setPropertyValue(field,value);}
     if(incoming.engineeringFeeRaw!=null)next.engineeringFee=incoming.engineeringFee;
     if(!business(old).equals(business(next))){next.version=Math.addExact(old.version,1);next.updater=Long.toString(c.actorId());next.updateTime=c.startedAt();next.sourceSyncTime=c.startedAt();s.expectedVersion=old.version;s.action=SyncWriteAction.UPDATE;}
    }
    if(old==null){next.setId(c.preview()?null:IdWorker.getId());next.setTenantId(c.tenantId());next.setVersion(0L);next.setDeleted(false);
     next.setStatus("ACTIVE");next.setAfEvidenceStatus("UNKNOWN");next.setCreator(Long.toString(c.actorId()));next.setUpdater(next.getCreator());
     next.setCreateTime(c.startedAt());next.setUpdateTime(c.startedAt());next.setSourceSyncTime(c.startedAt());s.action=SyncWriteAction.INSERT;}
    staged.put(key,next);
   }
   s.target=next;s.newOriginal=original==null;
  }
 }
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return b.context().preview()?SyncCounts.empty():SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));super.syncDataUpdate(b.matching(s->s.action==SyncWriteAction.UPDATE));}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){
  var saves=new ArrayList<SyncIdentityStore.Save>();
  for(var s:b.rows()){
   if(s.action!=SyncWriteAction.UNCHANGED)saves.add(new SyncIdentityStore.Save("EXECUTION_HEADER",s.businessKey(),s.target.id,fingerprint(s.target),s.previous));
   if(s.newOriginal)saves.add(new SyncIdentityStore.Save("EXECUTION_ORIGINAL",s.sourceId.toString(),s.target.id,identityFingerprint(s.target),s.originalPrevious));
  }
  b.context().identities().saveAll(saves);
 }
 static Row convert(Source s){
  var r=new Row();
  if(s.executionNo==null || s.executionNo.isBlank() || s.sourceSystem==null || s.sourceSystem.isBlank())fail("IDENTITY_MISSING");
  r.setSourceSystem(s.sourceSystem);
  r.setExecutionNo(s.executionNo);
  r.setProjectCode(s.projectCode);
  r.setProjectName(s.projectName);
  r.setSalesRepCode(s.salesRepCode);
  r.setSalesRepName(s.salesRepName);
  r.setMarketCode(s.marketCode);
  r.setMarketName(s.marketName);
  r.setSystemSourceKey(s.systemSourceKey);
  r.setSystemName(s.systemName);
  r.setExpendSourceKey(s.expendSourceKey);
  r.setExpendName(s.expendName);
  r.setIndustryCode(s.industryCode);
  r.setIndustryName(s.industryName);
  r.setDepartmentCode(s.departmentCode);
  r.setDepartmentName(s.departmentName);
  r.setServiceTypeName(s.serviceTypeName);
  r.setChannelName(s.channelName);
  r.setEngineeringFeeRaw(s.engineeringFeeRaw);
  r.setSourceObjectId(s.sourceObjectId);
  r.setApplyType(s.applyType);
  r.setCompanyCode(s.companyCode);
  r.setCustomerProjectName(s.customerProjectName);
  r.setFinalCustomerName(s.finalCustomerName);
  r.setAgentName(s.agentName);
  r.setMajorProjectLevel(s.majorProjectLevel);
  r.setProjectAmount(s.projectAmount);
  r.setSubmitTime(s.submitTime);
  r.setPredictedBidTime(s.predictedBidTime);
  r.setContactName(s.contactName);
  r.setContactPhone(s.contactPhone);
  if(r.getCompanyCode()!=null && !r.getCompanyCode().isBlank()){
   String code=r.getCompanyCode();r.setCompanyCode(switch(code){case "01","1"->"001";case "02","2"->"002";case "03","3"->"003";case "04","4"->"004";case "001","002","003","004"->code;default->throw new IllegalArgumentException("P13_COMPANY_UNMAPPED");});
  }
  if(r.getDepartmentCode()!=null && r.getDepartmentCode().matches("31120[2-9]|311210"))r.setDepartmentCode("1621"+r.getDepartmentCode().substring(4));
  // All organization IDs are optional on this approved header. Preserve codes, defer the association once.
  r.setEngineeringFee(decimal(s.engineeringFeeRaw));
  return r;
 }
 static BigDecimal decimal(String value){if(value==null || value.isBlank())return null;return exactDecimal(new BigDecimal(value.replace(",","").trim()),20,4);}
 private static final List<String> SOURCE_FIELDS=List.of("sourceSystem","executionNo","projectCode","projectName","salesRepCode","salesRepName","marketCode","marketName","systemSourceKey","systemName","expendSourceKey","expendName","industryCode","industryName","departmentCode","departmentName","serviceTypeName","channelName","engineeringFeeRaw","sourceObjectId","applyType","companyCode","customerProjectName","finalCustomerName","agentName","majorProjectLevel","projectAmount","submitTime","predictedBidTime","contactName","contactPhone");
 static String identityFingerprint(Row r){return SyncFingerprint.of(Arrays.asList(r.id,r.sourceSystem,r.executionNo));}
 static List<Object> business(Row r){return Arrays.asList(r.getSourceSystem(),r.getExecutionNo(),r.getProjectCode(),r.getProjectName(),r.getSalesRepCode(),r.getSalesRepName(),r.getMarketCode(),r.getMarketName(),r.getSystemSourceKey(),r.getSystemName(),r.getExpendSourceKey(),r.getExpendName(),r.getIndustryCode(),r.getIndustryName(),r.getDepartmentCode(),r.getDepartmentName(),r.getServiceTypeName(),r.getChannelName(),r.getEngineeringFeeRaw(),r.getSourceObjectId(),r.getApplyType(),r.getCompanyCode(),r.getCustomerProjectName(),r.getFinalCustomerName(),r.getAgentName(),r.getMajorProjectLevel(),r.getProjectAmount()==null?null:r.getProjectAmount().stripTrailingZeros(),r.getSubmitTime(),r.getPredictedBidTime(),r.getContactName(),r.getContactPhone());}
 static String fingerprint(Row r){return SyncFingerprint.of(Arrays.asList(r.getId(),r.getTenantId(),r.getSourceSystem(),r.getExecutionNo(),r.getProjectCode(),r.getProjectName(),r.getPrimaryProjectId(),r.getSalesRepCode(),r.getSalesRepName(),r.getSalesRepPhone(),r.getMarketCode(),r.getMarketName(),r.getSystemSourceKey(),r.getSystemCode(),r.getSystemName(),r.getExpendSourceKey(),r.getExpendCode(),r.getExpendName(),r.getIndustryCode(),r.getIndustryName(),r.getDepartmentId(),r.getDepartmentCode(),r.getDepartmentName(),r.getServiceTypeName(),r.getChannelName(),r.getEngineeringFeeRaw(),r.getEngineeringFee()==null?null:r.getEngineeringFee().stripTrailingZeros(),r.getSourceObjectId(),r.getApplyType(),r.getCompanyId(),r.getCompanyCode(),r.getCompanyName(),r.getCustomerProjectName(),r.getFinalCustomerName(),r.getAgentName(),r.getProjectManagerCode(),r.getProjectManagerName(),r.getDecisionPath(),r.getRequiredInDate(),r.getReceiverName(),r.getReceiverContact(),r.getReceiverAddress(),r.getLoanReason(),r.getProjectType(),r.getMajorProjectLevel(),r.getProjectAmount()==null?null:r.getProjectAmount().stripTrailingZeros(),r.getAfProjectAmount()==null?null:r.getAfProjectAmount().stripTrailingZeros(),r.getSubmitTime(),r.getPredictedBidTime(),r.getContactName(),r.getContactPhone(),r.getAfEvidenceStatus(),r.getSourceSyncTime(),r.getStatus(),r.getVersion(),r.getCreator(),r.getCreateTime(),r.getUpdater(),r.getUpdateTime(),r.getDeleted()));}
 static void fail(String code){throw new IllegalArgumentException("P13_"+code);}
 @lombok.Data public static class Source {
  private Long sourceId,canonicalSourceId;private String rawProjectCode; private Row target; private SyncWriteAction action;
  private String previous,originalPrevious;private boolean newOriginal;private Long expectedVersion;
  private String sourceSystem;
  private String executionNo;
  private String projectCode;
  private String projectName;
  private String salesRepCode;
  private String salesRepName;
  private String marketCode;
  private String marketName;
  private String systemSourceKey;
  private String systemName;
  private String expendSourceKey;
  private String expendName;
  private String industryCode;
  private String industryName;
  private String departmentCode;
  private String departmentName;
  private String serviceTypeName;
  private String channelName;
  private String engineeringFeeRaw;
  private String sourceObjectId;
  private String applyType;
  private String companyCode;
  private String customerProjectName;
  private String finalCustomerName;
  private String agentName;
  private String majorProjectLevel;
  private BigDecimal projectAmount;
  private LocalDateTime submitTime;
  private LocalDateTime predictedBidTime;
  private String contactName;
  private String contactPhone;
  String businessKey(){return SyncFingerprint.of(Arrays.asList(sourceSystem,executionNo));}
 }
@lombok.Data public static class Row { private Long id,tenantId,version; private String creator,updater; private LocalDateTime createTime,updateTime; private Boolean deleted;
    private String sourceSystem; private String executionNo; private String projectCode; private String projectName;
    private Long primaryProjectId; private String salesRepCode; private String salesRepName; private String salesRepPhone;
    private String marketCode; private String marketName; private String systemSourceKey; private String systemCode;
    private String systemName; private String expendSourceKey; private String expendCode; private String expendName;
    private String industryCode; private String industryName; private Long departmentId; private String departmentCode;
    private String departmentName; private String serviceTypeName; private String channelName; private String engineeringFeeRaw;
    private BigDecimal engineeringFee; private String sourceObjectId; private String applyType; private Long companyId;
    private String companyCode; private String companyName; private String customerProjectName; private String finalCustomerName;
    private String agentName; private String projectManagerCode; private String projectManagerName; private String decisionPath;
    private java.time.LocalDate requiredInDate; private String receiverName; private String receiverContact; private String receiverAddress;
    private String loanReason; private String projectType; private String majorProjectLevel; private BigDecimal projectAmount;
    private BigDecimal afProjectAmount; private LocalDateTime submitTime; private LocalDateTime predictedBidTime;
    private String contactName; private String contactPhone; private String afEvidenceStatus; private LocalDateTime sourceSyncTime;
    private String status; }
 public record Key(String sourceSystem,String executionNo){}
 public record ExistingQuery(long tenantId,List<Key> keys,boolean preview){}
 public interface SourceMapper {List<String> selectProjectPrefixes(SourceQuery<Parameters> query);void selectExecutionOrder(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper {
  List<Row> selectExisting(@Param("query") ExistingQuery query);
  int insertExecutionOrder(SyncBatch<Source,Parameters> batch);
  int updateExecutionOrder(RowParameter<Source,Parameters> parameter);
  @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true",dataPermission="true") int oneSourcePerTargetIndex();
 }
}
