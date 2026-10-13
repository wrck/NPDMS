package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.service.scope.DeliveryScopeProjectVersionService;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectScopeMigrationContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Historical consolidation includes product quantity details without device SN or current allocation effects. */
@Service
@RequiredArgsConstructor
public class DppmsProjectProductScopeService implements DppmsProjectProductScopeApi {
    private final ProjectScopeMigrationContextApi projects;
    private final DppmsProductScopeMapper migration;
    private final DeliveryScopeMapper scopes;
    private final DeliveryScopeProjectVersionService versions;
    private final ProjectScopeApi access;
    @Override public Page page(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.actorId()<=0 || query.pageNo()<1 || query.pageSize()<1 || query.pageSize()>100)
            throw blocked("PAGE_INVALID");
        var allowed=access.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(),query.actorId(),query.projectId(),ProjectScopeApi.ACTION_VIEW));
        if(allowed==null || allowed.fullProjectIds()==null || !allowed.fullProjectIds().contains(query.projectId()))
            throw blocked("PROJECT_FORBIDDEN");
        var page=new DppmsProductScopeMapper.PageQuery(query.tenantId(),query.projectId(),Math.multiplyExact(query.pageNo()-1,query.pageSize()),query.pageSize());
        var rows=migration.selectProjectPage(page).stream().map(row->{
            var values=new LinkedHashMap<String,Object>();
            values.put("contractNo",row.getContractNo());values.put("productCode",row.getProductCode());values.put("productName",row.getProductDesc());
            values.put("projectQuantity",row.getAllocatedQty());values.put("orderQuantity",row.getOrderQuantity());
            values.put("deliveredQuantity",row.getDeliveredQuantity());values.put("openQuantity",row.getOpenQuantity());
            values.put("orderNo",row.getOrderNo());values.put("lineNo",row.getLineNo());values.put("unitCode",row.getUnitCode());
            for(String field:List.of("allocationConfirmed","scopeStatus","allocationVersion","effectiveFrom","departmentCode","departmentName","creator","createTime","updater","updateTime"))
                values.put(field,new BeanWrapperImpl(row).getPropertyValue(field));
            String prefix="DPPMS:pm_project_product_line:";
            String evidence=row.getSourceEvidence();
            String key=evidence!=null && evidence.startsWith(prefix)?evidence.substring(evidence.lastIndexOf(':')+1):null;
            return new Result(key,row.getId(),"QUERY",Collections.unmodifiableMap(values));
        }).toList();
        return new Page(rows,migration.countProject(page));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command) { return execute(command,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command) { return execute(command,true); }
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.actorId()<=0
                || command.entries()==null || command.entries().isEmpty() || command.entries().size()>1000)
            throw blocked("INVALID_REQUEST");
        var query=new ProjectScopeMigrationContextApi.Query(command.tenantId(),command.actorId(),command.projectId(),
                command.expectedProjectVersion(),command.sourceProjectKey(),command.sourceConnectionId());
        ProjectScopeMigrationContextApi.Context project;
        try { project=apply?projects.lockAndRevalidate(query):projects.resolve(query); }
        catch(IllegalArgumentException ex) {
            if(apply || !"DPPMS_SCOPE_PROJECT_OFFICE_UNRESOLVED".equals(ex.getMessage()))throw ex;
            return command.entries().stream().map(entry->previewBlock(entry,"PROJECT_OFFICE_UNRESOLVED",false)).toList();
        }
        var version=apply?versions.lockExisting(command.tenantId(),command.projectId()):null;
        long nextVersion=Math.addExact(apply?(version==null?0:version.getScopeVersion()):versions.current(command.tenantId(),command.projectId()),1);
        LocalDateTime now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);Set<String> keys=new HashSet<>();Set<Long> ids=new HashSet<>();
        var prepared=new ArrayList<DeliveryScopeDO>();var preparedDetails=new ArrayList<DeliveryScopeDetailDO>();var result=new ArrayList<Result>();
        for(var entry:command.entries()) {
            var raw=entry.originalFields();
            if(raw==null || raw.size()!=11 || !raw.keySet().containsAll(ORIGINAL_COLUMNS)
                    || !Objects.equals(raw.get("id"),entry.sourceKey()) || !Objects.equals(raw.get("projectId"),command.sourceProjectKey())
                    || entry.sourceKey()==null || !entry.sourceKey().matches("[0-9]{1,19}") || !keys.add(entry.sourceKey()))
                throw blocked("ORIGINAL_IDENTITY_INVALID");
            for(String key:List.of("contractNo","itemCode","orderNumber","lineNum"))
                if(raw.get(key)==null || raw.get(key).isBlank())throw blocked("ORDER_IDENTITY_INCOMPLETE");
            var orderQuery=new DppmsProductScopeMapper.OrderLineQuery(command.tenantId(),command.projectId(),
                    raw.get("contractNo"),raw.get("orderNumber"),raw.get("lineNum"),raw.get("itemCode"));
            var lines=apply?migration.lockAssociatedLines(orderQuery):migration.selectAssociatedLines(orderQuery);
            if(lines.size()!=1) {
                if(apply)throw blocked("ORDER_LINE_NOT_UNIQUE:"+entry.sourceKey());
                result.add(previewBlock(entry,lines.isEmpty()?"ASSOCIATED_ORDER_LINE_MISSING":"ORDER_LINE_AMBIGUOUS",!lines.isEmpty()));
                continue;
            }
            var line=lines.getFirst();
            if(line.getOrderQty()==null || !same(decimal(raw.get("orderQuantity")),line.getOrderQty())) {
                if(apply)throw blocked("ORIGINAL_ORDER_QUANTITY_DIFFERS:"+entry.sourceKey());
                result.add(previewBlock(entry,"ORIGINAL_ORDER_QUANTITY_DIFFERS",true));continue;
            }
            BigDecimal quantity=raw.get("projectQuantity")==null?line.getOrderQty():decimal(raw.get("projectQuantity"));
            if(quantity==null || quantity.signum()<=0) {
                if(apply)throw blocked("POSITIVE_QUANTITY_REQUIRED");
                result.add(previewBlock(entry,"POSITIVE_QUANTITY_REQUIRED",true));continue;
            }
            String evidence="DPPMS:pm_project_product_line:"+command.sourceProjectKey()+":"+entry.sourceKey();
            var identity=new DppmsProductScopeMapper.IdentityQuery(command.tenantId(),evidence);
            var existing=apply?migration.lockIdentity(identity):migration.selectIdentity(identity);
            if(existing.size()>1)throw blocked("DUPLICATE_SCOPE_IDENTITY");
            DeliveryScopeDO previous=existing.isEmpty()?null:existing.getFirst();
            var row=build(command,entry,project,line,evidence,previous==null?nextVersion:previous.getAllocationVersion(),
                    previous==null?now:previous.getEffectiveFrom());
            if(previous!=null) {
                if(entry.baseline()==null)throw blocked("BASELINE_MISSING");
                var frozen=verifiedFormalBefore(entry,previous);
                if(frozen!=null) {
                    var snapshot=new BeanWrapperImpl(frozen);var planned=new BeanWrapperImpl(row);
                    for(String field:PROJECT_SNAPSHOT_FIELDS)planned.setPropertyValue(field,snapshot.getPropertyValue(field));
                }
                if(Boolean.TRUE.equals(previous.getDeleted()) || !Boolean.FALSE.equals(previous.getAllocationConfirmed())
                        || !"DPPMS".equals(previous.getAllocationSource()) || !"ACTIVE".equals(previous.getScopeStatus())
                        || previous.getAllocationVersion()==null || previous.getAllocationVersion()<1 || previous.getEffectiveFrom()==null
                        || !Objects.equals(previous.getVersion(),0L) || !matches(previous,row))
                    throw blocked("EXISTING_SCOPE_CHANGED:"+entry.sourceKey());
                if(entry.baseline()!=null && (!Objects.equals(previous.getId(),entry.baseline().id())
                        || !Objects.equals(previous.getCreator(),entry.baseline().creator())
                        || !Objects.equals(previous.getCreateTime(),storedTime(entry.baseline().createTime()))
                        || !Objects.equals(previous.getUpdater(),entry.baseline().updater())
                        || !Objects.equals(previous.getUpdateTime(),storedTime(entry.baseline().updateTime()))))throw blocked("BASELINE_ID_OR_AUDIT_CHANGED");
                var detail=productDetail(command,previous,apply,now);
                if(detail.created())preparedDetails.add(detail.row());
                result.add(new Result(entry.sourceKey(),previous.getId(),detail.created()?"UPDATED":"UNCHANGED",fields(previous,detail)));continue;
            }
            if(entry.baseline()!=null) {
                var audit=entry.baseline();
                if(audit.id()==null || audit.id()<=0
                        || audit.createTime()==null || audit.updateTime()==null || scopes.selectById(audit.id())!=null)
                    throw blocked("BASELINE_ID_OR_AUDIT_INVALID");
                row.setId(audit.id());row.setCreator(audit.creator());row.setCreateTime(audit.createTime());
                row.setUpdater(audit.updater());row.setUpdateTime(audit.updateTime());
            } else {
                row.setId(apply?IdWorker.getId():null);row.setCreator(command.actorId().toString());row.setUpdater(command.actorId().toString());
                row.setCreateTime(now);row.setUpdateTime(now);
            }
            if(row.getId()!=null && !ids.add(row.getId()))throw blocked("DUPLICATE_TARGET_ID");
            var detail=productDetail(command,row,apply,now);
            if(detail.created())preparedDetails.add(detail.row());
            prepared.add(row);result.add(new Result(entry.sourceKey(),row.getId(),"CREATED",fields(row,detail)));
        }
        // Validate the whole bounded batch before inserting. The enclosing migration owns rollback and receipts.
        if(apply)for(var row:prepared)if(migration.insertHistoricalScope(row)!=1)throw blocked("INSERT_CONFLICT");
        if(apply)for(var row:preparedDetails)if(migration.insertHistoricalProductDetail(row)!=1)throw blocked("PRODUCT_DETAIL_INSERT_CONFLICT");
        return List.copyOf(result);
    }
    private static Result previewBlock(Entry entry,String reason,boolean conflict) {
        return new Result(entry.sourceKey(),entry.baseline()==null?null:entry.baseline().id(),
                conflict || entry.baseline()!=null?"CONFLICT":"ISSUE",Map.of(),"DPPMS_PRODUCT_SCOPE_"+reason);
    }
    /** Exact old Owner result protects historical project snapshots while current authorization stays in the context Owner. */
    private static DeliveryScopeDO verifiedFormalBefore(Entry entry,DeliveryScopeDO previous) {
        String json=entry.baseline().formalFieldsJson();
        if(json==null)return null;
        var values=JsonUtils.parseMap(json);var required=new HashSet<>(BUSINESS_FIELDS);
        required.addAll(List.of("id","creator","createTime","updater","updateTime"));
        if(values==null || !values.keySet().equals(required))throw blocked("FORMAL_BEFORE_INCOMPLETE");
        var before=JsonUtils.parseObject(json,DeliveryScopeDO.class);
        // The platform JSON default reads dates as epoch milliseconds; original SQL guards retain ISO precision.
        var bean=new BeanWrapperImpl(before);
        for(String field:List.of("effectiveFrom","effectiveTo","createTime","updateTime"))
            if(values.get(field) instanceof String time)bean.setPropertyValue(field,storedTime(LocalDateTime.parse(time.replace(' ','T'))));
        if(before==null || !Objects.equals(before.getId(),previous.getId()) || !matches(previous,before)
                || !Objects.equals(previous.getCreator(),before.getCreator()) || !Objects.equals(previous.getCreateTime(),before.getCreateTime())
                || !Objects.equals(previous.getUpdater(),before.getUpdater()) || !Objects.equals(previous.getUpdateTime(),before.getUpdateTime()))
            throw blocked("FORMAL_BEFORE_CHANGED");
        return before;
    }
    /** Earlier receipts retained nanos: Connector/J rounds to micros, then DATETIME(3) rounds to millis. */
    private static LocalDateTime storedTime(LocalDateTime time) {
        if(time==null)return null;
        long micros=(time.getNano()+500L)/1000;
        long millis=(micros+500L)/1000;
        return time.withNano(0).plusNanos(millis*1_000_000);
    }
    private static final List<String> PROJECT_SNAPSHOT_FIELDS=List.of("projectCode","projectName","projectCompanyCode","projectCompanyName",
            "projectDepartmentCode","projectDepartmentName","projectCustomerCode","projectCustomerName","projectManagerEmployeeNo","projectManagerName",
            "departmentId","departmentCode","departmentName","departmentVersion");
    private record ProductDetail(DeliveryScopeDetailDO row,boolean created) {}
    private ProductDetail productDetail(Command command,DeliveryScopeDO scope,boolean apply,LocalDateTime now) {
        var row=new DeliveryScopeDetailDO();
        row.setTenantId(command.tenantId());row.setDeliveryScopeId(scope.getId());row.setDetailSequence(1);
        row.setProductCode(scope.getProductCode());row.setProductName(scope.getProductDesc());row.setAllocatedQty(scope.getAllocatedQty());
        row.setSourceRecordKey(scope.getSourceEvidence()+":PRODUCT");row.setDetailStatus("ACTIVE");row.setVersion(0);row.setDeleted(false);
        var query=new DppmsProductScopeMapper.ProductDetailQuery(command.tenantId(),scope.getId());
        var existing=scope.getId()==null?List.<DeliveryScopeDetailDO>of():apply?migration.selectProductDetailsForUpdate(query):migration.selectProductDetails(query);
        if(!existing.isEmpty()) {
            if(existing.size()!=1)throw blocked("EXISTING_PRODUCT_DETAIL_CHANGED");
            var previous=existing.getFirst();var old=new BeanWrapperImpl(previous);var expected=new BeanWrapperImpl(row);
            for(String field:PRODUCT_DETAIL_FIELDS)if(!same(old.getPropertyValue(field),expected.getPropertyValue(field)))
                throw blocked("EXISTING_PRODUCT_DETAIL_CHANGED");
            if(previous.getId()==null || previous.getCreator()==null || previous.getCreateTime()==null
                    || !Objects.equals(previous.getCreator(),previous.getUpdater())
                    || !Objects.equals(previous.getCreateTime(),previous.getUpdateTime()))throw blocked("EXISTING_PRODUCT_DETAIL_AUDIT_CHANGED");
            return new ProductDetail(previous,false);
        }
        row.setId(apply?IdWorker.getId():null);row.setCreator(command.actorId().toString());row.setUpdater(command.actorId().toString());
        row.setCreateTime(now);row.setUpdateTime(now);
        return new ProductDetail(row,true);
    }
    private static final List<String> PRODUCT_DETAIL_FIELDS=List.of("tenantId","deliveryScopeId","detailSequence","serialNo",
            "productCode","productName","deviceTypeCode","deviceTypeName","deliveryBatchNo","sourceRecordKey","allocatedQty",
            "detailStatus","sourceSnapshot","remark","version","deleted");
    private static DeliveryScopeDO build(Command command,Entry entry,ProjectScopeMigrationContextApi.Context p,
                                         SalesOrderLineDO line,String evidence,Long allocationVersion,LocalDateTime effectiveFrom) {
        var raw=entry.originalFields();var row=new DeliveryScopeDO();
        row.setTenantId(command.tenantId());row.setProjectId(p.projectId());row.setProjectCode(p.projectCode());row.setProjectName(p.projectName());
        row.setProjectCompanyCode(p.companyCode());row.setProjectCompanyName(p.companyName());
        row.setProjectDepartmentCode(p.departmentCode());row.setProjectDepartmentName(p.departmentName());
        row.setProjectCustomerCode(p.customerCode());row.setProjectCustomerName(p.customerName());
        row.setProjectManagerEmployeeNo(p.managerEmployeeNo());row.setProjectManagerName(p.managerName());
        row.setDepartmentId(p.departmentId());row.setDepartmentCode(p.departmentCode());row.setDepartmentName(p.departmentName());row.setDepartmentVersion(p.departmentVersion());
        row.setOrderLineId(line.getId());row.setOrderSourceSystem(line.getSourceSystem());row.setOrderCompanyCode(line.getCompanyCode());row.setOrderCompanyName(line.getCompanyName());
        row.setOrderType(line.getOrderType());row.setOrderNo(raw.get("orderNumber"));row.setLineNo(raw.get("lineNum"));
        row.setProductCode(raw.get("itemCode"));row.setProductDesc(raw.get("itemName"));
        BigDecimal quantity=raw.get("projectQuantity")==null?line.getOrderQty():decimal(raw.get("projectQuantity"));
        if(quantity==null || quantity.signum()<=0)throw blocked("POSITIVE_QUANTITY_REQUIRED");
        row.setAllocatedQty(quantity);row.setAllocationConfirmed(false);
        row.setDeliveredQuantity(decimal(raw.get("deliverQuantity")));row.setOpenQuantity(decimal(raw.get("openQuantity")));
        row.setScopeStatus("ACTIVE");row.setAllocationVersion(allocationVersion);row.setAllocationSource("DPPMS");
        row.setSourceEvidence(evidence);row.setEffectiveFrom(effectiveFrom);row.setStatus("ENABLED");row.setVersion(0L);row.setDeleted(false);
        // Enforce existing physical limits rather than silently truncating useful source fields.
        for(String key:List.of("allocatedQty","deliveredQuantity","openQuantity")) {
            BigDecimal value=(BigDecimal)new BeanWrapperImpl(row).getPropertyValue(key);
            if(value!=null && value.setScale(6,RoundingMode.UNNECESSARY).precision()>18)throw blocked("QUANTITY_OUT_OF_RANGE");
        }
        if(row.getProductDesc()!=null && row.getProductDesc().length()>255)throw blocked("PRODUCT_NAME_OUT_OF_RANGE");
        return row;
    }
    private static final List<String> BUSINESS_FIELDS=List.of("tenantId","projectId","projectCode","projectName","projectCompanyCode","projectCompanyName",
            "projectDepartmentCode","projectDepartmentName","projectCustomerCode","projectCustomerName","projectManagerEmployeeNo","projectManagerName",
            "orderLineId","orderSourceSystem","orderCompanyCode","orderCompanyName","orderType","orderNo","lineNo","productCode","productDesc","allocatedQty",
            "allocationConfirmed","deliveredQuantity","openQuantity","scopeStatus","allocationVersion","allocationSource","changeReason","departmentId","departmentCode",
            "departmentName","departmentVersion","sourceEvidence","effectiveFrom","effectiveTo","status","version","deleted");
    private static boolean matches(DeliveryScopeDO previous,DeliveryScopeDO incoming) {
        var old=new BeanWrapperImpl(previous);var next=new BeanWrapperImpl(incoming);
        for(String field:BUSINESS_FIELDS)if(!same(old.getPropertyValue(field),next.getPropertyValue(field)))return false;
        return true;
    }
    private static boolean same(Object a,Object b) {
        return a instanceof BigDecimal x && b instanceof BigDecimal y?x.compareTo(y)==0:Objects.equals(a,b);
    }
    private static Map<String,Object> fields(DeliveryScopeDO row,ProductDetail detail) {
        var result=new LinkedHashMap<String,Object>();var bean=new BeanWrapperImpl(row);
        for(String field:BUSINESS_FIELDS)result.put(field,bean.getPropertyValue(field));
        for(String field:List.of("id","creator","createTime","updater","updateTime"))result.put(field,bean.getPropertyValue(field));
        var detailValues=new LinkedHashMap<String,Object>();var detailBean=new BeanWrapperImpl(detail.row());
        for(String field:PRODUCT_DETAIL_FIELDS)detailValues.put(field,detailBean.getPropertyValue(field));
        for(String field:List.of("id","creator","createTime","updater","updateTime"))detailValues.put(field,detailBean.getPropertyValue(field));
        result.put("productDetailAction",detail.created()?"CREATED":"UNCHANGED");result.put("productDetail",Collections.unmodifiableMap(detailValues));
        return Collections.unmodifiableMap(result);
    }
    private static BigDecimal decimal(String value) {return value==null?null:new BigDecimal(value);}
    private static IllegalArgumentException blocked(String code) {return new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_"+code);}
}
