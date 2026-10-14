package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationByCodeQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverFrozenConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectivePublishedConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationPageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectiveConfigurationListQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationStorageQuery;

import java.util.List;

@Mapper
public interface CutoverConfigurationRevisionMapper extends BaseMapperX<CutoverConfigurationRevisionDO> {

    CutoverConfigurationRevisionDO selectForUpdate(@Param("query") CutoverConfigurationStorageQuery query);

    int updateDraftIfMatch(@Param("row") CutoverConfigurationRevisionDO row);

    default PageResult<CutoverConfigurationRevisionDO> selectPage(CutoverConfigurationPageQuery query) {
        return selectPage(query, new LambdaQueryWrapperX<CutoverConfigurationRevisionDO>()
                .likeIfPresent(CutoverConfigurationRevisionDO::getConfigurationCode, query.getConfigurationCode())
                .likeIfPresent(CutoverConfigurationRevisionDO::getConfigurationName, query.getConfigurationName())
                .eqIfPresent(CutoverConfigurationRevisionDO::getStatusCode, query.getStatusCode())
                .orderByDesc(CutoverConfigurationRevisionDO::getUpdateTime)
                .orderByDesc(CutoverConfigurationRevisionDO::getId));
    }

    CutoverConfigurationRevisionDO selectLatestByCode(@Param("query") CutoverConfigurationByCodeQuery query);

    CutoverConfigurationRevisionDO selectFrozen(@Param("query") CutoverFrozenConfigurationQuery query);

    CutoverConfigurationRevisionDO selectEffectivePublished(@Param("query") CutoverEffectivePublishedConfigurationQuery query);

    List<CutoverConfigurationRevisionDO> selectEffectivePublishedList(
            @Param("query") CutoverEffectiveConfigurationListQuery query);
}
