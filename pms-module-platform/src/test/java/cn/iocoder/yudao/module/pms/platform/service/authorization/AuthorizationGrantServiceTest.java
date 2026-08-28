package cn.iocoder.yudao.module.pms.platform.service.authorization;

import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization.AuthorizationGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.AuthorizationGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.EffectiveAuthorizationGrantQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationGrantServiceTest {

    private static final String DIGEST = "a".repeat(64);

    @Mock AuthorizationGrantMapper grantMapper;
    @Mock PlatformCommandExecutionApi commandExecutionApi;

    private AuthorizationGrantService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationGrantService(grantMapper, commandExecutionApi);
    }

    @Test
    void createAcceptsOnlyPm04CodesAndPersistsCurrentFact() {
        executeOperationsImmediately();
        when(grantMapper.insert(any(AuthorizationGrantDO.class))).thenAnswer(invocation -> {
            AuthorizationGrantDO grant = invocation.getArgument(0);
            grant.setId(7001L);
            return 1;
        });

        AuthorizationGrantDTO created = service.create(createCommand("PROJECT_MANAGE",
                "PROJECT_AND_DESCENDANTS"));

        assertEquals(7001L, created.id());
        assertEquals("ACTIVE", created.statusCode());
        assertEquals(0, created.version());
        verify(grantMapper).expireCurrentByKey(any());
        verify(grantMapper).insert(any(AuthorizationGrantDO.class));
    }

    @Test
    void invalidActionIsRejectedBeforePersistence() {
        AuthorizationGrantCreateCommand command = createCommand("PROJECT_DELETE", "CURRENT_PROJECT");

        assertThrows(IllegalArgumentException.class, () -> service.create(command));

        verify(grantMapper, never()).insert(any(AuthorizationGrantDO.class));
    }

    @Test
    void emptyResourceIdsReturnEmptyWithoutQueryingDatabase() {
        AuthorizationGrantQuery query = new AuthorizationGrantQuery(
                0L, "USER", 9L, "PROJ", "PROJECT", Set.of(),
                "PROJECT_VIEW", LocalDateTime.now());

        assertEquals(List.of(), service.listEffective(query));

        verify(grantMapper, never()).selectListEffective(any());
    }

    @Test
    void effectiveQueryMapsFactsWithoutExpandingResourceScope() {
        AuthorizationGrantDO grant = new AuthorizationGrantDO();
        grant.setId(88L);
        grant.setTenantId(0L);
        grant.setSubjectTypeCode("USER");
        grant.setSubjectId(9L);
        grant.setResourceContextCode("PROJ");
        grant.setResourceTypeCode("PROJECT");
        grant.setResourceId(100L);
        grant.setActionCode("PROJECT_VIEW");
        grant.setScopeCode("CURRENT_PROJECT");
        grant.setStatusCode("ACTIVE");
        grant.setVersion(0);
        when(grantMapper.selectListEffective(any(EffectiveAuthorizationGrantQuery.class)))
                .thenReturn(List.of(grant));
        AuthorizationGrantQuery query = new AuthorizationGrantQuery(
                0L, "USER", 9L, "PROJ", "PROJECT", Set.of(100L),
                "PROJECT_VIEW", LocalDateTime.now());

        List<AuthorizationGrantDTO> result = service.listEffective(query);

        assertEquals(List.of(100L), result.stream().map(AuthorizationGrantDTO::resourceId).toList());
    }

    @SuppressWarnings("unchecked")
    private void executeOperationsImmediately() {
        when(commandExecutionApi.execute(any(), anyString(), eq(AuthorizationGrantDTO.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<AuthorizationGrantDTO> operation = invocation.getArgument(3);
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, operation.get());
                });
    }

    private AuthorizationGrantCreateCommand createCommand(String action, String scope) {
        LocalDateTime now = LocalDateTime.now();
        return new AuthorizationGrantCreateCommand(
                0L, 7L, "key-1", DIGEST, "USER", 9L, "PROJ", "PROJECT", 100L,
                action, scope, now.minusMinutes(1), now.plusDays(1),
                "PROJ", "PROJECT", "100", "项目协作");
    }
}
