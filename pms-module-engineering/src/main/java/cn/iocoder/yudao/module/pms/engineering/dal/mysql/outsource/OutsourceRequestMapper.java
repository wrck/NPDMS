package cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OutsourceRequestMapper extends BaseMapperX<OutsourceRequestDO> {

    default PageResult<OutsourceRequestDO> selectPage(OutsourceRequestPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OutsourceRequestDO>()
                .eqIfPresent(OutsourceRequestDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(OutsourceRequestDO::getCode, reqVO.getCode())
                .likeIfPresent(OutsourceRequestDO::getName, reqVO.getName())
                .eqIfPresent(OutsourceRequestDO::getOutsourceType, reqVO.getOutsourceType())
                .eqIfPresent(OutsourceRequestDO::getStatus, reqVO.getStatus())
                .eqIfPresent(OutsourceRequestDO::getApplicantUserId, reqVO.getApplicantUserId())
                .eqIfPresent(OutsourceRequestDO::getTriggerSource, reqVO.getTriggerSource())
                .betweenIfPresent(OutsourceRequestDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(OutsourceRequestDO::getId));
    }

    /**
     * 按单号查询，用于全局唯一性校验
     */
    default OutsourceRequestDO selectByCode(String code) {
        return selectOne(OutsourceRequestDO::getCode, code);
    }

    /**
     * 按触发来源与触发来源关联编号查询
     */
    default List<OutsourceRequestDO> selectListByTriggerSource(String triggerSource, Long triggerRefId) {
        return selectList(new LambdaQueryWrapperX<OutsourceRequestDO>()
                .eq(OutsourceRequestDO::getTriggerSource, triggerSource)
                .eq(OutsourceRequestDO::getTriggerRefId, triggerRefId));
    }

}
