package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanGeneratedFileDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanGeneratedFileMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanGeneratedFileQuery;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** CUT 自有生成引用；平台仍拥有文件、可用性与访问授权。 */
@Component
@RequiredArgsConstructor
public class CutoverPlanGeneratedFiles {
    private final CutoverPlanGeneratedFileMapper mapper;

    public DownloadCutoverPlanDraftResult find(CutoverPlanGeneratedFileQuery query) {
        var row = mapper.selectLatest(query);
        if (row == null) return null;
        var fact = CutoverPlanFilePort.FileFact.builder().artifactId(row.getArtifactId())
                .versionNo(row.getFileVersionNo()).referenceKey(row.getReferenceKey())
                .fileFactVersion(new CutoverPlanFilePort.FileFactVersion(row.getArtifactFactVersion(),
                        row.getReferenceFactVersion(), row.getAvailabilityFactVersion()))
                .scopeVersion(row.getScopeVersion()).sha256(row.getSha256()).build();
        return new DownloadCutoverPlanDraftResult(row.getPlanRevisionId(), row.getPlanVersion(), fact, row.getGeneratedAt());
    }

    public record Write(Long tenantId, Long actorId, Long taskId, DownloadCutoverPlanDraftResult result) {}

    public void record(Write value) {
        var result = value.result(); var fact = result.fileArtifactFact();
        var row = new CutoverPlanGeneratedFileDO();
        row.setId(IdUtil.getSnowflakeNextId()); row.setTenantId(value.tenantId()); row.setCreator(value.actorId());
        row.setTaskId(value.taskId()); row.setPlanRevisionId(result.planRevisionId()); row.setPlanVersion(result.planVersion());
        row.setArtifactId(fact.artifactId()); row.setFileVersionNo(fact.versionNo()); row.setReferenceKey(fact.referenceKey());
        row.setArtifactFactVersion(fact.fileFactVersion().artifactVersion());
        row.setReferenceFactVersion(fact.fileFactVersion().referenceVersion());
        row.setAvailabilityFactVersion(fact.fileFactVersion().availabilityVersion());
        row.setScopeVersion(fact.scopeVersion()); row.setSha256(fact.sha256()); row.setGeneratedAt(result.downloadedAt());
        if (mapper.insertIfCurrentDraft(row) != 1) {
            throw new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.VERSION_CONFLICT, "草稿版本已变化");
        }
    }
}
