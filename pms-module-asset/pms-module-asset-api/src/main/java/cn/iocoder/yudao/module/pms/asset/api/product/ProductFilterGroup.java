package cn.iocoder.yudao.module.pms.asset.api.product;

import lombok.Data;
import java.util.List;

/** 公共产品分组条件：使用当前产品字段，仅承载绑定参数，匹配在Mapper XML执行。 */
@Data
public class ProductFilterGroup {
    private String productCodeLike;
    private List<String> productCodeIn;
    private List<String> productCodeNotIn;
    private String productModelLike;
    private List<String> productModelIn;
    private List<String> productModelNotIn;
    private String productDescLike;
    private String productDescNotLike;
    private List<String> productDescIn;
    private List<String> productDescNotIn;
    private String productCategoryLike;
    private List<String> productCategoryIn;
    private List<String> productCategoryNotIn;
    private String productTypeLike;
    private List<String> productTypeIn;
    private List<String> productTypeNotIn;
    private List<ProductFilterGroup> alternativeGroups;
}
