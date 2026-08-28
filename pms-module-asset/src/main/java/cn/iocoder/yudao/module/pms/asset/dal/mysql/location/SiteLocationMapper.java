package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SiteLocationMapper extends BaseMapperX<SiteLocationDO> {

    default SiteLocationDO selectBySiteIdAndCode(Long siteId, String code) {
        return selectOne(SiteLocationDO::getSiteId, siteId, SiteLocationDO::getCode, code);
    }

    default List<SiteLocationDO> selectListBySiteId(Long siteId) {
        return selectList(new LambdaQueryWrapperX<SiteLocationDO>()
                .eq(SiteLocationDO::getSiteId, siteId)
                .orderByAsc(SiteLocationDO::getTreeDepth, SiteLocationDO::getTreeSort, SiteLocationDO::getId));
    }

    default int updateByIdAndVersion(SiteLocationDO update, Integer expectedVersion) {
        return update(update, new LambdaUpdateWrapper<SiteLocationDO>()
                .eq(SiteLocationDO::getId, update.getId())
                .eq(SiteLocationDO::getVersion, expectedVersion));
    }
}
