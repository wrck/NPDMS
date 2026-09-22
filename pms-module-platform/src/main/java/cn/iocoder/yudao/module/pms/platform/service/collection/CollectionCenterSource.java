package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
@Component @RequiredArgsConstructor
public class CollectionCenterSource implements CollectionSourceAdapter {
    private final CollectionAuthorization auth;
    private final ProjectDeviceSelectionApi devices;
    @Override public String entry(){return "center";}
    @Override public Source authorize(Long tenant,Long actor,Long project,Long device,Access access,Integer expectedVersion){
        if(!CollectionAuthorization.tenant().equals(tenant))throw new CollectionOperationException("租户不匹配");
        boolean edit=access!=Access.READ;
        auth.permission(actor,edit?"pms:device-collection:execute":"pms:device-collection:query");
        auth.project(actor,project,edit,access==Access.EXECUTE);
        String name=device==null?"":devices.validateSelection(project,List.of(device)).getFirst().name();
        return new Source(entry(),project,"PLT","CollectionCenter",project,device,name,0,true,
                auth.allowed(actor,"pms:device-collection:execute"),"CALLBACK_TERMINAL","设备连接与采集");
    }
}
