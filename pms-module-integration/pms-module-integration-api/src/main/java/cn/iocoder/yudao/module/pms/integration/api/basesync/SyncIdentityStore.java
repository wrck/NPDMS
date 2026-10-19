package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.util.List;

/** Source identity and a target fingerprint; never stores business payloads or credentials. */
public interface SyncIdentityStore {
    record Query(String object, List<String> sourceKeys) {
        public Query { sourceKeys=List.copyOf(sourceKeys); }
    }
    record Identity(String sourceKey, long targetId, String fingerprint) {}
    record Save(String object, String sourceKey, long targetId, String fingerprint, String previousFingerprint) {}
    List<Identity> find(Query query);
    void save(Save identity);
    default java.util.Map<String,Identity> findByKeys(Query query) {
        var selected=new java.util.HashSet<>(query.sourceKeys());
        var result=new java.util.HashMap<String,SyncIdentityStore.Identity>();
        if(selected.isEmpty()) return result;
        for(var identity:find(query))
            if(identity.targetId()<=0 || !selected.contains(identity.sourceKey()) || result.putIfAbsent(identity.sourceKey(),identity)!=null)
                throw new IllegalStateException("独立同步来源绑定不唯一或超出查询范围");
        return result;
    }

    default void saveAll(List<Save> rows) { rows.forEach(this::save); }
}
