package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemWaiverDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreparationItemWaiverMapper {
    int insert(@Param("row") PreparationItemWaiverDO row);
    PreparationItemWaiverDO selectForUpdate(@Param("query") PreparationWaiverRowQuery query);
    List<PreparationItemWaiverDO> selectListForUpdate(@Param("query") PreparationChildrenQuery query);
    List<PreparationItemWaiverDO> selectPage(@Param("query") PreparationWaiverPageQuery query);
    int updateStatusIfMatch(@Param("update") PreparationWaiverStatusUpdate update);
}
