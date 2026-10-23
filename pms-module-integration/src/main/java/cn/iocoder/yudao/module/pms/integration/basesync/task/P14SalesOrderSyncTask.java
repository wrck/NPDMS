package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Orders and lines preserve business keys; nullable customer/product/company IDs are deferred. */
@Component @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class P14SalesOrderSyncTask extends AbstractSyncTask<P14SalesOrderSyncTask.Source,P14SalesOrderSyncTask.Parameters> {
 private final TargetMapper mapper;
 private final ManagedOrganizationApi organizations;
 public static final class Parameters { private final Map<OrderKey,OrderRow> orders=new HashMap<>(); private long pendingOrderReferences; private final Map<String,ManagedOrganizationApi.Node> companies=new HashMap<>(); private final Map<String,Long> missingCompanies=new LinkedHashMap<>(); private long connectionId; public long getConnectionId(){return connectionId;} }
 @Override protected Parameters createParameters(SyncContext<?> c){
  var parameters=new Parameters();parameters.connectionId=c.connectionId();var grouped=new HashMap<String,List<ManagedOrganizationApi.Node>>();
  for(var node:organizations.list()){
   if(node==null||(node.company()==null)==(node.department()==null)||node.id()==null||node.id()<=0
     ||!Objects.equals(c.tenantId(),node.company()!=null?node.company().getTenantId():node.department().getTenantId()))fail("ORGANIZATION_DIRECTORY_SCOPE_INVALID");
   if("COMPANY".equals(node.type())&&node.code()!=null&&!node.code().isBlank())grouped.computeIfAbsent(node.code(),ignored->new ArrayList<>()).add(node);
  }
  grouped.forEach((code,nodes)->{if(nodes.getFirst().name()!=null&&!nodes.getFirst().name().isBlank()&&nodes.stream().map(ManagedOrganizationApi.Node::id).distinct().count()==1&&nodes.stream().map(ManagedOrganizationApi.Node::name).distinct().count()==1)parameters.companies.put(code,nodes.getFirst());});
  return parameters;
 }
 @Override public String taskCode(){return "BASE_P14";}
 @Override public String taskName(){return "ERP销售订单与订单行";}
 @Override public void work(SyncContext<Parameters> c){
  // Explicit full header snapshot: planned parent identities must remain available to line preview.
  syncData(c.forData("Order",false,false));syncData(c.forData("Line",false,true));
  syncData(c.forLocalData("OrderCompany",false,true));
  c.parameters().missingCompanies.forEach((code,count)->c.runState().warning("P14_ORDER_COMPANY_DIRECTORY_MISSING:"+code+":"+count));
  if(c.parameters().pendingOrderReferences>0)c.runState().warning("P14_ORDER_REFERENCE_PENDING:"+c.parameters().pendingOrderReferences);
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  if("OrderCompany".equals(b.context().dataName()))completeOrderCompanies(b);else if("Order".equals(b.context().dataName()))prepareOrders(b);else prepareLines(b);
 }
 private void completeOrderCompanies(SyncBatch<Source,Parameters> b){
  var c=b.context();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("SALES_ORDER",b.rows().stream().map(Source::getKey).toList()));
  b.rows().forEach(s->s.action=SyncWriteAction.UNCHANGED);var owned=b.rows().stream().filter(s->bindings.containsKey(s.key)).toList();if(owned.isEmpty())return;
  var current=new HashMap<OrderKey,OrderRow>();
  for(int i=0;i<owned.size();i+=80)for(var old:mapper.selectOrders(new OrdersQuery(c.tenantId(),owned.subList(i,Math.min(i+80,owned.size())).stream().map(s->key(s.order)).toList(),c.preview()))){
   if(!Objects.equals(old.tenantId,c.tenantId())||current.putIfAbsent(key(old),old)!=null)fail("ORDER_TARGET_AMBIGUOUS");
  }
  for(var source:owned){
   var old=current.get(key(source.order));var binding=bindings.get(source.key);protect(new Protection(old,binding));
   if(!Objects.equals(source.order.id,binding.targetId()))fail("EXISTING_TARGET_PROTECTED");
   var next=new OrderRow();org.springframework.beans.BeanUtils.copyProperties(old,next);
   if(next.companyId==null||next.companyName==null||next.companyName.isBlank()){
    var company=c.parameters().companies.get(old.companyCode);
    if(company==null)c.parameters().missingCompanies.merge(old.companyCode,1L,Long::sum);
    else{if(next.companyId==null)next.companyId=company.id();if(next.companyName==null||next.companyName.isBlank())next.companyName=company.name();}
   }
   source.previous=binding.fingerprint();source.order=next;source.expectedVersion=old.version;
   if(!Objects.equals(old.companyId,next.companyId)||!Objects.equals(old.companyName,next.companyName)){next.version=Math.addExact(old.version,1);source.action=SyncWriteAction.UPDATE;}
  }
 }
 private void prepareOrders(SyncBatch<Source,Parameters> b){
  var c=b.context();var keys=b.rows().stream().map(s->s.key).toList();
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();for(int i=0;i<keys.size();i+=500)bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query("SALES_ORDER",keys.subList(i,Math.min(i+500,keys.size())))));
  var incoming=b.rows().stream().map(s->s.order).toList();var oldByKey=new HashMap<OrderKey,OrderRow>();
  for(int i=0;i<incoming.size();i+=80)for(var old:mapper.selectOrders(new OrdersQuery(c.tenantId(),incoming.subList(i,Math.min(i+80,incoming.size())).stream().map(P14SalesOrderSyncTask::key).toList(),c.preview()))){
   if(old.getTenantId()!=c.tenantId() || oldByKey.putIfAbsent(key(old),old)!=null)fail("ORDER_TARGET_AMBIGUOUS");
  }
  for(var s:b.rows()){
   validate(s);var row=s.order;var old=oldByKey.get(key(row));var binding=bindings.get(s.key);
   protect(new Protection(old,binding));
   s.previous=binding==null?null:binding.fingerprint();s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){
    row.customInfo=old.customInfo;
    if(old.sourceUpdatedAt==null)fail("BOUND_SOURCE_VERSION_MISSING");int revision=row.sourceUpdatedAt.compareTo(old.sourceUpdatedAt);
    if(revision<0){s.order=old;c.parameters().orders.put(key(old),old);continue;}
    if(revision==0){if(!orderBusiness(old).equals(orderBusiness(row)))fail("ORDER_SAME_VERSION_CONFLICT");s.order=old;c.parameters().orders.put(key(old),old);continue;}
    preserveOrder(row,old);s.expectedVersion=old.version;s.action=SyncWriteAction.UPDATE;
   }else{row.id=c.preview()?null:IdWorker.getId();row.tenantId=c.tenantId();row.version=0;row.deleted=0;row.authorityStatus="CONFIRMED";row.creator=Long.toString(c.actorId());row.createTime=c.startedAt();s.action=SyncWriteAction.INSERT;}
   row.updater=Long.toString(c.actorId());row.updateTime=c.startedAt();row.sourceSyncTime=c.startedAt();
   if(c.parameters().orders.putIfAbsent(key(row),row)!=null)fail("ORDER_SOURCE_DUPLICATE");
  }
 }
 private void prepareLines(SyncBatch<Source,Parameters> b){
  var c=b.context();var keys=b.rows().stream().map(s->s.key).toList();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("SALES_LINE",keys));
  var incoming=b.rows().stream().map(s->s.line).toList();var oldByKey=new HashMap<LineKey,LineRow>();
  for(int i=0;i<incoming.size();i+=80)for(var old:mapper.selectLines(new LinesQuery(c.tenantId(),incoming.subList(i,Math.min(i+80,incoming.size())).stream().map(P14SalesOrderSyncTask::key).toList(),c.preview()))){
   if(old.getTenantId()!=c.tenantId() || oldByKey.putIfAbsent(key(old),old)!=null)fail("LINE_TARGET_AMBIGUOUS");
  }
  for(var s:b.rows()){
   validate(s);var row=s.line;var old=oldByKey.get(key(row));var binding=bindings.get(s.key);protect(new Protection(old,binding));
   var parent=c.parameters().orders.get(new OrderKey(row.sourceSystem,row.companyCode,row.lineType,row.orderNo));
   // The original line type supplies the required parent-key type; absent parent IDs stay NULL.
   row.orderType=row.lineType;row.orderId=parent==null?null:parent.id;
   if(parent==null){
    c.parameters().pendingOrderReferences=Math.incrementExact(c.parameters().pendingOrderReferences);
    log.warn("P14_ORDER_REFERENCE_PENDING:tenantId={},connectionId={},startedAt={},preview={},sourceIds={},sourceKey={},sourceSystem={},companyCode={},lineType={},orderNo={},lineNo={},orderQty={},openQty={}",c.tenantId(),c.connectionId(),c.startedAt(),c.preview(),s.originalSourceIds,s.key,row.sourceSystem,row.companyCode,row.lineType,row.orderNo,row.lineNo,row.orderQty,row.openQty);
   }
   s.previous=binding==null?null:binding.fingerprint();s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){
    row.customInfo=old.customInfo;
    if(old.sourceUpdatedAt==null)fail("BOUND_SOURCE_VERSION_MISSING");int revision=row.sourceUpdatedAt.compareTo(old.sourceUpdatedAt);
    if(revision<0){s.line=old;continue;}
    if(revision==0){if(!lineBusiness(old).equals(lineBusiness(row)))fail("LINE_SAME_VERSION_CONFLICT");s.line=old;continue;}
    preserveLine(row,old);s.expectedVersion=old.version;s.action=SyncWriteAction.UPDATE;
   }else{row.id=c.preview()?null:IdWorker.getId();row.tenantId=c.tenantId();row.version=0;row.deleted=0;row.creator=Long.toString(c.actorId());row.createTime=c.startedAt();s.action=SyncWriteAction.INSERT;}
   row.updater=Long.toString(c.actorId());row.updateTime=c.startedAt();row.sourceSyncTime=c.startedAt();
   // Source has no authoritative unit. Retain quantities as pending facts, with their exact scale.
   if(old==null){row.unitScale=6;row.quantityStatus="PENDING_AUTHORITY";}
   else for(var value:Arrays.asList(row.orderQty,row.openQty,row.deliveredQty))if(value!=null && value.stripTrailingZeros().scale()>row.unitScale)fail("CONFIRMED_UNIT_PRECISION_DIFFERS");
  }
 }
 record Protection(Object row,SyncIdentityStore.Identity binding){}
 static void protect(Protection q){var old=q.row;var binding=q.binding;Long id=old instanceof OrderRow r?r.id:old instanceof LineRow l?l.id:null;String fingerprint=old instanceof OrderRow r?fingerprint(r):old instanceof LineRow l?fingerprint(l):null;
  if(old==null && binding!=null || old!=null && (binding==null || binding.targetId()!=id || !binding.fingerprint().equals(fingerprint)))fail("EXISTING_TARGET_PROTECTED");
  if(old instanceof OrderRow r && r.deleted!=0 || old instanceof LineRow l && l.deleted!=0)fail("DELETED_TARGET_PROTECTED");
 }
 static void validate(Source s){
  if(s.issue!=null)fail(s.issue);if(s.key==null || s.key.isBlank())fail("SOURCE_KEY_MISSING");
  if(s.order!=null){var r=s.order;checkIdentity(new OrderKey(r.sourceSystem,r.companyCode,r.orderType,r.orderNo));if(r.sourceUpdatedAt==null)fail("SOURCE_VERSION_MISSING");
   validateTextOrder(r);checkMillis(r.orderCreateTime);checkMillis(r.customerRequiredTime);checkMillis(r.sourceUpdatedAt);validateJson(r.customInfo);
  }else{var r=s.line;checkIdentity(new OrderKey(r.sourceSystem,r.companyCode,r.lineType,r.orderNo));if(r.sourceUpdatedAt==null || r.lineNo==null || r.lineNo.isBlank())fail("LINE_IDENTITY_MISSING");
   validateTextLine(r);checkMillis(r.sourceUpdatedAt);validateJson(r.customInfo);
   exactDecimal(r.orderQty==null?null:r.orderQty.stripTrailingZeros(),18,6);exactDecimal(r.openQty==null?null:r.openQty.stripTrailingZeros(),18,6);exactDecimal(r.deliveredQty==null?null:r.deliveredQty.stripTrailingZeros(),18,6);
   if(r.warrantyMonth!=null && r.warrantyMonth<0 || "0".equals(r.lineType) && (negative(r.orderQty)||negative(r.openQty)||negative(r.deliveredQty)))fail("QUANTITY_INVALID");
  }
 }
 static boolean negative(BigDecimal n){return n!=null && n.signum()<0;}
 static void checkIdentity(OrderKey q){var system=q.sourceSystem;var company=q.companyCode;var type=q.orderType;var number=q.orderNo;if(!Set.of("SAP","D365","SMS").contains(Objects.toString(system,"")) || !Set.of("001","002","003","004").contains(Objects.toString(company,"")) || !Set.of("0","1").contains(Objects.toString(type,"")) || number==null || number.isBlank())fail("BUSINESS_IDENTITY_INVALID");}
 static void checkMillis(LocalDateTime t){if(t!=null && t.getNano()%1_000_000!=0)fail("TIME_PRECISION_INVALID");}
 static void validateJson(String value){if(value!=null)try{JsonUtils.parseTree(value);}catch(RuntimeException e){fail("CUSTOM_INFO_INVALID");}}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){var known=SyncCounts.fromActions(b.rows().stream().filter(s->s.action!=null).toList(),Source::getAction);return new SyncCounts(b.rows().size(),known.inserted(),known.updated(),known.unchanged(),known.deleted());}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){
  if("OrderCompany".equals(b.context().dataName())){
   var changed=b.matching(s->s.action==SyncWriteAction.UPDATE);if(changed.rows().isEmpty()||b.context().preview())return;
   if(mapper.updateOrderCompany(changed)!=changed.rows().size())fail("ORDER_COMPANY_TARGET_CHANGED");return;
  }
  super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));super.syncDataUpdate(b.matching(s->s.action==SyncWriteAction.UPDATE));}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){var order="Order".equals(b.context().dataName())||"OrderCompany".equals(b.context().dataName());
  b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT || s.action==SyncWriteAction.UPDATE).map(s->new SyncIdentityStore.Save(order?"SALES_ORDER":"SALES_LINE",s.key,order?s.order.id:s.line.id,order?fingerprint(s.order):fingerprint(s.line),s.previous)).toList());
 }
 static OrderKey key(OrderRow r){return new OrderKey(r.sourceSystem,r.companyCode,r.orderType,r.orderNo);}
 static LineKey key(LineRow r){return new LineKey(r.sourceSystem,r.companyCode,r.lineType,r.orderNo,r.lineNo);}
 static String fingerprint(OrderRow r){return SyncFingerprint.of(Arrays.asList(r.getId(),r.getTenantId(),r.getSourceSystem(),r.getSourceRecordKey(),r.getSourceVersion(),r.getCompanyId(),r.getCompanyCode(),r.getCompanyName(),r.getOrderType(),r.getOrderNo(),r.getSalesType(),r.getContractNo(),r.getExecutionNo(),r.getCustomerId(),r.getCustomerCode(),r.getCustomerName(),r.getSourceProjectName(),r.getOrderComment(),r.getOrderAmount()==null?null:r.getOrderAmount().stripTrailingZeros(),r.getCurrencyCode(),r.getAuthorityStatus(),r.getSourceLifecycleStatus(),r.getOrderCreateTime(),r.getCustomerRequiredTime(),cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncJson.canonicalJson(r.getCustomInfo()),r.getSourceSyncTime(),r.getSourceUpdatedAt(),r.getStatus(),r.getVersion(),r.getCreator(),r.getCreateTime(),r.getUpdater(),r.getUpdateTime(),r.getDeleted()));}
 static String fingerprint(LineRow r){return SyncFingerprint.of(Arrays.asList(r.getId(),r.getTenantId(),r.getOrderId(),r.getSourceSystem(),r.getSourceRecordKey(),r.getSourceVersion(),r.getCompanyId(),r.getCompanyCode(),r.getCompanyName(),r.getOrderType(),r.getOrderNo(),r.getLineNo(),r.getLineType(),r.getProductCode(),r.getProductModel(),r.getProductDesc(),cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncJson.canonicalJson(r.getCustomInfo()),r.getProductId(),r.getBundleCode(),r.getProfitCenter(),r.getRealExecutionNo(),r.getWarrantyMonth(),r.getCustomerId(),r.getCustomerCode(),r.getCustomerName(),r.getOrderQty()==null?null:r.getOrderQty().stripTrailingZeros(),r.getOpenQty()==null?null:r.getOpenQty().stripTrailingZeros(),r.getDeliveredQty()==null?null:r.getDeliveredQty().stripTrailingZeros(),r.getUnitCode(),r.getUnitScale(),r.getQuantityStatus(),r.getSourceLifecycleStatus(),r.getSourceSyncTime(),r.getSourceUpdatedAt(),r.getStatus(),r.getVersion(),r.getCreator(),r.getCreateTime(),r.getUpdater(),r.getUpdateTime(),r.getDeleted()));}
 static void fail(String code){throw new IllegalArgumentException("P14_"+code);}
 @lombok.Data public static class Source {private String key,issue,previous,originalSourceIds;private Integer expectedVersion;private SyncWriteAction action;private OrderRow order;private LineRow line;}
 public record OrderKey(String sourceSystem,String companyCode,String orderType,String orderNo){}
 public record LineKey(String sourceSystem,String companyCode,String lineType,String orderNo,String lineNo){}
 public record OrdersQuery(long tenantId,List<OrderKey> keys,boolean preview){}
 public record LinesQuery(long tenantId,List<LineKey> keys,boolean preview){}
 public interface SourceMapper {List<Source> selectOrder(SourceQuery<Parameters> query);void selectLine(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper {void selectOrderCompany(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);int updateOrderCompany(SyncBatch<Source,Parameters> batch);List<OrderRow> selectOrders(@Param("query") OrdersQuery query);List<LineRow> selectLines(@Param("query") LinesQuery query);int insertOrder(SyncBatch<Source,Parameters> batch);int insertLine(SyncBatch<Source,Parameters> batch);int updateOrder(RowParameter<Source,Parameters> row);int updateLine(RowParameter<Source,Parameters> row);}
@lombok.Data public static class OrderRow {
 private Long id;
 private Long tenantId;
 private String sourceSystem;
 private String sourceRecordKey;
 private String sourceVersion;
 private Long companyId;
 private String companyCode;
 private String companyName;
 private String orderType;
 private String orderNo;
 private String salesType;
 private String contractNo;
 private String executionNo;
 private Long customerId;
 private String customerCode;
 private String customerName;
 private String sourceProjectName;
 private String orderComment;
 private BigDecimal orderAmount;
 private String currencyCode;
 private String authorityStatus;
 private String sourceLifecycleStatus;
 private LocalDateTime orderCreateTime;
 private LocalDateTime customerRequiredTime;
 private String customInfo;
 private LocalDateTime sourceSyncTime;
 private LocalDateTime sourceUpdatedAt;
 private String status;
 private Integer version;
 private String creator;
 private LocalDateTime createTime;
 private String updater;
 private LocalDateTime updateTime;
 private Integer deleted;
}
static List<Object> orderBusiness(OrderRow r){return Arrays.asList(r.sourceSystem,r.companyCode,r.orderNo,cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncJson.canonicalJson(r.customInfo),r.orderType,r.salesType,r.contractNo,r.executionNo,r.customerCode,r.customerName,r.sourceProjectName,r.orderComment,r.orderCreateTime,r.customerRequiredTime,r.sourceLifecycleStatus,r.status);}
static void preserveOrder(OrderRow r,OrderRow old){
 r.id=old.id;
 r.tenantId=old.tenantId;
 r.sourceSystem=old.sourceSystem;
 r.sourceRecordKey=old.sourceRecordKey;
 r.companyId=old.companyId;
 r.companyCode=old.companyCode;
 r.companyName=old.companyName;
 r.orderType=old.orderType;
 r.orderNo=old.orderNo;
 r.customerId=old.customerId;
 r.orderAmount=old.orderAmount;
 r.currencyCode=old.currencyCode;
 r.authorityStatus=old.authorityStatus;
 r.creator=old.creator;
 r.createTime=old.createTime;
 r.deleted=old.deleted;
 r.version=Math.addExact(old.version,1);
}
static void validateTextOrder(OrderRow r){checkTextLength(r.sourceSystem,32);checkTextLength(r.sourceRecordKey,128);checkTextLength(r.sourceVersion,64);checkTextLength(r.companyCode,64);checkTextLength(r.companyName,255);checkTextLength(r.orderType,32);checkTextLength(r.orderNo,64);checkTextLength(r.salesType,32);checkTextLength(r.contractNo,64);checkTextLength(r.executionNo,64);checkTextLength(r.customerCode,64);checkTextLength(r.customerName,512);checkTextLength(r.sourceProjectName,512);checkTextLength(r.orderComment,2048);checkTextLength(r.currencyCode,32);checkTextLength(r.authorityStatus,32);checkTextLength(r.sourceLifecycleStatus,32);checkTextLength(r.status,32);checkTextLength(r.creator,64);checkTextLength(r.updater,64);}
@lombok.Data public static class LineRow {
 private Long id;
 private Long tenantId;
 private Long orderId;
 private String sourceSystem;
 private String sourceRecordKey;
 private String sourceVersion;
 private Long companyId;
 private String companyCode;
 private String companyName;
 private String orderType;
 private String orderNo;
 private String lineNo;
 private String lineType;
 private String productCode;
 private String productModel;
 private String productDesc;
 private String customInfo;
 private Long productId;
 private String bundleCode;
 private String profitCenter;
 private String realExecutionNo;
 private Integer warrantyMonth;
 private Long customerId;
 private String customerCode;
 private String customerName;
 private BigDecimal orderQty;
 private BigDecimal openQty;
 private BigDecimal deliveredQty;
 private String unitCode;
 private Integer unitScale;
 private String quantityStatus;
 private String sourceLifecycleStatus;
 private LocalDateTime sourceSyncTime;
 private LocalDateTime sourceUpdatedAt;
 private String status;
 private Integer version;
 private String creator;
 private LocalDateTime createTime;
 private String updater;
 private LocalDateTime updateTime;
 private Integer deleted;
}
static List<Object> lineBusiness(LineRow r){return Arrays.asList(r.sourceSystem,r.companyCode,r.orderNo,cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncJson.canonicalJson(r.customInfo),r.orderType,r.lineType,r.lineNo,r.productCode,r.productDesc,r.bundleCode,r.profitCenter,r.realExecutionNo,r.warrantyMonth,r.orderQty==null?null:r.orderQty.stripTrailingZeros(),r.openQty==null?null:r.openQty.stripTrailingZeros(),r.deliveredQty==null?null:r.deliveredQty.stripTrailingZeros(),r.sourceLifecycleStatus,r.status);}
static void preserveLine(LineRow r,LineRow old){
 r.id=old.id;
 r.tenantId=old.tenantId;
 r.orderId=old.orderId;
 r.sourceSystem=old.sourceSystem;
 r.sourceRecordKey=old.sourceRecordKey;
 r.companyId=old.companyId;
 r.companyCode=old.companyCode;
 r.companyName=old.companyName;
 r.orderType=old.orderType;
 r.orderNo=old.orderNo;
 r.lineNo=old.lineNo;
 r.productModel=old.productModel;
 r.productId=old.productId;
 r.customerId=old.customerId;
 r.customerCode=old.customerCode;
 r.customerName=old.customerName;
 r.unitCode=old.unitCode;
 r.unitScale=old.unitScale;
 r.quantityStatus=old.quantityStatus;
 r.creator=old.creator;
 r.createTime=old.createTime;
 r.deleted=old.deleted;
 r.version=Math.addExact(old.version,1);
}
static void validateTextLine(LineRow r){checkTextLength(r.sourceSystem,32);checkTextLength(r.sourceRecordKey,128);checkTextLength(r.sourceVersion,64);checkTextLength(r.companyCode,64);checkTextLength(r.companyName,255);checkTextLength(r.orderType,32);checkTextLength(r.orderNo,64);checkTextLength(r.lineNo,32);checkTextLength(r.lineType,32);checkTextLength(r.productCode,64);checkTextLength(r.productModel,64);checkTextLength(r.productDesc,512);checkTextLength(r.bundleCode,64);checkTextLength(r.profitCenter,64);checkTextLength(r.realExecutionNo,64);checkTextLength(r.customerCode,64);checkTextLength(r.customerName,512);checkTextLength(r.unitCode,32);checkTextLength(r.quantityStatus,32);checkTextLength(r.sourceLifecycleStatus,32);checkTextLength(r.status,32);checkTextLength(r.creator,64);checkTextLength(r.updater,64);}
}
