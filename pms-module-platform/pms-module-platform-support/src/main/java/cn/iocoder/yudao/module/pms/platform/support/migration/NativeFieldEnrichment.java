package cn.iocoder.yudao.module.pms.platform.support.migration;
import org.springframework.beans.BeanWrapperImpl;
import java.util.*;
/** Owners enrich missing columns only. All conflicts are checked before any field changes. */
public final class NativeFieldEnrichment {
    private NativeFieldEnrichment() {}
    public static boolean compatible(Object target, Object incoming, List<String> fields) {
        var old = new BeanWrapperImpl(target); var fresh = new BeanWrapperImpl(incoming);
        for (String field : fields) {
            Object next = fresh.getPropertyValue(field), previous = old.getPropertyValue(field);
            if (next != null && previous != null && !same(previous,next,field)) return false;
        }
        return true;
    }
    private static boolean same(Object previous,Object next,String field) {
        if (previous instanceof java.math.BigDecimal left && next instanceof java.math.BigDecimal right)
            return left.compareTo(right)==0;
        if (field.toLowerCase(java.util.Locale.ROOT).endsWith("custominfo") && previous instanceof String left && next instanceof String right) {
            try {
                return Objects.equals(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(left,Object.class),
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(right,Object.class));
            } catch (RuntimeException invalidJson) { return Objects.equals(previous,next); }
        }
        return Objects.equals(previous,next);
    }
    public static boolean fillMissing(Object target, Object incoming, List<String> fields) {
        if (!compatible(target,incoming,fields)) throw new IllegalArgumentException("DPPMS_NATIVE_FIELD_CONFLICT");
        var old = new BeanWrapperImpl(target); var fresh = new BeanWrapperImpl(incoming);
        boolean changed = false;
        for (String field : fields) {
            Object next = fresh.getPropertyValue(field);
            if (next != null && old.getPropertyValue(field) == null) {
                old.setPropertyValue(field,next); changed = true;
            }
        }
        return changed;
    }
}
