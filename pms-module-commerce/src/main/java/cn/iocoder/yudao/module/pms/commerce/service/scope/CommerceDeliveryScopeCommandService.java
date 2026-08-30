package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.CommerceDeliveryScopeCommandQuery.*;
import cn.iocoder.yudao.module.pms.commerce.domain.scope.DeliveryScopeStateMachine;
import cn.iocoder.yudao.module.pms.commerce.domain.scope.DeliveryScopeValidationRules;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.*;

/** F-COM-001唯一新增范围写入口；旧DeliveryScopeService不调用本服务。 */
@Service
public class CommerceDeliveryScopeCommandService {

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final ProjectScopeQualificationAdapter projectQualification;
    private final DeviceAndLocationFactAdapter deviceAndLocation;
    private final CommerceDeliveryScopeCommandMapper commandMapper;
    private final DeliveryScopeMapper scopeMapper;
    private final DeliveryScopeDetailMapper detailMapper;
    private final CommerceOutboxEventMapper outboxMapper;
    private final DeliveryScopeValidationRules rules;
    private final DeliveryScopeStateMachine stateMachine;
    private final Clock clock;

    @Autowired
    public CommerceDeliveryScopeCommandService(PlatformCommandExecutionApi commandExecutionApi,
                                               ProjectScopeQualificationAdapter projectQualification,
                                               DeviceAndLocationFactAdapter deviceAndLocation,
                                               CommerceDeliveryScopeCommandMapper commandMapper,
                                               DeliveryScopeMapper scopeMapper,
                                               DeliveryScopeDetailMapper detailMapper,
                                               CommerceOutboxEventMapper outboxMapper) {
        this(commandExecutionApi, projectQualification, deviceAndLocation, commandMapper, scopeMapper,
                detailMapper, outboxMapper, new DeliveryScopeValidationRules(),
                new DeliveryScopeStateMachine(), Clock.systemDefaultZone());
    }

