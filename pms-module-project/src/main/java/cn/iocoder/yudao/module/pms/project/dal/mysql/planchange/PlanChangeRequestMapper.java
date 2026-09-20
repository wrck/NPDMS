package cn.iocoder.yudao.module.pms.project.dal.mysql.planchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangeRequestRetiredDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 计划变更审批 Mapper（FR-PROJ-020 / T-V2-PROJ-003）
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface PlanChangeRequestMapper extends BaseMapperX<PlanChangeRequestRetiredDO> {

    default PlanChangeRequestRetiredDO selectByChangeNoRetired(String changeNo) {
        return selectOne(new LambdaQueryWrapperX<PlanChangeRequestRetiredDO>()
                .eq(PlanChangeRequestRetiredDO::getChangeNo, changeNo));
    }

    default PageResult<PlanChangeRequestRetiredDO> selectPageRetired(PlanChangePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PlanChangeRequestRetiredDO>()
                .eqIfPresent(PlanChangeRequestRetiredDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(PlanChangeRequestRetiredDO::getChangeNo, reqVO.getChangeNo())
                .likeIfPresent(PlanChangeRequestRetiredDO::getTitle, reqVO.getTitle())
                .eqIfPresent(PlanChangeRequestRetiredDO::getChangeType, reqVO.getChangeType())
                .eqIfPresent(PlanChangeRequestRetiredDO::getStatus, reqVO.getStatus())
                .eqIfPresent(PlanChangeRequestRetiredDO::getApplicantUserId, reqVO.getApplicantUserId())
                .orderByDesc(PlanChangeRequestRetiredDO::getId));
    }

}
