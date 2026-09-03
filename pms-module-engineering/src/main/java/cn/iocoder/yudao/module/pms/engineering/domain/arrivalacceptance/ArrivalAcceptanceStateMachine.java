package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import java.util.Set;

/** EXE-01到货批次状态机；项目完成事实不复用批次状态。 */
public final class ArrivalAcceptanceStateMachine {

    public static final String DRAFT = "DRAFT";
    public static final String PARTIALLY_ACCEPTED = "PARTIALLY_ACCEPTED";
    public static final String DIFFERENCE_PENDING = "DIFFERENCE_PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String CONFIRMED = "CONFIRMED";

    private static final Set<String> CONFIRMABLE = Set.of(PARTIALLY_ACCEPTED, ACCEPTED);

    public String submit(boolean hasOpenDifference, boolean projectScopeSatisfied) {
        if (hasOpenDifference) {
            return DIFFERENCE_PENDING;
        }
        return projectScopeSatisfied ? ACCEPTED : PARTIALLY_ACCEPTED;
    }

    public String afterDifferenceResolution(boolean allDifferencesResolved,
                                            boolean projectScopeSatisfied) {
        if (!allDifferencesResolved) {
            return DIFFERENCE_PENDING;
        }
        return projectScopeSatisfied ? ACCEPTED : PARTIALLY_ACCEPTED;
    }

    public String confirm(String currentStatus) {
        if (!CONFIRMABLE.contains(currentStatus)) {
            throw new IllegalStateException("arrival batch is not confirmable");
        }
        return CONFIRMED;
    }
}
