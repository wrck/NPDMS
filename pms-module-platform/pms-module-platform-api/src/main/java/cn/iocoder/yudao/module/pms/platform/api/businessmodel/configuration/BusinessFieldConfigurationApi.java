package cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import java.util.List;

/** Shared presentation metadata only. Business bodies remain in their typed business tables. */
public interface BusinessFieldConfigurationApi {
    record Identity(String ownerModule,String entityType) { }
    record Field(String code,String label,Integer displayOrder,Boolean listVisible,Boolean searchable,Boolean sortable) { }
    record Configuration(long version,List<Field> fields) {
        public Configuration { fields=List.copyOf(fields); }
    }
    Configuration read(Identity identity,EntityActor actor);
    Configuration save(Identity identity,EntityActor actor,long expectedVersion,List<Field> fields);
}
