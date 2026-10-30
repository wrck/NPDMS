package cn.iocoder.yudao.module.pms.platform.dal.mysql.evidence;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence.PlatformExecutionEvidenceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface PlatformExecutionEvidenceMapper extends BaseMapperX<PlatformExecutionEvidenceDO> {

    default Optional<PlatformExecutionEvidenceDO> selectBySubscriptionRound(Long subscriptionId, Long roundNo) {
        return selectList(new LambdaQueryWrapperX<PlatformExecutionEvidenceDO>()
                .eq(PlatformExecutionEvidenceDO::getSubscriptionId, subscriptionId)
                .eq(PlatformExecutionEvidenceDO::getRoundNo, roundNo)
                .orderByDesc(PlatformExecutionEvidenceDO::getId))
                .stream().findFirst();
    }
}
