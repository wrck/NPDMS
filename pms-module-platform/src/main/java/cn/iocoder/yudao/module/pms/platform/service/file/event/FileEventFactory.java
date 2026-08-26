package cn.iocoder.yudao.module.pms.platform.service.file.event;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class FileEventFactory {

    public static final String VERSION_COMMITTED = "FileVersionCommitted";
    public static final String REFERENCE_ATTACHED = "FileReferenceAttached";
    public static final String REFERENCE_DETACHED = "FileReferenceDetached";
    public static final String FILE_ARCHIVED = "FileArchived";

    public PlatformCommandExecutionApi.BusinessEvent versionCommitted(
            Long tenantId, Long artifactId, Integer versionNo, String sha256,
            LocalDateTime occurredAt, String operationId) {
        String eventId = UUID.randomUUID().toString();
        var message = new FileVersionCommittedMessage(eventId, tenantId, artifactId, versionNo,
                sha256, "PASSED", occurredAt, operationId);
        return event(eventId, VERSION_COMMITTED, message);
    }

    public PlatformCommandExecutionApi.BusinessEvent referenceAttached(
            Long tenantId, Long referenceId, Long artifactId, Integer versionNo,
            String ownerContext, String objectType, String objectId, String purposeCode,
            LocalDateTime occurredAt, String operationId) {
        String eventId = UUID.randomUUID().toString();
        var message = new FileReferenceAttachedMessage(eventId, tenantId, referenceId, artifactId,
                versionNo, ownerContext, objectType, objectId, purposeCode, occurredAt, operationId);
        return event(eventId, REFERENCE_ATTACHED, message);
    }

    public PlatformCommandExecutionApi.BusinessEvent detached(FileReferenceDetachedMessage message) {
        return event(message.eventId(), REFERENCE_DETACHED, message);
    }

    public PlatformCommandExecutionApi.BusinessEvent archived(FileArchivedMessage message) {
        return event(message.eventId(), FILE_ARCHIVED, message);
    }

    private PlatformCommandExecutionApi.BusinessEvent event(String eventId, String eventType, Object message) {
        return new PlatformCommandExecutionApi.BusinessEvent(
                eventId, eventType, JsonUtils.toJsonString(message));
    }
}
