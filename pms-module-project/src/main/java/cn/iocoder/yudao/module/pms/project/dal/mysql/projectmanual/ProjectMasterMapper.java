package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目主档 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectMasterMapper extends BaseMapperX<ProjectMasterDO> {

    /**
     * 分页查询（简单条件）：名称模糊、编码/状态/三维精确，id 倒序
     */
    default PageResult<ProjectMasterDO> selectPage(PageParam pageParam, String projectName, String projectCode,
                                                   String status, String signingMethod, String projectCategory,
                                                   String implementationMode) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ProjectMasterDO>()
                .likeIfPresent(ProjectMasterDO::getProjectName, projectName)
                .likeRightIfPresent(ProjectMasterDO::getProjectCode, projectCode)
                .eqIfPresent(ProjectMasterDO::getStatus, status)
                .eqIfPresent(ProjectMasterDO::getSigningMethod, signingMethod)
                .eqIfPresent(ProjectMasterDO::getProjectCategory, projectCategory)
                .eqIfPresent(ProjectMasterDO::getImplementationMode, implementationMode)
                .orderByDesc(ProjectMasterDO::getId));
    }
}
