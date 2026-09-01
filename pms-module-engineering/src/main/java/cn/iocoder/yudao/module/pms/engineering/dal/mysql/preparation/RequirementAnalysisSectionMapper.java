package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionListQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionIdentityQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionPatchUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
@Deprecated // F-SOL-003已改用DynamicFormBusinessInstanceApi；不得增加新调用方。
public interface RequirementAnalysisSectionMapper {
    int insert(@Param("row") RequirementAnalysisSectionDO row);
    RequirementAnalysisSectionDO selectByIdentity(@Param("query") RequirementAnalysisSectionIdentityQuery query);
    RequirementAnalysisSectionDO selectById(@Param("query") RequirementAnalysisSectionRowQuery query);
    RequirementAnalysisSectionDO selectForUpdate(@Param("query") RequirementAnalysisSectionRowQuery query);
    List<RequirementAnalysisSectionDO> selectList(@Param("query") RequirementAnalysisSectionListQuery query);
    List<RequirementAnalysisSectionDO> selectListForUpdate(@Param("query") RequirementAnalysisSectionListQuery query);
    int patchIfMatch(@Param("update") RequirementAnalysisSectionPatchUpdate update);
}
