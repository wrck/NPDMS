package cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import java.util.List;

/** Shared presentation metadata only. Business bodies remain in their typed business tables. */
public interface BusinessFieldConfigurationApi {
    record Identity(String ownerModule,String entityType) { }
    record Field(String code,String label,Integer displayOrder,Boolean listVisible,Boolean searchable,Boolean sortable,
                 Boolean formVisible,Boolean tabVisible) {
        public Field(String code,String label,Integer displayOrder,Boolean listVisible,Boolean searchable,Boolean sortable){
            this(code,label,displayOrder,listVisible,searchable,sortable,null,null);
        }
    }
    /** Optional shared panels only; native domain requirements and authorizations remain authoritative. */
    record Features(boolean attachments,boolean approvals) { }
    record Named(String key,String name,List<Field> fields,Long formRevisionId,Features features) {
        public Named { fields=fields==null?List.of():List.copyOf(fields); }
        public Named(String key,String name,List<Field> fields,Long formRevisionId){this(key,name,fields,formRevisionId,null);}
    }
    record Configuration(long version,List<Field> fields,List<Named> profiles,String defaultProfile,Features features) {
        public Configuration { fields=List.copyOf(fields);profiles=profiles==null?List.of():List.copyOf(profiles);features=features==null?new Features(false,false):features; }
        public Configuration(long version,List<Field> fields,List<Named> profiles,String defaultProfile){this(version,fields,profiles,defaultProfile,null);}
        public Configuration(long version,List<Field> fields){this(version,fields,List.of(),null);}
    }
    Configuration read(Identity identity,EntityActor actor);
    Configuration save(Identity identity,EntityActor actor,long expectedVersion,List<Field> fields);
    Configuration save(Identity identity,EntityActor actor,Configuration configuration);
}
