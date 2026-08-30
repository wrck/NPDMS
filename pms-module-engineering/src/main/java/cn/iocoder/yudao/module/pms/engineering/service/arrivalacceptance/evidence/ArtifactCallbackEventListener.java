package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactAcceptedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactArchivedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArtifactCallbackEventListener {

    private final ArtifactCallbackHandler handler;

    @EventListener
    public void onArtifactAccepted(ArtifactAcceptedMessage message) {
        handler.handle(message);
    }

    @EventListener
    public void onArtifactArchived(ArtifactArchivedMessage message) {
        handler.handle(message);
    }
}
