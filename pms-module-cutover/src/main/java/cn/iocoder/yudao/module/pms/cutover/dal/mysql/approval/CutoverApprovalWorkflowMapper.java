package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalWorkflowDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverApprovalWorkflowMapper extends BaseMapperX<CutoverApprovalWorkflowDO> {
    java.util.List<cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection.CutoverApprovalProjectSummary>
            selectProjectSummaries(@org.apache.ibatis.annotations.Param("query")
                    cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalProjectSummaryQuery query);
}
