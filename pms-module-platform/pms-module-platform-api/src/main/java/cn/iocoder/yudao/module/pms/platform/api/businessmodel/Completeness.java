package cn.iocoder.yudao.module.pms.platform.api.businessmodel;

/**
 * 集合与事实读取的完整性。PARTIAL 必须携带续读游标，UNAVAILABLE 必须给出原因；
 * 不允许把不完整或不可读的成员当作"全部满足"。
 */
public enum Completeness {
    COMPLETE, PARTIAL, UNAVAILABLE
}
