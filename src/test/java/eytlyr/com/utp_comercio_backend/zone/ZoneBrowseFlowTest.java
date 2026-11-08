package eytlyr.com.utp_comercio_backend.zone;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
class ZoneBrowseFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void browsingPublicDataNeverMovesTheUsersSavedZone() throws Exception {
        var user = TestAuth.login(mvc, "987654321");
        UUID original = jdbc.queryForObject("select zone_id from app_user where id = ?", UUID.class, user.userId());
        UUID temporary = UUID.randomUUID();
        jdbc.update("insert into zone(id,name,district,center_lat,center_lng,radius_m) values (?,?,?,?,?,?)",
          temporary,"Zona de prueba " + temporary,"Cusco",-13.532,-71.9675,2000);
        try {
            jdbc.update("update app_user set zone_id = ? where id = ?", temporary, user.userId());
            mvc.perform(get("/api/v1/zones").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(temporary.toString())));
            mvc.perform(get("/api/v1/map/pins").param("zoneId",original.toString()).param("radiusM","100000")
                .header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].kind", everyItem(is("svc"))));
            mvc.perform(get("/api/v1/map/pins").param("zoneId",original.toString()).param("layer","emergency").param("radiusM","100000")
                .header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].kind", everyItem(is("safe"))));
            mvc.perform(get("/api/v1/me").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.zone.id").value(temporary.toString()))
                .andExpect(jsonPath("$.zone.centerLat").value(-13.532));
            mvc.perform(get("/api/v1/map/pins").param("zoneId",UUID.randomUUID().toString()).header("Authorization",user.bearer()))
                .andExpect(status().isNotFound());
        } finally {
            jdbc.update("update app_user set zone_id = ? where id = ?", original, user.userId());
            jdbc.update("delete from zone where id = ?", temporary);
        }
    }
    @Test void catalogRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/zones")).andExpect(status().isUnauthorized());
    }
}
