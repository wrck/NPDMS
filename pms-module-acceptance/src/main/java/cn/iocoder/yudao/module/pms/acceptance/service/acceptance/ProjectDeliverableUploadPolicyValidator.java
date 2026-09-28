package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * 统一交付材料上传策略校验方（P06R I2，SPI）：平台策略提供方在 purposeCode 非类型目录编码时
 * 按 ownerModule=ACC 委派本校验方。锚为 PLT/DELIVERY_MATERIAL/ACC:project_deliverable:{projectId}，
 * purposeCode=交付件编码；裁决 = 要求存在 + allowedSources 含 UPLOAD（写动作）+ 项目范围 + ACTIVE 生命周期。
 * 媒体与大小约束沿用原交付件上传口径（PDF/JPEG/PNG、50MiB）。
 */
@Component
@RequiredArgsConstructor
public class ProjectDeliverableUploadPolicyValidator implements DeliveryMaterialUploadPolicyValidator {

    /** 上传文件类别：与原交付件上传口径一致，前端按此类别上传。 */
    static final String UPLOAD_CATEGORY = "PROJECT_DELIVERABLE_DOCUMENT";

    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableRuleApi rules;
    private final ProjectDeliverableAccess access;

    @Override
    public String ownerModule() {
        return PlatformDeliveryRequirementApi.TEMPLATE_OWNER_MODULE;
    }

    @Override
    public FileBusinessObjectPolicyFact validateUpload(Long tenantId, Long actorUserId, String entityType,
                                                       String entityId, String purposeCode, String action,
                                                       boolean lock, Long expectedScopeVersion) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())
                || !PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE.equals(entityType)
                || purposeCode == null || purposeCode.isBlank()
                || !Set.of(FileActionCodes.UPLOAD, FileActionCodes.READ, FileActionCodes.DOWNLOAD,
                        FileActionCodes.PREVIEW).contains(action)) {
            throw denied("交付材料上传锚不合法");
        }
        Long projectId;
        try {
            projectId = Long.valueOf(entityId);
        } catch (NumberFormatException invalid) {
            throw denied("交付材料上传锚不合法");
        }
        ProjectDeliverableRuleApi.Context context;
        try {
            context = lock ? rules.lock(projectId, purposeCode) : rules.read(projectId, purposeCode);
        } catch (ServiceException e) {
            throw denied(e.getMessage());
        }
        // 锁定重验时锁要求行：要求被改版暂存（~plan:）或退役即拒绝，身份以平台行锁复核。
        var requirement = lock ? platform.lockByIdentity(projectId, purposeCode)
                : platform.findByIdentity(projectId, purposeCode);
        if (requirement.isEmpty()) {
            throw denied("项目交付件不存在");
        }
        boolean write = FileActionCodes.UPLOAD.equals(action);
        if (write && !ProjectDeliverableRequirementResolver.allowed(context.configuration(), "UPLOAD")) {
            throw denied("模板未允许此交付件来源");
        }
        Long scope;
        try {
            scope = access.check(context, tenantId, actorUserId, write, lock, expectedScopeVersion);
        } catch (ServiceException e) {
            throw denied(e.getMessage());
        }
        return new FileBusinessObjectPolicyFact(true, scope, "IMMUTABLE", "MULTIPLE",
                Set.of(UPLOAD_CATEGORY), Set.of("application/pdf", "image/jpeg", "image/png"),
                52_428_800L, "INTERNAL");
    }

    private static BusinessContractException denied(String message) {
        return new BusinessContractException("DELIVERY_UPLOAD_DENIED", message);
    }
}
