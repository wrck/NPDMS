package com.dp.plat.lowcode.engine;

import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.engine.trigger.CrudTriggerExecutor;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.dp.plat.lowcode.service.LowCodeTriggerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 动态实体数据服务单元测试。
 */
@DisplayName("动态实体数据服务测试")
@ExtendWith(MockitoExtension.class)
class DynamicEntityDataServiceTest {

    @Mock
    private LowCodeEntityService entityService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private LowCodeTriggerService triggerService;
    @Mock
    private CrudTriggerExecutor crudTriggerExecutor;
    @InjectMocks
    private DynamicEntityDataService dataService;

    private LowCodeEntity buildEntity() {
        LowCodeEntity entity = LowCodeEntity.builder()
                .code("device").tableName("pms_lc_device").build();
        entity.setId(1L);
        return entity;
    }

    private List<LowCodeField> buildFields() {
        return List.of(
                LowCodeField.builder().name("id").fieldType("LONG").primaryKey(1).build(),
                LowCodeField.builder().name("device_name").fieldType("STRING").build());
    }

    private EntityDesignDTO buildDesign() {
        EntityDesignDTO dto = new EntityDesignDTO();
        dto.setEntity(buildEntity());
        dto.setFields(buildFields());
        dto.setRelations(List.of());
        return dto;
    }

    @Test
    @DisplayName("列表查询 — 返回分页结果")
    void list_success() {
        when(entityService.getOne(any())).thenReturn(buildEntity());
        when(entityService.getDesign(any())).thenReturn(buildDesign());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("id", 1, "device_name", "Router")));

        Map<String, Object> result = dataService.list("device", 1, 10, null);

        assertNotNull(result);
        assertEquals(5L, result.get("total"));
    }

    @Test
    @DisplayName("查询单条 — 返回记录")
    void getById_success() {
        when(entityService.getOne(any())).thenReturn(buildEntity());
        when(entityService.getDesign(any())).thenReturn(buildDesign());
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(
                Map.of("id", 1, "device_name", "Router"));

        Map<String, Object> result = dataService.getById("device", 1L);

        assertEquals(1, result.get("id"));
    }

    @Test
    @DisplayName("新增 — 过滤非法字段后插入")
    void create_success() {
        when(entityService.getOne(any())).thenReturn(buildEntity());
        when(entityService.getDesign(any())).thenReturn(buildDesign());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class)))
                .thenReturn(10L);

        Long id = dataService.create("device", Map.of("device_name", "Switch", "hacked_field", "x"));

        assertEquals(10L, id);
        verify(jdbcTemplate).update(contains("INSERT INTO"), any(Object[].class));
    }

    @Test
    @DisplayName("更新 — 过滤 id 和非法字段")
    void update_success() {
        when(entityService.getOne(any())).thenReturn(buildEntity());
        when(entityService.getDesign(any())).thenReturn(buildDesign());

        dataService.update("device", 1L, Map.of("device_name", "Updated", "id", 999));

        verify(jdbcTemplate).update(contains("UPDATE"), any(Object[].class));
    }

    @Test
    @DisplayName("删除 — 执行 DELETE")
    void delete_success() {
        when(entityService.getOne(any())).thenReturn(buildEntity());
        when(entityService.getDesign(any())).thenReturn(buildDesign());

        dataService.delete("device", 1L);

        verify(jdbcTemplate).update(contains("DELETE FROM"), eq(1L));
    }
}