    CommerceDeliveryScopeCommandService(PlatformCommandExecutionApi commandExecutionApi,
                                        ProjectScopeQualificationAdapter projectQualification,
                                        DeviceAndLocationFactAdapter deviceAndLocation,
                                        CommerceDeliveryScopeCommandMapper commandMapper,
                                        DeliveryScopeMapper scopeMapper,
                                        DeliveryScopeDetailMapper detailMapper,
                                        CommerceOutboxEventMapper outboxMapper,
                                        DeliveryScopeValidationRules rules,
                                        DeliveryScopeStateMachine stateMachine, Clock clock) {
        this.commandExecutionApi = commandExecutionApi;
        this.projectQualification = projectQualification;
        this.deviceAndLocation = deviceAndLocation;
        this.commandMapper = commandMapper;
        this.scopeMapper = scopeMapper;
        this.detailMapper = detailMapper;
        this.outboxMapper = outboxMapper;
        this.rules = rules;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandResult apply(ApplyCommand command) {
        requireCommon(command == null ? null : command.tenantId(), command == null ? null : command.projectId(),
                command == null ? null : command.actorId(), command == null ? null : command.expectedScopeVersion(),
                command == null ? null : command.idempotencyKey(), command == null ? null : command.correlationId());
        List<ScopeLine> lines = rules.requireLines(command.lines());
        String reason = rules.requireText(command.reason(), 128, "reason");
        ProjectScopeQualificationAdapter.Snapshot project = projectQualification.inspect(
                command.tenantId(), command.projectId(), command.actorId());
        DeviceAndLocationFactAdapter.Snapshot device = deviceAndLocation.inspect(
                command.tenantId(), command.projectId(), lines);
        return execute(command.tenantId(), command.actorId(), "APPLY", command.idempotencyKey(),
                digest(new ApplyDigest(command.projectId(), command.expectedScopeVersion(), lines, reason)),
                command.correlationId(), () -> applyLocked(command, lines, reason, project, device));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandResult release(ReleaseCommand command) {
        requireCommon(command == null ? null : command.tenantId(), command == null ? null : command.projectId(),
                command == null ? null : command.actorId(), command == null ? null : command.expectedScopeVersion(),
                command == null ? null : command.idempotencyKey(), command == null ? null : command.correlationId());
        List<Long> orderLineIds = rules.requireOrderLineIds(command.orderLineIds());
        String reason = rules.requireText(command.reason(), 128, "reason");
        String evidence = rules.requireText(command.releaseEvidence(), 128, "releaseEvidence");
        ProjectScopeQualificationAdapter.Snapshot project = projectQualification.inspect(
                command.tenantId(), command.projectId(), command.actorId());
        return execute(command.tenantId(), command.actorId(), "RELEASE", command.idempotencyKey(),
                digest(new ReleaseDigest(command.projectId(), command.expectedScopeVersion(), orderLineIds,
                        reason, evidence)), command.correlationId(),
                () -> releaseLocked(command, orderLineIds, reason, evidence, project));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandResult resolveConflict(ResolveConflictCommand command) {
        requireCommon(command == null ? null : command.tenantId(), command == null ? null : command.projectId(),
                command == null ? null : command.actorId(), command == null ? null : command.expectedScopeVersion(),
                command == null ? null : command.idempotencyKey(), command == null ? null : command.correlationId());
        if (command.resolution() == null) invalid("resolution不能为空");
        String evidence = rules.requireText(command.evidence(), 128, "evidence");
        List<ScopeLine> lines = command.resolution() == Resolution.ACTIVE
                ? rules.requireLines(command.lines()) : List.of();
        List<Long> orderLineIds = command.resolution() == Resolution.ACTIVE
                ? lines.stream().map(ScopeLine::orderLineId).toList()
                : rules.requireOrderLineIds(command.orderLineIds());
        ProjectScopeQualificationAdapter.Snapshot project = projectQualification.inspect(
                command.tenantId(), command.projectId(), command.actorId());
        DeviceAndLocationFactAdapter.Snapshot device = command.resolution() == Resolution.ACTIVE
                ? deviceAndLocation.inspect(command.tenantId(), command.projectId(), lines)
                : new DeviceAndLocationFactAdapter.Snapshot(List.of());
        return execute(command.tenantId(), command.actorId(), "RESOLVE_CONFLICT", command.idempotencyKey(),
                digest(new ResolveDigest(command.projectId(), command.expectedScopeVersion(), command.resolution(),
                        lines, orderLineIds, evidence)), command.correlationId(),
                () -> resolveLocked(command, lines, orderLineIds, evidence, project, device));
    }

    private CommandResult applyLocked(ApplyCommand command, List<ScopeLine> lines, String reason,
                                      ProjectScopeQualificationAdapter.Snapshot expectedProject,
                                      DeviceAndLocationFactAdapter.Snapshot expectedDevice) {
        ProjectScopeQualificationAdapter.Snapshot currentProject =
                projectQualification.lockAndRevalidate(expectedProject);
        deviceAndLocation.lockAndRevalidate(command.tenantId(), command.projectId(), expectedDevice, lines);
        LockedContext locked = lock(command.tenantId(), command.projectId(), command.expectedScopeVersion(),
                lines.stream().map(ScopeLine::orderLineId).toList(), command.actorId());
        validateQualifiedLines(lines, locked.linesById());
        Map<Long, DeliveryScopeDO> current = currentProjectScopes(locked, command.projectId());
        current.values().forEach(scope -> stateMachine.requireAdjustable(scope.getScopeStatus()));
        validateCapacity(lines, locked.currentScopes(), current, locked.linesById());
        LocalDateTime now = LocalDateTime.now(clock);
        long nextScopeVersion = locked.projectVersion().getScopeVersion() + 1;
        List<Long> newIds = new ArrayList<>();
        boolean protectedConflict = false;
        for (ScopeLine line : lines) {
            DeliveryScopeDO old = current.get(line.orderLineId());
            if (old != null) {
                List<DeliveryScopeDetailDO> oldDetails = requireStoredDetails(locked, old);
                end(old, oldDetails.size(), command.tenantId(), command.actorId(), now);
                if (currentProject.protectsReduction()
                        && line.quantity().compareTo(old.getAllocatedQty()) < 0) {
                    DeliveryScopeDO conflict = insertScope(command.tenantId(), command.projectId(),
                            line.orderLineId(), old.getAllocatedQty(), "CONFLICT",
                            nextAllocationVersion(locked, line.orderLineId()),
                            evidence("PROTECTED_ADJUST", reason), command.actorId(), now);
                    copyDetails(command.tenantId(), conflict, oldDetails, "ACTIVE", command.actorId(), now);
                    newIds.add(conflict.getId());
                    protectedConflict = true;
                    continue;
                }
                outbox("DeliveryScopeReleased", old, nextScopeVersion, command.tenantId(), now,
                        old.getAllocatedQty(), oldDetails, command.actorId());
            }
            DeliveryScopeDO created = insertScope(command.tenantId(), command.projectId(), line.orderLineId(),
                    line.quantity(), "ACTIVE", nextAllocationVersion(locked, line.orderLineId()),
                    evidence("APPLY", reason), command.actorId(), now);
            List<DeliveryScopeDetailDO> createdDetails = insertDetails(command.tenantId(), created, line,
                    expectedDevice, "ACTIVE", command.actorId(), now);
            outbox("DeliveryScopeAssigned", created, nextScopeVersion, command.tenantId(), now,
                    line.quantity(), createdDetails, command.actorId());
            newIds.add(created.getId());
        }
        advanceProjectVersion(locked.projectVersion(), nextScopeVersion,
                protectedConflict ? "PROTECTED_CONFLICT" : "ASSIGNED", command.actorId(), now);
        return new CommandResult("APPLY", command.projectId(), nextScopeVersion, newIds, protectedConflict);
    }

    private CommandResult releaseLocked(ReleaseCommand command, List<Long> orderLineIds, String reason,
                                        String releaseEvidence,
                                        ProjectScopeQualificationAdapter.Snapshot expectedProject) {
        ProjectScopeQualificationAdapter.Snapshot currentProject =
                projectQualification.lockAndRevalidate(expectedProject);
        LockedContext locked = lock(command.tenantId(), command.projectId(), command.expectedScopeVersion(),
                orderLineIds, command.actorId());
        Map<Long, DeliveryScopeDO> current = requireCurrentProjectScopes(locked, command.projectId(), orderLineIds);
        current.values().forEach(scope -> stateMachine.requireReleasable(scope.getScopeStatus()));
        boolean protectedConflict = currentProject.protectsReduction();
        LocalDateTime now = LocalDateTime.now(clock);
        long nextScopeVersion = locked.projectVersion().getScopeVersion() + 1;
        List<Long> newIds = new ArrayList<>();
        for (Long orderLineId : orderLineIds) {
            DeliveryScopeDO old = current.get(orderLineId);
            List<DeliveryScopeDetailDO> oldDetails = requireStoredDetails(locked, old);
            end(old, oldDetails.size(), command.tenantId(), command.actorId(), now);
            String nextState = protectedConflict ? "CONFLICT" : "RELEASED";
            DeliveryScopeDO created = insertScope(command.tenantId(), command.projectId(), orderLineId,
                    old.getAllocatedQty(), nextState, nextAllocationVersion(locked, orderLineId),
                    evidence(protectedConflict ? "PROTECTED_RELEASE" : "RELEASE", reason + ":" + releaseEvidence),
                    command.actorId(), now);
            List<DeliveryScopeDetailDO> createdDetails = copyDetails(command.tenantId(), created, oldDetails,
                    protectedConflict ? "ACTIVE" : "RELEASED",
                    command.actorId(), now);
            if (!protectedConflict) outbox("DeliveryScopeReleased", created, nextScopeVersion,
                    command.tenantId(), now, created.getAllocatedQty(), createdDetails, command.actorId());
            newIds.add(created.getId());
        }
        advanceProjectVersion(locked.projectVersion(), nextScopeVersion,
                protectedConflict ? "PROTECTED_CONFLICT" : "RELEASED", command.actorId(), now);
        return new CommandResult("RELEASE", command.projectId(), nextScopeVersion, newIds, protectedConflict);
    }

    private CommandResult resolveLocked(ResolveConflictCommand command, List<ScopeLine> lines,
                                        List<Long> orderLineIds, String evidence,
                                        ProjectScopeQualificationAdapter.Snapshot expectedProject,
                                        DeviceAndLocationFactAdapter.Snapshot expectedDevice) {
        projectQualification.lockAndRevalidate(expectedProject);
        if (command.resolution() == Resolution.ACTIVE) {
            deviceAndLocation.lockAndRevalidate(command.tenantId(), command.projectId(), expectedDevice, lines);
        }
        LockedContext locked = lock(command.tenantId(), command.projectId(), command.expectedScopeVersion(),
                orderLineIds, command.actorId());
        Map<Long, DeliveryScopeDO> current = requireCurrentProjectScopes(locked, command.projectId(), orderLineIds);
        current.values().forEach(scope -> stateMachine.requireResolvable(scope.getScopeStatus()));
        if (command.resolution() == Resolution.ACTIVE) {
            validateQualifiedLines(lines, locked.linesById());
            validateCapacity(lines, locked.currentScopes(), current, locked.linesById());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long nextScopeVersion = locked.projectVersion().getScopeVersion() + 1;
        Map<Long, ScopeLine> desired = lines.stream().collect(Collectors.toMap(ScopeLine::orderLineId, value -> value));
        List<Long> newIds = new ArrayList<>();
        for (Long orderLineId : orderLineIds) {
            DeliveryScopeDO old = current.get(orderLineId);
            List<DeliveryScopeDetailDO> oldDetails = requireStoredDetails(locked, old);
            end(old, oldDetails.size(), command.tenantId(), command.actorId(), now);
            ScopeLine line = desired.get(orderLineId);
            boolean active = command.resolution() == Resolution.ACTIVE;
            BigDecimal quantity = active ? line.quantity() : old.getAllocatedQty();
            DeliveryScopeDO created = insertScope(command.tenantId(), command.projectId(), orderLineId,
                    quantity, active ? "ACTIVE" : "RELEASED", nextAllocationVersion(locked, orderLineId),
                    evidence("RESOLVE_" + command.resolution(), evidence), command.actorId(), now);
            List<DeliveryScopeDetailDO> createdDetails;
            if (active) {
                createdDetails = insertDetails(command.tenantId(), created, line, expectedDevice,
                        "ACTIVE", command.actorId(), now);
            } else {
                createdDetails = copyDetails(command.tenantId(), created, oldDetails,
                        "RELEASED", command.actorId(), now);
            }
            outbox(active ? "DeliveryScopeAssigned" : "DeliveryScopeReleased", created, nextScopeVersion,
                    command.tenantId(), now, quantity, createdDetails, command.actorId());
            newIds.add(created.getId());
        }
        advanceProjectVersion(locked.projectVersion(), nextScopeVersion,
                command.resolution() == Resolution.ACTIVE ? "CONFLICT_RESOLVED_ACTIVE" : "CONFLICT_RESOLVED_RELEASED",
                command.actorId(), now);
        return new CommandResult("RESOLVE_CONFLICT", command.projectId(), nextScopeVersion, newIds, false);
    }

    private LockedContext lock(Long tenantId, Long projectId, Long expectedScopeVersion,
                               List<Long> orderLineIds, Long actorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String actor = String.valueOf(actorId);
        commandMapper.insertProjectVersionIfAbsent(new ProjectVersionSeed(
                IdWorker.getId(), tenantId, projectId, actor, now));
        DeliveryScopeProjectVersionDO projectVersion = commandMapper.selectProjectVersionForUpdate(
                new ProjectLock(tenantId, projectId));
        if (projectVersion == null || !Objects.equals(projectVersion.getScopeVersion(), expectedScopeVersion)) {
            throw new CommerceDeliveryScopeCommandException(SCOPE_STALE, "项目交付范围水位已变化");
        }
        List<OrderLineDO> lines = commandMapper.selectOrderLinesForUpdate(new OrderLinesLock(tenantId, orderLineIds));
        List<DeliveryScopeDO> scopes = commandMapper.selectCurrentScopesForUpdate(
                new CurrentScopesLock(tenantId, orderLineIds));
        List<Long> scopeIds = scopes.stream().map(DeliveryScopeDO::getId).toList();
        List<DeliveryScopeDetailDO> details = scopeIds.isEmpty() ? List.of()
                : commandMapper.selectScopeDetailsForUpdate(new ScopeDetailsLock(tenantId, scopeIds));
        Map<Long, Long> maxVersions = commandMapper.selectMaxAllocationVersions(
                        new AllocationVersionQuery(tenantId, projectId, orderLineIds)).stream()
                .collect(Collectors.toMap(AllocationVersionFact::orderLineId,
                        fact -> fact.maxAllocationVersion() == null ? 0L : fact.maxAllocationVersion()));
        return new LockedContext(projectVersion, lines.stream().collect(Collectors.toMap(OrderLineDO::getId, value -> value)),
                scopes, details.stream().collect(Collectors.groupingBy(DeliveryScopeDetailDO::getDeliveryScopeId,
                        LinkedHashMap::new, Collectors.toList())), maxVersions);
    }

    private void validateQualifiedLines(List<ScopeLine> requested, Map<Long, OrderLineDO> locked) {
        if (locked.size() != requested.size()) {
            throw new CommerceDeliveryScopeCommandException(ORDER_LINE_NOT_QUALIFIED, "订单行不存在或不可见");
        }
        for (ScopeLine request : requested) {
            OrderLineDO line = locked.get(request.orderLineId());
            if (line == null || !"CONFIRMED".equals(line.getQuantityStatus())
                    || !"ACTIVE".equals(line.getSourceLifecycleStatus()) || line.getQuantity() == null
                    || line.getQuantity().signum() <= 0 || line.getSourceUpdatedAt() == null
                    || rules.trimToNull(line.getUnitCode()) == null
                    || rules.trimToNull(line.getItemCode()) == null && rules.trimToNull(line.getModelCode()) == null) {
                throw new CommerceDeliveryScopeCommandException(ORDER_LINE_NOT_QUALIFIED, "订单行未满足正式分配资格");
            }
            if (!Objects.equals(request.expectedSourceVersion(), line.getSourceVersion())) {
                throw new CommerceDeliveryScopeCommandException(ORDER_LINE_SOURCE_STALE, "订单行来源版本已变化");
            }
            if (!Objects.equals(request.unitCode(), line.getUnitCode())) {
                throw new CommerceDeliveryScopeCommandException(INVALID_REQUEST, "订单行单位不一致");
            }
            for (ScopeDetail detail : request.details()) {
                if (rules.trimToNull(line.getItemCode()) != null
                        && !Objects.equals(line.getItemCode(), rules.trimToNull(detail.productCode()))
                        || rules.trimToNull(line.getModelCode()) != null
                        && !Objects.equals(line.getModelCode(), rules.trimToNull(detail.modelCode()))) {
                    throw new CommerceDeliveryScopeCommandException(INVALID_REQUEST, "范围产品型号与订单行不一致");
                }
            }
        }
    }

    private void validateCapacity(List<ScopeLine> requested, List<DeliveryScopeDO> currentScopes,
                                  Map<Long, DeliveryScopeDO> replacedByLine,
                                  Map<Long, OrderLineDO> lockedLines) {
        Map<Long, BigDecimal> reserved = new HashMap<>();
        for (DeliveryScopeDO scope : currentScopes) {
            reserved.merge(scope.getOrderLineId(), scope.getAllocatedQty(), BigDecimal::add);
        }
        for (DeliveryScopeDO replaced : replacedByLine.values()) {
            reserved.merge(replaced.getOrderLineId(), replaced.getAllocatedQty().negate(), BigDecimal::add);
        }
        for (ScopeLine request : requested) {
            BigDecimal total = reserved.getOrDefault(request.orderLineId(), BigDecimal.ZERO).add(request.quantity());
            OrderLineDO line = lockedLines.get(request.orderLineId());
            if (line == null || line.getQuantity() == null) {
                throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "订单行锁定数量缺失");
            }
            if (total.compareTo(line.getQuantity()) > 0) {
                throw new CommerceDeliveryScopeCommandException(OVER_ALLOCATION, "项目间分配总量超过ERP权威数量");
            }
        }
    }

    private Map<Long, DeliveryScopeDO> currentProjectScopes(LockedContext locked, Long projectId) {
        return locked.currentScopes().stream().filter(scope -> Objects.equals(projectId, scope.getProjectId()))
                .collect(Collectors.toMap(DeliveryScopeDO::getOrderLineId, value -> value));
    }

    private Map<Long, DeliveryScopeDO> requireCurrentProjectScopes(LockedContext locked, Long projectId,
                                                                    List<Long> orderLineIds) {
        Map<Long, DeliveryScopeDO> current = currentProjectScopes(locked, projectId);
        if (!current.keySet().containsAll(orderLineIds)) {
            throw new CommerceDeliveryScopeCommandException(STATE_CONFLICT, "目标订单行不存在当前范围");
        }
        return current;
    }

    private long nextAllocationVersion(LockedContext locked, Long orderLineId) {
        return locked.maxAllocationVersions().getOrDefault(orderLineId, 0L) + 1;
    }

    private void end(DeliveryScopeDO scope, int expectedDetailCount, Long tenantId, Long actorId,
                     LocalDateTime now) {
        if (commandMapper.endScope(new EndScope(tenantId, scope.getId(), scope.getVersion(), now,
                String.valueOf(actorId), now)) != 1) concurrent();
        if (commandMapper.endDetails(new EndDetails(tenantId, scope.getId(), String.valueOf(actorId), now))
                != expectedDetailCount) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "当前范围明细状态损坏");
        }
    }

    private DeliveryScopeDO insertScope(Long tenantId, Long projectId, Long orderLineId, BigDecimal quantity,
                                        String state, long allocationVersion, String sourceEvidence,
                                        Long actorId, LocalDateTime now) {
        DeliveryScopeDO row = base(new DeliveryScopeDO(), tenantId, actorId, now);
        row.setId(IdWorker.getId());
        row.setOrderLineId(orderLineId);
        row.setProjectId(projectId);
        row.setAllocatedQty(quantity);
        row.setScopeStatus(state);
        row.setAllocationVersion(allocationVersion);
        row.setSourceEvidence(sourceEvidence);
        row.setEffectiveFrom(now);
        row.setEffectiveTo("RELEASED".equals(state) ? now : null);
        row.setVersion(0);
        if (scopeMapper.insert(row) != 1) concurrent();
        return row;
    }

    private List<DeliveryScopeDetailDO> insertDetails(Long tenantId, DeliveryScopeDO scope, ScopeLine line,
                                                      DeviceAndLocationFactAdapter.Snapshot deviceSnapshot,
                                                      String status, Long actorId, LocalDateTime now) {
        Map<String, DeviceAndLocationFactAdapter.Device> devices = deviceSnapshot.devices().stream()
                .collect(Collectors.toMap(device -> DeliveryScopeValidationRules.serialKey(device.serialNumber()),
                        value -> value));
        List<DeliveryScopeDetailDO> created = new ArrayList<>();
        for (ScopeDetail source : line.details()) {
            DeliveryScopeDetailDO row = base(new DeliveryScopeDetailDO(), tenantId, actorId, now);
            row.setId(IdWorker.getId());
            row.setDeliveryScopeId(scope.getId());
            row.setOfficeDepartmentCode(rules.trimToNull(source.officeDepartmentCode()));
            row.setSerialNo(rules.trimToNull(source.serialNumber()));
            row.setAllocatedQty(source.quantity());
            row.setUnitCode(source.unitCode());
            row.setProductCode(rules.trimToNull(source.productCode()));
            row.setModelCode(rules.trimToNull(source.modelCode()));
            row.setSiteId(source.location().siteId());
            row.setSiteLocationId(source.location().siteLocationId());
            row.setLocationText(rules.trimToNull(source.location().locationText()));
            row.setLocationResolutionStatus(source.location().resolution().name());
            row.setDetailStatus(status);
            row.setSourceSnapshot(sourceSnapshot(line, source, devices));
            row.setVersion(0);
            if (detailMapper.insert(row) != 1) concurrent();
            created.add(row);
        }
        return List.copyOf(created);
    }

    private List<DeliveryScopeDetailDO> copyDetails(Long tenantId, DeliveryScopeDO target,
                                                    List<DeliveryScopeDetailDO> sources,
                                                    String status, Long actorId, LocalDateTime now) {
        if (sources.isEmpty()) throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "范围明细缺失");
        List<DeliveryScopeDetailDO> created = new ArrayList<>();
        for (DeliveryScopeDetailDO source : sources) {
            DeliveryScopeDetailDO row = base(new DeliveryScopeDetailDO(), tenantId, actorId, now);
            row.setId(IdWorker.getId());
            row.setDeliveryScopeId(target.getId());
            row.setOfficeDepartmentCode(source.getOfficeDepartmentCode());
            row.setSerialNo(source.getSerialNo());
            row.setAllocatedQty(source.getAllocatedQty());
            row.setUnitCode(source.getUnitCode());
            row.setProductCode(source.getProductCode());
            row.setModelCode(source.getModelCode());
            row.setSiteId(source.getSiteId());
            row.setSiteLocationId(source.getSiteLocationId());
            row.setLocationText(source.getLocationText());
            row.setLocationResolutionStatus(source.getLocationResolutionStatus());
            row.setDetailStatus(status);
            row.setSourceSnapshot(source.getSourceSnapshot());
            row.setVersion(0);
            if (detailMapper.insert(row) != 1) concurrent();
            created.add(row);
        }
        return List.copyOf(created);
    }

