package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationSnapshotPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreparationReadinessSnapshotMapper {
    int insert(@Param("row") PreparationReadinessSnapshotDO row);
    List<PreparationReadinessSnapshotDO> selectPage(@Param("query") PreparationSnapshotPageQuery query);
}
