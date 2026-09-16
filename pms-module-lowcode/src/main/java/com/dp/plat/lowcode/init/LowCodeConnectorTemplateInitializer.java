package com.dp.plat.lowcode.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.entity.LowCodeConnector;
import com.dp.plat.lowcode.service.LowCodeConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 预置连接器模板初始化器：D365 / FP / OA 3 个 REST 连接器配置。
 *
 * <p>仅预置配置（无凭据），凭据由用户填写。</p>
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class LowCodeConnectorTemplateInitializer implements CommandLineRunner {

    private final LowCodeConnectorService connectorService;

    @Override
    public void run(String... args) {
        initD365Connector();
        initFpConnector();
        initOaConnector();
    }

    private void initD365Connector() {
        if (connectorService.count(new LambdaQueryWrapper<LowCodeConnector>()
                .eq(LowCodeConnector::getCode, "d365_connector")) > 0) {
            return;
        }
        String config = "{\"url\":\"https://d365.example.com/api\",\"method\":\"GET\",\"auth\":{\"type\":\"BEARER\"},\"retry\":{\"maxAttempts\":3,\"waitMillis\":500}}";
        connectorService.save(LowCodeConnector.builder()
                .code("d365_connector").name("D365 REST 连接器")
                .description("D365 ERP 集成，Bearer 认证")
                .type("REST").config(config).bizType("D365").build());
        log.info("预置连接器模板: d365_connector");
    }

    private void initFpConnector() {
        if (connectorService.count(new LambdaQueryWrapper<LowCodeConnector>()
                .eq(LowCodeConnector::getCode, "fp_connector")) > 0) {
            return;
        }
        String config = "{\"url\":\"https://fp.example.com/api\",\"method\":\"GET\",\"auth\":{\"type\":\"BASIC\"},\"retry\":{\"maxAttempts\":3,\"waitMillis\":500}}";
        connectorService.save(LowCodeConnector.builder()
                .code("fp_connector").name("FP REST 连接器")
                .description("FP 系统集成，Basic 认证")
                .type("REST").config(config).bizType("FP").build());
        log.info("预置连接器模板: fp_connector");
    }

    private void initOaConnector() {
        if (connectorService.count(new LambdaQueryWrapper<LowCodeConnector>()
                .eq(LowCodeConnector::getCode, "oa_connector")) > 0) {
            return;
        }
        String config = "{\"url\":\"https://oa.example.com/api\",\"method\":\"GET\",\"auth\":{\"type\":\"API_KEY\",\"headerName\":\"X-API-Key\"},\"retry\":{\"maxAttempts\":3,\"waitMillis\":500}}";
        connectorService.save(LowCodeConnector.builder()
                .code("oa_connector").name("OA REST 连接器")
                .description("OA 系统集成，API Key 认证")
                .type("REST").config(config).bizType("OA").build());
        log.info("预置连接器模板: oa_connector");
    }
}
