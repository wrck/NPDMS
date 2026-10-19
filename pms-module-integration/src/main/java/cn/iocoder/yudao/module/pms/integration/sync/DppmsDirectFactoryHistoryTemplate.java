package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectFactoryHistoryTemplate {
    static final String ADAPTER="DPPMS_FACTORY_HISTORY_DIRECT",OBJECT=DppmsFactoryHistorySyncTemplate.OBJECT;
    static final java.util.List<String> COLUMNS=DppmsFactoryHistorySyncTemplate.COLUMNS;
    private DppmsDirectFactoryHistoryTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("出厂版本历史来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P34-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("出厂版本历史直写配置缺失",error);}
    }
}
