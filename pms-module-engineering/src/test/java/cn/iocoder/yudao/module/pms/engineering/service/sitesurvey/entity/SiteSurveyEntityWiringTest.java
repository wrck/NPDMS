package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.beans.Introspector;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyEntityWiringTest {
    @Test void newOwnerInjectsItsMapperEvenWhenTheLegacyMapperIsStillRegistered() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var legacy = mock(SiteSurveyMapper.class);
            context.registerBean("siteSurveyMapper", SiteSurveyMapper.class, () -> legacy);
            for (var field : SiteSurveyEntityDomainCommands.class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Resource.class)) registerDependency(context, field.getType());
            }
            context.register(SiteSurveyEntityDomainCommands.class);
            assertDoesNotThrow(context::refresh);
            var service = context.getBean(SiteSurveyEntityDomainCommands.class);
            assertSame(context.getBean(SiteSurveyEntityMapper.class), ReflectionTestUtils.getField(service, "siteSurveyEntityMapper"));
            verifyNoInteractions(legacy);
        }
    }

    private <T> void registerDependency(AnnotationConfigApplicationContext context, Class<T> type) {
        if (context.containsBeanDefinition(Introspector.decapitalize(type.getSimpleName()))) return;
        for (var field:type.getDeclaredFields()) if(field.isAnnotationPresent(Resource.class)) registerDependency(context,field.getType());
        context.registerBean(Introspector.decapitalize(type.getSimpleName()), type, () -> mock(type));
    }
}
