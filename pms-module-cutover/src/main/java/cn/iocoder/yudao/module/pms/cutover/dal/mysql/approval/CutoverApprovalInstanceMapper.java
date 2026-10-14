package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceReassignmentUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceStateUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface CutoverApprovalInstanceMapper extends BaseMapperX<CutoverApprovalInstanceDO> {
    CutoverApprovalInstanceDO selectByTaskAndPlanForUpdate(@Param("query") ApprovalInstanceLockQuery query);
    CutoverApprovalInstanceDO selectByIdForUpdate(@Param("query") ApprovalInstanceLockQuery query);
    CutoverApprovalInstanceDO selectCurrentByTask(@Param("query") ApprovalTaskQuery query);
    default List<CutoverApprovalInstanceDO> history(ApprovalTaskQuery query) {
        return selectList(new LambdaQueryWrapperX<CutoverApprovalInstanceDO>()
                .eq(CutoverApprovalInstanceDO::getTenantId, query.tenantId())
                .eq(CutoverApprovalInstanceDO::getTaskId, query.taskId())
                .orderByDesc(CutoverApprovalInstanceDO::getId));
    }
    int updateAfterReassignmentIfMatch(@Param("query") ApprovalInstanceReassignmentUpdate query);
    int updateStateIfMatch(@Param("query") ApprovalInstanceStateUpdate query);
}
