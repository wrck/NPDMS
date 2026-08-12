package cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BriefingMapper extends BaseMapperX<BriefingDO> {

    default PageResult<BriefingDO> selectPage(BriefingPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BriefingDO>()
                .eqIfPresent(BriefingDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(BriefingDO::getCode, reqVO.getCode())
                .likeIfPresent(BriefingDO::getName, reqVO.getName())
                .eqIfPresent(BriefingDO::getBriefingType, reqVO.getBriefingType())
                .eqIfPresent(BriefingDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BriefingDO::getCreatorUserId, reqVO.getCreatorUserId())
                .eqIfPresent(BriefingDO::getApproverUserId, reqVO.getApproverUserId())
                .betweenIfPresent(BriefingDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BriefingDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default BriefingDO selectByCode(String code) {
        return selectOne(BriefingDO::getCode, code);
    }

    /**
     * 按项目ID查询数量，用于项目下交底书数量统计
     */
    default Long selectCountByProjectId(Long projectId) {
        return selectCount(BriefingDO::getProjectId, projectId);
    }

}
