package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.AcceptancePageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Objects;

@Mapper
@Deprecated(since = "F-ACC-001", forRemoval = false)
public interface AcceptanceMapper extends ProjectScopedCodeMapper<AcceptanceDO> {
    AcceptanceDO selectOwnerForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.AcceptanceOwnerLockQuery query);

    default PageResult<AcceptanceDO> selectPage(AcceptancePageQuery query) {
        Objects.requireNonNull(query.getVisibleProjectIds());
        if (query.getVisibleProjectIds().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }
        return selectPage(query, new LambdaQueryWrapperX<AcceptanceDO>()
                .eq(AcceptanceDO::getTenantId, query.getTenantId())
                .in(AcceptanceDO::getProjectId, query.getVisibleProjectIds())
                .eqIfPresent(AcceptanceDO::getProjectId, query.getProjectId())
                .likeIfPresent(AcceptanceDO::getCode, query.getCode())
                .likeIfPresent(AcceptanceDO::getName, query.getName())
                .eqIfPresent(AcceptanceDO::getAcceptanceType, query.getAcceptanceType())
                .eqIfPresent(AcceptanceDO::getStatus, query.getStatus())
                .betweenIfPresent(AcceptanceDO::getAcceptanceDate, query.getAcceptanceDate())
                .orderByDesc(AcceptanceDO::getId));
    }

}
