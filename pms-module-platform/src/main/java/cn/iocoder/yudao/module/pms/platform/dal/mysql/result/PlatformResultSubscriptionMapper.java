package cn.iocoder.yudao.module.pms.platform.dal.mysql.result;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.result.PlatformResultSubscriptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlatformResultSubscriptionMapper extends BaseMapperX<PlatformResultSubscriptionDO> {

    default Optional<PlatformResultSubscriptionDO> selectByCode(String subscriptionCode) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<PlatformResultSubscriptionDO>()
                .eq(PlatformResultSubscriptionDO::getSubscriptionCode, subscriptionCode)));
    }

    /** 未满足（仍在收集）的订阅按结果类型枚举；空集合返回空结果，不扩大范围。 */
    default List<PlatformResultSubscriptionDO> selectCollecting(String resultType, String ownerModule,
                                                                String entityType) {
        return selectList(new LambdaQueryWrapperX<PlatformResultSubscriptionDO>()
                .eq(PlatformResultSubscriptionDO::getResultType, resultType)
                .eq(PlatformResultSubscriptionDO::getOwnerModule, ownerModule)
                .eq(PlatformResultSubscriptionDO::getEntityType, entityType)
                .ne(PlatformResultSubscriptionDO::getStatus, "SATISFIED")
                .orderByAsc(PlatformResultSubscriptionDO::getId));
    }
}
