package com.dp.deviceops.adapter.web.masterdata;

import com.dp.deviceops.core.port.MasterDataQueryPort;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** No cache and no persistence: every call obtains a client-credentials bearer token then queries upstream. */
@Component
public final class HttpMasterDataQueryAdapter implements MasterDataQueryPort {
    private final MasterDataHttpProperties properties; private final RestClient client;
    public HttpMasterDataQueryAdapter(MasterDataHttpProperties properties) { this.properties = Objects.requireNonNull(properties); properties.validate(); var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build()); factory.setReadTimeout(properties.getReadTimeout()); this.client=RestClient.builder().requestFactory(factory).build(); }
    @Override public List<ProjectProjection> findProjects(String query) { enabled(); UpstreamProject[] rows=read(properties.getIntegrationHost() + "/projects?query={query}", UpstreamProject[].class, query); return java.util.Arrays.stream(rows).map(x->new ProjectProjection(x.namespace,x.projectKey,x.projectName,x.projectCode)).toList(); }
    @Override public List<DeviceProjection> findDevices(String projectKey, String query) { enabled(); UpstreamDevice[] rows=read(properties.getIntegrationHost() + "/projects/{project}/devices?query={query}", UpstreamDevice[].class, projectKey, query); return java.util.Arrays.stream(rows).map(x->new DeviceProjection(x.deviceKey,x.deviceName,x.vendor,x.model)).toList(); }
    private <T> T read(String uri,Class<T> type,Object... values) { try { T result=client.get().uri(uri,values).headers(h -> h.setBearerAuth(token())).retrieve().onStatus(s->s.value()==403,(r,x)->{throw new MasterDataException(403);}).onStatus(s->s.value()==404,(r,x)->{throw new MasterDataException(404);}).onStatus(HttpStatusCode::isError,(r,x)->{throw new MasterDataException(502);}).body(type); if(result==null) throw new MasterDataException(502); return result; } catch(MasterDataException e){throw e;} catch(Exception e){throw new MasterDataException(503);} }
    private void enabled(){if(!properties.isEnabled())throw new MasterDataException(503);}
    private String token() { try { String body="grant_type=client_credentials"+(properties.getScope().isBlank()?"":"&scope="+java.net.URLEncoder.encode(properties.getScope(),java.nio.charset.StandardCharsets.UTF_8)); Token response = client.post().uri(properties.getTokenUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED).headers(h -> h.setBasicAuth(properties.getClientId(), properties.getClientSecret())).body(body).retrieve().onStatus(HttpStatusCode::isError,(r,x)->{throw new MasterDataException(503);}).body(Token.class); if(response==null||response.access_token==null)throw new MasterDataException(503); return response.access_token; }catch(MasterDataException e){throw e;}catch(Exception e){throw new MasterDataException(503);} }
    private static final class Token { public String access_token; }
    @JsonIgnoreProperties(ignoreUnknown = true) private static final class UpstreamProject { public String namespace; public String projectKey; public String projectName; public String projectCode; }
    @JsonIgnoreProperties(ignoreUnknown = true) private static final class UpstreamDevice { public String deviceKey; public String deviceName; public String vendor; public String model; }
    public static final class MasterDataException extends RuntimeException { public final int status; MasterDataException(int status){this.status=status;} }
}
