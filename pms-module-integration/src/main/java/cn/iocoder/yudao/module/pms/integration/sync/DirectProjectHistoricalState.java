package cn.iocoder.yudao.module.pms.integration.sync;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Adjudicated DPPMS baseline initialization, used only by the project migration Owner.
 * Ordinary closure, stage advancement and reopen continue through their existing commands.
 * This policy neither completes runtime stages/tasks nor manufactures approval/exit facts.
 */
public final class DirectProjectHistoricalState {
    private DirectProjectHistoricalState() {}

    public record State(String lifecycleStatus, String currentStage, LocalDateTime closedAt) {}

    public static State resolve(String sourceState, LocalDateTime sourceClosedAt, State current,
                                boolean previouslyInitialized) {
        return resolve("10",sourceState,sourceClosedAt,current,previouslyInitialized);
    }

    public static State resolve(String projectType,String sourceState,LocalDateTime sourceClosedAt,State current,
                                boolean previouslyInitialized) {
        if (current == null || current.currentStage() == null
                || !Set.of("S0", "S1", "S2", "S3", "S4", "S5", "S6").contains(current.currentStage())) {
            throw new IllegalArgumentException("DPPMS_PROJECT_CURRENT_STATE_INVALID");
        }
        if (!Set.of("10","afss","afxx").contains(projectType==null?"":projectType)) {
            throw new IllegalArgumentException("DPPMS_PROJECT_TYPE_UNADJUDICATED");
        }
        State target = "10".equals(projectType) ? switch (sourceState == null ? "" : sourceState) {
            // Adjudicated whole-table exception: a missing original date stays unknown.
            // This migration policy does not change the normal business closure command.
            case "100" -> new State("NORMAL_CLOSED", "S6", sourceClosedAt);
            case "20" -> new State("NO_TRACKING_CLOSED", current.currentStage(), sourceClosedAt);
            case "30" -> new State("ACTIVE", "S0", null);
            // These source states describe personnel assignment, not a lifecycle transition.
            case "31", "32" -> {
                yield new State("ACTIVE", current.currentStage(), null);
            }
            default -> throw new IllegalArgumentException("DPPMS_PROJECT_SOURCE_STATE_UNADJUDICATED");
        } : switch (sourceState == null ? "" : sourceState) {
            case "10", "31" -> new State("ACTIVE","S0",null);
            case "32" -> new State("ACTIVE","S4",null);
            case "40" -> new State("ACTIVE","S5",null);
            case "50" -> new State("ACTIVE","S6",null);
            case "100" -> new State("NORMAL_CLOSED","S6",sourceClosedAt);
            default -> throw new IllegalArgumentException("DPPMS_PROJECT_SOURCE_STATE_UNADJUDICATED");
        };
        if (previouslyInitialized) {
            if (!Objects.equals(current, target)) {
                throw new IllegalArgumentException("DPPMS_PROJECT_HISTORICAL_STATE_CHANGED");
            }
        } else if (!"ACTIVE".equals(current.lifecycleStatus()) || current.closedAt() != null) {
            throw new IllegalArgumentException("DPPMS_PROJECT_CURRENT_LIFECYCLE_PROTECTED");
        }
        return target;
    }

}
