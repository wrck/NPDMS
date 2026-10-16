package cn.iocoder.yudao.module.pms.engineering.service.installation.business;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.business.InstallationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.business.InstallationBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationService;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteInput;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationInput;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Location effect and its previous record are still owned by InstallationService.completeInstallation. */
@Service
@RequiredArgsConstructor
public class InstallationBusinessService extends DefaultProjectBusinessService<InstallationBusinessMapper, InstallationBusinessDO> {
    private final EngineeringRecordCodeGenerator codes;
    private final InstallationMapper nativeMapper;
    private final InstallationService nativeService;
    private final EngineeringLocationFactService locations;
    private final ProjectDeviceSelectionApi devices;
    private final cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi addresses;

    public record BatchResult(BusinessOperationReceipt receipt, List<Long> ids) { }

    /** Same atomic save/start/complete sequence as the project workbench, with inherited form persistence. */
    public BatchResult createBatch(Map<String,Object> values,List<Long> equipmentIds,String key) {
        if(equipmentIds==null || equipmentIds.isEmpty() || equipmentIds.stream().anyMatch(id->id==null || id<=0)
                || new HashSet<>(equipmentIds).size()!=equipmentIds.size())
            throw new BusinessContractException("DEVICE_SELECTION_INVALID","请选择不重复的项目设备");
        var intent=Map.<String,Object>of("values",values,"equipmentIds",equipmentIds);
        var receipt=write("create",null,null,intent,key,actor->{
            var fixed=new LinkedHashMap<>(values);fixed.keySet().removeAll(Set.of("$business","$extensions","$binding"));
            Long projectId=input(fixed).getProjectId();
            defaults.projects().requireWritable(projectId,actor,true);
            devices.validateSelection(projectId,equipmentIds);
            var body=values.get("$business") instanceof Map<?,?> fields?fields:Map.of();
            LocationMaintenanceCommand location=body.get("locationMaintenance")==null?null:
                    JsonUtils.parseObject(JsonUtils.toJsonString(body.get("locationMaintenance")),LocationMaintenanceCommand.class);
            LocalDateTime savedAt=LocalDateTime.now();InstallationBusinessDO first=null;
            for(int index=0;index<equipmentIds.size();index++) {
                var item=new LinkedHashMap<>(values);
                item.put("projectId",projectId);item.put("equipmentId",equipmentIds.get(index));
                item.put("installerUserId",actor.userId());item.put("installTime",savedAt);
                if(location!=null)item.put("$business",Map.of("locationMaintenance",location));
                var created=createForm(item,batchItemKey(key,index));
                Long id=created.entityRef().entityId();
                nativeService.startInstallation(id);nativeService.completeInstallation(id);
                var saved=get(id);if(first==null)first=saved;
                if(location!=null && saved.getSiteId()!=null) {
                    location=new LocationMaintenanceCommand(projectId,null,
                            new SiteInput(saved.getSiteId(),saved.getSiteVersion(),null,null,null,null),
                            saved.getSiteLocationId()==null?null:new SiteLocationInput(saved.getSiteLocationId(),saved.getSiteLocationVersion(),null,null,null,null,null),
                            saved.getInstallLocation(),null,null,null,List.of());
                }
            }
            return new BusinessOperationReceipt(ReceiptOutcome.SAVED,identity(first),first.getVersion(),List.of(),null,null,"create",1);
        });
        // Read the real child receipts on replay as well; no duplicate rows or synthesized result identifiers.
        var ids=new ArrayList<Long>();
        for(int index=0;index<equipmentIds.size();index++)ids.add(receipt("create",batchItemKey(key,index)).entityRef().entityId());
        return new BatchResult(receipt,List.copyOf(ids));
    }
    private String batchItemKey(String key,int index) {
        return "installation-batch:"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(key+":"+index);
    }

