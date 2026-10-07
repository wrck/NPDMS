package cn.iocoder.yudao.module.pms.platform.support.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityScopePolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRevision;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认业务访问：普通实体无须专用实现即可获得标准读取、受控分页、关系成员枚举与扩展字段合并。
 * 专业查询仍由本域 Mapper 提供；空权限/空集合筛选返回空结果，不扩大查询范围。
 */
public class DefaultBusinessEntityAccess implements BusinessEntityAccessPort, BusinessCollectionPort {

    private static final String ID_COLUMN = "id";
    private static final String TENANT_COLUMN = "tenant_id";

    private final cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog catalog;
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessAccessGuard guard;
    private final EntityExtensionApi extensionApi;
    private final List<BusinessEntityScopePolicy> scopePolicies;
    private final DeclaredBusinessScopeSupport declaredScopes;
    private final List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityContentReader> contentReaders;

    public DefaultBusinessEntityAccess(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence,
            BusinessAccessGuard guard,
            EntityExtensionApi extensionApi) {
        this(catalog, persistence, guard, extensionApi, List.of());
    }

    public DefaultBusinessEntityAccess(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard,
            EntityExtensionApi extensionApi, List<BusinessEntityScopePolicy> scopePolicies) {
        this(catalog,persistence,guard,extensionApi,scopePolicies,List.of());
    }

    public DefaultBusinessEntityAccess(
            cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard,
            EntityExtensionApi extensionApi, List<BusinessEntityScopePolicy> scopePolicies,
            List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityContentReader> contentReaders) {
        this(catalog,persistence,guard,extensionApi,scopePolicies,contentReaders,new DeclaredBusinessScopeSupport(List.of()));
    }
    public DefaultBusinessEntityAccess(cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog catalog, BusinessEntityPersistenceRegistry persistence,
            BusinessAccessGuard guard,EntityExtensionApi extensionApi,List<BusinessEntityScopePolicy> scopePolicies,
            List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityContentReader> contentReaders,
            DeclaredBusinessScopeSupport declaredScopes) {
        this.declaredScopes=declaredScopes;
        this.catalog = catalog;
        this.persistence = persistence;
        this.guard = guard;
        this.extensionApi = extensionApi;
        this.scopePolicies = List.copyOf(scopePolicies);
        this.contentReaders = List.copyOf(contentReaders);
    }

    @Override
    public BusinessEntityData read(EntityDataRef ref, EntityActor actor, String sceneCode) {
        return read(ref,actor,sceneCode,true);
    }

    /** Fixed readable projection for capability adapters; avoids recursively merging extensions. */
    public BusinessEntityData readFixed(EntityDataRef ref, EntityActor actor, String sceneCode) {
        return read(ref,actor,sceneCode,false);
    }

    private BusinessEntityData read(EntityDataRef ref, EntityActor actor, String sceneCode, boolean extensions) {
        actor.requireTenant(ref.entity());
        var descriptor = catalog.require(ref.entity().ownerModule(), ref.entity().entityType());
        guard.requireReadable(descriptor, actor, sceneCode);
        var readers=contentReaders.stream().filter(reader -> reader.supports(descriptor.ownerModule(),descriptor.entityType())).toList();
        if (readers.size()>1) throw new BusinessContractException("CONTENT_READER_CONFLICT", "业务内容读取来源必须唯一");
        if (readers.size()==1) return readers.getFirst().read(ref,actor);
        scopePolicies.stream().filter(p -> p.supports(descriptor.ownerModule(), descriptor.entityType()))
                .forEach(p -> p.requireReadable(ref.entity(), actor));
        BusinessModelDeclaration declaration = persistence.require(
                ref.entity().ownerModule(), ref.entity().entityType());
        if (ref.isRevision()) {
            BaseMapper<?> revisionMapper = persistence.revisionMapperOf(declaration)
                    .orElseThrow(() -> new BusinessContractException("REVISION_UNSUPPORTED",
                            "实体未声明修订映射，修订身份不能当作当前对象读取: " + descriptor.stableCode()));
            Object row = revisionMapper.selectById(ref.revisionId());
            if (!(row instanceof EntityRevision revision)) {
                return unavailable(ref.entity(), "REVISION_NOT_FOUND");
            }
            if (!revision.entityRef().tenantId().equals(actor.tenantId())
                    || !revision.entityRef().entityId().equals(ref.entity().entityId())) {
                return unavailable(ref.entity(), "REVISION_NOT_FOUND");
            }
            requireDeclaredScope(declaration,row,actor);
            return toData(declaration, ref.entity(), row, ref.revisionId(), actor, extensions);
        }
        BaseBusinessEntity row = persistence.<BaseBusinessEntity>mapperOf(declaration)
                .selectById(ref.entity().entityId());
        if (row == null || !actor.tenantId().equals(row.getTenantId())) {
            return unavailable(ref.entity(), "ENTITY_NOT_FOUND");
        }
        requireDeclaredScope(declaration,row,actor);
        return toData(declaration, ref.entity(), row, null, actor, extensions);
    }

