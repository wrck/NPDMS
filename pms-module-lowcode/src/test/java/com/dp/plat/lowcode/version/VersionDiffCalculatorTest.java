package com.dp.plat.lowcode.version;

import com.dp.plat.lowcode.dto.VersionDiffDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 版本 Diff 计算器单元测试。
 */
@DisplayName("版本 Diff 计算器测试")
class VersionDiffCalculatorTest {

    private VersionDiffCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new VersionDiffCalculator(new ObjectMapper());
    }

    @Test
    @DisplayName("Diff — 字段值修改")
    void diff_modified() {
        String oldJson = "{\"name\":\"设备\",\"code\":\"device\"}";
        String newJson = "{\"name\":\"设备清单\",\"code\":\"device\"}";

        VersionDiffDTO result = calculator.diff(oldJson, newJson, 1, 2);

        assertEquals(1, result.getEntries().size());
        assertEquals("MODIFIED", result.getEntries().get(0).getChangeType());
        assertEquals("name", result.getEntries().get(0).getFieldPath());
        assertEquals("设备", result.getEntries().get(0).getOldValue());
        assertEquals("设备清单", result.getEntries().get(0).getNewValue());
    }

    @Test
    @DisplayName("Diff — 字段新增")
    void diff_added() {
        String oldJson = "{\"name\":\"设备\"}";
        String newJson = "{\"name\":\"设备\",\"code\":\"device\"}";

        VersionDiffDTO result = calculator.diff(oldJson, newJson, 1, 2);

        assertTrue(result.getEntries().stream()
                .anyMatch(e -> "ADDED".equals(e.getChangeType()) && "code".equals(e.getFieldPath())));
    }

    @Test
    @DisplayName("Diff — 字段删除")
    void diff_removed() {
        String oldJson = "{\"name\":\"设备\",\"code\":\"device\"}";
        String newJson = "{\"name\":\"设备\"}";

        VersionDiffDTO result = calculator.diff(oldJson, newJson, 1, 2);

        assertTrue(result.getEntries().stream()
                .anyMatch(e -> "REMOVED".equals(e.getChangeType()) && "code".equals(e.getFieldPath())));
    }

    @Test
    @DisplayName("Diff — 无变化返回空列表")
    void diff_noChange() {
        String json = "{\"name\":\"设备\",\"code\":\"device\"}";

        VersionDiffDTO result = calculator.diff(json, json, 1, 2);

        assertTrue(result.getEntries().isEmpty());
    }

    @Test
    @DisplayName("Diff — 嵌套对象字段修改")
    void diff_nested() {
        String oldJson = "{\"entity\":{\"name\":\"旧名\"},\"fields\":[]}";
        String newJson = "{\"entity\":{\"name\":\"新名\"},\"fields\":[]}";

        VersionDiffDTO result = calculator.diff(oldJson, newJson, 1, 2);

        assertEquals(1, result.getEntries().size());
        assertEquals("entity.name", result.getEntries().get(0).getFieldPath());
    }
}
