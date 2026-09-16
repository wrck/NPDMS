package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Opt-in execution contract. Absence never implies a global project-state guard. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProjectExecutionControlled {
    String operation();
    int version() default 1;
}
