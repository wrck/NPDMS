package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.*;

/** A single project-side bridge discovers inherited business services; entities supply no extra adapters. */
@Component
public class DefaultProjectBusinessRuntimeProviders {
    private final ObjectProvider<ProjectBusinessRuntimeApi> runtime;
    private final ProjectNodeExecutionApi executions;
    public DefaultProjectBusinessRuntimeProviders(ObjectProvider<ProjectBusinessRuntimeApi> runtime,ProjectNodeExecutionApi executions){this.runtime=runtime;this.executions=executions;}
    public List<TaskBusinessObjectProvider> withDefaults(List<TaskBusinessObjectProvider> existing){
        var api=runtime.getIfAvailable();if(api==null)return existing;
        var definitions=api.definitions();var result=new ArrayList<TaskBusinessObjectProvider>();
        for(var owner:existing){
            var matches=definitions.stream().filter(def->def.type().ownerModule().equals(owner.ownerContext()) && Objects.equals(def.nativeObjectType(),owner.objectType())).toList();
            if(matches.size()>1)throw new IllegalStateException("BUSINESS_RUNTIME_ALIAS_AMBIGUOUS");
            result.add(matches.isEmpty()?owner:new Provider(api,matches.getFirst(),owner,owner.objectType()));
        }
        for(var definition:definitions){
            if(existing.stream().anyMatch(owner->owner.ownerContext().equals(definition.type().ownerModule()) && owner.objectType().equals(definition.type().entityType())))continue;
            result.add(new Provider(api,definition,null,definition.type().entityType()));
        }
        return List.copyOf(result);
    }
    public List<StageBusinessViewProvider> stageProviders(){return withDefaults(List.of()).stream().map(value->(StageBusinessViewProvider)value).toList();}
    private final class Provider implements TaskBusinessObjectProvider,StageBusinessViewProvider {
        private final ProjectBusinessRuntimeApi api;
        private final ProjectBusinessRuntimeApi.Definition definition;
        private final TaskBusinessObjectProvider legacy;
        private final String type;
        private Provider(ProjectBusinessRuntimeApi api,ProjectBusinessRuntimeApi.Definition definition,TaskBusinessObjectProvider legacy,String type){this.api=api;this.definition=definition;this.legacy=legacy;this.type=type;}
        @Override public String ownerContext(){return definition.type().ownerModule();}
        @Override public String objectType(){return type;}
        @Override public Set<String> completionFactCodes(){var values=new HashSet<>(legacy==null?definition.factLabels().keySet():legacy.completionFactCodes());values.add(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"ATTACHMENT");return Set.copyOf(values);}
        @Override public boolean supportsCompletionFact(String code){return completionFactCodes().contains(code) || ProjectBusinessRuntimeApi.deliveryFact(code);}
        @Override public Map<String,String> completionFactLabels(){var labels=new HashMap<>(legacy==null?definition.factLabels():legacy.completionFactLabels());labels.put(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"ATTACHMENT","指定类型交付件已有有效上传");return Map.copyOf(labels);}
        @Override public boolean supportsStageCompletionFacts(){return legacy==null || legacy.supportsStageCompletionFacts();}
        private ProjectBusinessRuntimeApi.Query query(Long tenant,Long project,String id){return new ProjectBusinessRuntimeApi.Query(tenant,project,definition.type(),cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.nativeId(id));}
        private ProjectBusinessRuntimeApi.UserContext user(TaskBusinessObjectProvider.Context context){return new ProjectBusinessRuntimeApi.UserContext(context.tenantId(),context.actorId(),context.projectId(),definition.type());}
        @Override public Set<String> inspectContext(TaskBusinessObjectProvider.Context context){return legacy==null?api.actions(user(context)):legacy.inspectContext(context);}
        private BusinessObjectFact enrich(TaskBusinessObjectProvider.Context context,BusinessObjectFact fact,boolean lock){
            var delivery=api.deliveryFacts(query(context.tenantId(),context.projectId(),fact.objectId()),lock);var values=new HashMap<>(fact.completionFacts());values.putAll(delivery.facts());
            return new BusinessObjectFact(fact.objectId(),fact.displayName(),factVersion(fact.factVersion(),delivery.factVersion()),fact.allowedActions(),values,fact.artifacts());
        }
        private BusinessObjectFact plain(TaskBusinessObjectProvider.Context context,String id,boolean lock,String expected){
            var observation=api.inspectForUser(user(context),cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.nativeId(id),lock,expected);
            return new BusinessObjectFact(id,definition.title()+" #"+id,observation.factVersion(),api.actions(user(context)),observation.facts(),List.of());
        }
        @Override public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context,String id){return enrich(context,legacy==null?plain(context,id,false,null):legacy.inspect(context,id),false);}
        @Override public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context,String id,String expected){
            requireTransaction();var before=legacy==null?plain(context,id,false,null):legacy.inspect(context,id);
            var locked=legacy==null?plain(context,id,true,before.factVersion()):legacy.lockAndRevalidate(context,id,before.factVersion());
            var result=enrich(context,locked,true);if(!Objects.equals(expected,result.factVersion()))throw new IllegalArgumentException("BUSINESS_FACT_VERSION_MISMATCH");return result;
        }
        @Override public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context){
            if(legacy!=null)return legacy.candidates(context).stream().map(fact->enrich(context,fact,false)).toList();
            inspectContext(context);var result=new ArrayList<BusinessObjectFact>();Long after=null;
            do{var rows=api.candidates(new ProjectBusinessRuntimeApi.Candidates(context.tenantId(),context.projectId(),definition.type(),after,100));
                for(var row:rows){try{result.add(inspect(context,row.entityId().toString()));}catch(cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException denied){/* Private rows stay private. */}if(result.size()==100)return List.copyOf(result);}
                if(rows.size()<100)return List.copyOf(result);after=rows.getLast().entityId();
            }while(true);
        }
        @Override public List<AssociationCandidate> associationCandidates(AssociationContext context,String after,int limit){
            if(legacy!=null)return legacy.associationCandidates(context,after,limit);
            return api.candidates(new ProjectBusinessRuntimeApi.Candidates(context.tenantId(),context.projectId(),definition.type(),cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.nativeId(after),limit)).stream()
                    .map(row->new AssociationCandidate(row.entityId().toString(),row.factVersion())).toList();
        }
        private CompletionFact enrich(Long tenant,Long project,CompletionFact fact){
            var delivery=api.deliveryFacts(query(tenant,project,fact.objectId()),true);var values=new HashMap<>(fact.completionFacts());values.putAll(delivery.facts());
            return new CompletionFact(fact.objectId(),factVersion(fact.factVersion(),delivery.factVersion()),fact.handlingCompleted(),values);
        }
        private CompletionFact observed(Long tenant,Long project,String id){var value=api.lockAndInspect(query(tenant,project,id));return new CompletionFact(id,value.factVersion(),value.handlingCompleted(),value.facts());}
        @Override public CompletionFact lockCompletionFact(CompletionContext context,String id){
            requireTransaction();requireTenant(context.tenantId());var current=executions.lockAndRevalidate(context.execution());
            var fact=legacy==null?observed(context.tenantId(),current.projectId(),id):legacy.lockCompletionFact(context,id);
            return fact==null?null:enrich(context.tenantId(),current.projectId(),fact);
        }
        @Override public CompletionFact lockStageCompletionFact(StageCompletionContext context,String id){
            requireTransaction();requireTenant(context.tenantId());var current=executions.lockAndRevalidateStage(context.execution());
            var fact=legacy==null?observed(context.tenantId(),current.projectId(),id):legacy.lockStageCompletionFact(context,id);
            return fact==null?null:enrich(context.tenantId(),current.projectId(),fact);
        }
        @Override public StageBusinessViewProvider.Result inspectStage(StageBusinessViewProvider.Context context){
            requireTenant(context.tenantId());if(context.stageId()==null || context.stageId()<=0 || !Set.of("REFERENCE_EXISTING","CREATE_ON_FIRST_ACTION","READ_ONLY_AGGREGATE").contains(context.instanceResolutionStrategy()))throw new IllegalArgumentException("BUSINESS_STAGE_CONTEXT_INVALID");
            var actions=api.actions(new ProjectBusinessRuntimeApi.UserContext(context.tenantId(),context.actorId(),context.projectId(),definition.type()));
            return new StageBusinessViewProvider.Result("READ_ONLY_AGGREGATE".equals(context.instanceResolutionStrategy()) || context.execution()==null || !context.execution().writable()?Set.of("QUERY"):actions);
        }
    }
    private static void requireTransaction(){if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("BUSINESS_RUNTIME_TRANSACTION_REQUIRED");}
    private static String factVersion(String business,String delivery){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((business+"\n"+delivery).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void requireTenant(Long tenant){if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()))throw new IllegalArgumentException("BUSINESS_RUNTIME_TENANT_INVALID");}
}