    @Override protected Long generatedId() { return com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(); }
    @Override protected void beforeCreate(InstallationBusinessDO row) {
        row.setCode(codes.next(row.getProjectId(), EngineeringRecordCodeGenerator.INSTALLATION, nativeMapper));
        row.setStatus(0);
        if (row.getInstallTime() == null) row.setInstallTime(LocalDateTime.now());
        row.setLocationResolutionStatus("UNRESOLVED");
    }
    @Override protected void beforeUpdate(InstallationBusinessDO before, InstallationBusinessDO row) {
        editable(before);
        if (!Objects.equals(before.getInstallLocation(), row.getInstallLocation())) {
            row.setLocationResolutionStatus("UNRESOLVED");
            row.setAddressId(null); row.setAddressVersion(null); row.setSiteId(null); row.setSiteVersion(null);
            row.setSiteLocationId(null); row.setSiteLocationVersion(null); row.setAddressSnapshot(null); row.setLocationSnapshot(null);
        }
    }
    @Override protected void beforeFormWrite(InstallationBusinessDO before, InstallationBusinessDO row, Map<String, Object> business) {
        if (business.isEmpty()) return;
        if (!business.keySet().equals(Set.of("locationMaintenance"))) throw new BusinessContractException("FIELD_NOT_OPEN", "Unsupported installation form command");
        var command = JsonUtils.parseObject(JsonUtils.toJsonString(business.get("locationMaintenance")), LocationMaintenanceCommand.class);
        if (command == null) throw exception(INSTALLATION_LOCATION_REQUIRED);
        var fact = locations.maintain(row.getProjectId(), "INSTALLATION", row.getId(),
                before == null ? 0L : before.getVersion() + 1, row.getInstallLocation(), command);
        if (fact == null || !"RESOLVED".equals(fact.resolutionStatus())) throw exception(INSTALLATION_LOCATION_INVALID);
        row.setAddressId(fact.addressId()); row.setAddressVersion(fact.addressVersion());
        row.setSiteId(fact.siteId()); row.setSiteVersion(fact.siteVersion());
        row.setSiteLocationId(fact.siteLocationId()); row.setSiteLocationVersion(fact.siteLocationVersion());
        row.setLocationResolutionStatus(fact.resolutionStatus()); row.setAddressSnapshot(fact.addressSnapshot()); row.setLocationSnapshot(fact.locationSnapshot());
    }
    @Override protected Map<String, Object> formContext(InstallationBusinessDO row) {
        var context = new LinkedHashMap<String, Object>();
        context.put("addressId", row.getAddressId()); context.put("addressVersion", row.getAddressVersion());
        context.put("siteId", row.getSiteId()); context.put("siteVersion", row.getSiteVersion());
        context.put("siteLocationId", row.getSiteLocationId()); context.put("siteLocationVersion", row.getSiteLocationVersion());
        return context;
    }
    @Override protected void validateBusiness(InstallationBusinessDO row) {
        if (!defaults.validator().validate(BeanUtils.toBean(row, InstallationSaveReqVO.class)).isEmpty())
            throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID", "项目和关联设备不能为空");
        if (row.getInstallLocation() == null || row.getInstallLocation().isBlank()) throw exception(INSTALLATION_LOCATION_REQUIRED);
        devices.validateSelection(row.getProjectId(), List.of(row.getEquipmentId()));
    }
    @Override protected void beforeDelete(InstallationBusinessDO row) { editable(row); }
    @Override protected void validateDelivery(InstallationBusinessDO row, boolean write) { if (write) editable(row); }
    @Override protected Map<String, String> runtimeFactLabels() { return Map.of("INSTALLATION_COMPLETED", "硬件安装已完成"); }
    @Override protected RuntimeResult runtimeResult(InstallationBusinessDO row) {
        var fact=defaults.transactions().execute(status->addresses.lockAndInspect(
                new cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi.Query(row.getTenantId(),row.getProjectId())));
        boolean completed=Objects.equals(row.getStatus(),2)&&row.getEffectiveTo()==null&&fact!=null&&fact.completed();
        return new RuntimeResult(Map.of("INSTALLATION_COMPLETED",completed),completed,row.getEffectiveFrom(),
                fact==null?"UNAVAILABLE":fact.totalDevices()+":"+fact.addressedDevices());
    }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(operation("start", "开始安装", prefix), operation("complete", "完成安装", prefix), operation("abnormal", "标记异常", prefix));
    }
    private BusinessOperationDescriptor operation(String code, String title, String prefix) { return new BusinessOperationDescriptor(code, 1, title, BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix + ":update"); }
    public BusinessOperationReceipt start(Long id, Long version, String key) { return businessAction("start", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.startInstallation(id); return mapper.selectById(id); }); }
    public BusinessOperationReceipt complete(Long id, Long version, String key) { return businessAction("complete", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.completeInstallation(id); return mapper.selectById(id); }); }
    public BusinessOperationReceipt abnormal(Long id, Long version, String key) { return businessAction("abnormal", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.markAbnormal(id); return mapper.selectById(id); }); }
    private void editable(InstallationBusinessDO row) { if (!Set.of(0, 1, 3).contains(row.getStatus())) throw exception(INSTALLATION_STATUS_INVALID); }
}

