package cn.iocoder.yudao.module.pms.cutover.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskRetiredDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 割接风险 Mapper。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface CutRiskMapper extends BaseMapperX<CutRiskRetiredDO> {

    default PageResult<CutRiskRetiredDO> selectPageRetired(CutRiskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutRiskRetiredDO>()
                .eqIfPresent(CutRiskRetiredDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(CutRiskRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(CutRiskRetiredDO::getName, reqVO.getName())
                .eqIfPresent(CutRiskRetiredDO::getRiskType, reqVO.getRiskType())
                .eqIfPresent(CutRiskRetiredDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CutRiskRetiredDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutRiskRetiredDO::getId));
    }

    default CutRiskRetiredDO selectByTaskCodeRetired(Long taskId, String code) {
        return selectOne(CutRiskRetiredDO::getTaskId, taskId, CutRiskRetiredDO::getCode, code);
    }

    default List<CutRiskRetiredDO> selectListByTaskRetired(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CutRiskRetiredDO>()
                .eq(CutRiskRetiredDO::getTaskId, taskId)
                .orderByDesc(CutRiskRetiredDO::getId));
    }

    default Long selectCountByTaskNotClosedRetired(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<CutRiskRetiredDO>()
                .eq(CutRiskRetiredDO::getTaskId, taskId)
                .in(CutRiskRetiredDO::getStatus, 0, 1, 3));
    }
}
