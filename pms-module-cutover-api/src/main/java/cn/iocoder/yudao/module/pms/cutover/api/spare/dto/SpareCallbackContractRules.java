package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import cn.iocoder.yudao.module.pms.cutover.api.spare.CutoverSpareCallbackException;
import java.util.Map;

final class SpareCallbackContractRules {
    private SpareCallbackContractRules() { }

    static long positive(Long value, String field) {
        if (value == null || value <= 0) throw invalid(field);
        return value;
    }

    static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw invalid(field);
        }
        return value;
    }

    static Map<String, Object> jsonObject(Map<String, Object> value, String field) {
        try {
            return SpareStatusSnapshotNormalizer.normalize(value);
        } catch (RuntimeException exception) {
            throw invalid(field);
        }
    }

    static CutoverSpareCallbackException invalid(String field) {
        return new CutoverSpareCallbackException(CutoverSpareCallbackException.Code.INVALID_REQUEST,
                "invalid " + field);
    }
}
