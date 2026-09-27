package cn.iocoder.yudao.module.pms.platform.api.businessmodel.result;

import java.util.List;

/** 统一结果读取：当前结果、指定结果与存量枚举。 */
public interface BusinessResultPort {

    BusinessResultRecord current(BusinessResultQuery query);

    List<BusinessResultRecord> inventory(BusinessResultQuery query);
}
