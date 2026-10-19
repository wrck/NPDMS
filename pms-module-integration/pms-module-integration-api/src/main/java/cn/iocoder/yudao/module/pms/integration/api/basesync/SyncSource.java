package cn.iocoder.yudao.module.pms.integration.api.basesync;

/** Only an existing tenant-scoped connection ID is accepted; no credentials or SQL in the API. */
public interface SyncSource {
    Session open(long connectionId);
    default boolean local() { return false; }
    /** Borrow the Spring-managed target for local-to-local reads; never close or commit it. */
    static SyncSource local(org.apache.ibatis.session.SqlSession target) {
        java.util.Objects.requireNonNull(target);
        return new SyncSource() {
            public boolean local() { return true; }
            public Session open(long connectionId) {
                return new Session() {
                    public org.apache.ibatis.session.SqlSession sqlSession() { return target; }
                    public <M> M mapper(Class<M> type) { return target.getMapper(type); }
                    public void close() { /* Spring owns this session. */ }
                };
            }
        };
    }
    interface Session extends AutoCloseable {
        org.apache.ibatis.session.SqlSession sqlSession();
        <M> M mapper(Class<M> mapperType);
        @Override void close();
    }
}
