package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Identity of a normal project business. It does not define a workflow or register a new permission grant. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectBusinessModel {
    String ownerModule();
    String entityType();
    String stableCode();
    String name();
    String permissionPrefix();
}
