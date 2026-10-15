package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class ServiceIssueService {
    private final InspectionAccessService access;
    private final InspectionTaskMapper taskMapper;
    private final InspectionTaskService tasks;
    private final InspectionClosureFacts closure;
    private final ServiceIssueMapper issues;
    private final ServiceIssueRemediationMapper remediations;
    private final InspectionEvidenceMapper evidence;
    private final InspectionFiles files;
    private final FileEvidenceApi fileEvidence;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    public record Query(InspectionRequestContext context, Long issueId) { }
    @Builder public record Process(InspectionRequestContext context, Long issueId, String content, List<InspectionFiles.Reference> evidence) { }
    public record Closure(InspectionRequestContext context, Long issueId, String description) { }
    private record Access(InspectionRequestContext context, Long issueId, String permission, boolean write, boolean confirm) { }
    private record Scope(InspectionTaskDO task, ServiceIssueDO issue, boolean manager) { }
    private record Record(InspectionRequestContext context, Scope scope, String action, String content, List<InspectionFiles.Reference> files) { }

    public InspectionViews.IssueDetail detail(Query query) {
        var scope = scope(new Access(query.context(), query.issueId(), "pms:service-issue:query", false, false));
        var task = scope.task(); var issue = scope.issue(); var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        var view = BeanUtils.toBean(issue, InspectionViews.IssueDetail.class);
        view.setTaskCode(task.getTaskCode()); view.setTaskName(task.getTaskName()); view.setTaskStatus(task.getStatusCode());
        view.setResponsible(Objects.equals(issue.getResponsibleUserId(), query.context().actorId()));
        view.setCanConfirm((Objects.equals(task.getOwnerUserId(), query.context().actorId()) || scope.manager())
                && permissions.hasAnyPermissions(query.context().actorId(), "pms:service-issue:confirm"));
        var notes = remediations.selectForTask(rows).stream().filter(n -> Objects.equals(n.getIssueId(), issue.getId())).toList();
        var noteIds = notes.stream().map(ServiceIssueRemediationDO::getId).collect(Collectors.toSet());
        Set<Long> userIds = notes.stream().map(ServiceIssueRemediationDO::getActorId).collect(Collectors.toSet());
        userIds.add(issue.getResponsibleUserId()); userIds.remove(null);
        var people = users.getUserMap(userIds);
        var responsible = people.get(issue.getResponsibleUserId());
        if (responsible != null) view.setResponsibleName(responsible.getNickname());
        view.setRemediations(notes.stream().map(n -> {
            var note = BeanUtils.toBean(n, InspectionViews.RemediationView.class);
            var person = people.get(n.getActorId());
            if (person != null) note.setActorName(person.getNickname());
            return note;
        }).toList());
        view.setEvidence(evidence.selectForTask(rows).stream().filter(e -> noteIds.contains(e.getRemediationId()))
                .map(e -> BeanUtils.toBean(e, InspectionViews.EvidenceView.class)).toList()); return view;
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.IssueView process(Process command) {
        var scope = scope(new Access(command.context(), command.issueId(), "pms:service-issue:remediate", true, false));
        require(Set.of("PENDING", "PROCESSING").contains(scope.issue().getStatusCode()), "当前状态不能追加处理记录");
        record(new Record(command.context(), scope, "PROCESS", command.content(), command.evidence()));
        scope.issue().setStatusCode("PROCESSING"); save(new Save(command.context(), scope, "ISSUE_PROCESS", "追加问题处理记录"));
        return BeanUtils.toBean(scope.issue(), InspectionViews.IssueView.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.IssueView requestClosure(Closure command) {
        var scope = scope(new Access(command.context(), command.issueId(), "pms:service-issue:remediate", true, false));
        require(Set.of("PENDING", "PROCESSING").contains(scope.issue().getStatusCode()), "当前状态不能申请闭环");
        proof(scope);
        record(new Record(command.context(), scope, "REQUEST_CLOSURE", command.description(), List.of()));
        scope.issue().setClosureDescription(command.description()); scope.issue().setClosureRequestedBy(command.context().actorId());
        scope.issue().setClosureRequestedAt(LocalDateTime.now()); scope.issue().setStatusCode("CLOSURE_PENDING");
        save(new Save(command.context(), scope, "ISSUE_REQUEST_CLOSURE", "责任人申请问题闭环"));
        return BeanUtils.toBean(scope.issue(), InspectionViews.IssueView.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.IssueView confirmClosure(Closure command) {
        var scope = scope(new Access(command.context(), command.issueId(), "pms:service-issue:confirm", true, true));
        require("CLOSURE_PENDING".equals(scope.issue().getStatusCode()), "责任人尚未提交闭环申请"); proof(scope);
        record(new Record(command.context(), scope, "CONFIRM_CLOSURE", command.description(), List.of()));
        scope.issue().setStatusCode("CLOSED"); scope.issue().setClosedAt(LocalDateTime.now()); scope.issue().setClosedBy(command.context().actorId());
        require(issues.updateById(scope.issue()) == 1, "待办已更新，请刷新");
        var facts = closure.current(new InspectionClosureFacts.Query(command.context(), scope.task()));
        if ("TRACKING".equals(scope.task().getStatusCode()) && facts.openIssues() == 0) {
            tasks.transition(new InspectionTaskService.Change(command.context(), scope.task(),
                    facts.transition(scope.task(), InspectionTaskStateMachine.Action.ISSUES_CLOSED), "全部跟踪项已确认闭环"));
        } else tasks.save(new InspectionTaskService.Save(command.context(), scope.task(), "ISSUE_CONFIRM_CLOSURE",
                scope.task().getStatusCode(), "确认问题" + scope.issue().getId() + "闭环"));
        return BeanUtils.toBean(scope.issue(), InspectionViews.IssueView.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.IssueView returnClosure(Closure command) {
        var scope = scope(new Access(command.context(), command.issueId(), "pms:service-issue:confirm", true, true));
        require("CLOSURE_PENDING".equals(scope.issue().getStatusCode()), "待办不在待闭环状态");
        record(new Record(command.context(), scope, "RETURN_CLOSURE", command.description(), List.of()));
        scope.issue().setStatusCode("PROCESSING"); save(new Save(command.context(), scope, "ISSUE_RETURN_CLOSURE", "退回问题闭环申请"));
        return BeanUtils.toBean(scope.issue(), InspectionViews.IssueView.class);
    }

    private Scope scope(Access request) {
        access.permission(request.context(), request.permission());
        var issue = issues.selectById(request.issueId());
        if (issue == null || !Objects.equals(issue.getTenantId(), request.context().tenantId()))
            throw new InspectionException(InspectionException.Reason.NOT_FOUND, "待办不存在或不可见");
        var task = taskMapper.selectById(issue.getTaskId());
        require(task != null && Objects.equals(task.getTenantId(), request.context().tenantId()), "待办所属任务不可用");
        access.project(new InspectionAccessService.ProjectAccess(request.context(), task.getProjectId(), request.write()));
        boolean manager = access.manager(new InspectionAccessService.ProjectAccess(request.context(), task.getProjectId(), request.write()));
        if (request.write()) {
            task = taskMapper.selectForUpdate(new InspectionTaskRows(request.context().tenantId(), task.getId()));
            require(task != null, "待办所属任务不可用");
            // 先持有任务锁，再读取最新待办，避免RR快照沿用等待锁之前的责任人或版本。
            issue = issues.selectForTaskForUpdate(new InspectionTaskRows(request.context().tenantId(), task.getId()))
                    .stream().filter(row -> Objects.equals(row.getId(), request.issueId())).findFirst().orElse(null);
            require(issue != null, "待办不存在或不可见");
            require(!"ARCHIVED".equals(task.getStatusCode()), "已归档任务只读");
            if (!Objects.equals(issue.getVersion(), request.context().requireVersion()))
                throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT, "待办已更新，请刷新后重试", issue.getVersion());
        }
        boolean owner = Objects.equals(task.getOwnerUserId(), request.context().actorId());
        boolean responsible = Objects.equals(issue.getResponsibleUserId(), request.context().actorId());
        boolean allowed = request.confirm() ? owner || manager : request.write() ? responsible : responsible || owner || manager;
        if (!allowed) throw new InspectionException(InspectionException.Reason.FORBIDDEN, "没有该问题的处理或确认权限");
        return new Scope(task, issue, manager);
    }
    private void record(Record command) {
        InspectionTaskService.validateText(command.content(), 8000, "处理记录或闭环说明");
        var references = command.files() == null ? List.<InspectionFiles.Reference>of() : command.files();
        require(references.size() <= 20 && new HashSet<>(references).size() == references.size(), "证据数量超过限制或存在重复");
        var note = new ServiceIssueRemediationDO(); note.setTenantId(command.scope().task().getTenantId()); note.setVersion(1L);
        note.setTaskId(command.scope().task().getId()); note.setIssueId(command.scope().issue().getId()); note.setActionCode(command.action());
        note.setContent(command.content().trim()); note.setActorId(command.context().actorId()); note.setActionAt(LocalDateTime.now()); remediations.insert(note);
        for (var ref : references) {
            require(ref != null && ref.referenceKey() != null && ref.referenceKey().startsWith("issue:" + note.getIssueId() + ":"),
                    "证据引用不属于当前问题");
            var fact = files.lock(new InspectionFiles.Query(note.getTaskId(), "EVIDENCE", ref));
            var row = new InspectionEvidenceDO(); row.setTenantId(note.getTenantId()); row.setVersion(1L); row.setTaskId(note.getTaskId());
            row.setRemediationId(note.getId()); row.setArtifactId(fact.artifactId()); row.setFileVersionNo(fact.versionNo());
            row.setReferenceKey(fact.referenceKey()); row.setFileName(fact.name()); row.setSha256(fact.sha256()); evidence.insert(row);
        }
    }
    private void proof(Scope scope) {
        var rows = new InspectionTaskRows(scope.task().getTenantId(), scope.task().getId());
        var noteIds = remediations.selectForTask(rows).stream().filter(n -> Objects.equals(n.getIssueId(), scope.issue().getId())
                && "PROCESS".equals(n.getActionCode())).map(ServiceIssueRemediationDO::getId).collect(Collectors.toSet());
        var proof = evidence.selectForTask(rows).stream().filter(e -> noteIds.contains(e.getRemediationId())).toList();
        require(!noteIds.isEmpty() && !proof.isEmpty(), "闭环前须提交处理记录和有效处理证据");
        for (var row : proof) {
            var fact = fileEvidence.lockAndRevalidate(new FileEvidenceApi.Query(row.getTenantId(), row.getArtifactId(), row.getFileVersionNo(),
                    "SRV", "INSPECTION_TASK", row.getTaskId().toString(), "EVIDENCE", row.getReferenceKey(), row.getSha256()));
            require(fact != null && fact.valid(), "处理证据不可用，不能确认闭环");
        }
    }
    private record Save(InspectionRequestContext context, Scope scope, String action, String description) { }
    private void save(Save change) {
        require(issues.updateById(change.scope().issue()) == 1, "待办已更新，请刷新");
        tasks.save(new InspectionTaskService.Save(change.context(), change.scope().task(), change.action(),
                change.scope().task().getStatusCode(), change.description()));
    }
}
