package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

/** 基础平台启用的EXE-01到货差异类型只读事实；生产适配在Task 12统一装配。 */
public interface ArrivalDifferenceTypePort {

    void requireEnabled(String differenceTypeCode);
}
