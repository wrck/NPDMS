package com.dp.plat.lowcode.service.impl;

import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.dto.DdlResultDTO;
import com.dp.plat.lowcode.engine.DdlGenerator;
import com.dp.plat.lowcode.engine.ddl.DdlExecutionService;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.mapper.LowCodeEntityMapper;
import com.dp.plat.lowcode.mapper.LowCodeFieldMapper;
import com.dp.plat.lowcode.mapper.LowCodeRelationMapper;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 低代码实体 Service 单元测试。
 */
@DisplayName("低代码实体 Service 测试")
@ExtendWith(MockitoExtension.class)
class LowCodeEntityServiceImplTest {

    @Mock
    private LowCodeEntityMapper entityMapper;
    @Mock
    private LowCodeFieldMapper fieldMapper;
    @Mock
    private LowCodeRelationMapper relationMapper;
    @Mock
    private DdlGenerator ddlGenerator;
    @Mock
    private LowCodeConfigVersionService configVersionService;
    @Mock
    private DdlExecutionService ddlExecutionService;
    @Mock
    private ObjectMapper objectMapper;
    @Spy
    @InjectMocks
    private LowCodeEntityServiceImpl entityService;

    @Test
    @DisplayName("保存实体设计 — 持久化实体+字段+关联")
    void saveDesign_success() {
        ReflectionTestUtils.setField(entityService, "baseMapper", entityMapper);

        LowCodeEntity entity = LowCodeEntity.builder()
                .code("device").name("设备").tableName("pms_lc_device").build();
        LowCodeField field = LowCodeField.builder()
                .name("id").label("ID").fieldType("LONG").primaryKey(1).nullable(0).build();
        EntityDesignDTO design = new EntityDesignDTO();
        design.setEntity(entity);
        design.setFields(List.of(field));
        design.setRelations(List.of());

        doAnswer(inv -> {
            ((LowCodeEntity) inv.getArgument(0)).setId(1L);
            return true;
        }).when(entityService).save(any(LowCodeEntity.class));

        LowCodeEntity result = entityService.saveDesign(design);

        assertNotNull(result.getId());
        verify(fieldMapper).insert(any(LowCodeField.class));
    }

    @Test
    @DisplayName("查询实体设计 — 返回实体+字段+关联")
    void getDesign_success() {
        ReflectionTestUtils.setField(entityService, "baseMapper", entityMapper);

        LowCodeEntity entity = LowCodeEntity.builder()
                .code("device").tableName("pms_lc_device").build();
        entity.setId(1L);
        LowCodeField field = LowCodeField.builder()
                .entityId(1L).name("id").fieldType("LONG").primaryKey(1).build();

        doReturn(entity).when(entityService).getById(1L);
        when(fieldMapper.selectList(any())).thenReturn(List.of(field));
        when(relationMapper.selectList(any())).thenReturn(List.of());

        EntityDesignDTO design = entityService.getDesign(1L);

        assertEquals("device", design.getEntity().getCode());
        assertEquals(1, design.getFields().size());
    }

    @Test
    @DisplayName("生成 DDL — 含 CREATE TABLE")
    void generateDdl_success() {
        ReflectionTestUtils.setField(entityService, "baseMapper", entityMapper);

        LowCodeEntity entity = LowCodeEntity.builder()
                .code("device").tableName("pms_lc_device").build();
        entity.setId(1L);
        LowCodeField idField = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).nullable(0).build();

        doReturn(entity).when(entityService).getById(1L);
        when(fieldMapper.selectList(any())).thenReturn(List.of(idField));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(ddlGenerator.generateCreateTable(any(), any(), any(), any()))
                .thenReturn("CREATE TABLE `pms_lc_device` (\n  `id` BIGINT NOT NULL\n)");

        DdlResultDTO result = entityService.generateDdl(1L);

        assertEquals("pms_lc_device", result.getTableName());
        assertFalse(result.getDdlStatements().isEmpty());
        assertTrue(result.getDdlStatements().get(0).contains("CREATE TABLE"));
    }

    @Test
    @DisplayName("发布实体 — DRAFT → PUBLISHED + 版本快照")
    void publish_success() throws Exception {
        ReflectionTestUtils.setField(entityService, "baseMapper", entityMapper);

        LowCodeEntity entity = LowCodeEntity.builder()
                .code("device").tableName("pms_lc_device").status("DRAFT").build();
        entity.setId(1L);
        LowCodeField field = LowCodeField.builder()
                .name("id").fieldType("LONG").primaryKey(1).build();

        doReturn(entity).when(entityService).getById(1L);
        when(fieldMapper.selectList(any())).thenReturn(List.of(field));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doReturn(true).when(entityService).updateById(any());

        LowCodeEntity result = entityService.publish(1L, "首次发布");

        assertEquals("PUBLISHED", result.getStatus());
        verify(ddlExecutionService).executeCreate(1L, false);
        verify(configVersionService)
                .createSnapshot(any(LowCodeConfigVersionService.SnapshotContext.class));
    }

    @Test
    @DisplayName("校验表名唯一 — 已存在返回 true")
    void isTableNameExists_exists() {
        ReflectionTestUtils.setField(entityService, "baseMapper", entityMapper);

        LowCodeEntity existing = LowCodeEntity.builder()
                .tableName("pms_lc_device").build();
        existing.setId(2L);
        when(entityMapper.selectOne(any())).thenReturn(existing);

        assertTrue(entityService.isTableNameExists("pms_lc_device", 1L));
    }

    @Test
    @DisplayName("校验表名唯一 — 排除自身返回 false")
    void isTableNameExists_excludeSelf() {
        ReflectionTestUtils.setField(entityService, "baseMapper", entityMapper);

        // 排除自身后查询无结果（其余无同表名实体）
        when(entityMapper.selectOne(any())).thenReturn(null);

        assertFalse(entityService.isTableNameExists("pms_lc_device", 1L));
    }
}
