package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstallationAddressFilterConfigTest {

    @Test
    void missingAndEmptyConfigurationProducesEmptyFiltersAndVersion() {
        var missing = InstallationAddressFilterConfig.parse(null);
        var blank = InstallationAddressFilterConfig.parse("");
        var empty = InstallationAddressFilterConfig.parse("{\"productFilters\":[]}");

        assertTrue(missing.productFilters().isEmpty());
        assertTrue(blank.productFilters().isEmpty());
        assertTrue(empty.productFilters().isEmpty());
        assertEquals("", missing.version());
        assertEquals("", blank.version());
        assertEquals("", empty.version());
    }

    @Test
    void validProductFieldsAndNestedAlternativesDeserializeToTypedConditions() {
        var parsed = InstallationAddressFilterConfig.parse("""
                {"productFilters":[{
                  "productCodeLike":"CODE%","productCodeIn":["CODE1"],"productCodeNotIn":["CODE2"],
                  "productModelLike":"MODEL%","productModelIn":["MODEL1"],"productModelNotIn":["MODEL2"],
                  "productDescLike":"DESC%","productDescIn":["DESC1"],"productDescNotIn":["DESC2"],
                  "productCategoryLike":"CATEGORY%","productCategoryIn":["CATEGORY1"],"productCategoryNotIn":["CATEGORY2"],
                  "productTypeLike":"TYPE%","productTypeIn":["TYPE1"],"productTypeNotIn":["TYPE2"],
                  "alternativeGroups":[{"productCodeLike":"ALT%","productDescIn":["ALT-DESC"],"productTypeNotIn":["ALT-TYPE"]}]
                }]}
                """);

        assertEquals(1, parsed.productFilters().size());
        ProductFilterGroup group = parsed.productFilters().getFirst();
        assertEquals("CODE%", group.getProductCodeLike());
        assertEquals(List.of("CODE1"), group.getProductCodeIn());
        assertEquals(List.of("CODE2"), group.getProductCodeNotIn());
        assertEquals("MODEL%", group.getProductModelLike());
        assertEquals(List.of("MODEL1"), group.getProductModelIn());
        assertEquals(List.of("MODEL2"), group.getProductModelNotIn());
        assertEquals("DESC%", group.getProductDescLike());
        assertEquals(List.of("DESC1"), group.getProductDescIn());
        assertEquals(List.of("DESC2"), group.getProductDescNotIn());
        assertEquals("CATEGORY%", group.getProductCategoryLike());
        assertEquals(List.of("CATEGORY1"), group.getProductCategoryIn());
        assertEquals(List.of("CATEGORY2"), group.getProductCategoryNotIn());
        assertEquals("TYPE%", group.getProductTypeLike());
        assertEquals(List.of("TYPE1"), group.getProductTypeIn());
        assertEquals(List.of("TYPE2"), group.getProductTypeNotIn());

        assertEquals(1, group.getAlternativeGroups().size());
        ProductFilterGroup nested = group.getAlternativeGroups().getFirst();
        assertEquals("ALT%", nested.getProductCodeLike());
        assertEquals(List.of("ALT-DESC"), nested.getProductDescIn());
        assertEquals(List.of("ALT-TYPE"), nested.getProductTypeNotIn());
        assertFalse(parsed.version().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "{", "{\"productFilters\":[]}{}", "{\"productFilters\":[],\"productFilters\":[]}",
            "{\"productFilters\":null}", "{\"productFilters\":[{}]}",
            "{\"productFilters\":[{\"productCodeNotIn2\":[\"X\"]}]}", "{\"productFilters\":[{\"productCodeIn\":[]}]}",
            "{\"productFilters\":[{\"productTypeIn\":[null]}]}", "{\"productFilters\":[{\"productCodeLike\":\"\"}]}",
            "{\"productFilters\":[],\"productCodeNotIn\":[\"X\"]}",
            "{\"productFilters\":[{\"alternativeGroups\":[{\"alternativeGroups\":[{\"productCodeIn\":[\"X\"]}]}]}]}",
            "{\"itemFilters\":[{\"itemCodeLike\":\"CODE%\"}]}",
            "{\"productFilters\":[{\"itemCodeLike\":\"CODE%\"}]}",
            "{\"productFilters\":[{\"productCodeLike\":\"CODE%\",\"itemDescLIn\":[\"DESC\"]}]}"
    })
    void malformedAndUnsupportedConfigurationFailsExplicitly(String value) {
        assertThrows(IllegalArgumentException.class, () -> InstallationAddressFilterConfig.parse(value));
    }
}
