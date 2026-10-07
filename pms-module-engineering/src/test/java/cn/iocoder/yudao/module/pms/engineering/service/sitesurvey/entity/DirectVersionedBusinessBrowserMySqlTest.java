package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.business.VersionedProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelContractAdvice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Root-written browser test. Actual current/revision/extension SQL and inherited APIs; auth/project/layout ports are fixtures. */
@EnabledIfSystemProperty(named="npdms.survey.mysql.optIn",matches="true")
class DirectVersionedBusinessBrowserMySqlTest extends DirectVersionedBusinessTest {
    @RestController @RequestMapping("/api/v1/pms/version-notes")
    public static class BrowserController extends VersionedProjectBusinessController<NoteService,Note>{}
    @Test @EnabledIfSystemProperty(named="npdms.version.browser",matches="true")
    void browserDefaultVersionedBusiness() throws Exception {
        ctx.registerBean(BrowserController.class);
        org.mockito.Mockito.when(versionDeliveries.list(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(),0L));
        org.mockito.Mockito.when(versionDeliveries.completion(org.mockito.ArgumentMatchers.any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Completion(false,null));
        var converter=new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(
                tools.jackson.databind.json.JsonMapper.builder().addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(ctx.getBean(BrowserController.class))
                .setMessageConverters(converter).setControllerAdvice(new BusinessModelContractAdvice()).build();
        var extension=ctx.getBean(EntityExtensionApi.class).publishDefinition(1L,"IT","versionNote",List.of(
                new EntityExtensionApi.Definition("flag","扩展标志",EntityField.Type.BOOLEAN,false,null,List.of()),
                new EntityExtensionApi.Definition("memo","扩展备注",EntityField.Type.TEXT,false,100,List.of())),new EntityActor(1L,9L,"browser-config"));
        var json=tools.jackson.databind.json.JsonMapper.builder().build();var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",27462),0);
        server.createContext("/",exchange->{
            try{
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);login();byte[] content;int status=200;
                if(exchange.getRequestURI().getPath().equals("/fixture/context"))content=json.writeValueAsBytes(Map.of("definitionId",extension.id().toString()));
                else if(exchange.getRequestURI().getPath().equals("/fixture/evidence")){
                    var result=new LinkedHashMap<String,Object>();for(String table:List.of("it_version_note","it_version_note_revision","plt_entity_extension_value"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"));content=json.writeValueAsBytes(result);
                }else{
                    var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(exchange.getRequestMethod()),exchange.getRequestURI()).content(exchange.getRequestBody().readAllBytes());
                    exchange.getRequestHeaders().forEach((name,values)->values.forEach(value->request.header(name,value)));
                    var response=mvc.perform(request).andReturn().getResponse();status=response.getStatus();content=response.getContentAsByteArray();
                }
                exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.sendResponseHeaders(status,content.length);exchange.getResponseBody().write(content);
            }catch(Exception error){error.printStackTrace();byte[] content=json.writeValueAsBytes(Map.of("code",500,"msg",Objects.toString(error.getMessage(),error.getClass().getSimpleName())));exchange.sendResponseHeaders(500,content.length);exchange.getResponseBody().write(content);}
            finally{exchange.close();cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        });server.start();
        try{
            var repo=Path.of("").toAbsolutePath();while(repo!=null && !Files.isDirectory(repo.resolve("scripts/tests")))repo=repo.getParent();
            var log=repo.resolve(".run/direct-version-browser/process.log");Files.createDirectories(log.getParent());
            var process=new ProcessBuilder("python3",repo.resolve("scripts/tests/run_direct_business_version_browser.py").toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!process.waitFor(240,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Version browser timeout");}
            System.out.println(Files.readString(log));assertEquals(0,process.exitValue(),"See direct version browser evidence");
        }finally{server.stop(0);}
    }
}
