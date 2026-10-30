package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in acceptance only inside a freshly owned, marked MySQL environment; never a default local DB port. */
@EnabledIfSystemProperty(named="npdms.survey.mysql.optIn",matches="true")
class SiteSurveyMysqlIsolatedTest extends SiteSurveySpringPersistenceTest {
    @Test void ordinaryRolePermissionMappingAndSecuredPublicOwnerEntry() throws Exception {
        SiteSurveyOrdinaryRoleMysqlAcceptance.verify(this);
    }
    @Test void originalMigrationRejectsForgedStatusDuplicateSerialAndGeneratedIdentityIsExact() {
        var r=create();long id=r.entityRef().entityId();
        assertTrue(id>9007199254740991L,"production assigned identity must be exercised, not an H2 auto-increment stand-in");
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE sol_site_survey SET status=9 WHERE id=?",id));
        assertThrows(RuntimeException.class,()->jdbc.update("INSERT INTO sol_site_survey_material(id,tenant_id,survey_id,project_id,sn,sort_order) VALUES (?,1,?,20,'SN-1',1)",id+1,id));
        assertEquals(0,jdbc.queryForObject("SELECT status FROM sol_site_survey WHERE id=?",Integer.class,id));
        assertEquals(1,count("sol_site_survey_material"));
    }
}
