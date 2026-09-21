package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTask;

import java.util.List;
import java.util.Optional;

public interface ParseResultQueryPort {
    Optional<ParseTask> findTask(String callerNamespace, String taskId);

    List<ParseTask> listTasks(String callerNamespace, int limit, String afterTaskId);

    List<ParseTaskResult> listTaskResultsByRequestPrefix(String callerNamespace, String requestPrefix);

    Optional<ParseResultEnvelope> findResult(String callerNamespace, String resultId);

    record ParseTaskResult(ParseTask task, ParseResultEnvelope result) { }
}
