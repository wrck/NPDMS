package cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProjectTemplateMatchHistoryMapperContractTest {

    @Test
    void mapperExposesOnlyAppendAndReadOperations() {
        assertFalse(BaseMapper.class.isAssignableFrom(ProjectTemplateMatchHistoryMapper.class));
        Set<String> methods = Arrays.stream(ProjectTemplateMatchHistoryMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("insert", "selectCountPage", "selectListPage", "selectPage"), methods);
    }
}
