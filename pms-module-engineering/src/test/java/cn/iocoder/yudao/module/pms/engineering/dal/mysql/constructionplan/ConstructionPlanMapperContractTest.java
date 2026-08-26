package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructionPlanMapperContractTest {

    @Test
    void shouldExposeOnlyExplicitConstructionPlanPersistenceMethods() {
        assertMapperContract(ConstructionPlanMapper.class,
                Set.of("insert", "selectByProjectId", "selectById", "selectForUpdate", "updateVersionIfMatch"));
        assertMapperContract(ConstructionPlanRevisionMapper.class,
                Set.of("insert", "selectById", "selectForUpdate", "selectLatestForUpdate", "selectPage",
                        "updateDraftIfMatch", "freezeForSubmitIfMatch"));
        assertMapperContract(ConstructionPlanChangeMapper.class,
                Set.of("insert", "selectById", "selectForUpdate", "selectByProcessInstanceId", "selectPage",
                        "updateVersionIfMatch", "updateDraftIfMatch"));
    }

    private static void assertMapperContract(Class<?> mapperType, Set<String> expectedMethods) {
        assertEquals(0, mapperType.getInterfaces().length,
                () -> mapperType.getSimpleName() + " 不得继承通用CRUD接口");
        Set<String> actualMethods = Arrays.stream(mapperType.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(expectedMethods, actualMethods);
    }

}
