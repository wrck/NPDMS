package cn.iocoder.yudao.module.pms.project.dal.mysql.planchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangeRequestDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 计划变更审批 Mapper（FR-PROJ-020 / T-V2-PROJ-003）
 */
@Mapper
public interface PlanChangeRequestMapper extends BaseMapperX<PlanChangeRequestDO> {

    default PlanChangeRequestDO selectByChangeNo(String changeNo) {
        return selectOne(new LambdaQueryWrapperX<PlanChangeRequestDO>()
                .eq(PlanChangeRequestDO::getChangeNo, changeNo));
    }

    default PageResult<PlanChangeRequestDO> selectPage(PlanChangePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PlanChangeRequestDO>()
                .eqIfPresent(PlanChangeRequestDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(PlanChangeRequestDO::getChangeNo, reqVO.getChangeNo())
                .likeIfPresent(PlanChangeRequestDO::getTitle, reqVO.getTitle())
                .eqIfPresent(PlanChangeRequestDO::getChangeType, reqVO.getChangeType())
                .eqIfPresent(PlanChangeRequestDO::getStatus, reqVO.getStatus())
                .eqIfPresent(PlanChangeRequestDO::getApplicantUserId, reqVO.getApplicantUserId())
                .orderByDesc(PlanChangeRequestDO::getId));
    }

}
