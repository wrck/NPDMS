package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** Historical scope and product quantity detail; no allocation confirmation, version advancement or SN effects. */
@Component @RequiredArgsConstructor
public class P22ProductScopeSyncTask extends AbstractSyncTask<P22ProductScopeSyncTask.Source,P22ProductScopeSyncTask.Parameters> {
 private final TargetMapper mapper;private final BaseSyncProjectReferences projects;
 @Override public String taskCode(){return "BASE_P22";}
 @Override public String taskName(){return "项目产品实施范围历史";}
 public static final class Parameters {private BaseSyncProjectReferences.CurrentManageScope manageScope;private final Map<String,Department> departments=new HashMap<>();private final Map<Long,String> companies=new HashMap<>();private final Map<OrderKey,OrderLine> ownedLines=new HashMap<>();private final Map<String,List<Long>> scopeIds=new HashMap<>();private long zeroQuantityPending,missingDepartmentPending;private boolean departmentSchemaReady,ownedLinesLoaded,scopeIdentitiesLoaded;}
 @Override protected Parameters createParameters(SyncContext<?> c){return new Parameters();}
 @Override public void work(SyncContext<Parameters> c){
  c.parameters().manageScope=projects.currentManageScope(new BaseSyncProjectReferences.ManageScopeQuery(c.tenantId(),c.actorId()));
  if(c.preview()){c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.runState().warning("P22_DEPARTMENT_ID_POSTFILL_PENDING:-1");c.runState().warning("P22_ORDER_LINE_ID_POSTFILL_PENDING:-1");}
  if(mapper.pendingOrderSchemaReady()!=1){if(!c.preview())fail("EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference");c.runState().warning("P22_EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference");}
  c.parameters().departmentSchemaReady=mapper.pendingDepartmentSchemaReady()==1;
  try(var source=c.source().open(c.connectionId())){
   for(var company:source.mapper(SourceMapper.class).selectCompanies(new SourceQuery<>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters())))
    if(c.parameters().companies.putIfAbsent(company.id,company.code)!=null||c.parameters().companies.size()>c.maxRows())fail("SOURCE_COMPANY_AMBIGUOUS_OR_CAPACITY");
  }
  for(var d:mapper.selectDepartments(new DepartmentCatalogueQuery(c.tenantId())))if(d.code==null||d.id==null||d.id<=0||c.parameters().departments.putIfAbsent(d.code,d)!=null)fail("DEPARTMENT_CODE_AMBIGUOUS");
  syncData(c.forData("ProductScope",false,true));
  if(c.parameters().zeroQuantityPending>0)c.runState().warning("P22_ZERO_QUANTITY_PENDING:"+c.parameters().zeroQuantityPending);
  if(c.parameters().missingDepartmentPending>0){
   c.runState().warning("P22_DEPARTMENT_SOURCE_MISSING_PENDING:"+c.parameters().missingDepartmentPending);
   if(!c.parameters().departmentSchemaReady)c.runState().warning("P22_EXECUTION_SCHEMA_REQUIRED:department_pending_reference");
  }
  if(!c.preview()){
   var q=new CurrentRunQuery(c.tenantId(),c.connectionId());
   mapper.backfillDepartmentIds(q);
   if(mapper.countUnresolvedCodedDepartments(q)!=0)fail("DEPARTMENT_POSTFILL_UNRESOLVED");
   mapper.backfillOrderLineIds(q);
   long pending=mapper.countPendingOrderLines(q);if(pending>0)c.runState().warning("P22_ORDER_LINE_POSTFILL_UNRESOLVED:"+pending);
  }
 }
 @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  // DPPMS-SCOPE-039: retain positive-quantity constraints; enumerate zero rows as pending, without target/binding writes.
  for(var iterator=b.rows().iterator();iterator.hasNext();){var s=iterator.next();
   if(s.sourceId==null||s.sourceId<=0||!"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");
   var quantity=s.projectQuantity==null?s.orderQuantity:s.projectQuantity;
   if(quantity!=null&&quantity.signum()==0){b.context().parameters().zeroQuantityPending++;iterator.remove();}
  }
  if(b.rows().isEmpty())return;
  var c=b.context();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PRODUCT_SCOPE",keys));
  var references=projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),b.rows().stream().map(s->s.canonicalProjectId.toString()).distinct().toList(),c.preview(),c.parameters().manageScope));
  var projectIds=references.values().stream().map(BaseSyncProjectReferences.Identity::id).distinct().toList();var snapshots=new HashMap<Long,Snapshot>();
  for(var p:mapper.selectSnapshots(new ProjectsQuery(c.tenantId(),projectIds,c.preview())))if(snapshots.putIfAbsent(p.projectId,p)!=null)fail("PROJECT_SNAPSHOT_AMBIGUOUS");
  for(var s:b.rows()){
   if(s.sourceId==null || s.sourceId<=0 || !"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");
   var p=references.get(s.canonicalProjectId.toString());if(p==null || !s.projectType.equals(p.projectType()))fail("PROJECT_IDENTITY_CHANGED");
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   for(var v:Arrays.asList(s.orderNumber,s.lineNum,s.itemCode))if(v==null || v.isBlank())fail("ORDER_IDENTITY_MISSING");
   s.formalProjectId=p.id();
  }
  if(!c.parameters().scopeIdentitiesLoaded){
   for(var identity:mapper.selectScopeIdentities(new ScopeIdentityQuery(c.tenantId(),c.preview())))
    c.parameters().scopeIds.computeIfAbsent(identity.sourceEvidence,k->new ArrayList<>()).add(identity.id);
   c.parameters().scopeIdentitiesLoaded=true;
  }
  var existingIds=b.rows().stream().flatMap(s->c.parameters().scopeIds.getOrDefault(s.evidence(),List.of()).stream()).distinct().toList();var existing=new HashMap<String,ScopeRow>();
  for(var row:mapper.selectScopes(new ScopesQuery(c.tenantId(),existingIds,c.preview())))if(existing.putIfAbsent(row.sourceEvidence,row)!=null)fail("SCOPE_IDENTITY_AMBIGUOUS");
  var scopeIds=existing.values().stream().map(ScopeRow::getId).toList();var details=new HashMap<Long,List<DetailRow>>();
  if(!scopeIds.isEmpty())for(var d:mapper.selectDetails(new ScopesQuery(c.tenantId(),scopeIds,c.preview())))details.computeIfAbsent(d.deliveryScopeId,k->new ArrayList<>()).add(d);
  // Load owned references once per run when an existing resolved row first needs checking.
  // The existing exact OrderKey lookup remains unchanged; fresh raw rows need no catalogue.
  if(!c.parameters().ownedLinesLoaded&&existing.values().stream().anyMatch(r->r.orderLineId!=null&&r.orderLineId>0)){
   for(var line:mapper.selectOwnedOrderCatalogue(new OwnedOrderCatalogueQuery(c.tenantId(),c.connectionId())))
    if(c.parameters().ownedLines.putIfAbsent(new OrderKey(line.companyCode,line.orderNo,line.lineNo,line.productCode),line)!=null)fail("OWNED_ORDER_LINE_AMBIGUOUS");
   c.parameters().ownedLinesLoaded=true;
  }
  var ownedLines=c.parameters().ownedLines;
  for(var s:b.rows()){
   var p=snapshots.get(s.formalProjectId);var identity=references.get(s.canonicalProjectId.toString());
   if(p!=null&&!Objects.equals(p.projectVersion,identity.version()))fail("PROJECT_VERSION_CHANGED");
   if(p==null)p=new Snapshot().setProjectId(s.formalProjectId).setProjectCode(s.originalProjectCode).setProjectName(s.originalProjectName).setScopeVersion(0L);
   var rawCompany=s.originalCompanyId==null?null:c.parameters().companies.get(s.originalCompanyId);
   if(rawCompany!=null&&!rawCompany.isBlank()){
    var company=DppmsCompanyCodeConverter.fromSource(rawCompany);
    if(p.companyCode!=null&&!p.companyCode.equals(company))fail("PROJECT_COMPANY_DIFFERS_FROM_SOURCE");p.companyCode=company;
   }
   String code=s.originalDepartmentCode;if(code!=null&&code.matches("31120[2-9]|311210"))code="1621"+code.substring(4);
   var department=c.parameters().departments.get(code);
   if(code==null||code.isBlank()){
    department=null;
    if(!c.preview()&&!c.parameters().departmentSchemaReady)fail("EXECUTION_SCHEMA_REQUIRED:department_pending_reference");
    c.parameters().missingDepartmentPending++;
    p.setDepartmentCode(code).setDepartmentName(null).setDepartmentVersion(null);
   }else{
    if(department==null||department.version==null||department.name==null||department.name.isBlank())fail("DEPARTMENT_CODE_UNRESOLVED:"+code);
    // Match known source offices by tenant and exact code. Missing source codes retain their original value.
    p.setDepartmentCode(department.code).setDepartmentName(department.name).setDepartmentVersion(department.version);
   }
   var quantity=s.projectQuantity==null?s.orderQuantity:s.projectQuantity;if(quantity==null||quantity.signum()<=0)fail("POSITIVE_QUANTITY_REQUIRED:"+s.sourceId);
   var old=existing.get(s.evidence());var binding=bindings.get(s.sourceId.toString());s.previous=binding==null?null:binding.fingerprint();s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){
    var ds=details.getOrDefault(old.id,List.of());if(binding==null || binding.targetId()!=old.id || Boolean.TRUE.equals(old.deleted) || ds.size()!=1 || !binding.fingerprint().equals(fingerprint(old,ds.getFirst())))fail("EXISTING_SCOPE_PROTECTED");
    if(!Objects.equals(old.departmentCode,p.departmentCode)||old.departmentId==null||old.departmentId!=-1L&&(department==null||!Objects.equals(old.departmentId,department.id)))fail("DEPARTMENT_IDENTITY_CHANGED");
    if(old.orderLineId==null||old.orderLineId!=-1L&&old.orderLineId<=0)fail("ORDER_LINE_IDENTITY_CHANGED");
    if(old.orderLineId>0){var line=ownedLines.get(new OrderKey(old.projectCompanyCode,old.orderNo,old.lineNo,old.productCode));
     if(line==null||!Objects.equals(old.orderLineId,line.id)||!Objects.equals(old.orderSourceSystem,line.sourceSystem)||!Objects.equals(old.orderCompanyCode,line.companyCode)||!Objects.equals(old.orderCompanyName,line.companyName)||!Objects.equals(old.orderType,line.orderType))fail("ORDER_LINE_IDENTITY_CHANGED");}
    if(!Objects.equals(old.projectCompanyCode,p.companyCode)||!Objects.equals(old.orderNo,s.orderNumber)||!Objects.equals(old.lineNo,s.lineNum))fail("ORDER_BUSINESS_KEY_CHANGED");
    if(!Objects.equals(old.projectId,s.formalProjectId) || !Objects.equals(old.productCode,s.itemCode) || !Objects.equals(old.productDesc,s.itemName) || old.allocatedQty.compareTo(quantity)!=0 || !same(old.deliveredQuantity,s.deliverQuantity) || !same(old.openQuantity,s.openQuantity))fail("IMMUTABLE_SOURCE_CHANGED");
    s.target=old;s.detail=ds.getFirst();continue;
   }
   if(binding!=null)fail("BOUND_SCOPE_MISSING");
   var row=build(new Plan(s,p,quantity,c));var detail=new DetailRow();detail.id=c.preview()?null:IdWorker.getId();detail.tenantId=c.tenantId();detail.deliveryScopeId=row.id;detail.detailSequence=1;
   detail.productCode=row.productCode;detail.productName=row.productDesc;detail.allocatedQty=row.allocatedQty;detail.sourceRecordKey=s.evidence()+":PRODUCT";detail.detailStatus="ACTIVE";detail.version=0;detail.deleted=false;
   detail.creator=row.creator;detail.updater=row.updater;detail.createTime=row.createTime;detail.updateTime=row.updateTime;s.target=row;s.detail=detail;s.action=SyncWriteAction.INSERT;
  }
 }
 record Plan(Source source,Snapshot project,BigDecimal quantity,SyncContext<Parameters> context){}
 static ScopeRow build(Plan q){var s=q.source;var p=q.project;var c=q.context;var r=new ScopeRow();
  r.id=c.preview()?null:IdWorker.getId();r.tenantId=c.tenantId();r.projectId=s.formalProjectId;r.projectCode=p.projectCode;r.projectName=p.projectName;r.projectCompanyCode=p.companyCode;r.projectCompanyName=p.companyName;
  r.projectDepartmentCode=p.departmentCode;r.projectDepartmentName=p.departmentName;r.projectCustomerCode=p.customerCode;r.projectCustomerName=p.customerName;r.projectManagerEmployeeNo=p.managerEmployeeNo;r.projectManagerName=p.managerName;
  r.departmentId=-1L;r.departmentCode=p.departmentCode;r.departmentName=p.departmentName;r.departmentVersion=p.departmentVersion;
  r.orderLineId=-1L;r.orderCompanyCode=p.companyCode;r.orderNo=s.orderNumber;r.lineNo=s.lineNum;r.productCode=s.itemCode;r.productDesc=s.itemName;
  r.allocatedQty=exactDecimal(q.quantity,18,6);r.deliveredQuantity=exactDecimal(s.deliverQuantity,18,6);r.openQuantity=exactDecimal(s.openQuantity,18,6);checkTextLength(r.productDesc,255);
  r.allocationConfirmed=false;r.scopeStatus="ACTIVE";r.allocationVersion=Math.addExact(p.scopeVersion,1);r.allocationSource="DPPMS";r.sourceEvidence=s.evidence();r.effectiveFrom=c.startedAt();r.status="ENABLED";r.version=0L;r.deleted=false;
  r.creator=Long.toString(c.actorId());r.updater=r.creator;r.createTime=c.startedAt();r.updateTime=c.startedAt();return r;
 }
 static boolean same(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
 // Derived ids/order snapshots are checked separately against owned business keys; postfill preserves the raw baseline.
 static String scopeFingerprint(ScopeRow r){return SyncFingerprint.of(Arrays.asList(r.getId(),r.getTenantId(),r.getProjectId(),r.getProjectCode(),r.getProjectName(),r.getProjectCompanyCode(),r.getProjectCompanyName(),r.getProjectDepartmentCode(),r.getProjectDepartmentName(),r.getProjectCustomerCode(),r.getProjectCustomerName(),r.getProjectManagerEmployeeNo(),r.getProjectManagerName(),r.getCurrentOrderLineId(),r.getOrderNo(),r.getLineNo(),r.getProductCode(),r.getProductDesc(),r.getAllocatedQty()==null?null:r.getAllocatedQty().stripTrailingZeros(),r.getScopeStatus(),r.getAllocationVersion(),r.getAllocationSource(),r.getChangeReason(),r.getDepartmentCode(),r.getDepartmentName(),r.getDepartmentVersion(),r.getSourceEvidence(),r.getEffectiveFrom(),r.getEffectiveTo(),r.getStatus(),r.getVersion(),r.getCreator(),r.getCreateTime(),r.getUpdater(),r.getUpdateTime(),r.getDeleted(),r.getAllocationConfirmed(),r.getDeliveredQuantity()==null?null:r.getDeliveredQuantity().stripTrailingZeros(),r.getOpenQuantity()==null?null:r.getOpenQuantity().stripTrailingZeros()));}
 static String detailFingerprint(DetailRow r){return SyncFingerprint.of(Arrays.asList(r.getId(),r.getTenantId(),r.getDeliveryScopeId(),r.getDetailSequence(),r.getSerialNo(),r.getProductCode(),r.getProductName(),r.getDeviceTypeCode(),r.getDeviceTypeName(),r.getDeliveryBatchNo(),r.getSourceRecordKey(),r.getAllocatedQty()==null?null:r.getAllocatedQty().stripTrailingZeros(),r.getDetailStatus(),r.getSourceSnapshot(),r.getRemark(),r.getVersion(),r.getCreator(),r.getCreateTime(),r.getUpdater(),r.getUpdateTime(),r.getDeleted()));}
 static String fingerprint(ScopeRow row,DetailRow detail){return SyncFingerprint.of(List.of(scopeFingerprint(row),detailFingerprint(detail)));}
 static void fail(String code){throw new IllegalArgumentException("P22_"+code);}
 @Override protected SyncCounts syncDataCount(SyncBatch<Source,Parameters> b){return SyncCounts.fromActions(b.rows(),Source::getAction);}
 @Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);super.syncDataInsert(fresh);if(!fresh.rows().isEmpty()&&mapper.insertDetails(fresh)!=fresh.rows().size())fail("DETAIL_INSERT_CONFLICT");}
 @Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){
  b.context().identities().saveAll(b.rows().stream().map(s->new SyncIdentityStore.Save("PRODUCT_SCOPE",s.sourceId.toString(),s.target.id,fingerprint(s.target,s.detail),s.previous)).toList());
  for(var s:b.matching(row->row.action==SyncWriteAction.INSERT).rows())
   b.context().parameters().scopeIds.computeIfAbsent(s.evidence(),k->new ArrayList<>()).add(s.target.id);
 }
 @lombok.Data public static class Source {private Long sourceId,originalProjectId,canonicalProjectId,formalProjectId,originalCompanyId;private String originalProjectCode,originalProjectName,originalDepartmentCode,previous,projectType,itemCode,itemName,orderNumber,lineNum;private BigDecimal projectQuantity,orderQuantity,deliverQuantity,openQuantity;private ScopeRow target;private DetailRow detail;private SyncWriteAction action;String evidence(){return "DPPMS:pm_project_product_line:"+originalProjectId+":"+sourceId;}}
 @lombok.Data public static class Snapshot {private Long projectId,projectVersion,departmentId,scopeVersion;private String projectCode,projectName,companyCode,companyName,departmentCode,departmentName,customerCode,customerName,managerEmployeeNo,managerName;private Integer departmentVersion;}
 @lombok.Data public static class OrderLine {private Long id,projectId;private String contractNo,orderNo,lineNo,productCode,sourceSystem,companyCode,companyName,orderType;private BigDecimal orderQty;}
 @lombok.Data public static class Department {private Long id;private String code,name;private Integer version;}
 public record DepartmentCatalogueQuery(long tenantId){}
 public record CurrentRunQuery(long tenantId,long connectionId){}
 @lombok.Data public static class Company {private Long id;private String code;}
 public record OrderKey(String companyCode,String orderNo,String lineNo,String productCode){}
 public record OwnedOrderCatalogueQuery(long tenantId,long connectionId){}
 public record ProjectsQuery(long tenantId,List<Long> ids,boolean preview){}
 @lombok.Data public static class ScopeIdentity {private Long id;private String sourceEvidence;}
 public record ScopeIdentityQuery(long tenantId,boolean preview){}
 public record ScopesQuery(long tenantId,List<Long> ids,boolean preview){}
 public interface SourceMapper {List<Company> selectCompanies(SourceQuery<Parameters> query);void selectProductScope(SourceQuery<Parameters> query,ResultHandler<? super Source> handler);}
 @Mapper public interface TargetMapper {int pendingOrderSchemaReady();int pendingDepartmentSchemaReady();int backfillOrderLineIds(@Param("query") CurrentRunQuery query);long countPendingOrderLines(@Param("query") CurrentRunQuery query);List<Department> selectDepartments(@Param("query") DepartmentCatalogueQuery query);int backfillDepartmentIds(@Param("query") CurrentRunQuery query);long countUnresolvedCodedDepartments(@Param("query") CurrentRunQuery query);List<Snapshot> selectSnapshots(@Param("query") ProjectsQuery query);List<OrderLine> selectOwnedOrderCatalogue(@Param("query") OwnedOrderCatalogueQuery query);List<ScopeIdentity> selectScopeIdentities(@Param("query") ScopeIdentityQuery query);List<ScopeRow> selectScopes(@Param("query") ScopesQuery query);List<DetailRow> selectDetails(@Param("query") ScopesQuery query);int insertProductScope(SyncBatch<Source,Parameters> batch);int insertDetails(SyncBatch<Source,Parameters> batch);}
