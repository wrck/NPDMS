package cn.iocoder.yudao.module.pms.integration.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionGovernanceGuardProviderTest {

    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 16, 0);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldFailClosedWhenEndpointIsUnconfiguredOrContractUnverified() {
        CollectionGovernanceGuardProperties properties = new CollectionGovernanceGuardProperties();
        ProjectGovernanceProviderFact unconfigured = new CollectionGovernanceGuardProvider(properties)
                .inspect(query(Set.of(11L)));
        properties.setBaseUrl("https://collection.invalid");
        ProjectGovernanceProviderFact unverified = new CollectionGovernanceGuardProvider(properties)
                .inspect(query(Set.of(11L)));

        assertEquals("ENDPOINT_UNCONFIGURED", unconfigured.watermark());
        assertEquals("ENDPOINT_CONTRACT_UNVERIFIED", unverified.watermark());
        assertEquals("PROVIDER_UNAVAILABLE", unconfigured.blockers().getFirst().code());
        assertEquals("PROVIDER_UNAVAILABLE", unverified.blockers().getFirst().code());
    }

    @Test
    void shouldReturnEmptyForNoCandidatesAndRejectCrossTenant() {
        CollectionGovernanceGuardProvider provider = new CollectionGovernanceGuardProvider(
                new CollectionGovernanceGuardProperties());
        ProjectGovernanceProviderFact empty = provider.inspect(query(Set.of()));

        assertEquals("EMPTY", empty.watermark());
        assertTrue(empty.blockers().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> provider.inspect(
                new ProjectGovernanceGuardQuery(8L, Set.of(11L), "ROLLBACK", CHECKED_AT)));
    }

    private static ProjectGovernanceGuardQuery query(Set<Long> projectIds) {
        return new ProjectGovernanceGuardQuery(7L, projectIds, "ROLLBACK", CHECKED_AT);
    }
}
