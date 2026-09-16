package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.ImportConflictDTO;
import cn.iocoder.yudao.module.pms.lowcode.dto.PromotionPipelineDTO;
import cn.iocoder.yudao.module.pms.lowcode.dto.VersionDiffDTO;
import cn.iocoder.yudao.module.pms.lowcode.dto.VersionTreeNode;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeConfigVersion;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeConfigVersionService;
import cn.iocoder.yudao.module.pms.lowcode.version.EnvironmentPromotionService;
import cn.iocoder.yudao.module.pms.lowcode.version.PromotionGateService;
import cn.iocoder.yudao.module.pms.lowcode.version.PublishImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 低代码配置版本管理 Controller。
 *
 * <p>提供版本历史查询、版本 Diff 对比、版本回滚、环境晋升、配置包导出等接口。
 * 写操作需对应权限，并记录操作日志。</p>
 */
@Tag(name = "低代码配置版本管理", description = "LowCode config versioning APIs")
@RestController
@RequestMapping("/api/lowcode/version")
@RequiredArgsConstructor
public class LowCodeConfigVersionController {

    private final LowCodeConfigVersionService configVersionService;
    private final EnvironmentPromotionService promotionService;
    private final PromotionGateService gateService;
    private final PublishImpactService impactService;

    @Operation(summary = "查询版本历史")
    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('lowcode:version:list')")
    public CommonResult<List<LowCodeConfigVersion>> history(@RequestParam String configType,
                                                        @RequestParam Long configId) {
        return CommonResult.success(configVersionService.getVersionHistory(configType, configId));
    }

    @Operation(summary = "查询版本树（按 parentVersionId 构建分支树，支持多分支）")
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermission('lowcode:version:list')")
    public CommonResult<List<VersionTreeNode>> tree(@RequestParam String configType,
                                                @RequestParam Long configId) {
        return CommonResult.success(configVersionService.getVersionTree(configType, configId));
    }

    @Operation(summary = "对比两个版本差异")
    @GetMapping("/diff")
    @PreAuthorize("@ss.hasPermission('lowcode:version:diff')")
    public CommonResult<VersionDiffDTO> diff(@RequestParam String configType,
                                        @RequestParam Long configId,
                                        @RequestParam Integer fromVersion,
                                        @RequestParam Integer toVersion) {
        return CommonResult.success(configVersionService.diff(configType, configId, fromVersion, toVersion));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/rollback")
    @PreAuthorize("@ss.hasPermission('lowcode:version:rollback')")
    public CommonResult<LowCodeConfigVersion> rollback(@RequestParam String configType,
                                                   @RequestParam Long configId,
                                                   @RequestParam Integer targetVersion,
                                                   @RequestParam(required = false) String changeLog) {
        return CommonResult.success(configVersionService.rollback(configType, configId, targetVersion, changeLog));
    }

    @Operation(summary = "回滚预览（批次5-T5，对比当前版本与目标版本差异，不实际回滚）")
    @GetMapping("/rollback-preview")
    @PreAuthorize("@ss.hasPermission('lowcode:version:rollback')")
    public CommonResult<VersionDiffDTO> rollbackPreview(@RequestParam String configType,
                                                    @RequestParam Long configId,
                                                    @RequestParam Integer targetVersion) {
        return CommonResult.success(configVersionService.rollbackPreview(configType, configId, targetVersion));
    }

    @Operation(summary = "发布影响范围分析（批次5-T5）")
    @GetMapping("/publish-impact")
    @PreAuthorize("@ss.hasPermission('lowcode:version:list')")
    public CommonResult<cn.iocoder.yudao.module.pms.lowcode.dto.PublishImpactDTO> publishImpact(@RequestParam String configType,
                                                                            @RequestParam Long configId,
                                                                            @RequestParam(required = false) String configCode) {
        return CommonResult.success(impactService.analyzeImpact(configType, configId, configCode));
    }

    @Operation(summary = "环境晋升")
    @PostMapping("/promote")
    @PreAuthorize("@ss.hasPermission('lowcode:version:promote')")
    public CommonResult<Void> promote(@RequestParam String targetEnvironment,
                                 @RequestBody List<String> configCodes) {
        promotionService.promote(targetEnvironment, configCodes);
        return CommonResult.success(null);
    }

    @Operation(summary = "查询晋升管道状态（批次5-T2）")
    @GetMapping("/pipeline")
    @PreAuthorize("@ss.hasPermission('lowcode:version:list')")
    public CommonResult<List<PromotionPipelineDTO>> pipeline(@RequestParam List<String> configCodes) {
        return CommonResult.success(promotionService.getPipelineStatus(configCodes));
    }

    @Operation(summary = "晋升门禁预检（批次5-T2，不实际晋升）")
    @PostMapping("/gate-check")
    @PreAuthorize("@ss.hasPermission('lowcode:version:promote')")
    public CommonResult<PromotionGateService.GateResult> gateCheck(@RequestBody GateCheckRequest req) {
        return CommonResult.success(gateService.check(req.getSourceEnvironment(), req.getTargetEnvironment(), req.getConfigCodes()));
    }

    @Operation(summary = "导出配置包")
    @GetMapping("/export-package")
    @PreAuthorize("@ss.hasPermission('lowcode:version:export')")
    public CommonResult<String> exportPackage(@RequestParam List<String> configCodes) {
        return CommonResult.success(promotionService.exportPackageJson(configCodes));
    }

    @Operation(summary = "导出配置包（zip）")
    @PostMapping("/export-package")
    @PreAuthorize("@ss.hasPermission('lowcode:version:export')")
    public ResponseEntity<byte[]> exportPackageZip(@RequestBody ExportPackageRequest req) {
        byte[] zip = promotionService.exportPackageZip(req.getConfigCodes(), req.getTargetEnvironment());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=lowcode-package.zip")
                .header("Content-Type", "application/zip")
                .body(zip);
    }

    @Operation(summary = "导入配置包")
    @PostMapping("/import-package")
    @PreAuthorize("@ss.hasPermission('lowcode:version:import')")
    public CommonResult<Void> importPackage(@RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "false") boolean overwrite) {
        try {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            promotionService.importPackageWithConfirm(json, overwrite);
            return CommonResult.success(null);
        } catch (Exception e) {
            throw new RuntimeException("导入失败", e);
        }
    }

