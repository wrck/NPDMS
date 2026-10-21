package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.JdbcDesktopTaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DesktopServerClientTest {
    @Test void httpSuccessCannotSubstituteForPositiveBusinessClaimConfirmation() {
        var client=new DesktopServerClient(new DesktopProperties("https://npdms.example", "https://web.example"));
        var builder=RestClient.builder();
        var upstream=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client,"http",builder.build());
        var task=new JdbcDesktopTaskStore.Task("npdms-1","task-1",1,"42","7","execution-1",new byte[0],new byte[0],"v1",null,false);
        var authorization=new DesktopServerClient.TaskAuthorization(task,"fixture-task-token");
        for (String body : new String[]{"{\"code\":409,\"data\":null}", "{}", "{\"accepted\":false}", "{\"accepted\":true}"}) {
            upstream.expect(requestTo("https://npdms.example/api/v1/pms/integration/device-ops/desktop/tasks/task-1/claim"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }
        assertThrows(IllegalStateException.class,()->client.claim(authorization));
        assertThrows(IllegalStateException.class,()->client.claim(authorization));
        assertThrows(IllegalStateException.class,()->client.claim(authorization));
        assertDoesNotThrow(()->client.claim(authorization));
        upstream.verify();
    }
}
