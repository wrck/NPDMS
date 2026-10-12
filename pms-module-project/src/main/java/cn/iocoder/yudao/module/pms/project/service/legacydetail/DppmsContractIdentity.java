package cn.iocoder.yudao.module.pms.project.service.legacydetail;
final class DppmsContractIdentity {
    private DppmsContractIdentity() {}
    static boolean same(String left,String right) {
        return left != null && right != null && left.replaceFirst("-[CL]$","").equals(right.replaceFirst("-[CL]$",""));
    }
}
