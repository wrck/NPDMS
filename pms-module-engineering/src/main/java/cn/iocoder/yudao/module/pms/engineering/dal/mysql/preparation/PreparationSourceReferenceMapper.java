package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSourceReferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationChildrenQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationSourceSyncUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreparationSourceReferenceMapper {
    int insert(@Param("row") PreparationSourceReferenceDO row);
    List<PreparationSourceReferenceDO> selectListForUpdate(@Param("query") PreparationChildrenQuery query);
    int updateSyncIfMatch(@Param("update") PreparationSourceSyncUpdate update);
}
