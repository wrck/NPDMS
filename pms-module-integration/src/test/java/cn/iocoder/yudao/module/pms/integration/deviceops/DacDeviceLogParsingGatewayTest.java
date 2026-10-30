package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DacDeviceLogParsingGatewayTest {
    DacDeviceLogParsingGateway gateway;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var properties = new DacDeviceOpsGatewayProperties();
        properties.setBaseUrl("https://dac.test"); properties.setBearerToken("test-token");
        gateway = new DacDeviceLogParsingGateway(properties);
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(gateway, "http")).build();
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void usesGenericRouteAndNativeCollectionIdentity() {
        server.expect(requestTo("https://dac.test/api/v1/log-parses"))
                .andExpect(method(HttpMethod.POST)).andExpect(jsonPath("$.sourceCollectionId").value("collection-1"))
                .andExpect(jsonPath("$.namespace").value("npdms-1"))
                .andRespond(withSuccess(response("21", "task-1"), MediaType.APPLICATION_JSON));
        assertEquals("task-1", gateway.submit(new DeviceLogParsingApi.Submission("log-123", "21", "collection-1", null, null)).taskId());
        server.verify();
    }
    @Test void crossProjectResponseIsRejected() {
        server.expect(anything()).andRespond(withSuccess(response("22", "task-1"), MediaType.APPLICATION_JSON));
        assertThrows(DeviceLogParsingApi.ParsingException.class, () -> gateway.find("log-123", "21", "collection-1"));
    }
    @Test void resultFromAnotherTaskIsRejected() {
        server.expect(anything()).andRespond(withSuccess(response("21", "foreign-task"), MediaType.APPLICATION_JSON));
        assertThrows(DeviceLogParsingApi.ParsingException.class, () -> gateway.find("log-123", "21", "collection-1"));
    }
    @Test void onlyTaskNotFoundMeansNotAcceptedAndErrorsDoNotEchoBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"TASK_NOT_FOUND\"}"));
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("sensitive remote error"));
        assertNull(gateway.find("log-123", "21", "collection-1"));
        var error = assertThrows(DeviceLogParsingApi.ParsingException.class, () -> gateway.find("log-123", "21", "collection-1"));
        assertFalse(error.getMessage().contains("sensitive"));
    }
    @Test void explicitInvalidLogInputRetainsItsClassificationWithoutEchoingRemoteContent() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"code\":\"INVALID_REQUEST\",\"message\":\"sensitive original log\"}"));
        server.expect(anything()).andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("{\"code\":\"INPUT_TOO_LARGE\"}"));
        var input = new DeviceLogParsingApi.LogInput("21", "invalid log", null);
        var invalid = assertThrows(DeviceLogParsingApi.InvalidInputException.class, () -> gateway.normalize(input));
        assertFalse(invalid.getMessage().contains("sensitive"));
        assertThrows(DeviceLogParsingApi.InvalidInputException.class, () -> gateway.normalize(input));
        server.verify();
    }
    @Test void authenticationAndServerFailuresAreNotReportedAsInvalidUserInput() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("{\"code\":\"INVALID_REQUEST\"}"));
        server.expect(anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"code\":\"INVALID_REQUEST\"}"));
        var input = new DeviceLogParsingApi.LogInput("21", "valid log", null);
        for (int index = 0; index < 2; index++) {
            var failure = assertThrows(DeviceLogParsingApi.ParsingException.class, () -> gateway.normalize(input));
            assertFalse(failure instanceof DeviceLogParsingApi.InvalidInputException);
        }
        server.verify();
    }
    private String response(String project, String resultTask) {
        return "{\"requestId\":\"log-123\",\"namespace\":\"npdms-1\",\"projectKey\":\""+project+"\","
                +"\"sourceCollectionId\":\"collection-1\",\"taskId\":\"task-1\",\"state\":\"SUCCEEDED\","
                +"\"result\":{\"taskId\":\""+resultTask+"\",\"semanticResult\":{}}}";
    }
}
