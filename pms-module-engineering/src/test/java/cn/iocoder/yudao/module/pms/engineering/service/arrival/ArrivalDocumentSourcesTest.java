package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalDocumentSourcesTest {

    private static final Long TENANT = 1L;

    @Mock private ArrivalMapper arrivalMapper;
    @InjectMocks private ArrivalDocumentSources sources;

    @BeforeEach
    void holdTenant() {
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void releaseTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void declaresTheArrivalSignDocumentSource() {
        var descriptors = sources.descriptors();

        assertEquals(1, descriptors.size());
        assertEquals(ArrivalDocumentSources.SOURCE_CODE, descriptors.getFirst().code());
        assertTrue(descriptors.getFirst().code().startsWith("IMP."));
    }

    @Test
    void resolvesTheArrivalProjectForTheSignDocumentPurpose() {
        ArrivalDO arrival = new ArrivalDO();
        arrival.setId(7L);
        arrival.setTenantId(TENANT);
        arrival.setProjectId(66L);
        when(arrivalMapper.selectById(7L)).thenReturn(arrival);

        var scope = sources.resolve(TENANT, new FileEvidenceApi.Document(null, "IMP", "ARRIVAL", "7", "ARRIVAL_SIGN_DOCUMENT", null, null, null, null, null, false));

        assertEquals(new FileDocumentSourceProvider.Scope(66L, ArrivalDocumentSources.SOURCE_CODE,"IMP","arrival",7L,null),
                scope);
    }

    @Test
    void ignoresForeignOwnersTypesAndPurposes() {
        assertNull(sources.resolve(TENANT, new FileEvidenceApi.Document(null, "SOL", "ARRIVAL", "7", "ARRIVAL_SIGN_DOCUMENT", null, null, null, null, null, false)));
        assertNull(sources.resolve(TENANT, new FileEvidenceApi.Document(null, "IMP", "ARRIVAL_ACCEPTANCE", "7", "ARRIVAL_SIGN_DOCUMENT", null, null, null, null, null, false)));
        assertNull(sources.resolve(TENANT, new FileEvidenceApi.Document(null, "IMP", "ARRIVAL", "7", "RECEIPT", null, null, null, null, null, false)));
    }

    @Test
    void ignoresForeignTenantsAndMissingRecords() {
        assertNull(sources.resolve(999L, new FileEvidenceApi.Document(null, "IMP", "ARRIVAL", "7", "ARRIVAL_SIGN_DOCUMENT", null, null, null, null, null, false)));

        when(arrivalMapper.selectById(7L)).thenReturn(null);
        assertNull(sources.resolve(TENANT, new FileEvidenceApi.Document(null, "IMP", "ARRIVAL", "7", "ARRIVAL_SIGN_DOCUMENT", null, null, null, null, null, false)));
    }

    @Test
    void ignoresNonNumericObjectIds() {
        assertNull(sources.resolve(TENANT, new FileEvidenceApi.Document(null, "IMP", "ARRIVAL", "not-a-number", "ARRIVAL_SIGN_DOCUMENT", null, null, null, null, null, false)));
    }
}
