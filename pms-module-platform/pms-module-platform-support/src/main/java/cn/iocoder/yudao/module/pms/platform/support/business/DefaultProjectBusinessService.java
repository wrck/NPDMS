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
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
        implements ProjectBusinessService<E>, EntityFieldProvider, cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessObjectPolicyProvider {
    @Autowired protected M mapper;
    @Autowired protected BusinessDefaults defaults;
    private BusinessEntityBinding<E> binding;
    @Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi> fieldConfigurations;
    @Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi> dynamicForms;
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
    /** Configuration metadata only. A persisted plain record is a save result, not an approval or task transition. */
    protected String runtimeNativeObjectType(){return binding.mapping.nativeEntityType();}
    protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存");}
    protected Map<String,Boolean> runtimeFacts(E row){return Map.of("BUSINESS_RECORD_SAVED",true);}
    protected boolean runtimeHandlingCompleted(E row){return true;}
    protected java.time.LocalDateTime runtimeFormedAt(E row){return row.getUpdateTime()==null?row.getCreateTime():row.getUpdateTime();}
    protected record RuntimeResult(Map<String,Boolean> facts,boolean completed,java.time.LocalDateTime formedAt) { }
    protected RuntimeResult runtimeResult(E row){return new RuntimeResult(runtimeFacts(row),runtimeHandlingCompleted(row),runtimeFormedAt(row));}
    @Override public final ProjectBusinessRuntimeApi.Definition runtimeDefinition(){
        return new ProjectBusinessRuntimeApi.Definition(new ProjectBusinessRuntimeApi.Type(ownerModule(),entityType()),definition().stableCode(),definition().title(),runtimeFactLabels(),runtimeNativeObjectType());
    }
    private void requireRuntimeScope(Long tenant,Long project,ProjectBusinessRuntimeApi.Type type){
        if(tenant==null || !Objects.equals(tenant,TenantContextHolder.getTenantId()) || project==null || project<=0
                || type==null || !ownerModule().equals(type.ownerModule()) || !entityType().equals(type.entityType()))
            throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime identity differs from the bound business");
    }
    private ProjectBusinessRuntimeApi.Observation runtimeObservation(E row){
        if(row.getVersion()==null || row.getVersion()<0)throw invalid("BUSINESS_RUNTIME_VERSION_INVALID","Missing runtime concurrency basis");
        var result=runtimeResult(copy(row));var facts=result.facts();
        if(facts==null || !runtimeFactLabels().keySet().containsAll(facts.keySet()))throw invalid("BUSINESS_RUNTIME_FACT_INVALID","Undeclared runtime fact");
        return new ProjectBusinessRuntimeApi.Observation(row.getId(),definition().stableCode()+":"+row.getId()+":"+row.getVersion(),facts,result.completed(),result.formedAt());
    }
    @Override public final ProjectBusinessRuntimeApi.Observation runtimeObservation(ProjectBusinessRuntimeApi.Query query,boolean lock){
        requireRuntimeScope(query.tenantId(),query.projectId(),query.type());
        if(query.entityId()==null || query.entityId()<=0)throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime entity is required");
        if(lock)BusinessEntitySaveSupport.requireTransaction();
        E row=lock?binding.type.cast(DeclaredBusinessCurrentRows.lock(binding.mapping,new DeclaredCurrentRowQuery(query.tenantId(),query.entityId()))):mapper.selectById(query.entityId());
        checkRow(row,query.entityId(),new EntityActor(query.tenantId(),0L,EntityActor.SYSTEM_OBSERVER));
        if(Boolean.TRUE.equals(row.getDeleted()) || !query.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime business project differs");
        // Deliberately expose boolean facts only. Do not impersonate a user to read private draft form content.
        return runtimeObservation(row);
    }
    @Override public final List<ProjectBusinessRuntimeApi.Reference> runtimeCandidates(ProjectBusinessRuntimeApi.Candidates query){
        requireRuntimeScope(query.tenantId(),query.projectId(),query.type());
        return selectRuntimeCandidates(new BusinessMapper.RuntimeCandidates(query.tenantId(),query.projectId(),query.afterId(),query.limit())).stream().map(row->{
            if(Boolean.TRUE.equals(row.getDeleted()) || !query.tenantId().equals(row.getTenantId()) || !query.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Foreign runtime candidate");
            return new ProjectBusinessRuntimeApi.Reference(row.getId(),runtimeObservation(row).factVersion());
        }).toList();
    }
    protected List<E> selectRuntimeCandidates(BusinessMapper.RuntimeCandidates query){return mapper.selectRuntimeCandidates(query);}
    @Override public final Set<String> runtimeActions(ProjectBusinessRuntimeApi.UserContext context){
        var caller=actor();requireRuntimeScope(context.tenantId(),context.projectId(),context.type());
        if(!Objects.equals(caller.tenantId(),context.tenantId()) || !Objects.equals(caller.userId(),context.userId()))throw invalid("ACCESS_DENIED","Runtime view actor differs");
        defaults.projects().requireReadable(context.projectId(),caller);var model=model();var actions=new HashSet<String>();actions.add("QUERY");
        for(var operation:model.operations())if(operation.executable())actions.add(operation.code());
        return Set.copyOf(actions);
    }
    @Override public final ProjectBusinessRuntimeApi.Observation runtimeForUser(ProjectBusinessRuntimeApi.UserContext context,Long id,boolean lock,String expectedVersion){
        runtimeActions(context);var row=get(id);
        if(!context.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime view project differs");
        if(lock){BusinessEntitySaveSupport.requireTransaction();row=lock(id,actor());if(!context.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime view project changed");}
        var observed=runtimeObservation(row);
        if(lock && !Objects.equals(expectedVersion,observed.factVersion()))throw invalid("CONCURRENCY_CONFLICT","Runtime fact changed");
        return observed;
    }
    @Override public final BusinessModelDescriptor definition() { return binding.mapping.descriptor(); }
    @Override public final BusinessModelViews.ModelDetailVO model() {
        var baseline=configurationModel(false);
        return BusinessFieldConfigurations.apply(baseline,fieldConfiguration());
    }
    @Override public final BusinessModelViews.ModelDetailVO configurationModel(boolean write) {
        var actor=actor();authorizeModelRead(actor,"detail");
        if(write)defaults.permissions().requireWritable(definition(),actor,"operation:configure-fields");
        return modelView(BusinessModelViews.ModelDetailVO.of(definition(),actor,defaults.permissions()));
    }
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Identity configurationIdentity(){
        return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Identity(definition().ownerModule(),definition().entityType());
    }
    @Override public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration fieldConfiguration(){
        configurationModel(false);var api=fieldConfigurations.getIfAvailable();
        return api==null?new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(0,List.of()):api.read(configurationIdentity(),actor());
    }
    @Override public final cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration saveFieldConfiguration(long version,List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field> fields){
        BusinessFieldConfigurations.validate(configurationModel(true),fields);
        return fieldConfigurations.getObject().save(configurationIdentity(),actor(),version,fields);
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
        var configured=model();
        if(query.getFilters()!=null)for(var filter:query.getFilters()){
            if(filter==null || configured.fields().stream().noneMatch(field->field.code().equals(filter.fieldCode())&&field.searchable()))throw invalid("FILTER_FIELD_FORBIDDEN","Field is not configured for querying");
        }
        if(query.getSorts()!=null)for(var sort:query.getSorts()){
            if(sort==null || configured.fields().stream().noneMatch(field->field.code().equals(sort.fieldCode())&&field.sortable()))throw invalid("SORT_FIELD_FORBIDDEN","Field is not configured for sorting");
        }
        var projects = defaults.projects().readableScopeIds(actor);
        if (projects == null || projects.isEmpty()) return new PageResult<>(List.of(), 0L);
        if(query.getProjectId()!=null){
            if(query.getProjectId()<=0)throw invalid("QUERY_INVALID","Invalid project identity");
            if(!projects.contains(query.getProjectId()))return new PageResult<>(List.of(),0L);
            projects=Set.of(query.getProjectId());
        }
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
        return createRecord(values,null,values,key);
    }
    @Override public final BusinessOperationReceipt createForm(Map<String,Object> values,String key){
        var form=formWrite(values);var fixed=inputValues(binding.create(form.fixed()),null);
        var intent=new LinkedHashMap<String,Object>(fixed);
        for(String part:List.of("$binding","$extensions","$business"))if(values.containsKey(part))intent.put(part,values.get(part));
        return createRecord(fixed,form,intent,key);
    }
    private BusinessOperationReceipt createRecord(Map<String,Object> values,FormWrite form,Map<String,Object> intent,String key){
        return write("create", null, null, intent, key, actor -> {
            E entity = binding.create(values);
            Long generated=generatedId(),project=entity.getProjectId();long initial=initialVersion();
            if(initial<0 || generated!=null && generated<=0)throw invalid("CONTROL_FIELD_CHANGED","Invalid generated identity");
            entity.setId(generated);entity.setTenantId(actor.tenantId());entity.setVersion(initial);
            requireProject(entity);defaults.projects().requireWritable(entity.getProjectId(), actor, true);
            return inBusinessOperation("create", null, entity, () -> {
            beforeCreate(entity);
            if(form!=null)beforeFormWrite(null,entity,form.business());
            if (!Objects.equals(project,entity.getProjectId()) || !Objects.equals(generated,entity.getId()) || !actor.tenantId().equals(entity.getTenantId()) || !Long.valueOf(initial).equals(entity.getVersion()))
                throw invalid("CONTROL_FIELD_CHANGED", "Initialization changed business identity");
            requireProject(entity); defaults.projects().requireWritable(entity.getProjectId(), actor, true);
            validate(entity,"create");
            if (mapper.insert(entity) != 1 || entity.getId() == null) throw invalid("ENTITY_INSERT_FAILED", "Business insert did not return one persisted identity");
            afterCreate(copy(entity));
            if(form!=null && (form.binding()!=null || form.extension()!=null)){
                storeFormWrite(formTarget(entity),entity.getVersion(),form);
                validateConfiguredForm(entity,"save-form");
            }
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
    @Override public cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey providerKey(){
        return new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey(ownerModule(),entityType());
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact inspectRevisionCompatibility(
            cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionPolicyQuery query){
        var caller=actor();var action=query.action();
        boolean allowed=Objects.equals(query.tenantId(),caller.tenantId()) && Objects.equals(query.actorUserId(),caller.userId())
                && providerKey().equals(query.providerKey()) && formUsage().equals(query.requiredUsage())
                && query.revisionFactVersion()!=null && query.revisionFactVersion()>=0
                && (action==cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction.REVISION_BINDING_PUBLISH
                    || action==cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction.REVISION_FROZEN_USE)
                && query.fields().stream().allMatch(field->!field.controlledFile() && field.valueType()!=null && Set.of("any","number","boolean","array").contains(field.valueType()));
        // Content belongs to the real entity/revision. Files belong to the shared delivery component, not a synthetic form instance.
        return new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact(action,allowed,allowed?null:"BUSINESS_FORM_INCOMPATIBLE",
                query.revisionFactVersion()==null?0L:query.revisionFactVersion().longValue(),"DEFAULT_ENTITY_FIELDS");
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact inspectInstanceOwnerPolicy(
            cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePolicyQuery query){
        return new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact(query.action(),false,"ENTITY_FORM_HAS_NO_SEPARATE_INSTANCE",0L,"ENTITY_ONLY");
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(
            cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyRevalidationQuery query){
        return new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact(query.expectedFact().action(),false,"ENTITY_FORM_HAS_NO_SEPARATE_INSTANCE",0L,"ENTITY_ONLY");
    }
    private void validateConfiguredForm(E row,String operation){
        if("create".equals(operation) || row.getId()==null)return;
        var formApi=formPorts.getIfAvailable();if(formApi==null)return;var target=formTarget(row);var layout=formApi.layout(target,actor());if(layout==null)return;
        var port=dynamicForms.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Published form validation is unavailable");
        var fixed=businessValues(row);var extras=extensionPort().read(target,actor()).fields();var values=new LinkedHashMap<String,Object>();
        layout.binding().fieldBindings().forEach((field,property)->{if(fixed.containsKey(property))values.put(field,fixed.get(property));else if(extras.containsKey(property))values.put(field,extras.get(property));});
        var query=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionUsageQuery(actor().tenantId(),actor().userId(),
                new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey(target.entity().ownerModule(),target.entity().entityType()),
                layout.binding().formRevisionId(),configuredFormUsage(row,target),cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction.REVISION_FROZEN_USE,layout.formVersion());
        @SuppressWarnings("unchecked") Map<String,Object> normalized=JsonUtils.parseObject(JsonUtils.toJsonString(values),Map.class);
        var result=port.validateRevisionValues(new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionValuesQuery(query,normalized));
        if(result==null || !"VALID".equals(result.result()))throw invalid("BUSINESS_FORM_INVALID","Configured form validation failed");
    }
    protected String configuredFormUsage(E row,EntityDataRef target){return target.entity().entityType();}
    /** Existing revision businesses override only their historical storage identity. */
    protected EntityDataRef formTarget(E row){return EntityDataRef.current(identity(row));}
    private EntityFormApi formPort(){var port=formPorts.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Form capability is unavailable");return port;}
    private EntityExtensionApi extensionPort(){var port=extensionPorts.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Extension capability is unavailable");return port;}
    protected Map<String,Object> formContext(E row){return Map.of();}
    protected BusinessFormData defaultForm(Long projectId){return new BusinessFormData(null,new EntityExtensionApi.Values(null,Map.of(),0),List.of());}
    @Override public final BusinessFormData formDefaults(Long projectId){
        var caller=actor();authorizeModelRead(caller,"form");
        if(projectId==null || projectId<=0)throw invalid("PROJECT_REQUIRED","Business project is required");
        defaults.projects().requireReadable(projectId,caller);return defaultForm(projectId);
    }
    /** Only an explicit business override can accept domain command inputs alongside form content. */
    protected void beforeFormWrite(E before,E proposed,Map<String,Object> business){
        if(!business.isEmpty())throw invalid("FIELD_NOT_OPEN","Business form commands are not declared");
    }
    @Override public final BusinessFormData form(Long id){
        var row=get(id);var target=formTarget(row);var caller=actor();var values=extensionPort().read(target,caller);var layout=formPort().layout(target,caller);
        var definitionId=layout!=null && layout.binding().extensionDefinitionRevisionId()!=null?layout.binding().extensionDefinitionRevisionId():values.definitionRevisionId();
        return new BusinessFormData(layout,values,definitionId==null?List.of():extensionPort().definition(definitionId,target.entity(),caller).fields(),formContext(copy(row)));
    }
    protected record FormWrite(Map<String,Object> fixed,BusinessFormData.BindingPatch binding,BusinessEntitySaveSupport.ExtensionPatch extension,Map<String,Object> business){}
    protected final FormWrite formWrite(Map<String,Object> values){
        if(values==null)throw invalid("INPUT_REQUIRED","Business values are required");
        var fixed=new LinkedHashMap<>(values);Object extension=fixed.remove("$extensions"),layout=fixed.remove("$binding"),business=fixed.remove("$business");
        if(business!=null && !(business instanceof Map<?,?>))throw invalid("INPUT_INVALID","Invalid business form commands");
        @SuppressWarnings("unchecked") var commands=business==null?Map.<String,Object>of():new LinkedHashMap<>((Map<String,Object>)business);
        return new FormWrite(fixed,layout==null?null:JsonUtils.parseObject(JsonUtils.toJsonString(layout),BusinessFormData.BindingPatch.class),
                extension==null?null:JsonUtils.parseObject(JsonUtils.toJsonString(extension),BusinessEntitySaveSupport.ExtensionPatch.class),commands);
    }
    protected final void storeFormWrite(EntityDataRef target,Long version,FormWrite input){
        var layout=input.binding();EntityFormApi.Binding bound=null;
        if(layout!=null){
            if(layout.expectedVersion()<0 || layout.formRevisionId()==null || layout.formRevisionId()<=0)throw invalid("INPUT_INVALID","Invalid form binding");
            bound=formPort().bind(new EntityFormApi.Bind(target,actor(),version,layout.expectedVersion(),layout.formRevisionId(),layout.extensionDefinitionRevisionId(),layout.fieldBindings(),layout.bindRemainingFields()));
        }
        var patch=input.extension();if(patch==null)return;
        if(patch.values()==null || patch.expectedVersion()<0)throw invalid("INPUT_INVALID","Invalid extension patch");
        var existing=extensionPort().read(target,actor());var definition=patch.definitionRevisionId()!=null?patch.definitionRevisionId():bound!=null?bound.extensionDefinitionRevisionId():existing.definitionRevisionId();
        var merged=new LinkedHashMap<String,Object>();if(Objects.equals(existing.definitionRevisionId(),definition))merged.putAll(existing.fields());merged.putAll(patch.values());
        // Lossless text number controls are converted using the published definition, never arbitrary property names.
        var schema=extensionPort().definition(definition,target.entity(),actor());
        for(var field:schema.fields())if(field.type()==EntityField.Type.NUMBER && merged.get(field.code()) instanceof String text){
            try{merged.put(field.code(),text.isBlank()?null:new java.math.BigDecimal(text));}catch(NumberFormatException invalid){throw invalid("INPUT_INVALID","Invalid number for extension: "+field.code());}
        }
        extensionPort().save(new EntityExtensionApi.Save(target,actor(),version,patch.expectedVersion(),definition,merged));
    }
    @Override public final BusinessOperationReceipt saveForm(Long id,Map<String,Object> values,Long version,String key){
        var input=formWrite(values);
        return change("save-form",id,version,key,values,row->{var before=copy(row);binding.patch(row,input.fixed());beforeFormWrite(before,row,input.business());beforeUpdate(before,row);storeFormWrite(formTarget(row),version,input);});
    }

    protected List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor> defaultOperations(String prefix){
        return List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("save-form",1,"保存表单",
                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update"),
                new cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor("configure-fields",1,"字段配置",
                cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor.StandardOperationKind.CONFIGURE,prefix+":configure"));
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
            if(publishDefaultEvent(operation)) {
                // Use the persisted identity, including soft-deleted rows, never a project ID from request input.
                E persisted=result.outcome()==ReceiptOutcome.DELETED
                        ? binding.type.cast(DeclaredBusinessCurrentRows.lockDeletedForReceipt(binding.mapping,
                            new DeclaredCurrentRowQuery(actor.tenantId(),result.entityRef().entityId())))
                        : current(result.entityRef().entityId(),actor);
                checkRow(persisted,result.entityRef().entityId(),actor);
                defaults.events().append(new BusinessEventRecord(UUID.randomUUID().toString(), result.entityRef(), BusinessEventKind.CHANGED,
                        result.newConcurrencyBasis().toString(), key, Map.of("operation", operation, "operationVersion", operationVersion(operation)), 0L,
                        new BusinessEventRecord.ProjectChange(persisted.getProjectId(),actor.userId())));
            }
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
        validateFramework(copy(entity),operation);validateBusiness(copy(entity),operation);validateConfiguredForm(entity,operation);
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
