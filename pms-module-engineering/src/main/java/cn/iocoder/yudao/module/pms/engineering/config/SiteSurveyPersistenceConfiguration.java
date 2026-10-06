package cn.iocoder.yudao.module.pms.engineering.config;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.springframework.context.annotation.Bean;

/** Preserve the current table's assigned identity without shadowing the common id field or changing DDL. */
@org.springframework.context.annotation.Configuration
public class SiteSurveyPersistenceConfiguration {
    @Bean
    public MybatisPlusPropertiesCustomizer siteSurveyAssignedIdentity() {
        return properties -> {
            var previous=properties.getGlobalConfig().getPostInitTableInfoHandler();
            properties.getGlobalConfig().setPostInitTableInfoHandler(new PostInitTableInfoHandler() {
                @Override public TableInfo creteTableInfo(Configuration configuration, Class<?> type) {
                    return previous.creteTableInfo(configuration,type);
                }
                @Override public void postTableInfo(TableInfo table, Configuration configuration) {
                    previous.postTableInfo(table,configuration);
                    if (table.getEntityType()==SiteSurveyEntityDO.class)
                        SystemMetaObject.forObject(table).setValue("idType",IdType.ASSIGN_ID);
                }
                @Override public void postFieldInfo(com.baomidou.mybatisplus.core.metadata.TableFieldInfo field, Configuration configuration) {
                    previous.postFieldInfo(field,configuration);
                }
            });
        };
    }
}
