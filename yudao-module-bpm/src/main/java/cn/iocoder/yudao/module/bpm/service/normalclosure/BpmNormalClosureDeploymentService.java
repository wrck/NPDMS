package cn.iocoder.yudao.module.bpm.service.normalclosure;

import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmAutoApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Explicit operator entry only: no startup hook, HTTP endpoint, process start or automatic approval. */
@Service
@RequiredArgsConstructor
public class BpmNormalClosureDeploymentService {
    private final BpmModelService modelService;
    private final AdminUserApi adminUserApi;
    private final BpmNormalClosureApi normalClosureApi;

    /**
     * Called by authorized provisioning, with a real BPM model manager and existing business UI paths.
     * Uses normal create/deploy, including manager check, candidate validation and definition-info registration.
     * An existing model is deliberately not overwritten or silently redeployed.
     */
    @Transactional(rollbackFor = Exception.class)
    public BpmNormalClosureApi.Definition deploy(Long tenantId, Long managerUserId, String category,
                                                String formCreatePath, String formViewPath) {
        BpmNormalClosureGuard.requireTenant(tenantId);
        if (managerUserId == null || formCreatePath == null || formCreatePath.isBlank()
                || formViewPath == null || formViewPath.isBlank()) {
            throw new IllegalArgumentException("Real manager and existing business form paths are required");
        }
        adminUserApi.validateUser(managerUserId);
        BpmModelSaveReqVO model = new BpmModelSaveReqVO();
        model.setKey(BpmNormalClosureApi.PROCESS_DEFINITION_KEY);
        model.setName("项目正常闭环双人工审核");
        model.setCategory(category);
        model.setType(BpmModelTypeEnum.BPMN.getType());
        model.setFormType(BpmModelFormTypeEnum.CUSTOM.getType());
        model.setFormCustomCreatePath(formCreatePath);
        model.setFormCustomViewPath(formViewPath);
        model.setVisible(false); // Business Owner is the sole start entry, not the generic form launcher.
        model.setManagerUserIds(List.of(managerUserId));
        model.setAutoApprovalType(BpmAutoApproveTypeEnum.NONE.getType());
        model.setAllowWithdrawTask(false);
        model.setAllowCancelRunningProcess(true); // Original applicant may cancel an invalid attempt and reapply.
        model.setBpmnXml(new String(BpmNormalClosureService.resourceBytes(), StandardCharsets.UTF_8));
        String modelId = modelService.createModel(model);
        modelService.deployModel(managerUserId, modelId);
        return normalClosureApi.inspectDefinition(tenantId, BpmNormalClosureApi.PROCESS_DEFINITION_KEY);
    }
}
