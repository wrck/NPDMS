package cn.iocoder.yudao.module.pms.integration.sync;

/** User-adjudicated account scope. Matching never trims, renames or infers employee identity. */
final class DppmsUserMigrationScope {
    private DppmsUserMigrationScope() {}
    static boolean includes(String username) {
        if (username == null || username.isBlank()) return false;
        return username.matches("[A-Za-z][0-9]+")
                || username.endsWith("_ss") && !username.substring(0, username.length() - 3).isBlank()
                || username.endsWith("_supp") && !username.substring(0, username.length() - 5).isBlank();
    }
    static boolean excludesAdjudicatedOrphan(cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row row) {
        if (!"t_user_info".equals(row.object()) || row.fields().get("user_id") != null) return false;
        String expected = switch (row.sourceKey()) {
            case "1000186" -> "test";
            case "1000188", "1000189" -> "admin";
            default -> null;
        };
        return expected != null && expected.equals(java.util.Objects.toString(row.fields().get("workNo"), null));
    }
}
