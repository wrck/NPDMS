package cn.iocoder.yudao.module.pms.integration.basesync;
public final class BaseSyncJson {
 private BaseSyncJson(){}
    /** Structural JSON order and decimal scale are presentation details, unlike NULL and empty strings. */
    public static String canonicalJson(String value) {
        return value==null?null:canonical(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(value));
    }
    private static String canonical(tools.jackson.databind.JsonNode node) {
        if(node.isObject()) {
            var fields=new java.util.TreeMap<String,tools.jackson.databind.JsonNode>();
            node.properties().forEach(entry->fields.put(entry.getKey(),entry.getValue()));
            var result=new java.util.StringJoiner(",","{","}");
            fields.forEach((key,value)->result.add(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(key)+":"+canonical(value)));
            return result.toString();
        }
        if(node.isArray()) {
            var result=new java.util.StringJoiner(",","[","]");node.forEach(value->result.add(canonical(value)));return result.toString();
        }
        return node.isNumber()?node.decimalValue().stripTrailingZeros().toPlainString():node.toString();
    }

}
