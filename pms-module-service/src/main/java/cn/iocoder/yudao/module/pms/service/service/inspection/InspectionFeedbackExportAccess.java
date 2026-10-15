package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class InspectionFeedbackExportAccess {
    private final InspectionFeedbackExportMapper exports;
    private final InspectionFeedbackExportItemMapper items;
    private final InspectionAccessService access;
    public record Query(InspectionRequestContext context, Long exportId, boolean lock) { }
    public InspectionFeedbackExportDO authorized(Query query) {
        access.permission(query.context(), "pms:inspection-feedback:query");
        access.permission(query.context(), "pms:inspection-feedback:export");
        access.permission(query.context(), "pms:inspection-feedback:analyze");
        var row = exports.selectById(query.exportId());
        if (row == null || !Objects.equals(row.getTenantId(), query.context().tenantId()) || !Objects.equals(row.getExportedBy(), query.context().actorId()))
            throw new InspectionException(InspectionException.Reason.NOT_FOUND, "导出记录不存在或不可见");
        if (Boolean.TRUE.equals(row.getIncludeDetails())) access.permission(query.context(), "pms:inspection-feedback:details");
        var projects = items.selectForExport(new InspectionFeedbackExportQuery(row.getTenantId(), row.getId())).stream()
                .map(InspectionFeedbackExportItemDO::getProjectId).collect(Collectors.toCollection(TreeSet::new));
        if (!access.visibleProjects(query.context()).containsAll(projects))
            throw new InspectionException(InspectionException.Reason.FORBIDDEN, "导出来源的项目授权已变化");
        if (query.lock()) {
            for (var project : projects) access.project(new InspectionAccessService.ProjectAccess(query.context(), project, true));
            row = exports.selectForUpdate(new InspectionFeedbackExportQuery(row.getTenantId(), row.getId()));
        }
        return row;
    }
}
