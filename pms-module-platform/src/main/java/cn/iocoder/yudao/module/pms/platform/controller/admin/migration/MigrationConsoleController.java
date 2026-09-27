package cn.iocoder.yudao.module.pms.platform.controller.admin.migration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendExternalMappingCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationIssueCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationSourceRecordCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationSourceRecordsCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ClaimStagedBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CloseMigrationIssueCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CompleteReconciliationCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CreateImportBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ImportStagingDecision;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchClaimResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationImportFailureCode;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationIssueFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MarkStagedReadyCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewRecordRequest;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationPreviewResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPage;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.SourceReconciliationType;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationBatchDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationBatchMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationBatchPageQuery;
import cn.iocoder.yudao.module.pms.platform.service.migration.MigrationConsoleService;
import cn.iocoder.yudao.module.pms.platform.service.migration.MigrationPreviewService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationIssueDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationIssueMapper;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 迁移工具台：迁移证据Owner合同（批次导入/暂存/认领/对账/问题结案）的只读预演与控制台入口。
 * 所有命令的租户取自服务端租户上下文，不信任客户端显式租户。
 */
@RestController
@RequestMapping("/api/v1/pms/migration")
@Tag(name = "管理后台 - PMS 迁移工具台")
@Validated
@RequiredArgsConstructor
public class MigrationConsoleController {

    private final PlatformMigrationEvidenceApi evidenceApi;
    private final MigrationConsoleService consoleService;
    private final MigrationPreviewService previewService;
    private final MigrationBatchMapper batchMapper;
    private final MigrationIssueMapper issueMapper;

    // ========== 只读：批次台账 / 来源记录 / 预演 ==========

    /** 批次分页筛选。 */
    @Data
    public static class MigrationBatchPageReqVO extends PageParam {

        private String ownerContextCode;

        private String purposeCode;

        private String sourceSystem;

        /** 批次状态：IMPORTING/STAGED_READY/RECONCILING/COMPLETED/FAILED。 */
        private String status;
    }

    @GetMapping("/batches/page")
    @Operation(summary = "分页查询迁移批次")
    @PreAuthorize("@ss.hasPermission('pms:migration:query')")
    public CommonResult<PageResult<MigrationBatchRespVO>> getBatchPage(
            @Valid MigrationBatchPageReqVO pageVO) {
        MigrationBatchPageQuery query = new MigrationBatchPageQuery();
        query.setTenantId(tenantId());
        query.setOwnerContextCode(pageVO.getOwnerContextCode());
        query.setPurposeCode(pageVO.getPurposeCode());
        query.setSourceSystem(pageVO.getSourceSystem());
        query.setBatchStatus(pageVO.getStatus());
        query.setPageNo(pageVO.getPageNo());
        query.setPageSize(pageVO.getPageSize());
        PageResult<MigrationBatchDO> page = batchMapper.selectPageByFilter(query);
        List<MigrationBatchRespVO> list = page.getList().stream().map(MigrationBatchRespVO::of).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/batches/{id}/source-records")
    @Operation(summary = "游标分页查询批次来源记录")
    @PreAuthorize("@ss.hasPermission('pms:migration:query')")
    public CommonResult<MigrationSourceRecordPage> getSourceRecords(
            @PathVariable("id") Long id,
            @RequestParam(value = "afterSourceRecordId", required = false) Long afterSourceRecordId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return success(evidenceApi.pageSourceRecords(new MigrationSourceRecordPageQuery(
                tenantId(), id, afterSourceRecordId, limit)));
    }

    /** 迁移预演请求：来源身份集合 + 可选的待导入内容校验和。 */
    @Data
    public static class PreviewReqVO {

        @NotBlank
        private String sourceSystem;

        @NotBlank
        private String sourceTable;

        @NotEmpty
        @Valid
        private List<PreviewRecordVO> records;

        @Data
        public static class PreviewRecordVO {

            @NotBlank
            private String sourcePk;

            @Pattern(regexp = "[0-9a-f]{64}", message = "sourceChecksum 必须为小写 sha256 十六进制")
            private String sourceChecksum;
        }

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("不支持的预演请求字段: " + name);
        }
    }

