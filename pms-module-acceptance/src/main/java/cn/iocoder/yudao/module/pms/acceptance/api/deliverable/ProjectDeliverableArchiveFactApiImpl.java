package cn.iocoder.yudao.module.pms.acceptance.api.deliverable;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableArchiveFactApi.ProjectDeliverableArchiveFact;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目交付件归档事实只读实现：ACC 权威表的投影，不提供写入口。
 * 类别判定由 ACC Owner 完成（验收活动类型 / 满意度来源），消费方只做展示。
 */
@Service
@RequiredArgsConstructor
public class ProjectDeliverableArchiveFactApiImpl implements ProjectDeliverableArchiveFactApi {

    private final AccProjectDeliverableMapper deliverableMapper;
    private final ProjectDeliverableSourceVersionMapper sourceVersionMapper;
    private final AcceptanceReportVersionMapper reportVersionMapper;
    private final AcceptanceActivityMapper acceptanceActivityMapper;

    @Override
    public List<ProjectDeliverableArchiveFact> listByProject(Long tenantId, Long projectId) {
        Long currentTenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId == null || projectId == null || projectId <= 0 || !Objects.equals(tenantId, currentTenantId)) {
            throw new IllegalArgumentException("invalid project deliverable archive fact query");
        }
        List<AccProjectDeliverableDO> deliverables = deliverableMapper.selectListByProjectId(projectId);
        List<Long> deliverableIds = deliverables.stream().map(AccProjectDeliverableDO::getId).toList();
        Map<Long, ProjectDeliverableSourceVersionDO> currentSources = sourceVersionMapper
                .selectListCurrentByDeliverableIds(deliverableIds).stream()
                .collect(Collectors.toMap(ProjectDeliverableSourceVersionDO::getDeliverableId, Function.identity()));
        return deliverables.stream().map(row -> toFact(row, currentSources.get(row.getId()))).toList();
    }

    private ProjectDeliverableArchiveFact toFact(AccProjectDeliverableDO row,
                                                 ProjectDeliverableSourceVersionDO source) {
        if (source == null) {
            return new ProjectDeliverableArchiveFact(row.getId(), row.getDeliverableCode(), row.getName(),
                    row.getStageCode(), row.getRequired(), null, null, null, null, null, null);
        }
        return new ProjectDeliverableArchiveFact(row.getId(), row.getDeliverableCode(), row.getName(),
                row.getStageCode(), row.getRequired(), source.getSourceObjectType(),
                resolveCategory(source), source.getSourceObjectId(), source.getSourceVersion(),
                source.getRelationStatus(), source.getArchiveStatus());
    }

    private String resolveCategory(ProjectDeliverableSourceVersionDO source) {
        return switch (source.getSourceObjectType() == null ? "" : source.getSourceObjectType()) {
            case "SatisfactionResult" -> "SATISFACTION";
            case "AcceptanceReportVersion" -> resolveAcceptanceCategory(source.getSourceObjectId());
            default -> null;
        };
    }

    private String resolveAcceptanceCategory(Long reportVersionId) {
        AcceptanceReportVersionDO version = reportVersionId == null ? null
                : reportVersionMapper.selectById(reportVersionId);
        if (version == null || version.getAcceptanceId() == null) {
            return null;
        }
        AcceptanceActivityDO activity = acceptanceActivityMapper.selectById(version.getAcceptanceId());
        return activity == null ? null : activity.getAcceptanceType();
    }
}
