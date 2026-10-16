package cn.iocoder.yudao.module.pms.engineering.service.training;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.business.TrainingBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.business.TrainingBusinessMapper;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingIssueRespVO;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import lombok.RequiredArgsConstructor;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Shared CRUD; customer confirmation continues to use its frozen questionnaire and grant. */
@Service @RequiredArgsConstructor
public class TrainingBusinessService extends DefaultProjectBusinessService<TrainingBusinessMapper,TrainingBusinessDO> {
    private final TrainingMapper nativeMapper;
    private final TrainingService nativeService;
    private final EngineeringRecordCodeGenerator codes;
    private final AdminUserApi users;
    private final TrainingPrintService print;

    @Override protected void beforeCreate(TrainingBusinessDO row) {
        row.setCode(codes.next(row.getProjectId(), EngineeringRecordCodeGenerator.TRAINING, nativeMapper));
        row.setStatus(0);
        if (row.getTrainerUserId() == null) row.setTrainerUserId(SecurityFrameworkUtils.getLoginUserId());
        captureTrainer(row);
        if (row.getConfirmationTemplateId() == null) row.setConfirmationTemplateId(TrainingConfirmationForms.DEFAULT_TEMPLATE);
        capturePrint(row);
    }
    @Override protected void beforeUpdate(TrainingBusinessDO before, TrainingBusinessDO row) {
        editable(before);
        if (row.getTrainerUserId() == null) row.setTrainerUserId(before.getTrainerUserId());
        if (!Objects.equals(before.getTrainerUserId(), row.getTrainerUserId())) captureTrainer(row);
        if (row.getConfirmationTemplateId() == null) row.setConfirmationTemplateId(before.getConfirmationTemplateId());
        if (row.getPrintTemplateId() == null) row.setPrintTemplateId(before.getPrintTemplateId());
        if (!Objects.equals(before.getPrintTemplateId(), row.getPrintTemplateId()) || row.getPrintLayoutSnapshot() == null) capturePrint(row);
    }
    @Override protected void validateBusiness(TrainingBusinessDO row) {
        if (row.getTrainingTypes() == null || row.getTrainingTypes().isBlank()
                || Arrays.stream(row.getTrainingTypes().split(",", -1)).anyMatch(type -> !Set.of("TECHNICAL_PRINCIPLE", "PRODUCT_OPS", "OTHER").contains(type)))
            throw exception(TRAINING_ARGUMENT_INVALID, "培训类型取值无效");
    }
    @Override protected void beforeDelete(TrainingBusinessDO row) { editable(row); }
    @Override protected void validateDelivery(TrainingBusinessDO row, boolean write) {
        if (write && Objects.equals(row.getStatus(), 3)) throw exception(TRAINING_STATUS_INVALID);
    }
    @Override protected boolean runtimeHandlingCompleted(TrainingBusinessDO row) { return Objects.equals(row.getStatus(), 2); }
    @Override protected Map<String,String> runtimeFactLabels() { return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","TRAINING_CONFIRMED","培训已由客户确认"); }
    @Override protected Map<String,Boolean> runtimeFacts(TrainingBusinessDO row) { return Map.of("BUSINESS_RECORD_SAVED",true,"TRAINING_CONFIRMED",runtimeHandlingCompleted(row)); }
    @Override protected java.time.LocalDateTime runtimeFormedAt(TrainingBusinessDO row) { return row.getSignTime() == null ? super.runtimeFormedAt(row) : row.getSignTime(); }
    @Override protected Map<String,Object> formContext(TrainingBusinessDO row) {
        var context = new LinkedHashMap<String,Object>();
        context.put("confirmationFormRules", row.getConfirmationFormRules()); context.put("confirmationValues", row.getConfirmationValues());
        context.put("signatureImageDataUrl", row.getSignatureImageDataUrl()); context.put("printLayoutSnapshot", row.getPrintLayoutSnapshot());
        return context;
    }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(new BusinessOperationDescriptor("issue", 1, "外发客户确认", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix+":issue"),
                new BusinessOperationDescriptor("void", 1, "作废", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix+":update"));
    }
    public record Issued(BusinessOperationReceipt receipt, TrainingIssueRespVO issuance) { }
    public Issued issue(Long id, Long version, String key) {
        var issuance = new AtomicReference<TrainingIssueRespVO>();
        var receipt = businessAction("issue", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> {
            issuance.set(nativeService.issueTraining(id)); return mapper.selectById(id);
        });
        // Only the ordinary receipt is journalled. A replay never persists or returns a previous plaintext token.
        return new Issued(receipt, issuance.get());
    }
    public BusinessOperationReceipt voidRecord(Long id, Long version, String key) {
        return businessAction("void", id, version, key, Map.of(), ReceiptOutcome.SAVED, row -> {
            nativeService.voidTraining(id); return mapper.selectById(id);
        });
    }
    private void editable(TrainingBusinessDO row) { if (!Objects.equals(row.getStatus(), 0)) throw exception(TRAINING_STATUS_INVALID); }
    private void captureTrainer(TrainingBusinessDO row) {
        if (row.getTrainerUserId() == null) throw exception(TRAINING_ARGUMENT_INVALID, "培训工程师不能为空");
        users.validateUser(row.getTrainerUserId()); var user = users.getUser(row.getTrainerUserId());
        row.setTrainerName(user == null ? null : user.getNickname());
    }
    private void capturePrint(TrainingBusinessDO row) {
        if (row.getPrintTemplateId() == null) return;
        var target = BeanUtils.toBean(row, TrainingDO.class); print.capture(target, row.getPrintTemplateId());
        row.setPrintTemplateId(target.getPrintTemplateId()); row.setPrintRevisionId(target.getPrintRevisionId()); row.setPrintLayoutSnapshot(target.getPrintLayoutSnapshot());
    }
}
