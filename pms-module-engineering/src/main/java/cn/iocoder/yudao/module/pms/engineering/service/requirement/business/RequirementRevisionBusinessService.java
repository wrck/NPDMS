package cn.iocoder.yudao.module.pms.engineering.service.requirement.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.business.RequirementRevisionBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Revision content uses inherited CRUD; completing/copying retain the established multi-row version policy. */
@Service @RequiredArgsConstructor
public class RequirementRevisionBusinessService extends DefaultProjectBusinessService<RequirementRevisionBusinessMapper,RequirementAnalysisRevisionDO> {
    private final RequirementAnalysisMapper history;
    private final RequirementAnalysisAccess access;
    private final EntityVersionApi versions;
    private final RequirementAnalysisRevisionFiles files;
    private final EngineeringRuleReevaluationEvents events;
    private interface DraftInput { }
    @Override protected EntityDataRef formTarget(RequirementAnalysisRevisionDO row){return EntityDataRef.revision(row.revisionRef());}
    @Override protected void afterChange(String operation,RequirementAnalysisRevisionDO before,RequirementAnalysisRevisionDO row){if("save-form".equals(operation))afterUpdate(before,row);}
    @Override protected Long generatedId(){return IdWorker.getId();}
    @Override protected long initialVersion(){return 1L;}
    @Override protected Class<?>[] validationGroups(RequirementAnalysisRevisionDO row){return new Class<?>[]{DraftInput.class};}
    @Override protected void authorizeModelRead(EntityActor actor,String scene){access.requireReadPermission(actor);}
    @Override protected void afterRead(RequirementAnalysisRevisionDO row){access.requireRead(row.getProjectId(),actor(),"DRAFT".equals(row.getRevisionState()));}
    @Override protected void authorizeReceipt(String operation,RequirementAnalysisRevisionDO row){access.lockScope(row.getProjectId(),actor());}
    @Override protected BusinessModelViews.ModelDetailVO modelView(BusinessModelViews.ModelDetailVO view) {
        return new BusinessModelViews.ModelDetailVO(view.ownerModule(),view.entityType(),view.stableCode(),view.title(),view.viewCode(),
                view.fields().stream().map(f->new BusinessModelViews.FieldVO(f.code(),f.name(),f.type(),"projectId".equals(f.code())&&f.required(),f.readable(),f.writable())).toList(),view.operations(),view.capabilities());
    }
    @Override protected List<BusinessOperationDescriptor> configureOperations(List<BusinessOperationDescriptor> operations) {
        return operations.stream().map(op->new BusinessOperationDescriptor(op.code(),op.version(),op.code().equals("delete")?"放弃草稿":op.name(),op.kind(),"pms:requirement-analysis:manage")).toList();
    }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(new BusinessOperationDescriptor("complete",1,"完成并生效",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":manage"),
                new BusinessOperationDescriptor("copy",1,"复制为新草稿",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":manage"));
    }
    @Override protected PageResult<RequirementAnalysisRevisionDO> selectPage(BusinessReadQuery query) {
        var managers=new HashSet<Long>();for(Long project:query.projectIds())if(access.isManager(project,actor()))managers.add(project);
        return mapper.selectVisibleRevisions(new RequirementRevisionBusinessMapper.VisibleRevisionPage(query,managers));
    }
    @Override protected <T>T inBusinessOperation(String operation,RequirementAnalysisRevisionDO current,RequirementAnalysisRevisionDO proposed,Supplier<T> work) {
        var row=current==null?proposed:current;var actor=actor();String nativeOperation="SOL.REQUIREMENT_ANALYSIS."+(operation.equals("delete")?"DISCARD":operation.toUpperCase(Locale.ROOT));
        return ProjectOwnerOperationScope.call("SOL","REQUIREMENT_ANALYSIS",()->new ProjectOwnerOperationScope.Declaration(actor.tenantId(),actor.userId(),row.getProjectId(),"SOL","REQUIREMENT_ANALYSIS",nativeOperation,1,current==null?null:current.getId().toString(),null),()->{
            access.lockScope(row.getProjectId(),actor);
            var execution=access.lockExecution(row.getProjectId(),current==null?null:current.getExecutionSnapshot(),null,actor,current==null?null:current.getId());
            if(current==null)proposed.setExecutionSnapshot(JsonUtils.toJsonString(execution));
            return work.get();
        });
    }
    @Override protected void beforeCreate(RequirementAnalysisRevisionDO row) {
        if(history.selectLatest(new RequirementProjectQuery(actor().tenantId(),row.getProjectId()))!=null)throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        row.setEntityId(IdWorker.getId());row.setRevisionNo(1);row.setRevisionState("DRAFT");row.setDraftMarker(1);row.setStatusCode("DRAFT");
    }
    @Override protected void beforeUpdate(RequirementAnalysisRevisionDO before,RequirementAnalysisRevisionDO row) {
        draft(before);if(!Objects.equals(before.getProjectId(),row.getProjectId()))throw exception(REQUIREMENT_STATUS_INVALID);
    }
    @Override protected void beforeDelete(RequirementAnalysisRevisionDO row){draft(row);}
    @Override protected void afterCreate(RequirementAnalysisRevisionDO row) {
        var actor=actor();ProjectOwnerOperationScope.registerCreated(actor.tenantId(),actor.userId(),row.getProjectId(),"SOL","REQUIREMENT_ANALYSIS","SOL.REQUIREMENT_ANALYSIS.CREATE",1,null,row.getId().toString());changed(row);
    }
    @Override protected void afterUpdate(RequirementAnalysisRevisionDO before,RequirementAnalysisRevisionDO row){files.registerSaved(row.revisionRef(),actor());changed(row);}
    @Override protected void afterDelete(RequirementAnalysisRevisionDO row){changed(row);}
    @Override protected boolean publishDefaultEvent(String operation){return !Set.of("complete","copy").contains(operation);}
    @Override protected void validateDelivery(RequirementAnalysisRevisionDO row,boolean write){access.requireRead(row.getProjectId(),actor(),"DRAFT".equals(row.getRevisionState()));if(write){draft(row);access.lockScope(row.getProjectId(),actor());}}
    public BusinessOperationReceipt complete(Long id,Long version,String key) {
        return businessAction("complete",id,version,key,Map.of(),ReceiptOutcome.EFFECTED,row->{
            draft(row);var result=versions.complete(row.revisionRef(),Math.toIntExact(row.getVersion()),actor());return mapper.selectById(result.ref().revisionId());
        });
    }
    public BusinessOperationReceipt copyRevision(Long id,Long version,String key,String reason) {
        var intent=new LinkedHashMap<String,Object>();intent.put("reason",reason);
        return businessAction("copy",id,version,key,intent,ReceiptOutcome.SAVED,row->{
            var result=versions.create(row.entityRef(),row.revisionRef(),reason,actor());copyDeliveries(row.getId(),result.ref().revisionId(),"revision-copy-"+result.ref().revisionId());return mapper.selectById(result.ref().revisionId());
        });
    }
    private void draft(RequirementAnalysisRevisionDO row){if(!"DRAFT".equals(row.getRevisionState()))throw exception(REQUIREMENT_STATUS_INVALID);}
    private void changed(RequirementAnalysisRevisionDO row){events.changed(row.getProjectId(),"RequirementAnalysis",row.getId(),actor().userId(),"requirement-revision:"+row.getId()+":"+row.getVersion());}
}
