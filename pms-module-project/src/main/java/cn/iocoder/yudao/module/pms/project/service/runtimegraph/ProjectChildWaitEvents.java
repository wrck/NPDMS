package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Producers append in the closure/reopen transaction; ancestor reads and retries happen after commit. */
@Component
@RequiredArgsConstructor
public class ProjectChildWaitEvents {
    public static final String EVENT_TYPE = "ProjectChildClosureChanged";
    public record Changed(String eventId, Long tenantId, Long projectId, Long actorId, String correlationId) { }
    private final PlatformBusinessEventApi events;

    public void closureChanged(Long tenantId, Long projectId, Long actorId, String correlationId) {
        var changed = new Changed(UUID.randomUUID().toString(), tenantId, projectId, actorId, correlationId);
        events.append("Project", projectId.toString(), new BusinessEvent(changed.eventId(), EVENT_TYPE, JsonUtils.toJsonString(changed)));
    }
}
