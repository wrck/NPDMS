package cn.iocoder.yudao.module.pms.integration.sync;
import static cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.*;
import java.util.*;

@RequiredArgsConstructor
final class DirectDeviceBasePersistence {
    private final javax.sql.DataSource dataSource;
    private final DirectDeviceBaseMapper mapper;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private record Actor(Long tenantId,Long actorId) {}
    private void authorize(Actor actor) {
        if(!Objects.equals(actor.tenantId(),TenantContextHolder.getRequiredTenantId()) || actor.actorId()==null || actor.actorId()<=0)
            fail("ACTOR_INVALID");
        users.validateUser(actor.actorId());
        if(!permissions.hasAnyPermissions(actor.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
    }
    private List<Seed> seeds(DirectDeviceBaseMapper.Latest query) {
        var shipments=mapper.selectLatestShipments(query);
        var codes=shipments.stream().map(s->s.getProductCode()).filter(Objects::nonNull).distinct().toList();
        var catalogue=mapper.selectShipmentProducts(new DirectDeviceBaseMapper.Products(query.tenantId(),Set.copyOf(codes)));
        var byCode=new HashMap<String,DirectProductRow>();
        for(var product:catalogue)if(byCode.putIfAbsent(product.getProductCode(),product)!=null)fail("PRODUCT_AMBIGUOUS");
        var result=new ArrayList<Seed>();
        for(var shipment:shipments) {
            if(!query.deviceSns().contains(shipment.getDeviceSn()) || !"DPPMS".equals(shipment.getSourceSystem())
                    || !Objects.equals(shipment.getTenantId(),query.tenantId()))fail("LATEST_SHIPMENT_AUTHORITY_CONFLICT");
            var product=byCode.get(shipment.getProductCode());
            result.add(new Seed(shipment.getDeviceSn(),shipment.getId(),shipment.getSourceKey(),shipment.getContractNo(),
                    shipment.getPackageNo(),shipment.getShipmentTime(),shipment.getProductCode(),
                    product==null?null:product.getProductModel(),product==null?null:product.getProductName()));
        }
        return List.copyOf(result);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<BaseResult> previewBase(BaseCommand command){return base(command,false);}
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<BaseResult> applyBase(BaseCommand command){return base(command,true);}
    private List<BaseResult> base(BaseCommand command,boolean apply) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource))
            throw new IllegalStateException("DPPMS_DEVICE_BASE_TRANSACTION_REQUIRED");
        if(command==null || command.sourceReadAt()==null || command.sourceVersion()==null || command.seeds()==null
                || command.seeds().size()>500 || command.seeds().stream().anyMatch(Objects::isNull))fail("COMMAND_INVALID");
        authorize(new Actor(command.tenantId(),command.actorId()));
        var serials=command.seeds().stream().map(Seed::deviceSn).toList();
        if(new HashSet<>(serials).size()!=serials.size() || serials.stream().anyMatch(s->s==null || s.isBlank()))fail("IDENTITY_INVALID");
        var latest=seeds(new DirectDeviceBaseMapper.Latest(command.tenantId(),serials));
        if(!new HashSet<>(latest).equals(new HashSet<>(command.seeds())))fail("SOURCE_CHANGED");
        var result=new ArrayList<BaseResult>();var creates=new ArrayList<DirectDeviceBaseRow>();long previewId=-1;
        for(var seed:command.seeds()) {
            var device=mapper.selectIdentityForUpdate(new DirectDeviceBaseMapper.DeviceIdentityQuery(command.tenantId(),seed.deviceSn()));
            if(device!=null) {
                if(Boolean.TRUE.equals(device.getDeleted()) || !"DPPMS".equals(device.getSourceSystem())
                        || !Objects.equals(device.getTenantId(),command.tenantId()) || !Objects.equals(device.getSn(),seed.deviceSn()))fail("DEVICE_AUTHORITY_CONFLICT");
                result.add(new BaseResult(seed.deviceSn(),device.getId(),"UNCHANGED",seed.shipmentId(),device.getProjectAssignmentVersion()));
                continue;
            }
            device=new DirectDeviceBaseRow();device.setId(apply?IdWorker.getId():previewId--);device.setTenantId(command.tenantId());device.setSn(seed.deviceSn());
            device.setName(seed.productName());device.setProductName(seed.productName());device.setProductModel(seed.productModel());device.setProductCode(seed.productCode());
            device.setShipmentTime(seed.shipmentTime());device.setPackageNo(seed.packageNo());device.setContractNo(seed.contractNo());device.setShipmentRecordId(seed.shipmentId());
            device.setProjectAssignmentVersion(0L);device.setCustomerAssignmentVersion(0L);device.setStatus("IN_STOCK");device.setVersion(0L);
            device.setSourceSystem("DPPMS");device.setSourceKey("SN:"+seed.deviceSn());device.setSourceVersion(command.sourceVersion());
            device.setSyncedAt(command.sourceReadAt());device.setSyncStatus("SUCCESS");device.setRemark("DPPMS设备基础承接");
            var now=java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            device.setCreator(command.actorId().toString());device.setUpdater(command.actorId().toString());
            device.setCreateTime(now);device.setUpdateTime(now);device.setDeleted(false);
            creates.add(device);result.add(new BaseResult(seed.deviceSn(),device.getId(),"CREATED",seed.shipmentId(),0L));
        }
        if(apply)for(var device:creates)if(mapper.insert(device)!=1)fail("CREATE_FAILED");
        return List.copyOf(result);
    }
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_DEVICE_BOOTSTRAP_"+code);}
}
