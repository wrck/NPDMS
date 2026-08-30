package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetProductTypeAuditService {

    public static final String IMPORT_REJECTED = "ASSET_PRODUCT_TYPE_IMPORT_REJECTED";
    public static final String SOURCE_CONFLICT = "ASSET_PRODUCT_TYPE_SOURCE_CONFLICT";
    public static final String SOURCE_FAILURE = "ASSET_PRODUCT_TYPE_SOURCE_FAILURE";

    private final OperationAuditApi operationAuditApi;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordRejected(Long tenantId, Long actorId, String correlationId, String rejectionCode,
                               String aggregateKey, Map<String, ?> safeDetail) {
        operationAuditApi.record(tenantId, actorId, correlationId, IMPORT_REJECTED,
                "AssetProductType", aggregateKey, rejectionCode, safeDetail);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void recordConflict(Long tenantId, Long actorId, AssetProductTypeImportRejectedException rejection,
                               String currentProductTypeCode, String currentSourceSystem, String currentSourceKey,
                               String currentSourceVersion, java.time.LocalDateTime currentSourceUpdatedAt) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("incomingSourceKeyDigest", sha256(rejection.command().sourceSystem() + ":" + rejection.command().sourceKey()));
        detail.put("currentSourceKeyDigest", currentSourceSystem == null || currentSourceKey == null
                ? null : sha256(currentSourceSystem + ":" + currentSourceKey));
        detail.put("incomingProductTypeCodeDigest", sha256(rejection.command().productTypeCode()));
        detail.put("currentProductTypeCodeDigest", sha256(currentProductTypeCode));
        detail.put("incomingSourceVersion", rejection.command().sourceVersion());
        detail.put("currentSourceVersion", currentSourceVersion);
        detail.put("incomingSourceUpdatedAt", rejection.command().sourceUpdatedAt());
        detail.put("currentSourceUpdatedAt", currentSourceUpdatedAt);
        detail.put("observedSourceUpdatedAt", rejection.observedSourceUpdatedAt());
        detail.put("rejectionCode", rejection.rejectionCode());
        operationAuditApi.record(tenantId, actorId, rejection.command().operationId(), SOURCE_CONFLICT,
                "AssetProductTypeSourceMapping",
                sha256(rejection.command().sourceSystem() + ":" + rejection.command().sourceKey()),
                rejection.rejectionCode(), detail);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void recordSourceFailure(Long tenantId, Long actorId, String correlationId,
                                    String sourceSystem, String sourceKey, String failureCode) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceKeyDigest", sha256(sourceSystem + ":" + sourceKey));
        detail.put("failureCode", failureCode);
        operationAuditApi.record(tenantId, actorId, correlationId, SOURCE_FAILURE,
                "AssetProductTypeSourceMapping", sha256(sourceSystem + ":" + sourceKey), failureCode, detail);
    }

    public Map<String, Object> rejectionDetail(AssetProductTypeImportRejectedException rejection) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceKeyDigest", sha256(rejection.command().sourceSystem() + ":" + rejection.command().sourceKey()));
        detail.put("productTypeCodeDigest", sha256(rejection.command().productTypeCode()));
        detail.put("sourceVersion", rejection.command().sourceVersion());
        detail.put("sourceUpdatedAt", rejection.command().sourceUpdatedAt());
        detail.put("observedSourceUpdatedAt", rejection.observedSourceUpdatedAt());
        detail.put("rejectionCode", rejection.rejectionCode());
        detail.put("deviceCount", rejection.command().devices().size());
        return detail;
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
