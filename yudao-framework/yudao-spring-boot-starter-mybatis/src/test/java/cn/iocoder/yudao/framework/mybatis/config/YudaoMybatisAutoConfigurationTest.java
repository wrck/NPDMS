package cn.iocoder.yudao.framework.mybatis.config;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class YudaoMybatisAutoConfigurationTest {

    @Test
    void cachedSqlNeverSharesMutableTenantPredicatesAcrossCalls() throws Exception {
        new YudaoMybatisAutoConfiguration();
        String sql = "SELECT id FROM int_sync_binding WHERE task_id = ?";
        var first = (net.sf.jsqlparser.statement.select.PlainSelect)
                com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(sql);
        first.setWhere(net.sf.jsqlparser.parser.CCJSqlParserUtil.parseCondExpression("tenant_id = 1"));
        var second = (net.sf.jsqlparser.statement.select.PlainSelect)
                com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(sql);
        assertNotSame(first, second);
        assertEquals("task_id = ?", second.getWhere().toString());
        second.setWhere(net.sf.jsqlparser.parser.CCJSqlParserUtil.parseCondExpression("tenant_id = 2"));
        var third = (net.sf.jsqlparser.statement.select.PlainSelect)
                com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(sql);
        assertEquals("task_id = ?", third.getWhere().toString());
    }

    @Test
    void jacksonInitializerMustNotBecomeGlobalTypeHandler() {
        Object initializer = new YudaoMybatisAutoConfiguration()
                .jacksonTypeHandler(List.of(JsonUtils.getObjectMapper()));

        assertFalse(initializer instanceof TypeHandler<?>);
    }
}
