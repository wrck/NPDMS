package cn.iocoder.yudao.module.pms.integration.umc;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

/** One invocation owns its TLS client and cookies. Never follow redirects with credentials. */
final class UmcHttpsTransport implements JavaUmcInspectionGateway.Transport {
    private final URI base;
    private final HttpClient client;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    UmcHttpsTransport(UmcInspectionProperties properties) throws Exception {
        base = URI.create(properties.getBaseUrl());
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);
        if (properties.getCertificateSha256() != null && !properties.getCertificateSha256().isBlank()) {
            byte[] expected = HexFormat.of().parseHex(properties.getCertificateSha256().replace(":", ""));
            var context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509ExtendedTrustManager() {
                private void verify(X509Certificate[] chain) throws CertificateException {
                    if (chain == null || chain.length == 0) throw new CertificateException("UMC certificate missing");
                    chain[0].checkValidity();
                    try {
                        if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())))
                            throw new CertificateException("UMC certificate pin mismatch");
                    } catch (CertificateException error) { throw error;
                    } catch (Exception error) { throw new CertificateException("UMC certificate verification failed"); }
                }
                public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException { verify(c); }
                public void checkServerTrusted(X509Certificate[] c, String a, java.net.Socket s) throws CertificateException { verify(c); }
                public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { verify(c); }
                public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { throw new CertificateException("Not a client trust store"); }
                public void checkClientTrusted(X509Certificate[] c, String a, java.net.Socket s) throws CertificateException { checkClientTrusted(c,a); }
                public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { checkClientTrusted(c,a); }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            builder.sslContext(context);
        }
        client = builder.build();
    }

    public JavaUmcInspectionGateway.Reply exchange(JavaUmcInspectionGateway.Call call) throws Exception {
        URI target = base.resolve(call.route());
        if (!Objects.equals(base.getScheme(),target.getScheme()) || !Objects.equals(base.getRawAuthority(),target.getRawAuthority())
                || !call.route().startsWith("/") || call.route().startsWith("//")) throw new IllegalArgumentException("Invalid UMC route");
        var request = HttpRequest.newBuilder(target).timeout(Duration.ofMillis(call.timeoutMillis()))
                .header("accept", "*/*").header("apiversion", "2");
        if (!cookies.isEmpty()) request.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).toList()));
        call.headers().forEach(request::header);
        request.method(call.method(), call.body() == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(call.body()));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), JavaUmcInspectionGateway.MAX_BYTES));
        for (String value : response.headers().allValues("set-cookie")) {
            for (var cookie : java.net.HttpCookie.parse(value)) {
                if (cookie.hasExpired()) cookies.remove(cookie.getName());
                else cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        return new JavaUmcInspectionGateway.Reply(response.statusCode(), response.body());
    }

    public void close() { cookies.clear(); client.shutdownNow(); }
}
