package cn.iocoder.yudao.module.pms.project.domain.template;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** 无框架场景与JUnit使用同一断言，不用源码字符串匹配代替行为验证。 */
public final class TemplatePresentationUrlScenarios {
    private TemplatePresentationUrlScenarios() { }

    public static int run() {
        int cases = 0;
        for (String path : new String[]{"/", "/pms/delivery-business/site-survey", "/pms/需求分析", "/pms/v2.1/detail/"}) {
            equal(path, TemplatePresentationUrl.path(path)); cases++;
        }
        for (String path : new String[]{"", "pms/x", "https://example.test/pms", "javascript:alert(1)",
                "data:text/html,x", "//example.test", "/\\example.test", "/pms\\x", "/pms/../x", "/pms/./x",
                "/pms//x", "/pms/%2e%2e/x", "/pms/%252e%252e/x", "/%2fexample.test", "/pms/x?id=1",
                "/pms/x#fragment", "/pms/\nx", "/pms/\tx", "/pms/ x", "/api/v1/write", "/admin-api/pms/write",
                "/app-api/write", "/API/write", "/api", "/pms/\ud800"}) {
            rejects(() -> TemplatePresentationUrl.path(path)); cases++;
        }
        rejects(() -> TemplatePresentationUrl.path(null)); cases++;
        for (String key : new String[]{"tenantId", "TENANT_ID", "userId", "actorId", "Authorization", "access_token",
                "refreshToken", "password", "secret", "api_key", "sessionId", "cookie", "credentials", "returnUrl",
                "redirect", "callbackUrl", "__proto__", "constructor", "prototype", "x&tenantId", "x.y", ""}) {
            rejects(() -> TemplatePresentationUrl.query(Map.of(key, "value"))); cases++;
        }
        for (String value : new String[]{"\r\n", "\u0000", "\ud800"}) {
            rejects(() -> TemplatePresentationUrl.query(Map.of("objectId", value))); cases++;
        }
        Map<String, String> invalid = new LinkedHashMap<>(); invalid.put("objectId", null);
        rejects(() -> TemplatePresentationUrl.query(invalid)); cases++;
        rejects(() -> TemplatePresentationUrl.query(null)); cases++;
        Map<String, String> original = new LinkedHashMap<>(); original.put("projectId", "900719925474099312345");
        Map<String, String> frozen = TemplatePresentationUrl.query(original);
        original.put("projectId", "other"); equal("900719925474099312345", frozen.get("projectId")); cases++;
        try { frozen.put("objectId", "x"); throw new AssertionError("query must be immutable"); }
        catch (UnsupportedOperationException expected) { cases++; }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("projectId", "900719925474099312345"); values.put("objectId", "工勘 A&B=#/+%😀");
        String url = TemplatePresentationUrl.assemble("/pms/delivery-business/site-survey", values);
        equal("/pms/delivery-business/site-survey?projectId=900719925474099312345&objectId=%E5%B7%A5%E5%8B%98%20A%26B%3D%23%2F%2B%25%F0%9F%98%80", url); cases++;
        URI parsed = URI.create(url); equal(null, parsed.getRawFragment()); equal(null, parsed.getRawAuthority()); cases++;
        equal("/pms/x", TemplatePresentationUrl.assemble("/pms/x", Map.of())); cases++;
        equal("/pms/x?objectId=", TemplatePresentationUrl.assemble("/pms/x", Map.of("objectId", ""))); cases++;
        return cases;
    }

    public static void main(String[] args) { System.out.println("PASS " + run() + " presentation URL scenarios"); }
    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("unsafe presentation accepted");
    }
}
