package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;

/**
 * Direct typed business implementation. A normal business supplies only E and M through inheritance.
 * Controllers call these CRUD methods, which use this service's Mapper directly. The older model
 * catalog/operation dispatcher is neither a dependency nor an enrollment prerequisite.
 */
public abstract class DefaultProjectBusinessService<M extends BusinessMapper<E>, E extends BaseProjectBusinessEntity>
        implements ProjectBusinessService<E>, EntityFieldProvider {
    @Autowired protected M mapper;
    @Autowired protected BusinessDefaults defaults;
    private BusinessEntityBinding<E> binding;
    @Autowired private org.springframework.beans.factory.ObjectProvider<EntityFormApi> formPorts;
    @Autowired private org.springframework.beans.factory.ObjectProvider<EntityExtensionApi> extensionPorts;

    @PostConstruct
    @SuppressWarnings("unchecked")
    protected final void initializeBinding() {
        Class<?> type = ResolvableType.forClass(getClass()).as(DefaultProjectBusinessService.class).getGeneric(1).resolve();
        if (type == null || !BaseProjectBusinessEntity.class.isAssignableFrom(type)) throw invalid("BUSINESS_TYPE_UNRESOLVED", "Service must bind its concrete entity");
        var identity = type.getDeclaredAnnotation(cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel.class);
        if (identity == null) throw invalid("BUSINESS_IDENTITY_REQUIRED", "Entity must define its stable business identity");
        var operations=new ArrayList<>(businessOperations(identity.permissionPrefix()));
        operations.addAll(defaultOperations(identity.permissionPrefix()));
        binding = new BusinessEntityBinding<>((Class<E>) type, mapper, operations, this::configureOperations,this::configureCapabilities);
    }
    @Override public final BusinessModelDescriptor definition() { return binding.mapping.descriptor(); }
    @Override public final BusinessModelViews.ModelDetailVO model() {
        var actor = actor(); authorizeModelRead(actor, "detail");
        return modelView(BusinessModelViews.ModelDetailVO.of(definition(), actor, defaults.permissions()));
    }
    @Override public final E input(Map<String,Object> values) { return binding.create(values); }
    @Override public final Map<String,Object> readableValues(E entity) { return binding.readable(entity); }
    @Override public final E get(Long id) {
        var actor = actor(); authorizeModelRead(actor, "detail");
        E row = current(id, actor); defaults.projects().requireReadable(row.getProjectId(), actor); return row;
    }
    @Override public final PageResult<E> page(BusinessPageQuery query) {
        var actor = actor(); authorizeModelRead(actor, "list");
        if (query == null || query.getPageNo() < 1 || query.getPageSize() < 1 || query.getPageSize() > 200)
            throw invalid("QUERY_INVALID", "Invalid page bounds");
        var projects = defaults.projects().readableScopeIds(actor);
        if (projects == null || projects.isEmpty()) return new PageResult<>(List.of(), 0L);
        var result = selectPage(new BusinessReadQuery(actor.tenantId(), projects, query));
        if (result == null || result.getList() == null || result.getList().size() > query.getPageSize())
            throw invalid("QUERY_RESULT_INVALID", "Business query returned an invalid page");
        for (E row : result.getList()) {
            checkRow(row, row.getId(), actor);
            if (!projects.contains(row.getProjectId())) throw invalid("ENTITY_SCOPE_DENIED", "Business query returned a foreign project");
            defaults.projects().requireReadable(row.getProjectId(), actor);afterRead(row);
        }
        return result;
    }
    /** Override this method and call this business Mapper's XML query for complex selection. */
    protected PageResult<E> selectPage(BusinessReadQuery query) { return mapper.selectBusinessPage(query); }

    @Override public final BusinessOperationReceipt create(E source, String key) {
        var values = inputValues(source, null);
        return write("create", null, null, values, key, actor -> {
            E entity = binding.create(values);
            Long generated=generatedId(),project=entity.getProjectId();long initial=initialVersion();
            if(initial<0 || generated!=null && generated<=0)throw invalid("CONTROL_FIELD_CHANGED","Invalid generated identity");
            entity.setId(generated);entity.setTenantId(actor.tenantId());entity.setVersion(initial);
            requireProject(entity);defaults.projects().requireWritable(entity.getProjectId(), actor, true);
            return inBusinessOperation("create", null, entity, () -> {
            beforeCreate(entity);
            if (!Objects.equals(project,entity.getProjectId()) || !Objects.equals(generated,entity.getId()) || !actor.tenantId().equals(entity.getTenantId()) || !Long.valueOf(initial).equals(entity.getVersion()))
                throw invalid("CONTROL_FIELD_CHANGED", "Initialization changed business identity");
            requireProject(entity); defaults.projects().requireWritable(entity.getProjectId(), actor, true);
            validate(entity,"create");
            if (mapper.insert(entity) != 1 || entity.getId() == null) throw invalid("ENTITY_INSERT_FAILED", "Business insert did not return one persisted identity");
            afterCreate(copy(entity));
            return saved(entity, "create", ReceiptOutcome.SAVED);
            });
        });
    }
    @Override public final BusinessOperationReceipt update(Long id, E source, Set<String> changedFields, Long version, String key) {
        var values = inputValues(source, Objects.requireNonNull(changedFields));
        return write("save", id, version, values, key, actor -> {
            E before = current(id, actor); requireVersion(before, version);
            E proposed = copy(before); binding.patch(proposed, values); beforeUpdate(copy(before), proposed);
            checkIdentity(proposed, before); requireProject(proposed);
            defaults.projects().requireWritableScopes(new HashSet<>(List.of(before.getProjectId(), proposed.getProjectId())), actor, true);
            return inBusinessOperation("save", before, proposed, () -> {
            E locked = lock(id, actor); requireVersion(locked, version);
            if (!binding.values(before).equals(binding.values(locked))) throw invalid("CONCURRENCY_CONFLICT", "Business changed while its scopes were locked");
            persistBusinessChange(before,proposed,"save");
            return saved(proposed, "save", ReceiptOutcome.SAVED);
            });
        });
    }
    @Override public final BusinessOperationReceipt delete(Long id, Long version, String key) {
        return write("delete", id, version, Map.of(), key, actor -> {
            E observed = current(id, actor); requireVersion(observed, version);
            defaults.projects().requireWritable(observed.getProjectId(), actor, true);
            return inBusinessOperation("delete", observed, null, () -> {
            E locked = lock(id, actor); requireVersion(locked, version);
            if (!Objects.equals(observed.getProjectId(), locked.getProjectId())) throw invalid("CONCURRENCY_CONFLICT", "Business project changed");
            requireFrameworkDelete(copy(locked));beforeDelete(copy(locked));
            if (defaults.deletionGuards().isEmpty()) throw invalid("DELETE_PROTECTION_UNAVAILABLE", "Shared reference protection is unavailable");
            for (var guard : defaults.deletionGuards()) {
                guard.requireDeletable(identity(locked), actor);
                if(binding.mapping.nativeEntityType()!=null && !definition().entityType().equals(binding.mapping.nativeEntityType()))
                    guard.requireDeletable(new EntityRef(actor.tenantId(),definition().ownerModule(),binding.mapping.nativeEntityType(),id),actor);
            }
            DeclaredBusinessCurrentRows.delete(binding.mapping, new DeclaredBusinessCurrentRows.DeleteCommand(actor.tenantId(), id, version, actor.userId().toString()));
            locked.setVersion(Math.incrementExact(version)); afterDelete(copy(locked));
            return saved(locked, "delete", ReceiptOutcome.DELETED);
            });
        });
    }

    /** The service itself supplies common entity capabilities; no per-business provider or adapter. */
    @Override public final String ownerModule(){return definition().ownerModule();}
    @Override public final String entityType(){return definition().entityType();}
    @Override public final List<EntityField> fields(){return definition().fields().stream()
            .map(f->new EntityField(f.code(),f.type(),f.required())).toList();}
    @Override public Map<String,EntityFieldValue> read(EntityDataRef target,EntityActor caller){
        requireProviderCaller(target,caller);var row=get(target.entity().entityId());var result=new LinkedHashMap<String,EntityFieldValue>();
        readableValues(row).forEach((key,value)->result.put(key,EntityFieldValue.known(value)));return result;
    }
    @Override public void requireReadable(EntityDataRef target,EntityActor caller){requireProviderCaller(target,caller);get(target.entity().entityId());}
    @Override public Long concurrencyBasis(EntityDataRef target,EntityActor caller){requireProviderCaller(target,caller);return get(target.entity().entityId()).getVersion();}
    @Override public boolean usesValidatedExtensionPatch(){return true;}
    @Override public void lockForWrite(EntityDataRef target,EntityActor caller,Long expectedVersion){
        requireProviderCaller(target,caller);BusinessEntitySaveSupport.requireTransaction();
        defaults.permissions().requireWritable(definition(),caller,"operation:save");
        var observed=current(target.entity().entityId(),caller);defaults.projects().requireWritable(observed.getProjectId(),caller,true);
        inBusinessOperation("save",observed,null,()->{var row=lock(observed.getId(),caller);requireVersion(row,expectedVersion);beforeUpdate(copy(row),copy(row));return null;});
    }
    protected final void requireProviderCaller(EntityDataRef target,EntityActor caller){
        var actual=actor();
        if(target==null || caller==null || !Objects.equals(actual.tenantId(),caller.tenantId()) || !Objects.equals(actual.userId(),caller.userId())
                || target.isRevision() || !Objects.equals(target.entity().tenantId(),actual.tenantId())
                || !ownerModule().equals(target.entity().ownerModule()) || !entityType().equals(target.entity().entityType()))
            throw invalid("ENTITY_SCOPE_DENIED","Entity capability identity or caller differs from the bound service");
    }
    /** Existing revision businesses override only their historical storage identity. */
    protected EntityDataRef formTarget(E row){return EntityDataRef.current(identity(row));}
    private EntityFormApi formPort(){var port=formPorts.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Form capability is unavailable");return port;}
    private EntityExtensionApi extensionPort(){var port=extensionPorts.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Extension capability is unavailable");return port;}
    @Override public final BusinessFormData form(Long id){
        var row=get(id);var target=formTarget(row);var caller=actor();var values=extensionPort().read(target,caller);var layout=formPort().layout(target,caller);
        var definitionId=layout!=null && layout.binding().extensionDefinitionRevisionId()!=null?layout.binding().extensionDefinitionRevisionId():values.definitionRevisionId();
        return new BusinessFormData(layout,values,definitionId==null?List.of():extensionPort().definition(definitionId,target.entity(),caller).fields());
    }
    @Override public final BusinessOperationReceipt saveForm(Long id,Map<String,Object> values,Long version,String key){
        if(values==null)throw invalid("INPUT_REQUIRED","Business values are required");
        var fixed=new LinkedHashMap<>(values);Object extension=fixed.remove("$extensions"),layout=fixed.remove("$binding");
        var layoutPatch=layout==null?null:JsonUtils.parseObject(JsonUtils.toJsonString(layout),BusinessFormData.BindingPatch.class);
        var patch=extension==null?null:JsonUtils.parseObject(JsonUtils.toJsonString(extension),BusinessEntitySaveSupport.ExtensionPatch.class);
        return change("save-form",id,version,key,values,row->{
            var before=copy(row);binding.patch(row,fixed);beforeUpdate(before,row);
            if(layoutPatch!=null){
                if(layoutPatch.expectedVersion()<0 || layoutPatch.formRevisionId()==null || layoutPatch.formRevisionId()<=0)
                    throw invalid("INPUT_INVALID","Invalid form binding");
                formPort().bind(new EntityFormApi.Bind(formTarget(row),actor(),version,layoutPatch.expectedVersion(),layoutPatch.formRevisionId(),
                        layoutPatch.extensionDefinitionRevisionId(),layoutPatch.fieldBindings(),layoutPatch.bindRemainingFields()));
            }
            if(patch!=null){
                if(patch.values()==null || patch.expectedVersion()<0)throw invalid("INPUT_INVALID","Invalid extension patch");
                var target=formTarget(row);var existing=extensionPort().read(target,actor());
                var merged=new LinkedHashMap<String,Object>();
                if(Objects.equals(existing.definitionRevisionId(),patch.definitionRevisionId()))merged.putAll(existing.fields());
                merged.putAll(patch.values());
                extensionPort().save(new EntityExtensionApi.Save(target,actor(),version,patch.expectedVersion(),patch.definitionRevisionId(),merged));
            }
        });
    }

    protected List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor> defaultOperations(String prefix){
        return List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("save-form",1,"保存表单",
                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update"));
    }
    protected List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding> configureCapabilities(
            List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding> capabilities){return capabilities;}
    protected final Class<E> entityClass(){return binding.type;}
    protected final void patchBusinessFields(E entity,Map<String,Object> values){binding.patch(entity,values);}
    protected final Map<String,Object> businessValues(E entity){return binding.values(entity);}
    /** Normal saves and revision activation share the same validation, CAS, explicit-null and persistence hooks. */
    protected final E persistBusinessChange(E before,E proposed,String operation){
        checkIdentity(proposed,before);validate(proposed,operation);
        var nulls=new HashSet<String>();binding.values(proposed).forEach((name,value)->{if(value==null)nulls.add(name);});
        persistUpdate(proposed,actor(),before.getVersion(),nulls);afterUpdate(copy(before),copy(proposed));return proposed;
    }
    /** Only additional business actions are listed here; standard CRUD and API enrollment remain inherited. */
    protected java.util.List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor> businessOperations(String permissionPrefix) {
        return List.of();
    }
    /** A dedicated business API can call this typed mutation; it is not a generic operation dispatcher. */
    protected final BusinessOperationReceipt change(String operation, Long id, Long version, String key,
            Map<String,Object> intent, java.util.function.Consumer<E> mutation) {
        requireOperation(operation);
        if (Set.of("create", "save", "delete").contains(operation)) throw invalid("BUSINESS_OPERATION_INVALID", "Use the inherited CRUD method for standard operations");
        return write(operation, id, version, intent, key, actor -> {
            E before = current(id, actor); requireVersion(before, version);
            defaults.projects().requireWritable(before.getProjectId(),actor,true);
            return inBusinessOperation(operation,before,null,()->{
                E locked=lock(id,actor);requireVersion(locked,version);
                if(!binding.values(before).equals(binding.values(locked)))throw invalid("CONCURRENCY_CONFLICT","Business changed while its scopes were locked");
                E proposed=copy(locked);mutation.accept(proposed);checkIdentity(proposed,locked);
                if(!Objects.equals(proposed.getProjectId(),locked.getProjectId()))throw invalid("ENTITY_SCOPE_DENIED","A domain action cannot move its project");
                validate(proposed,operation);
                var nulls=new HashSet<String>();binding.values(proposed).forEach((name,value)->{if(value==null)nulls.add(name);});
                persistUpdate(proposed,actor,version,nulls);
                afterChange(operation,copy(locked),copy(proposed));
                return saved(proposed,operation,ReceiptOutcome.SAVED);
            });
        });
    }

    /** Additional multi-row domain operation: keeps shared authorization, locks, idempotency and receipt verification. */
    protected final BusinessOperationReceipt businessAction(String operation,Long id,Long version,String key,
            Map<String,Object> intent,ReceiptOutcome outcome,Function<E,E> command) {
        if(Set.of("create","save","delete").contains(operation))throw invalid("BUSINESS_OPERATION_INVALID","Use the inherited CRUD operation");
        return write(operation,id,version,intent,key,actor->{
            E before=current(id,actor);defaults.projects().requireWritable(before.getProjectId(),actor,true);requireVersion(before,version);
            return inBusinessOperation(operation,before,null,()->{
                E locked=lock(id,actor);requireVersion(locked,version);
                if(!Objects.equals(before.getProjectId(),locked.getProjectId()))throw invalid("CONCURRENCY_CONFLICT","Business scope changed");
                E result=command.apply(copy(locked));
                checkRow(result,result==null?null:result.getId(),actor);
                if(!Objects.equals(locked.getProjectId(),result.getProjectId()))throw invalid("ENTITY_SCOPE_DENIED","Domain result changed project");
                E actual=current(result.getId(),actor);
                if(!Objects.equals(actual.getProjectId(),result.getProjectId()) || !Objects.equals(actual.getVersion(),result.getVersion()))throw invalid("BUSINESS_RESULT_INVALID","Domain result differs from persisted identity");
                return saved(actual,operation,outcome);
            });
        });
    }

    /** Override only existing business permission names/availability; inherited CRUD implementations remain shared. */
    protected List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor> configureOperations(
            List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor> operations) { return operations; }
    /** Existing Owner execution context surrounds the shared lock/write; the default has no workflow dependency. */
    protected <T> T inBusinessOperation(String operation,E current,E proposed,java.util.function.Supplier<T> action) { return action.get(); }
    protected Long generatedId() { return null; }
    protected long initialVersion() { return 0L; }
    protected void afterRead(E entity) { }
    protected void authorizeModelRead(EntityActor actor,String scene) { defaults.permissions().requireReadable(definition(),actor,scene); }
    protected BusinessModelViews.ModelDetailVO modelView(BusinessModelViews.ModelDetailVO view) { return view; }
    protected boolean publishDefaultEvent(String operation) { return true; }
    protected void authorizeReceipt(String operation,E entity) { }
    protected void afterChange(String operation,E before,E current) { }
    protected Class<?>[] validationGroups(E entity) { return new Class<?>[]{jakarta.validation.groups.Default.class}; }
    protected void beforeCreate(E entity) { }
    protected void afterCreate(E entity) { }
    protected void beforeUpdate(E current, E proposed) { }
    protected void afterUpdate(E before, E current) { }
    protected void requireFrameworkDelete(E current) { }
    protected void validateFramework(E entity,String operation) { }
    protected void beforeDelete(E current) { }
    protected void afterDelete(E deleted) { }
    protected void validateBusiness(E entity) { }
    protected void validateBusiness(E entity,String operation) { validateBusiness(entity); }
    protected void validateDelivery(E entity, boolean write) { }

    @Override public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi deliveryApi() {
        return Objects.requireNonNull(defaults.deliveries().get(), "Shared delivery API is unavailable");
    }
    @Override public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope deliveryScope(Long id, String type) {
        E row = get(id);
        return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope(
                row.getProjectId(), definition().stableCode(), id.toString(), type);
    }
    @Override public final Long requireDeliveryAccess(Long id, boolean write, boolean lock) {
        var actor = actor();
        if (write) defaults.permissions().requireWritable(definition(), actor, "operation:save");
        else authorizeModelRead(actor, "delivery");
        E row = current(id, actor);
        if (write) defaults.projects().requireWritable(row.getProjectId(), actor, lock);
        else defaults.projects().requireReadable(row.getProjectId(), actor);
        if (lock) {
            E locked = lock(id, actor);
            if (!Objects.equals(row.getProjectId(), locked.getProjectId()) || !Objects.equals(row.getVersion(), locked.getVersion()))
                throw invalid("CONCURRENCY_CONFLICT", "Business changed before delivery access");
            row = locked;
        }
        validateDelivery(copy(row), write); return row.getProjectId();
    }
    @Override public final BusinessOperationReceipt receipt(String operation, String key) {
        requireOperation(operation); requireKey(key);
        return defaults.transactions().execute(status -> {
            var actor = actor(); defaults.permissions().requireWritable(definition(), actor, "operation:" + operation);
            var existing = defaults.journal().findExisting(journalKey(actor, key), payload -> JsonUtils.parseObject(payload, BusinessOperationReceipt.class)).orElse(null);
            if (existing == null || !"COMPLETED".equals(existing.status()) || existing.receipt() == null) return null;
            requireReceiptAccess(existing.receipt(), operation, actor); return existing.receipt();
        });
    }
    protected final BusinessOperationReceipt write(String operation, Long id, Long version, Map<String,Object> input,
            String key, Function<EntityActor,BusinessOperationReceipt> action) {
        requireKey(key);
        if (id != null && (id <= 0 || version == null || version < 0)) throw invalid("CONCURRENCY_BASIS_REQUIRED", "Update/delete require identity and version");
        requireOperation(operation);
        String digest = digest(Arrays.asList(operation, operationVersion(operation), id, version, input));
        return defaults.transactions().execute(status -> {
            BusinessEntitySaveSupport.requireTransaction(); var actor = actor();
            defaults.permissions().requireWritable(definition(), actor, "operation:" + operation);
            var journalKey = journalKey(actor, key);
            if (!defaults.journal().reserve(journalKey, digest)) {
                var prior = defaults.journal().findExisting(journalKey, payload -> JsonUtils.parseObject(payload, BusinessOperationReceipt.class))
                        .orElseThrow(() -> invalid("OPERATION_IN_PROGRESS", "Business operation is still in progress"));
                if (!digest.equals(prior.requestDigest())) throw invalid("IDEMPOTENCY_DIGEST_CONFLICT", "Key belongs to a different business intent");
                if (!"COMPLETED".equals(prior.status()) || prior.receipt() == null) throw invalid("OPERATION_IN_PROGRESS", "Business operation is still in progress");
                requireReceiptAccess(prior.receipt(), operation, actor); return prior.receipt();
            }
            var result = action.apply(actor);
            defaults.journal().complete(journalKey, definition().entityType(), result.entityRef().entityId().toString(), result);
            defaults.audit().record(actor.tenantId(), actor.userId(), key, operation, definition().entityType(),
                    result.entityRef().entityId().toString(), "SUCCESS", Map.of("outcome", result.outcome().name()));
            if(publishDefaultEvent(operation))defaults.events().append(new BusinessEventRecord(UUID.randomUUID().toString(), result.entityRef(), BusinessEventKind.CHANGED,
                    result.newConcurrencyBasis().toString(), key, Map.of("operation", operation, "operationVersion", operationVersion(operation)), 0L));
            return result;
        });
    }
    private void requireReceiptAccess(BusinessOperationReceipt receipt, String operation, EntityActor actor) {
        if (!operation.equals(receipt.operationCode()) || !Integer.valueOf(operationVersion(operation)).equals(receipt.operationVersion()) || receipt.entityRef() == null
                || !actor.tenantId().equals(receipt.entityRef().tenantId()) || !definition().ownerModule().equals(receipt.entityRef().ownerModule())
                || !definition().entityType().equals(receipt.entityRef().entityType())) throw invalid("RECEIPT_IDENTITY_INVALID", "Receipt belongs to a different business");
        E row = receipt.outcome() == ReceiptOutcome.DELETED
                ? binding.type.cast(DeclaredBusinessCurrentRows.lockDeletedForReceipt(binding.mapping, new DeclaredCurrentRowQuery(actor.tenantId(), receipt.entityRef().entityId())))
                : current(receipt.entityRef().entityId(), actor);
        checkRow(row, receipt.entityRef().entityId(), actor);
        defaults.projects().requireWritable(row.getProjectId(), actor, false);authorizeReceipt(operation,copy(row));
    }
    private OperationExecutionStore.OperationExecutionKey journalKey(EntityActor actor, String key) {
        return new OperationExecutionStore.OperationExecutionKey(actor.tenantId(), "crud:" + definition().ownerModule() + "/" + definition().entityType(), actor.userId(), key);
    }
    protected final E current(Long id, EntityActor actor) { E row = mapper.selectById(id); checkRow(row,id,actor);afterRead(row);return row; }
    protected final E lock(Long id, EntityActor actor) {
        E row = binding.type.cast(DeclaredBusinessCurrentRows.lock(binding.mapping, new DeclaredCurrentRowQuery(actor.tenantId(), id)));
        checkRow(row,id,actor);afterRead(row);return row;
    }
    private void checkRow(E row, Long id, EntityActor actor) {
        if (row == null || !binding.type.isInstance(row) || id == null || id <= 0 || !id.equals(row.getId()) || !actor.tenantId().equals(row.getTenantId()) || row.getProjectId() == null || row.getProjectId() <= 0)
            throw invalid("ENTITY_NOT_FOUND", "Business is unavailable in the current tenant");
    }
    private void checkIdentity(E proposed, E before) {
        if (!Objects.equals(proposed.getId(), before.getId()) || !Objects.equals(proposed.getTenantId(), before.getTenantId()) || !Objects.equals(proposed.getVersion(), before.getVersion()))
            throw invalid("CONTROL_FIELD_CHANGED", "Business hook cannot change identity or concurrency basis");
    }
    private void requireProject(E entity) {
        if (entity.getProjectId() == null || entity.getProjectId() <= 0) throw invalid("PROJECT_REQUIRED", "Business project is required");
    }
    protected final void requireVersion(E row, Long version) {
        if (version == null || !version.equals(row.getVersion())) throw invalid("CONCURRENCY_CONFLICT", "Business concurrency basis is stale");
    }
    protected final void validate(E entity,String operation) {
        var violations = defaults.validator().validate(entity,validationGroups(entity));
        if (!violations.isEmpty()) throw invalid("ENTITY_CONSTRAINT_INVALID", violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList().toString());
        validateFramework(copy(entity),operation);validateBusiness(copy(entity),operation);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected final void persistUpdate(E entity, EntityActor actor, Long version, Set<String> nulls) {
        entity.setUpdater(actor.userId().toString());entity.setUpdateTime(java.time.LocalDateTime.now());
        DeclaredBusinessEntityWriter.update((com.baomidou.mybatisplus.core.mapper.BaseMapper) mapper, entity, actor.tenantId(), version, nulls);
    }
    private Map<String,Object> inputValues(E source, Set<String> selected) {
        if (source == null) throw invalid("INPUT_REQUIRED", "Business data is required");
        var all = binding.values(source); var values = new LinkedHashMap<String,Object>();
        if (selected == null) definition().fields().stream().filter(f -> f.writable()).forEach(f -> values.put(f.code(), all.get(f.code())));
        else selected.forEach(name -> { if (!all.containsKey(name)) throw invalid("FIELD_NOT_OPEN", "Unknown business field"); values.put(name,all.get(name)); });
        binding.create(values); // validate the same field/type whitelist for typed service callers
        return Collections.unmodifiableMap(values);
    }
    protected final E copy(E source) { return BusinessEntityCopies.copy(source); }
    protected final EntityActor actor() { var caller = defaults.callers().require(); return new EntityActor(caller.tenantId(), caller.userId(), caller.entryCorrelationId()); }
    protected final EntityRef identity(E entity) { return new EntityRef(entity.getTenantId(), definition().ownerModule(), definition().entityType(), entity.getId()); }
    private BusinessOperationReceipt saved(E entity, String operation, ReceiptOutcome outcome) {
        return new BusinessOperationReceipt(outcome, identity(entity), entity.getVersion(), List.of(), null, null, operation, operationVersion(operation));
    }
    private int operationVersion(String code) {
        return definition().operations().stream().filter(operation -> operation.code().equals(code)).findFirst()
                .orElseThrow(() -> invalid("OPERATION_UNKNOWN", "Unknown business operation")).version();
    }
    private void requireOperation(String operation) { operationVersion(operation); }
    private void requireKey(String key) { if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,128}")) throw invalid("IDEMPOTENCY_KEY_REQUIRED", "Business operation requires an idempotency key"); }
    private static String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(canonical(value)).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static Object canonical(Object value) {
        // Arrays of key/value pairs preserve explicit nulls even when the shared mapper omits null map entries.
        if (value instanceof Map<?,?> map) return map.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(e -> Arrays.asList(e.getKey().toString(), canonical(e.getValue()))).toList();
        if (value instanceof List<?> list) return list.stream().map(DefaultProjectBusinessService::canonical).toList();
        return value;
    }
    private static BusinessContractException invalid(String code, String message) { return new BusinessContractException(code,message); }
}
