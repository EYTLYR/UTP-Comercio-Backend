package eytlyr.com.utp_comercio_backend.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Bloque de configuración propio "nearby" de application.yml. */
@Validated
@ConfigurationProperties(prefix = "nearby")
public record NearbyProperties(
		@NotBlank @Size(min = 32, message = "nearby.jwt-secret debe tener al menos 32 caracteres") String jwtSecret,
		@DefaultValue("60") @Min(1) int accessTokenMinutes,
		@DefaultValue("30") @Min(1) int refreshTokenDays,
		@DefaultValue @Valid Otp otp,
		@DefaultValue Sms sms,
		@DefaultValue Emergency emergency,
		@DefaultValue Prevention prevention,
		@DefaultValue Ai ai,
		@DefaultValue Storage storage,
		@DefaultValue Cors cors,
		@DefaultValue RateLimit rateLimit,
		@DefaultValue Roles roles) {

	/**
	 * Roles por celular (listas separadas por coma). Al ingresar, esos números quedan como ADMIN
	 * (Municipalidad) o BRIGADIST. Los demás vecinos se registran como NEIGHBOR.
	 */
	public record Roles(String adminPhones, String brigadePhones) {

		public boolean isAdmin(String phone) {
			return contains(adminPhones, phone);
		}

		public boolean isBrigade(String phone) {
			return contains(brigadePhones, phone);
		}

		private static boolean contains(String list, String phone) {
			if (list == null || list.isBlank() || phone == null) {
				return false;
			}
			for (String p : list.split(",")) {
				String digits = p.replaceAll("\\D", "");
				if (digits.length() >= 9 && digits.substring(digits.length() - 9).equals(phone)) {
					return true;
				}
			}
			return false;
		}
	}

	/** Código SMS de acceso. Aleatorio salvo que devFixedCode esté definido (NEARBY_OTP_DEV_CODE, solo demos). */
	public record Otp(
			@DefaultValue("6") @Min(4) int length,
			@DefaultValue("5") @Min(1) int expiryMinutes,
			@DefaultValue("5") @Min(1) int maxAttempts,
			@DefaultValue("5") @Min(1) int maxRequestsPerHour,
			String devFixedCode) {
	}

	/**
	 * provider: "console" (log, por defecto) o "gateway" (microservicio SMS, ver micro/sms-gateway).
	 * inboundToken protege /api/v1/sms/inbound, donde el microservicio reenvía los SMS recibidos.
	 */
	public record Sms(@DefaultValue("console") String provider, String inboundToken, @DefaultValue Gateway gateway) {

		/** Microservicio SMS: url base (http://localhost:3000) y la clave que espera en X-Api-Key. */
		public record Gateway(@DefaultValue("http://localhost:3000") String url, String apiKey) {
		}
	}

	/** Activación automática del modo emergencia por cantidad de SOS en una ventana de tiempo. */
	public record Emergency(
			@DefaultValue("5") int autoActivateSosCount,
			@DefaultValue("10") int autoActivateWindowMinutes) {
	}

	/** Agrupación de reportes de riesgo (El Niño). */
	public record Prevention(
			@DefaultValue("150") int clusterRadiusM,
			@DefaultValue("6") int clusterWindowHours) {
	}

	/**
	 * Proveedor de IA de texto: "rules" (sin credenciales) o "anthropic".
	 * indexOnEvents: indexar cada contenido nuevo en segundo plano.
	 * autoRescuePlan: generar el plan de rescate al pasar la zona a EMERGENCY.
	 */
	public record Ai(
			@DefaultValue("rules") String provider,
			@DefaultValue("claude-haiku-5-5") String model,
			String apiKey,
			@DefaultValue("true") boolean indexOnEvents,
			@DefaultValue("true") boolean autoRescuePlan) {

		public boolean isAnthropic() {
			return "anthropic".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
		}
	}

	/**
	 * provider: "s3" (por defecto: Neon Object Storage o cualquier S3 compatible) o "memory" (solo pruebas).
	 * endpoint vacío = AWS S3; accessKey/secretKey vacíos = cadena de credenciales por defecto de AWS.
	 */
	public record Storage(@DefaultValue("s3") String provider, String bucket, String endpoint,
			@DefaultValue("us-east-2") String region, String accessKey, String secretKey,
			@DefaultValue("risk-photos/") String prefix) {
	}

	/** Límites de peticiones en memoria (por usuario; el de OTP, por IP). */
	public record RateLimit(@DefaultValue("true") boolean enabled, @DefaultValue("10") int sosPerMinute,
			@DefaultValue("20") int riskReportsPerHour, @DefaultValue("30") int otpRequestsPerHourPerIp) {
	}

	/** Orígenes permitidos para CORS (patrones). Vacío = sin CORS. */
	public record Cors(@DefaultValue List<String> allowedOriginPatterns) {
	}
}
