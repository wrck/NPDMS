package cn.iocoder.yudao.module.pms.asset.api.product;

import java.util.Collection;
import java.util.List;

/** 产品信息只读事实 API：换货产品等引用的 ACTIVE 快照解析。 */
public interface AssetProductOfficialApi {

    /**
     * 批量解析产品 ACTIVE 快照；ID 不存在或非 ACTIVE 不返回（调用方按缺失拒绝）。
     * 空入参返回空结果。
     */
    List<ProductOfficialSnapshot> getActiveProductSnapshots(Collection<Long> productIds);

    /** 当前产品清单的展示副本（包含目录停用展示项），精确编码、ID倒序；空编码集合返回空。 */
    List<ProductOfficialSnapshot> getProductDisplaySnapshots(ProductDisplayQuery query);

}
