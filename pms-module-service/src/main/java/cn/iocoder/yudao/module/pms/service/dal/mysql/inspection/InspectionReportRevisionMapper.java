package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Comparator;

@Mapper
public interface InspectionReportRevisionMapper extends BaseMapperX<InspectionReportRevisionDO> {
    default List<InspectionReportRevisionDO> selectUmcExecutionResults(InspectionTaskRows query) {
        // MySQL filesort can retain the full JSON value and exhaust its sort buffer.
        // Keep large snapshots out of database sorting while preserving the returned order.
        return selectList(new LambdaQueryWrapperX<InspectionReportRevisionDO>()
                .eq(InspectionReportRevisionDO::getTenantId, query.tenantId())
                .eq(InspectionReportRevisionDO::getTaskId, query.taskId())
                .eq(InspectionReportRevisionDO::getSourceCode, "UMC")
                .eq(InspectionReportRevisionDO::getTemplateVersion, "umc-doc-v1")
                .select(InspectionReportRevisionDO::getId, InspectionReportRevisionDO::getTenantId,
                        InspectionReportRevisionDO::getTaskId, InspectionReportRevisionDO::getAttemptId,
                        InspectionReportRevisionDO::getSourceCode, InspectionReportRevisionDO::getStatusCode,
                        InspectionReportRevisionDO::getFailureMessage, InspectionReportRevisionDO::getSourceContextJson,
                        InspectionReportRevisionDO::getResultJson, InspectionReportRevisionDO::getResultSha256))
                .stream().sorted(Comparator.comparing(InspectionReportRevisionDO::getId)).toList();
    }
    default InspectionReportRevisionDO selectContent(InspectionReportContentQuery query) {
        return selectOne(new LambdaQueryWrapperX<InspectionReportRevisionDO>()
                .eq(InspectionReportRevisionDO::getTenantId, query.tenantId())
                .eq(InspectionReportRevisionDO::getTaskId, query.taskId())
                .eq(InspectionReportRevisionDO::getId, query.reportId())
                .select(InspectionReportRevisionDO::getId, InspectionReportRevisionDO::getTenantId,
                        InspectionReportRevisionDO::getVersion, InspectionReportRevisionDO::getTaskId,
                        InspectionReportRevisionDO::getAttemptId, InspectionReportRevisionDO::getRevisionNo,
                        InspectionReportRevisionDO::getSourceCode, InspectionReportRevisionDO::getStatusCode,
                        InspectionReportRevisionDO::getTemplateVersion, InspectionReportRevisionDO::getInputDigest,
                        InspectionReportRevisionDO::getExplanation, InspectionReportRevisionDO::getFailureMessage,
                        InspectionReportRevisionDO::getGeneratedBy, InspectionReportRevisionDO::getGeneratedAt,
                        InspectionReportRevisionDO::getSourceContextJson, InspectionReportRevisionDO::getResultJson,
                        InspectionReportRevisionDO::getResultSha256));
    }

    default List<InspectionReportRevisionDO> selectUmcSources(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionReportRevisionDO>()
                .eq(InspectionReportRevisionDO::getTenantId, query.tenantId())
                .eq(InspectionReportRevisionDO::getTaskId, query.taskId())
                .eq(InspectionReportRevisionDO::getSourceCode, "UMC")
                .eq(InspectionReportRevisionDO::getTemplateVersion, "umc-doc-v1")
                .select(InspectionReportRevisionDO::getId, InspectionReportRevisionDO::getSourceCode, InspectionReportRevisionDO::getSourceContextJson));
    }

    default List<InspectionReportRevisionDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionReportRevisionDO>()
                .eq(InspectionReportRevisionDO::getTenantId, query.tenantId())
                .eq(InspectionReportRevisionDO::getTaskId, query.taskId()).orderByAsc(InspectionReportRevisionDO::getId));
    }
}
