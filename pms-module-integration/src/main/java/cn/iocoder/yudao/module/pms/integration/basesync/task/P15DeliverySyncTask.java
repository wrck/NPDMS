package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsWarrantyYearsConverter;
import cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask.*;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Five independent raw/local reads. Optional business IDs stay empty; no raw payload mirrors or source writes. */
@Component @RequiredArgsConstructor
public class P15DeliverySyncTask extends AbstractSyncTask<P15DeliverySyncTask.Source,P15DeliverySyncTask.Parameters> {
 private final TargetMapper mapper;
 private final ManagedOrganizationApi organizations;
 public record OrganizationKey(String type,String code){}
 public record Parameters(Map<String,String> carriers,long connectionId,
         Map<OrganizationKey,ManagedOrganizationApi.Node> organizations,Map<String,Long> missing){
  public Parameters(Map<String,String> carriers,long connectionId){this(carriers,connectionId,Map.of(),new LinkedHashMap<>());}
 }
 @Override public String taskCode(){return "BASE_P15";}
 @Override public String taskName(){return "合同及全量发货历史";}
 @Override public List<String> supportedStages(){return List.of("ShipmentContract","Package","ShipmentEvent","DeviceRelationship","Contract","ContractOrganization","ShipmentContractOrganization","OrderContractRelation","ShipmentOrderLine");}
 @Override protected Parameters createParameters(SyncContext<?> c){
  var names=new HashMap<String,String>();var ambiguous=new HashSet<String>();
  for(var item:mapper.selectCarriers(new TenantQuery(c.tenantId()))){if(item.code==null || item.label==null || item.label.isBlank())continue;var old=names.putIfAbsent(item.code,item.label);if(old!=null && !old.equals(item.label))ambiguous.add(item.code);}
  ambiguous.forEach(names::remove);
  var grouped=new LinkedHashMap<OrganizationKey,List<ManagedOrganizationApi.Node>>();
  for(var node:organizations.list()){
   if(node==null || (node.company()==null)==(node.department()==null) || node.id()==null || node.id()<=0
     || !Objects.equals(c.tenantId(),node.company()!=null?node.company().getTenantId():node.department().getTenantId()))
    fail("ORGANIZATION_DIRECTORY_SCOPE_INVALID");
   if(node.code()!=null&&!node.code().isBlank())grouped.computeIfAbsent(new OrganizationKey(node.type(),node.code()),ignored->new ArrayList<>()).add(node);
  }
  var directory=new HashMap<OrganizationKey,ManagedOrganizationApi.Node>();
  for(var entry:grouped.entrySet())if(entry.getValue().stream().map(ManagedOrganizationApi.Node::id).distinct().count()==1
    &&entry.getValue().stream().map(ManagedOrganizationApi.Node::name).distinct().count()==1)directory.put(entry.getKey(),entry.getValue().getFirst());
  return new Parameters(Map.copyOf(names),c.connectionId(),Map.copyOf(directory),new LinkedHashMap<>());
 }
 @Override public void work(SyncContext<Parameters> c){
  if((c.runState().includesStage("Package")||c.runState().includesStage("ShipmentEvent"))&&mapper.packageSourceColumnReady()!=1)fail("TARGET_COLUMN_MISSING:com_shipment_package.shipment_contract_source_key");
  if(c.preview()&&c.runState().includesStage("ShipmentEvent"))try(var session=c.source().open(c.connectionId())){long count=session.mapper(SourceMapper.class).countShipmentEvents(new SourceQuery<>(1,c.tenantId(),true,c.parameters()));if(count>c.maxRows())fail("FULL_SHIPMENT_SOURCE_EXCEEDS_CONFIGURED_LIMIT:"+count+":"+c.maxRows());}
  if(!c.preview()){var prerequisite=mapper.latestP24Execution(new PrerequisiteQuery(c.tenantId(),c.connectionId()));if(prerequisite==null || !"COMPLETED".equals(prerequisite))fail("COMPLETE_FORMAL_BASE_P24_INPUT_REQUIRED");}
  else c.runState().warning("P15_SOURCE_PREVIEW:FORMAL_BASE_P14_P24_INPUTS_NOT_VERIFIED");
  try{
   // Only fixed, reviewed stages are selectable; caller ordering never changes dependency order.
   for(String stage:supportedStages()){
    if(!c.runState().includesStage(stage))continue;
    boolean local=stage.equals("ContractOrganization")||stage.equals("ShipmentContractOrganization")||stage.equals("OrderContractRelation");
    syncData(local?c.forLocalData(stage,false,true):c.forData(stage,false,!stage.equals("Contract")));
   }
   c.parameters().missing.forEach((key,count)->c.runState().warning("P15_"+key+":"+count));
  }catch(StackOverflowError error){var frames=error.getStackTrace();org.slf4j.LoggerFactory.getLogger(P15DeliverySyncTask.class).error("P15_STACK_OVERFLOW_FRAMES:{}",Arrays.toString(Arrays.copyOf(frames,Math.min(32,frames.length))));throw error;}
 }
 @Override protected List<Source> syncDataQuery(SyncContext<Parameters> c){
  if(!"Contract".equals(c.dataName()))return super.syncDataQuery(c);
  try(var session=c.source().open(c.connectionId())){
   var source=session.mapper(SourceMapper.class);var q=new SourceQuery<Parameters>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters());
   var customers=source.selectContractCustomers(q);var shipments=source.selectContractShipmentFields(q);var receipts=source.selectContractReceivableFields(q);
   if(customers.size()>c.maxRows()||shipments.size()>c.maxRows()||receipts.size()>c.maxRows())fail("CONTRACT_SOURCE_CAPACITY");
    // Select the maximum order number within the chosen type: positive first, otherwise return.
    var orderPriority=Comparator.comparing(ContractCustomer::getOrderType).thenComparing(ContractCustomer::getOrderNo,Comparator.reverseOrder());
    var maximumOrders=new HashMap<String,ContractCustomer>();
    for(var customer:customers)if(("0".equals(customer.orderType)||"1".equals(customer.orderType))&&nonempty(customer.orderNo))maximumOrders.merge(customer.rawKey,customer,(previous,incoming)->orderPriority.compare(previous,incoming)<=0?previous:incoming);
    var grouped=new LinkedHashMap<String,Source>();
   for(var customer:customers){
     var maximumOrder=maximumOrders.get(customer.rawKey);
     if(maximumOrder!=null&&(!maximumOrder.orderType.equals(customer.orderType)||!maximumOrder.orderNo.equals(customer.orderNo)))continue;
    var row=new Row();row.companyCode=customer.companyCode;row.contractNo=customer.contractNo;row.customerCode=customer.customerCode;row.customerName=customer.customerName;row.masterSourceSystem="DPPMS_ERP";row.masterSourceRecordKey=customer.rawKey;
    var incoming=new Source();incoming.key=customer.rawKey;incoming.issue=customer.issue;incoming.target=row;var previous=grouped.putIfAbsent(incoming.key,incoming);if(previous==null)continue;
    if(incoming.issue!=null)previous.issue=incoming.issue;
    boolean conflict=nonempty(row.customerCode)&&nonempty(previous.target.customerCode)&&!row.customerCode.equals(previous.target.customerCode)||nonempty(row.customerName)&&nonempty(previous.target.customerName)&&!row.customerName.equals(previous.target.customerName);
    if(conflict&&previous.issue==null)previous.issue="CONTRACT_CUSTOMER_CONFLICT";
    if(!nonempty(previous.target.customerCode))previous.target.customerCode=row.customerCode;if(!nonempty(previous.target.customerName))previous.target.customerName=row.customerName;
   }
   var shipmentFields=new HashMap<String,Row>();for(var row:shipments)if(shipmentFields.putIfAbsent(row.contractNo,row)!=null)fail("SHIPMENT_CONTRACT_FIELDS_AMBIGUOUS");
   var receiptFields=new HashMap<String,Row>();for(var row:receipts)if(receiptFields.putIfAbsent(row.contractNo,row)!=null)fail("RECEIVABLE_CONTRACT_FIELDS_AMBIGUOUS");
   for(var entry:grouped.values()){
    var row=entry.target;var shipment=shipmentFields.get(row.contractNo);var receipt=receiptFields.get(row.contractNo);
    if(receipt!=null){row.currencyName=receipt.currencyName;row.currencyCode=receipt.currencyName;row.contractAmount=receipt.contractAmount;row.contractCreateTime=receipt.contractCreateTime;row.projectName=receipt.projectName;row.projectCode=receipt.projectCode;row.marketCode=receipt.marketCode;row.marketName=receipt.marketName;row.departmentCode=receipt.departmentCode;row.departmentName=receipt.departmentName;row.industryCode=receipt.industryCode;row.industryName=receipt.industryName;row.marketingRepresentativeCode=receipt.marketingRepresentativeCode;row.marketingRepresentativeName=receipt.marketingRepresentativeName;row.secondaryRepresentativeCode=receipt.secondaryRepresentativeCode;}
    if(shipment!=null){if(shipment.projectName!=null)row.projectName=shipment.projectName;if(shipment.marketCode!=null)row.marketCode=shipment.marketCode;if(shipment.marketName!=null)row.marketName=shipment.marketName;if(shipment.departmentCode!=null)row.departmentCode=shipment.departmentCode;row.systemName=shipment.systemName;}
   }
   org.slf4j.LoggerFactory.getLogger(P15DeliverySyncTask.class).info("P15_CONTRACT_SOURCE_READ:orderRows={},shipmentGroups={},receivableGroups={},contracts={},sourceIssues={}",customers.size(),shipments.size(),receipts.size(),grouped.size(),grouped.values().stream().filter(row->row.issue!=null).count());
   return new ArrayList<>(grouped.values());
  }
 }
 static boolean nonempty(String value){return value!=null&&!value.isEmpty();}
 @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){
  if(!"ShipmentEvent".equals(c.dataName())){super.syncDataQueryByRowHandler(c,handler);return;}
  try(var session=c.source().open(c.connectionId())){
   var source=session.mapper(SourceMapper.class);var query=new SourceQuery<>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters());
   var packageRows=source.selectShipmentPackageReferences(query);var contractRows=source.selectShipmentContractReferences(query);
   if(packageRows.size()>c.maxRows()||contractRows.size()>c.maxRows())fail("SHIPMENT_LOOKUP_SOURCE_CAPACITY");
   var packages=new HashMap<String,ShipmentPackageReference>();for(var row:packageRows)if(row.sourceKey!=null&&packages.putIfAbsent(row.sourceKey,row)!=null)fail("PACKAGE_SOURCE_IDENTITY_AMBIGUOUS");
   var contracts=new HashMap<String,ShipmentContractReference>();for(var row:contractRows)if(row.sourceKey!=null&&contracts.putIfAbsent(row.sourceKey,row)!=null)fail("SHIPMENT_CONTRACT_SOURCE_IDENTITY_AMBIGUOUS");
   syncDataQueryBySql(session.sqlSession(),sourceQuery(c),(ResultHandler<Source>)result->{
    var row=result.getResultObject().getTarget();var pack=packages.get(row.legacyPackageKey);var contract=pack==null?null:contracts.get(pack.contractSourceKey);
    row.shipmentTime=pack==null?null:pack.shipmentTime;row.contractNo=contract==null?null:contract.contractNo;handler.handleResult(result);
   });
  }
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();String kind=c.dataName();
  if(kind.equals("OrderContractRelation")){completeOrderContractRelations(b);return;}
  if(kind.equals("ShipmentOrderLine")){completeShipmentOrderLines(b);return;}
  if(kind.endsWith("Organization")){completeOrganizations(b);return;}
  if(kind.equals("Contract")&&b.rows().size()>c.batchSize()){org.slf4j.LoggerFactory.getLogger(P15DeliverySyncTask.class).info("P15_CONTRACT_IDENTITY_BATCHES:rows={},batchSize={}",b.rows().size(),c.batchSize());for(int i=0;i<b.rows().size();i+=c.batchSize())syncDataInsertBefore(new SyncBatch<>(c,b.rows().subList(i,Math.min(i+c.batchSize(),b.rows().size()))));return;}
  var keys=b.rows().stream().map(s->s.key).toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(objectKey(kind),keys));
  var current=new HashMap<String,Row>();
  // Typed scene query per table, with source identity and tenant fixed in XML.
  List<Row> selected=switch(kind){case "Contract"->mapper.selectContracts(new ContractsQuery(c.tenantId(),b.rows().stream().map(s->new ContractIdentity(s.target.companyCode,s.target.contractNo)).toList(),c.preview()));case "ShipmentContract"->mapper.selectShipmentContracts(new KeysQuery(c.tenantId(),keys,c.preview()));case "Package"->mapper.selectPackages(new KeysQuery(c.tenantId(),keys,c.preview()));case "ShipmentEvent"->mapper.selectEvents(new KeysQuery(c.tenantId(),keys,c.preview()));case "DeviceRelationship"->mapper.selectRelationships(new KeysQuery(c.tenantId(),keys,c.preview()));default->throw new IllegalArgumentException("P15_STEP_UNKNOWN");};
  var contractKeys=new HashMap<String,String>();if(kind.equals("Contract"))for(var s:b.rows())contractKeys.put(SyncFingerprint.of(Arrays.asList(s.target.companyCode,s.target.contractNo)),s.key);
  for(var row:selected){String key=kind.equals("Contract")?contractKeys.get(SyncFingerprint.of(Arrays.asList(row.companyCode,row.contractNo))):kind.equals("Package")?row.packageNo:kind.equals("ShipmentEvent")||kind.equals("DeviceRelationship")?row.sourceKey:row.sourceRecordKey;if(!keys.contains(key)||current.putIfAbsent(key,row)!=null)fail("FORMAL_IDENTITY_AMBIGUOUS");}
  for(var s:b.rows()){
   if(s.issue!=null)fail(s.issue);if(s.key==null || s.key.isBlank())fail("RAW_SOURCE_KEY_MISSING");
   var row=s.target;if(kind.equals("ShipmentContract")){var years=DppmsWarrantyYearsConverter.fromSource(s.rawWarranty);row.warrantyYears=years==null?null:years.longValue();}
   if(kind.equals("Package"))row.carrierName=row.carrierCode==null?null:c.parameters().carriers.getOrDefault(row.carrierCode,row.carrierCode);
   if(kind.equals("DeviceRelationship"))row.effectiveFrom=relationshipTime(s);
   validate(new Validation(kind,row));var old=current.get(s.key);var binding=bindings.get(s.key);s.action=SyncWriteAction.UNCHANGED;s.previous=binding==null?null:binding.fingerprint();
   if(old!=null){
    if(kind.equals("Contract")&&(!"DPPMS_ERP".equals(old.masterSourceSystem)||!Objects.equals(old.masterSourceRecordKey,s.key)) || kind.equals("Package")&&!Objects.equals(old.sourceRecordKey,s.key))fail("BUSINESS_KEY_ALREADY_OWNED");
    if(Boolean.TRUE.equals(old.deleted) || binding==null || binding.targetId()!=old.id || !binding.fingerprint().equals(fingerprint(new Validation(kind,old))))fail("EXISTING_FACT_PROTECTED");
    var next=new Row();org.springframework.beans.BeanUtils.copyProperties(old,next);var from=new org.springframework.beans.BeanWrapperImpl(row);var into=new org.springframework.beans.BeanWrapperImpl(next);
    for(var field:sourceFields(kind))into.setPropertyValue(field,from.getPropertyValue(field));
    if(business(new Validation(kind,old)).equals(business(new Validation(kind,next)))){s.target=old;continue;}
    if(kind.equals("ShipmentEvent")||kind.equals("DeviceRelationship"))fail("IMMUTABLE_HISTORY_CHANGED");
    next.updater=Long.toString(c.actorId());next.updateTime=c.startedAt();next.sourceSyncTime=c.startedAt();s.expectedVersion=old.version;
    if(next.version!=null)next.version=Math.addExact(next.version,1);s.target=next;s.action=SyncWriteAction.UPDATE;
   }else{
    if(binding!=null)fail("BOUND_TARGET_MISSING");row.id=c.preview()?null:IdWorker.getId();row.tenantId=c.tenantId();row.deleted=false;
    row.creator=Long.toString(c.actorId());row.updater=row.creator;row.createTime=c.startedAt();row.updateTime=c.startedAt();
    if(kind.equals("Contract")||kind.equals("ShipmentEvent")||kind.equals("DeviceRelationship"))row.version=0L;
    if(kind.equals("Contract"))row.authorityStatus="CONFIRMED";
    if(kind.equals("ShipmentContract")||kind.equals("Package")){row.mappingStatus="PENDING_MAPPING";row.sourceSyncTime=c.startedAt();}
    if(kind.equals("ShipmentEvent")){row.syncStatus="SYNCED";row.syncedAt=c.startedAt();}
    s.action=SyncWriteAction.INSERT;
   }
  }
 }
 private void completeOrganizations(SyncBatch<Source,Parameters> b){
  var c=b.context();String kind=completionKind(c.dataName());
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query(objectKey(kind),b.rows().stream().map(Source::getKey).toList()));
  var owned=b.rows().stream().filter(s->bindings.containsKey(s.key)).toList();
  b.rows().forEach(s->s.action=SyncWriteAction.UNCHANGED);
  if(owned.isEmpty())return;
  var current=new HashMap<Long,Row>();
  List<Row> selected=kind.equals("Contract")
    ?mapper.selectContracts(new ContractsQuery(c.tenantId(),owned.stream().map(s->new ContractIdentity(s.target.companyCode,s.target.contractNo)).toList(),c.preview()))
    :mapper.selectShipmentContracts(new KeysQuery(c.tenantId(),owned.stream().map(Source::getKey).toList(),c.preview()));
  for(var row:selected)if(current.putIfAbsent(row.id,row)!=null)fail("FORMAL_IDENTITY_AMBIGUOUS");
  for(var source:owned){
   var binding=bindings.get(source.key);var old=current.get(binding.targetId());
   if(old==null||!Objects.equals(source.target.id,binding.targetId())||Boolean.TRUE.equals(old.deleted)||!Objects.equals(old.tenantId,c.tenantId())
     ||!Objects.equals(binding.fingerprint(),fingerprint(new Validation(kind,old))))fail("EXISTING_FACT_PROTECTED");
   var next=new Row();org.springframework.beans.BeanUtils.copyProperties(old,next);
   if(kind.equals("Contract")){
    var company=c.parameters().organizations.get(new OrganizationKey("COMPANY",old.companyCode));
    if(company!=null){next.companyId=company.id();next.companyName=company.name();}
    else c.parameters().missing.merge("CONTRACT_COMPANY_DIRECTORY_MISSING",1L,Long::sum);
   }else{
    if(source.resolvedContractId!=null){
     next.contractId=source.resolvedContractId;next.companyCode=source.resolvedCompanyCode;
     var company=c.parameters().organizations.get(new OrganizationKey("COMPANY",source.resolvedCompanyCode));
     if(company!=null){next.companyId=company.id();next.companyName=company.name();}
     else c.parameters().missing.merge("SHIPMENT_COMPANY_DIRECTORY_MISSING",1L,Long::sum);
    }else c.parameters().missing.merge("SHIPMENT_CONTRACT_UNRESOLVED",1L,Long::sum);
    var department=c.parameters().organizations.get(new OrganizationKey("DEPARTMENT",old.departmentCode));
    if(department!=null){next.departmentId=department.id();next.departmentName=department.name();}
    else c.parameters().missing.merge("SHIPMENT_DEPARTMENT_UNRESOLVED",1L,Long::sum);
   }
   source.previous=binding.fingerprint();source.target=next;source.expectedVersion=old.version;
   if(!fingerprint(new Validation(kind,old)).equals(fingerprint(new Validation(kind,next)))){
    if(kind.equals("Contract"))next.version=Math.addExact(old.version,1);
    source.action=SyncWriteAction.UPDATE;
   }
  }
 }
 private void completeOrderContractRelations(SyncBatch<Source,Parameters> batch){
  var c=batch.context();var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("ORDER_CONTRACT_RELATION",batch.rows().stream().map(Source::getKey).toList()));
  var current=new HashMap<OrderContractKey,OrderContractRow>();
  var candidates=batch.rows().stream().filter(s->s.relation.contractId!=null).toList();
  if(!candidates.isEmpty())for(var row:mapper.selectOrderContractRelations(new OrderContractQuery(c.tenantId(),candidates,c.preview())))
   if(current.putIfAbsent(new OrderContractKey(row.orderId,row.contractId),row)!=null)fail("ORDER_CONTRACT_TARGET_AMBIGUOUS");
  for(var source:batch.rows()){
   source.action=SyncWriteAction.UNCHANGED;var row=source.relation;var binding=bindings.get(source.key);
   if(row.contractId==null||row.effectiveFrom==null){
    if(binding!=null)fail("ORDER_CONTRACT_BOUND_INPUT_MISSING");
    orderContractPending(batch,row.contractId==null?"CONTRACT_MISSING":"ORDER_DATE_MISSING");continue;
   }
   var old=current.get(new OrderContractKey(row.orderId,row.contractId));
   if(old!=null){
    if(binding==null){orderContractPending(batch,"EXISTING_RELATION_NOT_OWNED");continue;}
    if(!Objects.equals(old.tenantId,c.tenantId())||binding.targetId()!=old.id||Boolean.TRUE.equals(old.deleted)
      ||!binding.fingerprint().equals(orderContractFingerprint(old)))fail("ORDER_CONTRACT_EXISTING_FACT_PROTECTED");
    if(!orderContractBusiness(old).equals(orderContractBusiness(row)))orderContractPending(batch,"EXISTING_RELATION_DIFFERENT");
    source.relation=old;continue;
   }
   if(binding!=null)fail("ORDER_CONTRACT_BOUND_TARGET_MISSING");
   row.id=c.preview()?null:IdWorker.getId();row.tenantId=c.tenantId();row.deleted=false;
   row.creator=Long.toString(c.actorId());row.updater=row.creator;row.createTime=c.startedAt();row.updateTime=c.startedAt();
   source.action=SyncWriteAction.INSERT;
  }
 }
 private static void orderContractPending(SyncBatch<Source,Parameters> batch,String reason){
  batch.context().parameters().missing.merge("ORDER_CONTRACT_"+reason,1L,Long::sum);
 }
 static List<Object> orderContractBusiness(OrderContractRow row){
  return Arrays.asList(row.orderId,row.contractId,row.relationRole,row.relationSource,row.sourceSystem,
    row.salesOrderSourceKey,row.contractSourceKey,row.sourceVersion,JsonUtils.parseTree(row.sourceEvidence),row.effectiveFrom,row.effectiveTo);
 }
 static String orderContractFingerprint(OrderContractRow row){
  return SyncFingerprint.of(Arrays.asList(row.id,row.tenantId,orderContractBusiness(row),row.creator,row.createTime,row.updater,row.updateTime,row.deleted));
 }
 private void completeShipmentOrderLines(SyncBatch<Source,Parameters> batch){
  var c=batch.context();var keys=batch.rows().stream().map(Source::getKey).toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("SHIPMENT_EVENT",keys));
  var current=new HashMap<String,Row>();
  for(var row:mapper.selectEvents(new KeysQuery(c.tenantId(),keys,c.preview())))
   if(current.putIfAbsent(row.sourceKey,row)!=null)fail("FORMAL_IDENTITY_AMBIGUOUS");
  var candidates=batch.rows().stream().filter(s->Objects.equals(s.lineCandidateCount,1L)&&s.lineSourceKey!=null).toList();
  var resolved=new HashMap<String,Long>();
  if(!candidates.isEmpty())for(var match:mapper.selectShipmentOrderLines(new ShipmentOrderLineQuery(c.tenantId(),c.connectionId(),candidates)))
   if(resolved.putIfAbsent(match.shipmentKey,match.orderLineId)!=null)fail("FORMAL_LINE_IDENTITY_AMBIGUOUS");
  for(var source:batch.rows()){
   source.action=SyncWriteAction.UNCHANGED;var binding=bindings.get(source.key);var old=current.get(source.key);
   if(binding==null){shipmentLinePending(batch,source,"SHIPMENT_NOT_OWNED");continue;}
   if(old==null||binding.targetId()!=old.id||Boolean.TRUE.equals(old.deleted)||!Objects.equals(old.tenantId,c.tenantId())
     ||!binding.fingerprint().equals(fingerprint(new Validation("ShipmentEvent",old))))fail("EXISTING_FACT_PROTECTED");
   source.target=old;
   if(!Objects.equals(old.orderNo,source.rawOrderNo)||!Objects.equals(old.lineNo,source.rawLineNo)){
    shipmentLinePending(batch,source,"RAW_IDENTITY_CHANGED");continue;
   }
   if(!Objects.equals(source.lineCandidateCount,1L)){
    shipmentLinePending(batch,source,source.lineCandidateCount==null?"SOURCE_LINE_MISSING":"SOURCE_LINE_AMBIGUOUS");continue;
   }
   var targetId=resolved.get(source.key);
   if(targetId==null){shipmentLinePending(batch,source,"FORMAL_LINE_MISSING");continue;}
   if(old.orderLineId!=null){if(!old.orderLineId.equals(targetId))shipmentLinePending(batch,source,"EXISTING_REFERENCE_DIFFERENT");continue;}
   var next=new Row();org.springframework.beans.BeanUtils.copyProperties(old,next);next.orderLineId=targetId;
   source.target=next;source.previous=binding.fingerprint();source.expectedVersion=old.version;source.action=SyncWriteAction.UPDATE;
  }
 }
 private static void shipmentLinePending(SyncBatch<Source,Parameters> batch,Source source,String reason){
  long count=batch.context().parameters().missing.merge("SHIPMENT_ORDER_LINE_"+reason,1L,Long::sum);
  if(count<=20)org.slf4j.LoggerFactory.getLogger(P15DeliverySyncTask.class).warn("P15_SHIPMENT_ORDER_LINE_PENDING:tenantId={},sourceKey={},reason={}",batch.context().tenantId(),source.key,reason);
 }
 static String completionKind(String kind){return switch(kind){case "ContractOrganization"->"Contract";case "ShipmentContractOrganization"->"ShipmentContract";case "ShipmentOrderLine"->"ShipmentEvent";default->kind;};}
 public record Validation(String kind,Row row){}
 static LocalDateTime relationshipTime(Source source){
  if(source.rawRelationshipCreatedAt==null)return null;
  LocalDateTime value;try{value=LocalDateTime.parse(source.rawRelationshipCreatedAt.replace(' ','T'));}catch(java.time.format.DateTimeParseException error){throw new IllegalArgumentException("P15_RELATIONSHIP_TIME_INVALID",error);}
  if(value.getNano()%1_000_000!=0)fail("RELATIONSHIP_TIME_PRECISION_EXCEEDS_TARGET");return value;
 }
 static void validate(Validation q){var r=q.row;
  switch(q.kind){case "Contract"->{if(r.companyCode==null||!Set.of("001","002","003","004").contains(r.companyCode)||r.contractNo==null||r.contractNo.isBlank())fail("LOCAL_CONTRACT_IDENTITY_INVALID");exactDecimal(r.contractAmount,20,2);}
   case "Package"->{if(r.packageNo==null||r.packageNo.isBlank())fail("PACKAGE_NUMBER_MISSING");if(r.warrantyStartTime!=null&&r.warrantyEndTime!=null&&r.warrantyEndTime.isBefore(r.warrantyStartTime))fail("PACKAGE_WARRANTY_INTERVAL_INVALID");}
   case "ShipmentEvent"->{if(r.deviceSn==null)fail("SHIPMENT_SN_NULL");if(r.warrantyMonths!=null&&r.warrantyMonths<0)fail("WARRANTY_MONTHS_NEGATIVE");}
   case "DeviceRelationship"->{if(r.sourceDeviceSn==null||r.targetDeviceSn==null||r.sourceDeviceSn.equalsIgnoreCase(r.targetDeviceSn))fail("RELATIONSHIP_SN_INVALID");}
  }
  validateCapacity(q);
 }
 static List<String> sourceFields(String kind){return switch(kind){case "ShipmentContract"->List.of("sourceSystem","sourceRecordKey","contractNo","departmentCode","contractType","customerName","projectName","marketCode","marketName","systemSourceKey","systemName","remark","warrantyYears");case "Package"->List.of("sourceSystem","sourceRecordKey","packageNo","shipmentTime","warrantyStartTime","warrantyEndTime","receiverName","expressNo","carrierCode","shipmentContractSourceKey","carrierName");case "ShipmentEvent"->List.of("sourceSystem","sourceKey","deviceSn","shipmentTime","packageNo","legacyPackageKey","contractNo","eventType","rmaMarked","warrantyStartDate","warrantyMonths","rmaNo","rmaRelatedSn","sourceUpdatedAt","orderNo","lineNo","productCode","secondarySn","secondaryItem","internalSerialNo","isRma","profitCenter","soleAgentSuffix","syncTime","uuid");case "DeviceRelationship"->List.of("sourceSystem","sourceKey","sourceDeviceSn","targetDeviceSn","relationshipType","contractNo","effectiveFrom","sourceVersion","sourceItem","targetItem");case "Contract"->List.of("companyCode","contractNo","customerCode","customerName","currencyName","currencyCode","contractAmount","contractCreateTime","projectName","projectCode","marketCode","marketName","departmentCode","departmentName","industryCode","industryName","marketingRepresentativeCode","marketingRepresentativeName","secondaryRepresentativeCode","systemName","masterSourceSystem","masterSourceRecordKey"); default->throw new IllegalArgumentException("P15_STEP_UNKNOWN");};}
 static List<Object> business(Validation q){var bean=new org.springframework.beans.BeanWrapperImpl(q.row);var result=new ArrayList<Object>();for(var field:sourceFields(q.kind)){var v=bean.getPropertyValue(field);result.add(v instanceof BigDecimal n?n.stripTrailingZeros():v);}return result;}
 static String fingerprint(Validation q){return SyncFingerprint.of(switch(q.kind){case "Contract"->Arrays.asList(q.row.id,q.row.tenantId,q.row.companyId,q.row.companyCode,q.row.companyName,q.row.contractNo,q.row.masterSourceSystem,q.row.masterSourceRecordKey,q.row.masterSourceVersion,q.row.contractType,q.row.customerId,q.row.customerCode,q.row.customerName,q.row.contractName,q.row.contractAmount==null?null:q.row.contractAmount.stripTrailingZeros(),q.row.currencyCode,q.row.currencyName,q.row.contractCreateTime,q.row.authorityStatus,q.row.sourceLifecycleStatus,q.row.effectiveDate,q.row.expiryDate,q.row.projectName,q.row.projectCode,q.row.marketCode,q.row.marketName,q.row.departmentCode,q.row.departmentName,q.row.systemCode,q.row.systemName,q.row.expendCode,q.row.expendName,q.row.industryCode,q.row.industryName,q.row.marketingRepresentativeCode,q.row.marketingRepresentativeName,q.row.secondaryRepresentativeCode,q.row.sourceSyncTime,q.row.sourceUpdatedAt,q.row.status,q.row.version,q.row.creator,q.row.createTime,q.row.updater,q.row.updateTime,q.row.deleted);case "ShipmentContract"->Arrays.asList(q.row.id,q.row.tenantId,q.row.contractId,q.row.contractNo,q.row.companyId,q.row.companyCode,q.row.companyName,q.row.departmentId,q.row.departmentCode,q.row.departmentName,q.row.contractType,q.row.customerName,q.row.projectName,q.row.marketCode,q.row.marketName,q.row.systemSourceKey,q.row.systemCode,q.row.systemName,q.row.warrantyYears,q.row.remark,q.row.mappingStatus,q.row.sourceSystem,q.row.sourceRecordKey,q.row.sourceSyncTime,q.row.creator,q.row.createTime,q.row.updater,q.row.updateTime,q.row.deleted);case "Package"->Arrays.asList(q.row.id,q.row.tenantId,q.row.shipmentContractRefId,q.row.packageNo,q.row.shipmentTime,q.row.warrantyStartTime,q.row.warrantyEndTime,q.row.receiverName,q.row.expressNo,q.row.carrierName,q.row.mappingStatus,q.row.sourceSystem,q.row.sourceRecordKey,q.row.sourceSyncTime,q.row.creator,q.row.createTime,q.row.updater,q.row.updateTime,q.row.deleted,q.row.carrierCode,q.row.shipmentContractSourceKey);case "ShipmentEvent"->Arrays.asList(q.row.id,q.row.deviceSn,q.row.shipmentTime,q.row.packageNo,q.row.contractNo,q.row.eventType,q.row.warrantyStartDate,q.row.warrantyMonths,q.row.rmaNo,q.row.relatedDeviceSn,q.row.sourceSystem,q.row.sourceKey,q.row.sourceVersion,q.row.sourceUpdatedAt,q.row.syncedAt,q.row.syncStatus,q.row.version,q.row.creator,q.row.createTime,q.row.updater,q.row.updateTime,q.row.deleted,q.row.tenantId,q.row.legacyPackageKey,q.row.rmaRelatedSn,q.row.rmaMarked,q.row.orderLineId,q.row.sourcePayload,q.row.orderNo,q.row.lineNo,q.row.productCode,q.row.secondarySn,q.row.secondaryItem,q.row.internalSerialNo,q.row.isRma,q.row.profitCenter,q.row.soleAgentSuffix,q.row.syncTime,q.row.uuid);case "DeviceRelationship"->Arrays.asList(q.row.id,q.row.sourceDeviceSn,q.row.targetDeviceSn,q.row.relationshipType,q.row.contractNo,q.row.effectiveFrom,q.row.effectiveTo,q.row.sourceSystem,q.row.sourceKey,q.row.sourceVersion,q.row.version,q.row.creator,q.row.createTime,q.row.updater,q.row.updateTime,q.row.deleted,q.row.tenantId,q.row.sourceItem,q.row.targetItem); default->throw new IllegalArgumentException("P15_STEP_UNKNOWN");});}
 static void validateCapacity(Validation q){var r=q.row;switch(q.kind){case "Contract"->{checkTextLength(r.companyCode,64);checkTextLength(r.companyName,255);checkTextLength(r.contractNo,64);checkTextLength(r.masterSourceSystem,32);checkTextLength(r.masterSourceRecordKey,128);checkTextLength(r.masterSourceVersion,64);checkTextLength(r.contractType,32);checkTextLength(r.customerCode,64);checkTextLength(r.customerName,512);checkTextLength(r.contractName,512);checkTextLength(r.currencyCode,32);checkTextLength(r.currencyName,32);if(r.contractCreateTime!=null&&r.contractCreateTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.authorityStatus,32);checkTextLength(r.sourceLifecycleStatus,32);checkTextLength(r.projectName,512);checkTextLength(r.projectCode,80);checkTextLength(r.marketCode,64);checkTextLength(r.marketName,128);checkTextLength(r.departmentCode,64);checkTextLength(r.departmentName,128);checkTextLength(r.systemCode,64);checkTextLength(r.systemName,255);checkTextLength(r.expendCode,64);checkTextLength(r.expendName,255);checkTextLength(r.industryCode,64);checkTextLength(r.industryName,128);checkTextLength(r.marketingRepresentativeCode,64);checkTextLength(r.marketingRepresentativeName,128);checkTextLength(r.secondaryRepresentativeCode,64);if(r.sourceSyncTime!=null&&r.sourceSyncTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");if(r.sourceUpdatedAt!=null&&r.sourceUpdatedAt.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.status,32);checkTextLength(r.creator,64);if(r.createTime!=null&&r.createTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.updater,64);if(r.updateTime!=null&&r.updateTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");}case "ShipmentContract"->{checkTextLength(r.contractNo,64);checkTextLength(r.companyCode,64);checkTextLength(r.companyName,255);checkTextLength(r.departmentCode,64);checkTextLength(r.departmentName,128);checkTextLength(r.contractType,32);checkTextLength(r.customerName,512);checkTextLength(r.projectName,512);checkTextLength(r.marketCode,64);checkTextLength(r.marketName,128);checkTextLength(r.systemSourceKey,64);checkTextLength(r.systemCode,64);checkTextLength(r.systemName,128);checkTextLength(r.remark,4096);checkTextLength(r.mappingStatus,32);checkTextLength(r.sourceSystem,32);checkTextLength(r.sourceRecordKey,128);if(r.sourceSyncTime!=null&&r.sourceSyncTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.creator,64);if(r.createTime!=null&&r.createTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.updater,64);if(r.updateTime!=null&&r.updateTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");}case "Package"->{checkTextLength(r.packageNo,128);if(r.shipmentTime!=null&&r.shipmentTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");if(r.warrantyStartTime!=null&&r.warrantyStartTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");if(r.warrantyEndTime!=null&&r.warrantyEndTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.receiverName,512);checkTextLength(r.expressNo,512);checkTextLength(r.carrierName,256);checkTextLength(r.mappingStatus,32);checkTextLength(r.sourceSystem,32);checkTextLength(r.sourceRecordKey,128);if(r.sourceSyncTime!=null&&r.sourceSyncTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.creator,64);if(r.createTime!=null&&r.createTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.updater,64);if(r.updateTime!=null&&r.updateTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.carrierCode,256);checkTextLength(r.shipmentContractSourceKey,128);}case "ShipmentEvent"->{checkTextLength(r.deviceSn,100);if(r.shipmentTime!=null&&r.shipmentTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.packageNo,128);checkTextLength(r.contractNo,64);checkTextLength(r.eventType,32);checkTextLength(r.rmaNo,128);checkTextLength(r.relatedDeviceSn,100);checkTextLength(r.sourceSystem,32);checkTextLength(r.sourceKey,128);checkTextLength(r.sourceVersion,64);if(r.sourceUpdatedAt!=null&&r.sourceUpdatedAt.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");if(r.syncedAt!=null&&r.syncedAt.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.syncStatus,32);checkTextLength(r.creator,64);if(r.createTime!=null&&r.createTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.updater,64);if(r.updateTime!=null&&r.updateTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.legacyPackageKey,128);checkTextLength(r.rmaRelatedSn,100);checkTextLength(r.orderNo,64);checkTextLength(r.lineNo,64);checkTextLength(r.productCode,64);checkTextLength(r.secondarySn,100);checkTextLength(r.secondaryItem,64);checkTextLength(r.internalSerialNo,128);checkTextLength(r.profitCenter,32);checkTextLength(r.soleAgentSuffix,32);if(r.syncTime!=null&&r.syncTime.getNano()%1000000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.uuid,64);}case "DeviceRelationship"->{checkTextLength(r.sourceDeviceSn,100);checkTextLength(r.targetDeviceSn,100);checkTextLength(r.relationshipType,32);checkTextLength(r.contractNo,64);if(r.effectiveFrom!=null&&r.effectiveFrom.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");if(r.effectiveTo!=null&&r.effectiveTo.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.sourceSystem,32);checkTextLength(r.sourceKey,128);checkTextLength(r.sourceVersion,64);checkTextLength(r.creator,64);if(r.createTime!=null&&r.createTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.updater,64);if(r.updateTime!=null&&r.updateTime.getNano()%1000000!=0)fail("SOURCE_TIME_PRECISION_INVALID");checkTextLength(r.sourceItem,15);checkTextLength(r.targetItem,15);} default->throw new IllegalArgumentException("P15_STEP_UNKNOWN");}}
 static String objectKey(String name){return switch(name){case "Contract"->"CONTRACT";case "ShipmentContract"->"SHIPMENT_CONTRACT";case "Package"->"PACKAGE";case "ShipmentEvent"->"SHIPMENT_EVENT";case "DeviceRelationship"->"DEVICE_RELATIONSHIP";default->throw new IllegalArgumentException("P15_STEP_UNKNOWN");};}
 static void fail(String code){throw new IllegalArgumentException("P15_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){
  if("ShipmentOrderLine".equals(b.context().dataName())){
   var changed=b.matching(s->s.action==SyncWriteAction.UPDATE);if(changed.rows().isEmpty()||b.context().preview())return;
   if(mapper.updateShipmentOrderLine(changed)!=changed.rows().size())fail("SHIPMENT_ORDER_LINE_TARGET_CHANGED");return;
  }
  if(b.context().dataName().endsWith("Organization")){
   var changed=b.matching(s->s.action==SyncWriteAction.UPDATE);if(changed.rows().isEmpty())return;
   int count=b.context().dataName().equals("ContractOrganization")?mapper.updateContractOrganization(changed):mapper.updateShipmentContractOrganization(changed);
   if(count!=changed.rows().size())fail("ORGANIZATION_TARGET_CHANGED");return;
  }
  super.syncDataInsert(b.matching(s->s.action==SyncWriteAction.INSERT));super.syncDataUpdate(b.matching(s->s.action==SyncWriteAction.UPDATE));
 }
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){
  if("OrderContractRelation".equals(b.context().dataName())){
   b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT)
     .map(s->new SyncIdentityStore.Save("ORDER_CONTRACT_RELATION",s.key,s.relation.id,orderContractFingerprint(s.relation),null)).toList());return;
  }
  String kind=completionKind(b.context().dataName());b.context().identities().saveAll(b.rows().stream().filter(s->s.action!=SyncWriteAction.UNCHANGED).map(s->new SyncIdentityStore.Save(objectKey(kind),s.key,s.target.id,fingerprint(new Validation(kind,s.target)),s.previous)).toList());}
 @lombok.Data public static class Source {private String key,issue,rawWarranty,rawRelationshipCreatedAt,previous,resolvedCompanyCode;private Long expectedVersion,resolvedContractId,lineCandidateCount;private String lineSourceKey,lineSourceSystem,lineCompanyCode,lineType,rawOrderNo,rawLineNo,formalLineNo,lineProductCode;private Row target;private OrderContractRow relation;private SyncWriteAction action;}
 @lombok.Data public static class Row {private Long id;private Long tenantId;private Long companyId;private String companyCode;private String companyName;private String contractNo;private String masterSourceSystem;private String masterSourceRecordKey;private String masterSourceVersion;private String contractType;private Long customerId;private String customerCode;private String customerName;private String contractName;private BigDecimal contractAmount;private String currencyCode;private String currencyName;private LocalDateTime contractCreateTime;private String authorityStatus;private String sourceLifecycleStatus;private LocalDate effectiveDate;private LocalDate expiryDate;private String projectName;private String projectCode;private String marketCode;private String marketName;private String departmentCode;private String departmentName;private String systemCode;private String systemName;private String expendCode;private String expendName;private String industryCode;private String industryName;private String marketingRepresentativeCode;private String marketingRepresentativeName;private String secondaryRepresentativeCode;private LocalDateTime sourceSyncTime;private LocalDateTime sourceUpdatedAt;private String status;private Long version;private String creator;private LocalDateTime createTime;private String updater;private LocalDateTime updateTime;private Boolean deleted;private Long contractId;private Long departmentId;private String systemSourceKey;private Long warrantyYears;private String remark;private String mappingStatus;private String sourceSystem;private String sourceRecordKey;private Long shipmentContractRefId;private String packageNo;private LocalDateTime shipmentTime;private LocalDateTime warrantyStartTime;private LocalDateTime warrantyEndTime;private String receiverName;private String expressNo;private String carrierName;private String carrierCode;private String deviceSn;private String eventType;private LocalDate warrantyStartDate;private Long warrantyMonths;private String rmaNo;private String relatedDeviceSn;private String sourceKey;private String sourceVersion;private LocalDateTime syncedAt;private String syncStatus;private String legacyPackageKey;private String rmaRelatedSn;private Boolean rmaMarked;private Long orderLineId;private String sourcePayload;private String orderNo;private String lineNo;private String productCode;private String secondarySn;private String secondaryItem;private String internalSerialNo;private Long isRma;private String profitCenter;private String soleAgentSuffix;private LocalDateTime syncTime;private String uuid;private String sourceDeviceSn;private String targetDeviceSn;private String relationshipType;private LocalDateTime effectiveFrom;private LocalDateTime effectiveTo;private String sourceItem;private String targetItem;private String shipmentContractSourceKey;}
 @lombok.Data public static class OrderContractRow {
  private Long id,tenantId,orderId,contractId;
  private String relationRole,relationSource,sourceSystem,salesOrderSourceKey,contractSourceKey,sourceVersion,sourceEvidence,creator,updater;
  private LocalDateTime effectiveFrom,effectiveTo,createTime,updateTime;
  private Boolean deleted;
 }
 public record OrderContractKey(Long orderId,Long contractId){}
 public record OrderContractQuery(long tenantId,List<Source> rows,boolean preview){}
 @lombok.Data public static class Carrier {private String code,label;}
  @lombok.Data public static class ContractCustomer {private String rawKey,issue,companyCode,contractNo,customerCode,customerName,orderType,orderNo;}
 @lombok.Data public static class ShipmentPackageReference {private String sourceKey,contractSourceKey;private LocalDateTime shipmentTime;}
 @lombok.Data public static class ShipmentContractReference {private String sourceKey,contractNo;}
 public record ShipmentOrderLineQuery(long tenantId,long connectionId,List<Source> rows){}
 @lombok.Data public static class ResolvedOrderLine {private String shipmentKey;private Long orderLineId;}
 public record TenantQuery(long tenantId){}
 public record PrerequisiteQuery(long tenantId,long connectionId){}
 public record KeysQuery(long tenantId,List<String> keys,boolean preview){}
 public record ContractIdentity(String companyCode,String contractNo){}
 public record ContractsQuery(long tenantId,List<ContractIdentity> keys,boolean preview){}
 public interface SourceMapper {void selectShipmentOrderLine(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);long countShipmentEvents(SourceQuery<Parameters> query);void selectShipmentContract(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);void selectPackage(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);void selectShipmentEvent(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);void selectDeviceRelationship(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);List<ContractCustomer> selectContractCustomers(SourceQuery<Parameters> query);List<Row> selectContractShipmentFields(SourceQuery<Parameters> query);List<Row> selectContractReceivableFields(SourceQuery<Parameters> query);List<ShipmentPackageReference> selectShipmentPackageReferences(SourceQuery<Parameters> query);List<ShipmentContractReference> selectShipmentContractReferences(SourceQuery<Parameters> query);}
 @Mapper public interface TargetMapper {void selectOrderContractRelation(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);List<OrderContractRow> selectOrderContractRelations(@Param("query") OrderContractQuery query);int insertOrderContractRelation(SyncBatch<Source,Parameters> batch);List<ResolvedOrderLine> selectShipmentOrderLines(@Param("query") ShipmentOrderLineQuery query);int updateShipmentOrderLine(SyncBatch<Source,Parameters> batch);List<Carrier> selectCarriers(@Param("query") TenantQuery query);@com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true",dataPermission="true") int packageSourceColumnReady();String latestP24Execution(@Param("query") PrerequisiteQuery query);List<Row> selectContracts(@Param("query") ContractsQuery query);List<Row> selectShipmentContracts(@Param("query") KeysQuery query);List<Row> selectPackages(@Param("query") KeysQuery query);List<Row> selectEvents(@Param("query") KeysQuery query);List<Row> selectRelationships(@Param("query") KeysQuery query);void selectContract(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);void selectContractOrganization(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);void selectShipmentContractOrganization(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);int updateContractOrganization(SyncBatch<Source,Parameters> batch);int updateShipmentContractOrganization(SyncBatch<Source,Parameters> batch);int insertContract(SyncBatch<Source,Parameters> batch);int insertShipmentContract(SyncBatch<Source,Parameters> batch);int insertPackage(SyncBatch<Source,Parameters> batch);int insertShipmentEvent(SyncBatch<Source,Parameters> batch);int insertDeviceRelationship(SyncBatch<Source,Parameters> batch); int updateContract(RowParameter<Source,Parameters> row);int updateShipmentContract(RowParameter<Source,Parameters> row);int updatePackage(RowParameter<Source,Parameters> row);}
}
