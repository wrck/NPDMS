package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactAcceptedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactArchivedMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ArtifactCallbackEventListenerTest {

    @Test
    void synchronouslyForwardsBothPublicCallbackTypes() {
        ArtifactCallbackHandler handler = mock(ArtifactCallbackHandler.class);
        ArtifactCallbackEventListener listener = new ArtifactCallbackEventListener(handler);
        ArtifactAcceptedMessage accepted = new ArtifactAcceptedMessage(
                "evt-a", 7L, 50L, 1, 40L, 5, "review-1",
                LocalDateTime.of(2026, 8, 30, 2, 0), "corr-a");
        ArtifactArchivedMessage archived = new ArtifactArchivedMessage(
                "evt-r", 7L, 50L, 1, 40L, 5, "archive-1",
                LocalDateTime.of(2026, 8, 30, 2, 1), "corr-r");

        listener.onArtifactAccepted(accepted);
        listener.onArtifactArchived(archived);

        verify(handler).handle(accepted);
        verify(handler).handle(archived);
    }
}
