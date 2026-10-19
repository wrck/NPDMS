package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.system.api.migration.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectAccountUsersPlanTest {
    private static final LocalDateTime HISTORY = LocalDateTime.of(2015, 3, 24, 11, 22);
    static Profile profile(String username, Integer status) {
        return new Profile(username, "原姓名", null, null, Set.of(), null, null, 1, null, status, "02791",
                JsonUtils.parseTree("{\"customInfo\":{\"optional\":null},\"legacyUserStatus\":2}"));
    }
    static Entry entry(String table, String key, Profile profile, Long target, User previous, boolean reference, Long adoption) {
        return new Entry(table, key, profile, new SourceAudit(null, HISTORY, null, HISTORY), target, previous, reference, adoption);
    }
    static Request request(List<Entry> entries, boolean adoption) { return new Request(1L, "integration:1:12", entries, adoption); }
    private List<Result> plan(List<Entry> entries, List<User> current, boolean adopt) {
        return DirectAccountUsers.plan(request(entries, adopt), current, false);
    }
    @Test void aliasesShareCanonicalBusinessAccountAndDoNotReuseOldPrimaryKeys() {
        Profile profile = profile("c00733", 1);
        var results = plan(List.of(entry("fnd_user_info", "233", profile, null, null, true, null),
                entry("fnd_user_info", "408", profile, null, null, false, null)), List.of(), false);
        assertEquals(2, results.size()); assertNull(results.getFirst().after().id());
        assertEquals("CREATED", results.getFirst().action()); assertEquals(1, results.getLast().after().profile().status());
        assertEquals(results.getFirst().after(), results.getLast().after());
    }
    @Test void sameNameIsNotSufficientToAdoptCurrentAccount() {
        Profile profile = profile("admin", 0);
        User current = new User(1L, profile, new Audit(null, HISTORY, null, HISTORY));
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "0", profile, null, null, false, null)), List.of(current), true).getFirst().action());
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "0", profile, null, null, false, 104L)), List.of(current), true).getFirst().action());
        assertEquals("ADOPTED", plan(List.of(entry("t_user", "0", profile, null, null, false, 1L)), List.of(current), true).getFirst().action());
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "0", profile, null, null, false, 1L)), List.of(current), false).getFirst().action());
    }
    @Test void deletedOrChangedBindingDoesNotRecreateOrReassign() {
        Profile profile = profile("c02791", 0);
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "12", profile, 9L, null, false, null)), List.of(), false).getFirst().action());
        User other = new User(19L, profile, new Audit(null, HISTORY, null, HISTORY));
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "12", profile, 9L, other, false, null)), List.of(other), false).getFirst().action());
    }
    @Test void manualChangesBlockAndIdenticalReplayDoesNotTouchHistory() {
        Profile profile = profile("c02791", 1);
        User current = new User(9L, profile, new Audit(null, HISTORY, null, HISTORY));
        assertEquals("UNCHANGED", plan(List.of(entry("t_user", "12", profile, 9L, current, false, null)), List.of(current), false).getFirst().action());
        User manuallyChanged = new User(9L, profile("c02791", 0), current.audit());
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "12", profile, 9L, current, false, null)), List.of(manuallyChanged), false).getFirst().action());
    }
    @Test void forwardAndSelfAuditReferencesResolveThroughSourceIdentityKeys() {
        Profile first = profile("c02791", 0), second = profile("admin", 0);
        User admin = new User(1L, second, new Audit(null, HISTORY, null, HISTORY));
        var user = new Entry("t_user", "12", first, new SourceAudit("fnd_user_info:81", HISTORY, "t_user:12", HISTORY), null, null, false, null);
        var result = plan(List.of(user, entry("fnd_user_info", "81", second, null, null, false, 1L)), List.of(admin), true).getFirst();
        assertEquals("1", result.after().audit().creator()); assertNull(result.after().audit().updater());
        var allocated = DirectAccountUsers.plan(request(List.of(user, entry("fnd_user_info", "81", second, null, null, false, 1L)), true), List.of(admin), true);
        assertEquals(allocated.getFirst().after().id().toString(), allocated.getFirst().after().audit().updater());
        var missing = new Entry("t_user", "12", first, new SourceAudit("fnd_user_info:9999", HISTORY, null, HISTORY), null, null, false, null);
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(missing), List.of(), false));
    }
    @Test void approvedSharedEmailsRemainFormalFieldsOnDistinctAccounts() {
        Profile first = withEmail(profile("c02791", 0), "same@example.invalid");
        Profile second = withEmail(profile("c00733", 1), "same@example.invalid");
        var results = plan(List.of(entry("t_user", "12", first, null, null, false, null),
                entry("t_user", "13", second, null, null, false, null)), List.of(), false);
        assertTrue(results.stream().allMatch(result -> "CREATED".equals(result.action())));
        assertTrue(results.stream().allMatch(result -> "same@example.invalid".equals(result.after().profile().email())));
    }
    @Test void twoPrimariesCaseDifferencesAndMissingHistoricalTimesAreRejected() {
        Profile profile = profile("c02791", 0);
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(entry("t_user", "12", profile, null, null, false, null),
                entry("fnd_user_info", "13", profile, null, null, false, null)), List.of(), false));
        User current = new User(9L, profile("C02791", 0), new Audit(null, HISTORY, null, HISTORY));
        assertEquals("CONFLICT", plan(List.of(entry("t_user", "12", profile, 9L, current, false, null)), List.of(current), false).getFirst().action());
        var missing = new Entry("t_user", "12", profile, new SourceAudit(null, HISTORY, null, null), null, null, false, null);
        assertEquals("CONFLICT", plan(List.of(missing), List.of(), false).getFirst().action());
    }
    private static Profile withEmail(Profile value, String email) {
        return new Profile(value.username(), value.nickname(), value.remark(), value.departmentId(), value.postIds(), email,
                value.mobile(), value.sex(), value.avatar(), value.status(), value.employeeNo(), value.customInfo());
    }
}
