package cn.iocoder.yudao.module.infra.api.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExternalDataSourceApiImplTest {
    @Test
    void acceptsCredentialFreeSqlServerUrl() {
        assertTrue(ExternalDataSourceApiImpl.supportedUrl(
                "jdbc:sqlserver://;serverName=172.17.90.134;databaseName=crmtest_MSCRM"));
        assertFalse(ExternalDataSourceApiImpl.containsCredentialOrUnsafeOption(
                "jdbc:sqlserver://;serverName=172.17.90.134;databaseName=crmtest_MSCRM"));
    }

    @Test
    void rejectsSqlServerCredentialsAndIntegratedAuthenticationInUrl() {
        assertTrue(ExternalDataSourceApiImpl.containsCredentialOrUnsafeOption(
                "jdbc:sqlserver://host;databaseName=crm;user=sa"));
        assertTrue(ExternalDataSourceApiImpl.containsCredentialOrUnsafeOption(
                "jdbc:sqlserver://host;databaseName=crm;integratedSecurity=true"));
    }
}
