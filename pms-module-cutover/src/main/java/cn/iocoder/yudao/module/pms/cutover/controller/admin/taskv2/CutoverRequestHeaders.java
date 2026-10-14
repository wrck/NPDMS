package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import org.springframework.http.HttpHeaders;

/** 保持版本和幂等头的单值契约；是否必填仍由各业务 Codec 判断。 */
final class CutoverRequestHeaders {
    private CutoverRequestHeaders() { }

    static String value(HttpHeaders headers, String name) {
        var values = headers.get(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() != 1) throw new IllegalArgumentException("duplicate header: " + name);
        return values.getFirst();
    }
}
