package cn.iocoder.yudao.server.sync;

/** Explicit review after comparing equivalent work; no arbitrary elapsed-time cutoff. */
final class InitialMigrationReview {
    enum Decision { CONTINUE, STOP }

    static Decision decision(String text) {
        if (text == null) throw new IllegalArgumentException("Missing performance review");
        return switch (text.strip()) {
            case "CONTINUE_INITIAL_VERIFICATION" -> Decision.CONTINUE;
            case "STOP_PERFORMANCE" -> Decision.STOP;
            default -> throw new IllegalArgumentException("Unknown performance review; do not continue");
        };
    }
}
