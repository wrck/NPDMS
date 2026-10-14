package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 仅用于配置元数据的标量；业务答案始终写入具名业务列。 */
public record CutoverMetadataScalar(String type, String text, BigDecimal number,
                                    Boolean bool, LocalDateTime datetime) {
    public static CutoverMetadataScalar from(Object value) {
        if (value == null) return new CutoverMetadataScalar("NULL", null, null, null, null);
        if (value instanceof String text) return new CutoverMetadataScalar("STRING", text, null, null, null);
        if (value instanceof Boolean bool) return new CutoverMetadataScalar("BOOLEAN", null, null, bool, null);
        if (value instanceof Number number) {
            return new CutoverMetadataScalar("NUMBER", null, new BigDecimal(number.toString()), null, null);
        }
        if (value instanceof LocalDateTime time) return new CutoverMetadataScalar("DATETIME", null, null, null, time);
        throw new IllegalArgumentException("配置条件和选项必须是标量值");
    }

    public Object value() {
        return switch (type) {
            case "NULL" -> null;
            case "STRING" -> text;
            case "NUMBER" -> number;
            case "BOOLEAN" -> bool;
            case "DATETIME" -> datetime;
            default -> throw new IllegalArgumentException("未知的配置标量类型：" + type);
        };
    }
}
