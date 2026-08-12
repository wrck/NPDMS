package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 到货签收 Mapper（FR-ENG-021）。
 */
@Mapper
public interface ArrivalMapper extends BaseMapperX<ArrivalDO> {

    default PageResult<ArrivalDO> selectPage(ArrivalPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ArrivalDO>()
                .eqIfPresent(ArrivalDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ArrivalDO::getCode, reqVO.getCode())
                .eqIfPresent(ArrivalDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ArrivalDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(ArrivalDO::getReceiverUserId, reqVO.getReceiverUserId())
                .orderByDesc(ArrivalDO::getId));
    }

    default ArrivalDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(ArrivalDO::getProjectId, projectId, ArrivalDO::getCode, code);
    }
}
