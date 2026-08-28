package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreparationItemMapper {
    int insert(@Param("row") PreparationItemDO row);
    PreparationItemDO selectByObjectId(@Param("query") PreparationItemObjectQuery query);
    PreparationItemDO selectCurrentByEvidenceObjectId(@Param("query") PreparationItemLineageQuery query);
    PreparationItemDO selectForUpdate(@Param("query") PreparationItemRowQuery query);
    List<PreparationItemDO> selectList(@Param("query") PreparationChildrenQuery query);
    List<PreparationItemDO> selectListForUpdate(@Param("query") PreparationChildrenQuery query);
    List<PreparationItemDO> selectPage(@Param("query") PreparationItemPageQuery query);
    int updateDraftIfMatch(@Param("update") PreparationItemDraftUpdate update);
    int updateReviewIfMatch(@Param("update") PreparationItemReviewUpdate update);
}