    private String sourceSnapshot(ScopeLine line, ScopeDetail detail,
                                  Map<String, DeviceAndLocationFactAdapter.Device> devices) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderLineSourceVersion", line.expectedSourceVersion());
        snapshot.put("siteVersion", detail.location().siteVersion());
        snapshot.put("siteLocationVersion", detail.location().siteLocationVersion());
        String serial = rules.trimToNull(detail.serialNumber());
        DeviceAndLocationFactAdapter.Device device = serial == null ? null
                : devices.get(DeliveryScopeValidationRules.serialKey(serial));
        if (serial != null && device == null) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "AST设备事实未覆盖范围SN");
        }
        snapshot.put("deviceId", device == null ? null : device.deviceId());
        snapshot.put("projectAssignmentVersion", device == null ? null : device.assignmentVersion());
        return JsonUtils.toJsonString(snapshot);
    }

    private void advanceProjectVersion(DeliveryScopeProjectVersionDO current, long nextScopeVersion,
                                       String changeType, Long actorId, LocalDateTime now) {
        int nextPayload = current.getPayloadVersion() + 1;
        if (commandMapper.advanceProjectVersion(new AdvanceProjectVersion(current.getTenantId(), current.getProjectId(),
                current.getScopeVersion(), current.getVersion(), nextScopeVersion, nextPayload, changeType,
                String.valueOf(actorId), now)) != 1) concurrent();
    }

    private void outbox(String eventType, DeliveryScopeDO scope, long scopeVersion, Long tenantId,
                        LocalDateTime now, BigDecimal quantity, List<DeliveryScopeDetailDO> details,
                        Long actorId) {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setId(IdWorker.getId());
        event.setTenantId(tenantId);
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(scopeVersion);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("tenantId", tenantId);
        payload.put("orderLineId", scope.getOrderLineId());
        payload.put("projectId", scope.getProjectId());
        payload.put("scopeId", scope.getId());
        payload.put("scopeVersion", scopeVersion);
        payload.put("allocatedQty", quantity);
        payload.put("dimensionDigest", dimensionDigest(details));
        payload.put("occurredAt", now);
        event.setPayload(JsonUtils.toJsonString(payload));
        event.setStatus("PENDING");
        event.setOccurredAt(now);
        event.setRetryCount(0);
        event.setCreator(String.valueOf(actorId));
        event.setUpdater(String.valueOf(actorId));
        event.setCreateTime(now);
        event.setUpdateTime(now);
        if (outboxMapper.insert(event) != 1) concurrent();
    }

    private List<DeliveryScopeDetailDO> requireStoredDetails(LockedContext locked, DeliveryScopeDO scope) {
        List<DeliveryScopeDetailDO> details = locked.detailsByScope().getOrDefault(scope.getId(), List.of());
        if (details.isEmpty()) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "当前范围明细缺失");
        }
        return details;
    }

    private String dimensionDigest(List<DeliveryScopeDetailDO> details) {
        if (details == null || details.isEmpty()) {
            throw new CommerceDeliveryScopeCommandException(OWNER_DATA_CORRUPTED, "范围明细摘要来源缺失");
        }
        List<DimensionFact> facts = details.stream().map(detail -> new DimensionFact(
                        detail.getOfficeDepartmentCode(), detail.getSerialNo(), detail.getAllocatedQty(),
                        detail.getUnitCode(), detail.getProductCode(), detail.getModelCode(), detail.getSiteId(),
                        detail.getSiteLocationId(), detail.getLocationText(), detail.getLocationResolutionStatus()))
                .sorted(Comparator.comparing(DimensionFact::stableKey)).toList();
        return digest(facts);
    }

    private String evidence(String action, String value) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("action", action);
        evidence.put("value", value);
        String json = JsonUtils.toJsonString(evidence);
        if (json.length() > 255) invalid("sourceEvidence超过物理上限");
        return json;
    }

    private CommandResult execute(Long tenantId, Long actorId, String action, String key, String digest,
                                  String correlationId, Supplier<CommandResult> operation) {
        PlatformCommandExecutionApi.ExecutionResult<CommandResult> result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(tenantId, "COM:DELIVERY_SCOPE:" + action,
                        actorId, key), digest, CommandResult.class, operation,
                response -> new PlatformCommandExecutionApi.SuccessFacts("COM_DELIVERY_SCOPE_" + action,
                        "DeliveryScopeProject", String.valueOf(response.projectId()), correlationId,
                        JsonUtils.toJsonString(response), null, null));
        return switch (result.decision()) {
            case NEW, REPLAY_COMPLETED -> result.response();
            case CONFLICT -> throw new CommerceDeliveryScopeCommandException(IDEMPOTENCY_CONFLICT,
                    "同幂等键载荷冲突");
            case IN_PROGRESS -> throw new CommerceDeliveryScopeCommandException(IDEMPOTENCY_IN_PROGRESS,
                    "同幂等键正在处理中");
        };
    }

    private void requireCommon(Long tenantId, Long projectId, Long actorId, Long expectedScopeVersion,
                               String idempotencyKey, String correlationId) {
        Long trusted;
        try {
            trusted = TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException exception) {
            throw new CommerceDeliveryScopeCommandException(TENANT_CONTEXT_MISMATCH, "缺少受信租户上下文");
        }
        if (!Objects.equals(trusted, tenantId) || tenantId == null || tenantId <= 0 || projectId == null
                || projectId <= 0 || actorId == null || actorId <= 0) {
            throw new CommerceDeliveryScopeCommandException(TENANT_CONTEXT_MISMATCH, "租户、项目或主体非法");
        }
        if (expectedScopeVersion == null || expectedScopeVersion < 0) invalid("expectedScopeVersion非法");
        rules.requireText(idempotencyKey, 128, "Idempotency-Key");
        rules.requireText(correlationId, 128, "correlationId");
    }

    private String digest(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private <T extends cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO> T base(
            T row, Long tenantId, Long actorId, LocalDateTime now) {
        row.setTenantId(tenantId);
        row.setCreator(String.valueOf(actorId));
        row.setUpdater(String.valueOf(actorId));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        return row;
    }

    private void concurrent() {
        throw new CommerceDeliveryScopeCommandException(SCOPE_STALE, "交付范围并发冲突");
    }

    private static void invalid(String message) {
        throw new CommerceDeliveryScopeCommandException(INVALID_REQUEST, message);
    }

    private record LockedContext(DeliveryScopeProjectVersionDO projectVersion,
                                 Map<Long, OrderLineDO> linesById,
                                 List<DeliveryScopeDO> currentScopes,
                                 Map<Long, List<DeliveryScopeDetailDO>> detailsByScope,
                                 Map<Long, Long> maxAllocationVersions) {
    }

    private record DimensionFact(String officeDepartmentCode, String serialNumber, BigDecimal quantity,
                                 String unitCode, String productCode, String modelCode, Long siteId,
                                 Long siteLocationId, String locationText, String locationResolutionStatus) {
        String stableKey() {
            return String.join("|", nullToEmpty(officeDepartmentCode),
                    serialNumber == null ? "" : DeliveryScopeValidationRules.serialKey(serialNumber),
                    quantity == null ? "" : quantity.stripTrailingZeros().toPlainString(), nullToEmpty(unitCode),
                    nullToEmpty(productCode), nullToEmpty(modelCode), String.valueOf(siteId),
                    String.valueOf(siteLocationId), nullToEmpty(locationText), nullToEmpty(locationResolutionStatus));
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private record ApplyDigest(Long projectId, Long expectedScopeVersion, List<ScopeLine> lines, String reason) {
    }

    private record ReleaseDigest(Long projectId, Long expectedScopeVersion, List<Long> orderLineIds,
                                 String reason, String evidence) {
    }

    private record ResolveDigest(Long projectId, Long expectedScopeVersion, Resolution resolution,
                                 List<ScopeLine> lines, List<Long> orderLineIds, String evidence) {
    }
}
