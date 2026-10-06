package cn.iocoder.yudao.module.pms.platform.service.businessview;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import lombok.RequiredArgsConstructor;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
/** One deployed view adapter for the existing safe default query/operation endpoints. */
@org.springframework.stereotype.Component @RequiredArgsConstructor
public class DeclaredBusinessViewProviderFactory {
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessOperationDispatcher operations;
    private final BusinessViewAccess access;
    public List<BusinessViewComponentProvider> providers() {
        List<BusinessViewComponentProvider> result=new ArrayList<>();
        for(var declaration:persistence.declarations()) {
            var model=declaration.descriptor();
            if(model.scopeBinding()==null) continue;
            try { operations.capabilityService(model.ownerModule(),model.entityType()); }
            catch(cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException nativeOwner){ continue; }
            String key="DECLARED_BUSINESS_"+model.stableCode();
            var actions=new LinkedHashSet<String>();actions.add("QUERY");model.operations().forEach(operation->actions.add(operation.code()));
            var component=new BusinessViewComponentProvider.Component(model.entityType(),model.ownerModule(),BusinessViewComponentProvider.ViewSource.PAGE,key,"1",
                JsonUtils.parseTree("{\"type\":\"object\",\"properties\":{\"businessObjectId\":{\"oneOf\":[{\"type\":\"integer\",\"minimum\":1},{\"type\":\"string\",\"pattern\":\"^[1-9][0-9]*$\"}]}},\"required\":[\"businessObjectId\"]}"),
                JsonUtils.parseTree(JsonUtils.toJsonString(actions)),key+"_QUERY",key+"_COMMAND",key+"_PERMISSION",model.title());
            result.add(new BusinessViewComponentProvider() {
                @Override public Component component(){return component;}
                @Override public boolean canConfigure(Context context,ConfigurationAction action){return access.has(context,action.name().toLowerCase(Locale.ROOT));}
                @Override public Dependencies validateConfiguration(Context context,Long revision,ValidationMode mode){
                    if(!access.context().equals(context) || revision!=null || mode==null) throw exception(BusinessViewErrors.UNAVAILABLE);
                    operations.capabilityService(model.ownerModule(),model.entityType());
                    return new Dependencies(false);
                }
            });
        }
        return List.copyOf(result);
    }
}
