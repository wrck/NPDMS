package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Actual inherited page against real MVC/services/SQL; authentication/project/storage ports remain fixtures. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DirectBusinessBrowserMySqlTest extends DirectBusinessCrudMySqlTest {
    @Test @EnabledIfSystemProperty(named="default.delivery.browser",matches="true")
    void inheritedPagesCreateEditUploadCollectAndDelete() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
        var context=fixture.context;
        var http=MockMvcBuilders.standaloneSetup(context.getBean(NoteController.class),context.getBean(OtherController.class),context.getBean(SpecialController.class),context.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.business.ProjectBusinessDeliveryController.class))
                .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new BusinessModelContractAdvice(),new cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler("direct-business",mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class))).build();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",27462),0);
        server.createContext("/",exchange->{
            try {
                fixture.login(7,880001);byte[] content;int status=200;
                if(exchange.getRequestURI().getPath().equals("/fixture/evidence")) {
                    var result=new LinkedHashMap<String,Object>();
                    for(String table:List.of("it_direct_note","it_direct_other","it_direct_special","plt_delivery_material"))
                        result.put(table,fixture.runtime.jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"));
                    content=json.writeValueAsBytes(result);
                } else {
                    var request=DefaultBusinessDeliveryBrowserMySqlTest.request(exchange.getRequestMethod(),exchange.getRequestURI().toString(),exchange.getRequestHeaders().getFirst("Content-Type"),exchange.getRequestBody().readAllBytes());
                    exchange.getRequestHeaders().forEach((name,values)->values.forEach(value->request.header(name,value)));
                    var response=http.perform(request).andReturn().getResponse();status=response.getStatus();content=response.getContentAsByteArray();
                }
                exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.sendResponseHeaders(status,content.length);exchange.getResponseBody().write(content);
            } catch(Exception failure) {
                failure.printStackTrace();byte[] content=json.writeValueAsBytes(Map.of("code",500,"msg",Objects.toString(failure.getMessage(),failure.getClass().getSimpleName())));
                exchange.sendResponseHeaders(500,content.length);exchange.getResponseBody().write(content);
            } finally {exchange.close();cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        });server.start();
        try {
            var repo=Path.of("").toAbsolutePath();while(repo!=null&&!Files.isDirectory(repo.resolve("scripts/tests")))repo=repo.getParent();
            var output=repo.resolve(".run/direct-business-browser/process.log");Files.createDirectories(output.getParent());
            var process=new ProcessBuilder("python3",repo.resolve("scripts/tests/run_direct_business_browser.py").toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Browser timed out");}
            System.out.println(Files.readString(output));assertEquals(0,process.exitValue(),"See direct business browser evidence");
        } finally {server.stop(0);}
    }
}
