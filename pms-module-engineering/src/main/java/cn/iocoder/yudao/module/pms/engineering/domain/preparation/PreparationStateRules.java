package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import java.util.Collection;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_STATUS_INVALID;

public final class PreparationStateRules {

    private static final Set<String> PREPARATION_STATES = Set.of(
            "DRAFT", "PENDING_CONFIRMATION", "CONFIRMED", "RETURNED");
    private static final Set<String> APPLICABILITY_STATES = Set.of(
            "REQUIRED", "NOT_APPLICABLE_PENDING", "NOT_APPLICABLE_CONFIRMED");
    private static final Set<String> CONFIRMATION_STATES = Set.of("PENDING", "CONFIRMED", "RETURNED");

    private PreparationStateRules() {
    }

    public static void requirePreparationTransition(String from, String to) {
        requireKnown(from, PREPARATION_STATES);
        requireKnown(to, PREPARATION_STATES);
        boolean allowed = ("DRAFT".equals(from) && "PENDING_CONFIRMATION".equals(to))
                || ("PENDING_CONFIRMATION".equals(from) && ("CONFIRMED".equals(to) || "RETURNED".equals(to)))
                || ("CONFIRMED".equals(from) && "RETURNED".equals(to));
        if (!allowed) throw exception(PREPARATION_STATUS_INVALID);
    }

    public static void requireItemConfirmationTransition(String preparationStatus, String applicability,
                                                         String fromConfirmation, String toConfirmation) {
        requireKnown(preparationStatus, PREPARATION_STATES);
        requireKnown(applicability, APPLICABILITY_STATES);
        requireKnown(fromConfirmation, CONFIRMATION_STATES);
        requireKnown(toConfirmation, CONFIRMATION_STATES);
        boolean preparationAllowsDecision = "PENDING_CONFIRMATION".equals(preparationStatus)
                || "CONFIRMED".equals(preparationStatus);
        boolean transitionAllowed = ("PENDING".equals(fromConfirmation) && "CONFIRMED".equals(toConfirmation))
                || (("PENDING".equals(fromConfirmation) || "CONFIRMED".equals(fromConfirmation))
                    && "RETURNED".equals(toConfirmation));
        if (!preparationAllowsDecision || !transitionAllowed) throw exception(PREPARATION_STATUS_INVALID);
    }

    public static void requireApplicabilityTransition(String preparationStatus, String from, String to) {
        requireKnown(preparationStatus, PREPARATION_STATES);
        requireKnown(from, APPLICABILITY_STATES);
        requireKnown(to, APPLICABILITY_STATES);
        boolean allowed = "DRAFT".equals(preparationStatus)
                && (("REQUIRED".equals(from) && "NOT_APPLICABLE_PENDING".equals(to))
                    || ("NOT_APPLICABLE_PENDING".equals(from) && "REQUIRED".equals(to)))
                || "PENDING_CONFIRMATION".equals(preparationStatus)
                && "NOT_APPLICABLE_PENDING".equals(from) && "NOT_APPLICABLE_CONFIRMED".equals(to);
        if (!allowed) throw exception(PREPARATION_STATUS_INVALID);
    }

    public static boolean allItemsConfirmed(Collection<ItemState> items) {
        if (items == null || items.isEmpty()) return false;
        return items.stream().allMatch(item -> {
            requireKnown(item.applicabilityCode(), APPLICABILITY_STATES);
            requireKnown(item.confirmationStatusCode(), CONFIRMATION_STATES);
            if (!"CONFIRMED".equals(item.confirmationStatusCode())) return false;
            return "REQUIRED".equals(item.applicabilityCode())
                    || "NOT_APPLICABLE_CONFIRMED".equals(item.applicabilityCode());
        });
    }

    private static void requireKnown(String value, Set<String> allowed) {
        if (value == null || !allowed.contains(value)) throw exception(PREPARATION_STATUS_INVALID);
    }

    public record ItemState(String applicabilityCode, String confirmationStatusCode) {
    }
}
