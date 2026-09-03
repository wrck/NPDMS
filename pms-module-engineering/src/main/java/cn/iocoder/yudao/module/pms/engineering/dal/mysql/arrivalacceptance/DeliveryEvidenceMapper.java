package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceAcceptedUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceArchivedUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceIdentityQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceFirstWatermarkUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceSourceQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenBatchQuery;

import java.util.List;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionAdvance;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidencePublishUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryClaimQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryStateUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliveryEvidenceMapper extends BaseMapperX<DeliveryEvidenceDO> {

    DeliveryEvidenceDO selectBySource(@Param("query") DeliveryEvidenceSourceQuery query);

    List<DeliveryEvidenceDO> selectByArrivalAcceptanceIdsInternal(
            @Param("query") ArrivalChildrenBatchQuery query);

    default List<DeliveryEvidenceDO> selectByArrivalAcceptanceIds(ArrivalChildrenBatchQuery query) {
        return query.arrivalAcceptanceIds().isEmpty() ? List.of() : selectByArrivalAcceptanceIdsInternal(query);
    }

    DeliveryEvidenceDO selectBySourceForUpdate(@Param("query") DeliveryEvidenceSourceQuery query);

    int advanceRevisionIfMatch(@Param("query") DeliveryEvidenceRevisionAdvance update);

    int markPublishedPendingAccIfMatch(@Param("query") DeliveryEvidencePublishUpdate update);

    DeliveryEvidenceDO selectByIdentityForUpdate(@Param("query") DeliveryEvidenceIdentityQuery query);

    int markAcceptedPendingArchiveIfMatch(@Param("query") DeliveryEvidenceAcceptedUpdate update);

    int markArchivedIfMatch(@Param("query") DeliveryEvidenceArchivedUpdate update);

    int registerFirstCallbackWatermarkIfMatch(
            @Param("query") DeliveryEvidenceFirstWatermarkUpdate update);

    DeliveryEvidenceDO selectNextDueForRetry(
            @Param("query") DeliveryEvidenceRetryClaimQuery query);

    int enterRetryStateIfMatch(@Param("query") DeliveryEvidenceRetryStateUpdate update);

    int advanceRetryIfMatch(@Param("query") DeliveryEvidenceRetryUpdate update);
}
