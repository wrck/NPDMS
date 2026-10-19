package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectSoftwareHistoryTemplate {
    static final String ADAPTER="DPPMS_SOFTWARE_HISTORY_DIRECT",BATCH=DppmsSoftwareHistorySyncTemplate.BATCH,VERSION=DppmsSoftwareHistorySyncTemplate.VERSION;
    static java.util.List<String> columns(String object){return DppmsSoftwareHistorySyncTemplate.columns(object);}
    static final java.util.List<String> BATCH_COLUMNS=DppmsSoftwareHistorySyncTemplate.BATCH_COLUMNS,VERSION_COLUMNS=DppmsSoftwareHistorySyncTemplate.VERSION_COLUMNS;
    private DppmsDirectSoftwareHistoryTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("软件版本历史来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P35-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("软件版本历史直写配置缺失",error);}
    }
}
