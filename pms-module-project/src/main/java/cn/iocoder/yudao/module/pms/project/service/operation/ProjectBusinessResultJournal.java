package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange.Channel;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.BusinessResultJournalMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.BusinessResultJournalMapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The channel lock, journal entry and Outbox record belong to the original Owner transaction. */
@Service
@RequiredArgsConstructor
public class ProjectBusinessResultJournal implements ProjectBusinessResultRecordingApi {
    private final BusinessResultJournalMapper mapper;
    private final ProjectBusinessResultSources sources;
    private final PlatformBusinessEventApi outbox;

    public record Boundary(Channel channel, long sequence) {
        public Boundary { Objects.requireNonNull(channel); if (sequence < 0) throw new IllegalArgumentException("RESULT_BOUNDARY_INVALID"); }
    }
    public record Page(Boundary through, long nextSequence, boolean complete, List<BusinessResultChange> changes) { }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void record(BusinessOperationResultEvent event) {
        Objects.requireNonNull(event, "Owner event").requireEnvelope(event.eventId(), TenantContextHolder.getRequiredTenantId());
        // Stable type ordering also fixes lock ordering when one Owner exposes more than one result kind.
        for (Type type : sources.changeTypes(event)) {
            var row = lockChannel(event.tenantId(), event.projectId(), type);
            Channel channel = channel(row);
            var previous = mapper.selectEvent(new EventLookup(event.tenantId(), channel.id(), event.eventId()));
            if (previous != null) {
                var stored = decode(previous, channel, previous.getSequenceNo());
                if (stored.sequence() > row.getCommittedSequence()) throw new IllegalStateException("RESULT_CHANGE_CORRUPT");
                // Compare the persisted event representation, including the platform's timestamp precision.
                if (!JsonUtils.parseTree(JsonUtils.toJsonString(stored.source())).equals(JsonUtils.parseTree(JsonUtils.toJsonString(event))))
                    throw new IllegalStateException("RESULT_EVENT_INTENT_CONFLICT");
                continue; // Do not query mutable Owner data again when replaying an already journaled event.
            }
            var observation = sources.inspect(sources.changeQuery(type, event));
            boolean formation = sources.declaresFormation(type, event);
            if (formation && (observation.result() == null || observation.result().validity() == Validity.REVOKED))
                throw new IllegalStateException("RESULT_FORMATION_UNVERIFIED");
            var result = observation.result();
            if (formation) {
                var prior = mapper.selectFormation(new FormationLookup(event.tenantId(), channel.id(), result.objectId(), result.resultId()));
                if (prior != null) {
                    var old = decode(prior, channel, prior.getSequenceNo());
                    if (!old.formation() || !old.observation().result().objectId().equals(result.objectId())
                            || !old.observation().result().resultId().equals(result.resultId()))
                        throw new IllegalStateException("RESULT_FORMATION_IDENTITY_MISMATCH");
                    formation = false;
                }
            }
            long next = Math.addExact(row.getCommittedSequence(), 1L);
            var change = new BusinessResultChange(UUID.randomUUID().toString(), 1, channel, next, event, observation, formation);
            var saved = new BusinessResultChangeDO();
            saved.setTenantId(event.tenantId()); saved.setChannelId(channel.id()); saved.setSequenceNo(next);
            saved.setSourceEventId(event.eventId()); saved.setNotificationId(change.eventId());
            saved.setObjectId(result == null ? null : result.objectId()); saved.setResultId(result == null ? null : result.resultId());
            saved.setFormationMarker(formation ? 1 : null); saved.setPayload(JsonUtils.toJsonString(change));
            if (mapper.advanceSequence(new Advance(event.tenantId(), channel.id(), row.getCommittedSequence(), next)) != 1
                    || mapper.insertChange(saved) != 1) throw new IllegalStateException("RESULT_JOURNAL_WRITE_CONFLICT");
            outbox.append("BusinessResultChannel", channel.id().toString(),
                    new BusinessEvent(change.eventId(), BusinessResultChange.EVENT_TYPE, saved.getPayload()));
        }
    }

