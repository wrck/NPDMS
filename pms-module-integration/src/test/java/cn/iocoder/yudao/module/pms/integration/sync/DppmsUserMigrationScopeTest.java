package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsUserMigrationScopeTest {
    @Test void explicitlyApprovedPatternsIncludeBothEnabledAndDisabledSourceIdentities() {
        assertTrue(DppmsUserMigrationScope.includes("c02791"));
        assertTrue(DppmsUserMigrationScope.includes("X00591"));
        assertTrue(DppmsUserMigrationScope.includes("xialangguo_ss"));
        assertTrue(DppmsUserMigrationScope.includes("zhaoyangxi_supp"));
    }
    @Test void excludedAccountsAndUnapprovedRenamingCannotEnterThroughNormalization() {
        for (String name : new String[]{"admin", "sse", "test", "pms_tbpublic", "白凯", "test_supp1", "xialangguo_ss\u00a0", " c02791", "xialangguo_ss "})
            assertFalse(DppmsUserMigrationScope.includes(name),name);
        assertFalse(DppmsUserMigrationScope.includes(null)); assertFalse(DppmsUserMigrationScope.includes(""));
    }
}
