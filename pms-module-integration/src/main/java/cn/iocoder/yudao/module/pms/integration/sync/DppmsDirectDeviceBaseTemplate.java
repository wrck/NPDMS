package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectDeviceBaseTemplate {
    static final String ADAPTER="DPPMS_DEVICE_BOOTSTRAP_DIRECT",BASE="DEVICE_BASE";
    static final java.util.List<String> COLUMNS=DppmsDeviceBootstrapSyncTemplate.COLUMNS;
    private DppmsDirectDeviceBaseTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("设备基础来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P30-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("设备基础直写配置缺失",error);}
    }
}
