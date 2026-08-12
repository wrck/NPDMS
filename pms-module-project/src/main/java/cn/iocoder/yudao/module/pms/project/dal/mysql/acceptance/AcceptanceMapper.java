package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptancePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AcceptanceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AcceptanceMapper extends BaseMapperX<AcceptanceDO> {

    default AcceptanceDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<AcceptanceDO>()
                .eq(AcceptanceDO::getProjectId, projectId)
                .eq(AcceptanceDO::getCode, code));
    }

    default PageResult<AcceptanceDO> selectPage(AcceptancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AcceptanceDO>()
                .eqIfPresent(AcceptanceDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(AcceptanceDO::getCode, reqVO.getCode())
                .likeIfPresent(AcceptanceDO::getName, reqVO.getName())
                .eqIfPresent(AcceptanceDO::getAcceptanceType, reqVO.getAcceptanceType())
                .eqIfPresent(AcceptanceDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(AcceptanceDO::getAcceptanceDate, reqVO.getAcceptanceDate())
                .orderByDesc(AcceptanceDO::getId));
    }

}
