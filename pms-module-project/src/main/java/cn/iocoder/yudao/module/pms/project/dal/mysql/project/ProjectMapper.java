package cn.iocoder.yudao.module.pms.project.dal.mysql.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectRetiredDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectReferenceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectSummaryPageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目 Mapper
 */
@Mapper
@Deprecated
/**
 * PMS 项目 Mapper（旧链）
 *
 * @deprecated 旧 {@code pms_project_retired} 已冻结只读（AI-MIG-000 / V260 全量前向导入）。
 * 口径A（V254 有承接领域）的领域服务已直接读写新权威主档 {@code proj_project}
 * （ProjectMasterMapper）；本 Mapper 仅保留口径A 范围外旧链过渡消费方使用，
 * 不得在新增实现中引用。
 */
public interface ProjectMapper extends BaseMapperX<ProjectRetiredDO> {

    default PageResult<ProjectRetiredDO> selectPageRetired(ProjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectRetiredDO>()
                .likeIfPresent(ProjectRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectRetiredDO::getName, reqVO.getName())
                .eqIfPresent(ProjectRetiredDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(ProjectRetiredDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectRetiredDO::getProjectType, reqVO.getProjectType())
                .eqIfPresent(ProjectRetiredDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ProjectRetiredDO::getMajorProjectFlag, reqVO.getMajorProjectFlag())
                .eqIfPresent(ProjectRetiredDO::getManagerUserId, reqVO.getManagerUserId())
                .eqIfPresent(ProjectRetiredDO::getParentId, reqVO.getParentId())
                .eqIfPresent(ProjectRetiredDO::getRootId, reqVO.getRootId())
                .orderByDesc(ProjectRetiredDO::getId));
    }

    default Long selectCountByCustomerRetired(CustomerProjectReferenceQuery query) {
        return selectCount(new LambdaQueryWrapperX<ProjectRetiredDO>()
                .eq(ProjectRetiredDO::getTenantId, query.tenantId())
                .eq(ProjectRetiredDO::getCustomerId, query.customerId()));
    }

    default PageResult<ProjectRetiredDO> selectCustomerSummaryPageRetired(CustomerProjectSummaryPageQuery query) {
        if (query.getVisibleProjectIds().isEmpty()) {
            return PageResult.empty();
        }
        return selectPage(query, new LambdaQueryWrapperX<ProjectRetiredDO>()
                .eq(ProjectRetiredDO::getTenantId, query.getTenantId())
                .eq(ProjectRetiredDO::getCustomerId, query.getCustomerId())
                .in(ProjectRetiredDO::getId, query.getVisibleProjectIds())
                .orderByDesc(ProjectRetiredDO::getId));
    }

    default ProjectRetiredDO selectByCodeRetired(String code) {
        return selectOne(ProjectRetiredDO::getCode, code);
    }

    default ProjectRetiredDO selectBySourceSystemAndBusinessKeyRetired(String sourceSystem, String sourceBusinessKey) {
        return selectOne(new LambdaQueryWrapperX<ProjectRetiredDO>()
                .eq(ProjectRetiredDO::getSourceSystem, sourceSystem)
                .eq(ProjectRetiredDO::getSourceBusinessKey, sourceBusinessKey));
    }

    default List<ProjectRetiredDO> selectListByParentIdRetired(Long parentId) {
        return selectList(ProjectRetiredDO::getParentId, parentId);
    }

    default List<ProjectRetiredDO> selectListByRootIdRetired(Long rootId) {
        return selectList(ProjectRetiredDO::getRootId, rootId);
    }

    default List<ProjectRetiredDO> selectListByPathPrefixRetired(String pathPrefix) {
        return selectList(new LambdaQueryWrapperX<ProjectRetiredDO>()
                .likeRight(ProjectRetiredDO::getPath, pathPrefix));
    }

}
