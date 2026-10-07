package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import java.lang.annotation.*;

/** Explicit model exposure without changing the Owner's JSON envelope. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BusinessModelField {
    String name() default "";
    boolean readable() default true;
    boolean writable() default true;
    String dictionaryRef() default "";
}
