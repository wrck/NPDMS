package cn.iocoder.yudao.module.pms.asset.api.product;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProductOfficialApiImplTest {

    @Mock
    private AssetProductOfficialMapper productOfficialMapper;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境未注册 Mapper，手工初始化实体 TableInfo 使 lambda wrapper 可构建
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AssetProductOfficialDO.class);
    }

    @Test
    void emptyInputReturnsEmptyWithoutQuery() {
        AssetProductOfficialApiImpl api = new AssetProductOfficialApiImpl(productOfficialMapper);

        assertTrue(api.getActiveProductSnapshots(List.of()).isEmpty());
        assertTrue(api.getActiveProductSnapshots(null).isEmpty());
        verify(productOfficialMapper, never()).selectList((Wrapper<AssetProductOfficialDO>) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsSnapshotsAndQueriesOnlyActive() {
        AssetProductOfficialApiImpl api = new AssetProductOfficialApiImpl(productOfficialMapper);
        AssetProductOfficialDO active = new AssetProductOfficialDO();
        active.setId(11L);
        active.setProductCode("01100003");
        active.setProductName("DPtech IPS2000-MA-N");
        active.setProductModel("IPS2000-MA-N+1Y");
        active.setStatus("ACTIVE");
        when(productOfficialMapper.selectList((Wrapper<AssetProductOfficialDO>) org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(active));

        List<ProductOfficialSnapshot> snapshots = api.getActiveProductSnapshots(List.of(11L, 404L));

        assertEquals(1, snapshots.size());
        assertEquals(11L, snapshots.get(0).id());
        assertEquals("01100003", snapshots.get(0).productCode());
        assertEquals("DPtech IPS2000-MA-N", snapshots.get(0).productName());
        assertEquals("IPS2000-MA-N+1Y", snapshots.get(0).productModel());
        // 快照只返回 ACTIVE：非 ACTIVE 测试发布状态不得进入换货产品，查询段必须携带状态过滤
        ArgumentCaptor<Wrapper<AssetProductOfficialDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productOfficialMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }
}
