package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDraftUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleIdentityLockQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRulePublicationLockQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleRevisionKeyQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleRevisionPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InspectionRuleRevisionMapper extends BaseMapperX<InspectionRuleRevisionDO> {

    default InspectionRuleRevisionDO selectByRuleIdAndRevisionNo(InspectionRuleRevisionKeyQuery query) {
        return selectOne(new LambdaQueryWrapperX<InspectionRuleRevisionDO>()
                .eq(InspectionRuleRevisionDO::getTenantId, query.tenantId())
                .eq(InspectionRuleRevisionDO::getRuleId, query.ruleId())
                .eq(InspectionRuleRevisionDO::getRevisionNo, query.revisionNo()));
    }

    default PageResult<InspectionRuleRevisionDO> selectPage(InspectionRuleRevisionPageQuery query) {
        long total = selectPageCount(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(selectPageList(query), total);
    }

    List<InspectionRuleRevisionDO> selectPageList(@Param("query") InspectionRuleRevisionPageQuery query);

    long selectPageCount(@Param("query") InspectionRuleRevisionPageQuery query);

    int updateDraftIfMatch(@Param("command") InspectionRuleDraftUpdate command);

    Integer selectMaxRevisionNoByRule(@Param("query") InspectionRuleIdentityLockQuery query);

    InspectionRulePublicationLockProjection selectPublicationLockForUpdate(
            @Param("query") InspectionRulePublicationLockQuery query);
}
