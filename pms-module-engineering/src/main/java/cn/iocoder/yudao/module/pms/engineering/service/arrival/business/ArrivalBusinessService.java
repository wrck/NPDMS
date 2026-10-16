package cn.iocoder.yudao.module.pms.engineering.service.arrival.business;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.business.ArrivalBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.business.ArrivalBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.arrival.ArrivalDeliveryRegistration;
import cn.iocoder.yudao.module.pms.engineering.service.arrival.ArrivalService;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.ARRIVAL_STATUS_INVALID;

/** Inherited persistence, with the existing arrival state and material rules. */
@Service
@RequiredArgsConstructor
public class ArrivalBusinessService extends DefaultProjectBusinessService<ArrivalBusinessMapper, ArrivalBusinessDO> {
    private final EngineeringRecordCodeGenerator codes;
    private final ArrivalMapper nativeMapper;
    private final ArrivalService nativeService;
    private final ArrivalDeliveryRegistration delivery;
    private final ProjectDeviceSelectionApi devices;

    @Override protected void beforeCreate(ArrivalBusinessDO row) {
        row.setCode(codes.next(row.getProjectId(), EngineeringRecordCodeGenerator.ARRIVAL, nativeMapper));
        row.setStatus(0);
        if (row.getQuantity() == null) row.setQuantity(1);
    }
    @Override protected void beforeUpdate(ArrivalBusinessDO before, ArrivalBusinessDO row) {
        editable(before);
        delivery.requireWrite(nativeRow(before));
        delivery.preventSourceMove(nativeRow(before), row.getProjectId());
        delivery.requireWrite(nativeRow(row));
    }
    @Override protected void validateBusiness(ArrivalBusinessDO row) {
        if (!defaults.validator().validate(BeanUtils.toBean(row, ArrivalSaveReqVO.class)).isEmpty())
            throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID", "到货时间及项目不能为空");
        if (row.getEquipmentId() != null) devices.validateSelection(row.getProjectId(), List.of(row.getEquipmentId()));
    }
    @Override protected void afterCreate(ArrivalBusinessDO row) {
        if (org.springframework.util.StringUtils.hasText(row.getAttachmentUrl())) nativeService.signArrival(row.getId());
    }
    @Override protected void afterUpdate(ArrivalBusinessDO before, ArrivalBusinessDO row) {
        delivery.registerFiles(nativeRow(row));
        if (Objects.equals(before.getStatus(), 0) && org.springframework.util.StringUtils.hasText(row.getAttachmentUrl()))
            nativeService.signArrival(row.getId());
    }
    @Override protected void beforeDelete(ArrivalBusinessDO row) { editable(row); delivery.requireDelete(nativeRow(row)); }
    @Override protected void validateDelivery(ArrivalBusinessDO row, boolean write) { if (write) editable(row); }
    @Override protected boolean runtimeHandlingCompleted(ArrivalBusinessDO row) { return Objects.equals(row.getStatus(), 1); }
    @Override protected Map<String,String> runtimeFactLabels() { return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","ARRIVAL_SIGNED","到货已签收"); }
    @Override protected Map<String,Boolean> runtimeFacts(ArrivalBusinessDO row) { return Map.of("BUSINESS_RECORD_SAVED",true,"ARRIVAL_SIGNED",runtimeHandlingCompleted(row)); }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(new BusinessOperationDescriptor("sign", 1, "签收", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix + ":update"),
                new BusinessOperationDescriptor("abnormal", 1, "标记异常", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix + ":update"));
    }
    public BusinessOperationReceipt sign(Long id, Long version, String key) {
        return businessAction("sign", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> {
            nativeService.signArrival(id); return mapper.selectById(id);
        });
    }
    public BusinessOperationReceipt abnormal(Long id, Long version, String key) {
        return businessAction("abnormal", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> {
            nativeService.markAbnormal(id); return mapper.selectById(id);
        });
    }
    private void editable(ArrivalBusinessDO row) { if (!Set.of(0, 2).contains(row.getStatus())) throw exception(ARRIVAL_STATUS_INVALID); }
    private ArrivalDO nativeRow(ArrivalBusinessDO row) { return BeanUtils.toBean(row, ArrivalDO.class); }
}

