package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentContractWarrantyCorrectionApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ShipmentContractWarrantyCorrectionMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.ShipmentContractWarrantyCorrectionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ShipmentContractWarrantyCorrectionService implements ShipmentContractWarrantyCorrectionApi {
    private final ShipmentContractWarrantyCorrectionMapper mapper;
    @Override public List<Result> preview(Request request) { return execute(request, false); }
    @Override @Transactional(propagation=Propagation.MANDATORY, rollbackFor=Exception.class)
    public List<Result> apply(Request request) { return execute(request, true); }

    private List<Result> execute(Request request, boolean apply) {
        if (request == null || !Objects.equals(request.tenantId(), TenantContextHolder.getRequiredTenantId())
                || request.owner() == null || !request.owner().matches("integration:" + request.tenantId() + ":[0-9]+")
                || request.entries() == null || request.entries().isEmpty() || request.entries().size() > 10000)
            throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_SCOPE_INVALID");
        var results = new ArrayList<Result>(); var ids = new HashSet<Long>(); var keys = new HashSet<String>();
        // Validate and lock every complete reference before the first write.
        for (var entry : request.entries()) {
            if (entry == null || entry.targetId() == null || entry.targetId() <= 0 || !ids.add(entry.targetId())
                    || entry.sourceKey() == null || entry.sourceKey().isBlank() || !keys.add(entry.sourceKey())
                    || entry.expectedFingerprint() == null || !entry.expectedFingerprint().matches("[0-9a-f]{64}")
                    || entry.normalizedFingerprint() == null || !entry.normalizedFingerprint().matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_PROOF_INVALID");
            var query = new ShipmentContractWarrantyCorrectionQuery(request.tenantId(), entry.targetId(), entry.sourceKey());
            var current = apply ? mapper.selectCurrentForUpdate(query) : mapper.selectCurrent(query);
            if (current == null || !Objects.equals(current.deleted(), 0)
                    || !(Objects.equals(current.fingerprint(), entry.expectedFingerprint())
                        || current.warrantyYears() == null && Objects.equals(current.fingerprint(), entry.normalizedFingerprint()))
                    || !Objects.equals(current.normalizedFingerprint(), entry.normalizedFingerprint())
                    || current.warrantyYears() != null && !current.warrantyYears().trim().isEmpty())
                throw new IllegalArgumentException("CONTRACT_REFERENCE_CHANGED_AFTER_REVIEW");
            results.add(new Result(current.id(), entry.sourceKey(), current.warrantyYears(),
                    current.warrantyYears() == null ? "UNCHANGED" : "UPDATED", current.fingerprint(), current.normalizedFingerprint()));
        }
        if (apply) for (var result : results) if ("UPDATED".equals(result.action())) {
            var query = new ShipmentContractWarrantyCorrectionQuery(request.tenantId(), result.targetId(), result.sourceKey());
            if (mapper.normalizeEmptyYear(query) != 1) throw new IllegalStateException("CONTRACT_REFERENCE_CORRECTION_CONFLICT");
        }
        return List.copyOf(results);
    }
}
