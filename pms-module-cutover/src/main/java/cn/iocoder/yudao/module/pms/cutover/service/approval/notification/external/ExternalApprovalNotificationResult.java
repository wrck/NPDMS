package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import java.time.LocalDateTime;

public sealed interface ExternalApprovalNotificationResult {

    record Accepted(String providerReferenceId, LocalDateTime acceptedAt)
            implements ExternalApprovalNotificationResult {
        public Accepted {
            require(normalized(providerReferenceId, 128) && acceptedAt != null, "accepted result invalid");
        }
    }

    record DeliveryUnknown(String providerReferenceId) implements ExternalApprovalNotificationResult {
        public DeliveryUnknown {
            require(providerReferenceId == null || normalized(providerReferenceId, 128), "unknown result invalid");
        }
    }

    record ExplicitFailure(String errorCode) implements ExternalApprovalNotificationResult {
        public ExplicitFailure {
            require(normalized(errorCode, 64), "failure result invalid");
        }
    }

    private static boolean normalized(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength && value.equals(value.trim());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
