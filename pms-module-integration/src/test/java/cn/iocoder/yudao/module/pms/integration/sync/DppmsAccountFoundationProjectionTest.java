package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsAccountFoundationProjectionTest {
    private List<MysqlSyncReader.SourceRows> expand(String object,Map<String,Object> group){
        var definition=DppmsAccountFoundationSyncTemplate.create(9L);
        var source=definition.sources().stream().filter(item->object.equals(item.object())).findFirst().orElseThrow();
        return SyncSourceProjection.expand(new SyncSourceProjection.Input(definition,source,List.of(group)));
    }
    @Test void accountAliasesRetainNullsAndOriginalJsonForBothAccountAndRoleConsumers(){
        var result=expand("t_user",Map.of("businessKey","633032373931","username","c02791","ehrVerification","""
                {"loginCount":null,"employeeId":null,"workNo":null,"name":null,
                 "verifiedEmployeeId":null,"verifiedWorkNo":null,"verifiedName":null}
                ""","sourceAliases","""
                [{"sourceTable":"fnd_user_info","sourceId":"7","fields":{"id":7,"username":"c02791",
                "customInfo":{"old":null,"values":[1,null]},"updateBy":null}}]
                """));
        var account=result.stream().filter(item->"fnd_user_info".equals(item.object())).findFirst().orElseThrow().rows().getFirst();
        var roles=result.stream().filter(item->"FND_USER_ROLE".equals(item.object())).findFirst().orElseThrow().rows().getFirst();
        assertEquals(account,roles);assertTrue(account.containsKey("migrationLoginCount"));assertNull(account.get("migrationLoginCount"));
        assertTrue(account.containsKey("updateBy"));assertNull(account.get("updateBy"));
        assertEquals("{\"old\":null,\"values\":[1,null]}",account.get("customInfo"));
    }
    private Map<String,Object> role(){
        return new LinkedHashMap<>(Map.of("code","projectViewer","businessKey","70726F6A656374566965776572","definitionVariants",1,
                "canonicalFields","""
                {"code":"projectViewer","name":"原名称","sort":106,"remark":null,"rawStatus":1,
                 "creatorAccount":null,"createTime":"2015-03-24 11:22:00","updaterAccount":null,"updateTime":null}
                ""","sourceAliases","[{\"table\":\"fnd_roles\",\"id\":6,\"sourceCode\":\"projectViewer\",\"rawStatus\":1}]"));
    }
    @Test void roleCanonicalDefinitionUsesApprovedCodeAndNullAuditWithoutEnablingUnknownStatus(){
        var group=role();var fields=expand("T_ROLE",group).stream().filter(item->"FND_ROLE".equals(item.object())).findFirst().orElseThrow().rows().getFirst();
        assertEquals("原名称",fields.get("name"));assertNull(fields.get("creatorName"));assertTrue(fields.containsKey("creatorName"));
        group.put("sourceAliases","[{\"table\":\"fnd_roles\",\"id\":6,\"sourceCode\":\"projectViewer\",\"rawStatus\":null}]");
        assertThrows(IllegalArgumentException.class,()->expand("T_ROLE",group));
    }
    @Test void conflictingDefinitionsOrUnknownRoleIdentityBlockBeforeOwners(){
        var group=role();group.put("definitionVariants",2);assertThrows(IllegalArgumentException.class,()->expand("T_ROLE",group));
        group.put("definitionVariants",1);group.put("sourceAliases","[{\"table\":\"fnd_roles\",\"id\":999,\"sourceCode\":\"projectViewer\",\"rawStatus\":1}]");
        assertThrows(IllegalArgumentException.class,()->expand("T_ROLE",group));
    }
}
