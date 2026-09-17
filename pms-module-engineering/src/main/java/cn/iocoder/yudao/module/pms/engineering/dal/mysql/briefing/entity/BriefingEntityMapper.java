package cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Objects;

/** 独立交底持久化；业务运行路径只读写 sol_engineering_briefing。 */
@Mapper
public interface BriefingEntityMapper extends BaseMapperX<BriefingEntityDO> {
    default PageResult<BriefingEntityDO> selectPage(BriefingEntityPageQuery query) {
        Objects.requireNonNull(query.getTenantId(), "tenantId");
        Objects.requireNonNull(query.getVisibleProjectIds(), "visibleProjectIds");
        if (query.getVisibleProjectIds().isEmpty())
            return new PageResult<>(java.util.List.of(), 0L);
        var wrapper = new LambdaQueryWrapperX<BriefingEntityDO>()
                .eq(BriefingEntityDO::getTenantId, query.getTenantId())
                .eq(BriefingEntityDO::getDeleted, false)
                .in(BriefingEntityDO::getProjectId, query.getVisibleProjectIds())
                .eqIfPresent(BriefingEntityDO::getProjectId, query.getProjectId())
                .likeIfPresent(BriefingEntityDO::getCode, query.getCode())
                .likeIfPresent(BriefingEntityDO::getName, query.getName())
                .eqIfPresent(BriefingEntityDO::getBriefingType, query.getBriefingType())
                .eqIfPresent(BriefingEntityDO::getStatus, query.getStatus())
                .eqIfPresent(BriefingEntityDO::getCreatorUserId, query.getCreatorUserId())
                .eqIfPresent(BriefingEntityDO::getApproverUserId, query.getApproverUserId());
        if (query.getCreateTime() != null) {
            wrapper.geIfPresent(BriefingEntityDO::getCreateTime, query.getCreateTime()[0]);
            wrapper.ltIfPresent(BriefingEntityDO::getCreateTime, query.getCreateTime()[1]);
        }
        return selectPage(query, wrapper.orderByDesc(BriefingEntityDO::getId));
    }

    default BriefingEntityDO selectByTenantAndId(Long tenantId, Long id) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(id, "id");
        return selectOne(new LambdaQueryWrapperX<BriefingEntityDO>()
                .eq(BriefingEntityDO::getTenantId, tenantId)
                .eq(BriefingEntityDO::getId, id).eq(BriefingEntityDO::getDeleted, false));
    }

    default BriefingEntityDO selectByTenantAndCode(Long tenantId, String code) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        return selectOne(new LambdaQueryWrapperX<BriefingEntityDO>()
                .eq(BriefingEntityDO::getTenantId, tenantId)
                .eq(BriefingEntityDO::getCode, code).eq(BriefingEntityDO::getDeleted, false));
    }

    BriefingEntityDO selectForUpdate(@Param("query") BriefingEntityLockQuery query);
}
