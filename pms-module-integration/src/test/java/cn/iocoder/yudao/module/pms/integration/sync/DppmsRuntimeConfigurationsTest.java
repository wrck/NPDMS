package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.GenericSyncJdbcStore;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericSyncEngine;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Validate the actual review/save payloads against current server descriptors and SQL restrictions. */
class DppmsRuntimeConfigurationsTest {
    @Test void reviewableConfigurationsUseCurrentSqlAndAvailableServerDescriptors() throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null && !Files.isDirectory(root.resolve("docs/generated/2026-10-13-dppms-migration-mapping")))root=root.getParent();
        assertNotNull(root);
        Path source=root.resolve("pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync");
        var adapters=new ArrayList<DataSyncAdapter>();
        try(var files=Files.list(source)) {
            for(Path path:files.filter(p->p.getFileName().toString().endsWith("Adapter.java")).toList()) {
                String name=path.getFileName().toString().replace(".java","");
                var type=Class.forName(getClass().getPackageName()+"."+name);
                if(DataSyncAdapter.class.isAssignableFrom(type))adapters.add((DataSyncAdapter)construct(type));
            }
        }
        var validator=new SyncDefinitionValidator(adapters);
        ReflectionTestUtils.setField(validator,"genericEngine",new GenericSyncEngine(new GenericTargetCatalog(),
                mock(GenericSyncJdbcStore.class),mock(ApplicationEventPublisher.class)));
        Path configurations=root.resolve("docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/runtime-configurations");
        try(var files=Files.list(configurations)) {
            var paths=files.filter(p->p.toString().endsWith(".json")).sorted().toList();assertEquals(21,paths.size());
            var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
            for(Path path:paths) {
                String payload=Files.readString(path);
                var wrapper=JsonUtils.parseMap(payload);
                var definition=JsonUtils.parseObject(JsonUtils.parseTree(payload).path("definition").toString(),SyncDefinition.class).connectionId(18L);
                assertEquals(Boolean.FALSE,wrapper.get("enabled"),path.toString());
                // COM adapters are supplied by server assembly, not an INT dependency on COM implementation.
                // Their full descriptor check is performed again by the real task-save API.
                if(Set.of("P13.json","P14.json").contains(path.getFileName().toString()))
                    for(var item:definition.sources())checks.add(()->assertDoesNotThrow(()->MysqlSyncReader.compile(item),path.getFileName().toString()));
                else checks.add(()->assertDoesNotThrow(()->validator.validate(definition),path.getFileName().toString()));
            }
            assertAll(checks);
        }
    }
    private static Object construct(Class<?> type) throws Exception {
        var constructor=Arrays.stream(type.getConstructors()).min(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
        var parameters=constructor.getParameterTypes();Object[] values=new Object[parameters.length];
        for(int i=0;i<parameters.length;i++)values[i]=DataSyncAdapter.class.isAssignableFrom(parameters[i])?construct(parameters[i]):mock(parameters[i]);
        return constructor.newInstance(values);
    }
}
