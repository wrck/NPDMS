package cn.iocoder.yudao.module.pms.integration.api.deviceops;

import java.util.List;
import java.util.Map;

/** DAC owns immutable parser inputs, releases, jobs and structured results. Callers own business authorization. */
public interface DeviceLogParsingApi {
    ParseTask submit(Submission command);

    /** Exact lookup only; null means this request has not been accepted by DAC. */
    ParseTask find(String requestId, String projectKey, String sourceCollectionId);

    record Submission(String requestId, String projectKey, String sourceCollectionId,
                      String inputText, String commandHint) { }

    record ParseTask(String taskId, String state, String waitReason, String releaseVersion,
                     List<String> warnings, Map<String, Object> result) { }

    class ParsingException extends RuntimeException {
        public ParsingException(String message) { super(message); }
    }
}
