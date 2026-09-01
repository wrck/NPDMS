package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverApprovalInstanceMapper extends BaseMapperX<CutoverApprovalInstanceDO> {
    CutoverApprovalInstanceDO selectByTaskAndPlanForUpdate(@Param("query") ApprovalInstanceLockQuery query);
    CutoverApprovalInstanceDO selectByIdForUpdate(@Param("query") ApprovalInstanceLockQuery query);
    CutoverApprovalInstanceDO selectCurrentByTask(@Param("query") ApprovalTaskQuery query);
}
