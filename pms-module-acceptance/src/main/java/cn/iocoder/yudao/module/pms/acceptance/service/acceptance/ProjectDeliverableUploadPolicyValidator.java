package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementIdentityApi;
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

    private final PlatformDeliveryRequirementIdentityApi platform;
    private final ProjectDeliverableRuleApi rules;
    private final ProjectDeliverableAccess access;
    @org.springframework.beans.factory.annotation.Autowired
    private NativeAcceptanceDeliveryAccess nativeOwners;

    @Override
    public String ownerModule() {
        return PlatformDeliveryRequirementApi.TEMPLATE_OWNER_MODULE;
    }

    @Override public boolean allowsGenericDeliveryActions(String type) {
        return PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE.equals(type) || "archiveDocument".equals(type);
    }

    @Override
    public boolean supportsEntityType(String entityType) {
        return PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE.equals(entityType)
                || nativeOwners != null && nativeOwners.supports(entityType);
    }

    @Override
    public FileBusinessObjectPolicyFact validateUpload(Long tenantId, Long actorUserId, String entityType,
                                                       String entityId, String purposeCode, String action,
                                                       boolean lock, Long expectedScopeVersion) {
        if (nativeOwners != null && nativeOwners.supports(entityType)) {
            if (!Set.of(FileActionCodes.UPLOAD,FileActionCodes.REFERENCE,FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW).contains(action)) throw denied("Unsupported native file action");
            boolean write=FileActionCodes.UPLOAD.equals(action) || FileActionCodes.REFERENCE.equals(action);
            Long scope=nativeOwners.require(tenantId,actorUserId,entityType,entityId,purposeCode,write,lock,expectedScopeVersion);
            return new FileBusinessObjectPolicyFact(true,scope,"IMMUTABLE","MULTIPLE",Set.of("交付资料"),
                    Set.of("application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint","text/plain","application/pdf"),
                    5_242_880L,"INTERNAL");
        }
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
        if (!FileActionCodes.UPLOAD.equals(action)) {
            Long scope = access.checkProject(projectId, null, tenantId, actorUserId, false, lock, expectedScopeVersion);
            return new FileBusinessObjectPolicyFact(true, scope, "IMMUTABLE", "MULTIPLE",
                    Set.of(UPLOAD_CATEGORY), Set.of("application/pdf", "image/jpeg", "image/png"),
                    52_428_800L, "INTERNAL");
        }
        ProjectDeliverableRuleApi.Context context;
        try {
            context = lock ? rules.lock(projectId, purposeCode) : rules.read(projectId, purposeCode);
        } catch (ServiceException e) {
            throw denied(e.getMessage());
        }
        // 锁定重验时锁要求行：要求被改版暂存（~plan:）或退役即拒绝，身份以平台行锁复核。
        var requirement = lock ? platform.lockTemplateIdentity(projectId, purposeCode)
                : platform.containsTemplateIdentity(projectId, purposeCode);
        if (!requirement) {
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

    @Override
    public Long requireDeliveryAccess(Long tenantId, Long actorUserId, String entityType, String entityId,
            String purposeCode, boolean write, boolean lock, Long expectedScopeVersion) {
        if (nativeOwners != null && nativeOwners.supports(entityType)) return nativeOwners.require(
                tenantId,actorUserId,entityType,entityId,purposeCode,write,lock,expectedScopeVersion);
        if (!PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE.equals(entityType)) throw denied("交付要求归属不合法");
        Long projectId;
        try { projectId = Long.valueOf(entityId); } catch (NumberFormatException invalid) { throw denied("项目标识不合法"); }
        // Historical read authority is project visibility, independent of the active plan/code.
        if (!write) return access.checkProject(projectId,null,tenantId,actorUserId,false,lock,expectedScopeVersion);
        if (purposeCode == null || purposeCode.isBlank()) throw denied("写操作必须定位交付要求");
        var context = lock ? rules.lock(projectId, purposeCode) : rules.read(projectId, purposeCode);
        if (!(lock ? platform.lockTemplateIdentity(projectId, purposeCode) : platform.containsTemplateIdentity(projectId, purposeCode)))
            throw denied("项目交付要求不存在");
        return access.check(context, tenantId, actorUserId, write, lock, expectedScopeVersion);
    }

    private static BusinessContractException denied(String message) {
        return new BusinessContractException("DELIVERY_UPLOAD_DENIED", message);
    }
}
