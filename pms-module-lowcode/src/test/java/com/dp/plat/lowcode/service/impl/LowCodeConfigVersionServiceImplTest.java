package com.dp.plat.lowcode.service.impl;

import com.dp.plat.lowcode.dto.VersionDiffDTO;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;
import com.dp.plat.lowcode.mapper.LowCodeConfigVersionMapper;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.dp.plat.lowcode.version.VersionDiffCalculator;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * 配置版本 Service 单元测试。
 */
@DisplayName("配置版本 Service 测试")
@ExtendWith(MockitoExtension.class)
class LowCodeConfigVersionServiceImplTest {

    @Mock
    private LowCodeConfigVersionMapper mapper;
    @Mock
    private VersionDiffCalculator diffCalculator;
    @Spy
    @InjectMocks
    private LowCodeConfigVersionServiceImpl service;

    @Test
    @DisplayName("创建快照 — 首次版本号为 1")
    void createSnapshot_firstVersion() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectList(any())).thenReturn(List.of());

        LowCodeConfigVersion result = service.createSnapshot(
                new LowCodeConfigVersionService.SnapshotContext(
                        "ENTITY", 1L, "device", "{\"name\":\"device\"}", "首次发布"));

        assertEquals(1, result.getVersion());
        assertEquals("ACTIVE", result.getStatus());
        verify(mapper).insert(any(LowCodeConfigVersion.class));
    }

    @Test
    @DisplayName("创建快照 — 后续版本号递增")
    void createSnapshot_incrementVersion() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LowCodeConfigVersion existing = LowCodeConfigVersion.builder().version(3).build();
        when(mapper.selectList(any())).thenReturn(List.of(existing));

        LowCodeConfigVersion result = service.createSnapshot(
                new LowCodeConfigVersionService.SnapshotContext(
                        "ENTITY", 1L, "device", "{}", "第二次发布"));

        assertEquals(4, result.getVersion());
    }

    @Test
    @DisplayName("查询版本历史 — 按版本号降序")
    void getVersionHistory() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LowCodeConfigVersion v1 = LowCodeConfigVersion.builder().version(1).build();
        LowCodeConfigVersion v2 = LowCodeConfigVersion.builder().version(2).build();
        when(mapper.selectList(any())).thenReturn(List.of(v2, v1));

        List<LowCodeConfigVersion> history = service.getVersionHistory("ENTITY", 1L);

        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("Diff 对比 — 委托给 VersionDiffCalculator")
    void diff() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LowCodeConfigVersion v1 = LowCodeConfigVersion.builder()
                .version(1).snapshot("{\"name\":\"old\"}").build();
        LowCodeConfigVersion v2 = LowCodeConfigVersion.builder()
                .version(2).snapshot("{\"name\":\"new\"}").build();
        when(mapper.selectOne(any())).thenReturn(v1, v2);
        VersionDiffDTO mockDiff = VersionDiffDTO.builder().fromVersion(1).toVersion(2).build();
        when(diffCalculator.diff(any(), any(), anyInt(), anyInt())).thenReturn(mockDiff);

        VersionDiffDTO result = service.diff("ENTITY", 1L, 1, 2);

        assertNotNull(result);
        verify(diffCalculator).diff("{\"name\":\"old\"}", "{\"name\":\"new\"}", 1, 2);
    }

    @Test
    @DisplayName("回滚 — 用历史快照生成新版本")
    void rollback() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LowCodeConfigVersion target = LowCodeConfigVersion.builder()
                .version(2).configCode("device").snapshot("{\"name\":\"target\"}").build();
        when(mapper.selectOne(any())).thenReturn(target);
        when(mapper.selectList(any())).thenReturn(List.of(
                LowCodeConfigVersion.builder().version(3).build()));

        LowCodeConfigVersion result = service.rollback("ENTITY", 1L, 2, "回滚测试");

        assertEquals(4, result.getVersion());
        assertEquals("{\"name\":\"target\"}", result.getSnapshot());
        // rollback 内部：getVersion 用 selectOne，createSnapshot 用 selectList（仅 1 次）
        verify(mapper, times(1)).selectList(any());
        verify(mapper).insert(any(LowCodeConfigVersion.class));
    }

    @Test
    @DisplayName("导出配置包 — 按环境过滤")
    void exportPackage() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LowCodeConfigVersion v1 = LowCodeConfigVersion.builder()
                .configCode("device").version(1).snapshot("{}").build();
        when(mapper.selectList(any())).thenReturn(List.of(v1));

        var pkg = service.exportPackage("DEV", List.of("device"));

        assertEquals("DEV", pkg.getSourceEnvironment());
        assertEquals(1, pkg.getItems().size());
    }
}
