package cn.iocoder.yudao.module.pms.platform.support.business;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi;
/** One named published field configuration; selection alone never writes an entity binding. */
public record BusinessFormOption(String name,EntityFormApi.Layout layout) { }
