package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalReassignmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverApprovalReassignmentMapper extends BaseMapperX<CutoverApprovalReassignmentDO> {
    Integer selectMaxReassignmentNo(@Param("query") ApprovalNodeLockQuery query);
}
