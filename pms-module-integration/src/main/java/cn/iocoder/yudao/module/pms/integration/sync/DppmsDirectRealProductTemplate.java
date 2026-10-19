package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectRealProductTemplate {
    static final String ADAPTER="DPPMS_PROJECT_REAL_PRODUCT_DIRECT",OBJECT="pm_project_product_line_real";
    static java.util.List<String> columns(){return DppmsProjectRealProductSyncTemplate.columns();}
    private DppmsDirectRealProductTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("真实产品清单来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P20-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("真实产品清单直写配置缺失",error);}
    }
}
