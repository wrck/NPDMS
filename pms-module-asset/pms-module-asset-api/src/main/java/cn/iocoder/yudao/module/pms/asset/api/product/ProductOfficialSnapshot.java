package cn.iocoder.yudao.module.pms.asset.api.product;

/** 产品信息 ACTIVE 快照：消费方按引用服务端写入，不信任前端传值。 */
public record ProductOfficialSnapshot(Long id, String productCode, String productName,
                                      String productModel) {
}
