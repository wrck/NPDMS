package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.ProjectBinding;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsPublishedProjectScopeApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.PublishedProjectScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DppmsPublishedProjectScopeService implements DppmsPublishedProjectScopeApi {
    private final PublishedProjectScopeMapper mapper;
    record Capture(SyncRunDO run, SyncDefinition definition, List<DataSyncAdapter.Change> changes) {}
    record Completion(SyncRunDO run, SyncDefinition definition) {}

    /** Resolve once per source read, pinning a complete immutable publication for all cursor chunks. */
    SyncDefinition sourceScope(SyncDefinition definition) {
        if(definition.sources().stream().noneMatch(PublishedProjectScopeRows::requested))return definition;
        long tenant=TenantContextHolder.getRequiredTenantId();
        Long publication=mapper.selectLatestRun(new PublishedProjectScopeMapper.Connection(tenant,definition.connectionId()));
        if(publication==null)throw new IllegalStateException("DPPMS_PROJECT_SCOPE_NOT_PUBLISHED");
        var projects=new ArrayList<ProjectBinding>();String after="";
        while(true) {
            var page=mapper.selectSourcePage(new PublishedProjectScopeMapper.SourcePage(tenant,definition.connectionId(),publication,after));
            if(page.isEmpty())break;
            projects.addAll(page);after=page.getLast().sourceProjectKey();
        }
        var frozen=List.copyOf(projects);
        return definition.toBuilder().sources(definition.sources().stream().map(s->
                PublishedProjectScopeRows.requested(s)?s.toBuilder().publishedProjects(frozen).build():s).toList()).build();
    }

    /** Restricted legacy master tasks keep their historical behavior and cannot replace full scope. */
    static boolean eligible(SyncDefinition definition) {
        if(!"DPPMS".equals(definition.sourceSystem()) || !DppmsProjectMasterSyncTemplate.ADAPTER.equals(definition.adapter())
                || definition.sources()==null || !"RESTART_ALL".equals(definition.effectiveRestartPolicy())
                || "KEYSET_PAGING".equals(definition.effectiveReadStrategy()))return false;
        var source=definition.sources().stream().filter(s->DppmsProjectMasterSyncTemplate.OBJECT.equals(s.object())).findFirst().orElse(null);
        if(source==null || source.parameters()==null || source.sql()==null
                || !source.sql().strip().replace("\r\n","\n").equals(DppmsMigrationSql.initial("project-master").replace("\r\n","\n"))
                || definition.sources().size()!=2 || definition.sources().stream().noneMatch(s->
                    DppmsProjectIdentitySyncTemplate.OBJECT.equals(s.object()) && "pm_project".equals(s.sourceObject())
                    && DppmsProjectMasterSyncTemplate.OBJECT.equals(s.queryRef())))return false;
        var parameters=source.parameters();
        return Objects.toString(parameters.get("projectIds"),"").isBlank()
                && new HashSet<>(Arrays.asList(Objects.toString(parameters.get("projectTypes"),"").split(",")))
                    .equals(Set.of("10","afss","afxx"));
    }

    void capture(Capture command) {
        var run=command.run();
        if(!eligible(command.definition()) || Boolean.TRUE.equals(run.getPreview()) || !Boolean.TRUE.equals(run.getFullSnapshot()))return;
        requireTransaction(run);
        Long root=run.getPageNumber()==null?run.getId():run.getParentRunId();
        for(var change:command.changes()) {
            if(!Set.of("CREATED","UPDATED","UNCHANGED").contains(change.action()))
                throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_INCOMPLETE: "+change.action());
            var project=project(command,change);
            var member=new PublishedProjectScopeMapper.Member(run.getTenantId(),root,project);
            var old=mapper.selectMember(member);
            if(old==null)mapper.insertMember(member);
            else if(!old.equals(project))throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_IDENTITY_CONFLICT");
        }
    }

    void publish(Completion command) {
        var run=command.run();
        if(!eligible(command.definition()) || Boolean.TRUE.equals(run.getPreview()) || !Boolean.TRUE.equals(run.getFullSnapshot())
                || run.getPageNumber()!=null)return;
        requireTransaction(run);
        if(!"SUCCESS".equals(run.getStatus()))throw new IllegalStateException("DPPMS_PROJECT_SCOPE_NOT_SUCCESSFUL");
        // P16 uses RESTART_ALL: every run owns a complete set, including retries and zero-row results.
        mapper.insertPublication(new PublishedProjectScopeMapper.Publication(run.getTenantId(),command.definition().connectionId(),
                run.getTaskId(),run.getId()));
    }

    @Override public Scope resolve(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.sourceConnectionId()==null || query.sourceConnectionId()<=0 || query.sourceProjectKeys()==null
                || query.sourceProjectKeys().size()>500 || query.sourceProjectKeys().stream().anyMatch(k->k==null || !k.matches("[1-9][0-9]*")))
            throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_QUERY_INVALID");
        Long run=mapper.selectLatestRun(new PublishedProjectScopeMapper.Connection(query.tenantId(),query.sourceConnectionId()));
        if(run==null)throw new IllegalStateException("DPPMS_PROJECT_SCOPE_NOT_PUBLISHED");
        return new Scope(run,query.sourceProjectKeys().isEmpty()?List.of():mapper.selectMembers(
                new PublishedProjectScopeMapper.Selection(query.tenantId(),query.sourceConnectionId(),run,query.sourceProjectKeys())));
    }

    private static ProjectBinding project(Capture command,DataSyncAdapter.Change change) {
        var run=command.run();Long connection=command.definition().connectionId();
        var row=new SyncBindingDO().setSourceKey(change.sourceKey()).setTargetId(change.targetId())
                .setFieldsJson(JsonUtils.toJsonString(change.after()));
        if(DppmsProjectMasterSyncTemplate.OBJECT.equals(change.object()))
            return DppmsProjectBindingApiImpl.masterBinding(row,run.getTenantId(),connection);
        if(!DppmsProjectIdentitySyncTemplate.OBJECT.equals(change.object()) || change.after()==null
                || !(change.after().get("originalIdentity") instanceof Map<?,?> original)
                || !(change.after().get("project") instanceof Map<?,?> target)
                || !original.keySet().containsAll(DppmsProjectIdentitySyncTemplate.COLUMNS)
                || !Objects.equals(change.sourceKey(),text(original.get("originalProjectId")))
                || change.targetId()==null || change.targetId()<=0
                || !change.targetId().toString().equals(text(target.get("id")))
                || !run.getTenantId().toString().equals(text(target.get("tenantId")))
                || !Set.of("10","afss","afxx").contains(text(original.get("projectType")))
                || !Objects.equals(text(original.get("projectType")),text(target.get("projectType")))
                || text(original.get("projectCode"))==null || text(target.get("projectCode"))==null)
            throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_EVIDENCE_INVALID");
        return new ProjectBinding(connection,change.sourceKey(),change.targetId(),text(target.get("projectType")),
                text(original.get("projectCode")),text(target.get("projectCode")));
    }
    private static String text(Object value){return value==null?null:value.toString();}
    private static void requireTransaction(SyncRunDO run) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()
                || !Objects.equals(run.getTenantId(),TenantContextHolder.getRequiredTenantId()))
            throw new IllegalStateException("DPPMS_PROJECT_SCOPE_TRANSACTION_REQUIRED");
    }
}
