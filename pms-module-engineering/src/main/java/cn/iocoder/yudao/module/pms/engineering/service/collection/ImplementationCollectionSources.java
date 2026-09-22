package cn.iocoder.yudao.module.pms.engineering.service.collection;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestCollectionOwnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
@Configuration(proxyBeanMethods=false) @RequiredArgsConstructor
public class ImplementationCollectionSources {
    private final ConfigurationCollectionOwnerMapper configurations;
    private final JointTestCollectionOwnerMapper jointTests;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    @Bean public CollectionSourceAdapter configurationCollectionSource(){return source("configuration","Configuration","配置调试",true,"pms:imp-configuration",(id,lock)->{
        var r=lock?configurations.lockById(id):configurations.selectById(id);return r==null?null:new Owner(r.getTenantId(),r.getProjectId(),r.getEquipmentId(),r.getVersion(),Set.of(0,1,3).contains(r.getStatus()));});}
    @Bean public CollectionSourceAdapter jointTestCollectionSource(){return source("joint-test","JointTest","业务联调",true,"pms:imp-joint-test",(id,lock)->{
        var r=lock?jointTests.lockById(id):jointTests.selectById(id);return r==null?null:new Owner(r.getTenantId(),r.getProjectId(),r.getEquipmentId(),r.getVersion(),Set.of(0,1).contains(r.getStatus()));});}
    private CollectionSourceAdapter source(String entry,String type,String title,boolean manual,String permission,Reader reader){
        return new CollectionSourceAdapter(){
            public String entry(){return entry;}
            public Source authorize(Long tenant,Long actor,Long id,Long device,Access access,Integer expectedVersion){
                boolean edit=access!=Access.READ,lock=access==Access.EXECUTE||access==Access.CONSUME;
                if(actor==null||!permissions.hasAnyPermissions(actor,permission+(edit?":update":":query")))throw new AccessDeniedException("无当前业务采集权限");
                Owner row=reader.get(id,lock);if(row==null||!tenant.equals(row.tenant()))throw new CollectionOperationException("业务记录不存在");
                String action=edit?ProjectScopeApi.ACTION_EDIT:ProjectScopeApi.ACTION_VIEW;
                var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.project(),action));
                if(scope==null||!scope.fullProjectIds().contains(row.project()))throw new AccessDeniedException("无当前项目权限");
                if(lock){var current=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.project(),action,scope.treeVersion()));if(current==null||!Objects.equals(scope.treeVersion(),current.treeVersion())||!current.fullProjectIds().contains(row.project()))throw new AccessDeniedException("项目权限已变化");}
                if(device!=null&&!Objects.equals(device,row.device()))throw new CollectionOperationException("设备与当前业务记录不一致");
                if(access==Access.EXECUTE&&(!row.executable()||!Objects.equals(expectedVersion,row.version())))throw new CollectionOperationException("业务记录已变化或不可执行，请刷新");
                return new Source(entry,id,"IMP",type,row.project(),row.device(),"",row.version(),manual,
                        row.executable()&&permissions.hasAnyPermissions(actor,permission+":update"),"BUSINESS_CONSUMPTION",title);
            }
        };
    }
    private record Owner(Long tenant,Long project,Long device,Integer version,boolean executable){}
    private interface Reader{Owner get(Long id,boolean lock);}
}
