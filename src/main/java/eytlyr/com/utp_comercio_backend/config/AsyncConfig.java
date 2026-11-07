package eytlyr.com.utp_comercio_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita @Async: los avisos (push y SMS) salen en el pool de tareas de Spring Boot. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
}
