package cn.iocoder.yudao.module.pms.commerce.domain.scope;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.ScopeDetail;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.ScopeLine;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.INVALID_REQUEST;

/** 无数据库、事务或事件副作用的F-COM-001范围校验。 */
public final class DeliveryScopeValidationRules {

    private static final Set<String> INTEGER_UNITS = Set.of("EA", "PCS", "SET");

    public List<ScopeLine> requireLines(List<ScopeLine> lines) {
        if (lines == null || lines.isEmpty()) fail("交付范围明细不能为空");
        Set<Long> orderLineIds = new HashSet<>();
        Set<String> serialKeys = new HashSet<>();
        List<ScopeLine> ordered = lines.stream().sorted(java.util.Comparator.comparing(ScopeLine::orderLineId)).toList();
        for (ScopeLine line : ordered) {
            if (line == null || line.orderLineId() == null || line.orderLineId() <= 0
                    || !orderLineIds.add(line.orderLineId()) || !normalizedText(line.expectedSourceVersion(), 64)
                    || line.quantity() == null || line.quantity().signum() <= 0
                    || !normalizedText(line.unitCode(), 32) || line.details() == null || line.details().isEmpty()) {
                fail("订单行范围输入非法");
            }
            requirePrecision(line.quantity(), line.unitCode());
            BigDecimal sum = BigDecimal.ZERO;
            for (ScopeDetail detail : line.details()) {
                requireDetail(detail, line.unitCode(), serialKeys);
                sum = sum.add(detail.quantity());
            }
            if (sum.compareTo(line.quantity()) != 0) fail("主明细分配数量不一致");
        }
        return List.copyOf(ordered);
    }

    public List<Long> requireOrderLineIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) fail("orderLineIds非法");
        return ids.stream().sorted().toList();
    }

    public String requireText(String value, int max, String field) {
        if (!hasText(value, max) || !value.equals(value.trim())) fail(field + "非法");
        return value;
    }

    public String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String serialKey(String value) {
        return DeviceScopeResolveQuery.comparisonKey(value);
    }

    private void requireDetail(ScopeDetail detail, String lineUnit, Set<String> serialKeys) {
        if (detail == null || detail.quantity() == null || detail.quantity().signum() <= 0
                || !Objects.equals(lineUnit, detail.unitCode())
                || !optionalText(detail.officeDepartmentCode(), 64)
                || !optionalText(detail.productCode(), 64) || !optionalText(detail.modelCode(), 64)
                || trimToNull(detail.productCode()) == null && trimToNull(detail.modelCode()) == null
                || detail.location() == null || detail.location().resolution() == null) {
            fail("范围维度非法");
        }
        requirePrecision(detail.quantity(), detail.unitCode());
        String serial = trimToNull(detail.serialNumber());
        if (serial != null) {
            if (serial.length() > 128 || detail.quantity().compareTo(BigDecimal.ONE) != 0
                    || !serialKeys.add(serialKey(serial))) fail("SN范围维度非法");
        }
        switch (detail.location().resolution()) {
            case RESOLVED -> {
                if (detail.location().siteId() == null || detail.location().siteId() <= 0
                        || detail.location().siteVersion() == null || detail.location().siteVersion() < 0
                        || detail.location().siteLocationId() == null || detail.location().siteLocationId() <= 0
                        || detail.location().siteLocationVersion() == null || detail.location().siteLocationVersion() < 0
                        || trimToNull(detail.location().locationText()) != null) fail("结构化地点非法");
            }
            case UNRESOLVED -> {
                if (detail.location().siteId() != null || detail.location().siteVersion() != null
                        || detail.location().siteLocationId() != null || detail.location().siteLocationVersion() != null
                        || !hasText(detail.location().locationText(), 512)) fail("待解析地点非法");
            }
        }
    }

    private void requirePrecision(BigDecimal quantity, String unit) {
        if (quantity.scale() > 6 || INTEGER_UNITS.contains(unit)
                && quantity.stripTrailingZeros().scale() > 0) fail("数量精度与单位不一致");
    }

    private boolean hasText(String value, int max) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= max;
    }

    private boolean normalizedText(String value, int max) {
        return hasText(value, max) && value.equals(value.trim());
    }

    private boolean optionalText(String value, int max) {
        return value == null || value.trim().length() <= max;
    }

    private static void fail(String message) {
        throw new CommerceDeliveryScopeCommandException(INVALID_REQUEST, message);
    }
}
