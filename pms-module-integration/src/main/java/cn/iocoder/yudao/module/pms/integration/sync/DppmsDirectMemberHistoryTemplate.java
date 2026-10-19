package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
public final class DppmsDirectMemberHistoryTemplate {
    static final String ADAPTER="DPPMS_MEMBER_HISTORY_DIRECT",MEMBERS="MEMBER_HISTORY",CONTACTS="CONTACT_HISTORY";
    static java.util.List<String> columns(){return DppmsMemberHistorySyncTemplate.columns();}
    private DppmsDirectMemberHistoryTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("成员及联系人历史来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/P31-direct.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("成员及联系人历史直写配置缺失",error);}
    }
}
