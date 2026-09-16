package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dp.plat.common.exception.BusinessException;
import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.mapper.LowCodeFormMapper;
import com.dp.plat.lowcode.service.impl.LowCodeFormServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LowCodeFormServiceImpl} 单元测试。
 *
 * <p>通过 Mock {@link LowCodeFormMapper}（注入到 ServiceImpl 的 baseMapper 字段）
 * 隔离数据库依赖，验证 create/update/delete/publish/archive/exportConfig/importConfig
 * 的业务逻辑与状态流转。</p>
 *
 * <p>注意：{@code id} 字段继承自 {@code BaseEntity}，Lombok {@code @Builder} 不覆盖
 * 父类字段，因此通过 {@code setId()} 设置而非 builder 链式调用。</p>
 */
@DisplayName("低代码表单配置 Service 单元测试")
class LowCodeFormServiceImplTest {

    private LowCodeFormMapper baseMapper;
    private ObjectMapper objectMapper;
    private LowCodeFormServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        baseMapper = mock(LowCodeFormMapper.class);
        service = new LowCodeFormServiceImpl(objectMapper);
        // ServiceImpl 的 baseMapper 是受保护字段，通过反射注入 Mock
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    @Test
    @DisplayName("create：新建表单配置应设置默认状态 DRAFT 与版本号 1")
    void create_shouldSetDefaultsAndSave() {
        LowCodeForm form = LowCodeForm.builder()
                .code("FORM_TEST")
                .name("测试表单")
                .formConfig("{\"fields\":[]}")
                .build();
        // code 不冲突
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(baseMapper.insert(any(LowCodeForm.class))).thenReturn(1);

        LowCodeForm result = service.create(form);

        assertEquals("DRAFT", result.getStatus());
        assertEquals(1, result.getVersion());
        verify(baseMapper).insert(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("create：编码重复时应抛出业务异常")
    void create_shouldThrowWhenCodeDuplicated() {
        LowCodeForm form = LowCodeForm.builder()
                .code("FORM_DUP")
                .name("重复表单")
                .formConfig("{}")
                .build();
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(form));
        assertTrue(ex.getMessage().contains("FORM_DUP"));
        verify(baseMapper, never()).insert(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("update：根据 ID 更新表单配置并保持乐观锁版本")
    void update_shouldUpdateAndKeepVersion() {
        LowCodeForm existing = new LowCodeForm();
        existing.setId(1L);
        existing.setCode("FORM_OLD");
        existing.setVersion(3);

        LowCodeForm toUpdate = LowCodeForm.builder()
                .code("FORM_OLD")
                .name("更新后名称")
                .formConfig("{\"fields\":[{\"name\":\"x\"}]}")
                .build();
        toUpdate.setId(1L);

        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(LowCodeForm.class))).thenReturn(1);

        LowCodeForm reloaded = new LowCodeForm();
        reloaded.setId(1L);
        reloaded.setName("更新后名称");
        // selectById 在 update 末尾再次调用以返回最新数据
        when(baseMapper.selectById(1L)).thenReturn(existing, reloaded);

        LowCodeForm result = service.update(toUpdate);

        // 传入 updateById 的版本号应与库中一致
        assertEquals(3, toUpdate.getVersion());
        assertNotNull(result);
        verify(baseMapper).updateById(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("update：记录不存在时应抛出业务异常")
    void update_shouldThrowWhenNotFound() {
        LowCodeForm toUpdate = LowCodeForm.builder().code("X").name("X").formConfig("{}").build();
        toUpdate.setId(99L);
        when(baseMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.update(toUpdate));
        verify(baseMapper, never()).updateById(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("delete：逻辑删除已存在的表单配置")
    void delete_shouldRemoveExisting() {
        LowCodeForm existing = new LowCodeForm();
        existing.setId(1L);
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(baseMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete：记录不存在时应抛出业务异常")
    void delete_shouldThrowWhenNotFound() {
        when(baseMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.delete(404L));
        verify(baseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("publish：草稿状态且有配置内容时发布成功")
    void publish_shouldSucceedFromDraftWithConfig() {
        LowCodeForm draft = LowCodeForm.builder()
                .code("FORM_PUB")
                .name("待发布")
                .formConfig("{\"fields\":[]}")
                .status("DRAFT")
                .version(1)
                .build();
        draft.setId(1L);
        when(baseMapper.selectById(1L)).thenReturn(draft);
        when(baseMapper.updateById(any(LowCodeForm.class))).thenReturn(1);

        service.publish(1L);

        assertEquals("PUBLISHED", draft.getStatus());
        verify(baseMapper).updateById(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("publish：非草稿状态时应抛出业务异常")
    void publish_shouldThrowWhenNotDraft() {
        LowCodeForm published = LowCodeForm.builder()
                .code("FORM_PUB")
                .name("已发布")
                .formConfig("{}")
                .status("PUBLISHED")
                .version(1)
                .build();
        published.setId(1L);
        when(baseMapper.selectById(1L)).thenReturn(published);

        assertThrows(BusinessException.class, () -> service.publish(1L));
        verify(baseMapper, never()).updateById(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("publish：配置内容为空时应抛出业务异常")
    void publish_shouldThrowWhenConfigEmpty() {
        LowCodeForm draft = LowCodeForm.builder()
                .code("FORM_PUB")
                .name("空配置")
                .status("DRAFT")
                .version(1)
                .build();
        draft.setId(1L);
        draft.setFormConfig(null);
        when(baseMapper.selectById(1L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.publish(1L));
    }

    @Test
    @DisplayName("archive：已发布状态时归档成功")
    void archive_shouldSucceedFromPublished() {
        LowCodeForm published = LowCodeForm.builder()
                .code("FORM_ARCH")
                .name("待归档")
                .formConfig("{}")
                .status("PUBLISHED")
                .version(1)
                .build();
        published.setId(1L);
        when(baseMapper.selectById(1L)).thenReturn(published);
        when(baseMapper.updateById(any(LowCodeForm.class))).thenReturn(1);

        service.archive(1L);

        assertEquals("ARCHIVED", published.getStatus());
        verify(baseMapper).updateById(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("archive：非已发布状态时应抛出业务异常")
    void archive_shouldThrowWhenNotPublished() {
        LowCodeForm draft = LowCodeForm.builder()
                .code("FORM_ARCH")
                .name("草稿")
                .status("DRAFT")
                .version(1)
                .build();
        draft.setId(1L);
        when(baseMapper.selectById(1L)).thenReturn(draft);

        assertThrows(BusinessException.class, () -> service.archive(1L));
        verify(baseMapper, never()).updateById(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("exportConfig：导出指定编码的表单配置为 JSON 字节数组")
    void exportConfig_shouldReturnJsonBytes() throws Exception {
        LowCodeForm form = LowCodeForm.builder()
                .code("FORM_EXP")
                .name("导出表单")
                .formConfig("{\"fields\":[]}")
                .status("PUBLISHED")
                .version(1)
                .build();
        form.setId(1L);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(form);

        byte[] data = service.exportConfig("FORM_EXP");

        assertNotNull(data);
        // 反序列化校验导出内容有效
        LowCodeForm parsed = objectMapper.readValue(new String(data, StandardCharsets.UTF_8), LowCodeForm.class);
        assertEquals("FORM_EXP", parsed.getCode());
        assertEquals("导出表单", parsed.getName());
    }

    @Test
    @DisplayName("exportConfig：编码不存在时应抛出业务异常")
    void exportConfig_shouldThrowWhenNotFound() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.exportConfig("NOT_EXIST"));
    }

    @Test
    @DisplayName("importConfig：导入 JSON 并在 code 冲突时自动追加后缀")
    void importConfig_shouldImportAndResolveCodeConflict() throws Exception {
        LowCodeForm source = LowCodeForm.builder()
                .code("FORM_IMP")
                .name("导入表单")
                .formConfig("{\"fields\":[]}")
                .status("PUBLISHED")
                .version(5)
                .build();
        String json = objectMapper.writeValueAsString(source);

        // code 已存在 → 触发后缀追加
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);
        when(baseMapper.insert(any(LowCodeForm.class))).thenReturn(1);

        LowCodeForm result = service.importConfig(json);

        // code 应被追加后缀
        assertNotEquals("FORM_IMP", result.getCode());
        assertTrue(result.getCode().startsWith("FORM_IMP_import"));
        // 导入后状态重置为 DRAFT，版本重置为 1，id 清空
        assertEquals("DRAFT", result.getStatus());
        assertEquals(1, result.getVersion());
        verify(baseMapper).insert(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("importConfig：JSON 缺少 code 时应抛出业务异常")
    void importConfig_shouldThrowWhenCodeMissing() {
        String json = "{\"name\":\"无编码表单\",\"formConfig\":\"{}\"}";
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.importConfig(json));
        verify(baseMapper, never()).insert(any(LowCodeForm.class));
    }

    @Test
    @DisplayName("importConfig：空 JSON 内容应抛出业务异常")
    void importConfig_shouldThrowWhenJsonBlank() {
        assertThrows(BusinessException.class, () -> service.importConfig(""));
        assertThrows(BusinessException.class, () -> service.importConfig(null));
    }

    @Test
    @DisplayName("getByCode：返回已发布状态的表单配置")
    void getByCode_shouldReturnPublishedForm() {
        LowCodeForm published = LowCodeForm.builder()
                .code("FORM_Q")
                .name("已发布")
                .formConfig("{}")
                .status("PUBLISHED")
                .build();
        published.setId(1L);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(published);

        LowCodeForm result = service.getByCode("FORM_Q");

        assertNotNull(result);
        assertEquals("FORM_Q", result.getCode());
        assertEquals("PUBLISHED", result.getStatus());
    }
}
