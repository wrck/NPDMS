package com.dp.deviceops.adapter.web;
import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.model.*;
import com.dp.deviceops.core.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NpdmsSavedConnectionGrantTest {
    final String key="test-only-request-signing-key-32-bytes";
    SavedConnectionStore store=mock(SavedConnectionStore.class);
    GenericCollectionController submissions=mock(GenericCollectionController.class);
    NpdmsCollectionController controller=new NpdmsCollectionController(mock(CollectionRepository.class),mock(CollectionQueryPort.class),new ProjectClaimAuthorizer(),mock(KeyedCollectionDispatcher.class),mock(CallbackOutboxPort.class),submissions,key);
    SavedConnection saved;
    @BeforeEach void setup(){
        saved=new SavedConnection("saved-1","npdms-7","test",null,new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SSH2,"device.example",22,"operator",CommandExecutionPort.AuthenticationType.PASSWORD,CommandExecutionPort.ExecutionMode.SHELL,null,null,Duration.ofSeconds(10)),true,4,Instant.now(),Instant.now());
        ReflectionTestUtils.setField(controller,"savedConnections",store);
        when(store.find("npdms","npdms-7","saved-1")).thenReturn(Optional.of(saved));
    }
    @Test void grantBindsSavedReferenceAndVersionToServerResolvedEndpoint() throws Exception {
        var now=Long.toString(Instant.now().getEpochSecond());var request=request();
        controller.submit(jwt(),"task",now,sign(now,"4"),request);
        verify(submissions).submit(any(),eq("task"),same(request));
        clearInvocations(submissions);
        assertThrows(ResponseStatusException.class,()->controller.submit(jwt(),"task",now,sign(now,"5"),request()));
        verifyNoInteractions(submissions);
    }
    @Test void changedConnectionVersionRejectsBeforeExecution() throws Exception {
        var now=Long.toString(Instant.now().getEpochSecond());
        when(store.find("npdms","npdms-7","saved-1")).thenReturn(Optional.of(new SavedConnection(saved.id(),saved.namespace(),saved.displayName(),null,saved.connection(),true,5,Instant.now(),Instant.now())));
        assertThrows(ResponseStatusException.class,()->controller.submit(jwt(),"task",now,sign(now,"4"),request()));
        verifyNoInteractions(submissions);
    }
    @Test void mapperRechecksVersionWhenLoadingSecretSnapshot() throws Exception {
        when(store.withConnection(eq("npdms"),eq("npdms-7"),eq("saved-1"),any())).thenAnswer(invocation->{
            SavedConnectionStore.SavedConnectionOperation<?> action=invocation.getArgument(3);
            try(var secret=new TransientCredential("test-password".toCharArray(),null)) {
                return action.apply(new SavedConnection(saved.id(),saved.namespace(),saved.displayName(),null,saved.connection(),true,5,Instant.now(),Instant.now()),secret);
            }
        });
        var response=assertThrows(ResponseStatusException.class,()->new ConnectionRequestMapper(store).map("npdms","npdms-7",request().connection()));
        assertEquals(409,response.getStatusCode().value());
    }
    private GenericCollectionController.Request request() throws Exception {return new ObjectMapper().readValue("""
      {"namespace":"npdms-7","externalRequestId":"task","context":{"project":{"namespace":"npdms-7","projectKey":"10"},"device":{"deviceKey":"20"}},
       "connection":{"savedConnectionId":"saved-1","savedConnectionVersion":4},"script":{"source":"EXTERNAL_DELIVERED","key":"plt-template","version":"1","content":"show run","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","policy":"EXECUTION_ONLY","parserType":"NONE"},"commandTimeoutSeconds":30,"parseTimeoutSeconds":30,"leaseGraceSeconds":0}
      """,GenericCollectionController.Request.class);}
    private String sign(String time,String version) throws Exception {
        var binding=java.util.stream.Stream.of(time,"npdms-7","task","10","20","device.example","22","SSH2","operator","plt-template","1","a".repeat(64),"30","","saved-1",version).map(v->v.length()+":"+v).collect(java.util.stream.Collectors.joining());
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(binding.getBytes(StandardCharsets.UTF_8)));
    }
    private Jwt jwt(){return Jwt.withTokenValue("test").header("alg","none").subject("npdms").claim("device_ops_namespaces",List.of("npdms-7")).build();}
}
