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
        var wrapper = new LambdaQueryWrapperX<BriefingEntityDO>()
                .eq(BriefingEntityDO::getTenantId, query.getTenantId())
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

    default BriefingEntityDO selectByCode(String code) {
        return selectOne(BriefingEntityDO::getCode, code);
    }

    BriefingEntityDO selectForUpdate(@Param("query") BriefingEntityLockQuery query);
}
