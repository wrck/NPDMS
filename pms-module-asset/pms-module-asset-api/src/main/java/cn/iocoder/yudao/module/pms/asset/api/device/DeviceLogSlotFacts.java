package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.*;

/** Typed slot observations; each missing version independently falls back to its parent version. */
public record DeviceLogSlotFacts(List<SlotDevice> devices) {
    public DeviceLogSlotFacts { devices=List.copyOf(devices); }
    public record SlotDevice(int slot,String serialNumber,String model,String boardType,
                             String pcbVersion,String cpldVersion,String softVersion,String basicBootloaderVersion) {
        public String effectiveSoftware(String parentSoftware) { return softVersion!=null ? softVersion : parentSoftware; }
        public String effectiveBoot(String parentBoot) { return basicBootloaderVersion!=null ? basicBootloaderVersion : parentBoot; }
        public String effectiveCpld(String parentCpld) { return cpldVersion!=null ? cpldVersion : parentCpld; }
        public String effectivePcb(String parentPcb) { return pcbVersion!=null ? pcbVersion : parentPcb; }
        public boolean softwareInherited() { return softVersion==null; }
    }
    public static DeviceLogSlotFacts from(Map<String,Object> result) {
        Object semantic=result==null ? null : result.get("semanticResult");
        Object snapshot=semantic instanceof Map<?,?> map ? map.get("snapshot") : null;
        List<Object> rows=new ArrayList<>();
        observed(snapshot,rows);
        List<SlotDevice> devices=new ArrayList<>();
        Set<Integer> positions=new HashSet<>();Set<String> serials=new HashSet<>();
        for (Object row:rows) {
            if (!(row instanceof Map<?,?> fields)) throw new IllegalArgumentException("插槽解析行格式无效");
            Object position=fields.get("slot");
            if (!(position instanceof Number number) || number.longValue()<0 || number.longValue()>Integer.MAX_VALUE
                    || number.doubleValue()!=number.intValue()) throw new IllegalArgumentException("插槽编号无效");
            int slot=number.intValue();
            String serial=text(fields.get("serialNumber"),128);
            if (serial==null || !positions.add(slot) || !serials.add(serial.toUpperCase(Locale.ROOT)))
                throw new IllegalArgumentException("插槽或子设备序列号缺失/重复，无法唯一关联");
            devices.add(new SlotDevice(slot,serial,text(fields.get("model"),255),text(fields.get("boardType"),128),
                    text(fields.get("pcbVersion"),255),text(fields.get("cpldVersion"),255),
                    text(fields.get("softVersion"),255),text(fields.get("basicBootloaderVersion"),255)));
        }
        return new DeviceLogSlotFacts(devices);
    }
    private static void observed(Object value,List<Object> rows) {
        if (value instanceof Map<?,?> map) {
            if ("device.hardware.slotDevices".equals(map.get("semanticKey"))) {
                if ("OBSERVED".equals(map.get("status"))) {
                    Object observed=map.get("value");
                    if (observed instanceof List<?> list) rows.addAll(list);
                    else if (observed instanceof Map<?,?>) rows.add(observed);
                    else throw new IllegalArgumentException("插槽解析行格式无效");
                }
                return;
            }
            for (Object child:map.values()) observed(child,rows);
        } else if (value instanceof List<?> list) {
            for (Object child:list) observed(child,rows);
        }
    }
    private static String text(Object value,int max) {
        if (value==null) return null;
        if (!(value instanceof String s) || s.length()>max || s.indexOf('\0')>=0 || s.contains("\n") || s.contains("\r"))
            throw new IllegalArgumentException("插槽解析字段无效");
        return s.isBlank() || "-".equals(s) ? null : s;
    }
}
