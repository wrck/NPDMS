package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.LogType;
import com.dp.deviceops.parser.runtime.model.ParserRelease;
import com.dp.deviceops.parser.runtime.model.ParserReleaseValidation;
import com.dp.deviceops.parser.runtime.model.ReleaseState;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import com.dp.deviceops.parser.semantic.release.ParserRuleArtifactCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JdbcParserReleaseRepository implements ParserReleaseRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final ParserJdbcJson json;
    private final ParserRuleArtifactCodec ruleArtifacts;
    private final Clock clock;

    public JdbcParserReleaseRepository(JdbcClient jdbc, TransactionTemplate transactions,
            ObjectMapper objectMapper, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.json = new ParserJdbcJson(Objects.requireNonNull(objectMapper, "objectMapper"));
        this.ruleArtifacts = new ParserRuleArtifactCodec();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LogType createLogType(LogType logType) {
        jdbc.sql("insert into device_ops_parser_log_type(log_type,display_name,description,created_at,updated_at) "
                        + "values(:type,:name,:description,:created,:updated)")
                .param("type", logType.logType()).param("name", logType.displayName())
                .param("description", logType.description()).param("created", logType.createdAt())
                .param("updated", logType.updatedAt()).update();
        return logType;
    }

    @Override
    public Optional<LogType> findLogType(String logType) {
        return jdbc.sql("select * from device_ops_parser_log_type where log_type=:type")
                .param("type", logType).query(this::mapLogType).optional();
    }

    @Override
    public List<LogType> listLogTypes() {
        return jdbc.sql("select * from device_ops_parser_log_type order by log_type")
                .query(this::mapLogType).list();
    }

    @Override
    public ParserRelease saveDraft(ParserRelease release, ParserReleaseBundle bundle) {
        return transactions.execute(status -> {
            Optional<ParserRelease> current = findRelease(release.releaseId());
            if (current.isEmpty()) {
                jdbc.sql("""
                        insert into device_ops_parser_release(
                          release_id,log_type,release_version,state,coordinate_json,manifest_json,rules_json,
                          projections_json,verification_cases_json,draft_revision,created_at)
                        values(:id,:type,:version,'DRAFT',:coordinate,:manifest,:rules,:projections,:cases,:revision,:created)
                        """).param("id", release.releaseId()).param("type", release.logType())
                        .param("version", release.releaseVersion()).param("coordinate", json.encode(release.coordinate()))
                        .param("manifest", json.encode(bundle.manifest())).param("rules", ruleArtifacts.encode(bundle))
                        .param("projections", bundle.projectionsJson())
                        .param("cases", json.encode(bundle.verificationCases()))
                        .param("revision", release.draftRevision()).param("created", release.createdAt()).update();
            } else {
                int changed = jdbc.sql("""
                        update device_ops_parser_release set coordinate_json=:coordinate,manifest_json=:manifest,
                          rules_json=:rules,projections_json=:projections,verification_cases_json=:cases,
                          draft_revision=:revision,validation_report_json=null,validation_revision=null
                        where release_id=:id and state='DRAFT' and draft_revision=:previous
                        """).param("coordinate", json.encode(release.coordinate()))
                        .param("manifest", json.encode(bundle.manifest())).param("rules", ruleArtifacts.encode(bundle))
                        .param("projections", bundle.projectionsJson()).param("cases", json.encode(bundle.verificationCases()))
                        .param("revision", release.draftRevision()).param("id", release.releaseId())
                        .param("previous", release.draftRevision() - 1).update();
                if (changed != 1) {
                    throw new IllegalStateException("parser release draft changed");
                }
            }
            return findRelease(release.releaseId()).orElseThrow();
        });
    }

    @Override
    public Optional<ParserRelease> findRelease(String releaseId) {
        return jdbc.sql("select * from device_ops_parser_release where release_id=:id")
                .param("id", releaseId).query(this::mapRelease).optional();
    }

    @Override
    public List<ParserRelease> listReleases(String logType) {
        return jdbc.sql("select * from device_ops_parser_release where log_type=:type order by release_version")
                .param("type", logType).query(this::mapRelease).list();
    }

    @Override
    public Optional<ParserRelease> findActive(String logType) {
        return jdbc.sql("select r.* from device_ops_parser_active_release a join device_ops_parser_release r "
                        + "on r.release_id=a.release_id where a.log_type=:type")
                .param("type", logType).query(this::mapRelease).optional();
    }

    @Override
    public ParserReleaseBundle loadBundle(String releaseId) {
        Map<String, Object> row = jdbc.sql("select manifest_json,rules_json,projections_json,verification_cases_json "
                        + "from device_ops_parser_release where release_id=:id")
                .param("id", releaseId).query().singleRow();
        ParserReleaseBundle.VerificationCase[] cases = json.decode((String) row.get("VERIFICATION_CASES_JSON"),
                ParserReleaseBundle.VerificationCase[].class);
        ParserReleaseManifest manifest = json.decode((String) row.get("MANIFEST_JSON"), ParserReleaseManifest.class);
        ParserRuleArtifactCodec.Decoded decoded = ruleArtifacts.decode(manifest, (String) row.get("RULES_JSON"));
        return new ParserReleaseBundle(manifest, decoded.rulesJson(), (String) row.get("PROJECTIONS_JSON"),
                decoded.modelProfilesJson(), decoded.ruleSetJsonByPath(), Arrays.asList(cases));
    }

    @Override
    public boolean saveValidation(ParserReleaseValidation validation) {
        return jdbc.sql("update device_ops_parser_release set validation_report_json=:report,validation_revision=:revision "
                        + "where release_id=:id and state='DRAFT' and draft_revision=:revision")
                .param("report", json.encode(validation)).param("revision", validation.draftRevision())
                .param("id", validation.releaseId()).update() == 1;
    }

    @Override
    public boolean publish(String releaseId, ReleaseState expectedState) {
        return jdbc.sql("update device_ops_parser_release set state='PUBLISHED',published_at=:now "
                        + "where release_id=:id and state=:state and validation_revision=draft_revision")
                .param("now", clock.instant()).param("id", releaseId).param("state", expectedState.name())
                .update() == 1;
    }

    @Override
    public boolean activate(String logType, String releaseId, String expectedCurrentReleaseId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            String current = jdbc.sql("select release_id from device_ops_parser_active_release where log_type=:type")
                    .param("type", logType).query(String.class).optional().orElse(null);
            if (!Objects.equals(current, expectedCurrentReleaseId)) {
                return false;
            }
            if (current == null) {
                try {
                    jdbc.sql("insert into device_ops_parser_active_release(log_type,release_id,updated_at) "
                                    + "values(:type,:release,:now)")
                            .param("type", logType).param("release", releaseId)
                            .param("now", clock.instant()).update();
                } catch (DuplicateKeyException exception) {
                    return false;
                }
            } else {
                if (jdbc.sql("update device_ops_parser_active_release set release_id=:release,updated_at=:now "
                                + "where log_type=:type and release_id=:expected")
                        .param("release", releaseId).param("now", clock.instant()).param("type", logType)
                        .param("expected", expectedCurrentReleaseId).update() != 1) {
                    return false;
                }
            }
            return true;
        }));
    }

    @Override
    public boolean clearActive(String logType, String expectedCurrentReleaseId) {
        return jdbc.sql("delete from device_ops_parser_active_release where log_type=:type and release_id=:expected")
                .param("type", logType).param("expected", expectedCurrentReleaseId).update() == 1;
    }

    @Override
    public boolean disable(String releaseId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            jdbc.sql("delete from device_ops_parser_active_release where release_id=:id")
                    .param("id", releaseId).update();
            return jdbc.sql("update device_ops_parser_release set state='DISABLED' "
                            + "where release_id=:id and state='PUBLISHED'")
                    .param("id", releaseId).update() == 1;
        }));
    }

    private LogType mapLogType(java.sql.ResultSet resultSet, int row) throws java.sql.SQLException {
        return new LogType(resultSet.getString("log_type"), resultSet.getString("display_name"),
                resultSet.getString("description"), ParserJdbcJson.instant(resultSet.getObject("created_at")),
                ParserJdbcJson.instant(resultSet.getObject("updated_at")));
    }

    private ParserRelease mapRelease(java.sql.ResultSet resultSet, int row) throws java.sql.SQLException {
        String validationJson = resultSet.getString("validation_report_json");
        return new ParserRelease(resultSet.getString("release_id"), resultSet.getString("log_type"),
                resultSet.getString("release_version"), ReleaseState.valueOf(resultSet.getString("state")),
                json.decode(resultSet.getString("coordinate_json"), ParserCoordinate.class),
                resultSet.getLong("draft_revision"), validationJson == null ? null
                        : json.decode(validationJson, ParserReleaseValidation.class),
                ParserJdbcJson.instant(resultSet.getObject("created_at")),
                ParserJdbcJson.instant(resultSet.getObject("published_at")));
    }
}
