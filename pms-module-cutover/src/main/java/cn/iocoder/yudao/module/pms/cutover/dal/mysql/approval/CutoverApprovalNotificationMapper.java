package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationClaimQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationClaimQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationDeliveryUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverApprovalNotificationMapper extends BaseMapperX<CutoverApprovalNotificationDO> {
    List<CutoverApprovalNotificationDO> selectDueForUpdateSkipLocked(
            @Param("query") ApprovalNotificationClaimQuery query);
    int updateDeliveryIfMatch(@Param("query") ApprovalNotificationDeliveryUpdate query);
    List<CutoverApprovalNotificationDO> selectExternalDueForUpdateSkipLocked(
            @Param("query") ExternalApprovalNotificationClaimQuery query);
    int updateExternalDeliveryIfMatch(@Param("query") ExternalApprovalNotificationDeliveryUpdate query);
}
