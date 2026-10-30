package cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionCandidateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ResultSubscriptionCandidateMapper {
    ResultSubscriptionCandidateDO selectIdentityForUpdate(@Param("query") Identity query);
    List<ResultSubscriptionCandidateDO> selectPage(@Param("query") Page query);
    int insert(@Param("row") ResultSubscriptionCandidateDO row);
    int observe(@Param("query") ObservationUpdate query);
    int invalidate(@Param("query") Invalidation query);

    record Identity(Long tenantId, Long projectId, Long subscriptionId, String objectId, String resultId) { }
    record Page(Long tenantId, Long projectId, Long subscriptionId, long afterId, int limit) { }
    record ObservationUpdate(Long tenantId, Long projectId, Long subscriptionId, Long id,
                             long observedSequence, Long formationSequence, String observation) { }
    record Invalidation(Long tenantId, Long projectId, Long subscriptionId, String objectId,
                        String resultId, long observedSequence, String observation) { }
}
