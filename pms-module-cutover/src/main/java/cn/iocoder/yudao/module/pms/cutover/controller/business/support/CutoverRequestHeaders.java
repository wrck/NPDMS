package cn.iocoder.yudao.module.pms.cutover.controller.business.support;

import org.springframework.http.HttpHeaders;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent;

/** 保持版本和幂等头的单值契约；是否必填仍由各业务 Codec 判断。 */
public final class CutoverRequestHeaders {
    private CutoverRequestHeaders() { }

    public static String value(HttpHeaders headers, String name) {
        var values = headers.get(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() != 1) throw new IllegalArgumentException("duplicate header: " + name);
        return values.getFirst();
    }

    public static CutoverWriteIntent intent(HttpHeaders headers) {
        String key = value(headers, "Idempotency-Key");
        if (key == null || key.isBlank() || !key.equals(key.trim()) || key.length() > 128)
            throw new IllegalArgumentException("invalid Idempotency-Key");
        return new CutoverWriteIntent(key, java.util.UUID.randomUUID().toString());
    }

    public static Long version(HttpHeaders headers, String name) {
        String value = value(headers, name);
        if (value == null || !value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("invalid " + name);
        try { return Long.valueOf(value); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("invalid " + name); }
    }
}
