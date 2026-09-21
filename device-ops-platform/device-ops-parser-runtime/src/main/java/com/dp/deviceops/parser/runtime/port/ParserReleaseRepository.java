package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;

import java.util.List;
import java.util.Optional;

public interface ParserReleaseRepository {
    LogType createLogType(LogType logType);

    Optional<LogType> findLogType(String logType);

    List<LogType> listLogTypes();

    ParserRelease saveDraft(ParserRelease release, ParserReleaseBundle bundle);

    Optional<ParserRelease> findRelease(String releaseId);

    List<ParserRelease> listReleases(String logType);

    Optional<ParserRelease> findActive(String logType);

    ParserReleaseBundle loadBundle(String releaseId);

    boolean saveValidation(ParserReleaseValidation validation);

    boolean publish(String releaseId, ReleaseState expectedState);

    boolean activate(String logType, String releaseId, String expectedCurrentReleaseId);

    boolean clearActive(String logType, String expectedCurrentReleaseId);

    boolean disable(String releaseId);
}
