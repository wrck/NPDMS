package cn.iocoder.yudao.module.pms.asset.controller.admin.producttype;

import cn.iocoder.yudao.module.pms.asset.controller.admin.producttype.vo.ImportAssetProductTypeReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AssetProductTypeImportControllerTest {

    @Test
    void shouldExposeOnlyDedicatedControlledImportEndpoint() throws Exception {
        RequestMapping mapping = AssetProductTypeImportController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/pms/asset-product-types"}, mapping.value());
        Method method = AssetProductTypeImportController.class.getDeclaredMethod(
                "controlledImport", String.class, ImportAssetProductTypeReqVO.class);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/actions/controlled-import"}, postMapping.value());
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals("@ss.hasPermission('pms:asset-product-type:controlled-import')", authorize.value());
        assertEquals(1, Arrays.stream(AssetProductTypeImportController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getAnnotation(PostMapping.class) != null).count());
    }

    @Test
    void shouldNotAcceptTenantActorOrServiceIdentityFields() {
        var fieldNames = Arrays.stream(ImportAssetProductTypeReqVO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList();
        assertFalse(fieldNames.contains("tenantId"));
        assertFalse(fieldNames.contains("actorId"));
        assertFalse(fieldNames.contains("serviceIdentity"));
        assertFalse(fieldNames.contains("actionCode"));
    }
}
