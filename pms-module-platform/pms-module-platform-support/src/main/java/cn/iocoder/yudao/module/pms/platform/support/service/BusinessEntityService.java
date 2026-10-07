package cn.iocoder.yudao.module.pms.platform.support.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the Owner identity of an inherited business application service. */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessEntityService {
    String ownerModule();
    String entityType();
    /** Explicit native identity; blank means the catalog identity is already native. */
    String nativeEntityType() default "";
}
