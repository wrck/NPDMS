package cn.iocoder.yudao.module.pms.engineering.service.requirement;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
/** Same assertions on original MySQL schema plus the actual V399 forward migration. */
@EnabledIfSystemProperty(named="npdms.ra.mysql.optIn",matches="true")
class RequirementInheritedMySqlTest extends RequirementInheritedBusinessTest { }
