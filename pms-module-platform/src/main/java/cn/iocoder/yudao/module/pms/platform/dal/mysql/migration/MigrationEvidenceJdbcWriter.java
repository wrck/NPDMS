package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationSourceRecordDO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Fixed-schema bulk persistence only. Classification, replay, locking, IDs and audit
 * remain owned by the transactional evidence service. Uses its transaction-bound DataSource. */
@Repository
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class MigrationEvidenceJdbcWriter {
    private final JdbcTemplate jdbc;
    private static final int MAX_ROWS = 1000;
    private static final String SOURCE_INSERT = "INSERT INTO plt_migration_source_record "
            + "(id,tenant_id,batch_id,source_system,source_table,source_record_key,source_business_key,"
            + "source_payload,source_checksum,extracted_at,creator,updater,create_time,update_time,deleted) VALUES ";
    private static final String MAPPING_INSERT = "INSERT INTO plt_external_key_mapping "
            + "(id,tenant_id,batch_id,source_record_id,result_type,target_context,target_object_type,target_table,"
            + "target_id,target_role,target_sequence,creator,updater,create_time,update_time,deleted) VALUES ";
    private static final int[] SOURCE_TYPES = {Types.BIGINT,Types.BIGINT,Types.BIGINT,
            Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,
            Types.TIMESTAMP,Types.VARCHAR,Types.VARCHAR,Types.TIMESTAMP,Types.TIMESTAMP};
    private static final int[] MAPPING_TYPES = {Types.BIGINT,Types.BIGINT,Types.BIGINT,Types.BIGINT,
            Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,Types.BIGINT,Types.VARCHAR,
            Types.INTEGER,Types.VARCHAR,Types.VARCHAR,Types.TIMESTAMP,Types.TIMESTAMP};

    public MigrationEvidenceJdbcWriter(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    public int insertSources(List<MigrationSourceRecordDO> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return 0;
        checkSize(rows.size());
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Object[] values = new Object[rows.size() * SOURCE_TYPES.length];
        int offset = 0;
        for (var row : rows) {
            Objects.requireNonNull(row, "row");
            checkTenant(tenant, row.getTenantId());
            Object[] cells = {row.getId(),row.getTenantId(),row.getBatchId(),row.getSourceSystem(),
                    row.getSourceTable(),row.getSourceRecordKey(),row.getSourceBusinessKey(),
                    row.getSourcePayload(),row.getSourceChecksum(),row.getExtractedAt(),
                    row.getCreator(),row.getUpdater(),row.getCreateTime(),row.getUpdateTime()};
            System.arraycopy(cells, 0, values, offset, cells.length);
            offset += cells.length;
        }
        return insert(SOURCE_INSERT, SOURCE_TYPES, values, rows.size());
    }

    public int insertMappings(List<ExternalKeyMappingDO> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) return 0;
        checkSize(rows.size());
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Object[] values = new Object[rows.size() * MAPPING_TYPES.length];
        int offset = 0;
        for (var row : rows) {
            Objects.requireNonNull(row, "row");
            checkTenant(tenant, row.getTenantId());
            Object[] cells = {row.getId(),row.getTenantId(),row.getBatchId(),row.getSourceRecordId(),
                    row.getResultType(),row.getTargetContext(),row.getTargetObjectType(),row.getTargetTable(),
                    row.getTargetId(),row.getTargetRole(),row.getTargetSequence(),row.getCreator(),
                    row.getUpdater(),row.getCreateTime(),row.getUpdateTime()};
            System.arraycopy(cells, 0, values, offset, cells.length);
            offset += cells.length;
        }
        return insert(MAPPING_INSERT, MAPPING_TYPES, values, rows.size());
    }

    private int insert(String prefix, int[] rowTypes, Object[] values, int count) {
        String tuple = "(" + String.join(",", Collections.nCopies(rowTypes.length, "?")) + ",FALSE)";
        int[] types = new int[values.length];
        for (int row = 0; row < count; row++)
            System.arraycopy(rowTypes, 0, types, row * rowTypes.length, rowTypes.length);
        int affected = jdbc.update(prefix + String.join(",", Collections.nCopies(count, tuple)), values, types);
        if (affected != count) throw new IllegalStateException("Evidence insert row count mismatch");
        return affected;
    }
    private static void checkSize(int size) {
        if (size > MAX_ROWS) throw new IllegalArgumentException("Evidence insert exceeds 1000 rows");
    }
    private static void checkTenant(Long expected, Long actual) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Evidence insert tenant mismatch");
    }
}
