package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import java.lang.annotation.*;

/** Explicit model exposure without changing the Owner's JSON envelope. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BusinessModelField {
    String name() default "";
    boolean readable() default true;
    boolean writable() default true;
    /** Display order and default list capabilities inherited with this field. */
    int displayOrder() default 0;
    boolean listVisible() default true;
    boolean searchable() default true;
    boolean sortable() default true;
    String dictionaryRef() default "";
}
