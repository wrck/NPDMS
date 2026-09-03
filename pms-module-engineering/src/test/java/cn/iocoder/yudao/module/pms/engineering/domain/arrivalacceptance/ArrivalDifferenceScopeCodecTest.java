package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArrivalDifferenceScopeCodecTest {

    @Test
    void roundTripsDeviceScopeWithExactKeys() {
        var scope = new ArrivalDifferenceScopeCodec.DeviceScope(11L);

        String snapshot = ArrivalDifferenceScopeCodec.serialize(scope);

        assertEquals(Set.of("scopeType", "deviceId"), keys(snapshot));
        assertEquals(scope, ArrivalDifferenceScopeCodec.parse(snapshot));
    }

    @Test
    void roundTripsQuantityScopeWithExplicitNullProductCode() {
        var scope = new ArrivalDifferenceScopeCodec.QuantityScope(
                20L, null, "MODEL-1", new BigDecimal("2.5"), "台");

        String snapshot = ArrivalDifferenceScopeCodec.serialize(scope);

        assertEquals(Set.of("scopeType", "orderLineId", "productCode", "modelCode", "quantity", "unitCode"),
                keys(snapshot));
        assertEquals(scope, ArrivalDifferenceScopeCodec.parse(snapshot));
    }

    @Test
    void rejectsNonCanonicalOrAmbiguousShapes() {
        List<String> invalid = List.of(
                "{\"scopeType\":\"DEVICE\",\"deviceId\":\"11\"}",
                "{\"scopeType\":\"DEVICE\"}",
                "{\"scopeType\":\"DEVICE\",\"deviceId\":0}",
                "{\"scopeType\":\"DEVICE\",\"deviceId\":11,\"extra\":1}",
                "{\"scopeType\":\"QUANTITY\",\"orderLineId\":20}",
                "{\"scopeType\":\"ORDER_MODEL_QUANTITY\",\"orderLineId\":20," +
                        "\"productCode\":null,\"modelCode\":null,\"quantity\":1,\"unitCode\":\"台\"}",
                "{\"scopeType\":\"ORDER_MODEL_QUANTITY\",\"orderLineId\":20," +
                        "\"productCode\":\"PRODUCT-1\",\"modelCode\":null,\"quantity\":\"1\"," +
                        "\"unitCode\":\"台\"}",
                "{\"scopeType\":\"ORDER_MODEL_QUANTITY\",\"orderLineId\":20," +
                        "\"productCode\":\"PRODUCT-1\",\"modelCode\":null,\"quantity\":0," +
                        "\"unitCode\":\"台\"}",
                "{\"scopeType\":\"ORDER_MODEL_QUANTITY\",\"orderLineId\":20," +
                        "\"productCode\":\"PRODUCT-1\",\"modelCode\":null,\"quantity\":1," +
                        "\"unitCode\":\" \"}",
                "{\"scopeType\":\"ORDER_MODEL_QUANTITY\",\"orderLineId\":20," +
                        "\"productCode\":\" PRODUCT-1 \",\"modelCode\":null,\"quantity\":1," +
                        "\"unitCode\":\"台\"}");

        invalid.forEach(snapshot -> assertThrows(IllegalArgumentException.class,
                () -> ArrivalDifferenceScopeCodec.parse(snapshot)));
    }

    private static Set<String> keys(String snapshot) {
        Set<String> keys = new HashSet<>();
        JsonUtils.parseTree(snapshot).properties().forEach(entry -> keys.add(entry.getKey()));
        return Set.copyOf(keys);
    }
}
