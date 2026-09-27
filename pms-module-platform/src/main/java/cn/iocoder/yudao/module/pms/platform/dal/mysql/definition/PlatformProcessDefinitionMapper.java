package cn.iocoder.yudao.module.pms.platform.dal.mysql.definition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.definition.ProcessDefinitionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.definition.query.ProcessDefinitionPageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlatformProcessDefinitionMapper extends BaseMapperX<ProcessDefinitionDO> {

    default PageResult<ProcessDefinitionDO> selectPage(ProcessDefinitionPageQuery query) {
        return selectPage(query, query.toWrapper());
    }

    default Optional<ProcessDefinitionDO> selectByCodeAndVersion(String definitionCode, int definitionVersion) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<ProcessDefinitionDO>()
                .eq(ProcessDefinitionDO::getDefinitionCode, definitionCode)
                .eq(ProcessDefinitionDO::getDefinitionVersion, definitionVersion)));
    }

    default Optional<ProcessDefinitionDO> selectDraft(String definitionCode) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<ProcessDefinitionDO>()
                .eq(ProcessDefinitionDO::getDefinitionCode, definitionCode)
                .eq(ProcessDefinitionDO::getStatus, "DRAFT")));
    }

    default List<ProcessDefinitionDO> selectPublished(String ownerModule, String entityType) {
        return selectList(new LambdaQueryWrapperX<ProcessDefinitionDO>()
                .eqIfPresent(ProcessDefinitionDO::getOwnerModule, ownerModule)
                .eqIfPresent(ProcessDefinitionDO::getEntityType, entityType)
                .eq(ProcessDefinitionDO::getStatus, "PUBLISHED")
                .orderByDesc(ProcessDefinitionDO::getId));
    }

    default List<ProcessDefinitionDO> selectPublishedByCode(String definitionCode) {
        return selectList(new LambdaQueryWrapperX<ProcessDefinitionDO>()
                .eq(ProcessDefinitionDO::getDefinitionCode, definitionCode)
                .eq(ProcessDefinitionDO::getStatus, "PUBLISHED")
                .orderByDesc(ProcessDefinitionDO::getDefinitionVersion));
    }
}
