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
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceAssemblyDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.assembly.DeviceAssemblyService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceCustomerAssignmentService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceProjectAssignmentService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceRelationshipQueryService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceCustomerCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceCustomerAssignmentResult;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceProjectAssignmentResult;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceConfigurationDownloadGrant;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceConfigurationFileContent;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceConfigurationLogDownloadService;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceConfigurationLogMetadata;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceConfigurationLogQueryService;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceDetailService;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import cn.iocoder.yudao.module.pms.asset.service.warranty.DeviceWarrantyQueryService;
import cn.iocoder.yudao.module.pms.asset.service.warranty.DeviceWarrantyResult;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
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
    private final DeviceRelationshipQueryService relationshipQueryService;
    private final DeviceAssemblyService assemblyService;
    private final DeviceWarrantyQueryService warrantyQueryService;
    private final DeviceConfigurationLogQueryService configurationLogQueryService;
    private final DeviceConfigurationLogDownloadService configurationLogDownloadService;
    private final DeviceAccessScopeService accessScopeService;

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

    @GetMapping("/{id}/archive")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<DeviceDetailRespVO> getDeviceArchive(@PathVariable("id") Long id) {
        return success(deviceDetailService.getDetail(id));
    }

    @GetMapping("/{id}/assignment-history")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceProjectRelationshipDO>> getAssignmentHistory(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNo", required = false) Long pageNo,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        assertDeviceVisible(id);
        return success(relationshipQueryService.getProjectHistory(currentTenantId(), id, pageNo, pageSize));
    }

    @GetMapping("/{id}/customer-relationships")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceCustomerRelationshipDO>> getCustomerRelationships(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNo", required = false) Long pageNo,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        assertDeviceVisible(id);
        return success(relationshipQueryService.getCustomerRelationships(currentTenantId(), id, pageNo, pageSize));
    }

    @GetMapping("/{id}/assembly-tree")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<List<DeviceAssemblyDO>> getAssemblyTree(@PathVariable("id") Long id) {
        assertDeviceVisible(id);
        return success(assemblyService.getCurrentTree(currentTenantId(), id));
    }

    @GetMapping("/{id}/warranty-records")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<DeviceWarrantyResult> getWarrantyRecords(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNo", required = false) Long pageNo,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        assertDeviceVisible(id);
        return success(warrantyQueryService.get(currentTenantId(), id, pageNo, pageSize));
    }

    @GetMapping("/{id}/configuration-logs")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<List<DeviceConfigurationLogMetadata>> getConfigurationLogs(@PathVariable("id") Long id) {
        assertDeviceVisible(id);
        return success(configurationLogQueryService.getList(
                currentTenantId(), SecurityFrameworkUtils.getLoginUserId(), id));
    }

    @PostMapping("/{id}/configuration-logs/{logId}/download-url")
    @PreAuthorize("@ss.hasPermission('pms:device-configuration-log:download')")
    public CommonResult<DeviceConfigurationDownloadGrant> createConfigurationLogDownloadUrl(
            @PathVariable("id") Long id,
            @PathVariable("logId") Long logId) {
        return success(configurationLogDownloadService.issueGrant(
                currentTenantId(), SecurityFrameworkUtils.getLoginUserId(), id, logId));
    }

    @GetMapping("/{id}/configuration-logs/download")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public void downloadConfigurationLog(
            @PathVariable("id") Long id,
            @RequestParam("token") String token,
            HttpServletResponse response) throws IOException {
        DeviceConfigurationFileContent file = configurationLogDownloadService.download(
                currentTenantId(), SecurityFrameworkUtils.getLoginUserId(), id, token);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.fileName() + "\"");
        try (var inputStream = file.content(); var outputStream = response.getOutputStream()) {
            inputStream.transferTo(outputStream);
        }
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

    private void assertDeviceVisible(Long deviceId) {
        accessScopeService.assertVisible(
                currentTenantId(), SecurityFrameworkUtils.getLoginUserId(), deviceId);
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
