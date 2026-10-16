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
    @Test void configurationHttpBodyPassesFeatureFlagsToTheSharedService() {
        @SuppressWarnings("unchecked") ProjectBusinessService<Note> service=mock(ProjectBusinessService.class);
        var controller=new ProjectBusinessController<ProjectBusinessService<Note>,Note>() { };
        controller.service=service;
        var body=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject("""
                {"version":3,"fields":[],"profiles":[],"features":{"attachments":true,"approvals":false}}
                """,ProjectBusinessController.FieldConfigurationWrite.class);
        controller.saveFieldConfiguration(body);
        verify(service).saveFieldConfiguration(argThat(configuration->configuration.version()==3 && configuration.features().attachments() && !configuration.features().approvals()));
        when(service.fieldConfiguration()).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(4,List.of(),List.of(),null,
                new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Features(true,true)));
        controller.saveFieldConfiguration(new ProjectBusinessController.FieldConfigurationWrite(4,List.of(),List.of(),null));
        verify(service).saveFieldConfiguration(argThat(configuration->configuration.version()==4 && configuration.features().attachments() && configuration.features().approvals()));
    }
    @Test void optionalPanelsDefaultToOffAndNamedConfigurationsStayIndependent() {
        var model=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.ModelDetailVO("IT","note","NOTE","Note",null,List.of(),List.of(),List.of());
        var empty=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(0,List.of());
        var defaultView=BusinessFieldConfigurations.apply(model,empty);
        assertFalse(defaultView.features().attachments());assertFalse(defaultView.features().approvals());
        var attachments=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Features(true,false);
        var approvals=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Features(false,true);
        var profile=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Named("approval","审批",List.of(),91L,approvals);
        var inherited=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Named("inherited","继承",List.of(),null);
        var settings=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(2,List.of(),List.of(profile,inherited),"approval",attachments);
        assertEquals(approvals,BusinessFieldConfigurations.apply(model,settings).features());
        assertEquals(91L,BusinessFieldConfigurations.apply(model,settings).formRevisionId());
        assertNull(BusinessFieldConfigurations.apply(model,settings,"").formRevisionId());
        assertEquals(attachments,BusinessFieldConfigurations.apply(model,settings,"").features());
        assertEquals(attachments,BusinessFieldConfigurations.apply(model,settings,"inherited").features());
        assertEquals(attachments,settings.features());
    }
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
