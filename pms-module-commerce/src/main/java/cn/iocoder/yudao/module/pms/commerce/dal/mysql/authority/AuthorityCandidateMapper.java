package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.AuthorityCandidateDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AuthorityCandidateMapper extends BaseMapperX<AuthorityCandidateDO> {
    AuthorityCandidateDO selectByIdentityForUpdate(AuthorityCandidateIdentityQuery query);

    AuthorityCandidateDO selectByIdForUpdate(AuthorityCandidateIdQuery query);

    AuthorityCandidateDO selectCandidateById(AuthorityCandidateIdQuery query);

    AuthorityCandidateOwnerFact selectConfirmedOwnerForUpdate(AuthorityCandidateOwnerQuery query);

    int decideByVersion(AuthorityCandidateDecisionUpdate update);

    List<AuthorityCandidateDO> selectVisiblePage(AuthorityCandidateVisibleQuery query);
}
