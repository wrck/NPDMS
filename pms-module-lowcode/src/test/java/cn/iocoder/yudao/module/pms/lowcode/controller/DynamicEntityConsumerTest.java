package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.pms.lowcode.dto.EntityDesignDTO;
import cn.iocoder.yudao.module.pms.lowcode.engine.DynamicEntityDataService;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeEntity;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeField;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeEntityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual controller/method-security/JDBC paths; only entity metadata and permission decisions are test ports. */
class DynamicEntityConsumerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class SecurityConfig { }

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SecurityFrameworkService permission;
    private DynamicEntityController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE pms_lc_device (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), note VARCHAR(100))");
        var entity = LowCodeEntity.builder().code("device").tableName("pms_lc_device").build();
        entity.setId(1L);
        var design = new EntityDesignDTO();
        design.setEntity(entity);
        design.setFields(List.of(LowCodeField.builder().name("name").fieldType("STRING").build(),
                LowCodeField.builder().name("note").fieldType("STRING").build()));
        design.setRelations(List.of());
        var metadata = mock(LowCodeEntityService.class);
        when(metadata.getOne(any())).thenReturn(entity);
        when(metadata.getDesign(1L)).thenReturn(design);
        permission = mock(SecurityFrameworkService.class);
        when(permission.hasPermission(anyString())).thenReturn(true);
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfig.class);
        context.registerBean("ss", SecurityFrameworkService.class, () -> permission);
        context.registerBean(DynamicEntityDataService.class, () -> new DynamicEntityDataService(metadata, jdbc));
        context.registerBean(DynamicEntityController.class);
        context.refresh();
        controller = context.getBean(DynamicEntityController.class);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fixture", "", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void createEditClearAndReopenUseRealJdbcAndCurrentPermissionMethod() throws Exception {
        mvc.perform(post("/api/lowcode/data/device").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Router\",\"note\":\"old\",\"unrecognized\":\"ignored\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(1));
        mvc.perform(put("/api/lowcode/data/device/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\",\"note\":null,\"id\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/lowcode/data/device/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated"))
                .andExpect(jsonPath("$.data.note").doesNotExist());
        assertNull(jdbc.queryForObject("SELECT note FROM pms_lc_device WHERE id=1", String.class));
        verify(permission).hasPermission("lowcode:data:device:add");
        verify(permission).hasPermission("lowcode:data:device:edit");
        verify(permission).hasPermission("lowcode:data:device:query");
    }

    @Test
    void generatedKeysBelongToTheInsertEvenWithNewConnections() {
        assertEquals(1L, controller.create("device", Map.of("name", "A")).getData());
        assertEquals(2L, controller.create("device", Map.of("name", "B")).getData());
        assertEquals("B", controller.getById("device", 2L).getData().get("name"));
    }

    @Test
    void deniedEntityWriteCannotMutateRows() {
        controller.create("device", Map.of("name", "Protected"));
        when(permission.hasPermission("lowcode:data:device:edit")).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> controller.update("device", 1L, Map.of("name", "Unauthorized")));
        assertEquals("Protected", jdbc.queryForObject("SELECT name FROM pms_lc_device WHERE id=1", String.class));
    }
}
