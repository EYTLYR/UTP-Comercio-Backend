package eytlyr.com.utp_comercio_backend;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration { }

    @Test
    void environmentStyleKeyActivatesProductionAndDemo(@TempDir Path directory) throws Exception {
        var env = directory.resolve("backend.env.properties");
        Files.writeString(env, "SPRING_PROFILES_ACTIVE=prod,demo\n");
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=classpath:/application.yml",
                     "--spring.config.import=" + env.toUri())) {
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod", "demo");
        }
    }

    @Test
    void explicitProfileOverridesEnvironmentStyleKey() {
        try (var context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=classpath:/application.yml",
                     "--spring.config.import=optional:file:./target/no-test-env.properties",
                     "--SPRING_PROFILES_ACTIVE=prod,demo",
                     "--spring.profiles.active=local")) {
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("local", "dev");
        }
    }
}
