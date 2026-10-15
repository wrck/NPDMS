package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.query.ProductCatalogCodesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceProjectAssignmentService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class DppmsDeviceBootstrapService implements DppmsDeviceBootstrapApi {
    private final DeviceBootstrapSourceMapper sources;
    private final AssetProductOfficialMapper products;
    private final DeviceMigrationIdentityMapper identities;
    private final DeviceMapper devices;
    private final DeviceAssignmentMapper relationships;
    private final DeviceProjectAssignmentService assignments;
    private final ProjectDeviceAssignmentGuardApi projectGuard;
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
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Seed> snapshot(SnapshotQuery query) {
        if(query==null || query.deviceSns()==null || query.deviceSns().size()>500
                || query.deviceSns().stream().anyMatch(s->s==null || s.isBlank())
                || query.maxBytes()<1 || query.maxBytes()>64L*1024*1024)fail("QUERY_INVALID");
        authorize(new Actor(query.tenantId(),query.actorId()));
        var result=new ArrayList<Seed>();String after=null;long bytes=0;
        while(true) {
            var serials=sources.selectSerialPage(new DeviceBootstrapSourceMapper.Page(query.tenantId(),query.deviceSns(),after));
            if(serials.isEmpty())break;
            var page=seeds(new DeviceBootstrapSourceMapper.Latest(query.tenantId(),serials));
            for(var seed:page) {
                bytes+=JsonUtils.toJsonString(seed).getBytes(StandardCharsets.UTF_8).length;
                if(bytes>query.maxBytes())fail("SNAPSHOT_TOO_LARGE");
                result.add(seed);
            }
            after=serials.getLast();
        }
        if(!query.deviceSns().isEmpty() && !new HashSet<>(query.deviceSns()).equals(new HashSet<>(result.stream().map(Seed::deviceSn).toList())))
            fail("SHIPMENT_MISSING");
        return List.copyOf(result);
    }
    private List<Seed> seeds(DeviceBootstrapSourceMapper.Latest query) {
        var shipments=sources.selectLatestShipments(query);
        var codes=shipments.stream().map(s->s.getProductCode()).filter(Objects::nonNull).distinct().toList();
        var catalogue=products.selectShipmentProducts(new ProductCatalogCodesQuery(query.tenantId(),Set.copyOf(codes)));
        var byCode=new HashMap<String,cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO>();
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
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<BaseResult> previewBase(BaseCommand command){return base(command,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<BaseResult> applyBase(BaseCommand command){return base(command,true);}
    private List<BaseResult> base(BaseCommand command,boolean apply) {
        if(command==null || command.sourceReadAt()==null || command.sourceVersion()==null || command.seeds()==null
                || command.seeds().size()>500 || command.seeds().stream().anyMatch(Objects::isNull))fail("COMMAND_INVALID");
        authorize(new Actor(command.tenantId(),command.actorId()));
        var serials=command.seeds().stream().map(Seed::deviceSn).toList();
        if(new HashSet<>(serials).size()!=serials.size() || serials.stream().anyMatch(s->s==null || s.isBlank()))fail("IDENTITY_INVALID");
        var latest=seeds(new DeviceBootstrapSourceMapper.Latest(command.tenantId(),serials));
        if(!new HashSet<>(latest).equals(new HashSet<>(command.seeds())))fail("SOURCE_CHANGED");
        var result=new ArrayList<BaseResult>();var creates=new ArrayList<DeviceDO>();long previewId=-1;
        for(var seed:command.seeds()) {
            var device=identities.selectIdentityForUpdate(new DeviceMigrationIdentityMapper.DeviceIdentityQuery(command.tenantId(),seed.deviceSn()));
            if(device!=null) {
                if(Boolean.TRUE.equals(device.getDeleted()) || !"DPPMS".equals(device.getSourceSystem())
                        || !Objects.equals(device.getTenantId(),command.tenantId()) || !Objects.equals(device.getSn(),seed.deviceSn()))fail("DEVICE_AUTHORITY_CONFLICT");
                result.add(new BaseResult(seed.deviceSn(),device.getId(),"UNCHANGED",seed.shipmentId(),device.getProjectAssignmentVersion()));
                continue;
            }
            device=new DeviceDO();device.setId(apply?IdWorker.getId():previewId--);device.setTenantId(command.tenantId());device.setSn(seed.deviceSn());
            device.setName(seed.productName());device.setProductName(seed.productName());device.setProductModel(seed.productModel());device.setProductCode(seed.productCode());
            device.setShipmentTime(seed.shipmentTime());device.setPackageNo(seed.packageNo());device.setContractNo(seed.contractNo());device.setShipmentRecordId(seed.shipmentId());
            device.setProjectAssignmentVersion(0L);device.setCustomerAssignmentVersion(0L);device.setStatus("IN_STOCK");device.setVersion(0L);
            device.setSourceSystem("DPPMS");device.setSourceKey("SN:"+seed.deviceSn());device.setSourceVersion(command.sourceVersion());
            device.setSyncedAt(command.sourceReadAt());device.setSyncStatus("SUCCESS");device.setRemark("DPPMS设备基础承接");
            creates.add(device);result.add(new BaseResult(seed.deviceSn(),device.getId(),"CREATED",seed.shipmentId(),0L));
        }
        if(apply)for(var device:creates)if(devices.insert(device)!=1)fail("CREATE_FAILED");
        return List.copyOf(result);
    }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public AssignmentResult previewAssignment(Assignment command){return assign(command,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public AssignmentResult applyAssignment(Assignment command){return assign(command,true);}
    private AssignmentResult assign(Assignment command,boolean apply) {
        if(command==null || command.projectId()==null || command.deviceId()==null || command.sourceKey()==null
                || command.sourceKey().isBlank() || command.effectiveAt()==null || command.expectedAssignmentVersion()==null)fail("ASSIGNMENT_INVALID");
        authorize(new Actor(command.tenantId(),command.actorId()));
        var guard=projectGuard.validate(new ProjectDeviceAssignmentGuardQuery(command.tenantId(),command.projectId(),command.actorId()));
        if(guard==null || !guard.assignable())fail("PROJECT_FORBIDDEN");
        var device=relationships.selectDeviceForUpdate(new DeviceAssignmentLockQuery(command.tenantId(),command.deviceId()));
        if(device==null || Boolean.TRUE.equals(device.getDeleted()) || !"DPPMS".equals(device.getSourceSystem())
                || !Objects.equals(device.getTenantId(),command.tenantId())
                || !Objects.equals(device.getProjectAssignmentVersion(),command.expectedAssignmentVersion()))fail("DEVICE_CHANGED");
        var latest=seeds(new DeviceBootstrapSourceMapper.Latest(command.tenantId(),List.of(device.getSn())));
        if(latest.size()!=1 || !Objects.equals(latest.getFirst().shipmentId(),command.expectedShipmentId())
                || !DppmsAssetEntityImportService.sameContract(latest.getFirst().contractNo(),command.contractNo()))fail("LATER_SHIPMENT_CONFLICT");
        if(device.getProjectId()!=null && !Objects.equals(device.getProjectId(),command.projectId())
                || device.getProjectId()==null && !DppmsAssetEntityImportService.sameContract(device.getContractNo(),command.contractNo()))fail("CURRENT_OWNER_CONFLICT");
        var relation=relationships.selectCurrentProject(new DeviceAssignmentLockQuery(command.tenantId(),device.getId()));
        if(device.getProjectId()!=null) {
            if(relation==null || !Objects.equals(relation.getProjectId(),command.projectId()))fail("RELATION_CHANGED");
            return new AssignmentResult(device.getId(),command.projectId(),"UNCHANGED",device.getProjectAssignmentVersion());
        }
        if(relation!=null)fail("RELATION_CHANGED");
        if(apply) {
            String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(command.projectId(),device.getSn(),command.sourceKey())));
            assignments.assign(new AssignDeviceProjectCommand(command.tenantId(),device.getId(),command.projectId(),command.expectedAssignmentVersion(),
                    "DPPMS来源项目指派","DPPMS-BOOTSTRAP:"+digest,digest,command.actorId(),"DPPMS-BOOTSTRAP:"+command.sourceKey(),command.effectiveAt()));
        }
        return new AssignmentResult(device.getId(),command.projectId(),"UPDATED",command.expectedAssignmentVersion()+1);
    }
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_DEVICE_BOOTSTRAP_"+code);}
}
