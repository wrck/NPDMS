package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionReportContentQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** A task report adopts all device reports, preserving their DOC originals and external identities. */
@Service
@RequiredArgsConstructor
public class InspectionUmcBundle {
    public static final String TEMPLATE = "umc-report-set-v1";
    private final InspectionReportRevisionMapper reports;
    private final InspectionReportFileMapper files;
    private final FileEvidenceApi evidence;
    public record Snapshot(List<Long> reportIds, List<Long> resultIds) { }
    public record Query(InspectionReportRevisionDO report) { }
    public static boolean matches(InspectionReportRevisionDO report) { return "UMC".equals(report.getSourceCode()) && TEMPLATE.equals(report.getTemplateVersion()); }
    public static Snapshot snapshot(InspectionReportRevisionDO report) {
        require(matches(report) && report.getResultJson() != null && report.getResultSha256() != null, "UMC正式报告集合缺少来源快照");
        var result = JsonUtils.parseObject(report.getResultJson(), Snapshot.class);
        require(result != null && result.reportIds() != null && !result.reportIds().isEmpty() && result.resultIds() != null
                && new HashSet<>(result.reportIds()).size() == result.reportIds().size()
                && new HashSet<>(result.resultIds()).size() == result.resultIds().size()
                && report.getResultSha256().equals(DigestUtil.sha256Hex(JsonUtils.toJsonString(result))), "UMC正式报告集合摘要或来源无效");
        return result;
    }
    public List<InspectionReportFileDO> files(Query query) {
        var report = query.report(); var snapshot = snapshot(report); var ids = new HashSet<>(snapshot.reportIds());
        for (var id : ids) {
            var source = reports.selectById(id);
            require(source != null && Objects.equals(source.getTenantId(), report.getTenantId()) && Objects.equals(source.getTaskId(), report.getTaskId())
                    && Objects.equals(source.getAttemptId(), report.getAttemptId()) && "UMC".equals(source.getSourceCode())
                    && "umc-doc-v1".equals(source.getTemplateVersion()) && "AVAILABLE".equals(source.getStatusCode()), "UMC设备报告不可用或归属不一致");
            if (snapshot.resultIds().isEmpty()) {
                var content = reports.selectContent(new InspectionReportContentQuery(report.getTenantId(), report.getTaskId(), id));
                require(content != null, "UMC设备报告结果缺失");
                var detail = InspectionUmcReportResults.load(content);
                require(detail.devlist() != null && detail.devlist().size() == 1 && detail.devlist().getFirst() != null
                        && detail.devlist().getFirst().rulelist() != null && detail.devlist().getFirst().rulelist().isEmpty(),
                        "UMC零结果报告与来源规则不一致");
            }
        }
        var result = files.selectForTask(new InspectionTaskRows(report.getTenantId(), report.getTaskId())).stream()
                .filter(file -> ids.contains(file.getReportId()) && "DOC".equals(file.getFormatCode())).toList();
        require(result.size() == ids.size() && result.stream().map(InspectionReportFileDO::getReportId).distinct().count() == ids.size(),
                "UMC设备报告DOC缺失或重复");
        return result;
    }
    public boolean available(Query query) {
        if (!"AVAILABLE".equals(query.report().getStatusCode())) return false;
        for (var file : files(query)) {
            var fact = evidence.lockAndRevalidate(new FileEvidenceApi.Query(file.getTenantId(), file.getArtifactId(), file.getFileVersionNo(),
                    "SRV", "INSPECTION_TASK", file.getTaskId().toString(), InspectionUmcTransactions.PURPOSE, file.getReferenceKey(), file.getSha256()));
            if (fact == null || !fact.valid()) return false;
        }
        return true;
    }
}
