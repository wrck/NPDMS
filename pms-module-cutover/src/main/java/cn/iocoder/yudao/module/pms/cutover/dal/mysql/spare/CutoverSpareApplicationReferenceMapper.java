package cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare.CutoverSpareApplicationReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.query.SpareApplicationQueries;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverSpareApplicationReferenceMapper extends BaseMapperX<CutoverSpareApplicationReferenceDO> {
    CutoverSpareApplicationReferenceDO selectByIdForUpdate(@Param("query") SpareApplicationQueries.ById query);
    CutoverSpareApplicationReferenceDO selectByPlatformRequestForUpdate(@Param("query") SpareApplicationQueries.ByPlatformRequest query);
    CutoverSpareApplicationReferenceDO selectByExternalApplicationForUpdate(@Param("query") SpareApplicationQueries.ByExternalApplication query);
    List<CutoverSpareApplicationReferenceDO> selectByTask(@Param("query") SpareApplicationQueries.ByTask query);
    int storeInitiateResultIfMatch(@Param("query") SpareApplicationQueries.StoreInitiateResult query);
    int storeFailureIfMatch(@Param("query") SpareApplicationQueries.StoreFailure query);
    int bindExternalReferenceIfMatch(@Param("query") SpareApplicationQueries.BindExternalReference query);
    int moveCurrentStatusIfMatch(@Param("query") SpareApplicationQueries.MoveCurrentStatus query);
}
