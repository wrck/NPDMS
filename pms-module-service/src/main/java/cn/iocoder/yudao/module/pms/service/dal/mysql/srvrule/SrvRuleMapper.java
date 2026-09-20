package cn.iocoder.yudao.module.pms.service.dal.mysql.srvrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleRetiredDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Deprecated
public interface SrvRuleMapper extends BaseMapperX<SrvRuleRetiredDO> {

    default SrvRuleRetiredDO selectByCodeRetired(String code) {
        return selectOne(SrvRuleRetiredDO::getCode, code);
    }

    default PageResult<SrvRuleRetiredDO> selectPageRetired(SrvRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvRuleRetiredDO>()
                .likeIfPresent(SrvRuleRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(SrvRuleRetiredDO::getName, reqVO.getName())
                .eqIfPresent(SrvRuleRetiredDO::getRuleType, reqVO.getRuleType())
                .eqIfPresent(SrvRuleRetiredDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvRuleRetiredDO::getEffectiveTime, reqVO.getEffectiveTime())
                .orderByDesc(SrvRuleRetiredDO::getId));
    }

}
