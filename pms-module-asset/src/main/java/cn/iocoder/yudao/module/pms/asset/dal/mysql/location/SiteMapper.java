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

    default List<SiteDO> selectEnabledSites(cn.iocoder.yudao.module.pms.asset.api.location.dto.EnabledSitesQuery query) {
        if (query == null || query.tenantId() == null || query.siteIds() == null || query.siteIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<SiteDO>().eq(SiteDO::getTenantId, query.tenantId())
                .in(SiteDO::getId, query.siteIds())
                .eq(SiteDO::getStatus, cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(SiteDO::getId));
    }

    default List<SiteDO> selectCurrentCustomerSites(
            cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.CurrentCustomerSiteListQuery query) {
        if (query == null || query.tenantId() == null || query.customerId() == null) return List.of();
        return selectList(new LambdaQueryWrapperX<SiteDO>()
                .eq(SiteDO::getTenantId, query.tenantId())
                .eq(SiteDO::getCustomerId, query.customerId())
                .eq(SiteDO::getStatus, 0)
                .orderByAsc(SiteDO::getId));
    }

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

    default int updateByIdAndVersion(SiteDO update, Long expectedVersion) {
        return update(update, new LambdaUpdateWrapper<SiteDO>()
                .eq(SiteDO::getId, update.getId())
                .eq(SiteDO::getVersion, expectedVersion));
    }
}
