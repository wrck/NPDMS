package cn.iocoder.yudao.module.system.framework.desensitize;

import cn.iocoder.yudao.framework.desensitize.core.regex.annotation.EmailDesensitize;
import cn.iocoder.yudao.framework.desensitize.core.slider.annotation.MobileDesensitize;
import cn.iocoder.yudao.framework.desensitize.core.slider.annotation.PasswordDesensitize;
import lombok.Data;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏框架的单元测试类
 *
 * 纯单元测试，不依赖 Spring 容器，直接使用 Jackson 序列化验证脱敏效果
 */
public class DesensitizeTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 带脱敏注解的测试 DTO
     */
    @Data
    public static class DesensitizeDTO {

        /**
         * 手机号：默认前缀保留 3 位，后缀保留 4 位
         * 例如：13812345678 → 138****5678
         */
        @MobileDesensitize
        private String mobile;

        /**
         * 密码：默认前后缀均保留 0 位，全部替换为 *
         * 例如：password → ********
         */
        @PasswordDesensitize
        private String password;

        /**
         * 邮箱：正则脱敏，首字符保留，其余用户名部分替换为 ****
         * 例如：example@gmail.com → e****@gmail.com
         */
        @EmailDesensitize
        private String email;

    }

    @Test
    public void testMobileDesensitize() throws Exception {
        // 准备参数
        DesensitizeDTO dto = new DesensitizeDTO();
        dto.setMobile("13812345678");

        // 调用：序列化
        String json = jsonMapper.writeValueAsString(dto);
        // 断言：13812345678 → 138****5678
        assertTrue(json.contains("138****5678"), "手机号脱敏结果不符合预期: " + json);
        assertTrue(!json.contains("13812345678"), "手机号脱敏后不应包含原始值: " + json);
    }

    @Test
    public void testPasswordDesensitize() throws Exception {
        // 准备参数
        DesensitizeDTO dto = new DesensitizeDTO();
        dto.setPassword("password");

        // 调用：序列化
        String json = jsonMapper.writeValueAsString(dto);
        // 断言：password → ********（长度 8，全部替换为 *）
        assertTrue(json.contains("\"password\":\"********\""), "密码脱敏结果不符合预期: " + json);
        assertTrue(!json.contains("\"password\":\"password\""), "密码脱敏后不应包含原始值: " + json);
    }

    @Test
    public void testEmailDesensitize() throws Exception {
        // 准备参数
        DesensitizeDTO dto = new DesensitizeDTO();
        dto.setEmail("example@gmail.com");

        // 调用：序列化
        String json = jsonMapper.writeValueAsString(dto);
        // 断言：example@gmail.com → e****@gmail.com
        assertTrue(json.contains("e****@gmail.com"), "邮箱脱敏结果不符合预期: " + json);
        assertTrue(!json.contains("example@gmail.com"), "邮箱脱敏后不应包含原始值: " + json);
    }

    @Test
    public void testAllDesensitize() throws Exception {
        // 准备参数
        DesensitizeDTO dto = new DesensitizeDTO();
        dto.setMobile("13812345678");
        dto.setPassword("password");
        dto.setEmail("example@gmail.com");

        // 调用：序列化
        String json = jsonMapper.writeValueAsString(dto);
        // 断言：三个字段同时脱敏
        assertTrue(json.contains("138****5678"), "手机号脱敏结果不符合预期: " + json);
        assertTrue(json.contains("\"password\":\"********\""), "密码脱敏结果不符合预期: " + json);
        assertTrue(json.contains("e****@gmail.com"), "邮箱脱敏结果不符合预期: " + json);
    }

    @Test
    public void testMobileDesensitize_exactValue() throws Exception {
        // 准备参数：验证脱敏后字段的精确值
        DesensitizeDTO dto = new DesensitizeDTO();
        dto.setMobile("13812345678");

        // 调用：序列化后反序列化对比
        String json = jsonMapper.writeValueAsString(dto);
        // 断言：JSON 中包含精确的脱敏手机号（其余字段为 null 也会被序列化）
        assertEquals("{\"email\":null,\"mobile\":\"138****5678\",\"password\":null}", json);
    }

}