    @PostMapping("/preview")
    @Operation(summary = "迁移预演（只读，不写任何证据表）")
    @PreAuthorize("@ss.hasPermission('pms:migration:query')")
    public CommonResult<MigrationPreviewResult> preview(@Valid @RequestBody PreviewReqVO reqVO) {
        List<MigrationPreviewRecordRequest> records = reqVO.getRecords().stream()
                .map(row -> new MigrationPreviewRecordRequest(row.getSourcePk(), row.getSourceChecksum()))
                .toList();
        return success(previewService.preview(new MigrationPreviewCommand(
                tenantId(), reqVO.getSourceSystem(), reqVO.getSourceTable(), records)));
    }

    // ========== 暂存阶段：建批 / 追加来源 / 暂存就绪 ==========

    @Data
    public static class BatchCreateReqVO {

        @NotBlank
        private String ownerContextCode;

        @NotBlank
        private String purposeCode;

        @NotBlank
        private String releaseId;

        @NotBlank
        private String sourceSystem;

        @NotBlank
        private String sourceTable;

        @NotBlank
        private String manifestSchemaVersion;

        @NotNull
        @PositiveOrZero
        private Long expectedRowCount;

        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}", message = "contentSha256 必须为小写 sha256 十六进制")
        private String contentSha256;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime exportedAt;

        private Long previousBatchId;

