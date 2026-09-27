package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DeliverySubmissionMapper extends BaseMapperX<DeliverySubmissionDO> {

    default Optional<DeliverySubmissionDO> selectByRequestKey(String requestKey) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<DeliverySubmissionDO>()
                .eq(DeliverySubmissionDO::getRequestKey, requestKey)));
    }

    default List<DeliverySubmissionDO> selectByRequirement(Long requirementId) {
        return selectList(new LambdaQueryWrapperX<DeliverySubmissionDO>()
                .eq(DeliverySubmissionDO::getRequirementId, requirementId)
                .orderByDesc(DeliverySubmissionDO::getId));
    }
}
