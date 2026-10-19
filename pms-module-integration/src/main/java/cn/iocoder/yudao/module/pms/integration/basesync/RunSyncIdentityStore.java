package cn.iocoder.yudao.module.pms.integration.basesync;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncIdentityStore;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.List;
final class RunSyncIdentityStore implements SyncIdentityStore {
    private final BaseSyncMetadataMapper mapper;
    private final BaseSyncMetadataMapper.RunRow run;
    RunSyncIdentityStore(BaseSyncMetadataMapper mapper,BaseSyncMetadataMapper.RunRow run) { this.mapper=mapper; this.run=run; }
    @Override public List<Identity> find(Query query) {
        validateObject(query.object());
        return mapper.selectBindings(new BaseSyncMetadataMapper.BindingQuery(run.tenantId(),run.taskId(),query.object(),query.sourceKeys(),run.preview()));
    }
    @Override public void save(Save identity) {
        if(run.preview()) throw new IllegalStateException("预览禁止写入来源绑定");
        validateObject(identity.object());
        if(identity.sourceKey()==null || identity.sourceKey().isBlank() || identity.sourceKey().length()>128
                || identity.targetId()<=0 || identity.fingerprint()==null || !identity.fingerprint().matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("来源绑定身份无效");
        var row=new BaseSyncMetadataMapper.BindingWrite(IdWorker.getId(),run.tenantId(),run.taskId(),run.id(),identity.object(),
                identity.sourceKey(),identity.targetId(),identity.fingerprint(),identity.previousFingerprint(),run.actor());
        int changed=identity.previousFingerprint()==null?mapper.insertBinding(row):mapper.updateBinding(row);
        if(changed!=1) throw new IllegalStateException("来源绑定发生并发变化");
    }
    private static void validateObject(String object) {
        if(object==null || !object.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("来源对象编码无效");
    }
}
