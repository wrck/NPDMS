package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessController;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Component;
import java.util.*;

/** Discovers deployed typed Controller routes, not client-supplied URLs or generic command adapters. */
@Component @RequiredArgsConstructor
public class DirectBusinessViews {
    public static final String PREFIX="DIRECT_BUSINESS_";
    private final ObjectProvider<ProjectBusinessController<?,?>> controllers;
    private final BusinessViewAccess access;
    private record Entry(String base,ProjectBusinessService<?> service) { }
    public record View(String ownerModule,String entityType,String stableCode,String apiBase) { }
    private List<Entry> entries(){
        var result=new ArrayList<Entry>();var codes=new HashSet<String>();
        controllers.orderedStream().forEach(controller->{
            var mapping=AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(controller),RequestMapping.class);
            if(mapping==null || mapping.path().length+mapping.value().length==0)return;
            var paths=mapping.path().length>0?mapping.path():mapping.value();
            if(paths.length!=1 || !paths[0].matches("/api/v1/pms/[A-Za-z0-9_/-]+"))return;
            var service=controller.businessService();if(!codes.add(service.definition().stableCode()))throw new IllegalStateException("DIRECT_BUSINESS_ROUTE_AMBIGUOUS");
            result.add(new Entry(paths[0].replaceAll("/$",""),service));
        });return List.copyOf(result);
    }
    public View view(String code){
        var entry=entries().stream().filter(value->value.service().definition().stableCode().equals(code)).findFirst().orElseThrow(()->new IllegalArgumentException("DIRECT_BUSINESS_VIEW_UNAVAILABLE"));
        var model=entry.service().model(); // Actual business query permission, not merely a registered view.
        return new View(model.ownerModule(),model.entityType(),model.stableCode(),entry.base());
    }
    public List<BusinessViewComponentProvider> providers(){
        return entries().stream().map(entry->{
            var model=entry.service().definition();var key=PREFIX+model.stableCode();var actions=new LinkedHashSet<String>();actions.add("QUERY");model.operations().forEach(op->actions.add(op.code()));
            var component=new BusinessViewComponentProvider.Component(model.entityType(),model.ownerModule(),BusinessViewComponentProvider.ViewSource.PAGE,key,"1",
                    JsonUtils.parseTree("{\"type\":\"object\",\"required\":[\"projectId\"],\"properties\":{\"projectId\":{\"oneOf\":[{\"type\":\"integer\",\"minimum\":1},{\"type\":\"string\",\"pattern\":\"^[1-9][0-9]*$\"}]}}}"),
                    JsonUtils.parseTree(JsonUtils.toJsonString(actions)),key+"_QUERY",key+"_COMMAND",key+"_PERMISSION",model.title());
            return (BusinessViewComponentProvider)new BusinessViewComponentProvider(){
                public Component component(){return component;}
                public boolean canConfigure(Context context,ConfigurationAction action){return access.has(context,action.name().toLowerCase(Locale.ROOT));}
                public Dependencies validateConfiguration(Context context,Long revision,ValidationMode mode){
                    if(!access.context().equals(context) || revision!=null || mode==null || entries().stream().noneMatch(current->current.base().equals(entry.base()) && current.service()==entry.service()))throw new IllegalArgumentException("DIRECT_BUSINESS_VIEW_UNAVAILABLE");
                    return new Dependencies(false);
                }
            };
        }).toList();
    }
}
