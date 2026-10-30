package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterGroup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import java.util.List;
import java.util.Set;

/** 只解析、校验配置结构；产品匹配全部交给AST Mapper XML。 */
record InstallationAddressFilterConfig(List<ProductFilterGroup> productFilters) {
    private static final Set<String> FIELDS = Set.of("productCode", "productModel", "productDesc", "productCategory", "productType");

    static InstallationAddressFilterConfig parse(String value) {
        if (value == null || value.isBlank()) return new InstallationAddressFilterConfig(List.of());
        try {
            var root = JsonUtils.getObjectMapper().reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(value);
            if (!root.isObject() || root.size() != 1 || !root.path("productFilters").isArray()) throw invalid();
            for (var group : root.path("productFilters")) validate(group, false);
            return JsonUtils.getObjectMapper().treeToValue(root, InstallationAddressFilterConfig.class);
        } catch (Exception failure) {
            throw new IllegalArgumentException("安装地址筛选参数无效，请核对分组字段、条件和非空集合", failure);
        }
    }
    String version() { return productFilters.isEmpty() ? "" : cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(this)); }

    private static void validate(JsonNode group, boolean nested) {
        if (!group.isObject() || group.isEmpty()) throw invalid();
        for (var field : group.properties()) {
            var key = field.getKey();
            var value = field.getValue();
            if (key.equals("alternativeGroups")) {
                if (nested || !value.isArray() || value.isEmpty()) throw invalid();
                for (var child : value) validate(child, true);
                continue;
            }
            String operation = key.endsWith("NotIn") ? "NotIn" : key.endsWith("Like") ? "Like" : key.endsWith("In") ? "In" : "";
            if (operation.isEmpty() || !FIELDS.contains(key.substring(0, key.length() - operation.length()))) throw invalid();
            if (operation.equals("Like")) text(value);
            else {
                if (!value.isArray() || value.isEmpty()) throw invalid();
                for (var item : value) text(item);
            }
        }
    }
    private static void text(JsonNode value) { if (!value.isTextual() || value.asText().isBlank()) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_INSTALLATION_PRODUCT_FILTER"); }
}
