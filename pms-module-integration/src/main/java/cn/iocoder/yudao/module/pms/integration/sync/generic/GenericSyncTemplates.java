package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class GenericSyncTemplates {
    public record Template(String key,String name,SyncDefinition definition) {}
    private final List<Template> templates;
    public GenericSyncTemplates() {
        try(var in=new ClassPathResource("sync/generic-templates.json").getInputStream()) {
            templates=List.of(JsonUtils.parseObject(in.readAllBytes(),Template[].class));
        }catch(IOException e){throw new IllegalStateException("通用同步模板读取失败",e);}
    }
    public List<Template> list(Long connectionId) {
        return templates.stream().map(t->new Template(t.key(),t.name(),t.definition().toBuilder().connectionId(connectionId).build())).toList();
    }
}
