package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCodeSequenceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目平台编码序列 Mapper（F-PM01 / V57；`SELECT ... FOR UPDATE` 行锁原子分配）
 */
@Mapper
public interface ProjectCodeSequenceMapper extends BaseMapperX<ProjectCodeSequenceDO> {

    /**
     * 行锁读取命名空间序列行（需在事务内调用；tenant_id 由租户插件过滤）
     */
    default ProjectCodeSequenceDO selectByNamespaceForUpdate(String codeNamespace) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectCodeSequenceDO>()
                .eq(ProjectCodeSequenceDO::getCodeNamespace, codeNamespace));
    }
}
