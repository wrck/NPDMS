package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectInstallationTemplate {
    static final String ADAPTER="DPPMS_INSTALLATION_HISTORY_DIRECT",OBJECT="INSTALLATION_HISTORY";
    private DppmsDirectInstallationTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("安装历史来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P33-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("安装历史直写配置缺失",error);}
    }
}
