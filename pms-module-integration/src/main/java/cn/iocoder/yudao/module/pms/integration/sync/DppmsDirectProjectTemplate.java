package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectProjectTemplate {
    static final String ADAPTER="DPPMS_PROJECT_MASTER_DIRECT",OBJECT="pm_project_header";
    static final java.util.List<String> ORIGINAL_COLUMNS=DppmsProjectMasterSyncTemplate.ORIGINAL_COLUMNS;
    static final java.util.List<String> ACTOR_COLUMNS=DppmsProjectMasterSyncTemplate.ACTOR_COLUMNS;
    static final java.util.List<String> ACTOR_REFERENCE_COLUMNS=DppmsProjectMasterSyncTemplate.ACTOR_REFERENCE_COLUMNS;
    static final java.util.Set<String> DATES=DppmsProjectMasterSyncTemplate.DATES;
    private DppmsDirectProjectTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目主档来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P16-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("项目主档直写配置缺失",error);}
    }
}
