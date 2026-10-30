package cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;

/**
 * 事实观察：类型化值、可用性、观察依据与集合完整性。
 * 未知、已知空值和不满足三者不同；否定未知不能通过。
 */
public record FactObservation(String source, Availability availability, Object value, String basis,
                              Completeness completeness) {

    public enum Availability { VALUE, EMPTY, UNKNOWN, UNAVAILABLE }
}
