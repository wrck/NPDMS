package cn.iocoder.yudao.module.pms.workflow.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（迁移自源工程 pms-common com.dp.plat.common.annotation.OperLog）。
 *
 * <p>标注在 Controller 方法上，由切面统一记录操作日志。
 * businessType 取值约定：1=新增，2=修改，3=删除，4=导出，5=导入，其他=查询。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /**
     * 操作模块标题（如"用户管理"）。
     */
    String title() default "";

    /**
     * 业务类型：1=新增，2=修改，3=删除，4=导出，5=导入，其他=查询。
     */
    int businessType() default 0;

    /**
     * 是否保存请求参数。
     */
    boolean isSaveRequestData() default true;

    /**
     * 是否保存响应结果。
     */
    boolean isSaveResponseData() default true;
}