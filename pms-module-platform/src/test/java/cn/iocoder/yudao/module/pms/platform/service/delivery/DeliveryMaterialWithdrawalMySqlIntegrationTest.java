package cn.iocoder.yudao.module.pms.platform.service.delivery;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

@EnabledIfSystemProperty(named="npdms.delivery.withdrawal.mysql.exclusive",matches="true")
class DeliveryMaterialWithdrawalMySqlIntegrationTest extends DeliveryMaterialWithdrawalPersistenceTest {
    @Override protected boolean mysql(){return true;}
    @Override protected DataSource database(){
        String name=System.getProperty("npdms.delivery.withdrawal.mysql.database","");
        String port=System.getProperty("npdms.delivery.withdrawal.mysql.port","");
        if(!name.matches("npdms_delivery_withdrawal_[a-zA-Z0-9_]+")||!port.matches("[1-9][0-9]{3,4}"))throw new IllegalStateException("Exclusive task database and port required");
        return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/"+name+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8","root","");
    }
}
