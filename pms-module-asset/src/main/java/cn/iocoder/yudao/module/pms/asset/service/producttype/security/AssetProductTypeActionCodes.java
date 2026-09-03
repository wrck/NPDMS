package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import java.util.Set;

public final class AssetProductTypeActionCodes {

    public static final String PRODUCT_TYPE_READ_CODES = "PRODUCT_TYPE_READ_CODES";
    public static final String DEVICE_PRODUCT_TYPE_READ = "DEVICE_PRODUCT_TYPE_READ";
    public static final String PRODUCT_TYPE_CONTROLLED_IMPORT = "PRODUCT_TYPE_CONTROLLED_IMPORT";
    public static final Set<String> ALL = Set.of(
            PRODUCT_TYPE_READ_CODES,
            DEVICE_PRODUCT_TYPE_READ,
            PRODUCT_TYPE_CONTROLLED_IMPORT);

    private AssetProductTypeActionCodes() {
    }
}
