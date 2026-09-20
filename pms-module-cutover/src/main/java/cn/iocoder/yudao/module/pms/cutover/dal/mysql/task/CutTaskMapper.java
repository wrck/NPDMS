package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskRetiredDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverGovernanceGuardQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * PMS 割接任务 Mapper。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface CutTaskMapper extends BaseMapperX<CutTaskRetiredDO> {

    default PageResult<CutTaskRetiredDO> selectPageRetired(CutTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutTaskRetiredDO>()
                .eqIfPresent(CutTaskRetiredDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(CutTaskRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(CutTaskRetiredDO::getName, reqVO.getName())
                .eqIfPresent(CutTaskRetiredDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CutTaskRetiredDO::getRiskLevel, reqVO.getRiskLevel())
                .betweenIfPresent(CutTaskRetiredDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutTaskRetiredDO::getId));
    }

    default CutTaskRetiredDO selectByProjectCodeRetired(Long projectId, String code) {
        return selectOne(CutTaskRetiredDO::getProjectId, projectId, CutTaskRetiredDO::getCode, code);
    }

    default List<CutTaskRetiredDO> selectListByProjectRetired(Long projectId) {
        return selectList(new LambdaQueryWrapperX<CutTaskRetiredDO>()
                .eq(CutTaskRetiredDO::getProjectId, projectId)
                .orderByDesc(CutTaskRetiredDO::getId));
    }

    default List<CutTaskRetiredDO> selectListForGovernanceGuardRetired(CutoverGovernanceGuardQuery query) {
        if (query.projectIds().isEmpty()) {
            return List.of();
        }
        return selectListForGovernanceGuard0Retired(query);
    }

    List<CutTaskRetiredDO> selectListForGovernanceGuard0Retired(@Param("query") CutoverGovernanceGuardQuery query);
}
