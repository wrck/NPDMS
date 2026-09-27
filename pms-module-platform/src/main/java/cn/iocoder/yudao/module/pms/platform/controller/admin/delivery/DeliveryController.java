package cn.iocoder.yudao.module.pms.platform.controller.admin.delivery;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialSource;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryCapabilityConfigDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryCapabilityConfigMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliverySubmissionMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryCatalogService;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryRequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 统一交付件入口：类型目录、实体适用配置、要求实例、材料记录、提交台账。
 * 材料上传复用统一文件两段式（/files:init-upload + :complete-upload，ownerContext=PLT），
 * 本入口只做材料登记与交付动作；权限不依赖任何项目执行对象。
 */
@RestController
@RequestMapping("/api/v1/pms/delivery")
@Tag(name = "管理后台 - PMS 统一交付件")
@Validated
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryCatalogService catalogService;
    private final DeliveryMaterialService materialService;
    private final DeliveryRequirementService requirementService;
    private final DeliverySubmissionMapper submissionMapper;

    @Data
    public static class DeliveryTypeCreateReqVO {
        @NotBlank
        private String typeCode;
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @NotEmpty
        private List<String> allowedMediaTypes;
        @NotNull
        private Long maxSizeBytes;
        private String remark;
    }

    @Data
    public static class DeliveryTypeUpdateReqVO {
        private String name;
        private String category;
        private List<String> allowedMediaTypes;
        private Long maxSizeBytes;
        private Boolean enabled;
        private String remark;
    }

    @Data
    public static class ConfigUpsertReqVO {
        @NotBlank
        private String ownerModule;
        @NotBlank
        private String entityType;
        @NotBlank
        private String typeCode;
        private Boolean required;
        private Integer minimumQuantity;
        @NotBlank
        private String countingUnit;
        private Boolean enabled;
    }

    @Data
    public static class OwnerReqVO {
        @NotBlank
        private String ownerModule;
        @NotBlank
        private String entityType;
        @NotNull
        private Long entityId;
    }

    @Data
    public static class MaterialRegisterReqVO extends OwnerReqVO {
        @NotBlank
        private String typeCode;
        @NotNull
        private Long fileReferenceId;
        private String title;
        private String sourceKind;
    }

    @Data
    public static class SubmissionReqVO {
        @NotNull
        private Long requirementId;
        @NotEmpty
        private List<Long> materialIds;
        @NotBlank
        private String requestKey;
    }

    @Data
    public static class RequirementVO {
        private Long id;
        private String ownerModule;
        private String entityType;
        private Long entityId;
        private String typeCode;
        private Boolean required;
        private Integer minimumQuantity;
        private String countingUnit;
        private String status;
        private Integer count;
        private Long configId;
    }

    @Data
    public static class MaterialVO {
        private Long id;
        private String typeCode;
        private String fileName;
        private String title;
        private Long fileArtifactId;
        private Integer fileVersionNo;
        private String fileSha256;
        private Long fileReferenceId;
        private String sourceKind;
        private String status;
        private String createTime;
    }

    @Data
    public static class SubmissionOutcomeVO {
        private Long submissionId;
        private boolean replay;
        private Long requirementId;
        private String requirementStatus;
        private int count;
        private String requestKey;
    }

    @Data
    public static class SubmissionVO {
        private Long id;
        private Long requirementId;
        private String requestKey;
        private String status;
        private List<Long> materialIds;
        private String createTime;
    }

    public static SubmissionVO toView(DeliverySubmissionDO row) {
        SubmissionVO vo = new SubmissionVO();
        vo.setId(row.getId());
        vo.setRequirementId(row.getRequirementId());
        vo.setRequestKey(row.getRequestKey());
        vo.setStatus(row.getStatus());
        vo.setMaterialIds(cn.iocoder.yudao.module.pms.platform.service.delivery.JsonSupport
                .parseLongList(row.getMaterialIdsJson()));
        vo.setCreateTime(String.valueOf(row.getCreateTime()));
        return vo;
    }

    public static RequirementVO toView(DeliveryRequirementService.RequirementView view) {
        RequirementVO vo = new RequirementVO();
        DeliveryRequirementDO row = view.requirement();
        vo.setId(row.getId());
        vo.setOwnerModule(row.getOwnerModule());
        vo.setEntityType(row.getEntityType());
        vo.setEntityId(row.getEntityId());
        vo.setTypeCode(row.getTypeCode());
        vo.setRequired(row.getRequired());
        vo.setMinimumQuantity(view.minimumQuantity());
        vo.setCountingUnit(view.countingUnit());
        vo.setStatus(row.getStatus());
        vo.setCount(view.count());
        vo.setConfigId(row.getConfigId());
        return vo;
    }

    public static MaterialVO toView(DeliveryMaterialDO row) {
        MaterialVO vo = new MaterialVO();
        vo.setId(row.getId());
        vo.setTypeCode(row.getTypeCode());
        vo.setFileName(row.getFileName());
        vo.setTitle(row.getTitle());
        vo.setFileArtifactId(row.getFileArtifactId());
        vo.setFileVersionNo(row.getFileVersionNo());
        vo.setFileSha256(row.getFileSha256());
        vo.setFileReferenceId(row.getFileReferenceId());
        vo.setSourceKind(row.getSourceKind());
        vo.setStatus(row.getStatus());
        vo.setCreateTime(String.valueOf(row.getCreateTime()));
        return vo;
    }

    // ---------- 类型目录 ----------

    @GetMapping("/material-sources")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<DeliveryMaterialSource.Descriptor>> materialSources() {
        return success(java.util.Arrays.stream(DeliveryMaterialSource.values())
                .map(source -> new DeliveryMaterialSource.Descriptor(source.code(), source.label()))
                .toList());
    }

    @GetMapping("/types")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<DeliveryTypeDO>> listTypes(@RequestParam(required = false) Boolean enabled) {
        return success(catalogService.listTypes(enabled));
    }

    @PostMapping("/types")
    @PreAuthorize("@ss.hasPermission('pms:delivery:manage')")
    public CommonResult<DeliveryTypeDO> createType(@Valid @RequestBody DeliveryTypeCreateReqVO reqVO) {
        return success(catalogService.createType(reqVO.getTypeCode(), reqVO.getName(), reqVO.getCategory(),
                reqVO.getAllowedMediaTypes(), reqVO.getMaxSizeBytes(), reqVO.getRemark()));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("@ss.hasPermission('pms:delivery:manage')")
    public CommonResult<DeliveryTypeDO> updateType(@PathVariable Long id,
                                                   @Valid @RequestBody DeliveryTypeUpdateReqVO reqVO) {
        return success(catalogService.updateType(id, reqVO.getName(), reqVO.getCategory(),
                reqVO.getAllowedMediaTypes(), reqVO.getMaxSizeBytes(), reqVO.getEnabled(), reqVO.getRemark()));
    }

    // ---------- 适用配置 ----------

    @GetMapping("/configs")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<DeliveryCapabilityConfigDO>> listConfigs(
            @RequestParam String ownerModule, @RequestParam String entityType) {
        return success(catalogService.listConfigs(ownerModule, entityType));
    }

    @PostMapping("/configs")
    @PreAuthorize("@ss.hasPermission('pms:delivery:manage')")
    public CommonResult<DeliveryCapabilityConfigDO> upsertConfig(@Valid @RequestBody ConfigUpsertReqVO reqVO) {
        return success(catalogService.upsertConfig(reqVO.getOwnerModule(), reqVO.getEntityType(),
                reqVO.getTypeCode(), reqVO.getRequired(), reqVO.getMinimumQuantity(),
                reqVO.getCountingUnit(), reqVO.getEnabled()));
    }

    // ---------- 要求实例 ----------

    @PostMapping("/requirements/sync")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<RequirementVO>> syncRequirements(@Valid @RequestBody OwnerReqVO reqVO) {
        return success(requirementService.syncFromConfig(reqVO.getOwnerModule(), reqVO.getEntityType(),
                reqVO.getEntityId()).stream().map(row -> toView(new DeliveryRequirementService.RequirementView(
                row, requirementService.countOf(row), row.getMinimumQuantity(), row.getCountingUnit()))).toList());
    }

    @GetMapping("/requirements")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<RequirementVO>> listRequirements(
            @RequestParam String ownerModule, @RequestParam String entityType, @RequestParam Long entityId) {
        return success(requirementService.viewByEntity(ownerModule, entityType, entityId).stream()
                .map(DeliveryController::toView).toList());
    }

    @PostMapping("/requirements/{id}/confirm")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    public CommonResult<RequirementVO> confirm(@PathVariable Long id) {
        return success(toView(requirementService.confirm(id)));
    }

    // ---------- 材料记录 ----------

    @GetMapping("/materials")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<MaterialVO>> listMaterials(
            @RequestParam String ownerModule, @RequestParam String entityType, @RequestParam Long entityId,
            @RequestParam(required = false) String typeCode) {
        return success(materialService.listByEntity(ownerModule, entityType, entityId, typeCode).stream()
                .map(DeliveryController::toView).toList());
    }

    @PostMapping("/materials")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    public CommonResult<MaterialVO> registerMaterial(@Valid @RequestBody MaterialRegisterReqVO reqVO) {
        return success(toView(materialService.register(reqVO.getOwnerModule(), reqVO.getEntityType(),
                reqVO.getEntityId(), reqVO.getTypeCode(), reqVO.getFileReferenceId(), reqVO.getTitle(),
                reqVO.getSourceKind())));
    }

    @PostMapping("/materials/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    public CommonResult<MaterialVO> withdrawMaterial(@PathVariable Long id) {
        return success(toView(materialService.withdraw(id)));
    }

    // ---------- 提交台账 ----------

    @PostMapping("/submissions")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    public CommonResult<SubmissionOutcomeVO> submit(@Valid @RequestBody SubmissionReqVO reqVO) {
        var outcome = requirementService.submit(reqVO.getRequirementId(), reqVO.getRequestKey(),
                reqVO.getMaterialIds());
        SubmissionOutcomeVO vo = new SubmissionOutcomeVO();
        vo.setSubmissionId(outcome.submission().getId());
        vo.setReplay(outcome.replay());
        vo.setRequirementId(outcome.requirement().getId());
        vo.setRequirementStatus(outcome.requirement().getStatus());
        vo.setCount(outcome.count());
        vo.setRequestKey(outcome.submission().getRequestKey());
        return success(vo);
    }

    @PostMapping("/submissions/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    public CommonResult<SubmissionOutcomeVO> withdrawSubmission(@PathVariable Long id) {
        var outcome = requirementService.withdrawSubmission(id);
        SubmissionOutcomeVO vo = new SubmissionOutcomeVO();
        vo.setSubmissionId(outcome.submission().getId());
        vo.setReplay(outcome.replay());
        vo.setRequirementId(outcome.requirement().getId());
        vo.setRequirementStatus(outcome.requirement().getStatus());
        vo.setCount(outcome.count());
        vo.setRequestKey(outcome.submission().getRequestKey());
        return success(vo);
    }

    @GetMapping("/submissions")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<SubmissionVO>> listSubmissions(@RequestParam Long requirementId) {
        return success(submissionMapper.selectByRequirement(requirementId).stream()
                .map(DeliveryController::toView).toList());
    }
}
