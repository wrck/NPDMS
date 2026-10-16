package cn.iocoder.yudao.module.pms.customer.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class CustomerInspectionHistoryService implements CustomerInspectionHistoryApi {
    private final CustomerInspectionHistoryMapper histories;
    private final CustomerInspectionReferenceMapper references;
    private final CustomerQueryApi customers;
    private final CustomerQueryService customerQueries;
    private final CustomerScopeContextService customerScopes;
    private final ProjectBusinessContextApi projects;
    private final ProjectScopeApi projectScopes;
    private final PermissionApi permissions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ArchiveReference register(ArchiveCommand command) {
        validate(command);
        var scope = projectScopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(command.tenantId())
                .subjectUserId(command.actorId()).anchorProjectId(command.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(command.projectId())) throw exception(CUSTOMER_SCOPE_DENIED);
        var query = ProjectBusinessContextApi.Query.builder().tenantId(command.tenantId()).actorId(command.actorId())
                .projectId(command.projectId()).expectedScopeVersion(scope.treeVersion()).build();
        var inspected = projects.inspect(query);
        var revalidation = ProjectBusinessContextApi.Revalidation.builder().query(ProjectBusinessContextApi.Query.builder()
                .tenantId(command.tenantId()).actorId(command.actorId()).projectId(command.projectId())
                .expectedScopeVersion(inspected.scopeVersion()).build()).expectedProjectVersion(inspected.projectVersion()).build();
        var project = projects.lockAndRevalidate(revalidation);
        var managers = projects.lockServiceManagers(revalidation);
        if (managers == null || managers.userIds() == null || !managers.userIds().contains(command.actorId()))
            throw exception(CUSTOMER_SCOPE_DENIED);
        var customer = customers.lockCustomerByCode(new CustomerCodeQuery(command.customerCode(), command.actorId()));
        if (customer == null || !Objects.equals(customer.id(), command.customerId())) throw exception(CUSTOMER_NOT_EXISTS);
        var ordered = command.references().stream().sorted(Comparator.comparing((SourceReference r) -> r.kind().name())
                .thenComparing(SourceReference::sourceId)).toList();
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(new Source(command.customerId(), command.projectId(),
                command.taskId(), command.archivedTaskVersion(), command.taskCode(), command.taskName(), command.reportId(), ordered)));
        var existing = histories.selectByTenantAndTask(command.tenantId(), command.taskId());
        if (existing != null) {
            if (!Objects.equals(fingerprint, existing.getSourceFingerprint())) throw exception(CUSTOMER_VERSION_CONFLICT);
            return new ArchiveReference(existing.getId(), existing.getTaskId(), existing.getTaskVersion());
        }
        var row = new CustomerInspectionHistoryDO(); row.setTenantId(command.tenantId()); row.setVersion(1L);
        row.setCustomerId(command.customerId()); row.setProjectId(command.projectId()); row.setProjectCode(project.projectCode());
        row.setProjectName(project.projectName()); row.setTaskId(command.taskId()); row.setTaskVersion(command.archivedTaskVersion());
        row.setTaskCode(command.taskCode()); row.setTaskName(command.taskName()); row.setReportId(command.reportId());
        row.setSourceFingerprint(fingerprint); row.setArchivedBy(command.actorId()); row.setArchivedAt(LocalDateTime.now()); histories.insert(row);
        for (var source : ordered) {
            var ref = new CustomerInspectionReferenceDO(); ref.setTenantId(row.getTenantId()); ref.setVersion(1L);
            ref.setHistoryId(row.getId()); ref.setReferenceKind(source.kind().name()); ref.setSourceId(source.sourceId()); references.insert(ref);
        }
        return new ArchiveReference(row.getId(), row.getTaskId(), row.getTaskVersion());
    }

    public record HistoryQuery(Long customerId, PageParam page) { }
    @Data public static class HistoryView {
        private Long id, projectId, taskId, taskVersion, reportId, archivedBy;
        private String projectCode, projectName, taskCode, taskName;
        private LocalDateTime archivedAt;
        private List<SourceReference> references;
    }
    public PageResult<HistoryView> page(HistoryQuery query) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || !permissions.hasAnyPermissions(actor, "pms:customer:query")) throw exception(CUSTOMER_SCOPE_DENIED);
        if (customerQueries.get(tenant, query.customerId(), customerScopes.resolve(tenant, actor)) == null) throw exception(CUSTOMER_NOT_EXISTS);
        var visible = projectScopes.resolveAllCurrent(new ProjectAllScopeQuery(tenant, actor, ProjectScopeApi.ACTION_VIEW));
        var page = histories.selectVisiblePage(new CustomerInspectionHistoryPageQuery(tenant, query.customerId(), Set.copyOf(visible), query.page()));
        var refRows = references.selectForHistory(new CustomerInspectionReferenceQuery(tenant,
                page.getList().stream().map(CustomerInspectionHistoryDO::getId).collect(Collectors.toSet())));
        var items = page.getList().stream().map(row -> {
            var view = BeanUtils.toBean(row, HistoryView.class);
            view.setReferences(refRows.stream().filter(r -> Objects.equals(r.getHistoryId(), row.getId()))
                    .map(r -> new SourceReference(Kind.valueOf(r.getReferenceKind()), r.getSourceId())).toList()); return view;
        }).toList();
        return new PageResult<>(items, page.getTotal());
    }
    private record Source(Long customerId, Long projectId, Long taskId, Long taskVersion, String taskCode,
                          String taskName, Long reportId, List<SourceReference> references) { }
    private void validate(ArchiveCommand command) {
        if (command == null || !Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())
                || command.actorId() == null || !Objects.equals(command.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(command.actorId(), "pms:inspection-task:archive")) throw exception(CUSTOMER_SCOPE_DENIED);
        if (command.customerId() == null || command.customerId() <= 0 || command.customerCode() == null || command.customerCode().isBlank()
                || command.projectId() == null || command.projectId() <= 0 || command.taskId() == null || command.taskId() <= 0
                || command.archivedTaskVersion() == null || command.archivedTaskVersion() <= 0 || command.reportId() == null || command.reportId() <= 0
                || command.taskCode() == null || command.taskCode().isBlank() || command.taskCode().length() > 64
                || command.taskName() == null || command.taskName().isBlank() || command.taskName().length() > 282
                || command.references() == null || command.references().isEmpty()
                || command.references().stream().anyMatch(r -> r == null || r.kind() == null || r.sourceId() == null || r.sourceId() <= 0)
                || new HashSet<>(command.references()).size() != command.references().size()
                || !command.references().contains(new SourceReference(Kind.REPORT, command.reportId())))
            throw new IllegalArgumentException("巡检归档来源引用不完整或重复");
    }
}
