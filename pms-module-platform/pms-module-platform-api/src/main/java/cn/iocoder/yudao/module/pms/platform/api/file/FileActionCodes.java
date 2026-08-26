package cn.iocoder.yudao.module.pms.platform.api.file;

import java.util.Set;

public final class FileActionCodes {

    public static final String UPLOAD = "UPLOAD";
    public static final String REFERENCE = "REFERENCE";
    public static final String READ = "READ";
    public static final String DOWNLOAD = "DOWNLOAD";
    public static final String PREVIEW = "PREVIEW";
    public static final String REPLACE = "REPLACE";
    public static final String DETACH = "DETACH";
    public static final String ARCHIVE = "ARCHIVE";
    public static final String INVALIDATE = "INVALIDATE";

    public static final Set<String> SUPPORTED_ACTIONS = Set.of(
            UPLOAD, REFERENCE, READ, DOWNLOAD, PREVIEW,
            REPLACE, DETACH, ARCHIVE, INVALIDATE);

    private FileActionCodes() {
    }

    public static String requireSupported(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("file action is required");
        }
        String normalized = action.trim();
        if (!SUPPORTED_ACTIONS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported file action");
        }
        return normalized;
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
