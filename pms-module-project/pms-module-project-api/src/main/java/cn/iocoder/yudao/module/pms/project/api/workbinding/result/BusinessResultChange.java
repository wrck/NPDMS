package cn.iocoder.yudao.module.pms.project.api.workbinding.result;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;

import java.util.Objects;
import java.util.UUID;

/** Durable notification of an Owner observation; consumers revalidate current validity before adoption. */
public record BusinessResultChange(String eventId, int eventVersion, Channel channel, long sequence,
                                   BusinessOperationResultEvent source, Observation observation, boolean formation) {
    public static final String EVENT_TYPE = "PMS.BusinessResultChangeCommitted.v1";

    public record Channel(Long id, Long tenantId, Long projectId, Type type) {
        public Channel {
            if (id == null || id <= 0 || tenantId == null || tenantId < 0 || projectId == null || projectId <= 0)
                throw new IllegalArgumentException("RESULT_CHANNEL_INVALID");
            Objects.requireNonNull(type, "result type");
        }
    }

    public BusinessResultChange {
        UUID.fromString(eventId);
        Objects.requireNonNull(channel, "result channel");
        Objects.requireNonNull(source, "source event");
        Objects.requireNonNull(observation, "Owner observation");
        if (eventVersion != 1 || sequence <= 0 || !Objects.equals(channel.tenantId(), source.tenantId())
                || !Objects.equals(channel.projectId(), source.projectId())
                || !channel.type().ownerContext().equals(source.ownerContext())
                || !channel.type().entityType().equals(source.objectType()))
            throw new IllegalArgumentException("RESULT_CHANGE_SCOPE_INVALID");
        var result = observation.result();
        if (result != null && (!Objects.equals(channel.tenantId(), result.tenantId())
                || !Objects.equals(channel.projectId(), result.projectId()) || !channel.type().equals(result.type())))
            throw new IllegalArgumentException("RESULT_CHANGE_IDENTITY_INVALID");
        if (formation && (result == null || result.validity() == Validity.REVOKED))
            throw new IllegalArgumentException("RESULT_FORMATION_INVALID");
    }
}
