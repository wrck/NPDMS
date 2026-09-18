package cn.iocoder.yudao.module.pms.project.domain.template;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 页面地址的纯语法边界；路由登记、实体归属及操作授权仍由各自消费者核验。 */
public final class TemplatePresentationUrl {
    private static final Set<String> RESERVED_PARAMETERS = Set.of(
            "tenant", "tenantid", "user", "userid", "actor", "actorid", "principal", "principalid",
            "authorization", "token", "accesstoken", "refreshtoken", "password", "secret", "apikey",
            "session", "sessionid", "cookie", "credential", "credentials",
            "redirect", "redirecturl", "redirecturi", "returnurl", "callback", "callbackurl",
            "constructor", "prototype");

    private TemplatePresentationUrl() { }

    public static String path(String value) {
        require(value != null && value.startsWith("/") && !value.startsWith("//"), "pageUrl");
        require(value.indexOf('\\') < 0 && value.indexOf('%') < 0 && !invalidCharacters(value), "pageUrl");
        final URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("PRESENTATION_URL_INVALID: pageUrl", invalid); }
        require(!uri.isAbsolute() && uri.getRawAuthority() == null && uri.getRawQuery() == null
                && uri.getRawFragment() == null && value.equals(uri.getRawPath()), "pageUrl");
        for (String segment : value.substring(1).split("/", -1)) {
            require(!".".equals(segment) && !"..".equals(segment), "pageUrl");
        }
        require(!value.contains("//"), "pageUrl");
        String first = value.substring(1).split("/", -1)[0].toLowerCase(Locale.ROOT);
        require(!Set.of("api", "admin-api", "app-api").contains(first), "pageUrl.writeApi");
        return value;
    }

    public static Map<String, String> query(Map<String, String> values) {
        require(values != null, "query");
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            require(name != null && name.matches("[A-Za-z][A-Za-z0-9_]*"), "query.name");
            String identity = name.replace("_", "").toLowerCase(Locale.ROOT);
            require(!RESERVED_PARAMETERS.contains(identity), "query.reserved");
            require(value != null && !invalidCharacters(value), "query.value");
            copy.put(name, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    /** 只接收已解析的参数值；逐项编码，绝不把值拼成路径、片段或新的参数。 */
    public static String assemble(String pageUrl, Map<String, String> values) {
        String route = path(pageUrl);
        Map<String, String> parameters = query(values);
        if (parameters.isEmpty()) return route;
        return route + "?" + parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean invalidCharacters(String value) {
        return value.codePoints().anyMatch(code -> Character.isISOControl(code)
                || code >= Character.MIN_SURROGATE && code <= Character.MAX_SURROGATE);
    }

    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("PRESENTATION_URL_INVALID: " + field);
    }
}