@lombok.Data public static class ScopeRow {private Long id,tenantId;private String creator,updater;private java.time.LocalDateTime createTime,updateTime;private Boolean deleted;private Long version;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String projectCompanyCode;
    private String projectCompanyName;
    private String projectDepartmentCode;
    private String projectDepartmentName;
    private String projectCustomerCode;
    private String projectCustomerName;
    private String projectManagerEmployeeNo;
    private String projectManagerName;
    private Long orderLineId;
    private Long currentOrderLineId;
    private String orderSourceSystem;
    private String orderCompanyCode;
    private String orderCompanyName;
    private String orderType;
    private String orderNo;
    private String lineNo;
    private String productCode;
    private String productDesc;
    private BigDecimal allocatedQty;
    /** Whether this scope has been confirmed to consume order allocation capacity. */
    private Boolean allocationConfirmed;
    /** Historical order delivery figures carried by this project's scope; not acceptance facts. */
    private BigDecimal deliveredQuantity;
    private BigDecimal openQuantity;
    private String scopeStatus;
    private Long allocationVersion;
    private String allocationSource;
    private String changeReason;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private Integer departmentVersion;
    private String sourceEvidence;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
}
@lombok.Data public static class DetailRow {private Long id,tenantId;private String creator,updater;private java.time.LocalDateTime createTime,updateTime;private Boolean deleted;
    
    
    private Long deliveryScopeId;
    private Integer detailSequence;
    // Historical callers retain this value; the current table stores office facts on the scope root.
    
    private String departmentCode;
    private String serialNo;
    private String productCode;
    private String productName;
    private String deviceTypeCode;
    private String deviceTypeName;
    private String deliveryBatchNo;
    private String sourceRecordKey;
    private BigDecimal allocatedQty;
    private String detailStatus;
    private String sourceSnapshot;
    private String remark;
    
    private Integer version;
}

}