    @Override
    public BusinessEntitySlice query(BusinessEntityPageQuery query, EntityActor actor) {
        requirePageSize(query.pageSize());
        var descriptor = catalog.require(query.ownerModule(), query.entityType());
        guard.requireReadable(descriptor, actor, query.sceneCode());
        BusinessModelDeclaration declaration = persistence.require(query.ownerModule(), query.entityType());
        requireReadableFilters(descriptor, query.filters());
        List<?> rows = select(declaration, actor.tenantId(), scopedFilters(descriptor, actor, query.filters()), query.pageSize(), query.cursor());
        return slice(declaration, rows, query.pageSize(), actor);
    }

    @Override
    public BusinessEntitySlice members(BusinessCollectionQuery query, EntityActor actor) {
        requirePageSize(query.pageSize());
        var ownerDescriptor = catalog.require(query.ownerModule(), query.entityType());
        var relation = ownerDescriptor.relations().stream()
                .filter(r -> r.code().equals(query.relationCode())).findFirst()
                .orElseThrow(() -> new BusinessContractException("RELATION_UNKNOWN",
                        "未知关系: " + query.relationCode()));
        guard.requireReadable(catalog.require(relation.targetOwnerModule(), relation.targetEntityType()),
                actor, "collection:" + query.relationCode());
        var targetDeclaration = persistence.require(relation.targetOwnerModule(), relation.targetEntityType());
        requireReadableFilters(targetDeclaration.descriptor(), query.scopeFilters());
        List<BusinessFieldFilter> scope = new ArrayList<>(query.scopeFilters() == null
                ? List.of() : query.scopeFilters());
        scope.add(new BusinessFieldFilter(relation.targetJoinFieldCode(),
                BusinessFieldFilter.Operator.EQ, List.of(query.entityId())));
        List<?> rows = select(targetDeclaration, actor.tenantId(), scopedFilters(targetDeclaration.descriptor(), actor, scope), query.pageSize(), query.cursor());
        return slice(targetDeclaration, rows, query.pageSize(), actor);
    }

    private void requirePageSize(int size) {
        // Same bound as the platform PageParam; public cursor reads never support PAGE_SIZE_NONE.
        if (size < 1 || size > 200)
            throw new BusinessContractException("PAGE_SIZE_INVALID", "分页大小必须在 1 到 200 之间");
    }

    private void requireReadableFilters(BusinessModelDescriptor model, List<BusinessFieldFilter> filters) {
        for (var filter : filters == null ? List.<BusinessFieldFilter>of() : filters) {
            if (model.fields().stream().noneMatch(field -> field.code().equals(filter.fieldCode()) && field.readable()))
                throw new BusinessContractException("FIELD_NOT_OPEN", "字段未开放查询: " + filter.fieldCode());
        }
    }

    private List<BusinessFieldFilter> scopedFilters(BusinessModelDescriptor descriptor, EntityActor actor,
                                                   List<BusinessFieldFilter> filters) {
        List<BusinessFieldFilter> result = new ArrayList<>(filters == null ? List.of() : filters);
        scopePolicies.stream().filter(p -> p.supports(descriptor.ownerModule(), descriptor.entityType()))
                .forEach(p -> result.addAll(p.queryScope(actor)));
        if(descriptor.scopeBinding()!=null || scopePolicies.stream().noneMatch(p->p.supports(descriptor.ownerModule(),descriptor.entityType())))
            result.addAll(declaredScopes.queryFilters(descriptor,actor));
        return result;
    }
    private void requireDeclaredScope(BusinessModelDeclaration declaration,Object row,EntityActor actor) {
        var model=declaration.descriptor();
        if(model.scopeBinding()!=null || scopePolicies.stream().noneMatch(p->p.supports(model.ownerModule(),model.entityType())))
            declaredScopes.requireReadable(model,BusinessModelIntrospector.readValues(row,
                    BusinessModelIntrospector.businessFields(declaration.entityClass())),actor);
    }

