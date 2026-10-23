package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFileCommand;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionFeedbackRow;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.report.InspectionFeedbackExporter;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionFeedbackExportService {
    private final InspectionFeedbackService feedback;
    private final InspectionFeedbackQueryMapper queries;
    private final InspectionFeedbackExportMapper exports;
    private final InspectionFeedbackExportItemMapper items;
    private final InspectionRuleQueryService rules;
    private final InspectionAccessService access;
    private final InspectionFeedbackExportAccess exportAccess;
    private final NativeGeneratedFileApi files;
    private final FileEvidenceApi evidence;
    private final InspectionFeedbackExporter renderer = new InspectionFeedbackExporter();
    public record Request(InspectionRequestContext context, InspectionFeedbackService.PageQuery filter, String format) { }
    public record Query(InspectionRequestContext context, Long exportId, boolean lock) { }
    public record Page(InspectionRequestContext context, PageParam page) { }
    @Data public static class View {
        private Long id, version, exportedBy, artifactId;
        private String formatCode, statusCode, referenceKey, fileName, failureMessage;
        private Integer resultCount, fileVersionNo;
        private LocalDateTime fromAt, toAt, dataAsOf, generatedAt;
    }
    @Transactional(rollbackFor = Exception.class)
    public View request(Request command) {
        access.permission(command.context(), "pms:inspection-feedback:export");
        access.permission(command.context(), "pms:inspection-feedback:analyze");
        require(command.format() != null && Set.of("XLSX", "CSV").contains(command.format()), "请选择Excel或CSV格式");
        var query = feedback.scope(new InspectionFeedbackService.ScopeQuery(command.context(), command.filter()));
        var rows = queries.selectExportRows(query); require(rows.size() <= 10000, "单次导出最多10000行，请收窄筛选范围后重试");
        var record = BeanUtils.toBean(command.filter(), InspectionFeedbackExportDO.class);
        record.setTenantId(command.context().tenantId()); record.setVersion(1L); record.setExportedBy(command.context().actorId());
        record.setFormatCode(command.format()); record.setStatusCode("REQUESTED"); record.setDataAsOf(query.getDataAsOf());
        record.setCurrentOnly(Boolean.TRUE.equals(query.getCurrentOnly())); record.setIncludeDetails(query.isDetails()); record.setResultCount(rows.size()); exports.insert(record);
        for (var row : rows) {
            var item = new InspectionFeedbackExportItemDO(); item.setTenantId(record.getTenantId()); item.setVersion(1L);
            item.setExportId(record.getId()); item.setProjectId(row.getProjectId()); item.setAnnotationId(row.getAnnotationId());
            item.setConfirmationId(row.getConfirmationId()); item.setActiveFlag(row.getActiveFlag()); items.insert(item);
        }
        return view(record);
    }
    @Transactional(rollbackFor = Exception.class)
    public View generate(Query command) {
        var row = exportAccess.authorized(new InspectionFeedbackExportAccess.Query(command.context(), command.exportId(), true));
        if (!Objects.equals(row.getVersion(), command.context().requireVersion()))
            throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT, "导出记录已更新，请刷新后重试", row.getVersion());
        require(Set.of("REQUESTED", "FAILED").contains(row.getStatusCode()), "当前导出不可重新生成");
        var frozen = items.selectFrozenRows(new InspectionFeedbackExportRows(row.getTenantId(), row.getId(), row.getIncludeDetails()));
        require(frozen.size() == row.getResultCount(), "导出来源不完整，不能生成部分结果");
        var commandCache = new HashMap<Long, String>(); var contentRows = new ArrayList<InspectionFeedbackExporter.Row>();
        for (var value : frozen) contentRows.add(exportRow(new ExportRow(value, row.getIncludeDetails(), commandCache)));
        InspectionFeedbackExporter.Exported document;
        try {
            document = renderer.render(new InspectionFeedbackExporter.Request(InspectionFeedbackExporter.Format.valueOf(row.getFormatCode()), contentRows));
            require(document.content().length <= 52_428_800, "导出文件超过50MB，请收窄筛选范围");
        } catch (IllegalArgumentException | InspectionException failure) {
            row.setStatusCode("FAILED"); row.setFailureMessage(failure.getMessage()); exports.updateById(row); return view(row);
        }
        row.setStatusCode("GENERATING"); require(exports.updateById(row) == 1, "导出记录已更新");
        var file = files.create(new NativeGeneratedFileCommand(row.getTenantId(), command.context().actorId(),
                "inspection-feedback-export:" + row.getId() + ":" + row.getVersion(), "SRV", "INSPECTION_FEEDBACK_EXPORT", row.getId(),
                row.getVersion(), "EXPORT", "INSPECTION_FEEDBACK_EXPORT", "inspection-feedback-" + row.getId() + ("CSV".equals(row.getFormatCode()) ? ".csv" : ".xlsx"),
                document.mediaType(), document.content()));
        var fact = evidence.inspectDocument(row.getTenantId(), file.referenceId());
        require(fact != null && fact.available(), "导出文件引用不可用");
        row.setArtifactId(file.artifactId()); row.setFileVersionNo(file.versionNo()); row.setReferenceKey(fact.referenceKey());
        row.setFileName(file.fileName()); row.setSha256(file.sha256()); row.setGeneratedAt(LocalDateTime.now()); row.setFailureMessage("");
        row.setStatusCode("AVAILABLE"); require(exports.updateById(row) == 1, "导出记录已更新"); return view(row);
    }
    /** 在生成事务已回滚之后独立记录失败；不会覆盖并发成功或更新后的版本。 */
    @Transactional(rollbackFor = Exception.class)
    public View recordFailure(Query command) {
        var row = exportAccess.authorized(new InspectionFeedbackExportAccess.Query(command.context(), command.exportId(), true));
        if (Objects.equals(row.getVersion(), command.context().expectedVersion()) && Set.of("REQUESTED", "FAILED").contains(row.getStatusCode())) {
            row.setStatusCode("FAILED"); row.setFailureMessage("导出文件生成失败，来源快照保留，可重试生成"); exports.updateById(row);
        }
        return view(row);
    }
    public View get(Query query) { return view(exportAccess.authorized(new InspectionFeedbackExportAccess.Query(query.context(), query.exportId(), query.lock()))); }
    public PageResult<View> page(Page query) {
        access.permission(query.context(), "pms:inspection-feedback:export");
        var page = exports.selectMine(new InspectionFeedbackExportPageQuery(query.context().tenantId(), query.context().actorId(), query.page()));
        return new PageResult<>(page.getList().stream().map(InspectionFeedbackExportService::view).toList(), page.getTotal());
    }
    private record ExportRow(InspectionFeedbackRow row, boolean details, Map<Long, String> commands) { }
    private InspectionFeedbackExporter.Row exportRow(ExportRow input) {
        var row = input.row();
        String commands = input.details() ? input.commands().computeIfAbsent(row.getRuleRevisionId(), id -> rules.detail(id).getCommands().stream()
                .sorted(Comparator.comparingInt(c -> c.executionOrder())).map(c -> c.commandContent()).collect(Collectors.joining("\n"))) : "无字段权限";
        return InspectionFeedbackExporter.Row.builder().detectionId(row.getDetectionId()).inspectionItem(row.getInspectionItem())
                .ruleRevision("R" + row.getRevisionNo() + " / " + row.getRuleRevisionId()).category(row.getCategoryName()).severity(row.getSeverityName())
                .commands(commands).output(input.details() ? row.getOutputSummary() : "无字段权限").reason(row.getReason()).annotator(String.valueOf(row.getAnnotatedBy()))
                .annotatedAt(String.valueOf(row.getAnnotatedAt())).taskCode(row.getTaskCode()).deviceSerial(row.getDeviceSerial()).productModel(row.getProductModel())
                .confirmation(row.getValidFlag() == null ? "待确认" : row.getValidFlag() ? "有效误报" : "非有效误报")
                .currentStatus(Boolean.TRUE.equals(row.getActiveFlag()) ? "当前" : "历史或已取消误报").build();
    }
    private static View view(InspectionFeedbackExportDO row) { return BeanUtils.toBean(row, View.class); }
}
