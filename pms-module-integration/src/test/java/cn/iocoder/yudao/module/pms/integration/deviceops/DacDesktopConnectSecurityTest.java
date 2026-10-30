package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.module.pms.integration.controller.admin.deviceops.DacDesktopConnectController;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DacDesktopConnectSecurityTest {
    @Configuration
    @EnableMethodSecurity(jsr250Enabled=true)
    static class Config {
        @Bean DacDesktopConnectService service() { return mock(DacDesktopConnectService.class); }
        @Bean DacDesktopConnectController controller(DacDesktopConnectService service) { return new DacDesktopConnectController(service); }
        @Bean DacDesktopConnectProofController proof(DacDesktopConnectService service) { return new DacDesktopConnectProofController(service); }
    }
    @Test void anonymousCannotCreateReadOrCancelButCanSubmitChallengeProof() {
        SecurityContextHolder.clearContext();
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(DacDesktopConnectController.class);
            var service = context.getBean(DacDesktopConnectService.class);
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.begin(null));
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.poll("a".repeat(32)));
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.cancel("a".repeat(32)));
            verifyNoInteractions(service);
            // No bearer token is sent to native code; the service independently verifies the proof.
            assertEquals(Boolean.TRUE, context.getBean(DacDesktopConnectProofController.class).proof("a".repeat(32), null).getData());
            verify(service).accept("a".repeat(32), null);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
