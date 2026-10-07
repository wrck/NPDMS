package cn.iocoder.yudao.module.pms.project.domain.legacydetail;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;

/** Fixed, audited DPPMS columns. Not a configurable SQL or target-table gateway. */
@Component
public class LegacyDetailCatalog {
    public record Field(String key, String label) {}
    public record Domain(String code, String label, String group, String sourceTable,
                         String keyField, List<Field> fields) {}
    private final List<Domain> domains;
    private final Map<String, Domain> byCode;
    public LegacyDetailCatalog() {
        try (var input = new ClassPathResource("projectlegacy/dppms-detail-domains.json").getInputStream()) {
            domains = List.copyOf(new ObjectMapper().readValue(input, new TypeReference<List<Domain>>() {}));
            var map = new LinkedHashMap<String, Domain>();
            for (var domain : domains) {
                if (map.put(domain.code(), domain) != null) throw new IllegalStateException("Duplicate legacy domain");
            }
            byCode = Collections.unmodifiableMap(map);
        } catch (java.io.IOException e) { throw new IllegalStateException("Legacy detail catalog unavailable", e); }
    }
    public List<Domain> domains() { return domains; }
    public Domain require(String code) {
        var domain = byCode.get(code);
        if (domain == null) throw ServiceExceptionUtil.invalidParamException("LEGACY_DOMAIN_INVALID");
        return domain;
    }
}
