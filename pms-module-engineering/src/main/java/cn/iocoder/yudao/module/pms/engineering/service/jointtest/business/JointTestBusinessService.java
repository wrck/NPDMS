package cn.iocoder.yudao.module.pms.engineering.service.jointtest.business;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.business.JointTestBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.business.JointTestBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.jointtest.JointTestService;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentKind;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentRegistration;
import cn.iocoder.yudao.module.pms.engineering.domain.JointTestStatusRules;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.JOINT_TEST_STATUS_INVALID;

@Service
@RequiredArgsConstructor
public class JointTestBusinessService extends DefaultProjectBusinessService<JointTestBusinessMapper, JointTestBusinessDO> {
    private final EngineeringRecordCodeGenerator codes;
    private final JointTestMapper nativeMapper;
    private final JointTestService nativeService;
    private final NativeAttachmentRegistration attachments;
    private final ProjectDeviceSelectionApi devices;

    @Override protected void beforeCreate(JointTestBusinessDO row) {
        attachments.requireLegacyUnchanged(null, row.getEvidenceUrl());
        row.setCode(codes.next(row.getProjectId(), EngineeringRecordCodeGenerator.JOINT_TEST, nativeMapper));
        row.setStatus(0);
    }
    @Override protected void beforeUpdate(JointTestBusinessDO before, JointTestBusinessDO row) {
        editable(before);
        attachments.requireLegacyUnchanged(before.getEvidenceUrl(), row.getEvidenceUrl());
        attachments.requireSameProject(NativeAttachmentKind.JOINT_TEST, before.getId(), before.getProjectId(), row.getProjectId());
    }
    @Override protected void validateBusiness(JointTestBusinessDO row) {
        if (!defaults.validator().validate(BeanUtils.toBean(row, JointTestSaveReqVO.class)).isEmpty())
            throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID", "项目和联调用例不能为空");
        if (row.getEquipmentId() != null) devices.validateSelection(row.getProjectId(), List.of(row.getEquipmentId()));
    }
    @Override protected void afterUpdate(JointTestBusinessDO before, JointTestBusinessDO row) { attachments.register(NativeAttachmentKind.JOINT_TEST, row.getId()); }
    @Override protected void beforeDelete(JointTestBusinessDO row) { editable(row); }
    @Override protected void validateDelivery(JointTestBusinessDO row, boolean write) { if (write) editable(row); }
    @Override protected boolean runtimeHandlingCompleted(JointTestBusinessDO row) { return Objects.equals(row.getStatus(), 2); }
    @Override protected Map<String,String> runtimeFactLabels() { return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","JOINT_TEST_PASSED","业务联调已通过"); }
    @Override protected Map<String,Boolean> runtimeFacts(JointTestBusinessDO row) { return Map.of("BUSINESS_RECORD_SAVED",true,"JOINT_TEST_PASSED",runtimeHandlingCompleted(row)); }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(operation("start", "开始联调", prefix), operation("pass", "联调通过", prefix), operation("fail", "联调失败", prefix));
    }
    private BusinessOperationDescriptor operation(String code, String title, String prefix) { return new BusinessOperationDescriptor(code, 1, title, BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix + ":update"); }
    public BusinessOperationReceipt start(Long id, Long version, String key) { return businessAction("start", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.start(id); return mapper.selectById(id); }); }
    public BusinessOperationReceipt pass(Long id, Long version, String key) { return businessAction("pass", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> { nativeService.pass(id); return mapper.selectById(id); }); }
    public BusinessOperationReceipt fail(Long id, Long version, String key, String exceptionRecord) {
        if (exceptionRecord == null || exceptionRecord.isBlank()) throw exception(JOINT_TEST_STATUS_INVALID);
        return businessAction("fail", id, version, key, Map.of("exceptionRecord", exceptionRecord), ReceiptOutcome.SAVED, row -> { nativeService.fail(id, exceptionRecord); return mapper.selectById(id); });
    }
    private void editable(JointTestBusinessDO row) { if (JointTestStatusRules.isTerminal(row.getStatus())) throw exception(JOINT_TEST_STATUS_INVALID); }
}

