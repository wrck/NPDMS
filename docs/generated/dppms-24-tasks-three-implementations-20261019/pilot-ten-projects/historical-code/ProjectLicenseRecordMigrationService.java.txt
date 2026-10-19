package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.ProjectSourcesQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

@Service @RequiredArgsConstructor
public class ProjectLicenseRecordMigrationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final DppmsLicenseRecordApi owner;
    private final DppmsCommerceEntityResolveApi commerce;
    private final PlatformCommandExecutionApi commands;
    private final ProjectManualCreationService access;
    private final ProjectLegacyDetailMapper sourceMapper;
    private static final Set<String> FIELDS=Set.of("id","sn","specModel","contract","contractType","item","status");
    public record Request(@NotNull @Valid ProjectLegacyEntityMigrationService.Request source,
                          @NotNull @Size(max=200) List<Envelope> envelopes) {}
    public record Report(Long sourceId,Long snapshotId,int count,List<Target> targets,List<Preview> associations) {}
    public record Result(String decision,Report report) {}

    @Transactional(rollbackFor=Exception.class)
    public List<Preview> preview(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);
        return owner.preview(command(projectId,request,loaded,actor));
    }
    @Transactional(rollbackFor=Exception.class)
    public Result migrate(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);
        var command=command(projectId,request,loaded,actor);
        var before=owner.preview(command);
        // A selected source partition can be carried only once; a later snapshot conflicts.
        var selectedKeys=command.licenses().stream().map(License::sourceKey).toList();
        String key="DPPMS-LICENSE-PARTITION:"+DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(request.source().sourceId(),selectedKeys)));
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request.source(),command.licenses().stream()
            .map(row->Arrays.asList(row.sourceKey(),row.deviceSn(),row.productModel(),row.contractNo(),row.contractType(),
                row.productCode(),row.licenseStatus(),row.envelope().codeMac())).toList())));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:LICENSE_INITIAL",0L,key),digest,Report.class,()->{
            var targets=owner.apply(command);
            if(targets.size()!=selectedKeys.size() || !selectedKeys.equals(targets.stream().map(Target::sourceKey).toList()))
                fail("OWNER_COVERAGE_INVALID");
            Map<String,NativeEntityMigrationRow> outcomes=new HashMap<>();
            for(var target:targets) outcomes.put("licenses:"+target.sourceKey(),NativeEntityMigrationRow.mapped("licenses",target.sourceKey(),
                new ExternalTargetMapping("AST","DeviceLicenseRecord","ast_device_license_record",target.id(),"LICENSE_HISTORY",0)));
            var selected=loaded.domains().get("licenses").stream().filter(row->selectedKeys.contains(row.getSourceKey())).toList();
            // Partitions within one snapshot append distinct batches while preserving the source snapshot.
            evidence.reconcile(loaded.source(),loaded.snapshot(),"licenses",selected,outcomes,"LICENSE_RECORD_V1");
            return new Report(request.source().sourceId(),request.source().snapshotId(),targets.size(),targets,before);
        },report->new SuccessFacts("PROJ_DPPMS_LICENSE_INITIAL","Project",projectId.toString(),key,
            JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"sourceId",report.sourceId(),"snapshotId",report.snapshotId(),"count",report.count())),null,null));
        if(execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)fail(execution.decision().name());
        if(execution.decision()==Decision.REPLAY_COMPLETED) {
            var current=owner.preview(command);
            if(current.stream().anyMatch(row->!"UNCHANGED".equals(row.action()))
                || !current.stream().map(row->new Target(row.sourceKey(),row.targetId())).toList().equals(execution.response().targets()))
                fail("REPLAY_TARGET_CHANGED");
        }
        return new Result(execution.decision().name(),execution.response());
    }
    Command command(Long projectId,Request request,Loaded loaded,ProjectAccessActor actor) {
        if(request==null || request.envelopes()==null || request.envelopes().size()>200)fail("REQUEST_INVALID");
        var byKey=new TreeMap<String,Envelope>();
        for(var envelope:request.envelopes())
            if(envelope==null || envelope.sourceKey()==null || byKey.putIfAbsent(envelope.sourceKey(),envelope)!=null)fail("ENVELOPE_INVALID");
        var raw=new HashMap<String,Map<String,String>>();
        for(var record:loaded.domains().get("licenses")) {
            var fields=JsonUtils.parseObject(record.getPayloadJson(),new TypeReference<Map<String,String>>(){});
            if(fields==null || !FIELDS.equals(fields.keySet()) || !"pm_project_license_info_from_license".equals(record.getSourceTable())
                || !Objects.equals(fields.get("id"),record.getSourceKey()) || raw.putIfAbsent(record.getSourceKey(),fields)!=null)fail("SOURCE_INVALID");
        }
        if(!raw.keySet().containsAll(byKey.keySet()) || byKey.isEmpty() && !raw.isEmpty())fail("ENVELOPE_COVERAGE_INVALID");
        var contracts=byKey.keySet().stream().map(key->raw.get(key).get("contract")).filter(no->no!=null && !no.isBlank()).distinct().toList();
        Map<String,Long> ids=new HashMap<>();
        commerce.resolveLicenseContracts(new DppmsCommerceEntityResolveApi.LicenseContracts(actor.tenantId(),actor.actorId(),projectId,contracts))
            .forEach(row->ids.put(row.contractNo(),row.contractId()));
        var licenses=byKey.entrySet().stream().map(e->{
            var fields=raw.get(e.getKey());
            return new License(e.getKey(),fields.get("sn"),fields.get("specModel"),fields.get("contract"),fields.get("contractType"),
                fields.get("item"),fields.get("status"),ids.get(fields.get("contract")),e.getValue());
        }).toList();
        return new Command(actor.tenantId(),actor.actorId(),projectId,loaded.snapshot().getId(),loaded.snapshot().getSourceReadAt(),licenses);
    }
    public PageResult<ProjectAssociatedDataService.Row> page(Long projectId,int pageNo,int pageSize,ProjectAccessActor actor) {
        access.getProject(projectId,actor);
        var ids=new LinkedHashSet<Long>();
        for(var source:sourceMapper.selectProjectSources(new ProjectSourcesQuery(actor.tenantId(),projectId))) {
            Long after=null;
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO> part;
            do {
                part=sourceMapper.selectSourceLicenseRecords(new ProjectLegacyDetailMapper.SourceLicenseRecordsQuery(actor.tenantId(),source.getId(),after,500));
                var snapshots=part.stream().collect(java.util.stream.Collectors.groupingBy(
                    cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO::getSnapshotId,LinkedHashMap::new,java.util.stream.Collectors.toList()));
                for(var snapshot:snapshots.entrySet()) {
                    var statuses=evidence.statuses(actor.tenantId(),source.getSourceProjectKey(),snapshot.getKey(),snapshot.getValue());
                    for(var status:statuses.values()) if("MAPPED".equals(status.classification())) {
                        if(status.targets().size()!=1)fail("BINDING_INVALID");
                        var target=status.targets().getFirst();
                        if(!"AST".equals(target.targetContext()) || !"DeviceLicenseRecord".equals(target.targetObjectType())
                            || !"ast_device_license_record".equals(target.targetTable()) || !"LICENSE_HISTORY".equals(target.targetRole())
                            || !Objects.equals(target.targetSequence(),0))fail("BINDING_INVALID");
                        ids.add(target.targetId());
                    }
                }
                if(!part.isEmpty())after=part.getLast().getId();
            } while(part.size()==500);
        }
        var page=owner.page(new Query(actor.tenantId(),actor.actorId(),projectId,List.copyOf(ids),pageNo,pageSize));
        return new PageResult<>(page.getList().stream().map(row->new ProjectAssociatedDataService.Row(row.id(),String.valueOf(row.id()),row.values())).toList(),page.getTotal());
    }
    private static void fail(String code) {throw new IllegalArgumentException("DPPMS_LICENSE_"+code);}
}
