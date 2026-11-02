package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.*;
import static cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryMigrationService.date;

/** 初次历史迁入：同一来源项目只执行一次，换快照不重复建立成员或接管后续维护。 */
@Service @RequiredArgsConstructor
public class ProjectMemberHistoryImportService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectMemberHistoryMigrationService qualification;
    private final ProjectMasterMapper projects;
    private final ProjectMemberHistoryMigrationMapper members;
    private final SystemUserMigrationApi identities;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;

    public record Request(@NotNull @Valid ProjectLegacyEntityMigrationService.Request source,
                          @NotNull @PositiveOrZero Long expectedProjectVersion,
                          @NotBlank @Pattern(regexp="[0-9a-f]{64}") String existingMembersChecksum,
                          @Size(max=45) String primaryServiceManagerCode, @Size(max=45) String primaryProjectManagerCode,
                          @NotNull @Size(max=200) Set<@Positive Long> closeCurrentMemberIds) { }
    public record Preview(Long projectId, Long projectVersion, String existingMembersChecksum,
                          List<ProjectMemberAssignmentDO> existingMembers,
                          ProjectMemberHistoryMigrationService.Report qualification,
                          List<SystemUserMigrationApi.MemberIdentity> memberIdentities) { }
    public record Target(String sourceKey, ProjectMemberAssignmentDO member) { }
    public record Report(Long sourceId, Long snapshotId, ProjectMasterDO projectAfter,
                         List<Target> targets, List<ProjectMemberAssignmentDO> closedIntervals,
                         LocalDateTime executedAt) { }
    public record Result(String decision, Report report) { }

    @Transactional(rollbackFor=Exception.class)
    public Preview preview(Long projectId, ProjectLegacyEntityMigrationService.Request request, ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        var project=projects.selectByIdForUpdate(projectId);
        requireProject(project,actor);
        var rows=members.selectProjectForUpdate(new ProjectMemberHistoryMigrationMapper.ProjectQuery(actor.tenantId(),projectId));
        var checked=qualification.preview(projectId,request,actor);
        var names=new LinkedHashSet<String>();var rawRows=new ArrayList<Map<String,String>>();
        for(var row:loaded.domains().get("members")) {
            var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});rawRows.add(raw);
            for(String field:List.of("memberCode","createBy","updateBy"))if(raw.get(field)!=null && !raw.get(field).isBlank())names.add(raw.get(field));
        }
        var accounts=accountIdentities(actor.tenantId(),names);
        rawRows.forEach(raw->{actorCode(raw.get("createBy"),accounts);actorCode(raw.get("updateBy"),accounts);});
        for(var candidate:checked.candidates()) {
            var account=accounts.get(candidate.memberCode());
            if(!Objects.equals(candidate.userId(),account==null?null:account.id()))fail("ACCOUNT_CHANGED");
        }
        return new Preview(projectId,project.getVersion(),fingerprint(rows),List.copyOf(rows),checked,
                accounts.values().stream().sorted(Comparator.comparing(SystemUserMigrationApi.MemberIdentity::username)).toList());
    }

    @Transactional(rollbackFor=Exception.class)
    public Result migrate(Long projectId, Request request, ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);
        var project=projects.selectByIdForUpdate(projectId);requireProject(project,actor);
        var qualified=qualification.preview(projectId,request.source(),actor); // 每次重放也核验原15字段及不可变行校验值。
        String key="DPPMS-MEMBER-INITIAL:"+projectId+":"+request.source().sourceId();
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request.source(),request.expectedProjectVersion(),
                request.existingMembersChecksum(),Arrays.asList(request.primaryServiceManagerCode(),request.primaryProjectManagerCode()),
                new TreeSet<>(request.closeCurrentMemberIds()))));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:MEMBER_INITIAL",0L,key),digest,Report.class,()->{
            var current=members.selectProjectForUpdate(new ProjectMemberHistoryMigrationMapper.ProjectQuery(actor.tenantId(),projectId));
            if(!Objects.equals(project.getVersion(),request.expectedProjectVersion()) || !fingerprint(current).equals(request.existingMembersChecksum()))
                fail("TARGET_CHANGED_SINCE_PREVIEW");
            var byKey=new HashMap<String,ProjectMemberHistoryMigrationService.Candidate>();
            qualified.candidates().forEach(row->{if(byKey.put(row.sourceKey(),row)!=null)fail("SOURCE_DUPLICATE");});
            var selected=loaded.domains().get("members").stream().filter(row->byKey.get(row.getSourceKey()).role()!=null).toList();
            if(selected.isEmpty())fail("NO_ACCOUNT_MEMBER_SUBJECT");
            var names=new LinkedHashSet<String>();
            var originals=new LinkedHashMap<String,Map<String,String>>();
            for(var row:selected) {
                var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
                if(!"pm_project_member".equals(row.getSourceTable()) || !Objects.equals(row.getTenantId(),actor.tenantId())
                        || !Objects.equals(row.getSnapshotId(),loaded.snapshot().getId()))fail("SOURCE_IDENTITY_INVALID");
                originals.put(row.getSourceKey(),raw);
                for(String field:List.of("memberCode","createBy","updateBy")) if(raw.get(field)!=null && !raw.get(field).isBlank())names.add(raw.get(field));
            }
            var accounts=accountIdentities(actor.tenantId(),names);
            var now=LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS); // 既有成员表DATETIME按秒保存。
            var closures=approvedClosures(current,request,now);
            var fresh=new ArrayList<Target>();
            for(var entry:originals.entrySet()) {
                var candidate=byKey.get(entry.getKey());var raw=entry.getValue();
                if(candidate.qualified() && !Set.of("OWNER_QUALIFIED","CURRENT_ROLE_CONFLICT").contains(candidate.result()))
                    fail("QUALIFIED_SOURCE_INVALID");
                var row=new ProjectMemberAssignmentDO();row.setId(IdWorker.getId());row.setTenantId(actor.tenantId());row.setDeleted(false);
                row.setProjectId(projectId);row.setUserId(candidate.userId());row.setMemberCode(raw.get("memberCode"));
                var account=accounts.get(raw.get("memberCode"));
                if(!Objects.equals(account==null?null:account.id(),candidate.userId()))fail("ACCOUNT_CHANGED");
                row.setEmployeeNo(account==null?null:account.employeeNo());row.setMemberName(raw.get("memberName"));
                row.setPhoneNumber(raw.get("phoneNum"));row.setEmail(raw.get("email"));row.setRegistrationSource(raw.get("fromFlag"));
                row.setMemberRole(candidate.role());row.setEffectiveFrom(date(raw.get("effectiveFrom")));row.setEffectiveTo(date(raw.get("effectiveTo")));
                row.setCreator(actorCode(raw.get("createBy"),accounts));row.setUpdater(actorCode(raw.get("updateBy"),accounts));
                row.setCreateTime(date(raw.get("createTime")));row.setUpdateTime(date(raw.get("updateTime"))==null?row.getCreateTime():date(raw.get("updateTime")));
                row.setAuthorizationConfirmed(candidate.qualified());row.setStatus("ACTIVE");row.setVersion(0);
                row.setCompanyId(project.getCompanyId());row.setCompanyCode(project.getCompanyCode());row.setCompanyName(project.getCompanyName());
                if(candidate.qualified()) row.setAssignmentType(isPrimary(candidate,request)?"PRIMARY":"COLLABORATOR");
                fresh.add(new Target(entry.getKey(),row));
            }
            var next=new ArrayList<ProjectMemberAssignmentDO>();
            for(var row:current) if(!request.closeCurrentMemberIds().contains(row.getId()))next.add(row);
            next.addAll(fresh.stream().map(Target::member).toList());
            validateIntervals(next);
            var manager=primary(next,PROJECT_MANAGER,request.primaryProjectManagerCode(),now);
            var serviceManager=primary(next,SERVICE_MANAGER,request.primaryServiceManagerCode(),now);
            var expectedMembers=new TreeMap<Long,ProjectMemberAssignmentDO>();
            current.forEach(row->expectedMembers.put(row.getId(),BeanUtil.copyProperties(row,ProjectMemberAssignmentDO.class)));
            fresh.forEach(target->expectedMembers.put(target.member().getId(),target.member()));
            for(var old:closures) {
                var expected=expectedMembers.get(old.getId());expected.setEffectiveTo(now);expected.setVersion(old.getVersion()+1);
                expected.setEndReason("按已裁决的DPPMS主责承接，保留管理员历史");expected.setUpdater(actor.actorId().toString());expected.setUpdateTime(now);
                if(members.closeApprovedInterval(new ProjectMemberHistoryMigrationMapper.CloseInterval(actor.tenantId(),projectId,old.getId(),
                        old.getVersion(),now,actor.actorId().toString(),"按已裁决的DPPMS主责承接，保留管理员历史"))!=1)fail("INTERVAL_CLOSE_CONFLICT");
            }
            Map<String,NativeEntityMigrationRow> outcomes=new LinkedHashMap<>();
            for(var target:fresh) {
                if(members.insertHistorical(new ProjectMemberHistoryMigrationMapper.HistoricalInsert(target.member()))!=1)fail("INSERT_CONFLICT");
                outcomes.put("members:"+target.sourceKey(),NativeEntityMigrationRow.mapped("members",target.sourceKey(),
                        new ExternalTargetMapping("PROJ","ProjectMemberAssignment","proj_project_member_assignment",target.member().getId(),"MEMBER_HISTORY",0)));
            }
            String assignment=manager!=null && serviceManager!=null?"ASSIGNED":"UNASSIGNED";
            var expectedProject=BeanUtil.copyProperties(project,ProjectMasterDO.class);
            expectedProject.setManagerId(manager==null?null:manager.getUserId());expectedProject.setManagerName(manager==null?null:manager.getMemberName());
            expectedProject.setManagerEmployeeNo(manager==null?null:manager.getEmployeeNo());expectedProject.setAssignmentStatus(assignment);
            expectedProject.setVersion(project.getVersion()+1);
            if(members.updateApprovedPrimary(new ProjectMemberHistoryMigrationMapper.PrimaryUpdate(actor.tenantId(),projectId,
                    project.getVersion(),project.getLifecycleStatus(),manager==null?null:manager.getUserId(),manager==null?null:manager.getMemberName(),
                    manager==null?null:manager.getEmployeeNo(),assignment))!=1)fail("PROJECT_VERSION_CONFLICT");
            var projectAfter=projects.selectById(projectId);
            if(!Objects.equals(expectedProject,projectAfter))fail("PROJECT_FIELDS_DIFFER");
            evidence.reconcile(loaded.source(),loaded.snapshot(),"members",selected,outcomes,"MEMBER_HISTORY_V1");
            var after=members.selectProjectForUpdate(new ProjectMemberHistoryMigrationMapper.ProjectQuery(actor.tenantId(),projectId));
            var byId=new HashMap<Long,ProjectMemberAssignmentDO>();after.forEach(row->byId.put(row.getId(),row));
            if(!expectedMembers.equals(byId))fail("EXISTING_MEMBER_FIELDS_DIFFER");
            for(var target:fresh) if(!Objects.equals(target.member(),byId.get(target.member().getId())))fail("INSERTED_FIELDS_DIFFER");
            return new Report(request.source().sourceId(),request.source().snapshotId(),projectAfter,List.copyOf(fresh),
                    closures.stream().map(row->byId.get(row.getId())).toList(),now);
        },report->new SuccessFacts("PROJ_DPPMS_MEMBER_INITIAL","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)fail(execution.decision().name());
        if(execution.decision()==Decision.REPLAY_COMPLETED) {
            var report=execution.response();
            if(!sameProject(project,report.projectAfter()))fail("REPLAY_PROJECT_CHANGED");
            var nowMembers=members.selectProjectForUpdate(new ProjectMemberHistoryMigrationMapper.ProjectQuery(actor.tenantId(),projectId));
            var byId=new HashMap<Long,ProjectMemberAssignmentDO>();nowMembers.forEach(row->byId.put(row.getId(),row));
            for(var target:report.targets()) if(!Objects.equals(target.member(),byId.get(target.member().getId())))fail("REPLAY_MEMBER_CHANGED");
            for(var closed:report.closedIntervals()) if(!Objects.equals(closed,byId.get(closed.getId())))fail("REPLAY_INTERVAL_CHANGED");
            var keys=report.targets().stream().map(Target::sourceKey).collect(java.util.stream.Collectors.toSet());
            var statuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),
                    loaded.domains().get("members").stream().filter(row->keys.contains(row.getSourceKey())).toList());
            for(var target:report.targets()) {
                var status=statuses.get(target.sourceKey());
                var expected=new ExternalTargetMapping("PROJ","ProjectMemberAssignment","proj_project_member_assignment",target.member().getId(),"MEMBER_HISTORY",0);
                if(status==null || !"MAPPED".equals(status.classification()) || !status.targets().equals(List.of(expected)))fail("REPLAY_BINDING_CHANGED");
            }
        }
        return new Result(execution.decision().name(),execution.response());
    }
    private Map<String,SystemUserMigrationApi.MemberIdentity> accountIdentities(Long tenant,Set<String> names) {
        var result=new HashMap<String,SystemUserMigrationApi.MemberIdentity>();var list=new ArrayList<>(names);
        for(int from=0;from<list.size();from+=200)for(var person:identities.getHistoricalMemberIdentities(tenant,Set.copyOf(list.subList(from,Math.min(from+200,list.size())))))
            if(!names.contains(person.username()) || result.putIfAbsent(person.username(),person)!=null)fail("ACCOUNT_AMBIGUOUS");
        return result;
    }
    private static String actorCode(String code,Map<String,SystemUserMigrationApi.MemberIdentity> accounts) {
        if(code==null || code.isEmpty())return code;
        var account=accounts.get(code);if(account==null)fail("AUDIT_ACCOUNT_PENDING");return account.id().toString();
    }
    private static boolean isPrimary(ProjectMemberHistoryMigrationService.Candidate candidate,Request request) {
        return SERVICE_MANAGER.equals(candidate.role())?Objects.equals(candidate.memberCode(),request.primaryServiceManagerCode())
                :PROJECT_MANAGER.equals(candidate.role()) && Objects.equals(candidate.memberCode(),request.primaryProjectManagerCode());
    }
    private static List<ProjectMemberAssignmentDO> approvedClosures(List<ProjectMemberAssignmentDO> current,Request request,LocalDateTime now) {
        var result=new ArrayList<ProjectMemberAssignmentDO>();
        for(Long id:request.closeCurrentMemberIds()) {
            var row=current.stream().filter(r->id.equals(r.getId())).findFirst().orElseThrow(()->new IllegalArgumentException("DPPMS_MEMBER_CLOSURE_NOT_FOUND"));
            if(!current(row,now) || !Objects.equals(row.getUserId(),1L) || !Set.of(SERVICE_MANAGER,PROJECT_MANAGER).contains(normalize(row.getMemberRole()))
                    || !"PRIMARY".equals(row.getAssignmentType()))fail("CLOSURE_OUTSIDE_APPROVED_ADMIN_INTERVAL");
            if(SERVICE_MANAGER.equals(normalize(row.getMemberRole()))?request.primaryServiceManagerCode()==null:request.primaryProjectManagerCode()==null)
                fail("CLOSURE_REPLACEMENT_REQUIRED");
            result.add(row);
        }
        return result;
    }
    static void validateIntervals(List<ProjectMemberAssignmentDO> rows) {
        for(var row:rows) if(row.getEffectiveFrom()!=null && row.getEffectiveTo()!=null && row.getEffectiveTo().isBefore(row.getEffectiveFrom()))
            fail("SOURCE_INTERVAL_INVALID");
        for(int i=0;i<rows.size();i++)for(int j=i+1;j<rows.size();j++) {
            var a=rows.get(i);var b=rows.get(j);
            if(a.getUserId()!=null && Objects.equals(a.getUserId(),b.getUserId()) && normalize(a.getMemberRole()).equals(normalize(b.getMemberRole()))
                    && a.getEffectiveFrom()!=null && b.getEffectiveFrom()!=null
                    && (a.getEffectiveTo()==null || b.getEffectiveFrom().isBefore(a.getEffectiveTo()))
                    && (b.getEffectiveTo()==null || a.getEffectiveFrom().isBefore(b.getEffectiveTo())))fail("SOURCE_INTERVAL_OVERLAP");
        }
    }
    private static ProjectMemberAssignmentDO primary(List<ProjectMemberAssignmentDO> rows,String role,String code,LocalDateTime now) {
        var active=rows.stream().filter(row->current(row,now) && role.equals(normalize(row.getMemberRole()))).toList();
        var primary=active.stream().filter(row->"PRIMARY".equals(row.getAssignmentType()) || row.getAssignmentType()==null).toList();
        if(active.isEmpty()) {if(code!=null)fail("PRIMARY_NOT_QUALIFIED");return null;}
        if(primary.size()!=1 || code==null || !code.equals(primary.getFirst().getMemberCode()))fail("PRIMARY_DECISION_REQUIRED");
        return primary.getFirst();
    }
    private static boolean current(ProjectMemberAssignmentDO row,LocalDateTime now) {
        return Boolean.TRUE.equals(row.getAuthorizationConfirmed()) && "ACTIVE".equals(row.getStatus())
                && (row.getEffectiveFrom()==null || !row.getEffectiveFrom().isAfter(now)) && (row.getEffectiveTo()==null || row.getEffectiveTo().isAfter(now));
    }
    static String fingerprint(List<ProjectMemberAssignmentDO> rows) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(rows.stream().sorted(Comparator.comparing(ProjectMemberAssignmentDO::getId)).toList()));
    }
    private static boolean sameProject(ProjectMasterDO current,ProjectMasterDO applied) {
        if(current==null || applied==null)return current==applied;
        // MySQL JSON规范化DECIMAL尾零；仍逐字段比较，金额/权重的数值变化不可被忽略。
        var left=BeanUtil.copyProperties(current,ProjectMasterDO.class);var right=BeanUtil.copyProperties(applied,ProjectMasterDO.class);
        left.setProgress(current.getProgress()==null?null:current.getProgress().stripTrailingZeros());
        right.setProgress(applied.getProgress()==null?null:applied.getProgress().stripTrailingZeros());
        left.setAggregationWeight(current.getAggregationWeight()==null?null:current.getAggregationWeight().stripTrailingZeros());
        right.setAggregationWeight(applied.getAggregationWeight()==null?null:applied.getAggregationWeight().stripTrailingZeros());
        return Objects.equals(left,right);
    }
    private static void requireProject(ProjectMasterDO project,ProjectAccessActor actor) {
        if(project==null || !Objects.equals(project.getTenantId(),actor.tenantId()) || !Set.of("ACTIVE","NORMAL_CLOSED","NO_TRACKING_CLOSED","EXCEPTION_CLOSED").contains(project.getLifecycleStatus()))fail("PROJECT_INACCESSIBLE");
    }
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_MEMBER_"+reason);}
}
