package cn.iocoder.yudao.module.pms.integration.api.deviceops;

import java.util.List;
import java.util.Map;

/** DAC owns immutable parser inputs, releases, jobs and structured results. Callers own business authorization. */
public interface DeviceLogParsingApi {
    /** 复用 DAC 脱敏与命令分块；仅内存处理，不创建执行或解析任务。 */
    NormalizedLog normalize(LogInput command);

    record LogInput(String projectKey, String inputText, String commandHint) { }
    record LogBlock(Integer commandIndex, String commandText, String stdout, String stderr, String status,
                    Boolean truncated, Integer exitCode) { }
    record NormalizedLog(List<LogBlock> commandBlocks, List<String> serialNumbers, List<String> warnings) { }

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

    /** DAC explicitly rejected the input; retrying unchanged content cannot resolve this failure. */
    class InvalidInputException extends ParsingException {
        public InvalidInputException(String message) { super(message); }
    }
}
