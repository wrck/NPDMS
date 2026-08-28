package cn.iocoder.yudao.module.pms.asset.domain.version;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TechnicalNoticeMatcherTest {

    @Test
    void shouldMatchExactConpAndOptionalVersions() {
        SoftwareVersion device = version("V3.2.1", "CONP", "S3", "3.2.1", "B1", "C1", "P1");
        SoftwareVersion notice = version("V3.2.1", "CONP", "S3", "3.2.*", "B1", null, null);
        assertEquals(TechnicalNoticeMatcher.Result.MATCHED, TechnicalNoticeMatcher.match(device, notice));
    }

    @Test
    void shouldReturnUndeterminedWhenConpEvidenceIsIncomplete() {
        SoftwareVersion device = version("V3.2.1", null, "S3", null, "B1", "C1", "P1");
        SoftwareVersion notice = version(null, "CONP", "S3", "3.2.*", null, null, null);
        assertEquals(TechnicalNoticeMatcher.Result.UNDETERMINED, TechnicalNoticeMatcher.match(device, notice));
    }

    @Test
    void shouldNotMatchDifferentConpSeriesOrOptionalVersion() {
        SoftwareVersion device = version("V3.2.1", "CONP", "S3", "3.2.1", "B2", "C1", "P1");
        SoftwareVersion seriesMismatch = version("V3.2.1", "CONP", "S4", "3.2.*", null, null, null);
        SoftwareVersion bootMismatch = version("V3.2.1", "CONP", "S3", "3.2.*", "B1", null, null);
        assertEquals(TechnicalNoticeMatcher.Result.NOT_MATCHED,
                TechnicalNoticeMatcher.match(device, seriesMismatch));
        assertEquals(TechnicalNoticeMatcher.Result.NOT_MATCHED,
                TechnicalNoticeMatcher.match(device, bootMismatch));
    }

    @Test
    void shouldKeepOriginalConpVersionSeparateFromParsedFields() {
        SoftwareVersion version = version("raw-CONP-3.2.1", "CONP", "S3", "3.2.*", null, null, null);
        assertEquals("raw-CONP-3.2.1", version.conpVersion());
        assertEquals("3.2.*", version.conpMark());
    }

    @Test
    void shouldKeepNetworkEffectiveTimeInDerivedEntity() {
        LocalDateTime effectiveFrom = LocalDateTime.of(2026, 8, 26, 20, 0);
        NetworkSoftwareVersion version = new NetworkSoftwareVersion(
                "V3.2.1", "CONP", "S3", "3.2.1",
                "B1", "C1", "P1", false,
                "ITR", "key", "1", null, null, "FRESH", effectiveFrom);
        assertEquals(effectiveFrom, version.effectiveFrom());
    }

    private static SoftwareVersion version(String conpVersion, String conpType, String conpSeries,
                                           String conpMark, String bootVersion, String cpldVersion,
                                           String pcbVersion) {
        return new NetworkSoftwareVersion(
                conpVersion, conpType, conpSeries, conpMark,
                bootVersion, cpldVersion, pcbVersion, false,
                "ITR", "key", "1", null, null, "FRESH", null);
    }
}
