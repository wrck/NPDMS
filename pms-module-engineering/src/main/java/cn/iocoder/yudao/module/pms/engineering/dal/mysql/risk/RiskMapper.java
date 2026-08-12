package cn.iocoder.yudao.module.pms.engineering.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.risk.RiskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskMapper extends BaseMapperX<RiskDO> {

    default PageResult<RiskDO> selectPage(RiskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RiskDO>()
                .eqIfPresent(RiskDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(RiskDO::getCode, reqVO.getCode())
                .likeIfPresent(RiskDO::getName, reqVO.getName())
                .eqIfPresent(RiskDO::getRiskType, reqVO.getRiskType())
                .eqIfPresent(RiskDO::getRiskLevel, reqVO.getRiskLevel())
                .eqIfPresent(RiskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RiskDO::getDeviceId, reqVO.getDeviceId())
                .likeIfPresent(RiskDO::getDeviceSerial, reqVO.getDeviceSerial())
                .eqIfPresent(RiskDO::getHandlerUserId, reqVO.getHandlerUserId())
                .eqIfPresent(RiskDO::getCrmSynced, reqVO.getCrmSynced())
                .betweenIfPresent(RiskDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RiskDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default RiskDO selectByCode(String code) {
        return selectOne(RiskDO::getCode, code);
    }

    /**
     * 按项目ID查询数量，用于项目下风险数量统计
     */
    default Long selectCountByProjectId(Long projectId) {
        return selectCount(RiskDO::getProjectId, projectId);
    }

}
