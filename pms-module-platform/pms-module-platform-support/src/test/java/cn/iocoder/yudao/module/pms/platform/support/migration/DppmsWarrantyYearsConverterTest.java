package cn.iocoder.yudao.module.pms.platform.support.migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsWarrantyYearsConverterTest {
    @Test void preservesYearCountsAndOnlyEmptyValuesBecomeNull() {
        assertNull(DppmsWarrantyYearsConverter.fromSource(null));
        assertNull(DppmsWarrantyYearsConverter.fromSource("  "));
        assertEquals(0, DppmsWarrantyYearsConverter.fromSource("0 "));
        assertEquals(3, DppmsWarrantyYearsConverter.fromSource("3 "));
        assertEquals(10, DppmsWarrantyYearsConverter.fromSource("10"));
        assertEquals(Integer.MAX_VALUE, DppmsWarrantyYearsConverter.fromSource("2147483647"));
    }
    @Test void rejectsNegativeFractionalNonNumericAndOverflowValues() {
        for (String value : new String[]{"-1", "3.5", "3.0", "1e1", "true", "2147483648"})
            assertThrows(IllegalArgumentException.class, () -> DppmsWarrantyYearsConverter.fromSource(value));
    }
}
