package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.ArrivalAcceptanceFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.*;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.implementationreadiness.ImplementationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness.*;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.*;

@Service @RequiredArgsConstructor
public class ImplementationReadinessService implements ImplementationReadinessApi, ImplementationReadinessEvaluationApi {
    private final ImplementationReadinessSnapshotMapper snapshots;
    private final ImplementationReadinessSourceMapper sources;
    private final ProjectBusinessContextApi projects;
    private final ProjectScopeApi scopes;
    private final DeviceQueryApi devices;
    private final ArrivalAcceptanceFactApi arrivals;
    private final DeliveryScopePort delivery;
    private final SolutionTieredReviewService reviews;
    private final ImplementationReadinessSnapshotStore snapshotStore;

    @Override @Transactional(rollbackFor = Exception.class)
    public ImplementationReadinessResult evaluate(ImplementationReadinessQuery query) {
        var project = project(query, true);
        var current = context(query, project, true);
        var lookup = new ReadinessSnapshotLookup(query.tenantId(), query.projectId(), deviceKey(query));
        var previous = snapshots.latest(lookup);
        if (previous != null && current.equals(snapshot(previous).context())) return result(snapshot(previous), current);
        var row = new ImplementationReadinessSnapshotDO();
        row.setTenantId(query.tenantId()); row.setProjectId(query.projectId()); row.setDeviceScopeKey(deviceKey(query));
        row.setSnapshotNo(snapshots.lastNumber(new ReadinessProjectQuery(query.tenantId(), query.projectId(), true)) + 1);
        row.setDecision(current.isReady() ? "READY" : "NOT_READY");
        row.setProjectScopeVersion(current.projectScopeVersion());
        row.setApprovedPlanId(current.approvedPlan().planId()); row.setApprovedPlanVersion(current.approvedPlan().planVersion());
        if (current.planApplicability() != null) {
            row.setProjectPlanVersionId(current.planApplicability().planVersionId());
            row.setProjectPlanVersion(current.planApplicability().planVersion());
        }
        row.setEvaluatedBy(actor());
        snapshots.insert(row);
        snapshotStore.insert(new ImplementationReadinessSnapshotStore.Write(row, current, unmet(current), actor(), row.getCreateTime()));
        // Reload database time; the returned identity is exactly the immutable stored snapshot.
        return result(snapshot(snapshots.selectById(row.getId())), current);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ImplementationReadinessResult inspect(ImplementationReadinessQuery query) {
        var project = project(query, false);
        var row = snapshots.latest(new ReadinessSnapshotLookup(query.tenantId(), query.projectId(), deviceKey(query)));
        if (row == null) throw failure(SNAPSHOT_NOT_FOUND, "请先在项目入口执行 EXE-06 就绪校验");
        return result(snapshot(row), context(query, project, false));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ImplementationReadinessResult lockAndRevalidate(ImplementationReadinessRevalidationQuery query) {
        var requested = new ImplementationReadinessQuery(query.tenantId(), query.projectId(), query.expectedDevices());
        var project = project(requested, false);
        project = projects.lockAndRevalidate(ProjectBusinessContextApi.Revalidation.builder()
                .query(ProjectBusinessContextApi.Query.builder()
                .tenantId(query.tenantId())
                .actorId(actor())
                .projectId(query.projectId())
                .expectedScopeVersion(project.scopeVersion()).build())
                .expectedProjectVersion(project.projectVersion()).build());
        var row = snapshots.selectById(query.expectedSnapshotId());
        if (row == null || !Objects.equals(row.getTenantId(), query.tenantId())
                || !Objects.equals(row.getProjectId(), query.projectId()) || !Objects.equals(row.getDeviceScopeKey(), deviceKey(requested))
                || query.expectedSnapshotVersion() != 0L) throw failure(SNAPSHOT_NOT_FOUND, "EXE-06 快照身份或设备范围不一致");
        return result(snapshot(row), context(requested, project, true));
    }

    @Override
    public ImplementationReadinessSnapshotFact readSnapshot(ImplementationReadinessSnapshotQuery query) {
        if (query == null || TenantContextHolder.isIgnore() || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())) {
            throw failure(TENANT_CONTEXT_MISMATCH, "实施就绪租户身份不一致");
        }
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(query.tenantId()).subjectUserId(actor())
                .anchorProjectId(query.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (!scope.fullProjectIds().contains(query.projectId())) throw new SecurityException("无权读取该项目就绪快照");
        var row = snapshots.selectById(query.snapshotId());
        if (row == null || !Objects.equals(row.getTenantId(), query.tenantId()) || !Objects.equals(row.getProjectId(), query.projectId())
                || query.snapshotVersion() != 0L) throw failure(SNAPSHOT_NOT_FOUND, "精确就绪快照不存在");
        return snapshot(row);
    }

    private ProjectBusinessContextApi.Context project(ImplementationReadinessQuery query, boolean write) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()))
            throw failure(TENANT_CONTEXT_MISMATCH, "实施就绪租户身份不一致");
        Long actor = actor();
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(actor)
                .anchorProjectId(query.projectId())
                .actionCode(write ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_VIEW).build());
        if (!scope.fullProjectIds().contains(query.projectId())) throw new SecurityException("无权校验该项目实施就绪");
        var request = ProjectBusinessContextApi.Query.builder()
                .tenantId(query.tenantId())
                .actorId(actor)
                .projectId(query.projectId())
                .expectedScopeVersion(scope.treeVersion()).build();
        var current = projects.inspect(request);
        return write ? projects.lockAndRevalidate(ProjectBusinessContextApi.Revalidation.builder()
                .query(request)
                .expectedProjectVersion(current.projectVersion()).build()) : current;
    }

    private ImplementationReadinessContextFact context(ImplementationReadinessQuery query,
            ProjectBusinessContextApi.Context project, boolean lock) {
        var all = devices.lockProjectInstallationDevices(new ProjectDeviceInstallationQuery(query.tenantId(), query.projectId()))
                .stream().map(value -> {
                    var device = devices.getDevice(value.deviceId());
                    if (device == null || !Objects.equals(query.tenantId(), device.tenantId())
                            || !Objects.equals(query.projectId(), device.projectId()))
                        throw failure(OWNER_DATA_CORRUPTED, "AST 当前项目设备事实不一致");
                    return new DeviceFact(device.deviceId(), device.sn(), device.projectAssignmentVersion());
                }).sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
        var selected = query.expectedDevices().stream().map(expected -> {
            var fact = all.stream().filter(device -> Objects.equals(device.deviceId(), expected.deviceId())).findFirst()
                    .orElseThrow(() -> failure(INVALID_REQUEST, "设备已不属于当前项目"));
            if (!ImplementationReadinessQuery.comparisonKey(fact.serialNumber()).equals(
                    ImplementationReadinessQuery.comparisonKey(expected.serialNumber()))
                    || !Objects.equals(fact.projectAssignmentVersion(), expected.projectAssignmentVersion()))
                throw failure(INVALID_REQUEST, "设备范围版本已变化，请重新校验");
            return fact;
        }).toList();
        var policyQuery = new ProjectBusinessContextApi.Query(query.tenantId(), actor(), query.projectId(), project.scopeVersion());
        var requirements = lock ? projects.lockImplementationRequirements(
                new ProjectBusinessContextApi.Revalidation(policyQuery, project.projectVersion())) : projects.implementationRequirements(policyQuery);
        if (requirements == null) throw failure(OWNER_DATA_CORRUPTED, "PROJ冻结实施前置不可用");
        var applicable = requirements.requiredSourceCodes().stream().map(SourceCode::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var planApplicability = requirements.configured()
                ? new PlanApplicability(requirements.planVersionId(), requirements.planVersion(), applicable) : null;
        var owner = new ReadinessProjectQuery(query.tenantId(), query.projectId(), lock);
        var plan = sources.approvedSolutions(owner).stream().filter(row -> Integer.valueOf(0).equals(row.getReviewLevel())
                || Integer.valueOf(1).equals(row.getReviewLevel()) && reviews.approved(row, lock)).findFirst()
                .orElseThrow(() -> failure(SNAPSHOT_NOT_FOUND, "尚无 SCH-05 最终批准的实施方案"));
        var installed = !applicable.contains(SourceCode.EXE_02) ? List.<ImplementationReadinessRules.Record>of()
                : sources.installations(owner).stream().map(row -> new ImplementationReadinessRules.Record(
                row.getId(), row.getVersion(), row.getEquipmentId(), Integer.valueOf(2).equals(row.getStatus())
                && row.getEffectiveFrom() != null && row.getInstallLocation() != null && !row.getInstallLocation().isBlank())).toList();
        var configured = !applicable.contains(SourceCode.EXE_03) ? List.<ImplementationReadinessRules.Record>of()
                : sources.configurations(owner).stream().map(row -> new ImplementationReadinessRules.Record(
                row.getId(), row.getVersion(), row.getEquipmentId(), Integer.valueOf(2).equals(row.getStatus()))).toList();
        var tested = !applicable.contains(SourceCode.EXE_04) ? List.<ImplementationReadinessRules.Record>of()
                : sources.jointTests(owner).stream().map(row -> new ImplementationReadinessRules.Record(
                row.getId(), row.getVersion(), row.getEquipmentId(), Integer.valueOf(2).equals(row.getStatus()))).toList();
        return new ImplementationReadinessContextFact(project.scopeVersion(), selected,
                new ApprovedPlanFact(plan.getId(), plan.getVersion()), List.of(
                applicable.contains(SourceCode.EXE_01) ? arrival(query, all, lock) : notApplicable(SourceCode.EXE_01),
                applicable.contains(SourceCode.EXE_02) ? ImplementationReadinessRules.milestone(SourceCode.EXE_02, installed, all) : notApplicable(SourceCode.EXE_02),
                applicable.contains(SourceCode.EXE_03) ? ImplementationReadinessRules.milestone(SourceCode.EXE_03, configured, all) : notApplicable(SourceCode.EXE_03),
                applicable.contains(SourceCode.EXE_04) ? ImplementationReadinessRules.milestone(SourceCode.EXE_04, tested, all) : notApplicable(SourceCode.EXE_04)),
                planApplicability);
    }

    private static SourceFact notApplicable(SourceCode code) {
        return new SourceFact(code, CompletionStatus.NOT_APPLICABLE, 0L, List.of(), List.of(), false);
    }

    private SourceFact arrival(ImplementationReadinessQuery query, List<DeviceFact> all, boolean lock) {
        var assigned = delivery.inspectAssignedScope(query.projectId());
        if (lock) assigned = delivery.lockAndRevalidate(query.projectId(), assigned.scopeVersion());
        var quantities = assigned.lines().stream().filter(line -> line.serialNumbers().isEmpty()).map(line ->
                new ArrivalQuantityScopeFact(line.orderLineId(), line.productCode(), line.productModel(), line.assignedQuantity(), line.unitCode())).toList();
        var ids = all.stream().map(DeviceFact::deviceId).collect(java.util.stream.Collectors.toSet());
        var fact = arrivals.inspect(new ArrivalAcceptanceFactQuery(query.tenantId(), query.projectId(), ids, quantities));
        if (lock) fact = arrivals.lockAndRevalidate(new ArrivalAcceptanceFactRevalidationQuery(query.tenantId(),
                query.projectId(), ids, quantities, fact.factVersion(), fact.scopeWatermark()));
        var marks = new ArrayList<WatermarkEntry>();
        for (var id : fact.sourceAcceptanceIds()) marks.add(new WatermarkEntry("ARRIVAL", id, fact.factVersion()));
        if (!marks.isEmpty()) {
            marks.add(new WatermarkEntry("DELIVERY_SCOPE", query.projectId(), fact.scopeWatermark().deliveryScopeVersion()));
            all.forEach(device -> marks.add(new WatermarkEntry("DEVICE_ASSIGNMENT", device.deviceId(), device.projectAssignmentVersion())));
        }
        return new SourceFact(SourceCode.EXE_01, "ACCEPTED".equals(fact.decision()) && !fact.reopened()
                ? CompletionStatus.ACCEPTED : CompletionStatus.NOT_COMPLETED,
                fact.factVersion(), fact.sourceAcceptanceIds(), marks, fact.reopened());
    }

    private ImplementationReadinessSnapshotFact snapshot(ImplementationReadinessSnapshotDO row) {
        return snapshotStore.read(row);
    }
    private static ImplementationReadinessResult result(ImplementationReadinessSnapshotFact snapshot, ImplementationReadinessContextFact context) {
        boolean same = snapshot.context().equals(context);
        return new ImplementationReadinessResult(same ? ImplementationReadinessResult.Decision.valueOf(snapshot.decision().name())
                : ImplementationReadinessResult.Decision.STALE, snapshot, context, unmet(context),
                same ? List.of() : List.of("IMPLEMENTATION_SOURCE_CHANGED"));
    }
    private static List<String> unmet(ImplementationReadinessContextFact context) {
        return context.sourceFacts().stream().filter(fact -> fact.reopened() || fact.completionStatus() == CompletionStatus.NOT_COMPLETED)
                .map(fact -> fact.sourceCode().name() + "_NOT_COMPLETED").toList();
    }
    private static String deviceKey(ImplementationReadinessQuery query) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(query.expectedDevices()));
    }
    private static Long actor() {
        var actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0) throw new SecurityException("实施就绪需要受信用户");
        return actor;
    }
    private static ImplementationReadinessException failure(ImplementationReadinessException.Code code, String message) {
        return new ImplementationReadinessException(code, message);
    }
}
