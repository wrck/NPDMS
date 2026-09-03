package cn.iocoder.yudao.module.pms.platform.controller.admin.collection;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.DeviceCredentialApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialDTO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo.DeviceCredentialCreateReqVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/pms/platform/device-credentials")
@Validated
@RequiredArgsConstructor
public class DeviceCredentialController {

    private final DeviceCredentialApi deviceCredentialApi;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:device-credential:create')")
    public CommonResult<DeviceCredentialDTO> create(@Valid @RequestBody DeviceCredentialCreateReqVO reqVO) {
        char[] secret = reqVO.getSecret();
        try {
            return success(deviceCredentialApi.create(new DeviceCredentialCreateCommand(
                    currentTenantId(), requiredActorId(), reqVO.getCredentialCode(), reqVO.getCredentialType(),
                    reqVO.getUsername(), secret, reqVO.getKmsReference(), reqVO.getDeviceId(),
                    reqVO.getCommandTemplateId(), reqVO.getExpiresAt())));
        } finally {
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:device-credential:query')")
    public CommonResult<DeviceCredentialDTO> get(@PathVariable("id") Long id) {
        return success(deviceCredentialApi.get(currentTenantId(), id));
    }

    private static Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getTenantId() == null) {
            return TenantContextHolder.getRequiredTenantId();
        }
        return loginUser.getTenantId();
    }

    private static Long requiredActorId() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) {
            throw new IllegalStateException("LOGIN_USER_REQUIRED");
        }
        return actorId;
    }
}
