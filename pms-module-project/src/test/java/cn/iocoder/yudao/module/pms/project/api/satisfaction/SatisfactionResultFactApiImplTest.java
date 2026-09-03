package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionResultFactApiImplTest {
    @Mock private SatisfactionResultMapper resultMapper;
    private SatisfactionResultFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        api = new SatisfactionResultFactApiImpl(resultMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsExactImmutableOwnerFact() {
        when(resultMapper.selectFact(any())).thenReturn(record(3));
        var fact = api.inspect(new SatisfactionResultFactQuery(7L, 50L, 3));
        assertEquals("FOUND", fact.outcome());
        assertEquals("SAT-40", fact.collectionKey());
        assertEquals(50L, fact.resultId());
        assertEquals("EFFECTIVE", fact.resultStatus());
    }

    @Test
    void rejectsMissingAndVersionConflictWithoutInventingFact() {
        assertEquals("NOT_FOUND", api.inspect(new SatisfactionResultFactQuery(7L, 50L, 3)).outcome());
        when(resultMapper.selectFactForUpdate(any())).thenReturn(record(4));
        assertEquals("VERSION_CONFLICT",
                api.lockAndRevalidate(new SatisfactionResultFactQuery(7L, 50L, 3)).outcome());
    }

    private SatisfactionResultFactRecord record(int version) {
        return new SatisfactionResultFactRecord(7L, "SAT-40", 40L, 1, 41L, 42L, 50L, 1,
                30L, "RULE-V1", new BigDecimal("80.00"), "ACC", "AcceptanceActivityCompletionFact",
                "20", 2L, true, "EFFECTIVE", "PENDING_COMPENSATION", version);
    }
}
