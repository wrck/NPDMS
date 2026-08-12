package cn.iocoder.yudao.server.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * PMS MyBatis-Plus 配置（FR-RES-002 / 项目域乐观锁统一装配）。
 * <p>
 * Yudao 默认 {@link MybatisPlusInterceptor} 仅注册分页插件，未启用乐观锁；
 * PMS 业务模块的 DO（{@code @Version}）依赖 {@link OptimisticLockerInnerInterceptor}
 * 才能正确解析 {@code MP_OPTLOCK_VERSION_ORIGINAL} 参数。
 * <p>
 * 本配置通过 {@link BeanPostProcessor} 在 Yudao 的
 * {@link MybatisPlusInterceptor} 初始化后追加乐观锁插件，避免修改 Yudao 上游源码，
 * 同时保持分页插件原有行为不变。
 */
@Configuration
public class PmsMybatisConfiguration implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MybatisPlusInterceptor interceptor) {
            List<InnerInterceptor> combined = new ArrayList<>();
            combined.add(new OptimisticLockerInnerInterceptor());
            combined.addAll(interceptor.getInterceptors());
            interceptor.setInterceptors(combined);
        }
        return bean;
    }
}
