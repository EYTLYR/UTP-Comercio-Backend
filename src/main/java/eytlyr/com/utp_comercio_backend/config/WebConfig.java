package eytlyr.com.utp_comercio_backend.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import eytlyr.com.utp_comercio_backend.common.TooManyRequestsException;

/** correlationId por petición (MDC "cid" y header X-Correlation-Id) y límites de peticiones. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	public static final String CORRELATION_HEADER = "X-Correlation-Id";

	private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

	private final RateLimiter limiter;

	private final NearbyProperties.RateLimit limits;

	public WebConfig(NearbyProperties props) {
		this.limiter = new RateLimiter(Clock.system(ClockConfig.LIMA));
		this.limits = props.rateLimit();
	}

	@Bean
	FilterRegistrationBean<OncePerRequestFilter> correlationIdFilter() {
		OncePerRequestFilter filter = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
					throws ServletException, IOException {
				String incoming = req.getHeader(CORRELATION_HEADER);
				String cid = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming
						: UUID.randomUUID().toString().substring(0, 8);
				MDC.put("cid", cid);
				res.setHeader(CORRELATION_HEADER, cid);
				try {
					chain.doFilter(req, res);
				}
				finally {
					MDC.remove("cid");
				}
			}
		};
		FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
		reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return reg;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if (!limits.enabled()) {
			return;
		}
		registry.addInterceptor(limit("sos", limits.sosPerMinute(), Duration.ofMinutes(1), false))
				.addPathPatterns("/api/v1/sos");
		registry.addInterceptor(limit("risk", limits.riskReportsPerHour(), Duration.ofHours(1), false))
				.addPathPatterns("/api/v1/risk-reports");
		registry.addInterceptor(limit("otp", limits.otpRequestsPerHourPerIp(), Duration.ofHours(1), true))
				.addPathPatterns("/api/v1/auth/otp/request");
	}

	/** Solo cuenta los POST. Clave: el usuario del token, o la IP si no hay token. */
	private HandlerInterceptor limit(String name, int max, Duration window, boolean byIp) {
		return new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
				if (!"POST".equals(req.getMethod())) {
					return true;
				}
				Authentication auth = SecurityContextHolder.getContext().getAuthentication();
				String who = !byIp && auth != null && auth.isAuthenticated() ? auth.getName() : req.getRemoteAddr();
				if (!limiter.tryAcquire(name + ":" + who, max, window)) {
					throw new TooManyRequestsException("RATE_LIMITED", "Demasiados intentos, espera unos minutos");
				}
				return true;
			}
		};
	}
}
