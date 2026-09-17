package cn.iocoder.yudao.module.pms.engineering.domain.briefing;

import java.util.Objects;

/**
 * SOL 工程交底迁移聚合：独占单个交底对象的身份、版本校验和业务状态。
 * 不拥有项目、模板或文件；乐观锁计数由持久化层在成功写入时递增一次。
 * 本类型承接旧对象行为，不把数据库版本号冒充 PRE-05 的不可变文档修订。
 */
public record BriefingAggregate(Identity identity, int version, State state) {
    public BriefingAggregate {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(state, "state");
        if (version < 0) throw new Rejected(Reason.VERSION_CONFLICT);
    }

    public record Identity(Long tenantId, Long projectId, Long id, String code) {
        public Identity {
            if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                    || (id != null && id <= 0) || code == null || code.isBlank() || code.length() > 64)
                throw new Rejected(Reason.INVALID_IDENTITY);
        }
    }

    public enum State {
        DRAFT(0), GENERATED(1), AUDITED(2), PUBLISHED(3), TERMINATED(4);
        private final int code;
        State(int code) { this.code = code; }
        public int code() { return code; }
        public static State from(Integer code) {
            if (code != null) for (State value : values()) if (value.code == code) return value;
            throw new Rejected(Reason.STATE_NOT_ALLOWED);
        }
    }

    public enum Reason { INVALID_IDENTITY, IDENTITY_CHANGED, VERSION_CONFLICT, STATE_NOT_ALLOWED }
    public static final class Rejected extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        public Rejected(Reason reason) { super("BRIEFING_" + reason.name()); this.reason = reason; }
        public Reason reason() { return reason; }
    }

    public static BriefingAggregate draft(Long tenantId, Long projectId, String code) {
        return new BriefingAggregate(new Identity(tenantId, projectId, null, code), 0, State.DRAFT);
    }

    public static BriefingAggregate restore(Identity identity, Integer version, Integer status) {
        if (identity.id() == null) throw new Rejected(Reason.INVALID_IDENTITY);
        if (version == null) throw new Rejected(Reason.VERSION_CONFLICT);
        return new BriefingAggregate(identity, version, State.from(status));
    }

    public void requireVersion(Integer expected) {
        if (expected == null || expected != version || version == Integer.MAX_VALUE)
            throw new Rejected(Reason.VERSION_CONFLICT);
    }

    public void requireDraft() { require(State.DRAFT); }

    public void requireEditable(Long projectId, String code, Integer expectedVersion) {
        requireDraft();
        requireVersion(expectedVersion);
        if (!Objects.equals(identity.projectId(), projectId) || !Objects.equals(identity.code(), code))
            throw new Rejected(Reason.IDENTITY_CHANGED);
    }

    public void requireGeneratable(Integer expectedVersion) {
        require(State.DRAFT);
        requireVersion(expectedVersion);
    }

    public BriefingAggregate generated(Integer expectedVersion, BriefingDocumentArtifact artifact) {
        requireGeneratable(expectedVersion);
        Objects.requireNonNull(artifact, "生成结果不能为空").requireTarget(identity, version);
        return transition(State.GENERATED);
    }

    public BriefingAggregate reviewed(Integer expectedVersion, String action) {
        require(State.GENERATED);
        requireVersion(expectedVersion);
        if ("PASS".equals(action)) return transition(State.AUDITED);
        if ("REJECT".equals(action)) return transition(State.DRAFT);
        throw new Rejected(Reason.STATE_NOT_ALLOWED);
    }

    public BriefingAggregate published() {
        require(State.AUDITED);
        return transition(State.PUBLISHED);
    }

    public BriefingAggregate terminated() {
        if (state != State.DRAFT && state != State.GENERATED && state != State.AUDITED)
            throw new Rejected(Reason.STATE_NOT_ALLOWED);
        return transition(State.TERMINATED);
    }

    private void require(State allowed) {
        if (state != allowed) throw new Rejected(Reason.STATE_NOT_ALLOWED);
    }

    private BriefingAggregate transition(State next) {
        if (version == Integer.MAX_VALUE) throw new Rejected(Reason.VERSION_CONFLICT);
        return new BriefingAggregate(identity, version, next);
    }
}
