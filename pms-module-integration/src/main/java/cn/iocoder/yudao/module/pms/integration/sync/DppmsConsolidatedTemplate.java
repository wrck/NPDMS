package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Reviewed task definitions; expanded SQL is frozen in the saved task version. */
public final class DppmsConsolidatedTemplate {
    private DppmsConsolidatedTemplate() {}
    public record Request(String taskKey,Long connectionId) {}
    public static SyncDefinition create(Request request) {
        if(request==null || !Set.of("P01","P08","P14","P15","P21","P23").contains(request.taskKey())
                || request.connectionId()==null || request.connectionId()<=0)throw new IllegalArgumentException("合并任务或来源连接无效");
        try(var input=new ClassPathResource("migration/dppms/tasks/"+request.taskKey()+".json").getInputStream()) {
            var definition=JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class);
            definition.connectionId(request.connectionId());
            for(var source:definition.sources()) if(source.sql()!=null && source.sql().startsWith("@dppms:"))
                source.sql(DppmsMigrationSql.initial(source.sql().substring(7)));
            return definition;
        }catch(java.io.IOException error){throw new IllegalStateException("合并任务初始配置缺失",error);}
    }
}
