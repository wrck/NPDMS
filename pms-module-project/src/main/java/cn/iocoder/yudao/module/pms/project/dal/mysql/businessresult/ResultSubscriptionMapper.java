package cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResultSubscriptionMapper {
    ResultSubscriptionDO selectIdentityForUpdate(@Param("query") Identity query);
    ResultSubscriptionDO selectByIdForUpdate(@Param("query") IdQuery query);
    ResultSubscriptionDO selectOrigin(@Param("query") Origin query);
    int insert(@Param("row") ResultSubscriptionDO row);
    java.util.List<ResultSubscriptionDO> selectChannelPage(@Param("query") ChannelPage query);
    int retire(@Param("query") Retirement query);
    int checkpoint(@Param("query") Checkpoint query);

    record ChannelPage(Long tenantId, Long projectId, Long channelId, long afterId, int limit) { }
    record Retirement(Long tenantId, Long projectId, Long id, Integer expectedVersion) { }
    record Identity(Long tenantId, Long projectId, Long planVersionId, Long executionId, String subscriptionKey) { }
    record IdQuery(Long tenantId, Long projectId, Long id) { }
    record Origin(Long tenantId, Long projectId, Long executionId, String subscriptionKey) { }
    record Checkpoint(Long tenantId, Long projectId, Long id, Integer expectedVersion,
                      String phase, String inventoryCursor, long processedSequence, Long throughSequence) { }
}
