package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
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
 * Public methods remain proxyable for transactional business overrides; protected write templates retain the guards.
 */
public abstract class DefaultProjectBusinessService<M extends BusinessMapper<E>, E extends BaseProjectBusinessEntity>
        implements ProjectBusinessService<E>, EntityFieldProvider, cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessObjectPolicyProvider {
    @Autowired protected M mapper;
    @Autowired protected BusinessDefaults defaults;
    private BusinessEntityBinding<E> binding;
    @Autowired private org.springframework.beans.factory.BeanFactory businessBeans;
    private final Map<String,OwnedBusinessDetails> ownedDetails=new LinkedHashMap<>();
    @Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi> fieldConfigurations;
    @Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi> dynamicForms;
    @Autowired private org.springframework.beans.factory.ObjectProvider<EntityFormApi> formPorts;
    @Autowired private org.springframework.beans.factory.ObjectProvider<EntityPresentationApi> presentationPorts;
    @Autowired private org.springframework.beans.factory.ObjectProvider<EntityExtensionApi> extensionPorts;
    @Autowired private org.springframework.beans.factory.ObjectProvider<BusinessConditionEvidenceApi> conditionEvidence;
    private BusinessCondition completionCondition;
    private record FormReadAccess(EntityDataRef target,EntityActor actor) {}
    private final ThreadLocal<FormReadAccess> formReadAccess=new ThreadLocal<>();
    private final ThreadLocal<InitialPageRead> initialPageRead=new ThreadLocal<>();
    /** Exists only while this service assembles one read; commands never use these decisions. */
    private final class InitialPageRead {
        final EntityActor actor;
        final BusinessPageQueryApi.Request request;
        final BusinessAccessGuard permissions;
        final Set<String> readScenes=new HashSet<>();
        final Set<Long> projects=new HashSet<>();
        final Map<Long,E> rows=new LinkedHashMap<>();
        final Map<String,BusinessModelViews.ModelDetailVO> models=new HashMap<>();
        final Map<Long,BusinessFormData> forms=new HashMap<>();
        BusinessModelViews.ModelDetailVO baseline;
        BusinessFieldConfigurationApi.Configuration configuration;
        List<E> pageRows=List.of();
        boolean completeProjectRows;
        List<BusinessPageQueryApi.Page> related=List.of();
        InitialPageRead(EntityActor actor,BusinessPageQueryApi.Request request) {
            this.actor=actor;this.request=request;
            this.permissions=defaults.permissions() instanceof BusinessPermissions inherited
                    ?inherited.forPageRead():defaults.permissions();
        }
    }
    @Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalApi> approvals;

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
        if(!identity.completionCondition().isBlank())completionCondition=JsonUtils.parseObject(identity.completionCondition(),BusinessCondition.class);
        for(var field:binding.fields)if(field.property().isAnnotationPresent(BusinessOwnedDetails.class))
            ownedDetails.put(field.code(),new OwnedBusinessDetails(field.property(),businessBeans));
    }
    /** Configuration metadata only. A persisted plain record is a save result, not an approval or task transition. */
    protected String runtimeNativeObjectType(){return binding.mapping.nativeEntityType();}
    protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存");}
    protected Map<String,Boolean> runtimeFacts(E row){return Map.of("BUSINESS_RECORD_SAVED",true);}
    protected boolean runtimeHandlingCompleted(E row){return false;}
    protected java.time.LocalDateTime runtimeFormedAt(E row){return row.getUpdateTime()==null?row.getCreateTime():row.getUpdateTime();}
    protected record RuntimeResult(Map<String,Boolean> facts,boolean completed,java.time.LocalDateTime formedAt,String sourceVersion) {
        public RuntimeResult { }
        public RuntimeResult(Map<String,Boolean> facts,boolean completed,java.time.LocalDateTime formedAt){this(facts,completed,formedAt,null);}
    }
    protected RuntimeResult runtimeResult(E row){return new RuntimeResult(runtimeFacts(row),runtimeHandlingCompleted(row),runtimeFormedAt(row));}
    @Override public ProjectBusinessRuntimeApi.Definition runtimeDefinition(){
        var labels=new LinkedHashMap<>(runtimeFactLabels());
        if(completionCondition!=null)labels.put("BUSINESS_CONDITION_SATISFIED","已满足业务完成条件");
        return new ProjectBusinessRuntimeApi.Definition(new ProjectBusinessRuntimeApi.Type(ownerModule(),entityType()),definition().stableCode(),definition().title(),labels,runtimeNativeObjectType());
    }
    private void requireRuntimeScope(Long tenant,Long project,ProjectBusinessRuntimeApi.Type type){
        if(tenant==null || !Objects.equals(tenant,TenantContextHolder.getTenantId()) || project==null || project<=0
                || type==null || !ownerModule().equals(type.ownerModule()) || !entityType().equals(type.entityType()))
            throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime identity differs from the bound business");
    }
    private ProjectBusinessRuntimeApi.Observation runtimeObservation(E row){return runtimeObservation(row,false);}
    private ProjectBusinessRuntimeApi.Observation runtimeObservation(E row,boolean lock){
        if(row.getVersion()==null || row.getVersion()<0)throw invalid("BUSINESS_RUNTIME_VERSION_INVALID","Missing runtime concurrency basis");
        var result=runtimeResult(copy(row));var facts=result.facts();
        if(facts==null || !runtimeFactLabels().keySet().containsAll(facts.keySet()))throw invalid("BUSINESS_RUNTIME_FACT_INVALID","Undeclared runtime fact");
        String version=definition().stableCode()+":"+row.getId()+":"+row.getVersion();boolean completed=result.completed();
        if(completionCondition!=null){
            var read=initialRead();if(read==null || !read.rows.containsKey(row.getId()))loadOwnedDetails(row);
            var evaluated=evaluateRow(row,completionCondition,lock);
            facts=new LinkedHashMap<>(facts);
            if(evaluated.verdict().satisfied()!=null)facts.put("BUSINESS_CONDITION_SATISFIED",evaluated.verdict().satisfied());
            completed=Boolean.TRUE.equals(evaluated.verdict().satisfied());version=evaluated.factVersion();
        }
        if(result.sourceVersion()!=null)version += ":"+result.sourceVersion();
        return new ProjectBusinessRuntimeApi.Observation(row.getId(),version,facts,completed,result.formedAt());
    }
    @Override public ProjectBusinessRuntimeApi.Observation runtimeObservation(ProjectBusinessRuntimeApi.Query query,boolean lock){
        requireRuntimeScope(query.tenantId(),query.projectId(),query.type());
        if(query.entityId()==null || query.entityId()<=0)throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime entity is required");
        if(lock)BusinessEntitySaveSupport.requireTransaction();
        E row=lock?binding.type.cast(DeclaredBusinessCurrentRows.lock(binding.mapping,new DeclaredCurrentRowQuery(query.tenantId(),query.entityId()))):mapper.selectById(query.entityId());
        checkRow(row,query.entityId(),new EntityActor(query.tenantId(),0L,EntityActor.SYSTEM_OBSERVER));
        if(Boolean.TRUE.equals(row.getDeleted()) || !query.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime business project differs");
        // Only verdicts leave this internal observation; the common evidence port never impersonates a user.
        return runtimeObservation(row,lock);
    }
    @Override public EntityDataRef requireEvidenceScope(EntityRef entity,Long projectId,boolean lock){
        requireRuntimeScope(entity.tenantId(),projectId,new ProjectBusinessRuntimeApi.Type(entity.ownerModule(),entity.entityType()));
        if(lock)BusinessEntitySaveSupport.requireTransaction();
        var read=initialRead();
        E row=lock?binding.type.cast(DeclaredBusinessCurrentRows.lock(binding.mapping,new DeclaredCurrentRowQuery(entity.tenantId(),entity.entityId())))
                :read!=null && read.rows.containsKey(entity.entityId())?read.rows.get(entity.entityId()):mapper.selectById(entity.entityId());
        checkRow(row,entity.entityId(),new EntityActor(entity.tenantId(),0L,EntityActor.SYSTEM_OBSERVER));
        if(Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(projectId,row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Evidence business project differs");
        return formTarget(row);
    }
    @Override public BusinessConditionResult evaluate(Long id,BusinessCondition condition){
        return defaults.transactions().execute(status->evaluateRow(get(id),condition,false));
    }
    @Override public BusinessConditionResult completion(Long id){
        return defaults.transactions().execute(status->{var row=get(id);return completionCondition==null?null:evaluateRow(row,completionCondition,false);});
    }
    private BusinessConditionResult evaluateRow(E row,BusinessCondition condition,boolean lock){
        String basis=definition().stableCode()+":"+row.getId()+":"+row.getVersion();
        var facts=conditionFacts(readableValues(row),basis);
        var extensions=new HashSet<String>();var deliveries=new HashSet<String>();
        conditionReferences(condition,facts.keySet(),extensions,deliveries,0);
        String evidenceVersion="";
        if(!extensions.isEmpty() || !deliveries.isEmpty()){
            var evidence=conditionEvidence.getObject().inspect(new BusinessConditionEvidenceApi.Query(
                    identity(row),row.getProjectId(),extensions,deliveries,lock));
            facts.putAll(evidence.facts());evidenceVersion=evidence.version();
        }
        String version;
        try{version=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((basis+"\n"+evidenceVersion+"\n"+JsonUtils.toJsonString(facts)).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        return new BusinessConditionResult(BusinessConditionEvaluator.evaluate(condition,facts,"BUSINESS_CONDITION/1"),version);
    }
    private static Map<String,FactObservation> conditionFacts(Map<String,Object> values,String basis){
        var facts=new TreeMap<String,FactObservation>();
        values.forEach((code,raw)->{
            Object value=raw;
            if(raw instanceof List<?> rows && rows.stream().allMatch(Map.class::isInstance))value=rows.stream().map(item->{
                @SuppressWarnings("unchecked") var member=(Map<String,Object>)item;return conditionFacts(member,basis);
            }).toList();
            facts.put(code,new FactObservation(code,value==null?FactObservation.Availability.EMPTY:FactObservation.Availability.VALUE,value,basis,Completeness.COMPLETE));
        });return facts;
    }
    private static void conditionReferences(BusinessCondition condition,Set<String> fixed,Set<String> extensions,Set<String> deliveries,int depth){
        if(condition==null || condition.kind()==null || depth>32)throw invalid("BUSINESS_CONDITION_INVALID","Invalid condition");
        switch(condition.kind()){
            case DELIVERY -> {if(condition.fieldCode()==null || !condition.fieldCode().matches("[A-Za-z0-9_.:-]{1,64}"))throw invalid("BUSINESS_CONDITION_INVALID","Invalid delivery type");deliveries.add(condition.fieldCode());}
            case FIELD,MEMBER_ALL,MEMBER_ANY,COUNT -> {if(condition.fieldCode()==null || condition.fieldCode().isBlank())throw invalid("BUSINESS_CONDITION_INVALID","Field required");if(!fixed.contains(condition.fieldCode()))extensions.add(condition.fieldCode());}
            default -> {for(var child:condition.children())conditionReferences(child,fixed,extensions,deliveries,depth+1);}
        }
    }
    @Override public List<ProjectBusinessRuntimeApi.Reference> runtimeCandidates(ProjectBusinessRuntimeApi.Candidates query){
        requireRuntimeScope(query.tenantId(),query.projectId(),query.type());
        if(query.limit()<1 || query.limit()>200 || query.afterId()!=null && query.afterId()<=0)throw invalid("BUSINESS_RUNTIME_QUERY_INVALID","Invalid candidate page");
        var selectors=runtimeSelectors(query.bindingParameters());
        var selected=new ArrayList<ProjectBusinessRuntimeApi.Reference>();Long after=query.afterId();
        do {
            var rows=selectRuntimeCandidates(new BusinessMapper.RuntimeCandidates(query.tenantId(),query.projectId(),after,query.limit()));
            for(var row:rows){
                if(Boolean.TRUE.equals(row.getDeleted()) || !query.tenantId().equals(row.getTenantId()) || !query.projectId().equals(row.getProjectId())
                        || row.getId()==null || after!=null && row.getId()<=after)throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Foreign or unordered runtime candidate");
                after=row.getId();var values=binding.readable(row);
                if(selectors.entrySet().stream().allMatch(entry->Objects.equals(entry.getValue(),JsonUtils.parseTree(JsonUtils.toJsonString(values.get(entry.getKey()))))))
                    selected.add(new ProjectBusinessRuntimeApi.Reference(row.getId(),runtimeObservation(row).factVersion()));
                if(selected.size()==query.limit())return List.copyOf(selected);
            }
            // Apply the frozen field selectors before the externally visible page limit.
            if(rows.size()<query.limit())return List.copyOf(selected);
        }while(true);
    }
    private Map<String,tools.jackson.databind.JsonNode> runtimeSelectors(String parameters){
        if(parameters==null || parameters.isBlank())return Map.of();
        var input=JsonUtils.parseTree(parameters);
        if(input==null || !input.isObject())throw invalid("BUSINESS_RUNTIME_PARAMETERS_INVALID","Frozen binding parameters must be an object");
        var routing=Set.of("businessViewRevisionId","contextMapping","instanceResolutionStrategy","configurationName");
        var fields=definition().fields().stream().filter(cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor::readable)
                .map(cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor::code).collect(java.util.stream.Collectors.toSet());
        var result=new LinkedHashMap<String,tools.jackson.databind.JsonNode>();
        for(var entry:input.properties()){
            if(routing.contains(entry.getKey()))continue;
            if(!fields.contains(entry.getKey()) || !entry.getValue().isValueNode())throw invalid("BUSINESS_RUNTIME_PARAMETERS_INVALID","Binding selector must name a declared scalar business field");
            result.put(entry.getKey(),entry.getValue());
        }
        return result;
    }
    protected List<E> selectRuntimeCandidates(BusinessMapper.RuntimeCandidates query){return mapper.selectRuntimeCandidates(query);}
    @Override public Set<String> runtimeActions(ProjectBusinessRuntimeApi.UserContext context){
        var caller=actor();requireRuntimeScope(context.tenantId(),context.projectId(),context.type());
        if(!Objects.equals(caller.tenantId(),context.tenantId()) || !Objects.equals(caller.userId(),context.userId()))throw invalid("ACCESS_DENIED","Runtime view actor differs");
        defaults.projects().requireReadable(context.projectId(),caller);var model=configurationModel(false);var actions=new HashSet<String>();actions.add("QUERY");
        for(var operation:model.operations())if(operation.executable())actions.add(operation.code());
        return Set.copyOf(actions);
    }
    @Override public ProjectBusinessRuntimeApi.Observation runtimeForUser(ProjectBusinessRuntimeApi.UserContext context,Long id,boolean lock,String expectedVersion){
        runtimeActions(context);return runtimeAuthorizedObject(context,id,lock,expectedVersion);
    }
    @Override public ProjectBusinessRuntimeApi.UserInspection runtimeInspectForUser(ProjectBusinessRuntimeApi.UserContext context,List<Long> ids){
        var actions=runtimeActions(context);
        return new ProjectBusinessRuntimeApi.UserInspection(actions,ids.stream().map(id->runtimeAuthorizedObject(context,id,false,null)).toList());
    }
    private ProjectBusinessRuntimeApi.Observation runtimeAuthorizedObject(ProjectBusinessRuntimeApi.UserContext context,Long id,boolean lock,String expectedVersion){
        // Both callers first authorize this user, model and project through runtimeActions.
        // Read each object with its tenant and domain hooks, then bind it to that authorized project.
        var row=current(id,actor());
        if(!context.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime view project differs");
        if(lock){BusinessEntitySaveSupport.requireTransaction();row=lock(id,actor());if(!context.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Runtime view project changed");}
        var values=binding.readable(row);
        if(!runtimeSelectors(context.bindingParameters()).entrySet().stream().allMatch(entry ->
                Objects.equals(entry.getValue(),JsonUtils.parseTree(JsonUtils.toJsonString(values.get(entry.getKey()))))))
            throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Business record does not match the frozen binding");
        var observed=runtimeObservation(row,lock);
        if(lock && !Objects.equals(expectedVersion,observed.factVersion()))throw invalid("CONCURRENCY_CONFLICT","Runtime fact changed");
        return observed;
    }
    @Override public BusinessModelDescriptor definition() { return binding.mapping.descriptor(); }
    @Override public BusinessModelDescriptor readableDefinition() { requireModelRead(actor(),"detail");return definition(); }
    @Override public BusinessModelViews.ModelDetailVO model() {
        return model(null);
    }
    @Override public BusinessModelViews.ModelDetailVO model(String profile) {
        var read=initialRead();
        if(read!=null && read.models.containsKey(profile))return read.models.get(profile);
        var baseline=configurationModel(false);
        var result=BusinessFieldConfigurations.apply(baseline,fieldConfiguration(),profile);
        if(read!=null)read.models.put(profile,result);
        return result;
    }
    @Override public BusinessModelViews.ModelDetailVO configurationModel(boolean write) {
        var actor=actor();requireModelRead(actor,"detail");
        if(write)defaults.permissions().requireWritable(definition(),actor,"operation:configure-fields");
        var read=write?null:initialRead();
        if(read!=null && read.baseline!=null)return read.baseline;
        var guard=read==null?defaults.permissions():read.permissions;
        var result=configurationFields(BusinessModelViews.ModelDetailVO.of(definition(),actor,
                guard instanceof BusinessPermissions inherited?inherited.forOperationDisplay():guard));
        if(read!=null)read.baseline=result;
        return result;
    }
    private BusinessModelViews.ModelDetailVO configurationFields(BusinessModelViews.ModelDetailVO baseline) {
        var view=modelView(baseline);
        return new BusinessModelViews.ModelDetailVO(view.ownerModule(),view.entityType(),view.stableCode(),view.title(),view.viewCode(),view.fields().stream().map(field->{
            if(!ownedDetails.containsKey(field.code()))return field;
            var property=binding.fields.stream().filter(f->f.code().equals(field.code())).findFirst().orElseThrow().property();
            return new BusinessModelViews.FieldVO(field.code(),field.name(),field.type(),field.required(),field.readable(),field.writable(),field.displayOrder(),field.listVisible(),false,false,OwnedBusinessDetails.fieldViews(property));
        }).toList(),view.operations(),view.capabilities());
    }
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Identity configurationIdentity(){
        return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Identity(definition().ownerModule(),definition().entityType());
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration fieldConfiguration(){
        readableDefinition();var api=fieldConfigurations.getIfAvailable();
        var read=initialRead();if(read!=null && read.configuration!=null)return read.configuration;
        var result=api==null?new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(0,List.of()):api.read(configurationIdentity(),actor());
        if(read!=null)read.configuration=result;return result;
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration saveFieldConfiguration(long version,List<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field> fields){
        BusinessFieldConfigurations.validate(configurationModel(true),fields);
        return fieldConfigurations.getObject().save(configurationIdentity(),actor(),version,fields);
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration saveFieldConfiguration(cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration configuration){
        BusinessFieldConfigurations.validate(configurationModel(true),configuration);
        return fieldConfigurations.getObject().save(configurationIdentity(),actor(),configuration);
    }
    @Override public E input(Map<String,Object> values) { return binding.create(values); }
    @Override public Map<String,Object> readableValues(E entity) {
        var values=new LinkedHashMap<>(binding.readable(entity));
        ownedDetails.forEach((code,details)->{if(values.containsKey(code))values.put(code,details.readable(entity));});return Collections.unmodifiableMap(values);
    }
    @Override public E get(Long id) {
        var actor = actor(); requireModelRead(actor, "detail");
        E row = current(id, actor); requireReadableProject(row.getProjectId(), actor); return row;
    }
    @Override public PageResult<E> page(BusinessPageQuery query) {
        var actor = actor(); requireModelRead(actor, "list");
        if (query == null || query.getPageNo() < 1 || query.getPageSize() < 1 || query.getPageSize() > 200)
            throw invalid("QUERY_INVALID", "Invalid page bounds");
        if(query.getConfigurationName()!=null || query.getFilters()!=null && !query.getFilters().isEmpty()
                || query.getSorts()!=null && !query.getSorts().isEmpty()){
            var settings=fieldConfiguration();
            var configured=BusinessFieldConfigurations.apply(configurationFields(BusinessModelViews.ModelDetailVO.forQuery(definition())),settings,query.getConfigurationName());
            if(query.getFilters()!=null)for(var filter:query.getFilters()){
                if(filter==null || configured.fields().stream().noneMatch(field->field.code().equals(filter.fieldCode())&&field.searchable()))throw invalid("FILTER_FIELD_FORBIDDEN","Field is not configured for querying");
            }
            if(query.getSorts()!=null)for(var sort:query.getSorts()){
                if(sort==null || configured.fields().stream().noneMatch(field->field.code().equals(sort.fieldCode())&&field.sortable()))throw invalid("SORT_FIELD_FORBIDDEN","Field is not configured for sorting");
            }
        }else{
            // An unfiltered default page uses no configurable query fields. Keep the same
            // detail-read permission as fieldConfiguration without reading its presentation data.
            requireModelRead(actor,"detail");
        }
        var read=initialRead();
        Set<Long> projects;
        if(read!=null && query.getProjectId()!=null){
            requireReadableProject(query.getProjectId(),actor);projects=Set.of(query.getProjectId());
        }else projects=defaults.projects().readableScopeIds(actor);
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
            requireReadableProject(row.getProjectId(), actor);loadOwnedDetails(row);afterRead(row);
            if(read!=null)read.rows.put(row.getId(),row);
        }
        return result;
    }
    /** Override this method and call this business Mapper's XML query for complex selection. */
    protected PageResult<E> selectPage(BusinessReadQuery query) { return mapper.selectBusinessPage(query); }

    @Override public BusinessPageQueryApi.Read initializePage(BusinessPageQueryApi.Request request,List<Long> observeEntityIds) {
        if(request==null || request.projectId()==null || request.projectId()<=0
                || request.initialEntityId()!=null && request.initialEntityId()<=0 || observeEntityIds==null
                || observeEntityIds.stream().anyMatch(id->id==null || id<=0))
            throw invalid("QUERY_INVALID","Invalid business page identity");
        if(initialPageRead.get()!=null)throw invalid("BUSINESS_PAGE_RECURSIVE","A business cannot initialize itself recursively");
        var caller=actor();var read=new InitialPageRead(caller,request);initialPageRead.set(read);
        try {
            requireModelRead(caller,"detail");requireReadableProject(request.projectId(),caller);
            var model=model(request.configurationName());
            var selectors=runtimeSelectors(request.bindingParameters());
            var query=new BusinessPageQuery();query.setProjectId(request.projectId());
            query.setConfigurationName(request.configurationName());query.setPageSize(initialPageSize());
            query.setFilters(selectors.entrySet().stream().map(entry->new BusinessFieldFilter(entry.getKey(),
                    BusinessFieldFilter.Operator.EQ,Collections.singletonList(JsonUtils.parseObject(entry.getValue().toString(),Object.class)))).toList());
            var records=readInitialPage(query);read.pageRows=List.copyOf(records.getList());
            read.completeProjectRows=selectors.isEmpty() && Objects.equals(records.getTotal(),(long)read.pageRows.size());
            for(var row:read.pageRows)requirePageIdentity(row,request,selectors);
            read.related=List.copyOf(initialRelatedPages(request));
            var formIds=new LinkedHashSet<>(initialFormIds(read.pageRows,request.initialEntityId()));
            if(request.initialEntityId()!=null)formIds.add(request.initialEntityId());
            var required=new LinkedHashSet<>(observeEntityIds);required.addAll(formIds);
            for(var id:required)requirePageIdentity(get(id),request,selectors);
            var observations=observeEntityIds.stream().distinct().map(id->{
                var row=read.rows.get(id);var observed=runtimeObservation(row);
                var delivery=conditionEvidence.getObject().deliveries(identity(row),row.getProjectId(),false);
                var facts=new LinkedHashMap<>(observed.facts());facts.putAll(delivery.facts());
                return new ProjectBusinessRuntimeApi.Observation(id,ProjectBusinessRuntimeApi.combinedFactVersion(
                        observed.factVersion(),delivery.factVersion()),facts,observed.handlingCompleted(),observed.formedAt());
            }).toList();
            var forms=formIds.stream().map(id->{
                var value=form(id);return new BusinessPageQueryApi.Form(value.layout(),value.extensions(),
                        value.definitions(),value.context(),value.entity());
            }).toList();
            var actions=new HashSet<String>();actions.add("QUERY");
            model.operations().stream().filter(BusinessModelViews.OperationVO::executable).forEach(op->actions.add(op.code()));
            var body=new BusinessPageQueryApi.Page(request,model,fieldConfiguration(),query.getPageSize(),
                    new PageResult<>(read.pageRows.stream().map(this::entityData).toList(),records.getTotal()),
                    forms,read.related,initialPageContext(request));
            return new BusinessPageQueryApi.Read(body,new ProjectBusinessRuntimeApi.UserInspection(actions,observations));
        } finally { initialPageRead.remove(); }
    }
    /** Ordinary pages load one bounded list; a domain workspace may assemble its existing version selector. */
    protected int initialPageSize(){return 20;}
    protected PageResult<E> readInitialPage(BusinessPageQuery query){return page(query);}
    protected List<Long> initialFormIds(List<E> rows,Long selected){return selected==null?List.of():List.of(selected);}
    protected List<BusinessPageQueryApi.Page> initialRelatedPages(BusinessPageQueryApi.Request request){return List.of();}
    protected Map<String,Object> initialPageContext(BusinessPageQueryApi.Request request){return Map.of();}
    protected final List<E> initialPageRows(){var read=initialRead();return read==null?List.of():read.pageRows;}
    protected final boolean initialPageCoversProject(){var read=initialRead();return read!=null && read.completeProjectRows;}
    protected final List<BusinessPageQueryApi.Page> initialRelatedPages(){var read=initialRead();return read==null?List.of():read.related;}
    protected final boolean isInitializingPage(){return initialRead()!=null;}
    private BusinessEntityData entityData(E row){return new BusinessEntityData(identity(row),null,readableValues(row),row.getVersion(),true,null);}
    private void requirePageIdentity(E row,BusinessPageQueryApi.Request request,Map<String,tools.jackson.databind.JsonNode> selectors){
        if(!request.projectId().equals(row.getProjectId()))throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Business page project differs");
        var values=readableValues(row);
        if(!selectors.entrySet().stream().allMatch(entry->Objects.equals(entry.getValue(),JsonUtils.parseTree(JsonUtils.toJsonString(values.get(entry.getKey()))))))
            throw invalid("BUSINESS_RUNTIME_SCOPE_INVALID","Business record does not match the frozen binding");
    }
    private InitialPageRead initialRead(){
        var read=initialPageRead.get();
        if(read!=null && !read.actor.equals(actor()))throw invalid("ACCESS_DENIED","Business page caller changed");
        return read;
    }
    private void requireModelRead(EntityActor caller,String scene){
        var read=initialRead();
        if(read!=null && read.readScenes.contains(scene))return;
        authorizeModelRead(caller,scene);
        if(read!=null)read.readScenes.add(scene);
    }
    private void requireReadableProject(Long projectId,EntityActor caller){
        var read=initialRead();
        if(read!=null && read.projects.contains(projectId))return;
        defaults.projects().requireReadable(projectId,caller);
        if(read!=null)read.projects.add(projectId);
    }

    @Override public BusinessOperationReceipt create(E source, String key) {
        var values = inputValues(source, null);
        return createRecord(values,null,values,key);
    }
    @Override public BusinessOperationReceipt createForm(Map<String,Object> values,String key){
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
            beforeSave(null,entity,"create");
            if(form!=null)beforeFormWrite(null,entity,form.business());
            if (!Objects.equals(project,entity.getProjectId()) || !Objects.equals(generated,entity.getId()) || !actor.tenantId().equals(entity.getTenantId()) || !Long.valueOf(initial).equals(entity.getVersion()))
                throw invalid("CONTROL_FIELD_CHANGED", "Initialization changed business identity");
            requireProject(entity); defaults.projects().requireWritable(entity.getProjectId(), actor, true);
            validate(entity,"create");
            if (mapper.insert(entity) != 1 || entity.getId() == null) throw invalid("ENTITY_INSERT_FAILED", "Business insert did not return one persisted identity");
            saveOwnedDetails(entity,false);afterBodyPersisted("create",null,copy(entity));
            if(form!=null && (form.binding()!=null || form.extension()!=null)){
                storeFormWrite(formTarget(entity),entity.getVersion(),form);
                validateConfiguredForm(entity,"save-form");
            }
            afterCreate(copy(entity));afterSave(null,copy(entity),"create");
            return saved(entity, "create", ReceiptOutcome.SAVED);
            });
        });
    }
    @Override public BusinessOperationReceipt update(Long id, E source, Set<String> changedFields, Long version, String key) {
        var values = inputValues(source, Objects.requireNonNull(changedFields));
        return updateRecord(id,values,null,values,version,key);
    }
    @Override public BusinessOperationReceipt updateForm(Long id,Map<String,Object> values,Long version,String key){
        var form=formWrite(values);
        var fixed=inputValues(binding.create(form.fixed()),form.fixed().keySet());
        var intent=new LinkedHashMap<String,Object>(fixed);
        for(String part:List.of("$binding","$extensions","$business"))if(values.containsKey(part))intent.put(part,values.get(part));
        return updateRecord(id,fixed,form,intent,version,key);
    }
    private BusinessOperationReceipt updateRecord(Long id,Map<String,Object> values,FormWrite form,
            Map<String,Object> intent,Long version,String key){
        return write("save", id, version, intent, key, actor -> {
            E before = current(id, actor); requireVersion(before, version);
            E proposed = copy(before); binding.patch(proposed, values);
            checkIdentity(proposed, before); requireProject(proposed);
            defaults.projects().requireWritableScopes(new HashSet<>(List.of(before.getProjectId(), proposed.getProjectId())), actor, true);
            return inBusinessOperation("save", before, proposed, () -> {
            E locked = lock(id, actor); requireVersion(locked, version);
            if (!binding.values(before).equals(binding.values(locked))) throw invalid("CONCURRENCY_CONFLICT", "Business changed while its scopes were locked");
            beforeUpdate(copy(locked),proposed);beforeSave(copy(locked),proposed,"save");checkIdentity(proposed,locked);
            if(!Objects.equals(before.getProjectId(),proposed.getProjectId()))defaults.projects().requireWritable(proposed.getProjectId(),actor,true);
            if(form!=null){
                Long authorizedProject=proposed.getProjectId();
                beforeFormWrite(copy(locked),proposed,form.business());
                checkIdentity(proposed,locked);
                if(!Objects.equals(authorizedProject,proposed.getProjectId()))throw invalid("ENTITY_SCOPE_DENIED","Form command cannot change the authorized project");
                storeFormWrite(formTarget(locked),version,form);
            }
            persistBusinessChange(before,proposed,"save");
            return saved(proposed, "save", ReceiptOutcome.SAVED);
            });
        });
    }
    @Override public BusinessOperationReceipt delete(Long id, Long version, String key) {
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
    @Override public String ownerModule(){return definition().ownerModule();}
    @Override public String entityType(){return definition().entityType();}
    @Override public List<EntityField> fields(){return definition().fields().stream()
            .map(f->new EntityField(f.code(),f.type(),f.required())).toList();}
    @Override public Map<String,EntityFieldValue> read(EntityDataRef target,EntityActor caller){
        requireProviderCaller(target,caller);var row=get(target.entity().entityId());var result=new LinkedHashMap<String,EntityFieldValue>();
        readableValues(row).forEach((key,value)->result.put(key,EntityFieldValue.known(value)));return result;
    }
    @Override public void requireReadable(EntityDataRef target,EntityActor caller){
        requireProviderCaller(target,caller);
        var authorized=formReadAccess.get();
        if(authorized==null || !authorized.target().equals(target) || !authorized.actor().equals(caller))get(target.entity().entityId());
    }
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
    /** Legacy types may override names/mappings; ordinary inherited businesses need no catalog adapter. */
    protected cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey formConfigurationProvider(){return providerKey();}
    protected String formConfigurationCategory(){return formUsage();}
    protected Map<String,String> formConfigurationBindings(cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact schema){
        var result=new LinkedHashMap<String,String>();
        schema.fields().stream().filter(field->!field.controlledFile()).forEach(field->result.put(field.fieldKey(),field.fieldKey()));
        return result;
    }
    @Override public List<BusinessFormOption> formOptions(Long projectId){
        var caller=actor();authorizeModelRead(caller,"form");
        if(projectId==null || projectId<=0)throw invalid("PROJECT_REQUIRED","Business project is required");
        defaults.projects().requireReadable(projectId,caller);
        var catalog=presentationPorts.getIfAvailable();if(catalog==null)return List.of();
        var validator=dynamicForms.getIfAvailable();if(validator==null)throw invalid("CAPABILITY_UNAVAILABLE","Published form validation is unavailable");
        var options=new ArrayList<BusinessFormOption>();
        for(var candidate:catalog.listForType(new EntityPresentationApi.TypeQuery(caller,runtimeDefinition().type(),projectId,formConfigurationCategory()))){
            try{
                var checked=validator.inspectRevisionForUsage(new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionUsageQuery(
                        caller.tenantId(),caller.userId(),formConfigurationProvider(),candidate.revisionId(),formConfigurationCategory(),
                        cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction.REVISION_FROZEN_USE,candidate.version()));
                if(checked==null)continue;
                var fieldBindings=formConfigurationBindings(checked);
                if(new HashSet<>(fieldBindings.values()).size()!=fieldBindings.size())continue;
                var binding=new EntityFormApi.Binding(checked.templateRevisionId(),null,fieldBindings,0);
                options.add(new BusinessFormOption(candidate.name(),new EntityFormApi.Layout(binding,checked.templateId(),checked.revisionNo(),checked.revisionFactVersion(),
                        checked.engineCode(),checked.designerVersion(),checked.rendererVersion(),checked.formConfJson(),checked.formRulesJson(),checked.fields())));
            }catch(cn.iocoder.yudao.framework.common.exception.ServiceException | BusinessContractException incompatible){
                // An unauthorized/incompatible named configuration is not offered for this business.
            }
        }
        return List.copyOf(options);
    }
    protected Map<String,Object> formContext(E row){return Map.of();}
    protected BusinessFormData defaultForm(Long projectId){return new BusinessFormData(null,new EntityExtensionApi.Values(null,Map.of(),0),List.of());}
    @Override public BusinessFormData formDefaults(Long projectId){
        var caller=actor();authorizeModelRead(caller,"form");
        if(projectId==null || projectId<=0)throw invalid("PROJECT_REQUIRED","Business project is required");
        defaults.projects().requireReadable(projectId,caller);return defaultForm(projectId);
    }
    /** Only an explicit business override can accept domain command inputs alongside form content. */
    protected void beforeFormWrite(E before,E proposed,Map<String,Object> business){
        if(!business.isEmpty())throw invalid("FIELD_NOT_OPEN","Business form commands are not declared");
    }
    @Override public BusinessFormData form(Long id){
        var read=initialRead();if(read!=null && read.forms.containsKey(id))return read.forms.get(id);
        var row=get(id);var target=formTarget(row);var caller=actor();
        EntityExtensionApi.Values values;EntityFormApi.Layout layout;List<EntityExtensionApi.Definition> fields;
        var previous=formReadAccess.get();
        // The capability ports re-enter this provider. Reuse only the current row just authorized
        // above, on this thread and for this actor; alternate form targets still authorize normally.
        formReadAccess.set(new FormReadAccess(EntityDataRef.current(identity(row)),caller));
        try{
            values=extensionPort().read(target,caller);layout=formPort().layout(target,caller);
            var definitionId=layout!=null && layout.binding().extensionDefinitionRevisionId()!=null?layout.binding().extensionDefinitionRevisionId():values.definitionRevisionId();
            fields=definitionId==null?List.of():extensionPort().definition(definitionId,target.entity(),caller).fields();
        }finally{
            if(previous==null)formReadAccess.remove();else formReadAccess.set(previous);
        }
        var model=definition();var entity=new BusinessEntityData(new EntityRef(row.getTenantId(),model.ownerModule(),model.entityType(),row.getId()),
                null,readableValues(row),row.getVersion(),true,null);
        var result=new BusinessFormData(layout,values,fields,formContext(copy(row)),entity);
        if(read!=null)read.forms.put(id,result);return result;
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
            bound=formPort().bind(new EntityFormApi.Bind(target,actor(),version,layout.expectedVersion(),layout.formRevisionId(),layout.extensionDefinitionRevisionId(),layout.fieldBindings(),layout.bindRemainingFields(),true,layout.definitionSources()));
        }
        var patch=input.extension();
        if(patch==null && bound==null)return;
        var existing=extensionPort().read(target,actor());
        if(patch==null){
            if(bound.extensionDefinitionRevisionId()==null || Objects.equals(bound.extensionDefinitionRevisionId(),existing.definitionRevisionId()) || existing.definitionRevisionId()==null)return;
            patch=new BusinessEntitySaveSupport.ExtensionPatch(bound.extensionDefinitionRevisionId(),existing.version(),existing.fields());
        }
        if(patch.values()==null || patch.expectedVersion()<0)throw invalid("INPUT_INVALID","Invalid extension patch");
        var definition=bound!=null?bound.extensionDefinitionRevisionId():patch.definitionRevisionId();
        if(definition==null){
            // A layout may already be bound while no extension value row exists yet.
            // Resolve the persisted target binding as well as a binding created in this command.
            if(bound==null)bound=formPort().read(target,actor());
            definition=bound!=null && bound.extensionDefinitionRevisionId()!=null?bound.extensionDefinitionRevisionId():existing.definitionRevisionId();
        }
        var merged=new LinkedHashMap<String,Object>(existing.fields());merged.putAll(patch.values());
        // Lossless text number controls are converted using the published definition, never arbitrary property names.
        var schema=extensionPort().definition(definition,target.entity(),actor());
        for(var field:schema.fields())if(field.type()==EntityField.Type.NUMBER && merged.get(field.code()) instanceof String text){
            try{merged.put(field.code(),text.isBlank()?null:new java.math.BigDecimal(text));}catch(NumberFormatException invalid){throw invalid("INPUT_INVALID","Invalid number for extension: "+field.code());}
        }
        extensionPort().save(new EntityExtensionApi.Save(target,actor(),version,patch.expectedVersion(),definition,merged));
    }
    @Override public BusinessOperationReceipt saveForm(Long id,Map<String,Object> values,Long version,String key){
        var input=formWrite(values);
        return change("save-form",id,version,key,values,row->{var before=copy(row);binding.patch(row,input.fixed());beforeUpdate(before,row);beforeFormWrite(before,row,input.business());storeFormWrite(formTarget(row),version,input);});
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
        persistUpdate(proposed,actor(),before.getVersion(),nulls);saveOwnedDetails(proposed,false);
        afterBodyPersisted(operation,copy(before),copy(proposed));afterUpdate(copy(before),copy(proposed));afterSave(copy(before),copy(proposed),operation);return proposed;
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
                E proposed=copy(locked);mutation.accept(proposed);beforeSave(copy(locked),proposed,operation);checkIdentity(proposed,locked);
                if(!Objects.equals(proposed.getProjectId(),locked.getProjectId()))throw invalid("ENTITY_SCOPE_DENIED","A domain action cannot move its project");
                validate(proposed,operation);
                var nulls=new HashSet<String>();binding.values(proposed).forEach((name,value)->{if(value==null)nulls.add(name);});
                persistUpdate(proposed,actor,version,nulls);saveOwnedDetails(proposed,false);
                afterBodyPersisted(operation,copy(locked),copy(proposed));
                afterChange(operation,copy(locked),copy(proposed));
                afterSave(copy(locked),copy(proposed),operation);
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
    protected void authorizeModelRead(EntityActor actor,String scene) {
        var read=initialRead();(read==null?defaults.permissions():read.permissions).requireReadable(definition(),actor,scene);
    }
    protected BusinessModelViews.ModelDetailVO modelView(BusinessModelViews.ModelDetailVO view) { return view; }
    protected boolean publishDefaultEvent(String operation) { return true; }
    protected void authorizeReceipt(String operation,E entity) { }
    protected void afterChange(String operation,E before,E current) { }
    protected Class<?>[] validationGroups(E entity) { return new Class<?>[]{jakarta.validation.groups.Default.class}; }
    protected void beforeCreate(E entity) { }
    /** Common normalization/validation input, inside the same transaction and after the parent lock. */
    protected void beforeSave(E before,E proposed,String operation) { }
    /** Legacy aggregate persistence only; completed-aggregate business hooks run afterwards. */
    protected void afterBodyPersisted(String operation,E before,E current) { }
    /** All body, details, bindings and extensions are persisted; throwing rolls the command back. */
    protected void afterSave(E before,E current,String operation) { }
    protected final void loadOwnedDetails(E row){ownedDetails.values().forEach(details->details.load(row,formTarget(row)));}
    protected final void saveOwnedDetails(E row,boolean copy){ownedDetails.values().forEach(details->details.save(row,formTarget(row),actor().userId(),defaults.validator(),copy));}
    protected final void clearOwnedDetailIdentities(E row){ownedDetails.values().forEach(details->details.clearIdentities(row));}
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
    /** An Owner may redact sensitive body fields before they enter immutable approval history. */
    protected Map<String,Object> approvalValues(E entity) { return readableValues(entity); }
    protected void validateApproval(E entity,boolean write) { }

    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalApi approvalApi() {
        return approvals.getObject();
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalApi.Subject approvalSubject(Long id, boolean write, boolean lock) {
        var actor=actor();authorizeModelRead(actor,"approval");
        if(write)defaults.permissions().requireWritable(definition(),actor,"operation:save");
        E row=current(id,actor);
        if(write)defaults.projects().requireWritable(row.getProjectId(),actor,lock);
        else defaults.projects().requireReadable(row.getProjectId(),actor);
        if(lock){
            E locked=lock(id,actor);
            if(!Objects.equals(row.getProjectId(),locked.getProjectId()) || !Objects.equals(row.getVersion(),locked.getVersion()))
                throw invalid("CONCURRENCY_CONFLICT","Business changed before approval");
            row=locked;loadOwnedDetails(row);afterRead(row);
        }
        validateApproval(copy(row),write);
        var values=new LinkedHashMap<String,Object>(approvalValues(row));
        var presentation=form(id);
        if(presentation.extensions()!=null){
            values.put("$extensions",presentation.extensions().fields());
            values.put("$extensionDefinitions",presentation.definitions());
            values.put("$extensionDefinitionRevisionId",presentation.extensions().definitionRevisionId());
        }
        return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalApi.Subject(
                identity(row),row.getProjectId(),row.getVersion(),Collections.unmodifiableMap(values));
    }

    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi deliveryApi() {
        return Objects.requireNonNull(defaults.deliveries().get(), "Shared delivery API is unavailable");
    }
    @Override public cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope deliveryScope(Long id, String type) {
        E row = get(id);
        return new cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope(
                row.getProjectId(), definition().stableCode(), id.toString(), type);
    }
    @Override public Long requireDeliveryAccess(Long id, boolean write, boolean lock) {
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
    @Override public BusinessOperationReceipt receipt(String operation, String key) {
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
    protected final E current(Long id, EntityActor actor) {
        var read=initialRead();
        if(read!=null && read.actor.equals(actor) && read.rows.containsKey(id))return read.rows.get(id);
        E row=mapper.selectById(id);checkRow(row,id,actor);loadOwnedDetails(row);afterRead(row);
        if(read!=null && read.actor.equals(actor))read.rows.put(id,row);return row;
    }
    protected final E lock(Long id, EntityActor actor) {
        E row = binding.type.cast(DeclaredBusinessCurrentRows.lock(binding.mapping, new DeclaredCurrentRowQuery(actor.tenantId(), id)));
        checkRow(row,id,actor);loadOwnedDetails(row);afterRead(row);return row;
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
