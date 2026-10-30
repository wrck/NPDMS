package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 实施方案 Mapper（FR-ENG-011 / FR-ENG-013）。
 */
@Mapper
public interface SolutionMapper extends ProjectScopedCodeMapper<SolutionDO> {

    default PageResult<SolutionDO> selectPage(SolutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SolutionDO>()
                .eqIfPresent(SolutionDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(SolutionDO::getCode, reqVO.getCode())
                .likeIfPresent(SolutionDO::getName, reqVO.getName())
                .eqIfPresent(SolutionDO::getSolutionType, reqVO.getSolutionType())
                .eqIfPresent(SolutionDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SolutionDO::getReviewLevel, reqVO.getReviewLevel())
                .orderByDesc(SolutionDO::getId));
    }


    /** 项目内已批准（状态 3）且基线已冻结的方案，按批准时间倒序。 */
    default java.util.List<SolutionDO> selectListApprovedByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<SolutionDO>()
                .eq(SolutionDO::getProjectId, projectId)
                .eq(SolutionDO::getStatus, 3)
                .orderByDesc(SolutionDO::getApprovedTime)
                .orderByDesc(SolutionDO::getId));
    }

    /** 实施方案成果锁读（业务成果判定前锁定当前行，身份三元组精确命中）。 */
    SolutionDO selectResultForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query.SolutionResultLockQuery query);

    /** 业务成果清单页：已批准方案的原生 ID 升序有界分页。 */
    java.util.List<String> selectResultInventory(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query.SolutionResultInventoryQuery query);
}
