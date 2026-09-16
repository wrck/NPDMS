package com.dp.plat.lowcode.engine.ddl;

import com.dp.plat.lowcode.engine.DdlGenerator;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeRelation;
import com.dp.plat.lowcode.mapper.LowCodeEntityMapper;
import com.dp.plat.lowcode.mapper.LowCodeFieldMapper;
import com.dp.plat.lowcode.mapper.LowCodeRelationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DDL 执行服务测试")
class DdlExecutionServiceImplTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DdlGenerator ddlGenerator;
    @Mock private LowCodeEntityMapper entityMapper;
    @Mock private LowCodeFieldMapper fieldMapper;
    @Mock private LowCodeRelationMapper relationMapper;
    @Mock private DdlExecutionLogMapper executionLogMapper;
    @Mock private DdlBackupMapper backupMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private DdlExecutionServiceImpl service;

    private LowCodeEntity entity;

    @BeforeEach
    void setUp() {
        // 使用反射注入 ObjectMapper（因为 @RequiredArgsConstructor 期望它作为依赖）
        service = new DdlExecutionServiceImpl(jdbcTemplate, ddlGenerator, entityMapper,
                fieldMapper, relationMapper, executionLogMapper, backupMapper, objectMapper);

        entity = LowCodeEntity.builder()
                .code("order")
                .name("订单")
                .tableName("pms_lc_order")
                .status("DRAFT")
                .build();
        entity.setId(1L);
    }

    @Test
    @DisplayName("validateBeforeExecution — 合法 DDL 通过")
    void testValidateLegalDdl() {
        assertDoesNotThrow(() -> service.validateBeforeExecution(
                "CREATE TABLE `pms_lc_order` (id BIGINT PRIMARY KEY)"));
    }

    @Test
    @DisplayName("validateBeforeExecution — DROP TABLE 被禁止")
    void testValidateDropTableForbidden() {
        assertThrows(DdlSecurityException.class, () -> service.validateBeforeExecution(
                "DROP TABLE `pms_lc_order`"));
    }

    @Test
    @DisplayName("validateBeforeExecution — TRUNCATE 被禁止")
    void testValidateTruncateForbidden() {
        assertThrows(DdlSecurityException.class, () -> service.validateBeforeExecution(
                "TRUNCATE TABLE `pms_lc_order`"));
    }

    @Test
    @DisplayName("validateBeforeExecution — 空 SQL 抛异常")
    void testValidateEmptySql() {
        assertThrows(DdlSecurityException.class, () -> service.validateBeforeExecution(""));
    }

    @Test
    @DisplayName("tableExists — 表存在返回 true")
    void testTableExistsTrue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1);
        assertTrue(service.tableExists("pms_lc_order"));
    }

    @Test
    @DisplayName("executeCreate — 表不存在时执行 CREATE TABLE")
    void testExecuteCreateWhenTableNotExists() {
        when(entityMapper.selectById(1L)).thenReturn(entity);
        when(fieldMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(relationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(ddlGenerator.generateCreateTable(any(), any(), any(), any())).thenReturn(
                "CREATE TABLE `pms_lc_order` (id BIGINT PRIMARY KEY)");

        List<String> result = service.executeCreate(1L, false);

        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("CREATE TABLE"));
        verify(jdbcTemplate).execute(anyString());
        verify(executionLogMapper).insert(any(DdlExecutionLog.class));
    }

    @Test
    @DisplayName("executeCreate — 表已存在时跳过")
    void testExecuteCreateWhenTableExists() {
        when(entityMapper.selectById(1L)).thenReturn(entity);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);

        List<String> result = service.executeCreate(1L, false);

        assertEquals(0, result.size());
        verify(jdbcTemplate, org.mockito.Mockito.never()).execute(anyString());
    }

    @Test
    @DisplayName("executeCreate — 多对多中间表自动创建")
    void testExecuteCreateWithJunctionTable() {
        LowCodeRelation relation = LowCodeRelation.builder()
                .fromEntityId(1L)
                .toEntityId(2L)
                .relationType("MANY_TO_MANY")
                .fromFieldName("order_id")
                .toFieldName("product_id")
                .junctionTable("pms_lc_order_product")
                .onDelete("CASCADE")
                .build();
        when(entityMapper.selectById(1L)).thenReturn(entity);
        when(fieldMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(ddlGenerator.generateCreateTable(any(), any(), any(), any())).thenReturn(
                "CREATE TABLE `pms_lc_order` (id BIGINT PRIMARY KEY)");
        when(ddlGenerator.generateJunctionTable(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(
                "CREATE TABLE `pms_lc_order_product` (id BIGINT PRIMARY KEY)");

        List<String> result = service.executeCreate(1L, false);

        assertEquals(2, result.size());
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).execute(anyString());
    }
}
