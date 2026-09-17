package cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 唯一允许读取旧交底表的专用承接 Mapper；没有旧表写方法。 */
@Mapper
public interface BriefingEntityImportMapper {
    BriefingEntityImportSource selectSourceForUpdate(@Param("query") BriefingEntityLockQuery query);
    BriefingEntityDO selectTargetForUpdate(@Param("query") BriefingEntityLockQuery query);
    int insertImported(@Param("row") BriefingEntityImportSource row);
}