        private Long previousIssueId;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String correlationId;
    }

    @PostMapping("/batches")
    @Operation(summary = "创建迁移导入批次（幂等）")
    @PreAuthorize("@ss.hasPermission('pms:migration:stage')")
    public CommonResult<MigrationBatchFact> createBatch(@Valid @RequestBody BatchCreateReqVO reqVO) {
        return success(consoleService.createBatch(new CreateImportBatchCommand(
                tenantId(), reqVO.getOwnerContextCode(), reqVO.getPurposeCode(), reqVO.getReleaseId(),
                reqVO.getSourceSystem(), reqVO.getSourceTable(), reqVO.getManifestSchemaVersion(),
                reqVO.getExpectedRowCount(), reqVO.getContentSha256(), reqVO.getExportedAt(),
                reqVO.getPreviousBatchId(), reqVO.getPreviousIssueId(),
                reqVO.getIdempotencyKey(), reqVO.getCorrelationId())));
    }

    @Data
    public static class SourceRecordAppendReqVO {

        @NotBlank
        private String sourcePk;

        private String sourceBusinessKey;

        @NotNull
        private Map<String, Object> sourcePayload;

        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}", message = "sourceChecksum 必须为小写 sha256 十六进制")
        private String sourceChecksum;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime extractedAt;
    }

    @Data
    public static class SourceRecordsAppendReqVO {

        @NotEmpty
        @Valid
        private List<SourceRecordAppendReqVO> records;

        @NotBlank
        private String correlationId;

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("不支持的来源追加字段: " + name);
        }
    }

    @PostMapping("/batches/{id}/source-records")
    @Operation(summary = "向批次追加来源记录（同批次同来源身份）")
    @PreAuthorize("@ss.hasPermission('pms:migration:stage')")
    public CommonResult<List<MigrationSourceRecordFact>> appendSourceRecords(
            @PathVariable("id") Long id, @Valid @RequestBody SourceRecordsAppendReqVO reqVO) {
        List<AppendMigrationSourceRecordCommand> records = new ArrayList<>(reqVO.getRecords().size());
        MigrationBatchDO batch = batchMapper.selectById(id);
        if (batch == null || !tenantId().equals(batch.getTenantId())) {
            throw new PlatformMigrationEvidenceException(
                    PlatformMigrationEvidenceException.Code.BATCH_NOT_FOUND, "batch not found: " + id);
        }
        for (SourceRecordAppendReqVO row : reqVO.getRecords()) {
            records.add(new AppendMigrationSourceRecordCommand(tenantId(), id, batch.getSourceSystem(),
                    batch.getSourceTable(), row.getSourcePk(), row.getSourceBusinessKey(),
                    JsonUtils.toJsonString(row.getSourcePayload()), row.getSourceChecksum(),
                    row.getExtractedAt(), reqVO.getCorrelationId()));
        }
        return success(consoleService.appendSourceRecords(new AppendMigrationSourceRecordsCommand(records)));
    }

    @Data
    public static class StagedReadyReqVO {

        @NotNull
        private Integer expectedBatchVersion;

        @NotNull
        private ImportStagingDecision decision;

        private Long manifestRowCount;

        private String manifestSchemaVersion;

        @Pattern(regexp = "[0-9a-f]{64}", message = "manifestContentSha256 必须为小写 sha256 十六进制")
        private String manifestContentSha256;

        private MigrationImportFailureCode failureCode;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String correlationId;
    }

    @PostMapping("/batches/{id}/actions/staged-ready")
    @Operation(summary = "批次暂存就绪或失败收尾")
    @PreAuthorize("@ss.hasPermission('pms:migration:stage')")
    public CommonResult<MigrationBatchFact> markStagedReady(
            @PathVariable("id") Long id, @Valid @RequestBody StagedReadyReqVO reqVO) {
        return success(consoleService.markStagedReady(new MarkStagedReadyCommand(
                tenantId(), id, reqVO.getExpectedBatchVersion(), reqVO.getDecision(),
                reqVO.getManifestRowCount(), reqVO.getManifestSchemaVersion(), reqVO.getManifestContentSha256(),
                reqVO.getFailureCode(), reqVO.getIdempotencyKey(), reqVO.getCorrelationId())));
    }

    // ========== 对账阶段：认领 / 映射 / 问题 / 对账完成 / 问题结案 ==========

    @Data
    public static class ClaimReqVO {

        @NotBlank
        private String ownerContextCode;

        @NotBlank
        private String purposeCode;

        @NotEmpty
        private List<String> sourceSystems;

        @NotEmpty
        private List<String> sourceTables;
    }

    @PostMapping("/actions/claim")
    @Operation(summary = "认领一个 STAGED_READY 批次进入 RECONCILING")
    @PreAuthorize("@ss.hasPermission('pms:migration:reconcile')")
    public CommonResult<MigrationBatchClaimResult> claim(@Valid @RequestBody ClaimReqVO reqVO) {
        return success(consoleService.claim(new ClaimStagedBatchCommand(
                tenantId(), reqVO.getOwnerContextCode(), reqVO.getPurposeCode(),
                reqVO.getSourceSystems(), reqVO.getSourceTables(), UUID.randomUUID().toString())));
    }

    @Data
    public static class MappingTargetVO {

        @NotBlank
        private String targetContext;

        @NotBlank
        private String targetObjectType;

        @NotBlank
        private String targetTable;

        @NotNull
        private Long targetId;

        @NotBlank
        private String targetRole;

        @NotNull
        private Integer targetSequence;
    }

    @Data
    public static class MappingAppendReqVO {

        @NotNull
        private Long sourceRecordId;

        @NotNull
        private SourceReconciliationType resultType;

        /** RETAINED 时必须为空数组；MAPPED 时至少一个目标，由证据层合同校验。 */
        @NotNull
        @Valid
        private List<MappingTargetVO> targets;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String correlationId;
    }

    @PostMapping("/batches/{id}/mappings")
    @Operation(summary = "登记来源记录的外部键映射结论")
    @PreAuthorize("@ss.hasPermission('pms:migration:reconcile')")
    public CommonResult<Map<String, Object>> appendMapping(
            @PathVariable("id") Long id, @Valid @RequestBody MappingAppendReqVO reqVO) {
        List<ExternalTargetMapping> targets = reqVO.getTargets().stream()
                .map(row -> new ExternalTargetMapping(row.getTargetContext(), row.getTargetObjectType(),
                        row.getTargetTable(), row.getTargetId(), row.getTargetRole(), row.getTargetSequence()))
                .toList();
        var result = consoleService.appendMapping(new AppendExternalMappingCommand(
                tenantId(), id, reqVO.getSourceRecordId(), reqVO.getResultType(), targets,
                reqVO.getIdempotencyKey(), reqVO.getCorrelationId()));
        return success(Map.of("sourceRecordId", result.sourceRecordId(),
                "resultType", result.resultType().name(), "mappingIds", result.mappingIds()));
    }

    @GetMapping("/batches/{id}/issues")
    @Operation(summary = "查询批次登记的迁移问题")
    @PreAuthorize("@ss.hasPermission('pms:migration:query')")
    public CommonResult<List<Map<String, Object>>> getIssues(@PathVariable("id") Long id) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MigrationIssueDO issue : issueMapper.selectList(new LambdaQueryWrapperX<MigrationIssueDO>()
                .eq(MigrationIssueDO::getTenantId, tenantId())
                .eq(MigrationIssueDO::getBatchId, id)
                .orderByAsc(MigrationIssueDO::getId))) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("issueId", issue.getId());
            row.put("batchId", issue.getBatchId());
            row.put("sourceRecordId", issue.getSourceRecordId());
            row.put("issueKey", issue.getIssueKey());
            row.put("issueType", issue.getIssueType());
            row.put("rawBusinessKey", issue.getRawBusinessKey());
            row.put("candidateTargetIds", issue.getCandidateTargetIds());
            row.put("rawPayload", issue.getRawPayload());
            row.put("status", issue.getIssueStatus());
            row.put("resolverUserId", issue.getResolverUserId());
            row.put("ruleVersion", issue.getRuleVersion());
            row.put("targetResult", issue.getTargetResult());
            row.put("resolvedAt", issue.getResolvedAt());
            row.put("version", issue.getVersion());
            list.add(row);
        }
        return success(list);
    }

    @Data
    public static class IssueAppendReqVO {

        @NotNull
        private Long sourceRecordId;

        @NotBlank
        private String issueKey;

        @NotBlank
        private String issueType;

        private String rawBusinessKey;

        private List<Long> candidateTargetIds;

        private Map<String, Object> rawPayload;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String correlationId;
    }

    @PostMapping("/batches/{id}/issues")
    @Operation(summary = "登记来源记录的迁移问题")
    @PreAuthorize("@ss.hasPermission('pms:migration:reconcile')")
    public CommonResult<MigrationIssueFact> appendIssue(
            @PathVariable("id") Long id, @Valid @RequestBody IssueAppendReqVO reqVO) {
        return success(consoleService.appendIssue(new AppendMigrationIssueCommand(
                tenantId(), id, reqVO.getSourceRecordId(), reqVO.getIssueKey(), reqVO.getIssueType(),
                reqVO.getRawBusinessKey(), reqVO.getCandidateTargetIds(),
                reqVO.getRawPayload() == null ? "{}" : JsonUtils.toJsonString(reqVO.getRawPayload()),
                reqVO.getIdempotencyKey(), reqVO.getCorrelationId())));
    }

    @Data
    public static class CompleteReconciliationReqVO {

        @NotNull
        private Integer expectedBatchVersion;

        @NotNull
        @PositiveOrZero
        private Long expectedMappedCount;

        @NotNull
        @PositiveOrZero
        private Long expectedIssueCount;

        @NotNull
        @PositiveOrZero
        private Long expectedRetainedCount;

        @NotBlank
        private String ruleVersion;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String correlationId;
    }

    @PostMapping("/batches/{id}/actions/complete-reconciliation")
    @Operation(summary = "完成批次对账（期望计数必须与事实一致）")
    @PreAuthorize("@ss.hasPermission('pms:migration:reconcile')")
    public CommonResult<MigrationBatchFact> completeReconciliation(
            @PathVariable("id") Long id, @Valid @RequestBody CompleteReconciliationReqVO reqVO) {
        long expectedSourceCount = reqVO.getExpectedMappedCount() + reqVO.getExpectedIssueCount()
                + reqVO.getExpectedRetainedCount();
        return success(consoleService.completeReconciliation(new CompleteReconciliationCommand(
                tenantId(), id, reqVO.getExpectedBatchVersion(), expectedSourceCount,
                reqVO.getExpectedMappedCount(), reqVO.getExpectedIssueCount(), reqVO.getExpectedRetainedCount(),
                reqVO.getRuleVersion(), reqVO.getIdempotencyKey(), reqVO.getCorrelationId())));
    }

    @Data
    public static class IssueCloseReqVO {

        @NotBlank
        private String ruleVersion;

        @NotNull
        private Map<String, Object> targetResult;

        @NotBlank
        private String idempotencyKey;

        @NotBlank
        private String correlationId;
    }

    @PostMapping("/issues/{id}/actions/close")
    @Operation(summary = "结案迁移问题（结案人取当前登录用户）")
    @PreAuthorize("@ss.hasPermission('pms:migration:reconcile')")
    public CommonResult<MigrationIssueFact> closeIssue(
            @PathVariable("id") Long id, @Valid @RequestBody IssueCloseReqVO reqVO) {
        Long resolverUserId = SecurityFrameworkUtils.getLoginUserId();
        if (resolverUserId == null) {
            throw new PlatformMigrationEvidenceException(
                    PlatformMigrationEvidenceException.Code.INVALID_REQUEST,
                    "resolver requires an authenticated user");
        }
        return success(consoleService.closeIssue(new CloseMigrationIssueCommand(
                tenantId(), id, resolverUserId, reqVO.getRuleVersion(),
                JsonUtils.toJsonString(reqVO.getTargetResult()), reqVO.getIdempotencyKey(),
                reqVO.getCorrelationId())));
    }

    private Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }
}
