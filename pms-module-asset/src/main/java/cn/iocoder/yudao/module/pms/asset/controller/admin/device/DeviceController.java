package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceAssignmentRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceCustomerAssignReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceDetailRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceListRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DevicePageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceProjectAssignReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceCustomerAssignmentService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceProjectAssignmentService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceCustomerCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceCustomerAssignmentResult;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceProjectAssignmentResult;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceDetailService;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/pms/asset/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceQueryService deviceQueryService;
    private final DeviceDetailService deviceDetailService;
    private final DeviceProjectAssignmentService projectAssignmentService;
    private final DeviceCustomerAssignmentService customerAssignmentService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceListRespVO>> getDevicePage(@Valid DevicePageReqVO reqVO) {
        VisibleDevicePageQuery query = new VisibleDevicePageQuery(
                TenantContextHolder.getTenantId(), null, reqVO.getSn(), reqVO.getProductCode(),
                reqVO.getProjectId(), reqVO.getCustomerId(), reqVO.getPageNo(), reqVO.getPageSize());
        PageResult<DeviceListProjection> page = deviceQueryService.getPage(query);
        List<DeviceListRespVO> list = page.getList().stream().map(DeviceController::toListResp).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<DeviceDetailRespVO> getDevice(@PathVariable("id") Long id) {
        return success(deviceDetailService.getDetail(id));
    }

    @PostMapping("/{id}/actions/assign-project")
    @PreAuthorize("@ss.hasPermission('pms:device:assign')")
    public CommonResult<DeviceAssignmentRespVO> assignProject(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody DeviceProjectAssignReqVO reqVO) {
        Long expectedVersion = parseIfMatch(ifMatch);
        DeviceProjectAssignmentResult result = projectAssignmentService.assign(
                new AssignDeviceProjectCommand(
                        currentTenantId(), id, reqVO.getProjectId(), expectedVersion, reqVO.getReason(),
                        idempotencyKey, requestDigest(id, expectedVersion, reqVO),
                        SecurityFrameworkUtils.getLoginUserId(), correlationId(), LocalDateTime.now()));
        return success(new DeviceAssignmentRespVO(result.assignmentVersion(), result.operationId()));
    }

    @PostMapping("/{id}/actions/assign-customer")
    @PreAuthorize("@ss.hasPermission('pms:device:assign')")
    public CommonResult<DeviceAssignmentRespVO> assignCustomer(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody DeviceCustomerAssignReqVO reqVO) {
        Long expectedVersion = parseIfMatch(ifMatch);
        DeviceCustomerAssignmentResult result = customerAssignmentService.assign(
                new AssignDeviceCustomerCommand(
                        currentTenantId(), id, reqVO.getCustomerId(), reqVO.getRelationshipType(),
                        expectedVersion, reqVO.getReason(), idempotencyKey,
                        requestDigest(id, expectedVersion, reqVO), SecurityFrameworkUtils.getLoginUserId(),
                        correlationId(), LocalDateTime.now()));
        return success(new DeviceAssignmentRespVO(result.assignmentVersion(), result.operationId()));
    }

    private static DeviceListRespVO toListResp(DeviceListProjection item) {
        return new DeviceListRespVO(
                item.deviceId(), item.sn(), item.productCode(), item.productModel(), item.productName(),
                item.shipmentTime(), item.packageNo(), item.contractNo(), item.shipmentRecordId(),
                item.projectId(), item.customerId(), item.warrantyStartDate(), item.warrantyEndDate(),
                item.warrantyStatus(), item.conpVersion(), item.conpType(), item.conpSeries(),
                item.conpMark(), item.syncStatus());
    }

    private Long currentTenantId() {
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

    private String requestDigest(Long id, Long expectedVersion, Object reqVO) {
        return sha256Digest(id + ":" + expectedVersion + ":" + JsonUtils.toJsonString(reqVO));
    }

    private String sha256Digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }

    private Long parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            long version = Long.parseLong(normalized);
            if (version < 0) {
                throw new NumberFormatException("negative version");
            }
            return version;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("If-Match必须是非负归属版本", ex);
        }
    }

    private String correlationId() {
        String traceId = TracerUtils.getTraceId();
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
