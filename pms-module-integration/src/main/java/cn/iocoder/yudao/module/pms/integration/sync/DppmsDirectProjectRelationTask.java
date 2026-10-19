package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDirectProjectRelationTemplate.*;

@Component
public class DppmsDirectProjectRelationTask implements DataSyncAdapter {
    record RestorationProof(Long retiredBindingId,Long creationRunId,Long lastRunId,Relation withdrawnBefore) {}
    private final DirectProjectRelationPersistence relations;
    public DppmsDirectProjectRelationTask(DirectProjectRelationMapper relationMapper,DirectRelationContractMapper contractMapper,
            javax.sql.DataSource dataSource,cn.iocoder.yudao.module.pms.project.api.migration.ProjectMigrationIdentityContextApi identity,
            cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi organization,
            cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions,cn.iocoder.yudao.module.system.api.user.AdminUserApi actors,
            SystemUserMigrationApi users,cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi projectIdentities,
            SyncTaskService tasks,SyncRunMapper runs,SyncBindingMapper bindings,DppmsProjectBindingApi projectBindings) {
        this.relations=new DirectProjectRelationPersistence(dataSource,relationMapper,contractMapper,identity,organization,permissions,actors,users,projectIdentities);
        this.users=users;this.tasks=tasks;this.runs=runs;this.bindings=bindings;this.projectBindings=projectBindings;
    }
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final DppmsProjectBindingApi projectBindings;
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean restoresSourceBindings() { return true; }
    @Override public Descriptor descriptor() {
        var columns=new ArrayList<>(columns());columns.addAll(List.of(DUPLICATE_GROUP_SIZE,"expectedProjectVersion","contractId","reviewedBefore","migrationDecision","migrationRestoration"));
        columns.addAll(STREAM_COLUMNS);
        columns.addAll(DppmsProjectContractFormalDecision.columns().stream().filter(name->!columns.contains(name)).toList());
        return new Descriptor(ADAPTER,"DPPMS正式项目合同关系",List.of(new ObjectDescriptor(OBJECT,"原合同及完整组桥",
                columns.stream().map(name->new Field(name,name,"STRING",false)).toList(),"COM","ProjectContractRelation",
                "com_project_contract_relation",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override public void refreshCaches() {}
    /** Source identities stay separate; shared records retain complete Owner protection. */
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch) { return execute(batch,true); }
    private List<Change> execute(Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || !batch.full() || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy())) fail("禁止清空或删除缺项");
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1])) fail("运行所有权无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus())) fail("运行或配置版本失效");
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings()) if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null) fail("来源绑定重复");
        var included=new ArrayList<Row>();var skipped=new LinkedHashMap<String,Change>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(columns()) || row.fields().containsKey("_sourcePrimaryKey")) fail("原7列及完整桥表字段缺失");
            if("STREAMING_CURSOR".equals(definition.effectiveReadStrategy()) && !DppmsProjectContractFormalDecision.present(row.fields())
                    && (!row.fields().keySet().containsAll(STREAM_COLUMNS)
                    || !Objects.equals(row.sourceKey(),DppmsProjectContractDuplicateDecision.orderedSourceKey(row.fields()))))
                fail("整表流式任务缺少完整分组元数据或稳定来源顺序");
            String exclusion=approvedExclusion(row);
            if(exclusion!=null) {
                if(row.targetId()!=null || previous.containsKey(row.sourceKey()) || row.fields().get("reviewedBefore")!=null) fail("来源排除不授权撤回现存关系");
                if(skipped.put(row.sourceKey(),new Change(OBJECT,row.sourceKey(),null,"SKIPPED",Map.of(),
                        Map.of("originalFields",original(row.fields())),exclusion))!=null) fail("原来源身份重复");
            } else included.add(row);
        }
        boolean formal=included.stream().anyMatch(row->DppmsProjectContractFormalDecision.present(row.fields()));
        var formalDecisions=formal?DppmsProjectContractFormalDecision.resolve(included):Map.<String,DppmsProjectContractFormalDecision.Selection>of();
        var projectKeys=new HashSet<String>();var actorNames=new HashSet<String>();
        for(var row:included) {
            var f=row.fields();String id=text(f.get("id")),member=text(f.get("member_id")),project=text(f.get("originalProjectId"));
            if(f.containsKey("projectCandidateCount") && !"1".equals(text(f.get("projectCandidateCount"))))
                fail("完整原项目候选不唯一，不能先写一条再等待重复来源检测");
            SyncFieldMapper.primaryKey(id);SyncFieldMapper.primaryKey(member);SyncFieldMapper.primaryKey(project);
            requireConfiguredDecision(row);
            if(!validSourceKey(row) || f.get("group_id")==null
                    || !Objects.equals(text(f.get("projectGroupCode")),text(f.get("member_projectGroupCode")))
                    || !Objects.equals(text(f.get("projectGroupCode")),text(f.get("group_projectGroupCode")))
                    || !Objects.equals(text(f.get("member_projectCode")),text(f.get("originalProjectCode"))) && !approvedHistorical(row)) fail("组、组成员或原项目未精确匹配；未经裁决的历史桥阻断");
            projectKeys.add(project);
            for(String name:List.of("createBy","updateBy")) {
                var selection=formalDecisions.get(row.sourceKey());
                String actor=selection==null?text(auditValue(f,name)):"createBy".equals(name)?selection.creator():selection.updater();
                if(actor!=null && !ProjectContractRelationMigrationApi.UNLINKED_AUDIT_CODES.contains(actor)) actorNames.add(actor);
            }
        }
        var projectIds=new HashMap<String,Long>();
        var projectReferences=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        if(!projectKeys.isEmpty()) for(var binding:projectBindings.resolve(new DppmsProjectBindingApi.BatchQuery(tenant,task.getConnectionId(),List.copyOf(projectKeys)))) {
            Long prior=projectIds.putIfAbsent(binding.sourceProjectKey(),binding.projectId());
            if(binding.projectId()==null || prior!=null && !prior.equals(binding.projectId())) fail("原项目正式绑定多义");
            projectReferences.put(binding.sourceProjectKey(),binding);
        }
        if(!projectKeys.containsAll(projectIds.keySet())) fail("返回了范围外项目绑定");
        included.removeIf(row->{
            if(projectIds.containsKey(text(row.fields().get("originalProjectId"))))return false;
            if(row.targetId()!=null || previous.containsKey(row.sourceKey()))fail("现存关系的项目绑定缺失，禁止接管");
            skipped.put(row.sourceKey(),new Change(OBJECT,row.sourceKey(),null,"ISSUE",Map.of(),row.fields(),
                    "PROJECT_BINDING_MISSING：先迁入规范项目主档"));return true;
        });
        if(included.isEmpty())return batch.rows().stream().map(row->skipped.get(row.sourceKey())).toList();
        for(var row:included) {
            var reference=projectReferences.get(text(row.fields().get("originalProjectId")));
            if(!Objects.equals(reference.originalProjectCode(),text(row.fields().get("originalProjectCode")))
                    || !Objects.equals(reference.projectType(),text(row.fields().get("group_projectType"))))
                fail("原项目类型或原编码与正式迁移绑定不一致");
        }
        var actors=new HashMap<String,Long>();
        var requestedActors=new ArrayList<>(actorNames);
        for(int offset=0;offset<requestedActors.size();offset+=200)
            for(var identity:users.getHistoricalIdentitiesByUsername(tenant,new HashSet<>(requestedActors.subList(offset,Math.min(offset+200,requestedActors.size())))))
                if(!actorNames.contains(identity.username()) || actors.put(identity.username(),identity.id())!=null) fail("原审计人员身份不唯一");
        if(!actors.keySet().equals(actorNames)) fail("原审计人员缺少已核实账号，等待裁决");
        var requestedReferences=new ArrayList<Reference>();
        for(var row:included) {
            var project=projectReferences.get(text(row.fields().get("originalProjectId")));
            requestedReferences.add(new Reference(row.sourceKey(),project.projectId(),project.projectType(),project.projectCode(),text(row.fields().get("contractNo"))));
        }
        var resolved=included.isEmpty()?List.<ResolvedReference>of():relations.resolveReferences(new ReferenceQuery(
                tenant,SyncFieldMapper.primaryKey(run.getCreator()),batch.owner(),List.copyOf(requestedReferences)));
        if(resolved==null || resolved.size()!=included.size()) fail("正式目标引用回执数量不一致");
        var targetReferences=new HashMap<String,ResolvedReference>();
        for(var ref:resolved) if(ref==null || targetReferences.put(ref.sourceKey(),ref)!=null) fail("正式目标引用回执重复");
        if(!targetReferences.keySet().equals(new HashSet<>(included.stream().map(Row::sourceKey).toList()))) fail("正式目标引用来源身份不一致");
        for(var row:included) {
            var ref=targetReferences.get(row.sourceKey());var f=row.fields();
            if(!Objects.equals(ref.projectId(),projectIds.get(text(f.get("originalProjectId"))))
                    || ref.projectVersion()==null || ref.projectVersion()<0
                    || f.get("expectedProjectVersion")!=null && !nonnegative(f.get("expectedProjectVersion")).equals(ref.projectVersion())
                    || ref.issue()==null && (ref.contractId()==null || ref.contractId()<=0
                    || f.get("contractId")!=null && !positive(f.get("contractId")).equals(ref.contractId()))
                    || ref.issue()!=null && ref.contractId()!=null)
                fail("正式目标解析与项目绑定或显式版本/合同引用不一致");
        }
        included.removeIf(row->{
            var ref=targetReferences.get(row.sourceKey());
            if(ref.issue()==null)return false;
            boolean conflict=ref.issue()==ReferenceIssue.CONTRACT_AMBIGUOUS || row.targetId()!=null
                    || previous.containsKey(row.sourceKey()) || row.fields().get("contractId")!=null;
            skipped.put(row.sourceKey(),new Change(OBJECT,row.sourceKey(),row.targetId(),conflict?"CONFLICT":"ISSUE",
                    Map.of(),row.fields(),ref.issue().name()+(ref.issue()==ReferenceIssue.PROJECT_COMPANY_MISSING
                    ?"：项目公司缺失，不能确定合同公司；不猜配公司、不创建关系"
                    :"：正式公司和合同号未唯一匹配，需先核实合同主档；不选择目标、不创建关系")));
            return true;
        });
        if(apply && skipped.values().stream().anyMatch(change->"CONFLICT".equals(change.action())))
            fail("正式合同引用冲突，整块停止写入");
        if(included.isEmpty())return batch.rows().stream().map(row->skipped.get(row.sourceKey())).toList();
        var duplicates=formal?Map.<String,DppmsProjectContractDuplicateDecision.Canonical>of():DppmsProjectContractDuplicateDecision.resolve(included);
        var canonicalBindings=new HashMap<String,Binding>();
        var canonicalKeys=formal?formalDecisions.values().stream().flatMap(selection->java.util.stream.Stream.of(
                DppmsProjectContractFormalDecision.canonicalBindingKey(selection),selection.sourceKey())).distinct().toList():
                included.stream().filter(row->row.fields().containsKey("canonicalOriginalId"))
                .flatMap(row->java.util.stream.Stream.of(DppmsProjectContractDuplicateDecision.canonicalBindingKey(row),duplicates.get(row.sourceKey()).sourceKey())).distinct().toList();
        if(!canonicalKeys.isEmpty()) {
            var source=definition.sources().stream().filter(item->OBJECT.equals(item.object())).findFirst().orElseThrow();
            for(var binding:bindings.selectSourceKeys(new SyncQueries.SourceKeys(tenant,task.getId(),OBJECT,source.sourceObject(),canonicalKeys))) {
                if(binding.getTargetId()==null || binding.getFieldsJson()==null || canonicalBindings.put(binding.getSourceKey(),
                        new Binding(OBJECT,binding.getSourceKey(),binding.getTargetId(),JsonUtils.parseMap(binding.getFieldsJson())))!=null)
                    fail("规范来源绑定缺失或重复");
            }
        }
        var restorationKeys=included.stream().filter(row->!previous.containsKey(row.sourceKey())
                && row.fields().get("migrationRestoration")!=null).map(Row::sourceKey).toList();
        var retired=new HashMap<String,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO>();
        if(!restorationKeys.isEmpty()) {
            var source=definition.sources().stream().filter(item->OBJECT.equals(item.object())).findFirst().orElseThrow();
            for(var binding:bindings.selectRetiredSourceKeysForUpdate(new SyncQueries.SourceKeys(
                    tenant,task.getId(),OBJECT,source.sourceObject(),restorationKeys)))
                if(retired.put(binding.getSourceKey(),binding)!=null) fail("同一来源存在多个撤回绑定");
        }
        var receipts=new HashMap<Long,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO>();
        var entries=new LinkedHashMap<String,Entry>();var originals=new LinkedHashMap<String,Map<String,Object>>();
        var ownerKeys=new LinkedHashMap<String,String>();
        var countedSources=new HashSet<String>();
        for(var row:included) {
            if(row.fields().containsKey(DUPLICATE_GROUP_SIZE)) countedSources.add(row.sourceKey());
            var f=row.fields();var raw=original(f);if(originals.put(row.sourceKey(),raw)!=null) fail("原合同与组成员复合身份重复");
            var prior=previous.get(row.sourceKey());
            if(row.targetId()!=null && prior==null || prior!=null && !Objects.equals(row.targetId(),prior.targetId())) fail("目标与当前来源绑定不一致");
            Relation last=prior==null?null:decode(prior.lastFields().get("relation"));
            if(prior!=null && (last==null || !sameOriginal(raw,prior.lastFields().get("originalFields")))) fail("原合同或桥记录发生变化，禁止自动接管");
            Relation reviewed=decode(f.get("reviewedBefore"));
            // After the adjudicated write, the binding supersedes the unchanged original review snapshot.
            if(prior!=null) reviewed=null;
            if(reviewed!=null && !batch.adoptExisting()) fail("具体存量纠正未批准");
            Long targetId=prior==null?reviewed==null?null:reviewed.id():row.targetId();
            Restoration restoration=null;
            if(prior==null && f.get("migrationRestoration")!=null) {
                var proof=JsonUtils.parseObject(JsonUtils.toJsonString(f.get("migrationRestoration")),RestorationProof.class);
                var old=retired.get(row.sourceKey());
                if(proof==null || proof.creationRunId()==null || proof.lastRunId()==null || proof.withdrawnBefore()==null
                        || old==null || !Objects.equals(old.getId(),proof.retiredBindingId())
                        || !Objects.equals(old.getTargetId(),proof.withdrawnBefore().id())
                        || !Objects.equals(old.getLastRunId(),proof.lastRunId())) fail("原撤回绑定、目标或成功回执不匹配");
                for(Long receiptId:List.of(proof.creationRunId(),proof.lastRunId())) {
                    var receipt=receipts.computeIfAbsent(receiptId,id->runs.selectScoped(new SyncQueries.Id(tenant,id)));
                    if(receipt==null || !Objects.equals(receipt.getTaskId(),task.getId())
                            || !Objects.equals(receipt.getTenantId(),tenant) || !Boolean.FALSE.equals(receipt.getPreview())
                            || !"SUCCESS".equals(receipt.getStatus())) fail("原创建或重放回执无效，禁止恢复");
                }
                var summary=JsonUtils.parseMap(receipts.get(proof.creationRunId()).getSummaryJson());
                if(summary==null || !(summary.get("CREATED") instanceof Number count) || count.longValue()<=0)
                    fail("缺少原成功创建回执");
                var saved=JsonUtils.parseMap(old.getFieldsJson());
                if(saved==null || !sameOriginal(raw,saved.get("originalFields"))) fail("撤回绑定的完整原字段已改变");
                var selected=formalDecisions.get(row.sourceKey());
                if(selected!=null && (!saved.containsKey("formalDecision")
                        || !DppmsProjectContractFormalDecision.sameProof(selected,saved.get("formalDecision"))))
                    fail("撤回绑定的完整身份归并事实已改变");
                last=decode(saved.get("relation"));targetId=old.getTargetId();
                if(last==null || !Objects.equals(last.id(),targetId) || !Objects.equals(last.tenantId(),tenant))
                    fail("撤回绑定的完整正式前值缺失");
                restoration=new Restoration(proof.withdrawnBefore());
            }
            var duplicate=duplicates.get(row.sourceKey());
            var selection=formalDecisions.get(row.sourceKey());
            if(selection!=null && prior!=null && prior.lastFields().containsKey("formalDecision")
                    && !DppmsProjectContractFormalDecision.sameProof(selection,prior.lastFields().get("formalDecision"))) fail("完整身份归并事实已改变，禁止自动覆盖");
            String ownerKey=selection!=null?selection.sourceKey():duplicate==null?row.sourceKey():duplicate.sourceKey();
            LocalDateTime created=selection!=null?selection.createdAt():duplicate==null?time(auditValue(f,"createTime")):duplicate.createdAt();
            if(selection!=null && prior==null) {
                var canonical=canonicalBindings.get(DppmsProjectContractFormalDecision.canonicalBindingKey(selection));
                if(canonical==null) canonical=canonicalBindings.get(ownerKey);
                if(canonical!=null) {
                    if(!sameOriginal(selection.canonical(),canonical.lastFields().get("originalFields"))
                            || canonical.lastFields().containsKey("formalDecision") && !DppmsProjectContractFormalDecision.sameProof(selection,canonical.lastFields().get("formalDecision")))
                        fail("跨块规范来源或完整身份归并事实已改变");
                    var canonicalLast=decode(canonical.lastFields().get("relation"));
                    if(canonicalLast==null || !Objects.equals(canonical.targetId(),canonicalLast.id())) fail("跨块规范绑定的完整正式前值缺失");
                    if(restoration!=null) {
                        if(!Objects.equals(targetId,canonical.targetId()) || !restoration.restored().equals(canonicalLast))
                            fail("跨块已恢复关系与限定快照不一致");
                    } else { last=canonicalLast;targetId=canonical.targetId(); }
                }
            }
            if(prior==null && restoration==null && f.containsKey("canonicalOriginalId")) {
                var canonical=canonicalBindings.get(DppmsProjectContractDuplicateDecision.canonicalBindingKey(row));
                if(canonical==null) canonical=canonicalBindings.get(ownerKey);
                if(canonical!=null) {
                    var canonicalRaw=new LinkedHashMap<>(f);canonicalRaw.put("id",f.get("canonicalOriginalId"));canonicalRaw.put("createTime",created);
                    if(!sameOriginal(original(canonicalRaw),canonical.lastFields().get("originalFields"))) fail("跨块规范来源完整原字段已改变");
                    last=decode(canonical.lastFields().get("relation"));
                    if(last==null || !Objects.equals(canonical.targetId(),last.id())) fail("跨块规范绑定的完整正式前值缺失");
                    targetId=canonical.targetId();
                }
            }
            Object originalUpdated=auditValue(f,"updateTime");
            LocalDateTime updated=selection!=null?selection.updatedAt():originalUpdated==null?created:time(originalUpdated);
            String projectKey=text(f.get("originalProjectId"));
            var targetReference=targetReferences.get(row.sourceKey());
            var incoming=new Relation(null,tenant,projectIds.get(projectKey),targetReference.contractId(),"RELATED","DPPMS",OBJECT,
                    ownerKey,created,null,selection!=null?selection.status():approvedHistorical(row)?"HISTORICAL":"ACTIVE",0,
                    actor(selection!=null?selection.creator():auditValue(f,"createBy"),actors),created,
                    actor(selection!=null?selection.updater():auditValue(f,"updateBy"),actors),updated,false);
            var entry=new Entry(ownerKey,targetId,last,reviewed,incoming,projectKey,projectReferences.get(projectKey).projectCode(),
                    targetReference.projectVersion(),text(f.get("contractNo")),task.getConnectionId(),restoration);
            var previousEntry=entries.putIfAbsent(ownerKey,entry);
            if(previousEntry!=null && !previousEntry.equals(entry)) {
                if(previousEntry.restoration()!=null && restoration!=null
                        || !restoredEntry(previousEntry).equals(restoredEntry(entry))) fail("获批重复记录的完整目标保护或绑定不一致");
                if(restoration!=null) entries.put(ownerKey,entry);
            }
            ownerKeys.put(row.sourceKey(),ownerKey);
        }
        var request=new Request(tenant,SyncFieldMapper.primaryKey(run.getCreator()),batch.owner(),List.copyOf(entries.values()),batch.adoptExisting());
        var results=entries.isEmpty()?List.<Result>of():apply?relations.apply(request):relations.preview(request);
        if(results.size()!=entries.size() || !new HashSet<>(results.stream().map(Result::sourceKey).toList()).equals(entries.keySet())) fail("COM回执数量或来源身份不一致");
        var byKey=new HashMap<String,Result>();results.forEach(result->byKey.put(result.sourceKey(),result));
        if(apply) for(var item:retired.entrySet()) {
            var result=byKey.get(ownerKeys.get(item.getKey()));
            if(result==null || !Set.of("UPDATED","UNCHANGED").contains(result.action()) || result.after()==null
                    || !Objects.equals(result.after().id(),item.getValue().getTargetId())
                    || bindings.restoreVerifiedBinding(item.getValue())!=1) fail("已核验来源绑定恢复冲突，当前块回滚");
        }
        var changes=new HashMap<String,Change>(skipped);
        for(var item:ownerKeys.entrySet()) {
            var result=byKey.get(item.getValue());var duplicate=duplicates.get(item.getKey());
            if("CONFLICT".equals(result.action())) {
                changes.put(item.getKey(),new Change(OBJECT,item.getKey(),result.before().id(),"CONFLICT",
                        encode(result.before()),Map.of("originalFields",originals.get(item.getKey())),
                        "RELATION_NOT_CURRENT：现存关系已删除、失效或版本缺失，禁止复活；等待限定恢复裁决"));
                continue;
            }
            var dispositions=fieldDecisions(originals.get(item.getKey()));
            var selection=formalDecisions.get(item.getKey());
            if(selection!=null) dispositions.putAll(DppmsProjectContractFormalDecision.fieldDecisions(originals.get(item.getKey()),selection));
            String message="正式合同关系；合同关系审计为主、成员审计补缺；拆分备注不迁入，由正式主子项目功能处理";
            if(dispositions.values().stream().anyMatch(value->value.startsWith("不独立迁入")))
                message+="；不同成员审计按裁决不独立迁入，逐字段原因见fieldDecisions";
            if(selection!=null) message+="；Q018按规范项目＋合同归并，当前来源优先、最早创建、最近非空更新；每个原ID独立绑定";
            else if(duplicate!=null && countedSources.contains(item.getKey()))
                message+="；Q-LEGACY-CONTRACT-DUPLICATE-POLICY-20261012-008共用关系 "+duplicate.sourceKey()
                        +(duplicate.identical()?"；原6项业务及审计值全部同值承接":item.getKey().equals(duplicate.sourceKey())?"，createTime承接最早原值":"；createTime不独立迁入正式审计：重复登记时间，不构成不同关系事实")
                        +"；每个原ID独立绑定";
            else if(duplicate!=null && duplicate.identical()) message+="；Q-LEGACY-CONTRACT-IDENTICAL-RELATIONS-20261012-003共用关系 "+duplicate.sourceKey()
                    +"；原6项业务及审计值全部同值承接，每个原ID独立绑定";
            else if(duplicate!=null) message+="；Q-LEGACY-CONTRACT-DUPLICATE-TIMES-20261012-004共用关系 "+duplicate.sourceKey()
                    +(item.getKey().equals(duplicate.sourceKey())?"，createTime承接最早原值":"；createTime不独立迁入正式审计：重复登记时间，不构成不同关系事实");
            var after=new LinkedHashMap<String,Object>();after.put("relation",encode(result.after()));
            after.put("originalFields",originals.get(item.getKey()));after.put("fieldDecisions",dispositions);
            if(selection!=null) after.put("formalDecision",selection.proof());
            changes.put(item.getKey(),new Change(OBJECT,item.getKey(),result.after().id(),result.action(),encode(result.before()),after,message));
        }
        return batch.rows().stream().map(row->changes.get(row.sourceKey())).toList();
    }
    private static Entry restoredEntry(Entry entry) {
        return entry.restoration()==null?entry:new Entry(entry.sourceKey(),entry.targetId(),entry.restoration().restored(),
                entry.reviewedBefore(),entry.incoming(),entry.sourceProjectKey(),entry.sourceProjectCode(),
                entry.expectedProjectVersion(),entry.contractNo(),entry.sourceConnectionId());
    }
    private static boolean approvedHistorical(Row row) {
        var f=row.fields();
        String code=text(f.get("member_projectCode"));
        // Old list joins remove all repeated historical suffixes, not only the last one.
        return code!=null && code.endsWith("-his") && code.replace("-his","").equals(text(f.get("originalProjectCode")));
    }
    static Object auditValue(Map<String,Object> fields,String name) {
        Object value=fields.get(name);
        String raw=text(value);
        if(raw!=null && !raw.isEmpty()) return value;
        Object fallback=fields.get("member_"+name);
        String fallbackRaw=text(fallback);
        return fallbackRaw==null || fallbackRaw.isEmpty()?null:fallback;
    }
    /** Field disposition explains the approved exclusions; originals remain immutable source evidence. */
    private static Map<String,String> fieldDecisions(Map<String,Object> original) {
        var result=new LinkedHashMap<String,String>();
        for(String name:List.of("createTime","createBy","updateTime","updateBy")) {
            String primary=text(original.get(name)),member=text(original.get("member_"+name));
            String disposition=member==null || member.isEmpty()?"原缺值，无额外审计事实":primary==null || primary.isEmpty()
                    ?"补齐正式同名审计字段":Objects.equals(primary,member)?"同义原值复用正式审计字段"
                    :"不独立迁入：同一正式关系只有一组审计，按裁决采用合同关系主值";
            result.put("member_"+name,disposition);
        }
        result.put("member_mergeBranchMark","不迁入：用户裁决拆分由新系统正式主子项目功能处理");
        return result;
    }
    private static String approvedExclusion(Row row) {
        var f=row.fields();
        if(!(f.get("migrationDecision") instanceof Map<?,?> decision)) return null;
        String action=text(decision.get("action"));
        if(!Set.of("SKIP_MISSING_GROUP","SKIP_MISSING_PROJECT_MEMBER","SKIP_MISSING_GROUP_MEMBER").contains(action==null?"":action)) return null;
        requireConfiguredDecision(row);
        if(!validSourceKey(row)) fail("已裁决排除记录的原身份改变");
        if("SKIP_MISSING_GROUP".equals(action)) {
            if(columns().stream().filter(name->!ORIGINAL_COLUMNS.contains(name)).anyMatch(name->f.get(name)!=null))
                fail("已裁决缺组记录的原字段或关联状态改变");
            return "用户明确不迁入：原项目组及成员不存在，无法形成项目合同关联";
        }
        if("SKIP_MISSING_GROUP_MEMBER".equals(action)) {
            if(f.get("group_id")==null || f.get("group_projectType")==null
                    || !Objects.equals(text(f.get("projectGroupCode")),text(f.get("group_projectGroupCode")))
                    || MEMBER_COLUMNS.stream().anyMatch(name->f.get("member_"+name)!=null)
                    || f.get("originalProjectId")!=null || f.get("originalProjectCode")!=null
                    || f.get("projectCandidateCount")!=null && !"0".equals(text(f.get("projectCandidateCount"))))
                fail("已裁决缺成员记录的关联已恢复或完整原组不一致");
            return "Q019用户明确不迁入：原项目组无成员，无法确定项目归属；完整原始证据保留";
        }
        // The task's frozen full original fields determine the approved scope; never skip all missing joins.
        if(f.get("originalProjectId")!=null || f.get("originalProjectCode")!=null
                || f.get("projectCandidateCount")!=null && !"0".equals(text(f.get("projectCandidateCount")))
                || f.get("group_id")==null || f.get("member_id")==null || f.get("group_projectType")==null
                || !Objects.equals(text(f.get("projectGroupCode")),text(f.get("member_projectGroupCode")))
                || !Objects.equals(text(f.get("projectGroupCode")),text(f.get("group_projectGroupCode"))))
            fail("已裁决缺项目成员的关联已恢复或完整原桥不一致");
        return "Q016用户明确不迁入关系：原项目身份不存在，不能挂靠其他类型或同名项目";
    }
    private static boolean validSourceKey(Row row) {
        var f=row.fields();
        if(DppmsProjectContractFormalDecision.present(f)) return Objects.equals(row.sourceKey(),DppmsProjectContractFormalDecision.orderedSourceKey(f));
        String originalKey=text(f.get("id"))+":"+(f.get("member_id")==null?"0":text(f.get("member_id")));
        return f.containsKey("canonicalOriginalId") ? Objects.equals(row.sourceKey(),DppmsProjectContractDuplicateDecision.orderedSourceKey(f))
                : Objects.equals(row.sourceKey(),originalKey);
    }
    private static void requireConfiguredDecision(Row row) {
        var value=row.fields().get("migrationDecision");
        if(value==null) return;
        if(!(value instanceof Map<?,?> decision) || !(decision.get("originalFields") instanceof Map<?,?> raw)
                || raw.size()!=columns().size() || !raw.keySet().containsAll(columns()))
            fail("任务配置中的裁决原字段或关联关系已改变");
        var expected=new LinkedHashMap<String,Object>();
        ((Map<?,?>)((Map<?,?>)value).get("originalFields")).forEach((key,item)->expected.put(key.toString(),item));
        if(!sameOriginal(original(row.fields()),original(expected))) fail("任务配置中的裁决原字段或关联关系已改变");
    }
    static Map<String,Object> original(Map<String,Object> fields) {
        var raw=new LinkedHashMap<String,Object>();
        for(String name:columns()) {
            Object value=fields.get(name);
            if(value!=null && (name.endsWith("Time"))) value=time(value).toString();
            raw.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value.toString());
        }
        return raw;
    }
    private static boolean sameOriginal(Map<String,Object> raw,Object saved) {
        if(!(saved instanceof Map<?,?> before) || !before.keySet().equals(raw.keySet())) return false;
        return raw.keySet().stream().allMatch(name->Objects.equals(text(raw.get(name)),text(before.get(name))));
    }
    private static Map<String,Object> encode(Relation row) {
        if(row==null) return Map.of();
        var result=new LinkedHashMap<String,Object>(JsonUtils.parseMap(JsonUtils.toJsonString(row)));
        for(String name:List.of("id","tenantId","projectId","contractId","relationRole","sourceSystem","sourceTable","sourceRecordKey",
                "effectiveFrom","effectiveTo","status","version","creator","createTime","updater","updateTime","deleted"))
            result.putIfAbsent(name,tools.jackson.databind.node.NullNode.getInstance());
        return result;
    }
    private static Relation decode(Object value) {
        return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:
                JsonUtils.parseObject(value instanceof String text?text:JsonUtils.toJsonString(value),Relation.class);
    }
    private static String actor(Object value,Map<String,Long> actors) {
        String name=text(value);
        return name==null?null:ProjectContractRelationMigrationApi.UNLINKED_AUDIT_CODES.contains(name)?name:actors.get(name).toString();
    }
    private static LocalDateTime time(Object value) { return value==null?null:SyncFieldMapper.sourceTime(value); }
    private static Long positive(Object value) { return SyncFieldMapper.primaryKey(text(value)); }
    private static Long nonnegative(Object value) {
        String raw=text(value);if(raw==null || !raw.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("项目版本缺失");return Long.valueOf(raw);
    }
    private static String text(Object value) { return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:value.toString(); }
    private static void fail(String message) { throw new IllegalArgumentException("DPPMS项目合同关系："+message); }
}
