package cn.iocoder.yudao.module.pms.project.dal.mysql.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目 Mapper
 */
@Mapper
public interface ProjectMapper extends BaseMapperX<ProjectDO> {

    default PageResult<ProjectDO> selectPage(ProjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectDO>()
                .likeIfPresent(ProjectDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectDO::getName, reqVO.getName())
                .eqIfPresent(ProjectDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(ProjectDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectDO::getProjectType, reqVO.getProjectType())
                .eqIfPresent(ProjectDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ProjectDO::getMajorProjectFlag, reqVO.getMajorProjectFlag())
                .eqIfPresent(ProjectDO::getManagerUserId, reqVO.getManagerUserId())
                .eqIfPresent(ProjectDO::getParentId, reqVO.getParentId())
                .eqIfPresent(ProjectDO::getRootId, reqVO.getRootId())
                .orderByDesc(ProjectDO::getId));
    }

    default ProjectDO selectByCode(String code) {
        return selectOne(ProjectDO::getCode, code);
    }

    default ProjectDO selectBySourceSystemAndBusinessKey(String sourceSystem, String sourceBusinessKey) {
        return selectOne(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getSourceSystem, sourceSystem)
                .eq(ProjectDO::getSourceBusinessKey, sourceBusinessKey));
    }

    default List<ProjectDO> selectListByParentId(Long parentId) {
        return selectList(ProjectDO::getParentId, parentId);
    }

    default List<ProjectDO> selectListByRootId(Long rootId) {
        return selectList(ProjectDO::getRootId, rootId);
    }

    default List<ProjectDO> selectListByPathPrefix(String pathPrefix) {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .likeRight(ProjectDO::getPath, pathPrefix));
    }

}
