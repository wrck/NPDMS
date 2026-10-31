package cn.iocoder.yudao.module.pms.integration.umc;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UmcHttpsTransportTest {
    @TempDir static Path directory;
    static HttpsServer server;
    static String base,pin;
    static final AtomicInteger calls=new AtomicInteger();
    @BeforeAll static void start() throws Exception {
        Path store=directory.resolve("fixture.p12");
        String keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        var p=new ProcessBuilder(keytool,"-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=fixture.invalid","-storetype","PKCS12","-keystore",store.toString(),"-storepass","synthetic-fixture-only","-keypass","synthetic-fixture-only","-noprompt").redirectErrorStream(true).start();p.getInputStream().readAllBytes();assertEquals(0,p.waitFor());
        var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,"synthetic-fixture-only".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"synthetic-fixture-only".toCharArray());
        var ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),null,null);
        pin=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("fixture").getEncoded()));
        server=HttpsServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/",exchange->{
            calls.incrementAndGet();String path=exchange.getRequestURI().getPath();
            try {
                if(path.equals("/slow")){Thread.sleep(500);}
                if(path.equals("/redirect")){exchange.getResponseHeaders().set("Location","https://example.invalid/never-send");exchange.sendResponseHeaders(302,-1);return;}
                String content=path.equals("/cookie")?Objects.toString(exchange.getRequestHeaders().getFirst("Cookie"),""):"ok";
                exchange.getResponseHeaders().add("Set-Cookie","JSESSIONID=synthetic-session; Path=/; HttpOnly");
                byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{exchange.close();}
        });server.start();base="https://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterAll static void stop(){if(server!=null)server.stop(0);}
    UmcInspectionProperties settings(String value){var p=JavaUmcInspectionGatewayTest.settings();p.setBaseUrl(base);p.setCertificateSha256(value);return p;}
    JavaUmcInspectionGateway.Call get(String route,long timeout){return new JavaUmcInspectionGateway.Call(route,"GET",null,Map.of(),timeout);}
    @Test void exactPinAllowsOnlyThatCertificateAndCookieSessionIsInvocationLocal()throws Exception {
        try(var t=new UmcHttpsTransport(settings(pin))){assertEquals(200,t.exchange(get("/",2000)).status());assertTrue(new String(t.exchange(get("/cookie",2000)).body()).contains("JSESSIONID=synthetic-session"));}
        try(var t=new UmcHttpsTransport(settings(pin))){assertEquals("",new String(t.exchange(get("/cookie",2000)).body()));}
    }
    @Test void wrongPinAndUntrustedDefaultCertificateFailBeforeSendingHttp()throws Exception {
        int before=calls.get();for(String value:new String[]{"0".repeat(64),null})try(var t=new UmcHttpsTransport(settings(value))){assertThrows(Exception.class,()->t.exchange(get("/",2000)));}assertEquals(before,calls.get());
    }
    @Test void redirectsNeverForwardCredentialSessionAndOffOriginRouteIsRejected()throws Exception {
        try(var t=new UmcHttpsTransport(settings(pin))){assertEquals(302,t.exchange(get("/redirect",2000)).status());assertThrows(IllegalArgumentException.class,()->t.exchange(get("//example.invalid/",1000)));assertThrows(IllegalArgumentException.class,()->t.exchange(get("https://example.invalid/",1000)));}
    }
    @Test void requestDeadlineIsEnforced()throws Exception {try(var t=new UmcHttpsTransport(settings(pin))){assertThrows(Exception.class,()->t.exchange(get("/slow",50)));}}
}
