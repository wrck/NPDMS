package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationByCodeQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverFrozenConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverEffectivePublishedConfigurationQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationPageQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverConfigurationRevisionMapper extends BaseMapperX<CutoverConfigurationRevisionDO> {

    default PageResult<CutoverConfigurationRevisionDO> selectPage(CutoverConfigurationPageQuery query) {
        return selectPage(query, new LambdaQueryWrapperX<CutoverConfigurationRevisionDO>()
                .likeIfPresent(CutoverConfigurationRevisionDO::getConfigurationCode, query.getConfigurationCode())
                .likeIfPresent(CutoverConfigurationRevisionDO::getConfigurationName, query.getConfigurationName())
                .eqIfPresent(CutoverConfigurationRevisionDO::getStatusCode, query.getStatusCode())
                .orderByDesc(CutoverConfigurationRevisionDO::getUpdateTime)
                .orderByDesc(CutoverConfigurationRevisionDO::getId));
    }

    CutoverConfigurationRevisionDO selectLatestByCode(CutoverConfigurationByCodeQuery query);

    CutoverConfigurationRevisionDO selectFrozen(CutoverFrozenConfigurationQuery query);

    CutoverConfigurationRevisionDO selectEffectivePublished(CutoverEffectivePublishedConfigurationQuery query);
}
