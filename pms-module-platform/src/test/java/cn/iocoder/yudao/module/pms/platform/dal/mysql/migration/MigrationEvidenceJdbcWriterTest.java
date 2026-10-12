package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationSourceRecordDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MigrationEvidenceJdbcWriterTest {
    private static final LocalDateTime NOW=LocalDateTime.of(2026,10,10,8,0,0,123000000);
    @BeforeEach void tenant(){TenantContextHolder.setTenantId(7L);}
    @AfterEach void clear(){TenantContextHolder.clear();}

    @Test void bindsSourceRowsWithFixedColumnOrderAndTypes() {
        try(var mocks=mockConstruction(JdbcTemplate.class,(jdbc,context)->
                when(jdbc.update(anyString(),any(Object[].class),any(int[].class))).thenReturn(2))) {
            var writer=new MigrationEvidenceJdbcWriter(mock(DataSource.class));
            var one=source(11L,7L);var two=source(12L,7L);two.setSourceBusinessKey(null);
            assertEquals(2,writer.insertSources(List.of(one,two)));
            var sql=ArgumentCaptor.forClass(String.class);var values=ArgumentCaptor.forClass(Object[].class);var types=ArgumentCaptor.forClass(int[].class);
            verify(mocks.constructed().getFirst()).update(sql.capture(),values.capture(),types.capture());
            assertTrue(sql.getValue().startsWith("INSERT INTO plt_migration_source_record "));
            assertFalse(sql.getValue().contains("sensitive-value"));
            assertEquals(28,values.getValue().length);assertEquals(28,types.getValue().length);
            assertArrayEquals(new Object[]{11L,7L,31L,"ERP","source_table","sensitive-value", "business", "{\"value\":null}","a".repeat(64),NOW,"0","0",NOW,NOW},Arrays.copyOfRange(values.getValue(),0,14));
            assertEquals(12L,values.getValue()[14]);assertNull(values.getValue()[20]);
            assertEquals(Types.BIGINT,types.getValue()[1]);assertEquals(Types.VARCHAR,types.getValue()[7]);assertEquals(Types.TIMESTAMP,types.getValue()[9]);
            assertEquals(2,sql.getValue().split("FALSE",-1).length-1);
        }
    }
    @Test void bindsMappedAndRetainedRowsWithoutSerializingOrChangingFacts() {
        try(var mocks=mockConstruction(JdbcTemplate.class,(jdbc,context)->
                when(jdbc.update(anyString(),any(Object[].class),any(int[].class))).thenReturn(2))) {
            var writer=new MigrationEvidenceJdbcWriter(mock(DataSource.class));
            var retained=mapping(21L,7L);var mapped=mapping(22L,7L);
            mapped.setResultType("MAPPED");mapped.setTargetContext("AST");mapped.setTargetObjectType("DEVICE");mapped.setTargetTable("ast_device");mapped.setTargetId(41L);mapped.setTargetRole("PRIMARY");mapped.setTargetSequence(0);
            assertEquals(2,writer.insertMappings(List.of(retained,mapped)));
            var values=ArgumentCaptor.forClass(Object[].class);var types=ArgumentCaptor.forClass(int[].class);
            verify(mocks.constructed().getFirst()).update(startsWith("INSERT INTO plt_external_key_mapping "),values.capture(),types.capture());
            assertEquals(30,values.getValue().length);assertEquals(30,types.getValue().length);
            assertArrayEquals(new Object[]{21L,7L,31L,51L,"RETAINED",null,null,null,null,null,null,"0","0",NOW,NOW},Arrays.copyOfRange(values.getValue(),0,15));
            assertEquals(41L,values.getValue()[23]);assertEquals(0,values.getValue()[25]);assertEquals(Types.INTEGER,types.getValue()[25]);
        }
    }
    @Test void rejectsAllMixedTenantRowsBeforeAnyDatabaseAccess() {
        try(var mocks=mockConstruction(JdbcTemplate.class)) {
            var writer=new MigrationEvidenceJdbcWriter(mock(DataSource.class));
            assertThrows(IllegalArgumentException.class,()->writer.insertSources(List.of(source(11L,7L),source(12L,8L))));
            assertThrows(IllegalArgumentException.class,()->writer.insertMappings(List.of(mapping(21L,7L),mapping(22L,8L))));
            assertThrows(IllegalArgumentException.class,()->writer.insertSources(List.of(source(11L,null))));
            verifyNoInteractions(mocks.constructed().getFirst());
        }
    }
    @Test void missingTenantCannotWrite() {
        TenantContextHolder.clear();
        try(var mocks=mockConstruction(JdbcTemplate.class)) {
            var writer=new MigrationEvidenceJdbcWriter(mock(DataSource.class));
            assertThrows(RuntimeException.class,()->writer.insertSources(List.of(source(11L,7L))));
            assertThrows(RuntimeException.class,()->writer.insertMappings(List.of(mapping(21L,7L))));
            verifyNoInteractions(mocks.constructed().getFirst());
        }
    }
    @Test void emptyRowsAreNoOpAndOversizedRowsCannotWrite() {
        try(var mocks=mockConstruction(JdbcTemplate.class)) {
            var writer=new MigrationEvidenceJdbcWriter(mock(DataSource.class));
            assertEquals(0,writer.insertSources(List.of()));assertEquals(0,writer.insertMappings(List.of()));
            assertThrows(IllegalArgumentException.class,()->writer.insertSources(Collections.nCopies(1001,source(11L,7L))));
            assertThrows(IllegalArgumentException.class,()->writer.insertMappings(Collections.nCopies(1001,mapping(21L,7L))));
            verifyNoInteractions(mocks.constructed().getFirst());
        }
    }
    @Test void rejectsPartialRowCountInsteadOfTreatingItAsSuccess() {
        try(var mocks=mockConstruction(JdbcTemplate.class,(jdbc,context)->
                when(jdbc.update(anyString(),any(Object[].class),any(int[].class))).thenReturn(0))) {
            var writer=new MigrationEvidenceJdbcWriter(mock(DataSource.class));
            assertThrows(IllegalStateException.class,()->writer.insertSources(List.of(source(11L,7L))));
            assertThrows(IllegalStateException.class,()->writer.insertMappings(List.of(mapping(21L,7L))));
        }
    }
    @Test void requiresExistingTransaction() {
        var annotation=MigrationEvidenceJdbcWriter.class.getAnnotation(Transactional.class);
        assertNotNull(annotation);assertEquals(Propagation.MANDATORY,annotation.propagation());
    }
    private static MigrationSourceRecordDO source(Long id,Long tenant) {
        var row=new MigrationSourceRecordDO();row.setId(id);row.setTenantId(tenant);row.setBatchId(31L);
        row.setSourceSystem("ERP");row.setSourceTable("source_table");row.setSourceRecordKey("sensitive-value");row.setSourceBusinessKey("business");
        row.setSourcePayload("{\"value\":null}");row.setSourceChecksum("a".repeat(64));row.setExtractedAt(NOW);
        row.setCreator("0");row.setUpdater("0");row.setCreateTime(NOW);row.setUpdateTime(NOW);return row;
    }
    private static ExternalKeyMappingDO mapping(Long id,Long tenant) {
        var row=new ExternalKeyMappingDO();row.setId(id);row.setTenantId(tenant);row.setBatchId(31L);row.setSourceRecordId(51L);row.setResultType("RETAINED");
        row.setCreator("0");row.setUpdater("0");row.setCreateTime(NOW);row.setUpdateTime(NOW);return row;
    }
}
