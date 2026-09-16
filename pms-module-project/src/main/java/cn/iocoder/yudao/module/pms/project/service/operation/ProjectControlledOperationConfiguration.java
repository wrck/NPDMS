package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import java.lang.reflect.Method;

/** Spring AOP only; the transactional executor encloses the complete invocation, including POST. */
@Configuration(proxyBeanMethods = false)
public class ProjectControlledOperationConfiguration {
    private static ProjectExecutionControlled annotation(Method method, Class<?> type) {
        return AnnotatedElementUtils.findMergedAnnotation(ClassUtils.getMostSpecificMethod(method, type), ProjectExecutionControlled.class);
    }
    @Bean
    public Advisor projectControlledOperationAdvisor(ObjectProvider<ProjectControlledOperationExecutor> executors) {
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method, Class<?> targetClass) { return annotation(method, targetClass) != null; }
        };
        MethodInterceptor advice = invocation -> {
            var target = invocation.getThis();
            if (target == null || invocation.getArguments().length != 1 || !(invocation.getArguments()[0] instanceof ProjectOperationCommand request))
                throw new IllegalArgumentException("CONTROLLED_METHOD_SIGNATURE_INVALID");
            var control = annotation(invocation.getMethod(), target.getClass());
            if (control == null) throw new IllegalStateException("CONTROLLED_DECLARATION_MISSING");
            return executors.getObject().execute(control.operation(), control.version(), request, current -> {
                invocation.getArguments()[0] = current;
                return (ProjectOperationResult) invocation.proceed();
            });
        };
        var advisor = new DefaultPointcutAdvisor(pointcut, advice);
        advisor.setOrder(100);
        return advisor;
    }
}
