package com.dp.deviceops.adapter.web;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.parser.semantic.log.DeviceLogSplitter;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class NpdmsLogSplitControllerTest {
    private final NpdmsLogSplitController controller = new NpdmsLogSplitController(new ProjectClaimAuthorizer());
    private Jwt jwt() { return Jwt.withTokenValue("test").header("alg","none").subject("npdms")
            .claim("device_ops_namespaces",List.of("npdms-1")).claim("device_ops_projects",List.of("10")).build(); }
    @Test void namespaceAndProjectAreRequiredBeforeParsing() {
        assertThrows(AccessDeniedException.class, () -> controller.split(jwt(),new NpdmsLogSplitController.Request("npdms-2","10","TERMINAL_TEXT","SN: SN001")));
        assertThrows(AccessDeniedException.class, () -> controller.split(jwt(),new NpdmsLogSplitController.Request("npdms-1","11","TERMINAL_TEXT","SN: SN001")));
    }
    @Test void nativeCallbackRetainsEveryCompleteCommandBlock() {
        String content = """
          {"projectKey":"10","status":"SUCCEEDED","truncated":false,"commandBlocks":[
          {"commandText":"show run","status":"SUCCEEDED","stdout":"interface Gi1","stderr":"diagnostic notice","exitCode":0,"truncated":false},
          {"commandText":"show tech","status":"SUCCEEDED","stdout":"SN: SN001\\nlast output","exitCode":0,"truncated":false}]}
          """;
        var result=controller.split(jwt(),new NpdmsLogSplitController.Request("npdms-1","10","DAC_CALLBACK",content));
        assertEquals(1,result.devices().size()); assertTrue(result.devices().getFirst().content().contains("interface Gi1"));
        assertTrue(result.devices().getFirst().content().contains("last output"));
        assertTrue(result.devices().getFirst().content().contains("diagnostic notice"));
        assertThrows(DeviceLogSplitter.SplitException.class, () -> controller.split(jwt(),
                new NpdmsLogSplitController.Request("npdms-1","10","DAC_CALLBACK",content.replace("\"truncated\":false","\"truncated\":true"))));
    }
}
