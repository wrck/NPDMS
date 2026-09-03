package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionRemediationFactDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionRemediationIdentityQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SatisfactionRemediationFactMapper extends BaseMapperX<SatisfactionRemediationFactDO> {
    default SatisfactionRemediationFactDO selectByIdentity(SatisfactionRemediationIdentityQuery query) {
        return selectOne(new LambdaQueryWrapperX<SatisfactionRemediationFactDO>()
                .eq(SatisfactionRemediationFactDO::getTenantId, query.tenantId())
                .eq(SatisfactionRemediationFactDO::getPriorResultId, query.priorResultId())
                .eq(SatisfactionRemediationFactDO::getRemediationRequestId, query.remediationRequestId()));
    }
}
