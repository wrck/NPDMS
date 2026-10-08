package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Chromium renders the actual default Host and collection page; HTTP is transport to production MVC/services/MySQL. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DefaultBusinessDeliveryBrowserMySqlTest extends DefaultBusinessDeliveryMySqlTest {
    @Test @EnabledIfSystemProperty(named="default.delivery.browser",matches="true")
    void browserTwoDefaultBusinessPagesAndCollection() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
        var http=MockMvcBuilders.standaloneSetup(context.getBean(DefaultBusinessDeliveryController.class),runtime.controller)
                .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new BusinessModelContractAdvice(),new cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler("default-delivery",mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class))).build();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",27462),0);
        server.createContext("/",exchange->{
            try {
                String actor=exchange.getRequestHeaders().getFirst("X-Fixture-Actor");login(7,actor==null?880001:Long.parseLong(actor));
                var path=exchange.getRequestURI().getPath();byte[] content;int status=200;
                if(path.equals("/fixture/context")) content=json.writeValueAsBytes(Map.of("first",first,"second",second));
                else if(path.equals("/fixture/reject-material")) {runtime.jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_ordinary CHECK(business_type_code <> 'IT_SECOND_DELIVERY')");content="{}".getBytes();}
                else if(path.equals("/fixture/allow-material")) {runtime.jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_ordinary");content="{}".getBytes();}
                else if(path.equals("/fixture/evidence")) {
                    var result=new LinkedHashMap<String,Object>();result.put("materials",runtime.jdbc.queryForList("SELECT id,project_id,business_type_code,entity_id,type_code,source_kind,material_kind,file_reference_id,file_artifact_id,file_version_no,status,version,deleted FROM plt_delivery_material ORDER BY id"));
                    result.put("files",count("plt_file_version"));result.put("requirements",count("plt_delivery_requirement"));result.put("submissions",count("plt_delivery_submission"));
                    result.put("firstCompletion",deliveries.completion(scope(first,"REPORT")));result.put("secondCompletion",deliveries.completion(scope(second,"REPORT")));
                    result.put("firstTitle",runtime.jdbc.queryForObject("SELECT title FROM it_declared_note WHERE id=?",String.class,first.entityId()));
                    content=json.writeValueAsBytes(result);
                } else {
                    var request=request(exchange.getRequestMethod(),exchange.getRequestURI().toString(),exchange.getRequestHeaders().getFirst("Content-Type"),exchange.getRequestBody().readAllBytes());
                    exchange.getRequestHeaders().forEach((name,values)->values.forEach(value->request.header(name,value)));
                    var response=http.perform(request).andReturn().getResponse();status=response.getStatus();content=response.getContentAsByteArray();
                }
                exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.sendResponseHeaders(status,content.length);exchange.getResponseBody().write(content);
            } catch(Exception failure) {failure.printStackTrace();byte[] content=json.writeValueAsBytes(Map.of("code",500,"msg",failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage()));exchange.sendResponseHeaders(500,content.length);exchange.getResponseBody().write(content);}
            finally {exchange.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        });server.start();
        try {
            var repo=Path.of("").toAbsolutePath();while(repo!=null && !Files.isDirectory(repo.resolve("scripts/tests")))repo=repo.getParent();
            var output=repo.resolve(".run/default-business-delivery-20261007/browser-process.log");
            var process=new ProcessBuilder("python3",repo.resolve("scripts/tests/run_default_business_delivery_browser.py").toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Browser timed out");}
            System.out.println(Files.readString(output));assertEquals(0,process.exitValue(),"See browser evidence");
        } finally {server.stop(0);}
    }
    /** Only the multipart transport is parsed here; content and ownership validation remain production code. */
    static AbstractMockHttpServletRequestBuilder<?> request(String method,String target,String mediaType,byte[] body) {
        if(mediaType!=null && mediaType.startsWith("multipart/form-data")) {
            var request=MockMvcRequestBuilders.multipart(target);String boundary=mediaType.substring(mediaType.indexOf("boundary=")+9).replace("\"","");
            for(String part:new String(body,java.nio.charset.StandardCharsets.ISO_8859_1).split(java.util.regex.Pattern.quote("--"+boundary))) {
                int split=part.indexOf("\r\n\r\n");if(split<0)continue;
                String headers=part.substring(0,split),value=part.substring(split+4);if(value.endsWith("\r\n"))value=value.substring(0,value.length()-2);
                var name=java.util.regex.Pattern.compile("name=\"([^\"]+)\"").matcher(headers);if(!name.find())continue;
                var file=java.util.regex.Pattern.compile("filename=\"([^\"]+)\"").matcher(headers);
                if(file.find()) {var mime=java.util.regex.Pattern.compile("Content-Type: ([^\r\n]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(headers);
                    request.file(new MockMultipartFile(name.group(1),file.group(1),mime.find()?mime.group(1):"application/octet-stream",value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));}
                else request.param(name.group(1),value);
            }
            return request;
        }
        var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),target);
        if(body.length>0)request.contentType(Objects.requireNonNullElse(mediaType,"application/json")).content(body);return request;
    }
}
