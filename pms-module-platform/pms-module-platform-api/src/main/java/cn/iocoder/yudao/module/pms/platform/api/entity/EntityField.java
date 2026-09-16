package cn.iocoder.yudao.module.pms.platform.api.entity;

/** Stable field code and business type; UI component choices are form configuration. */
public record EntityField(String code, Type type, boolean required) {
    public enum Type { TEXT, NUMBER, BOOLEAN, DATE, DATETIME, TEXT_LIST, OBJECT_LIST }

    public EntityField {
        if (code == null || code.isBlank() || type == null) {
            throw new IllegalArgumentException("Field code and type are required");
        }
    }
}
