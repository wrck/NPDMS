package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeStatusUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalReassignmentPageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTodoPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverApprovalNodeMapper extends BaseMapperX<CutoverApprovalNodeDO> {
    CutoverApprovalNodeDO selectByInstanceAndNodeForUpdate(@Param("query") ApprovalNodeLockQuery query);
    List<CutoverApprovalNodeDO> selectTodoPage(@Param("query") ApprovalTodoPageQuery query);
    List<CutoverApprovalNodeDO> selectReassignmentPage(@Param("query") ApprovalReassignmentPageQuery query);
    int updateStatusIfMatch(@Param("query") ApprovalNodeStatusUpdate query);
}
