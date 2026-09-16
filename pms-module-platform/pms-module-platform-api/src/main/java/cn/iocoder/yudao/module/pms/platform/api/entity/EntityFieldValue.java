package cn.iocoder.yudao.module.pms.platform.api.entity;

/** Known null is different from a fact the caller cannot read. */
public record EntityFieldValue(boolean readable, Object value) {
    public EntityFieldValue {
        if (!readable && value != null) throw new IllegalArgumentException("Unreadable facts cannot expose a value");
    }
    public static EntityFieldValue known(Object value) { return new EntityFieldValue(true, value); }
    public static EntityFieldValue unavailable() { return new EntityFieldValue(false, null); }
}
