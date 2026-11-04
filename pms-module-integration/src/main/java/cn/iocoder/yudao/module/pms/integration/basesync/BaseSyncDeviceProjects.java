package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask.DeviceRow;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;

/** Shared post-processing of P15/P18/P30/P33, in the caller's existing transaction. */
@Component
@RequiredArgsConstructor
public class BaseSyncDeviceProjects {
    public static final String SOURCE = "BASE_SYNC";
    private final TargetMapper mapper;
    private final ProjectScopeApi projects;
    private final PermissionApi permissions;

    public record DeviceQuery(long tenantId, List<Long> deviceIds) {}
    public record PageQuery(long tenantId, long afterId, int limit, boolean preview) {}
    public record BatchQuery(long tenantId, long connectionId, List<DeviceRow> devices, LocalDateTime at) {}
    public record ProjectQuery(long tenantId, List<Long> projectIds) {}
    public record AliasQuery(long tenantId, long connectionId, List<String> keys) {}
    public record RawQuery(List<DeviceRow> devices) {}
    public record Binding(long deviceId, long id, String sourceKey, String fingerprint) {}
    public record Alias(String sourceKey, long projectId) {}
    public record Relation(long deviceId, long id, Long projectId, long assignmentVersion,
                           String sourceSystem, String evidence, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {}
    public record Fact(long deviceId, Long installationId, Long installationProjectId,
                       String shipmentSource, Long contractProjectId, long contractCount,
                       Long shipmentId, Long orderProjectId, long orderCount) {}
    public record Raw(long deviceId, Long sourceId, String projectKey, boolean clearTransfer) {}
    public record Decision(Long projectId, String evidence) {}
    public record Change(DeviceRow device, Binding binding, Relation previous, Decision decision,
                         long relationshipId, String operationId, String nextFingerprint) {}
    public record WriteBatch(SyncContext<?> context, List<Change> changes) {}
    public record AssignmentEvent(long id, String eventId, long deviceId, String payload) {}
    public record EventBatch(SyncContext<?> context, List<AssignmentEvent> events) {}
    public record TreeFact(long projectId, long treeVersion, Long ancestorProjectId) {}
    public record Watermark(long deviceId, long assignmentVersion) {}
    public record Projection(String sn, long projectId, long treeVersion, long assignmentVersion, String eventId) {}
    public record ProjectionBatch(SyncContext<?> context, List<Projection> projections) {}
    public record Ancestor(String sn, long projectId, long ancestorProjectId, long treeVersion, long assignmentVersion) {}
    public record AncestorBatch(SyncContext<?> context, List<Ancestor> ancestors) {}

    /** A legitimate manual assignment makes the mutable device ineligible for base-sync writes. */
    public Set<Long> manualDeviceIds(DeviceQuery query) {
        var result = new HashSet<Long>();
        for (var row : mapper.selectLatestRelationships(query)) {
            if (!SOURCE.equals(row.sourceSystem())) result.add(row.deviceId());
        }
        return result;
    }

    public void refresh(SyncContext<?> context) {
        if (!context.preview() && !TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("DEVICE_PROJECT_SYNC_TRANSACTION_REQUIRED");
        long cursor = 0, changed = 0, protectedCount = 0, unresolved = 0;
        Set<Long> managed = null;
        while (true) {
            var page = mapper.selectDevices(new PageQuery(context.tenantId(), cursor, context.batchSize(), context.preview()));
            if (page.isEmpty()) break;
            cursor = page.getLast().getId();
            var query = new BatchQuery(context.tenantId(), context.connectionId(), page, context.startedAt());
            var bindings = new HashMap<Long, Binding>();
            for (var binding : mapper.selectBaseBindings(query))
                if (bindings.putIfAbsent(binding.deviceId(), binding) != null) fail("BASE_BINDING_AMBIGUOUS");
            var relations = new HashMap<Long, Relation>();
            for (var relation : mapper.selectLatestRelationships(new DeviceQuery(context.tenantId(), page.stream().map(DeviceRow::getId).toList())))
                if (relations.putIfAbsent(relation.deviceId(), relation) != null) fail("RELATION_AMBIGUOUS");
            var eligible = new ArrayList<DeviceRow>();
            for (var device : page) {
                var binding = bindings.get(device.getId());
                var relation = relations.get(device.getId());
                if (binding == null || relation != null && !SOURCE.equals(relation.sourceSystem())
                        || relation == null && (device.getProjectId() != null || device.getProjectAssignmentVersion() != 0)) {
                    protectedCount++;
                    continue;
                }
                if (!binding.sourceKey().equals(device.getSn())
                        || !binding.fingerprint().equals(P30DeviceBaseSyncTask.fingerprint(device))) fail("BASE_FINGERPRINT_CHANGED");
                if (relation != null && (relation.assignmentVersion() != device.getProjectAssignmentVersion()
                        || !Objects.equals(relation.effectiveTo() == null ? relation.projectId() : null, device.getProjectId())))
                    fail("RELATION_VERSION_CHANGED");
                eligible.add(device);
            }
            if (eligible.isEmpty()) { context.target().clearCache(); continue; }
            var decisions = decisions(new Resolve(context, eligible));
            var changes = new ArrayList<Change>();
            for (var device : eligible) {
                var next = decisions.get(device.getId());
                if (next == null) fail("DECISION_MISSING");
                var previous = relations.get(device.getId());
                if (next.projectId() == null) unresolved++;
                if (Objects.equals(device.getProjectId(), next.projectId())
                        && (next.projectId() == null || previous != null && Objects.equals(previous.evidence(), next.evidence()))) continue;
                if (managed == null) {
                    if (!permissions.hasAnyPermissions(context.actorId(), "pms:device:assign"))
                        throw new AccessDeniedException("DEVICE_PROJECT_ASSIGN_PERMISSION_REQUIRED");
                    var ids = projects.resolveAllCurrent(new ProjectAllScopeQuery(context.tenantId(), context.actorId(), ProjectScopeApi.ACTION_MANAGE));
                    managed = ids == null ? Set.of() : Set.copyOf(ids);
                }
                if (device.getProjectId() != null && !managed.contains(device.getProjectId())
                        || next.projectId() != null && !managed.contains(next.projectId()))
                    throw new AccessDeniedException("DEVICE_PROJECT_MANAGE_SCOPE_REQUIRED");
                if (previous != null && previous.effectiveTo() == null && !previous.effectiveFrom().isBefore(context.startedAt()))
                    fail("EFFECTIVE_TIME_CONFLICT");
                changes.add(new Change(device, bindings.get(device.getId()), previous, next,
                        context.preview() ? 0 : IdWorker.getId(), UUID.randomUUID().toString(), null));
            }
            if (!context.preview() && !changes.isEmpty()) write(new WriteBatch(context, changes));
            changed += changes.size();
            context.target().clearCache();
        }
        context.runState().warning("DEVICE_PROJECT_SYNC:changed=" + changed + ",protected=" + protectedCount + ",unresolved=" + unresolved);
    }

    private record Resolve(SyncContext<?> context, List<DeviceRow> devices) {}
    private Map<Long, Decision> decisions(Resolve request) {
        var c = request.context();
        var facts = mapper.selectFacts(new BatchQuery(c.tenantId(), c.connectionId(), request.devices(), c.startedAt()));
        var projectIds = new HashSet<Long>();
        for (var fact : facts) {
            if (fact.installationProjectId() != null) projectIds.add(fact.installationProjectId());
            if (fact.contractProjectId() != null) projectIds.add(fact.contractProjectId());
            if (fact.orderProjectId() != null) projectIds.add(fact.orderProjectId());
        }
        var valid = new HashSet<>(mapper.selectAssignableProjects(new ProjectQuery(c.tenantId(), List.copyOf(projectIds))));
        var result = new HashMap<Long, Decision>();
        for (var fact : facts) {
            if (fact.shipmentSource() != null && !"DPPMS".equals(fact.shipmentSource())) fail("LATEST_SHIPMENT_SOURCE_CHANGED");
            Decision decision = null;
            if (valid.contains(fact.installationProjectId())) decision = new Decision(fact.installationProjectId(), "INSTALLATION:" + fact.installationId());
            else if (fact.contractCount() > 1) decision = new Decision(null, "CONTRACT_CONFLICT");
            else if (valid.contains(fact.contractProjectId())) decision = new Decision(fact.contractProjectId(), "CONTRACT:" + fact.shipmentId() + ":" + fact.contractProjectId());
            else if (fact.orderCount() > 1) decision = new Decision(null, "ORDER_SCOPE_CONFLICT");
            else if (valid.contains(fact.orderProjectId())) decision = new Decision(fact.orderProjectId(), "SHIPMENT:" + fact.shipmentId() + ":" + fact.orderProjectId());
            if (decision != null) result.put(fact.deviceId(), decision);
        }
        var missing = request.devices().stream().filter(device -> !result.containsKey(device.getId())).toList();
        if (missing.isEmpty()) return result;
        List<Raw> raw;
        try (var source = c.source().open(c.connectionId())) {
            raw = source.mapper(SourceMapper.class).selectLatestRaw(new RawQuery(missing));
        }
        var keys = raw.stream().filter(Raw::clearTransfer).map(Raw::projectKey).filter(Objects::nonNull).distinct().toList();
        var aliases = new HashMap<String, Long>();
        for (var alias : mapper.selectAliases(new AliasQuery(c.tenantId(), c.connectionId(), keys))) {
            var previous = aliases.putIfAbsent(alias.sourceKey(), alias.projectId());
            if (previous != null && previous != alias.projectId()) fail("PROJECT_ALIAS_AMBIGUOUS");
        }
        valid = new HashSet<>(mapper.selectAssignableProjects(new ProjectQuery(c.tenantId(), List.copyOf(aliases.values()))));
        var rawByDevice = new HashMap<Long, Raw>();
        for (var row : raw) if (rawByDevice.putIfAbsent(row.deviceId(), row) != null) fail("RAW_SHIPMENT_AMBIGUOUS");
        for (var device : missing) {
            var row = rawByDevice.get(device.getId());
            var projectId = row == null || !row.clearTransfer() ? null : aliases.get(row.projectKey());
            result.put(device.getId(), valid.contains(projectId)
                    ? new Decision(projectId, "RAW_SHIPMENT:" + row.sourceId())
                    : new Decision(null, "NO_VALID_PROJECT"));
        }
        return result;
    }

    private void write(WriteBatch batch) {
        var c = batch.context();
        int closing = (int) batch.changes().stream().filter(change -> change.previous() != null && change.previous().effectiveTo() == null).count();
        if (closing > 0 && mapper.closeRelationships(batch) != closing) fail("RELATION_CLOSE_CONFLICT");
        var opening = batch.changes().stream().filter(change -> change.decision().projectId() != null).toList();
        if (!opening.isEmpty() && mapper.insertRelationships(new WriteBatch(c, opening)) != opening.size()) fail("RELATION_INSERT_CONFLICT");
        if (mapper.updateDevices(batch) != batch.changes().size()) fail("WRITE_VERSION_CONFLICT");
        var actual = new HashMap<Long, DeviceRow>();
        for (var row : mapper.selectDevicesByIds(new DeviceQuery(c.tenantId(), batch.changes().stream().map(change -> change.device().getId()).toList()))) actual.put(row.getId(), row);
        var completed = new ArrayList<Change>();
        for (var change : batch.changes()) {
            var row = actual.get(change.device().getId());
            if (row == null || row.getProjectAssignmentVersion() != change.device().getProjectAssignmentVersion() + 1
                    || !Objects.equals(row.getProjectId(), change.decision().projectId())) fail("PROJECTION_CHANGED");
            completed.add(new Change(change.device(), change.binding(), change.previous(), change.decision(),
                    change.relationshipId(), change.operationId(), P30DeviceBaseSyncTask.fingerprint(row)));
        }
        if (mapper.updateBindings(new WriteBatch(c, completed)) != completed.size()) fail("BINDING_VERSION_CONFLICT");
        writeProjections(batch);
        var pendingEvents = new ArrayList<AssignmentEvent>();
        for (var change : batch.changes()) {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("eventId", change.operationId());
            payload.put("deviceId", change.device().getId());
            payload.put("oldProjectId", change.device().getProjectId());
            payload.put("newProjectId", change.decision().projectId());
            payload.put("assignmentVersion", change.device().getProjectAssignmentVersion() + 1);
            payload.put("effectiveAt", c.startedAt());
            payload.put("operationId", change.operationId());
            payload.put("source", SOURCE);
            payload.put("evidence", change.decision().evidence());
            pendingEvents.add(new AssignmentEvent(IdWorker.getId(), change.operationId(), change.device().getId(), JsonUtils.toJsonString(payload)));
        }
        if (mapper.insertEvents(new EventBatch(c, pendingEvents)) != pendingEvents.size()) fail("EVENT_WRITE_CONFLICT");
    }
    /** Same tree, assignment-watermark and operation semantics as the AST consumer, in bulk. */
    private void writeProjections(WriteBatch batch) {
        var context=batch.context();
        var ids=batch.changes().stream().map(change->change.device().getId()).toList();
        var watermarks=new HashMap<Long,Long>();
        for(var watermark:mapper.selectProjectionWatermarks(new DeviceQuery(context.tenantId(),ids)))
            watermarks.put(watermark.deviceId(),watermark.assignmentVersion());
        var projects=batch.changes().stream().map(change->change.decision().projectId()).filter(Objects::nonNull).distinct().toList();
        var trees=new HashMap<Long,List<TreeFact>>();
        for(var fact:mapper.selectTreeFacts(new ProjectQuery(context.tenantId(),projects)))
            trees.computeIfAbsent(fact.projectId(),ignored->new ArrayList<>()).add(fact);
        var projections=new ArrayList<Projection>();
        var ancestors=new ArrayList<Ancestor>();
        for(var change:batch.changes()) {
            var device=change.device();
            if(watermarks.getOrDefault(device.getId(),-1L)>device.getProjectAssignmentVersion())
                fail("PROJECTION_WATERMARK_CHANGED");
            Long projectId=change.decision().projectId();
            var facts=projectId==null?List.<TreeFact>of():trees.get(projectId);
            if(facts==null || projectId!=null && facts.isEmpty()) fail("TREE_VERSION_UNAVAILABLE");
            long treeVersion=projectId==null?0: facts.getFirst().treeVersion();
            long assignmentVersion=device.getProjectAssignmentVersion()+1;
            projections.add(new Projection(device.getSn(),projectId==null?device.getProjectId():projectId,
                    treeVersion,assignmentVersion,change.operationId()));
            for(var fact:facts) {
                if(fact.treeVersion()!=treeVersion) fail("TREE_VERSION_AMBIGUOUS");
                if(fact.ancestorProjectId()!=null) ancestors.add(new Ancestor(device.getSn(),projectId,
                        fact.ancestorProjectId(),treeVersion,assignmentVersion));
            }
        }
        var projected=new ProjectionBatch(context,projections);
        mapper.deleteAncestors(projected);
        if(!ancestors.isEmpty() && mapper.insertAncestors(new AncestorBatch(context,ancestors))!=ancestors.size())
            fail("ANCESTOR_INSERT_CONFLICT");
        if(mapper.insertProjectionOperations(projected)!=projections.size()) fail("PROJECTION_OPERATION_CONFLICT");
    }
    private static void fail(String reason) { throw new IllegalStateException("DEVICE_PROJECT_SYNC_" + reason); }

    @Mapper public interface TargetMapper {
        List<DeviceRow> selectDevicesByIds(@Param("query") DeviceQuery query);
        List<DeviceRow> selectDevices(@Param("query") PageQuery query);
        List<Binding> selectBaseBindings(@Param("query") BatchQuery query);
        List<Relation> selectLatestRelationships(@Param("query") DeviceQuery query);
        List<Fact> selectFacts(@Param("query") BatchQuery query);
        List<Long> selectAssignableProjects(@Param("query") ProjectQuery query);
        List<Alias> selectAliases(@Param("query") AliasQuery query);
        int closeRelationships(WriteBatch batch);
        int insertRelationships(WriteBatch batch);
        int updateDevices(WriteBatch batch);
        int updateBindings(WriteBatch batch);
        int insertEvents(EventBatch batch);
        List<Watermark> selectProjectionWatermarks(@Param("query") DeviceQuery query);
        List<TreeFact> selectTreeFacts(@Param("query") ProjectQuery query);
        int deleteAncestors(ProjectionBatch batch);
        int insertAncestors(AncestorBatch batch);
        int insertProjectionOperations(ProjectionBatch batch);
    }
    public interface SourceMapper { List<Raw> selectLatestRaw(RawQuery query); }
}
