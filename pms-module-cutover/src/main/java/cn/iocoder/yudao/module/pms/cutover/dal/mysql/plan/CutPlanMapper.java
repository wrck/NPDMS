package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutPlanRetiredDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 割接方案 Mapper。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface CutPlanMapper extends BaseMapperX<CutPlanRetiredDO> {

    default PageResult<CutPlanRetiredDO> selectPageRetired(CutPlanPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutPlanRetiredDO>()
                .eqIfPresent(CutPlanRetiredDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(CutPlanRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(CutPlanRetiredDO::getName, reqVO.getName())
                .eqIfPresent(CutPlanRetiredDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CutPlanRetiredDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutPlanRetiredDO::getId));
    }

    default CutPlanRetiredDO selectByTaskCodeRetired(Long taskId, String code) {
        return selectOne(CutPlanRetiredDO::getTaskId, taskId, CutPlanRetiredDO::getCode, code);
    }

    default List<CutPlanRetiredDO> selectListByTaskRetired(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CutPlanRetiredDO>()
                .eq(CutPlanRetiredDO::getTaskId, taskId)
                .orderByDesc(CutPlanRetiredDO::getId));
    }

    default Long selectCountByTaskApprovedRetired(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<CutPlanRetiredDO>()
                .eq(CutPlanRetiredDO::getTaskId, taskId)
                .eq(CutPlanRetiredDO::getStatus, 2));
    }
}