    private List<?> select(BusinessModelDeclaration declaration, Long tenantId, List<BusinessFieldFilter> filters,
                           int pageSize, String cursor) {
        // Client filters were validated before this point. Owner filters may use non-readable ownership fields.
        var fields = BusinessModelIntrospector.businessFields(declaration.entityClass());
        QueryWrapper<BaseBusinessEntity> wrapper = new QueryWrapper<>();
        wrapper.eq(TENANT_COLUMN, tenantId);
        for (BusinessFieldFilter filter : filters == null ? List.<BusinessFieldFilter>of() : filters) {
            String column = BusinessModelIntrospector.requireColumn(fields, filter.fieldCode());
            List<Object> values = filter.values() == null ? List.of() : filter.values();
            switch (filter.operator()) {
                case EQ -> wrapper.eq(column, values.isEmpty() ? null : values.get(0));
                case NE -> wrapper.ne(column, values.isEmpty() ? null : values.get(0));
                case GT, GTE, LT, LTE -> applyComparison(wrapper, filter.operator(), column,
                        values.isEmpty() ? null : values.get(0));
                case LIKE -> wrapper.like(column, values.isEmpty() ? null : values.get(0));
                case IN -> {
                    if (values.isEmpty()) {
                        return List.of();
                    }
                    wrapper.in(column, values);
                }
                case IS_NULL -> wrapper.isNull(column);
                case NOT_NULL -> wrapper.isNotNull(column);
            }
        }
        if (cursor != null && !cursor.isBlank()) {
            wrapper.gt(ID_COLUMN, Long.parseLong(cursor));
        }
        wrapper.orderByAsc(ID_COLUMN);
        BaseMapper<BaseBusinessEntity> mapper = persistence.mapperOf(declaration);
        Page<BaseBusinessEntity> page = mapper.selectPage(
                new Page<>(1, pageSize + 1, false), wrapper);
        List<BaseBusinessEntity> records = page.getRecords();
        return records.size() > pageSize ? records.subList(0, pageSize + 1) : records;
    }

    private void applyComparison(QueryWrapper<BaseBusinessEntity> wrapper, BusinessFieldFilter.Operator operator,
                                 String column, Object value) {
        switch (operator) {
            case GT -> wrapper.gt(column, value);
            case GTE -> wrapper.ge(column, value);
            case LT -> wrapper.lt(column, value);
            case LTE -> wrapper.le(column, value);
            default -> throw new IllegalStateException(operator.name());
        }
    }

    private BusinessEntitySlice slice(BusinessModelDeclaration declaration, List<?> rows, int pageSize,
                                      EntityActor actor) {
        List<BusinessEntityData> members = new ArrayList<>();
        String nextCursor = null;
        List<?> visible = rows.size() > pageSize ? rows.subList(0, pageSize) : rows;
        for (Object row : visible) {
            members.add(toData(declaration, entityRef(declaration, row), row, null, actor));
        }
        if (rows.size() > pageSize) {
            nextCursor = String.valueOf(idOf(visible.getLast()));
        }
        return new BusinessEntitySlice(members, nextCursor,
                nextCursor == null ? Completeness.COMPLETE : Completeness.PARTIAL, null);
    }

    private BusinessEntityData toData(BusinessModelDeclaration declaration,
                                      cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef ref,
                                      Object row, Long revisionId, EntityActor actor) {
        return toData(declaration,ref,row,revisionId,actor,true);
    }

    private BusinessEntityData toData(BusinessModelDeclaration declaration,
            cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef ref,
            Object row, Long revisionId, EntityActor actor, boolean extensions) {
        // 业务字段按声明实体类解释：修订行的元数据列不进入业务值，当前行与修订行共用同一字段目录。
        Map<String, Object> values = new LinkedHashMap<>(
                BusinessModelIntrospector.readValues(row,
                        BusinessModelIntrospector.businessFields(declaration.entityClass()).stream()
                                .filter(field->declaration.descriptor().fields().stream().anyMatch(open->open.code().equals(field.code()) && open.readable()))
                                .toList()));
        Long concurrency = row instanceof BaseBusinessEntity business ? business.getVersion() : null;
        // 扩展字段按实体声明启用；修订读取合并修订携带的快照值，解释依据（定义版本）随值存储，不借用当前定义。
        if (extensions && extensionApi != null && extensionEnabled(declaration.descriptor())) {
            EntityDataRef extensionRef = revisionId == null ? EntityDataRef.current(ref)
                    : EntityDataRef.revision(new cn.iocoder.yudao.module.pms.platform.api.entity.RevisionRef(ref, revisionId));
            EntityExtensionApi.Values extension = extensionApi.read(extensionRef, actor);
            extension.fields().forEach((code,value)->{
                if(declaration.descriptor().fields().stream().noneMatch(field->field.code().equals(code))) values.put(code,value);
            });
        }
        return new BusinessEntityData(ref, revisionId, values, concurrency, true, null);
    }

    private boolean extensionEnabled(BusinessModelDescriptor descriptor) {
        return descriptor.capabilities().stream().anyMatch(capability ->
                capability.type() == BusinessCapabilityType.DYNAMIC_FORM && capability.enabled());
    }

    private cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef entityRef(
            BusinessModelDeclaration declaration, Object row) {
        return new cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef(
                ((BaseBusinessEntity) row).getTenantId(),
                declaration.descriptor().ownerModule(),
                declaration.descriptor().entityType(),
                idOf(row));
    }

    private Long idOf(Object row) {
        if (row instanceof BaseBusinessEntity entity && entity.getId() != null) {
            return entity.getId();
        }
        throw new BusinessContractException("ENTITY_ID_MISSING", "实体缺少主键 id 字段");
    }

    private BusinessEntityData unavailable(cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef ref,
                                           String reason) {
        return new BusinessEntityData(ref, null, Map.of(), null, false, reason);
    }
}
