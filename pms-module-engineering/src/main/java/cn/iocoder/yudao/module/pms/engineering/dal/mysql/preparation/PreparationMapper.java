package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreparationMapper {
    int insert(@Param("row") PreparationDO row);
    PreparationDO selectCurrent(@Param("query") PreparationCurrentQuery query);
    PreparationDO selectCurrentForUpdate(@Param("query") PreparationCurrentQuery query);
    PreparationDO selectById(@Param("query") PreparationRowQuery query);
    PreparationDO selectForUpdate(@Param("query") PreparationRowQuery query);
    List<PreparationDO> selectPage(@Param("query") PreparationPageQuery query);
    int updateLifecycleIfMatch(@Param("update") PreparationLifecycleUpdate update);
    int clearCurrentMarkerIfMatch(@Param("update") PreparationCurrentClearUpdate update);
    int invalidateReadinessIfMatch(@Param("update") PreparationInputInvalidationUpdate update);
    int updateReadinessIfMatch(@Param("update") PreparationReadinessUpdate update);
}
