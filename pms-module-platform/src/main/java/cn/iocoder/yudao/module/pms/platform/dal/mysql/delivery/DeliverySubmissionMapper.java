package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySubmissionCurrentLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialSubmissionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DeliverySubmissionMapper extends BaseMapperX<DeliverySubmissionDO> {

    /** 幂等键按（要求实例 + request_key）唯一：手工 Idempotency-Key 只在要求内唯一。 */
    default Optional<DeliverySubmissionDO> selectByRequestKey(Long requirementId, String requestKey) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<DeliverySubmissionDO>()
                .eq(DeliverySubmissionDO::getRequirementId, requirementId)
                .eq(DeliverySubmissionDO::getRequestKey, requestKey)));
    }

    default List<DeliverySubmissionDO> selectByRequirement(Long requirementId) {
        return selectList(new LambdaQueryWrapperX<DeliverySubmissionDO>()
                .eq(DeliverySubmissionDO::getRequirementId, requirementId)
                .orderByDesc(DeliverySubmissionDO::getId));
    }

    default List<DeliverySubmissionDO> selectByRequirementAndStatus(Long requirementId, String status) {
        return selectList(new LambdaQueryWrapperX<DeliverySubmissionDO>()
                .eq(DeliverySubmissionDO::getRequirementId, requirementId)
                .eq(DeliverySubmissionDO::getStatus, status)
                .orderByDesc(DeliverySubmissionDO::getId));
    }

    List<DeliverySubmissionDO> selectListUsingMaterial(@Param("query") DeliveryMaterialSubmissionQuery query);

    List<DeliverySubmissionDO> selectPendingArchiveSubmissions(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveQueueQuery query);
    DeliverySubmissionDO selectArchiveSubmissionForUpdate(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveSubmissionQuery query);
    int requireArchiveSubmission(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveSubmissionQuery query);
    int updateArchiveSubmissionState(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveSubmissionStateQuery query);

    DeliverySubmissionDO selectCurrentForUpdate(@Param("query") DeliverySubmissionCurrentLockQuery query);
}
