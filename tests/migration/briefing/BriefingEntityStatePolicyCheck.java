import cn.iocoder.yudao.module.pms.engineering.service.briefing.entity.BriefingEntityStatePolicy;

/** 可独立执行的状态矩阵检查；不替代 Spring、数据库或浏览器测试。 */
public class BriefingEntityStatePolicyCheck {
    private static int assertions;
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("状态矩阵检查失败：" + assertions);
    }
    public static void main(String[] args) {
        for (int state = -1; state <= 5; state++) {
            check(BriefingEntityStatePolicy.isDraft(state) == (state == 0));
            check(BriefingEntityStatePolicy.canGenerate(state) == (state == 0));
            check(BriefingEntityStatePolicy.canApprove(state) == (state == 1));
            check(BriefingEntityStatePolicy.canPublish(state) == (state == 2));
            check(BriefingEntityStatePolicy.canTerminate(state) == (state >= 0 && state <= 2));
        }
        check(!BriefingEntityStatePolicy.isDraft(null));
        check(!BriefingEntityStatePolicy.canGenerate(null));
        check(!BriefingEntityStatePolicy.canApprove(null));
        check(!BriefingEntityStatePolicy.canPublish(null));
        check(!BriefingEntityStatePolicy.canTerminate(null));
        check(BriefingEntityStatePolicy.approvalTarget("PASS") == 2);
        check(BriefingEntityStatePolicy.approvalTarget("REJECT") == 0);
        for (String action : new String[] {null, "", "pass", "DELETE"}) {
            boolean rejected = false;
            try { BriefingEntityStatePolicy.approvalTarget(action); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected);
        }
        System.out.println("通过 " + assertions + " 项状态矩阵断言");
    }
}
