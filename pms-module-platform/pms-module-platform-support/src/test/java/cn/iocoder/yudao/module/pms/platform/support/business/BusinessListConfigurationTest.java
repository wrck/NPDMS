package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessListConfigurationTest {
    public static class Parent extends BaseProjectBusinessEntity {
        @BusinessModelField(displayOrder=10) @TableField("display_title") String title;
        @BusinessModelField(readable=false) String secret;
        @BusinessModelField(searchable=false,sortable=false,listVisible=false) String privateFilter;
    }
    @TableName("it_list_config") public static class Note extends Parent { @BusinessModelField Integer priority; }
    interface NoteMapper extends BusinessMapper<Note> { }
    private NoteMapper mapper(){
        var mapper=mock(NoteMapper.class);
        doAnswer(call->call.getArgument(0)).when(mapper).selectPage(any(Page.class),any(Wrapper.class));
        return mapper;
    }
    @Test void runtimeConfigurationCanOnlyNarrowDeclaredCapabilities(){
        var field=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.FieldVO("internal","Internal","TEXT",false,false,true,0,false,false,false);
        var model=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.ModelDetailVO("IT","note","NOTE","Note",null,List.of(field),List.of(),List.of());
        var attempted=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field("internal","Visible",0,true,true,true);
        assertThrows(BusinessContractException.class,()->BusinessFieldConfigurations.validate(model,List.of(attempted)));
        var projected=BusinessFieldConfigurations.apply(model,new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(1,List.of(attempted))).fields().getFirst();
        assertFalse(projected.readable());assertFalse(projected.listVisible());assertFalse(projected.searchable());assertFalse(projected.sortable());
    }
    @Test void inheritedFieldsUseMappedColumnsAndStableTieBreaker(){
        var mapper=mapper();var query=new BusinessPageQuery();
        query.setSorts(List.of(new BusinessPageQuery.Sort("title",BusinessPageQuery.Direction.ASC),new BusinessPageQuery.Sort("priority",BusinessPageQuery.Direction.DESC)));
        BusinessMapperQueries.page(mapper,new BusinessReadQuery(7L,Set.of(99L),query));
        verify(mapper).selectPage(any(Page.class),argThat(wrapper->{
            var sql=wrapper.getSqlSegment();return sql.contains("tenant_id") && sql.contains("project_id") && sql.contains("ORDER BY display_title ASC,priority DESC,id DESC");
        }));
    }
    @Test void unknownUnreadableAndNonSortableFieldsNeverReachSql(){
        for(String code:List.of("title desc; drop table x","secret","privateFilter","creator")){
            var mapper=mapper();var query=new BusinessPageQuery();query.setSorts(List.of(new BusinessPageQuery.Sort(code,BusinessPageQuery.Direction.ASC)));
            assertThrows(BusinessContractException.class,()->BusinessMapperQueries.page(mapper,new BusinessReadQuery(7L,Set.of(99L),query)));
            verify(mapper,never()).selectPage(any(Page.class),any(Wrapper.class));
        }
    }
    @Test void nonSearchableFieldCannotBeUsedAsAHiddenProbe(){
        var mapper=mapper();var query=new BusinessPageQuery();query.setFilters(List.of(new BusinessFieldFilter("privateFilter",BusinessFieldFilter.Operator.EQ,List.of("value"))));
        assertThrows(BusinessContractException.class,()->BusinessMapperQueries.page(mapper,new BusinessReadQuery(7L,Set.of(99L),query)));
        verify(mapper,never()).selectPage(any(Page.class),any(Wrapper.class));
    }
    @Test void duplicateSortIsRejectedAndEmptyScopeDoesNotExpand(){
        var mapper=mapper();var query=new BusinessPageQuery();var sort=new BusinessPageQuery.Sort("title",BusinessPageQuery.Direction.ASC);query.setSorts(List.of(sort,sort));
        assertThrows(BusinessContractException.class,()->BusinessMapperQueries.page(mapper,new BusinessReadQuery(7L,Set.of(99L),query)));
        assertEquals(0L,BusinessMapperQueries.page(mapper,new BusinessReadQuery(7L,Set.of(),query)).getTotal());
        verify(mapper,never()).selectPage(any(Page.class),any(Wrapper.class));
    }
}
