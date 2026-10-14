package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalReviewItemDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalNodesQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalPlanQuery;

@Mapper
public interface CutoverRuntimeApprovalReviewItemMapper extends BaseMapperX<CutoverApprovalReviewItemDO> {

    default java.util.List<CutoverApprovalReviewItemDO> byInstance(CutoverApprovalNodesQuery query) {
        return selectList(new LambdaQueryWrapperX<CutoverApprovalReviewItemDO>()
                .eq(CutoverApprovalReviewItemDO::getTenantId, query.tenantId())
                .eq(CutoverApprovalReviewItemDO::getApprovalInstanceId, query.approvalInstanceId())
                .orderByAsc(CutoverApprovalReviewItemDO::getApprovalNodeId, CutoverApprovalReviewItemDO::getId));
    }
}
