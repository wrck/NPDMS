package com.dp.deviceops.core.terminal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalPagerControllerTest {

    @Test
    void continuesAnEvidenceBackedPagerWhenLaterPromptsAreOnlyRepainted() {
        AtomicLong clock = new AtomicLong();
        TerminalPagerController controller =
                new TerminalPagerController(Duration.ofMillis(250), 10, clock::get);

        controller.observe(decision(false, false, 0));
        clock.set(Duration.ofSeconds(1).toNanos());
        assertFalse(controller.continuationReady());

        controller.observe(decision(true, false, 1));
        clock.addAndGet(Duration.ofMillis(249).toNanos());
        assertFalse(controller.continuationReady());
        clock.addAndGet(Duration.ofMillis(1).toNanos());
        assertTrue(controller.continuationReady());
        controller.markContinuationSent();

        controller.observe(decision(false, false, 1));
        clock.addAndGet(Duration.ofMillis(250).toNanos());
        assertTrue(controller.continuationReady());
        controller.markContinuationSent();

        controller.observe(decision(false, true, 1));
        clock.addAndGet(Duration.ofSeconds(1).toNanos());
        assertFalse(controller.continuationReady());
    }

    private static TerminalTextProcessor.Decision decision(
            boolean sendContinue, boolean promptReached, int pageCount) {
        return new TerminalTextProcessor.Decision("", sendContinue, promptReached, pageCount);
    }
}
