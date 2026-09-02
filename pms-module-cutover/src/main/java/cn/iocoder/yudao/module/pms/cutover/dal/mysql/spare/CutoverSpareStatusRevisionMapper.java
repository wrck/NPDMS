package cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare.CutoverSpareStatusRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.query.SpareApplicationQueries;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverSpareStatusRevisionMapper extends BaseMapperX<CutoverSpareStatusRevisionDO> {
    CutoverSpareStatusRevisionDO selectCurrentForUpdate(@Param("query") SpareApplicationQueries.StatusByApplication query);
    CutoverSpareStatusRevisionDO selectByEvent(@Param("query") SpareApplicationQueries.StatusByEvent query);
    List<CutoverSpareStatusRevisionDO> selectByApplication(@Param("query") SpareApplicationQueries.StatusByApplication query);
    int clearCurrentMarkerIfMatch(@Param("query") SpareApplicationQueries.ClearCurrentStatus query);
}
