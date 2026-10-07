package cn.iocoder.yudao.module.pms.project.domain.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Normalization runs before any persistence; original free text is never included in errors. */
@Component
@RequiredArgsConstructor
public class LegacyDetailNormalizer {
    private final LegacyDetailCatalog catalog;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd\\s*[:=]|secret|token|private[ _-]?key|密码|口令|私钥|密钥|BEGIN[^\\r\\n]*PRIVATE KEY)");
    private static final Pattern URL_CREDENTIAL = Pattern.compile("(?i)[a-z][a-z0-9+.-]*://[^\\s/]+:[^\\s/]+@");
    public static final String REDACTED = "[敏感内容已隐藏]";
    public record Record(String domain,String sourceTable,String sourceKey,String parentDomain,
                         String parentSourceKey,String sourceUpdatedAt,Map<String,String> values,
                         List<String> redactedFields,String checksum) {}
    public record Bundle(List<Record> records, Map<String,Integer> counts, String checksum) {}
    public Bundle normalize(LegacyDetailInput input) {
        if (input == null || input.records() == null || input.domainCounts() == null
                || input.sourceReadAt() == null || input.sourceProjectKey() == null
                || !input.sourceProjectKey().matches("[1-9][0-9]*")
                || input.batchKey() == null || input.batchKey().isBlank()
                || input.sourceContractNo() == null || input.sourceContractNo().isBlank()) fail("LEGACY_INPUT_INVALID");
        var counts = new TreeMap<String,Integer>();
        for (var d : catalog.domains()) counts.put(d.code(),0);
        if (!counts.keySet().equals(input.domainCounts().keySet())) fail("LEGACY_DOMAIN_COVERAGE_INCOMPLETE");
        var result = new ArrayList<Record>();
        var identities = new HashSet<String>();
        for (var item : input.records()) {
            if (item == null || item.values() == null || item.sourceKey() == null || item.sourceKey().isBlank()
                    || !item.sourceKey().equals(item.sourceKey().trim()) || item.sourceKey().length()>191) fail("LEGACY_RECORD_INVALID");
            var domain = catalog.require(item.domain());
            var fields = new HashSet<String>();
            for (var f : domain.fields()) fields.add(f.key());
            if (!fields.containsAll(item.values().keySet())) fail("LEGACY_FIELD_NOT_ALLOWED");
            if (!"composite".equals(domain.keyField())
                    && !Objects.equals(item.sourceKey(), item.values().get(domain.keyField()))) fail("LEGACY_SOURCE_KEY_MISMATCH");
            if (item.values().containsKey("projectId") && !Objects.equals(item.values().get("projectId"),input.sourceProjectKey()))
                fail("LEGACY_PROJECT_KEY_MISMATCH");
            if (!identities.add(identity(item.domain(),item.sourceKey()))) fail("LEGACY_DUPLICATE_SOURCE_KEY");
            if ((item.parentDomain()==null)!=(item.parentSourceKey()==null)) fail("LEGACY_PARENT_INVALID");
            if (item.parentDomain()!=null) catalog.require(item.parentDomain());
            var values = new TreeMap<String,String>(); var redacted = new ArrayList<String>();
            for (var f : domain.fields()) {
                var raw = item.values().get(f.key());
                if (raw != null && (SECRET.matcher(raw).find() || URL_CREDENTIAL.matcher(raw).find())) {
                    values.put(f.key(),REDACTED);redacted.add(f.key());
                } else values.put(f.key(),raw);
            }
            if (values.values().stream().anyMatch(v -> v != null && v.length()>1_000_000)) fail("LEGACY_FIELD_TOO_LARGE");
            var canonical = new TreeMap<String,Object>();
            canonical.put("domain",item.domain());canonical.put("key",item.sourceKey());
            canonical.put("parentDomain",item.parentDomain());canonical.put("parentKey",item.parentSourceKey());
            canonical.put("sourceUpdatedAt",item.sourceUpdatedAt());canonical.put("values",values);
            result.add(new Record(item.domain(),domain.sourceTable(),item.sourceKey(),item.parentDomain(),
                    item.parentSourceKey(),item.sourceUpdatedAt(),Collections.unmodifiableMap(values),List.copyOf(redacted),sha(JsonUtils.toJsonString(canonical))));
            counts.compute(item.domain(),(k,n)->n+1);
        }
        if (!counts.equals(input.domainCounts())) fail("LEGACY_SOURCE_COUNT_MISMATCH");
        for (var item : result) if (item.parentDomain()!=null && !identities.contains(identity(item.parentDomain(),item.parentSourceKey())))
            fail("LEGACY_PARENT_NOT_IN_SNAPSHOT");
        result.sort(Comparator.comparing(Record::domain).thenComparing(Record::sourceKey));
        var digest = new TreeMap<String,Object>();digest.put("sourceProjectKey",input.sourceProjectKey());
        digest.put("sourceContractNo",input.sourceContractNo());digest.put("counts",counts);
        digest.put("records",result.stream().map(Record::checksum).toList());
        return new Bundle(List.copyOf(result),Collections.unmodifiableMap(counts),sha(JsonUtils.toJsonString(digest)));
    }
    private static String identity(String domain,String key) { return domain+"\u0000"+key; }
    public static String sha(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void fail(String code) { throw invalidParamException(code); }
}
