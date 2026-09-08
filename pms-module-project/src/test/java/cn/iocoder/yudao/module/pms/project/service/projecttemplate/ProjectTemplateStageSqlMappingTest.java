package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateStageDefinitionDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTemplateStageSqlMappingTest {
    @Test
    void generatedSelectIsParseableAndMapsStartColumnWithoutReservedAlias() throws Exception {
        var configuration = new MybatisConfiguration();
        var assistant = new MapperBuilderAssistant(configuration, "stage-mapping-test");
        assistant.setCurrentNamespace("stage.mapping.test");
        var table = TableInfoHelper.initTableInfo(assistant, ProjectTemplateStageDefinitionDO.class);
        String columns = table.getAllSqlSelect();
        assertFalse(columns.contains("AS start"), columns);
        assertNotNull(CCJSqlParserUtil.parse("SELECT " + columns + " FROM " + table.getTableName()));
        var resultMap = configuration.getResultMap(table.getResultMap());
        assertTrue(resultMap.getResultMappings().stream().anyMatch(mapping ->
                "start".equals(mapping.getProperty()) && "start_node".equals(mapping.getColumn())));
        assertTrue(resultMap.getResultMappings().stream().anyMatch(mapping ->
                "terminal".equals(mapping.getProperty()) && "terminal_node".equals(mapping.getColumn())));
    }
}
