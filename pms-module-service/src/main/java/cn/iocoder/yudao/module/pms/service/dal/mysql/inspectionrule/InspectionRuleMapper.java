package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleDetectionIdQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleIdentityLockQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleNameQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InspectionRuleMapper extends BaseMapperX<InspectionRuleDO> {

    default InspectionRuleDO selectByTenantAndDetectionId(InspectionRuleDetectionIdQuery query) {
        return selectOne(new LambdaQueryWrapperX<InspectionRuleDO>()
                .eq(InspectionRuleDO::getTenantId, query.tenantId())
                .eq(InspectionRuleDO::getDetectionId, query.detectionId()));
    }

    default InspectionRuleDO selectByTenantAndRuleName(InspectionRuleNameQuery query) {
        return selectOne(new LambdaQueryWrapperX<InspectionRuleDO>()
                .eq(InspectionRuleDO::getTenantId, query.tenantId())
                .eq(InspectionRuleDO::getRuleName, query.ruleName()));
    }

    InspectionRuleDO selectByIdForUpdate(@Param("query") InspectionRuleIdentityLockQuery query);
}
