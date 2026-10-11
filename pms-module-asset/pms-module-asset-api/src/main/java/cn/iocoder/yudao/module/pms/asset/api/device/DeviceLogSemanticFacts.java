package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.List;
import java.util.Map;

/** Reads published semantic facts; does not infer versions from text or change parser rules. */
public record DeviceLogSemanticFacts(String serialNumber, String conpVersion,
                                     String bootVersion, String cpldVersion, String pcbVersion) {
    public static DeviceLogSemanticFacts from(Map<String,Object> result) {
        Object semantic=result==null ? null : result.get("semanticResult");
        Object snapshot=semantic instanceof Map<?,?> map ? map.get("snapshot") : null;
        return new DeviceLogSemanticFacts(fact(snapshot,"device.identity.serialNumber"),
                fact(snapshot,"device.software.version"),basicBootVersion(snapshot),
                fact(snapshot,"device.hardware.cpldVersion"),fact(snapshot,"device.hardware.pcbVersion"));
    }
    private static String basicBootVersion(Object snapshot) {
        String value=fact(snapshot,"device.hardware.bootloader.basicVersion");
        return value!=null ? value : fact(snapshot,"device.software.bootloader.basicVersion");
    }
    private static String fact(Object value,String key) {
        if (value instanceof Map<?,?> map) {
            if (key.equals(map.get("semanticKey"))) {
                Object text=map.get("value");
                return "OBSERVED".equals(map.get("status")) && text instanceof String s && !s.isBlank() ? s : null;
            }
            for (Object child:map.values()) { String result=fact(child,key);if (result!=null) return result; }
        } else if (value instanceof List<?> list) {
            for (Object child:list) { String result=fact(child,key);if (result!=null) return result; }
        }
        return null;
    }
}
