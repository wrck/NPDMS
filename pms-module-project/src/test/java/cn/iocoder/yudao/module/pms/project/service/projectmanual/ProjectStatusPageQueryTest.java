package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.VisibleProjectPageQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectStatusPageQueryTest {
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProjectMasterDO.class);
    }

    @Test
    void filtersActiveStageInsteadOfStaleStatus() {
        var wrapper = query("S1");
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("current_stage"));
        assertTrue(sql.contains("lifecycle_status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ACTIVE"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("S1"));
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("id IN"));
    }

    @Test
    void filtersClosureWithoutRequiringStage() {
        for (String state : Set.of("NORMAL_CLOSED", "NO_TRACKING_CLOSED", "EXCEPTION_CLOSED")) {
            var wrapper = query(state);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("lifecycle_status"));
            assertFalse(sql.contains("current_stage"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(state));
        }
    }

    @Test
    void blankFilterIncludesAllLifecycleStates() {
        String sql = query("").getSqlSegment();
        assertFalse(sql.contains("lifecycle_status"));
        assertFalse(sql.contains("current_stage"));
    }

    @Test
    void emptyPermissionSetNeverQueriesRows() {
        var mapper = mock(ProjectMasterMapper.class, CALLS_REAL_METHODS);
        assertEquals(0L, mapper.selectPage(new VisibleProjectPageQuery(1L, Set.of(),
                new PageParam(), null, null, "S1", null, null, null)).getTotal());
        verify(mapper, never()).selectPage(any(PageParam.class), any(LambdaQueryWrapperX.class));
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapperX<ProjectMasterDO> query(String status) {
        var mapper = mock(ProjectMasterMapper.class, CALLS_REAL_METHODS);
        doReturn(PageResult.empty()).when(mapper).selectPage(any(PageParam.class), any(LambdaQueryWrapperX.class));
        mapper.selectPage(new VisibleProjectPageQuery(1L, Set.of(11L), new PageParam(),
                null, null, status, null, null, null));
        ArgumentCaptor<LambdaQueryWrapperX<ProjectMasterDO>> capture = ArgumentCaptor.forClass(LambdaQueryWrapperX.class);
        verify(mapper).selectPage(any(PageParam.class), capture.capture());
        return capture.getValue();
    }
}
