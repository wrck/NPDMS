package com.dp.plat.lowcode.engine.microflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 服务调用节点执行器：通过 Spring ApplicationContext 反射调用 bean.method(args)。
 *
 * <p>节点 config: {beanName: "xxx", methodName: "xxx", args: [...], target: "结果变量名"}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallServiceExecutor implements MicroflowNodeExecutor {

    private final ApplicationContext applicationContext;

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.CALL_SERVICE;
    }

    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String beanName = (String) config.get("beanName");
        String methodName = (String) config.get("methodName");
        if (beanName == null || methodName == null) return null;
        Object bean = applicationContext.getBean(beanName);
        try {
            Object[] args = config.containsKey("args")
                    ? ((List<?>) config.get("args")).toArray()
                    : new Object[0];
            java.lang.reflect.Method method = bean.getClass().getMethod(methodName);
            Object result = method.invoke(bean, args);
            String target = (String) config.get("target");
            if (target != null) context.setVariable(target, result);
            log.debug("CallServiceExecutor: {}.{}() = {}", beanName, methodName, result);
        } catch (Exception e) {
            throw new RuntimeException("服务调用失败: " + beanName + "." + methodName, e);
        }
        return null;
    }
}
