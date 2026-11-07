package eytlyr.com.utp_comercio_backend;

import java.io.IOException;
import java.nio.file.Path;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL con PostGIS para pruebas y para ./mvnw spring-boot:test-run.
 * Con Docker usa Testcontainers (imagen postgis/postgis); sin Docker arranca un clúster temporal
 * con una instalación local de PostgreSQL que tenga PostGIS (ver {@link LocalPostgis}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	@Conditional(DockerAvailable.class)
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(
				// Mismas versiones que Neon: PostgreSQL 18 y PostGIS 3.6.
				DockerImageName.parse("postgis/postgis:18-3.6-alpine").asCompatibleSubstituteFor("postgres"));
	}

	@Bean(destroyMethod = "close")
	@Conditional(DockerMissing.class)
	LocalPostgis localPostgis() throws IOException {
		Path home = LocalPostgis.findHome()
			.orElseThrow(() -> new IllegalStateException("""
					Las pruebas necesitan PostgreSQL con PostGIS. Opciones:
					  1) Docker (usa la imagen postgis/postgis), o
					  2) PostgreSQL instalado con la extensión PostGIS (Windows: instalador de EDB y
					     Stack Builder > Spatial Extensions > PostGIS). Si no está en la ruta habitual,
					     define NEARBY_PG_HOME con la carpeta que contiene bin/ y share/."""));
		return LocalPostgis.start(home);
	}

	@Bean
	@Conditional(DockerMissing.class)
	DataSource dataSource(LocalPostgis postgres) {
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(postgres.jdbcUrl());
		ds.setUsername("postgres");
		ds.setPassword("");
		ds.setMaximumPoolSize(5);
		return ds;
	}

	static final class DockerAvailable implements Condition {

		private static Boolean available;

		static synchronized boolean check() {
			if (available == null) {
				available = DockerClientFactory.instance().isDockerAvailable();
			}
			return available;
		}

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return check();
		}
	}

	static final class DockerMissing implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return !DockerAvailable.check();
		}
	}
}
