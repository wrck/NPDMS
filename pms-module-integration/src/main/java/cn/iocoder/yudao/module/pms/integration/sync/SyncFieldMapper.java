package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class SyncFieldMapper {
    public List<DataSyncAdapter.Row> transform(SyncDefinition d, MysqlSyncReader.Snapshot snapshot,
                                               List<DataSyncAdapter.Binding> bindings) {
        Map<String,Long> targets=new HashMap<>();
        bindings.forEach(b->targets.put(b.object()+":"+b.sourceKey(),b.targetId()));
        List<DataSyncAdapter.Row> result=new ArrayList<>();
        for(var object:snapshot.objects()) {
            var source=d.sources().stream().filter(s->s.object().equals(object.object())).findFirst().orElseThrow();
            for(var row:object.rows()) {
                Map<String,Object> fields=new LinkedHashMap<>();
                for(var mapping:source.mappings()) {
                    if(!"CONSTANT".equals(mapping.conversion()) && !row.containsKey(mapping.source()))
                        throw new IllegalArgumentException("来源缺少映射列: "+mapping.source());
                    Object value="CONSTANT".equals(mapping.conversion())?mapping.constant():row.get(mapping.source());
                    if(value==null) value=mapping.defaultValue();
                    if("ENUM".equals(mapping.conversion())) {
                        String key=value==null?"NULL":value.toString();
                        if(mapping.values()==null||!mapping.values().containsKey(key))
                            throw new IllegalArgumentException("枚举没有映射值: "+mapping.target());
                        value=mapping.values().get(key);
                    } else if(value!=null) value=convert(value,mapping.conversion());
                    fields.put(mapping.target(),value);
                }
                if(source.updatedAt()!=null && !source.updatedAt().isBlank())
                    fields.put("_sourceUpdatedAt",row.get(source.updatedAt())==null?null:sourceTime(row.get(source.updatedAt())).toString());
                String key=row.get(source.sourceKey()).toString();
                if(source.syncPrimaryKey()) {
                    long requestedId=primaryKey(key);
                    Long boundId=targets.get(object.object()+":"+key);
                    if(boundId!=null && boundId!=requestedId)
                        throw new IllegalArgumentException("来源主键与既有目标主键不一致，禁止改写既有 ID: "+object.object()+"/"+key);
                    fields.put("_sourcePrimaryKey",requestedId);
                }
                result.add(new DataSyncAdapter.Row(object.object(),key,Collections.unmodifiableMap(fields),
                        targets.get(object.object()+":"+key)));
            }
        }
        return result;
    }
    static long primaryKey(String value) {
        try {
            long id=Long.parseLong(value);
            if(id<=0 || !Long.toString(id).equals(value)) throw new NumberFormatException();
            return id;
        }catch(NumberFormatException ex) {
            throw new IllegalArgumentException("目标主键要求无损的正整数 Long，来源主键无效: "+value);
        }
    }
    static Object convert(Object value,String conversion) {
        return switch(conversion) {
            case "DIRECT","CONSTANT","REFERENCE" -> value;
            case "STRING" -> value.toString();
            case "TRIM" -> value.toString().trim();
            case "LONG" -> new BigDecimal(value.toString()).longValueExact();
            case "DECIMAL" -> new BigDecimal(value.toString());
            case "DATETIME" -> sourceTime(value).toString();
            case "BOOLEAN" -> {
                String v=value.toString();
                if(!Set.of("true","false","0","1").contains(v)) throw new IllegalArgumentException("布尔转换失败");
                yield "true".equals(v)||"1".equals(v);
            }
            default -> throw new IllegalArgumentException("不支持的字段转换");
        };
    }
    static LocalDateTime sourceTime(Object value) {
        // Existing JsonUtils bindings serialize JDBC LocalDateTime values as epoch milliseconds.
        if(value instanceof Number number)
            return java.time.Instant.ofEpochMilli(number.longValue()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ','T'));
    }
}
