package cn.iocoder.yudao.module.pms.project.domain.legacydetail;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDetailNormalizerTest {
    private final LegacyDetailCatalog catalog = new LegacyDetailCatalog();
    private final LegacyDetailNormalizer normalizer = new LegacyDetailNormalizer(catalog);

    private LegacyDetailInput input(List<LegacyDetailInput.SourceRecord> rows) {
        var counts = new LinkedHashMap<String, Integer>();
        catalog.domains().forEach(domain -> counts.put(domain.code(), 0));
        rows.forEach(row -> counts.compute(row.domain(), (key, count) -> count + 1));
        return new LegacyDetailInput("synthetic-batch", "101", "SYNTHETIC-CONTRACT",
                LocalDateTime.of(2026, 10, 8, 10, 0), null, counts, rows);
    }

    private LegacyDetailInput.SourceRecord record(String domain,String key,String parentDomain,String parentKey,
            String updated,Map<String,String> attributes) {
        var values=new LinkedHashMap<String,String>();
        catalog.require(domain).fields().forEach(field->values.put(field.key(),null));values.putAll(attributes);
        return new LegacyDetailInput.SourceRecord(domain,key,parentDomain,parentKey,updated,values);
    }

    private LegacyDetailInput.SourceRecord project(String remark) {
        return record("project", "101", null, null, null,
                Map.of("projectId", "101", "projectName", remark));
    }

    @Test void rejectsPartialOriginalFieldsIncludingAnOmittedNullField() {
        var partial=new LegacyDetailInput.SourceRecord("project","101",null,null,null,Map.of("projectId","101","projectName","Synthetic"));
        assertThrows(ServiceException.class,()->normalizer.normalize(input(List.of(partial))));
        var full=project("Synthetic");var omitted=new LinkedHashMap<>(full.values());
        assertNull(omitted.remove("customConfig"));
        var missingNull=new LegacyDetailInput.SourceRecord("project","101",null,null,null,omitted);
        assertThrows(ServiceException.class,()->normalizer.normalize(input(List.of(missingNull))));
    }

    @Test void explicitNullFieldsSurviveSourcePayloadEncodingAndAreAccepted() {
        var full=project("Synthetic");
        var encoded=LegacyDetailNormalizer.sourcePayloadJson(full.values());
        var decoded=JsonUtils.parseObject(encoded,new tools.jackson.core.type.TypeReference<Map<String,String>>(){});
        assertEquals(full.values().keySet(),decoded.keySet());
        assertTrue(decoded.containsKey("customConfig"));assertNull(decoded.get("customConfig"));
        var incoming=new LegacyDetailInput.SourceRecord("project","101",null,null,null,decoded);
        var normalized=normalizer.normalize(input(List.of(incoming))).records().getFirst();
        assertEquals(full.values().keySet(),normalized.values().keySet());
        assertNull(normalized.values().get("customConfig"));assertEquals("Synthetic",normalized.values().get("projectName"));
    }

    @Test void rejectsMissingRootBeforePersistence() {
        assertThrows(ServiceException.class, () -> normalizer.normalize(input(List.of())));
    }

    @Test void evidenceClockUsesIsoTimeEvenWithTheFrameworkTimestampModule() {
        var decoded = JsonUtils.parseObject("{\"sourceReadAt\":\"2026-10-08T10:20:30.123\"}", LegacyDetailInput.class);
        assertEquals(LocalDateTime.of(2026, 10, 8, 10, 20, 30, 123_000_000), decoded.sourceReadAt());
        var stale = JsonUtils.parseObject("{\"sourceReadAt\":\"2000-01-01T00:00:00.000\"}", LegacyDetailInput.class);
        assertTrue(stale.sourceReadAt().isBefore(decoded.sourceReadAt()));
        assertThrows(RuntimeException.class, () -> JsonUtils.parseObject("{\"sourceReadAt\":0}", LegacyDetailInput.class));
        assertThrows(RuntimeException.class, () -> JsonUtils.parseObject("{\"sourceReadAt\":\"invalid\"}", LegacyDetailInput.class));
    }

    @Test void retainsRedactionMarkersSuppliedByTheSourceAdapter() {
        var bundle = normalizer.normalize(input(List.of(project(LegacyDetailNormalizer.REDACTED))));
        assertEquals(List.of("projectName"), bundle.records().getFirst().redactedFields());
    }

    @Test void masksCredentialsBeforeComputingPersistedEvidence() {
        for (String text : List.of("password=synthetic-value", "Authorization: Bearer synthetic-value",
                "设备口令：synthetic-value", "ssh://synthetic:synthetic@example.invalid")) {
            var bundle = normalizer.normalize(input(List.of(project(text))));
            assertEquals(LegacyDetailNormalizer.REDACTED, bundle.records().getFirst().values().get("projectName"));
            assertEquals(List.of("projectName"), bundle.records().getFirst().redactedFields());
        }
    }

    @Test void rejectsUnknownFieldsAndAnotherProjectsRecords() {
        var hidden = record("project", "101", null, null, null,
                Map.of("projectId", "101", "devicePassword", "synthetic-only"));
        assertThrows(ServiceException.class, () -> normalizer.normalize(input(List.of(hidden))));
        var other = record("members", "9", null, null, null,
                Map.of("id", "9", "projectId", "102"));
        assertThrows(ServiceException.class, () -> normalizer.normalize(input(List.of(project(""), other))));
    }

    @Test void rejectsDuplicateKeysMissingParentsAndInaccurateCounts() {
        var root = project("");
        assertThrows(ServiceException.class, () -> normalizer.normalize(input(List.of(root, root))));
        var child = record("software-versions", "2", "software-changes", "3", null,
                Map.of("id", "2", "projectId", "101", "logId", "3"));
        assertThrows(ServiceException.class, () -> normalizer.normalize(input(List.of(root, child))));
        var original = input(List.of(root));
        var counts = new HashMap<>(original.domainCounts());
        counts.put("members", 1);
        assertThrows(ServiceException.class, () -> normalizer.normalize(new LegacyDetailInput(
                original.batchKey(), original.sourceProjectKey(), original.sourceContractNo(), original.sourceReadAt(),
                null, counts, original.records())));
    }

    @Test void digestIsIndependentOfInputOrderAndChangesWithHistoryContent() {
        var member = record("members", "9", "project", "101", "2026-10-07",
                Map.of("id", "9", "projectId", "101", "memberName", "Synthetic Member"));
        var a = normalizer.normalize(input(List.of(project("first"), member)));
        var b = normalizer.normalize(input(List.of(member, project("first"))));
        assertEquals(a.checksum(), b.checksum());
        assertNotEquals(a.checksum(), normalizer.normalize(input(List.of(project("changed"), member))).checksum());
        assertEquals(1, a.counts().get("members"));
    }

    @Test void rejectsIncompleteDomainCoverage() {
        var original = input(List.of(project("")));
        var counts = new HashMap<>(original.domainCounts());
        counts.remove("licenses");
        assertThrows(ServiceException.class, () -> normalizer.normalize(new LegacyDetailInput(
                original.batchKey(), original.sourceProjectKey(), original.sourceContractNo(), original.sourceReadAt(),
                null, counts, original.records())));
    }
}
