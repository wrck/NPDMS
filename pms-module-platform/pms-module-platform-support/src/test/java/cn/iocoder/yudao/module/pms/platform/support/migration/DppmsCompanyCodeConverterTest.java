package cn.iocoder.yudao.module.pms.platform.support.migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsCompanyCodeConverterTest {
    @Test void onlyTheApprovedSourceCodesCanBecomeCompanyIdentity() {
        assertEquals("001",DppmsCompanyCodeConverter.fromSource("01"));
        assertEquals("002",DppmsCompanyCodeConverter.fromSource("02"));
        assertEquals("003",DppmsCompanyCodeConverter.fromSource("03"));
        assertEquals("004",DppmsCompanyCodeConverter.fromSource("04"));
        for (var value:new String[] {null,"","05","1","001"," 01","01 "}) {
            var failure=assertThrows(IllegalArgumentException.class,()->DppmsCompanyCodeConverter.fromSource(value));
            assertTrue(failure.getMessage().contains("DPPMS_COMPANY_CODE_UNMAPPED"));
        }
    }
}