    /** Caller retains its normal project/round locks and persists the boundary in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Boundary capture(Long tenantId, Long projectId, Type type) {
        if (!sources.changeSupported(type)) throw new IllegalArgumentException("RESULT_CHANGE_SOURCE_UNAVAILABLE");
        var row = lockChannel(tenantId, projectId, type);
        return new Boundary(channel(row), row.getCommittedSequence());
    }

    /** The upper bound remains fixed across pages; a missing sequence is a gap, never an empty successful scan. */
    @Transactional(readOnly = true)
    public Page read(Boundary through, long afterSequence, int limit) {
        if (through == null || afterSequence < 0 || afterSequence > through.sequence() || limit < 1 || limit > 200)
            throw new IllegalArgumentException("RESULT_CHANGE_PAGE_INVALID");
        var channel = through.channel();
        var row = mapper.selectChannel(scope(channel.tenantId(), channel.projectId(), channel.type()));
        requireChannel(row, channel.tenantId(), channel.projectId(), channel.type());
        if (!channel.equals(channel(row)) || row.getCommittedSequence() < through.sequence())
            throw new IllegalStateException("RESULT_BOUNDARY_CHANGED");
        int expected = (int) Math.min((long) limit, through.sequence() - afterSequence);
        if (expected == 0) return new Page(through, afterSequence, true, List.of());
        var rows = mapper.selectChanges(new ChangePage(channel.tenantId(), channel.id(), afterSequence, through.sequence(), limit));
        if (rows == null || rows.size() != expected) throw new IllegalStateException("RESULT_CHANGE_GAP");
        var changes = new ArrayList<BusinessResultChange>();
        long next = afterSequence;
        for (var entry : rows) changes.add(decode(entry, channel, ++next));
        return new Page(through, next, next == through.sequence(), List.copyOf(changes));
    }

    private BusinessResultChannelDO lockChannel(Long tenant, Long project, Type type) {
        var scope = scope(tenant, project, type);
        mapper.ensureChannel(scope);
        var row = mapper.selectChannelForUpdate(scope);
        requireChannel(row, tenant, project, type);
        return row;
    }
    private ChannelScope scope(Long tenant, Long project, Type type) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || project == null || project <= 0 || type == null)
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        return new ChannelScope(tenant, project, type.ownerContext(), type.entityType(), type.resultType());
    }
    private void requireChannel(BusinessResultChannelDO row, Long tenant, Long project, Type type) {
        if (row == null || row.getCommittedSequence() == null || row.getCommittedSequence() < 0
                || !Objects.equals(tenant, row.getTenantId()) || !Objects.equals(project, row.getProjectId())
                || !Objects.equals(type.ownerContext(), row.getOwnerContext()) || !Objects.equals(type.entityType(), row.getEntityType())
                || !Objects.equals(type.resultType(), row.getResultType())) throw new IllegalStateException("RESULT_CHANNEL_MISMATCH");
    }
    private Channel channel(BusinessResultChannelDO row) {
        return new Channel(row.getId(), row.getTenantId(), row.getProjectId(), new Type(row.getOwnerContext(), row.getEntityType(), row.getResultType()));
    }
    private BusinessResultChange decode(BusinessResultChangeDO row, Channel channel, Long sequence) {
        if (row == null || sequence == null || sequence <= 0 || !sequence.equals(row.getSequenceNo())
                || !channel.id().equals(row.getChannelId()) || !channel.tenantId().equals(row.getTenantId()))
            throw new IllegalStateException("RESULT_CHANGE_GAP");
        var document = JsonUtils.parseTree(row.getPayload());
        if (document == null || !document.isObject() || !document.path("eventVersion").isIntegralNumber()
                || !"1".equals(document.path("eventVersion").asText()) || !document.path("sequence").isIntegralNumber()
                || !document.path("formation").isBoolean()) throw new IllegalStateException("RESULT_CHANGE_FORMAT_UNSUPPORTED");
        var change = JsonUtils.parseObject(row.getPayload(), BusinessResultChange.class);
        if (change == null || !channel.equals(change.channel()) || sequence != change.sequence()
                || !Objects.equals(row.getNotificationId(), change.eventId()) || !Objects.equals(row.getSourceEventId(), change.source().eventId())
                || !Objects.equals(row.getFormationMarker(), change.formation() ? 1 : null)
                || !Objects.equals(row.getObjectId(), change.observation().result() == null ? null : change.observation().result().objectId())
                || !Objects.equals(row.getResultId(), change.observation().result() == null ? null : change.observation().result().resultId()))
            throw new IllegalStateException("RESULT_CHANGE_CORRUPT");
        return change;
    }
}
