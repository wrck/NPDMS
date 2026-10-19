package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectWarrantyTemplate {
    static final String ADAPTER="DPPMS_WARRANTY_RENEWAL_DIRECT",OBJECT="fb_service";
    private DppmsDirectWarrantyTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("维保延期来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P25-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("维保延期直写配置缺失",error);}
    }
}
