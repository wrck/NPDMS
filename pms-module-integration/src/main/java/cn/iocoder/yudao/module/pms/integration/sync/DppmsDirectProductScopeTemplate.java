package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectProductScopeTemplate {
    static final String ADAPTER="DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT",OBJECT="pm_project_product_line";
    static final java.util.List<String> COLUMNS=DppmsProjectProductScopeSyncTemplate.COLUMNS;
    private DppmsDirectProductScopeTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目主档来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P22-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("项目主档直写配置缺失",error);}
    }
}
