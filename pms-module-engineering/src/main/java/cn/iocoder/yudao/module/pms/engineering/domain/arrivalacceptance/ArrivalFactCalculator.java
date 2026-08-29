package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalAcceptanceRules.QuantityKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** 从当前应到范围、已确认批次和有效明确豁免计算项目级EXE-01事实。 */
public final class ArrivalFactCalculator {

    public CalculationResult calculate(CalculationInput input) {
        if (input == null || input.checkedAt() == null || input.expectedDeviceIds() == null
                || input.expectedQuantityScopes() == null || input.acceptedDevices() == null
                || input.acceptedQuantities() == null || input.deviceExemptions() == null
                || input.quantityExemptions() == null) {
            throw new IllegalArgumentException("arrival fact calculation input is incomplete");
        }

        TreeSet<Long> expectedDevices = new TreeSet<>(input.expectedDeviceIds());
        TreeMap<QuantityKey, BigDecimal> expectedQuantities = expectedQuantities(input.expectedQuantityScopes());
        if (expectedDevices.isEmpty() && expectedQuantities.isEmpty()) {
            throw new IllegalStateException("current expected arrival scope is empty");
        }
        TreeSet<Long> acceptedDevices = new TreeSet<>();
        TreeSet<Long> exemptedDevices = new TreeSet<>();
        TreeSet<Long> sourceAcceptanceIds = new TreeSet<>();

        for (DeviceContribution contribution : input.acceptedDevices()) {
            requireExpectedDevice(expectedDevices, contribution.deviceId());
            if (!acceptedDevices.add(contribution.deviceId())) {
                throw new IllegalStateException("device is accepted by more than one current fact");
            }
            sourceAcceptanceIds.add(contribution.sourceAcceptanceId());
        }
        for (DeviceExemption exemption : input.deviceExemptions()) {
            if (!exemption.isEffective(input.checkedAt())) continue;
            requireExpectedDevice(expectedDevices, exemption.deviceId());
            if (!acceptedDevices.contains(exemption.deviceId())) {
                exemptedDevices.add(exemption.deviceId());
                sourceAcceptanceIds.add(exemption.sourceAcceptanceId());
            }
        }

        Map<QuantityKey, BigDecimal> acceptedByKey = sumQuantities(
                input.acceptedQuantities(), expectedQuantities, sourceAcceptanceIds);
        Map<QuantityKey, BigDecimal> exemptedByKey = new HashMap<>();
        for (QuantityExemption exemption : input.quantityExemptions()) {
            if (!exemption.isEffective(input.checkedAt())) continue;
            QuantityKey key = QuantityKey.from(exemption.scope());
            BigDecimal expected = requireExpectedQuantity(expectedQuantities, key);
            BigDecimal accepted = acceptedByKey.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal exempted = exemptedByKey.merge(key, exemption.scope().quantity(), BigDecimal::add);
            if (accepted.add(exempted).compareTo(expected) > 0) {
                throw new IllegalStateException("accepted and exempted quantity exceeds expected scope");
            }
            sourceAcceptanceIds.add(exemption.sourceAcceptanceId());
        }

        TreeSet<Long> unmetDevices = new TreeSet<>(expectedDevices);
        unmetDevices.removeAll(acceptedDevices);
        unmetDevices.removeAll(exemptedDevices);
        List<ArrivalQuantityScopeFact> acceptedQuantities = facts(acceptedByKey);
        List<ArrivalQuantityScopeFact> exemptedQuantities = facts(exemptedByKey);
        List<ArrivalQuantityScopeFact> unmetQuantities = new ArrayList<>();
        for (Map.Entry<QuantityKey, BigDecimal> entry : expectedQuantities.entrySet()) {
            BigDecimal remaining = entry.getValue()
                    .subtract(acceptedByKey.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .subtract(exemptedByKey.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            if (remaining.signum() > 0) {
                unmetQuantities.add(entry.getKey().quantity(remaining));
            }
        }
        String decision = unmetDevices.isEmpty() && unmetQuantities.isEmpty()
                ? ArrivalAcceptanceFact.DECISION_ACCEPTED
                : ArrivalAcceptanceFact.DECISION_NOT_ACCEPTED;
        return new CalculationResult(List.copyOf(sourceAcceptanceIds), decision,
                Collections.unmodifiableSet(acceptedDevices), Collections.unmodifiableSet(exemptedDevices),
                Collections.unmodifiableSet(unmetDevices),
                acceptedQuantities, exemptedQuantities, List.copyOf(unmetQuantities));
    }

    private static TreeMap<QuantityKey, BigDecimal> expectedQuantities(
            List<ArrivalQuantityScopeFact> scopes) {
        TreeMap<QuantityKey, BigDecimal> result = new TreeMap<>();
        for (ArrivalQuantityScopeFact scope : scopes) {
            QuantityKey key = QuantityKey.from(scope);
            if (result.putIfAbsent(key, scope.quantity()) != null) {
                throw new IllegalStateException("expected quantity scope is duplicated");
            }
        }
        return result;
    }

    private static Map<QuantityKey, BigDecimal> sumQuantities(
            List<QuantityContribution> contributions,
            Map<QuantityKey, BigDecimal> expectedQuantities,
            Set<Long> sourceAcceptanceIds) {
        Map<QuantityKey, BigDecimal> result = new HashMap<>();
        for (QuantityContribution contribution : contributions) {
            QuantityKey key = QuantityKey.from(contribution.scope());
            BigDecimal expected = requireExpectedQuantity(expectedQuantities, key);
            BigDecimal accepted = result.merge(key, contribution.scope().quantity(), BigDecimal::add);
            if (accepted.compareTo(expected) > 0) {
                throw new IllegalStateException("accepted quantity exceeds expected scope");
            }
            sourceAcceptanceIds.add(contribution.sourceAcceptanceId());
        }
        return result;
    }

    private static void requireExpectedDevice(Set<Long> expectedDevices, Long deviceId) {
        if (deviceId == null || !expectedDevices.contains(deviceId)) {
            throw new IllegalStateException("device is outside current expected scope");
        }
    }

    private static BigDecimal requireExpectedQuantity(Map<QuantityKey, BigDecimal> expected, QuantityKey key) {
        BigDecimal quantity = expected.get(key);
        if (quantity == null) {
            throw new IllegalStateException("quantity is outside current expected scope");
        }
        return quantity;
    }

    private static List<ArrivalQuantityScopeFact> facts(Map<QuantityKey, BigDecimal> quantities) {
        return quantities.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().quantity(entry.getValue()))
                .toList();
    }

    public record CalculationInput(
            Set<Long> expectedDeviceIds,
            List<ArrivalQuantityScopeFact> expectedQuantityScopes,
            List<DeviceContribution> acceptedDevices,
            List<QuantityContribution> acceptedQuantities,
            List<DeviceExemption> deviceExemptions,
            List<QuantityExemption> quantityExemptions,
            LocalDateTime checkedAt) {
    }

    public record DeviceContribution(Long sourceAcceptanceId, Long deviceId) {
        public DeviceContribution {
            requireContribution(sourceAcceptanceId, deviceId);
        }
    }

    public record QuantityContribution(Long sourceAcceptanceId, ArrivalQuantityScopeFact scope) {
        public QuantityContribution {
            if (sourceAcceptanceId == null || sourceAcceptanceId <= 0 || scope == null) {
                throw new IllegalArgumentException("invalid accepted quantity contribution");
            }
        }
    }

    public record DeviceExemption(Long sourceAcceptanceId, Long deviceId,
                                  String reason, String riskDescription,
                                  Long approvedBy, LocalDateTime approvedAt,
                                  Long evidenceId, Integer evidenceRevision,
                                  LocalDateTime expiresAt) {
        public DeviceExemption {
            requireContribution(sourceAcceptanceId, deviceId);
        }

        boolean isEffective(LocalDateTime checkedAt) {
            return hasText(reason) && hasText(riskDescription)
                    && approvedBy != null && approvedAt != null
                    && evidenceId != null && evidenceRevision != null && evidenceRevision > 0
                    && expiresAt != null && expiresAt.isAfter(checkedAt);
        }
    }

    public record QuantityExemption(Long sourceAcceptanceId, ArrivalQuantityScopeFact scope,
                                    String reason, String riskDescription,
                                    Long approvedBy, LocalDateTime approvedAt,
                                    Long evidenceId, Integer evidenceRevision,
                                    LocalDateTime expiresAt) {
        public QuantityExemption {
            if (sourceAcceptanceId == null || sourceAcceptanceId <= 0 || scope == null) {
                throw new IllegalArgumentException("invalid quantity exemption");
            }
        }

        boolean isEffective(LocalDateTime checkedAt) {
            return hasText(reason) && hasText(riskDescription)
                    && approvedBy != null && approvedAt != null
                    && evidenceId != null && evidenceRevision != null && evidenceRevision > 0
                    && expiresAt != null && expiresAt.isAfter(checkedAt);
        }
    }

    public record CalculationResult(
            List<Long> sourceAcceptanceIds,
            String decision,
            Set<Long> acceptedDeviceIds,
            Set<Long> exemptedDeviceIds,
            Set<Long> unmetDeviceIds,
            List<ArrivalQuantityScopeFact> acceptedQuantityScopes,
            List<ArrivalQuantityScopeFact> exemptedQuantityScopes,
            List<ArrivalQuantityScopeFact> unmetQuantityScopes) {
    }

    private static void requireContribution(Long sourceAcceptanceId, Long deviceId) {
        if (sourceAcceptanceId == null || sourceAcceptanceId <= 0 || deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("invalid arrival device contribution");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