    @Operation(summary = "检测导入冲突（批次5-T3）")
    @PostMapping("/import-conflicts")
    @PreAuthorize("@ss.hasPermission('lowcode:version:import')")
    public CommonResult<ImportConflictDTO> detectImportConflicts(@RequestBody DetectConflictsRequest req) {
        return CommonResult.success(promotionService.detectImportConflicts(req.getPackageJson(), req.getTargetEnvironment()));
    }

    @Operation(summary = "按解决方案导入配置包（批次5-T3）")
    @PostMapping("/import-resolve")
    @PreAuthorize("@ss.hasPermission('lowcode:version:import')")
    public CommonResult<Void> importWithResolution(@RequestBody ImportResolveRequest req) {
        promotionService.importPackageWithResolution(req.getPackageJson(), req.getTargetEnvironment(), req.getResolutions());
        return CommonResult.success(null);
    }

    @Operation(summary = "创建分支（批次5-T1）")
    @PostMapping("/branch")
    @PreAuthorize("@ss.hasPermission('lowcode:version:branch')")
    public CommonResult<LowCodeConfigVersion> createBranch(@RequestBody CreateBranchRequest req) {
        return CommonResult.success(configVersionService.createBranch(
                req.getConfigType(), req.getConfigId(),
                req.getBaseVersionId(), req.getBranchName(), req.getChangeLog()));
    }

    @Operation(summary = "为版本添加标签（批次5-T1）")
    @PostMapping("/tag")
    @PreAuthorize("@ss.hasPermission('lowcode:version:tag')")
    public CommonResult<LowCodeConfigVersion> addTag(@RequestBody AddTagRequest req) {
        return CommonResult.success(configVersionService.addTag(
                req.getConfigType(), req.getConfigId(),
                req.getVersionId(), req.getTag()));
    }

    /**
     * 导出配置包请求体
     */
    @Data
    @Schema(description = "导出配置包请求")
    public static class ExportPackageRequest {
        @Schema(description = "配置编码列表")
        private List<String> configCodes;
        @Schema(description = "目标环境")
        private String targetEnvironment;
    }

    @Data
    @Schema(description = "创建分支请求")
    public static class CreateBranchRequest {
        @Schema(description = "配置类型") private String configType;
        @Schema(description = "配置 ID") private Long configId;
        @Schema(description = "分支起点版本记录 ID") private Long baseVersionId;
        @Schema(description = "新分支名（不能为 main）") private String branchName;
        @Schema(description = "变更说明") private String changeLog;
    }

    @Data
    @Schema(description = "添加标签请求")
    public static class AddTagRequest {
        @Schema(description = "配置类型") private String configType;
        @Schema(description = "配置 ID") private Long configId;
        @Schema(description = "版本记录 ID") private Long versionId;
        @Schema(description = "要添加的标签") private String tag;
    }

    @Data
    @Schema(description = "晋升门禁预检请求")
    public static class GateCheckRequest {
        @Schema(description = "源环境（DEV/TEST）") private String sourceEnvironment;
        @Schema(description = "目标环境（TEST/PROD）") private String targetEnvironment;
        @Schema(description = "配置编码列表") private List<String> configCodes;
    }

    @Data
    @Schema(description = "检测导入冲突请求")
    public static class DetectConflictsRequest {
        @Schema(description = "配置包 JSON 字符串（zip 内 config.json 内容）") private String packageJson;
        @Schema(description = "目标环境") private String targetEnvironment;
    }

    @Data
    @Schema(description = "按解决方案导入请求")
    public static class ImportResolveRequest {
        @Schema(description = "配置包 JSON 字符串") private String packageJson;
        @Schema(description = "目标环境") private String targetEnvironment;
        @Schema(description = "冲突解决方案 Map<configCode, KEEP_SOURCE|KEEP_TARGET|SKIP>")
        private java.util.Map<String, String> resolutions;
    }
}
