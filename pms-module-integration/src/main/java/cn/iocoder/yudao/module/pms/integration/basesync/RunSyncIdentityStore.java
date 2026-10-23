package cn.iocoder.yudao.module.pms.integration.basesync;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncIdentityStore;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
final class RunSyncIdentityStore implements SyncIdentityStore {
    private final BaseSyncMetadataMapper mapper;
    private final BaseSyncMetadataMapper.RunRow run;
    RunSyncIdentityStore(BaseSyncMetadataMapper mapper,BaseSyncMetadataMapper.RunRow run) { this.mapper=mapper; this.run=run; }
    @Override public List<Identity> find(Query query) {
        validateObject(query.object());
        return mapper.selectBindings(new BaseSyncMetadataMapper.BindingQuery(run.tenantId(),run.taskId(),query.object(),query.sourceKeys(),run.preview()));
    }
    @Override public void save(Save identity) {
        var row=write(identity);
        int changed=identity.previousFingerprint()==null?mapper.insertBinding(row):mapper.updateBinding(row);
        if(changed!=1) throw new IllegalStateException("来源绑定发生并发变化");
    }
    @Override public void saveAll(List<Save> identities) {
        if(identities.isEmpty()) return;
        int batchSize=run.config().batchSize();
        var pending=new ArrayList<BaseSyncMetadataMapper.BindingWrite>(batchSize);
        var keys=new HashSet<BindingKey>();
        for(var identity:identities) {
            // Split repeated PAD SPACE keys to retain sequential compare-and-set semantics; raw values are untouched.
            var key=new BindingKey(identity.object(),identity.sourceKey()==null?null:identity.sourceKey().stripTrailing());
            if(!pending.isEmpty() && ((pending.getFirst().previousFingerprint()==null)!=(identity.previousFingerprint()==null)
                    || keys.contains(key))) { flush(pending); keys.clear(); }
            pending.add(write(identity));keys.add(key);
            if(pending.size()==batchSize) { flush(pending);keys.clear(); }
        }
        flush(pending);
    }
    private record BindingKey(String object,String sourceKey) {}
    private void flush(List<BaseSyncMetadataMapper.BindingWrite> pending) {
        if(pending.isEmpty()) return;
        var batch=new BaseSyncMetadataMapper.BindingBatch(List.copyOf(pending));
        int changed=pending.getFirst().previousFingerprint()==null?mapper.insertBindings(batch):mapper.updateBindings(batch);
        if(changed!=pending.size())
            throw new IllegalStateException("来源绑定发生并发变化");
        pending.clear();
    }
    private BaseSyncMetadataMapper.BindingWrite write(Save identity) {
        if(run.preview()) throw new IllegalStateException("预览禁止写入来源绑定");
        validateObject(identity.object());
        if(identity.sourceKey()==null || identity.sourceKey().isBlank() || identity.sourceKey().length()>128
                || identity.targetId()<=0 || identity.fingerprint()==null || !identity.fingerprint().matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("来源绑定身份无效");
        return new BaseSyncMetadataMapper.BindingWrite(IdWorker.getId(),run.tenantId(),run.taskId(),run.id(),identity.object(),
                identity.sourceKey(),identity.targetId(),identity.fingerprint(),identity.previousFingerprint(),run.actor());
    }
    private static void validateObject(String object) {
        if(object==null || !object.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("来源对象编码无效");
    }
}
