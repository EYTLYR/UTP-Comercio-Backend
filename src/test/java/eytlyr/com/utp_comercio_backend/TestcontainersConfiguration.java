package eytlyr.com.utp_comercio_backend;

import java.io.IOException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

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
 * PostgreSQL 16 para pruebas y para ./mvnw spring-boot:test-run.
 * Con Docker usa Testcontainers; sin Docker usa un PostgreSQL embebido (zonky) con el mismo major.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	@Conditional(DockerAvailable.class)
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

	@Bean(destroyMethod = "close")
	@Conditional(DockerMissing.class)
	EmbeddedPostgres embeddedPostgres() throws IOException {
		return EmbeddedPostgres.builder()
				.setLocaleConfig("encoding", "UTF8")
				.setLocaleConfig("locale", "C")
				.setServerConfig("timezone", "UTC")
				.start();
	}

	@Bean
	@Conditional(DockerMissing.class)
	DataSource dataSource(EmbeddedPostgres postgres) {
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(postgres.getJdbcUrl("postgres", "postgres"));
		ds.setUsername("postgres");
		ds.setPassword("postgres");
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
