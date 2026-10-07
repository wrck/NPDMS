package cn.iocoder.yudao.module.pms.platform.support.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * Thin entity/Mapper binding for normal project businesses. Metadata comes from the inherited entity;
 * no per-business scope policy, operation implementation or hand-maintained field list is needed.
 * Existing legacy declarations are unchanged: adopting defaults is explicit, not a permission expansion.
 */
public class DefaultProjectBusinessModel<T extends BaseProjectBusinessEntity> implements BusinessModelContributor {
    private final Class<T> entityClass;
    private final BaseMapper<T> mapper;
    private final ProjectBusinessModel model;
    private volatile BusinessModelDeclaration declaration;

    public DefaultProjectBusinessModel(Class<T> entityClass, BaseMapper<T> mapper) {
        this.entityClass = java.util.Objects.requireNonNull(entityClass, "entityClass");
        this.mapper = mapper;
        this.model = entityClass.getDeclaredAnnotation(ProjectBusinessModel.class);
        if (model == null || mapper == null) {
            throw new BusinessContractException("DECLARATION_INCOMPLETE", "项目业务必须提供实体身份和 Mapper");
        }
        requireCode(model.ownerModule());
        requireCode(model.entityType());
        requireCode(model.stableCode());
        requireCode(model.permissionPrefix());
        if (model.name().isBlank()) throw new BusinessContractException("DECLARATION_INCOMPLETE", "缺少业务名称");
    }

    private BusinessModelDeclaration buildDeclaration() {
        return DefaultBusinessModels.project(model.ownerModule(), model.entityType(), model.stableCode(),
                model.name(), model.permissionPrefix(), entityClass, mapper, additionalOperations(model.permissionPrefix()));
    }

    protected List<BusinessOperationDescriptor> additionalOperations(String prefix) {
        return List.of();
    }

    @Override
    public final synchronized List<BusinessModelDeclaration> declarations() {
        if (declaration == null) declaration = buildDeclaration();
        return List.of(declaration);
    }

    private static void requireCode(String code) {
        if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new BusinessContractException("DECLARATION_INCOMPLETE", "业务身份和权限前缀格式无效");
    }
}
