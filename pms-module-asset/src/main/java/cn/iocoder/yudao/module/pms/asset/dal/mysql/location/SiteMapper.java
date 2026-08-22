package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.SitePageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SiteMapper extends BaseMapperX<SiteDO> {

    default PageResult<SiteDO> selectPage(SitePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SiteDO>()
                .eqIfPresent(SiteDO::getCode, reqVO.getCode())
                .likeIfPresent(SiteDO::getName, reqVO.getName())
                .eqIfPresent(SiteDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(SiteDO::getStatus, reqVO.getStatus())
                .orderByDesc(SiteDO::getId));
    }

    default SiteDO selectByCode(String code) {
        return selectOne(SiteDO::getCode, code);
    }

    default List<SiteDO> selectListByIds(Collection<Long> ids) {
        return selectByIds(ids);
    }

    default int updateByIdAndVersion(SiteDO update, Integer expectedVersion) {
        return update(update, new LambdaUpdateWrapper<SiteDO>()
                .eq(SiteDO::getId, update.getId())
                .eq(SiteDO::getVersion, expectedVersion));
    }
}
