package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ControlledCutoverExternalApprovalNotificationPort
        implements CutoverExternalApprovalNotificationPort {

    private final Map<String, ExternalApprovalNotificationResult> results = new LinkedHashMap<>();
    private final List<ExternalApprovalNotificationRequest> requests = new ArrayList<>();

    void result(String channel, ExternalApprovalNotificationResult result) {
        results.put(channel, result);
    }

    List<ExternalApprovalNotificationRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public ExternalApprovalNotificationResult send(ExternalApprovalNotificationRequest request) {
        requests.add(request);
        ExternalApprovalNotificationResult result = results.get(request.channel());
        if (result == null) throw new IllegalStateException("controlled result missing");
        return result;
    }
}
