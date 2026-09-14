package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncConnectionDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@RequiredArgsConstructor
public class SyncConnectionService {
    private final SyncConnectionMapper mapper;
    private final ExternalDataSourceApi api;
    public record Info(Long id,String name,String url,String username) {}
    public record Save(Long id,Long dataSourceId,String name,String url,String username,String password) {
        @Override public String toString(){return "SyncConnectionSave[id="+id+"]";}
    }
    public PageResult<Info> page(SyncQueries.Page query) {
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        var page=mapper.selectPage(query);
        Map<Long,ExternalDataSourceApi.Info> info=new HashMap<>();
        api.list().forEach(i->info.put(i.id(),i));
        return new PageResult<>(page.getList().stream().map(c->{
            var d=info.get(c.getDataSourceId());
            return new Info(c.getId(),c.getName(),d==null?"":d.url(),d==null?"":d.username());
        }).toList(),page.getTotal());
    }
    @Transactional(rollbackFor=Exception.class)
    public Long save(Save command) {
        if(command.name()==null||command.name().isBlank()||command.name().length()>128)
            throw new IllegalArgumentException("连接名称不能为空或过长");
        var existing=command.id()==null?null:required(command.id());
        Long sourceId=existing==null?command.dataSourceId():existing.getDataSourceId();
        if(command.dataSourceId()!=null && existing==null) {
            if(api.list().stream().noneMatch(c->c.id().equals(command.dataSourceId())))
                throw new IllegalArgumentException("引用数据源不存在");
        } else sourceId=api.save(new ExternalDataSourceApi.Save(sourceId,command.name(),command.url(),command.username(),command.password()));
        var ref=existing==null?new SyncConnectionDO():existing;
        ref.setTenantId(TenantContextHolder.getRequiredTenantId());ref.setName(command.name());ref.setDataSourceId(sourceId);
        if(existing==null)mapper.insert(ref);else mapper.updateById(ref);
        return ref.getId();
    }
    public SyncConnectionDO required(Long id) {
        var c=mapper.selectScoped(new SyncQueries.Id(TenantContextHolder.getRequiredTenantId(),id));
        if(c==null)throw new IllegalArgumentException("集成连接不存在");
        return c;
    }
    public SyncDefinition resolve(SyncDefinition d) {
        Long sourceId=required(d.connectionId()).getDataSourceId();
        return d.toBuilder().connectionId(sourceId).build();
    }
}
