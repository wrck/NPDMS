package cn.iocoder.yudao.module.pms.service.dal.mysql.srvrule;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SrvRuleMapper extends BaseMapperX<SrvRuleDO> {

    default SrvRuleDO selectByCode(String code) {
        return selectOne(SrvRuleDO::getCode, code);
    }

    default PageResult<SrvRuleDO> selectPage(SrvRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvRuleDO>()
                .likeIfPresent(SrvRuleDO::getCode, reqVO.getCode())
                .likeIfPresent(SrvRuleDO::getName, reqVO.getName())
                .eqIfPresent(SrvRuleDO::getRuleType, reqVO.getRuleType())
                .eqIfPresent(SrvRuleDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvRuleDO::getEffectiveTime, reqVO.getEffectiveTime())
                .orderByDesc(SrvRuleDO::getId));
    }

}
