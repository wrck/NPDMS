import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingAggregate;
import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingDocumentArtifact;
import cn.iocoder.yudao.module.pms.engineering.service.briefing.entity.BriefingEntityStatePolicy;

import java.util.Objects;

/** 无测试框架依赖的真实领域代码检查；不模拟 Spring、SQL 或文件存储成功。 */
public final class BriefingAggregateCheck {
    private static int assertions;
    private static final BriefingAggregate.Identity ID = new BriefingAggregate.Identity(1L, 10L, 7L, "BR-7");
    private static BriefingAggregate row(int status) { return BriefingAggregate.restore(ID, 4, status); }
    private static BriefingDocumentArtifact artifact(BriefingAggregate.Identity id, int version) {
        return new BriefingDocumentArtifact(id, version, 20L, "{\"template\":20}",
                "{\"sourceVersion\":1}", "正文", "/files/verified", "briefing.pdf", 17L, "a".repeat(64));
    }
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void rejected(BriefingAggregate.Reason reason, Runnable action) {
        assertions++;
        try { action.run(); }
        catch (BriefingAggregate.Rejected error) {
            if (error.reason() == reason) return;
            throw new AssertionError("错误拒绝原因：" + error.reason(), error);
        }
        throw new AssertionError("应拒绝：" + reason);
    }
    private static void invalid(String message, Runnable action) {
        assertions++;
        try { action.run(); }
        catch (IllegalArgumentException error) {
            if (message.equals(error.getMessage())) return;
            throw new AssertionError("错误异常：" + error.getMessage(), error);
        }
        throw new AssertionError("应拒绝：" + message);
    }
    public static void main(String[] args) {
        var draft = BriefingAggregate.draft(1L, 10L, "BR-7");
        equal(null, draft.identity().id()); equal(0, draft.version()); equal(0, draft.state().code());
        for (int state=0; state<=4; state++) {
            var current = row(state);
            equal(state, current.state().code());
            equal(state == 0, BriefingEntityStatePolicy.isDraft(state));
            equal(state == 0, BriefingEntityStatePolicy.canGenerate(state));
            equal(state == 1, BriefingEntityStatePolicy.canApprove(state));
            equal(state == 2, BriefingEntityStatePolicy.canPublish(state));
            equal(state <= 2, BriefingEntityStatePolicy.canTerminate(state));
            if (state == 0) {
                current.requireEditable(10L, "BR-7", 4); assertions++;
                equal(1, current.generated(4, artifact(ID,4)).state().code());
            } else {
                rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, () -> current.requireEditable(10L,"BR-7",4));
                rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, () -> current.generated(4, artifact(ID,4)));
            }
            if (state == 1) {
                equal(2, current.reviewed(4,"PASS").state().code());
                equal(0, current.reviewed(4,"REJECT").state().code());
            } else {
                rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, () -> current.reviewed(4,"PASS"));
                rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, () -> current.reviewed(4,"REJECT"));
            }
            if (state == 2) equal(3, current.published().state().code());
            else rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, current::published);
            if (state <= 2) equal(4, current.terminated().state().code());
            else rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, current::terminated);
            equal(state, current.state().code()); equal(4, current.version());
        }
        for (Integer bad : new Integer[]{null,-1,5,999})
            rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, () -> BriefingAggregate.restore(ID,4,bad));
        for (Integer bad : new Integer[]{null,-1,3,5}) {
            rejected(BriefingAggregate.Reason.VERSION_CONFLICT, () -> row(0).requireEditable(10L,"BR-7",bad));
            rejected(BriefingAggregate.Reason.VERSION_CONFLICT, () -> row(0).generated(bad, artifact(ID,4)));
            rejected(BriefingAggregate.Reason.VERSION_CONFLICT, () -> row(1).reviewed(bad,"PASS"));
        }
        rejected(BriefingAggregate.Reason.IDENTITY_CHANGED, () -> row(0).requireEditable(11L,"BR-7",4));
        rejected(BriefingAggregate.Reason.IDENTITY_CHANGED, () -> row(0).requireEditable(null,"BR-7",4));
        rejected(BriefingAggregate.Reason.IDENTITY_CHANGED, () -> row(0).requireEditable(10L,"BR-8",4));
        for (String action : new String[]{null,"","pass","TRANSFER"})
            rejected(BriefingAggregate.Reason.STATE_NOT_ALLOWED, () -> row(1).reviewed(4,action));
        rejected(BriefingAggregate.Reason.VERSION_CONFLICT, () -> BriefingAggregate.restore(ID,null,0));
        rejected(BriefingAggregate.Reason.VERSION_CONFLICT, () -> BriefingAggregate.restore(ID,-1,0));
        rejected(BriefingAggregate.Reason.VERSION_CONFLICT,
                () -> BriefingAggregate.restore(ID,Integer.MAX_VALUE,2).published());
        rejected(BriefingAggregate.Reason.INVALID_IDENTITY, () -> BriefingAggregate.draft(-1L,10L,"B"));
        rejected(BriefingAggregate.Reason.INVALID_IDENTITY, () -> BriefingAggregate.draft(1L,0L,"B"));
        rejected(BriefingAggregate.Reason.INVALID_IDENTITY, () -> BriefingAggregate.draft(1L,10L," "));
        rejected(BriefingAggregate.Reason.INVALID_IDENTITY, () -> BriefingAggregate.draft(1L,10L,"x".repeat(65)));
        equal(0L, BriefingAggregate.draft(0L,10L,"B").identity().tenantId());
        for (var wrong : new BriefingAggregate.Identity[]{
                new BriefingAggregate.Identity(2L,10L,7L,"BR-7"), new BriefingAggregate.Identity(1L,20L,7L,"BR-7"),
                new BriefingAggregate.Identity(1L,10L,8L,"BR-7"), new BriefingAggregate.Identity(1L,10L,7L,"BR-8")})
            invalid("BRIEFING_DOCUMENT_TARGET_MISMATCH", () -> row(0).generated(4,artifact(wrong,4)));
        invalid("BRIEFING_DOCUMENT_TARGET_MISMATCH", () -> row(0).generated(4,artifact(ID,3)));
        invalid("BRIEFING_DOCUMENT_ARTIFACT_INVALID", () -> new BriefingDocumentArtifact(
                ID,4,20L,"{}","{}","正文","/pretend.pdf","pretend.pdf",102400L,"auto-BR-7"));
        invalid("BRIEFING_DOCUMENT_ARTIFACT_INVALID", () -> new BriefingDocumentArtifact(
                ID,4,20L,"{}","{}","正文","/empty.pdf","empty.pdf",0L,"a".repeat(64)));
        invalid("BRIEFING_DOCUMENT_ARTIFACT_INVALID", () -> new BriefingDocumentArtifact(
                ID,4,20L,"{}","{}","","/empty.pdf","empty.pdf",17L,"a".repeat(64)));
        equal(0, row(0).state().code()); equal(4,row(0).version());
        System.out.println("PASS: " + assertions + " domain assertions; no Spring/MySQL/file-service execution");
    }
}
