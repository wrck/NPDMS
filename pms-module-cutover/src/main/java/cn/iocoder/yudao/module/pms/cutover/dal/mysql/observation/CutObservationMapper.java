package cn.iocoder.yudao.module.pms.cutover.dal.mysql.observation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.observation.CutObservationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 稳定观察 Mapper。
 */
@Mapper
public interface CutObservationMapper extends BaseMapperX<CutObservationDO> {

    default PageResult<CutObservationDO> selectPage(CutObservationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutObservationDO>()
                .eqIfPresent(CutObservationDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(CutObservationDO::getCode, reqVO.getCode())
                .eqIfPresent(CutObservationDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CutObservationDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutObservationDO::getId));
    }

    default CutObservationDO selectByTaskCode(Long taskId, String code) {
        return selectOne(CutObservationDO::getTaskId, taskId, CutObservationDO::getCode, code);
    }

    default List<CutObservationDO> selectListByTask(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CutObservationDO>()
                .eq(CutObservationDO::getTaskId, taskId)
                .orderByDesc(CutObservationDO::getId));
    }
}
