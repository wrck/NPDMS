package cn.iocoder.yudao.framework.mybatis.config;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class YudaoMybatisAutoConfigurationTest {

    @Test
    void jacksonInitializerMustNotBecomeGlobalTypeHandler() {
        Object initializer = new YudaoMybatisAutoConfiguration()
                .jacksonTypeHandler(List.of(JsonUtils.getObjectMapper()));

        assertFalse(initializer instanceof TypeHandler<?>);
    }
}
