package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/** Own current/revision Mappers and inherited behavior; no catalog, factory or per-business version adapter. */
public abstract class DefaultVersionedProjectBusinessService<M extends BusinessMapper<E>,E extends BaseProjectBusinessEntity,
        RM extends BusinessRevisionMapper<R>,R extends BaseProjectBusinessEntity & MutableEntityRevision>
        extends DefaultProjectBusinessService<M,E> implements BusinessRevisions,EntityVersionProvider {
    @Autowired protected RM revisionMapper;
    @Autowired private ObjectProvider<EntityExtensionApi> extensions;
    @Autowired private ObjectProvider<EntityFormApi> forms;
    private final ThreadLocal<EntityRef> activating=new ThreadLocal<>();
    private Class<R> revisionClass;
    private BusinessModelDeclaration revisionBinding;
    @PostConstruct @SuppressWarnings("unchecked")
    protected final void initializeRevisions(){
        var type=ResolvableType.forClass(getClass()).as(DefaultVersionedProjectBusinessService.class).getGeneric(3).resolve();
        if(type==null || !entityClass().isAssignableFrom(type) || !MutableEntityRevision.class.isAssignableFrom(type))
            throw invalid("REVISION_MAPPING_INVALID","Revision entity must inherit its typed business fields and revision contract");
        revisionClass=(Class<R>)type;revisionBinding=new BusinessModelDeclaration(definition(),type,revisionMapper,null);
    }
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_CONTENT_EFFECTIVE","业务内容已有生效版本");}
    private R runtimeEffective(E row){
        var caller=new EntityActor(row.getTenantId(),0L,EntityActor.SYSTEM_OBSERVER);
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                ?effective(lockedHistory(identity(row),caller)):revisionMapper.selectEffective(new BusinessRevisionMapper.Effective(row.getTenantId(),row.getId()));
    }
    @Override protected RuntimeResult runtimeResult(E row){var current=runtimeEffective(row);boolean completed=current!=null && current.revisionState()==Revision.State.FROZEN;
        return new RuntimeResult(Map.of("BUSINESS_CONTENT_EFFECTIVE",completed),completed,current==null?null:current.revisionMetadata().frozenAt());}
    @Override protected EntityDataRef formTarget(E row){
        return revisionClass!=null && revisionClass.isInstance(row)?EntityDataRef.revision(revisionClass.cast(row).revisionRef()):super.formTarget(row);
    }
    @Override protected List<BusinessOperationDescriptor> defaultOperations(String prefix){
        var result=new ArrayList<>(super.defaultOperations(prefix));
        for(var op:List.of(new String[]{"revision-create","发起修订"},new String[]{"revision-save","保存修订"},new String[]{"revision-complete","冻结并生效"},new String[]{"revision-discard","放弃修订"}))
            result.add(new BusinessOperationDescriptor(op[0],1,op[1],BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update"));
        return result;
    }
    @Override protected List<BusinessCapabilityBinding> configureCapabilities(List<BusinessCapabilityBinding> capabilities){
        var result=new ArrayList<>(super.configureCapabilities(capabilities));result.add(new BusinessCapabilityBinding(BusinessCapabilityType.CONTENT_HISTORY,"inherited-revisions",true));return result;
    }
    @Override public List<Revision> revisions(Long id,Long beforeId,int limit){var row=get(id);return history(identity(row),actor(),beforeId,limit);}
    @Override public Map<String,EntityFieldValue> revisionValues(Long id,Long revisionId){var row=get(id);return read(EntityDataRef.revision(new RevisionRef(identity(row),revisionId)),actor());}
    @Override public BusinessFormData revisionForm(Long id,Long revisionId){
        var current=get(id);var reference=new RevisionRef(identity(current),revisionId);revision(reference,actor());var target=EntityDataRef.revision(reference);
        var values=extensionApi().read(target,actor());var layout=formApi().layout(target,actor());
        var definitionId=layout!=null && layout.binding().extensionDefinitionRevisionId()!=null?layout.binding().extensionDefinitionRevisionId():values.definitionRevisionId();
        return new BusinessFormData(layout,values,definitionId==null?List.of():extensionApi().definition(definitionId,target.entity(),actor()).fields());
    }
    @Override public List<EntityVersionApi.FieldDifference> compareRevisions(Long id,Long left,Long right){
        var before=new LinkedHashMap<>(revisionValues(id,left));var after=new LinkedHashMap<>(revisionValues(id,right));
        revisionForm(id,left).extensions().fields().forEach((key,value)->before.put(key,EntityFieldValue.known(value)));
        revisionForm(id,right).extensions().fields().forEach((key,value)->after.put(key,EntityFieldValue.known(value)));
        var codes=new TreeSet<>(before.keySet());codes.addAll(after.keySet());var result=new ArrayList<EntityVersionApi.FieldDifference>();
        for(var code:codes){var a=before.getOrDefault(code,EntityFieldValue.unavailable());var b=after.getOrDefault(code,EntityFieldValue.unavailable());if(!Objects.equals(a,b))result.add(new EntityVersionApi.FieldDifference(code,a,b));}return result;
    }
    @Override public BusinessOperationReceipt createRevision(Long id,Long version,Long sourceId,String reason,String key){
        var input=new LinkedHashMap<String,Object>();input.put("sourceRevisionId",sourceId);input.put("reason",reason);
        return revisionWrite("revision-create",id,version,key,input,row->createDraft(identity(row),sourceId==null?null:new RevisionRef(identity(row),sourceId),reason,actor()));
    }
    @Override public BusinessOperationReceipt saveRevision(Long id,Long version,Long revisionId,Long revisionVersion,Map<String,Object> values,String key){
        return revisionWrite("revision-save",id,version,key,intent(revisionId,revisionVersion,values),row->{
            var reference=new RevisionRef(identity(row),revisionId);var draft=lockedRevision(reference,actor());requireRevisionVersion(draft,revisionVersion);
            requireDraft(draft);var input=formWrite(values);if(!input.business().isEmpty())throw invalid("FIELD_NOT_OPEN","Revision form commands are not declared");storeFormWrite(EntityDataRef.revision(reference),revisionVersion,input);
            return save(reference,Math.toIntExact(revisionVersion),input.fixed(),actor());
        });
    }
    @Override public BusinessOperationReceipt completeRevision(Long id,Long version,Long revisionId,Long revisionVersion,String key){
        return revisionWrite("revision-complete",id,version,key,intent(revisionId,revisionVersion,Map.of()),row->{
            var reference=new RevisionRef(identity(row),revisionId);var frozen=freeze(reference,Math.toIntExact(revisionVersion),actor());return activate(reference,frozen.version(),actor());
        });
    }
    @Override public BusinessOperationReceipt discardRevision(Long id,Long version,Long revisionId,Long revisionVersion,String key){
        return revisionWrite("revision-discard",id,version,key,intent(revisionId,revisionVersion,Map.of()),row->{
            var reference=new RevisionRef(identity(row),revisionId);var draft=lockedRevision(reference,actor());requireRevisionVersion(draft,revisionVersion);
            var metadata=draft.revisionMetadata();discard(reference,actor());return metadata;
        });
    }
    private Map<String,Object> intent(Long revisionId,Long revisionVersion,Map<String,Object> values){
        if(revisionId==null || revisionId<=0 || revisionVersion==null || revisionVersion<0 || values==null)throw invalid("REVISION_INPUT_INVALID","Revision identity, version and values are required");
        return Map.of("revisionId",revisionId,"revisionVersion",revisionVersion,"values",values);
    }
    private BusinessOperationReceipt revisionWrite(String operation,Long id,Long version,String key,Map<String,Object> input,Function<E,Revision> action){
        return write(operation,id,version,input,key,caller->{
            var observed=current(id,caller);defaults.projects().requireWritable(observed.getProjectId(),caller,true);
            return inBusinessOperation(operation,observed,null,()->{
                var locked=lock(id,caller);requireVersion(locked,version);var result=action.apply(locked);var latest=current(id,caller);
                return new BusinessOperationReceipt(operation.equals("revision-complete")?ReceiptOutcome.EFFECTED:ReceiptOutcome.SAVED,identity(latest),latest.getVersion(),
                        List.of(new ResultReference(ResultReference.Kind.RESULT,ownerModule(),result.ref().revisionId().toString())),null,null,operation,1);
            });
        });
    }
    @Override public Revision inspect(RevisionRef ref,EntityActor caller){requireEntity(ref.entity(),caller,false);var row=revision(ref,caller);return row.revisionMetadata();}
    @Override public List<Revision> history(EntityRef entity,EntityActor caller,Long beforeRevisionId,int limit){
        requireEntity(entity,caller,false);return revisionMapper.selectHistory(new BusinessRevisionMapper.History(caller.tenantId(),entity.entityId(),beforeRevisionId,limit)).stream().map(MutableEntityRevision::revisionMetadata).toList();
    }
    @Override public Revision createDraft(EntityRef entity,RevisionRef source,String reason,EntityActor caller){
        var current=requireEntity(entity,caller,true);authorizeRevision("revision-create",caller);beforeRevisionCreate(copy(current));
        var active=lockedHistory(entity,caller);
        if(active.stream().anyMatch(row->row.revisionState()==Revision.State.DRAFT))throw invalid("REVISION_DRAFT_EXISTS","An open revision already exists");
        var effective=effective(active);R origin=null;
        if(source!=null){if(!source.entity().equals(entity))throw invalid("REVISION_IDENTITY_INVALID","Source belongs to another business");origin=lockedRevision(source,caller);if(origin.revisionState()!=Revision.State.FROZEN)throw invalid("REVISION_STATE_INVALID","Source revision is not frozen");}
        else if(effective!=null)origin=effective;
        var draft=newRevision();org.springframework.beans.BeanUtils.copyProperties(BusinessEntityCopies.copy(origin==null?current:origin),draft);
        draft.setId(null);draft.setTenantId(caller.tenantId());draft.setProjectId(current.getProjectId());draft.setEntityId(entity.entityId());
        draft.setRevisionNo(Math.incrementExact(DeclaredBusinessCurrentRows.maxRevisionNo(revisionBinding,new DeclaredCurrentRowQuery(caller.tenantId(),entity.entityId()))));
        draft.setSourceRevisionId(origin==null?null:origin.getId());draft.setBaseEffectiveRevisionId(effective==null?null:effective.getId());draft.setBaseEntityVersion(Math.toIntExact(current.getVersion()));
        draft.setRevisionState(Revision.State.DRAFT);draft.setEffective(false);draft.setChangeReason(reason);draft.setVersion(0L);draft.setFrozenBy(null);draft.setFrozenAt(null);
        draft.setCreator(caller.userId().toString());draft.setUpdater(caller.userId().toString());draft.setCreateTime(null);draft.setUpdateTime(null);draft.setDeleted(false);
        beforeRevisionSave(copy(current),draft);if(revisionMapper.insert(draft)!=1 || draft.getId()==null)throw invalid("REVISION_INSERT_FAILED","Revision was not persisted");
        checked(draft,new RevisionRef(entity,draft.getId()),caller);
        var from=origin==null?EntityDataRef.current(entity):EntityDataRef.revision(origin.revisionRef());var to=EntityDataRef.revision(draft.revisionRef());
        extensionApi().copy(from,to,draft.getVersion(),caller);formApi().copy(from,to,draft.getVersion(),caller);return draft.revisionMetadata();
    }
    @Override public Revision save(RevisionRef ref,Integer expectedVersion,Map<String,Object> fields,EntityActor caller){
        var current=requireEntity(ref.entity(),caller,true);authorizeRevision("revision-save",caller);var draft=lockedRevision(ref,caller);requireDraft(draft);requireRevisionVersion(draft,expectedVersion==null?null:expectedVersion.longValue());
        patchBusinessFields(asBusiness(draft),fields);if(!Objects.equals(current.getProjectId(),draft.getProjectId()))throw invalid("ENTITY_SCOPE_DENIED","Revision cannot move its business project");
        beforeRevisionSave(copy(current),draft);validate(asBusiness(draft),"revision-save");persistRevision(draft,caller);return draft.revisionMetadata();
    }
    @Override public Revision freeze(RevisionRef ref,Integer expectedVersion,EntityActor caller){
        var current=requireEntity(ref.entity(),caller,true);authorizeRevision("revision-complete",caller);var draft=lockedRevision(ref,caller);requireDraft(draft);requireRevisionVersion(draft,expectedVersion==null?null:expectedVersion.longValue());
        beforeRevisionFreeze(copy(current),draft);validate(asBusiness(draft),"revision-complete");extensionApi().validateComplete(EntityDataRef.revision(ref),caller);
        draft.setRevisionState(Revision.State.FROZEN);draft.setFrozenBy(caller.userId());draft.setFrozenAt(LocalDateTime.now());persistRevision(draft,caller);return draft.revisionMetadata();
    }
    @Override public Revision activate(RevisionRef ref,Integer expectedVersion,EntityActor caller){
        var current=requireEntity(ref.entity(),caller,true);authorizeRevision("revision-complete",caller);var frozen=lockedRevision(ref,caller);requireRevisionVersion(frozen,expectedVersion==null?null:expectedVersion.longValue());
        if(frozen.revisionState()!=Revision.State.FROZEN || frozen.effective())throw invalid("REVISION_STATE_INVALID","Revision must be frozen and not effective");
        var effective=effective(lockedHistory(ref.entity(),caller));
        if(!Objects.equals(frozen.getBaseEntityVersion(),Math.toIntExact(current.getVersion())) || !Objects.equals(frozen.getBaseEffectiveRevisionId(),effective==null?null:effective.getId()))
            throw invalid("REVISION_BASE_CHANGED","Current content changed after this draft was created");
        var proposed=copy(current);var values=new LinkedHashMap<String,Object>();var snapshot=businessValues(asBusiness(frozen));
        definition().fields().stream().filter(BusinessFieldDescriptor::writable).forEach(field->values.put(field.code(),snapshot.get(field.code())));patchBusinessFields(proposed,values);
        beforeUpdate(copy(current),proposed);if(!Objects.equals(proposed.getProjectId(),current.getProjectId()))throw invalid("ENTITY_SCOPE_DENIED","Revision cannot move its business project");
        var previousActivation=activating.get();activating.set(ref.entity());
        try{
            extensionApi().copy(EntityDataRef.revision(ref),EntityDataRef.current(ref.entity()),current.getVersion(),caller);
            formApi().copy(EntityDataRef.revision(ref),EntityDataRef.current(ref.entity()),current.getVersion(),caller);
            persistBusinessChange(current,proposed,"revision-complete");
        }finally{if(previousActivation==null)activating.remove();else activating.set(previousActivation);}
        if(effective!=null){effective.setEffective(false);persistRevision(effective,caller);}frozen.setEffective(true);persistRevision(frozen,caller);
        return frozen.revisionMetadata();
    }
    @Override public void discard(RevisionRef ref,EntityActor caller){
        var current=requireEntity(ref.entity(),caller,true);authorizeRevision("revision-discard",caller);var draft=lockedRevision(ref,caller);requireDraft(draft);beforeRevisionDiscard(copy(current),draft);
        DeclaredBusinessCurrentRows.delete(revisionBinding,new DeclaredBusinessCurrentRows.DeleteCommand(caller.tenantId(),draft.getId(),draft.getVersion(),caller.userId().toString()));
    }
    @Override public Map<String,EntityFieldValue> read(EntityDataRef target,EntityActor caller){
        if(!target.isRevision())return super.read(target,caller);requireEntity(target.entity(),caller,false);var row=revision(new RevisionRef(target.entity(),target.revisionId()),caller);
        var result=new LinkedHashMap<String,EntityFieldValue>();readableValues(asBusiness(row)).forEach((key,value)->result.put(key,EntityFieldValue.known(value)));return result;
    }
    @Override public void requireReadable(EntityDataRef target,EntityActor caller){if(target.isRevision())read(target,caller);else super.requireReadable(target,caller);}
    @Override public Long concurrencyBasis(EntityDataRef target,EntityActor caller){if(!target.isRevision())return super.concurrencyBasis(target,caller);requireEntity(target.entity(),caller,false);return revision(new RevisionRef(target.entity(),target.revisionId()),caller).getVersion();}
    @Override public void lockForWrite(EntityDataRef target,EntityActor caller,Long expectedVersion){
        if(!target.isRevision()){
            super.lockForWrite(target,caller,expectedVersion);
            if(!target.entity().equals(activating.get()) && DeclaredBusinessCurrentRows.maxRevisionNo(revisionBinding,new DeclaredCurrentRowQuery(caller.tenantId(),target.entity().entityId()))>0)
                throw invalid("REVISION_REQUIRED","Current form and extension writes must use a revision");
            return;
        }
        requireEntity(target.entity(),caller,true);authorizeRevision("revision-save",caller);var row=lockedRevision(new RevisionRef(target.entity(),target.revisionId()),caller);requireDraft(row);requireRevisionVersion(row,expectedVersion);
    }
    @Override protected final void validateFramework(E entity,String operation){
        if(entity.getId()!=null && Set.of("save","save-form").contains(operation)
                && DeclaredBusinessCurrentRows.maxRevisionNo(revisionBinding,new DeclaredCurrentRowQuery(entity.getTenantId(),entity.getId()))>0)
            throw invalid("REVISION_REQUIRED","Content with revision history must be changed through a draft");
    }
    @Override protected final void requireFrameworkDelete(E current){
        if(DeclaredBusinessCurrentRows.maxRevisionNo(revisionBinding,new DeclaredCurrentRowQuery(current.getTenantId(),current.getId()))>0)
            throw invalid("DELETE_REFERENCED_ENTITY","Revision history protects the current business identity");
    }
    protected void beforeRevisionCreate(E current){beforeUpdate(copy(current),copy(current));}
    protected void beforeRevisionSave(E current,R draft){}
    protected void beforeRevisionFreeze(E current,R draft){}
    protected void beforeRevisionDiscard(E current,R draft){}
    private E requireEntity(EntityRef ref,EntityActor caller,boolean write){
        requireProviderCaller(EntityDataRef.current(ref),caller);var row=get(ref.entityId());
        if(write){BusinessEntitySaveSupport.requireTransaction();defaults.projects().requireWritable(row.getProjectId(),caller,true);row=lock(row.getId(),caller);}return row;
    }
    private void authorizeRevision(String operation,EntityActor caller){defaults.permissions().requireWritable(definition(),caller,"operation:"+operation);}
    private List<R> lockedHistory(EntityRef entity,EntityActor caller){
        return DeclaredBusinessCurrentRows.activeRevisionsForUpdate(revisionBinding,new DeclaredCurrentRowQuery(caller.tenantId(),entity.entityId())).stream()
                .map(revisionClass::cast).map(row->checked(row,new RevisionRef(entity,row.getId()),caller)).toList();
    }
    private R effective(List<R> rows){var effective=rows.stream().filter(MutableEntityRevision::effective).toList();if(effective.size()>1)throw invalid("REVISION_STATE_INVALID","Multiple effective revisions");return effective.isEmpty()?null:effective.getFirst();}
    private R revision(RevisionRef ref,EntityActor caller){return checked(revisionMapper.selectById(ref.revisionId()),ref,caller);}
    private R lockedRevision(RevisionRef ref,EntityActor caller){return checked(revisionClass.cast(DeclaredBusinessCurrentRows.lock(revisionBinding,new DeclaredCurrentRowQuery(caller.tenantId(),ref.revisionId()))),ref,caller);}
    private R checked(R row,RevisionRef ref,EntityActor caller){if(row==null || !caller.tenantId().equals(row.getTenantId()) || !ref.equals(row.revisionRef()))throw invalid("REVISION_IDENTITY_INVALID","Revision does not belong to this business");return row;}
    private R newRevision(){try{return revisionClass.getDeclaredConstructor().newInstance();}catch(ReflectiveOperationException failure){throw invalid("REVISION_MAPPING_INVALID","Revision needs an accessible no-argument constructor");}}
    private E asBusiness(R row){return entityClass().cast(row);}
    private void requireDraft(R row){if(row.revisionState()!=Revision.State.DRAFT)throw invalid("REVISION_STATE_INVALID","Frozen revisions are immutable");}
    private void requireRevisionVersion(R row,Long version){if(version==null || !version.equals(row.getVersion()))throw invalid("CONCURRENCY_CONFLICT","Revision concurrency basis is stale");}
    @SuppressWarnings({"rawtypes","unchecked"}) private void persistRevision(R row,EntityActor caller){
        var nulls=new HashSet<String>();businessValues(asBusiness(row)).forEach((key,value)->{if(value==null)nulls.add(key);});row.setUpdater(caller.userId().toString());row.setUpdateTime(LocalDateTime.now());
        DeclaredBusinessEntityWriter.update((BaseMapper)revisionMapper,row,caller.tenantId(),row.getVersion(),nulls);
    }
    private EntityExtensionApi extensionApi(){var port=extensions.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Extension service is required for versioned content");return port;}
    private EntityFormApi formApi(){var port=forms.getIfAvailable();if(port==null)throw invalid("CAPABILITY_UNAVAILABLE","Form service is required for versioned content");return port;}
    private static BusinessContractException invalid(String code,String message){return new BusinessContractException(code,message);}
}
