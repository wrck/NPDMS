package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionPlanMapperContractTest {

    @Test
    void shouldExposeOnlyExplicitConstructionPlanPersistenceMethods() {
        // P12-B1 统一业务模型：施工计划主实体接入统一持久化，Mapper 必须继承 BaseMapperX；
        // 未接入的修订与变更 Mapper 仍保持无通用 CRUD 的显式方法契约。
        assertUnifiedMapperContract(ConstructionPlanMapper.class,
                Set.of("insertRow", "selectByProjectId", "selectByLockQuery", "selectForUpdate", "updateVersionIfMatch"));
        assertMapperContract(ConstructionPlanRevisionMapper.class,
                Set.of("insert", "selectById", "selectListByIds", "selectForUpdate", "selectLatestForUpdate", "selectPage",
                        "updateDraftIfMatch", "freezeForSubmitIfMatch"));
        assertMapperContract(ConstructionPlanChangeMapper.class,
                Set.of("insert", "selectById", "selectForUpdate", "selectByProcessInstanceId", "selectPage",
                        "selectByObjectId", "updateVersionIfMatch", "updateDraftIfMatch"));
    }

    private static void assertUnifiedMapperContract(Class<?> mapperType, Set<String> expectedDeclaredMethods) {
        assertTrue(BaseMapperX.class.isAssignableFrom(mapperType),
                () -> mapperType.getSimpleName() + " 必须继承 BaseMapperX 以接入统一持久化");
        Set<String> declaredMethods = Arrays.stream(mapperType.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(expectedDeclaredMethods, declaredMethods);
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
