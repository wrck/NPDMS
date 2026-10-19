package cn.iocoder.yudao.module.pms.integration.sync;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationHistoryInsertApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Explicit one-off phase of P30. The saved base task never contains assignment defaults. */
@Service @RequiredArgsConstructor
public class DppmsDeviceAssignmentService {
    private final SyncTaskService taskService;
    private final SyncTaskMapper tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final DppmsDeviceBootstrapApi devices;
    private final InstallationHistoryInsertApi installations;
    private final DppmsPublishedProjectScopeApi published;
    private final ProjectMasterMigrationApi projects;
    private final PermissionApi permissions;
    public record Request(Long taskId,Integer expectedVersion,List<String> deviceSns,LocalDateTime effectiveAt,String expectedDigest) {}
    public record Item(String deviceSn,Long deviceId,Long projectId,Long shipmentId,Long assignmentVersion,String action) {}
    public record Result(String digest,Long installationRunId,List<Item> items) {}
    @Transactional(rollbackFor=Exception.class)
    public Result preview(Request request){return execute(request,false);}
    @Transactional(rollbackFor=Exception.class)
    public Result apply(Request request){return execute(request,true);}
    private Result execute(Request q,boolean apply) {
        Long actor=SecurityFrameworkUtils.getLoginUserId();long tenant=SyncTaskService.tenant();
        if(actor==null || !permissions.hasAnyPermissions(actor,"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少同步执行权限");
        if(q==null || q.deviceSns()==null || q.deviceSns().isEmpty() || q.deviceSns().size()>500 || q.effectiveAt()==null
                || q.deviceSns().stream().anyMatch(s->s==null || s.isBlank() || s.length()>100)
                || new HashSet<>(q.deviceSns()).size()!=q.deviceSns().size())fail("EXPLICIT_SCOPE_REQUIRED");
        var task=taskService.locked(q.taskId());var definition=taskService.definition(task);
        if(!Objects.equals(task.getVersion(),q.expectedVersion()) || task.getActiveRunId()!=null
                || !DppmsDeviceBootstrapSyncTemplate.ADAPTER.equals(definition.adapter()) || !"DPPMS".equals(definition.sourceSystem())
                || !"ONCE".equals(definition.mode()) || definition.mergedIntoTaskId()!=null
                || !Objects.equals(definition.sources(),DppmsDeviceBootstrapSyncTemplate.create(definition.connectionId()).sources()))fail("TASK_CHANGED");
        var candidates=tasks.selectSourceAdapter(new SyncQueries.SourceAdapter(tenant,definition.connectionId(),"DPPMS",DppmsInstallationSyncAdapter.ADAPTER));
        if(candidates.size()!=1)fail("INSTALLATION_TASK_AMBIGUOUS");
        var sourceTask=tasks.selectForUpdate(new SyncQueries.Id(tenant,candidates.getFirst().getId()));
        if(sourceTask==null || sourceTask.getActiveRunId()!=null)fail("INSTALLATION_INPUT_ACTIVE");
        var sourceRun=runs.selectLatestFormalRoot(new SyncQueries.Task(tenant,sourceTask.getId()));
        if(sourceRun==null || !"SUCCESS".equals(sourceRun.getStatus()) || !Boolean.TRUE.equals(sourceRun.getFullSnapshot())
                || !Objects.equals(sourceRun.getConfigVersion(),sourceTask.getVersion()) || sourceRun.getSummaryJson()==null)fail("INSTALLATION_INPUT_INCOMPLETE");
        var summary=JsonUtils.parseMap(sourceRun.getSummaryJson());
        for(String action:List.of("ISSUE","CONFLICT","SKIPPED")) {
            Object count=summary.getOrDefault(action,0);if(!(count instanceof Number n) || n.longValue()!=0)fail("INSTALLATION_INPUT_INCOMPLETE");
        }
        var frozen=JsonUtils.parseObject(sourceRun.getConfigSnapshot(),SyncDefinition.class);
        if(!Objects.equals(frozen.sources(),DppmsInstallationSyncTemplate.create(definition.connectionId()).sources())
                || !Objects.equals(frozen.connectionId(),definition.connectionId()) || !"DPPMS".equals(frozen.sourceSystem())
                || !DppmsInstallationSyncAdapter.ADAPTER.equals(frozen.adapter()))fail("INSTALLATION_INPUT_RESTRICTED");
        var serials=q.deviceSns().stream().sorted().toList();
        var seeds=devices.snapshot(new DppmsDeviceBootstrapApi.SnapshotQuery(tenant,actor,serials,definition.maxBytes()));
        var base=new HashMap<String,DppmsDeviceBootstrapApi.BaseResult>();
        for(var value:devices.previewBase(new DppmsDeviceBootstrapApi.BaseCommand(tenant,actor,"P30-assignment",q.effectiveAt(),seeds))) {
            if(!serials.contains(value.deviceSn()) || value.deviceId()==null || value.deviceId()<=0 || !"UNCHANGED".equals(value.action())
                    || base.put(value.deviceSn(),value)!=null)fail("BASE_NOT_CREATED");
        }
        if(!base.keySet().equals(new HashSet<>(serials)))fail("BASE_NOT_CREATED");
        var selected=new TreeMap<String,List<History>>();Long after=null;long bytes=0;
        while(true) {
            var page=bindings.selectInstallationSelectionPage(new SyncQueries.InstallationSelection(tenant,sourceTask.getId(),serials,after));
            if(page.isEmpty())break;
            for(var row:page) {
                bytes+=row.getFieldsJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes>definition.maxBytes())fail("HISTORY_SIZE_LIMIT");
                var fields=SyncBindingSnapshots.decode(row.getFieldsJson());
                if(!(fields.get("originalFields") instanceof Map<?,?> raw))throw new IllegalArgumentException("P30_HISTORY_INVALID");
                String sn=text(raw.get("barcode")),key=text(raw.get("projectId"));
                Long project=SyncFieldMapper.primaryKey(text(fields.get("projectId")));
                Long device=SyncFieldMapper.primaryKey(text(fields.get("equipmentId")));
                if(!serials.contains(sn) || !Objects.equals(device,base.get(sn).deviceId()) || row.getTargetId()==null || row.getTargetId()<=0
                        || !Objects.equals(row.getSourceKey(),text(raw.get("id"))) || project==null || project<=0
                        || !(fields.get("formalFieldsJson") instanceof String formal) || formal.isBlank())fail("HISTORY_INVALID");
                selected.computeIfAbsent(sn,k->new ArrayList<>()).add(new History(row.getSourceKey(),row.getTargetId(),device,project,key,
                        text(raw.get("contractNo")),text(fields.get("formalFieldsJson"))));
            }
            Long last=page.getLast().getId();if(last==null || after!=null && last<=after)fail("HISTORY_CURSOR_INVALID");after=last;
        }
        if(!selected.keySet().equals(new HashSet<>(serials)))fail("INSTALLATION_HISTORY_MISSING");
        var projectKeys=selected.values().stream().flatMap(Collection::stream).map(History::sourceProjectKey).distinct().toList();
        if(projectKeys.size()>500 || projectKeys.stream().anyMatch(Objects::isNull))fail("PROJECT_SCOPE_INVALID");
        var scope=published.resolve(new DppmsPublishedProjectScopeApi.Query(tenant,definition.connectionId(),projectKeys));
        var references=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        for(var project:scope.projects())if(!projectKeys.contains(project.sourceProjectKey()) || references.put(project.sourceProjectKey(),project)!=null)fail("PROJECT_SCOPE_INVALID");
        if(!references.keySet().equals(new HashSet<>(projectKeys)))fail("PROJECT_SCOPE_CHANGED");
        var commands=new ArrayList<DppmsDeviceBootstrapApi.Assignment>();var items=new ArrayList<Item>();
        for(String sn:serials) {
            var histories=selected.get(sn);Long project=histories.getFirst().projectId();
            if(histories.stream().anyMatch(h->!Objects.equals(h.projectId(),project) || !Objects.equals(references.get(h.sourceProjectKey()).projectId(),project)))fail("PROJECT_CONFLICT");
            var identity=references.get(histories.getFirst().sourceProjectKey());
            var facts=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(tenant,actor,
                    List.of(new ProjectMasterMigrationApi.IdentityTarget(project,identity.projectType(),identity.projectCode()))));
            if(facts.size()!=1 || !Objects.equals(facts.getFirst().id(),project))fail("PROJECT_SCOPE_CHANGED");
            projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(tenant,actor,facts));
            var existing=histories.stream().map(h->new InstallationHistoryInsertApi.Existing(h.sourceKey(),h.installationId(),h.deviceId(),h.formalFieldsJson())).toList();
            var verified=installations.verify(new InstallationHistoryInsertApi.Verification(tenant,actor,project,existing));
            if(verified.size()!=histories.size() || !verified.stream().map(InstallationHistoryInsertApi.Result::sourceKey).collect(java.util.stream.Collectors.toSet())
                    .equals(histories.stream().map(History::sourceKey).collect(java.util.stream.Collectors.toSet())))fail("HISTORY_CHANGED");
            var b=base.get(sn);DppmsDeviceBootstrapApi.Assignment chosen=null;DppmsDeviceBootstrapApi.AssignmentResult checked=null;
            // Every retained installation contract must pass the existing latest-shipment Owner rule.
            for(var history:histories) {
                var command=new DppmsDeviceBootstrapApi.Assignment(tenant,actor,project,b.deviceId(),history.contractNo(),b.shipmentId(),b.assignmentVersion(),
                        DigestUtil.sha256Hex("P30:"+task.getId()+":"+sourceRun.getId()+":"+sn),q.effectiveAt());
                var receipt=devices.previewAssignment(command);
                if(receipt==null || !Objects.equals(receipt.deviceId(),b.deviceId()) || !Objects.equals(receipt.projectId(),project))fail("OWNER_RECEIPT_INVALID");
                chosen=command;checked=receipt;
            }
            commands.add(chosen);items.add(new Item(sn,b.deviceId(),project,b.shipmentId(),b.assignmentVersion(),checked.action()));
        }
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(tenant,actor,task.getId(),task.getVersion(),sourceRun.getId(),scope.runId(),q.effectiveAt(),items,selected)));
        if(apply) {
            if(q.expectedDigest()==null || !q.expectedDigest().equals(digest))fail("PREVIEW_CHANGED");
            var applied=new ArrayList<Item>();
            for(int i=0;i<commands.size();i++) {
                var receipt=devices.applyAssignment(commands.get(i));var old=items.get(i);
                if(receipt==null || !Objects.equals(receipt.deviceId(),old.deviceId()) || !Objects.equals(receipt.projectId(),old.projectId()))fail("OWNER_RECEIPT_INVALID");
                applied.add(new Item(old.deviceSn(),old.deviceId(),old.projectId(),old.shipmentId(),receipt.assignmentVersion(),receipt.action()));
            }
            return new Result(digest,sourceRun.getId(),List.copyOf(applied));
        }
        return new Result(digest,sourceRun.getId(),List.copyOf(items));
    }
    private record History(String sourceKey,Long installationId,Long deviceId,Long projectId,String sourceProjectKey,String contractNo,String formalFieldsJson) {}
    private static String text(Object value){return value==null?null:value.toString();}
    private static void fail(String code){throw new IllegalArgumentException("P30_"+code);}
}
