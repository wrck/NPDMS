package cn.iocoder.yudao.module.pms.engineering.service.configuration.business;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.business.ConfigurationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.business.ConfigurationBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.configuration.ConfigurationService;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentKind;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentRegistration;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigLogRecordApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONFIGURATION_STATUS_INVALID;

/** Imported log identities remain immutable; manual attachments retain their native archive path. */
@Service
@RequiredArgsConstructor
public class ConfigurationBusinessService extends DefaultProjectBusinessService<ConfigurationBusinessMapper, ConfigurationBusinessDO> {
    private final EngineeringRecordCodeGenerator codes;
    private final ConfigurationMapper nativeMapper;
    private final ConfigurationService nativeService;
    private final NativeAttachmentRegistration attachments;
    private final ProjectDeviceSelectionApi devices;
    private final DeviceConfigLogRecordApi archives;

    @Override protected void beforeCreate(ConfigurationBusinessDO row) {
        attachments.requireLegacyUnchanged(null, row.getConfigLogUrl());
        row.setCode(codes.next(row.getProjectId(), EngineeringRecordCodeGenerator.CONFIGURATION, nativeMapper));
        row.setStatus(0);
    }
    @Override protected void beforeUpdate(ConfigurationBusinessDO before, ConfigurationBusinessDO row) {
        editable(before);
        attachments.requireLegacyUnchanged(before.getConfigLogUrl(), row.getConfigLogUrl());
        attachments.requireSameProject(NativeAttachmentKind.CONFIGURATION, before.getId(), before.getProjectId(), row.getProjectId());
    }
    @Override protected void validateBusiness(ConfigurationBusinessDO row) {
        if (!defaults.validator().validate(BeanUtils.toBean(row, ConfigurationSaveReqVO.class)).isEmpty())
            throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID", "项目和关联设备不能为空");
        devices.validateSelection(row.getProjectId(), List.of(row.getEquipmentId()));
    }
    @Override protected void afterUpdate(ConfigurationBusinessDO before, ConfigurationBusinessDO row) {
        for (var material : attachments.register(NativeAttachmentKind.CONFIGURATION, row.getId())) {
            if (!material.newlyRegistered()) continue;
            var locator = new NativeConfigurationFileLocator(row.getId(), material.materialId()).toString();
            archives.recordConfigLog(new DeviceConfigLogRecordCommand(row.getEquipmentId(), "MANUAL_UPLOAD", "PMS", null,
                    locator, material.file().sha256(), "配置调试 " + row.getCode() + " 手动上传"));
        }
    }
    @Override protected void beforeDelete(ConfigurationBusinessDO row) { editable(row); }
    @Override protected void validateDelivery(ConfigurationBusinessDO row, boolean write) { if (write) editable(row); }
    @Override protected boolean runtimeHandlingCompleted(ConfigurationBusinessDO row) { return Objects.equals(row.getStatus(), 2); }
    @Override protected Map<String,String> runtimeFactLabels() { return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","CONFIGURATION_COMPLETED","配置调试已完成"); }
    @Override protected Map<String,Boolean> runtimeFacts(ConfigurationBusinessDO row) { return Map.of("BUSINESS_RECORD_SAVED",true,"CONFIGURATION_COMPLETED",runtimeHandlingCompleted(row)); }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(operation("start", "开始调试", prefix), operation("complete", "完成调试", prefix), operation("abnormal", "标记异常", prefix));
    }
    private BusinessOperationDescriptor operation(String code, String title, String prefix) { return new BusinessOperationDescriptor(code, 1, title, BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix + ":update"); }
    public BusinessOperationReceipt start(Long id, Long version, String key) { return businessAction("start", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.startConfiguration(id); return mapper.selectById(id); }); }
    public BusinessOperationReceipt complete(Long id, Long version, String key) { return businessAction("complete", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.completeConfiguration(id); return mapper.selectById(id); }); }
    public BusinessOperationReceipt abnormal(Long id, Long version, String key) { return businessAction("abnormal", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.markAbnormal(id); return mapper.selectById(id); }); }
    private void editable(ConfigurationBusinessDO row) {
        if (row.getImportedLogFileId() != null) throw new BusinessContractException("CONFIGURATION_SOURCE_IMMUTABLE", "导入日志记录及来源历史须保留");
        if (!Set.of(0, 1, 3).contains(row.getStatus())) throw exception(CONFIGURATION_STATUS_INVALID);
    }
}

