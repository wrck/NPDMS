package cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessview.BusinessViewRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
/** PM-03: tenant isolation, identity locks and per-row lifecycle CAS. */
@Mapper
public interface BusinessViewRevisionMapper extends BaseMapperX<BusinessViewRevisionDO> {
    default BusinessViewRevisionDO selectByRow(BusinessViewRowQuery query) {
        return selectOne(new LambdaQueryWrapperX<BusinessViewRevisionDO>()
                .eq(BusinessViewRevisionDO::getTenantId, query.tenantId())
                .eq(BusinessViewRevisionDO::getId, query.revisionId()));
    }
    default PageResult<BusinessViewRevisionDO> selectPage(BusinessViewPageQuery query) {
        if (query.getOwnerContexts().isEmpty()) return new PageResult<>(List.of(), 0L);
        long total = selectPageCount(query);
        if (total == 0) return new PageResult<>(List.of(), 0L);
        return new PageResult<>(selectPageRows(query), total);
    }
    List<BusinessViewRevisionDO> selectPageRows(@Param("query") BusinessViewPageQuery query);
    long selectPageCount(@Param("query") BusinessViewPageQuery query);

    List<BusinessViewRevisionDO> selectIdentityForUpdate(@Param("query") BusinessViewIdentityQuery query);
    int updateDraftIfMatch(@Param("row") BusinessViewRevisionDO row);
    int publishIfMatch(@Param("row") BusinessViewRevisionDO row);
    int disableIfMatch(@Param("row") BusinessViewRevisionDO row);
}
