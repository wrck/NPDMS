package cn.iocoder.yudao.module.pms.platform.dal.mysql.result.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.result.PlatformResultSubscriptionDO;
import lombok.Getter;
import lombok.Setter;

/** 结果订阅分页查询：场景化条件，空条件不扩大租户边界（租户由框架注入）。 */
@Getter
@Setter
public class ResultSubscriptionPageQuery extends PageParam {

    private String subscriptionCode;
    private String resultType;
    private String ownerModule;
    private String entityType;
    private String status;

    public LambdaQueryWrapperX<PlatformResultSubscriptionDO> toWrapper() {
        return new LambdaQueryWrapperX<PlatformResultSubscriptionDO>()
                .eqIfPresent(PlatformResultSubscriptionDO::getSubscriptionCode, subscriptionCode)
                .eqIfPresent(PlatformResultSubscriptionDO::getResultType, resultType)
                .eqIfPresent(PlatformResultSubscriptionDO::getOwnerModule, ownerModule)
                .eqIfPresent(PlatformResultSubscriptionDO::getEntityType, entityType)
                .eqIfPresent(PlatformResultSubscriptionDO::getStatus, status)
                .orderByDesc(PlatformResultSubscriptionDO::getId);
    }
}
