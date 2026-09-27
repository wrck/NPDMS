package cn.iocoder.yudao.module.pms.platform.testassembly;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试装配镜像生产装配（yudao-server PmsMybatisConfiguration）：
 * Yudao 默认 MybatisPlusInterceptor 仅注册分页插件；PMS 的 {@code @Version} DO
 * 依赖 {@link OptimisticLockerInnerInterceptor} 解析 {@code MP_OPTLOCK_VERSION_ORIGINAL}。
 */
@Configuration
public class TestAssemblyMybatisConfiguration implements BeanPostProcessor {

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
