package eytlyr.com.utp_comercio_backend.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.AuthResponse;
import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.OtpRequestResponse;
import eytlyr.com.utp_comercio_backend.common.ApiException;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.common.TooManyRequestsException;
import eytlyr.com.utp_comercio_backend.common.UnauthorizedException;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.sms.SmsGateway;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.user.Role;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;

/** Acceso sin contraseña: código SMS de un solo uso, token de acceso JWT y token de refresco rotativo. */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	public static final String OTP_INVALID = "OTP_INVALID";

	public static final String OTP_TOO_MANY_REQUESTS = "OTP_TOO_MANY_REQUESTS";

	public static final String OTP_TOO_MANY_ATTEMPTS = "OTP_TOO_MANY_ATTEMPTS";

	public static final String REFRESH_INVALID = "REFRESH_INVALID";

	static final long RESEND_SECONDS = 30;

	static final String NEW_USER_NAME = "Vecino";

	private static final String TOO_MANY = "Demasiados intentos, espera unos minutos";

	private final SecureRandom random = new SecureRandom();

	private final OtpCodeRepository otps;

	private final RefreshTokenRepository refreshTokens;

	private final AppUserRepository users;

	private final SecretHasher hasher;

	private final JwtService jwt;

	private final SmsGateway sms;

	private final Clock clock;

	private final NearbyProperties.Otp otpProps;

	private final NearbyProperties.Roles roles;

	private final Duration refreshTtl;

	/** Código fijo permitido: solo con perfil dev o local, nunca con prod (ver {@link #devCodeAllowed}). */
	private final boolean dev;

	public AuthService(OtpCodeRepository otps, RefreshTokenRepository refreshTokens, AppUserRepository users,
			SecretHasher hasher, JwtService jwt, SmsGateway sms, Clock clock, NearbyProperties props,
			Environment environment) {
		this.otps = otps;
		this.refreshTokens = refreshTokens;
		this.users = users;
		this.hasher = hasher;
		this.jwt = jwt;
		this.sms = sms;
		this.clock = clock;
		this.otpProps = props.otp();
		this.roles = props.roles();
		this.refreshTtl = Duration.ofDays(props.refreshTokenDays());
		String[] profiles = environment.getActiveProfiles();
		this.dev = devCodeAllowed(profiles, otpProps.devFixedCode());
		if (!dev && otpProps.devFixedCode() != null && !otpProps.devFixedCode().isBlank()) {
			log.warn("NEARBY_OTP_DEV_CODE se ignora con los perfiles {}: los códigos SMS son aleatorios.",
					Arrays.toString(profiles));
		}
	}

	/**
	 * El código fijo (y devolverlo en devCode) solo se permite si hay un código configurado, está activo el
	 * perfil dev o local, y NO está activo prod. Con prod nunca, aunque NEARBY_OTP_DEV_CODE tenga valor.
	 */
	static boolean devCodeAllowed(String[] activeProfiles, String devFixedCode) {
		if (devFixedCode == null || devFixedCode.isBlank()) {
			return false;
		}
		boolean prod = Arrays.asList(activeProfiles).contains("prod");
		boolean devOrLocal = Arrays.stream(activeProfiles).anyMatch(p -> p.equals("dev") || p.equals("local"));
		return devOrLocal && !prod;
	}

	// ---------------------------------------------------------------- código SMS

	@Transactional
	public OtpRequestResponse requestOtp(String phone) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		if (otps.countByPhoneAndCreatedAtAfter(phone, now.minusHours(1)) >= otpProps.maxRequestsPerHour()) {
			throw new TooManyRequestsException(OTP_TOO_MANY_REQUESTS, TOO_MANY);
		}
		otps.invalidatePending(phone);
		String code = newCode();
		long ttl = Duration.ofMinutes(otpProps.expiryMinutes()).toSeconds();
		otps.save(OtpCode.builder()
				.phone(phone)
				.codeHash(hasher.hash(phone + ":" + code))
				.expiresAt(now.plusSeconds(ttl))
				.build());
		try {
			sms.send(phone, "Tu código Nearby es " + code + ". No lo compartas.");
		}
		catch (RuntimeException ex) {
			// Con código fijo (dev/demo) se puede seguir; sin él, el vecino nunca recibiría el código.
			log.warn("No se pudo enviar el código a {}: {}", TextUtils.maskPhone(phone), ex.getMessage());
			if (!isDev()) {
				throw new BusinessRuleException("SMS_SEND_FAILED",
						"No pudimos enviar el SMS a ese número. Inténtalo en unos minutos.");
			}
		}
		return new OtpRequestResponse(ttl, RESEND_SECONDS, isDev() ? code : null);
	}

	/**
	 * Verifica el código. Los intentos fallidos se guardan aunque la respuesta sea un error
	 * (noRollbackFor), para que el límite de intentos funcione.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public AuthResponse verifyOtp(String phone, String code) {
		OtpCode otp = otps.findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(phone)
				.orElseThrow(AuthService::otpInvalid);
		otp.setAttempts(otp.getAttempts() + 1);
		if (otp.getAttempts() > otpProps.maxAttempts()) {
			otp.setConsumed(true);
			throw new TooManyRequestsException(OTP_TOO_MANY_ATTEMPTS, TOO_MANY);
		}
		boolean expired = !otp.getExpiresAt().isAfter(OffsetDateTime.now(clock));
		if (expired || !hasher.matches(phone + ":" + code, otp.getCodeHash())) {
			throw otpInvalid();
		}
		otp.setConsumed(true);

		AppUser existing = users.findByPhone(phone).orElse(null);
		boolean isNew = existing == null;
		AppUser user = isNew
				? users.save(AppUser.builder().phone(phone).name(NEW_USER_NAME).role(Role.NEIGHBOR).build())
				: existing;
		// Solo el administrador de la plataforma se define por celular (NEARBY_ADMIN_PHONES): es quien aprueba a
		// las Municipalidades. NEARBY_BRIGADE_PHONES es opcional (demos) y solo sube a un vecino, nunca baja a nadie.
		if (roles != null && roles.isAdmin(phone)) {
			user.setRole(Role.ADMIN);
		}
		else if (roles != null && roles.isBrigade(phone) && user.getRole() == Role.NEIGHBOR) {
			user.setRole(Role.BRIGADIST);
		}
		return issue(user, isNew);
	}

	// ---------------------------------------------------------------- tokens

	/** Rota el token de refresco: el usado queda revocado y se entrega uno nuevo. */
	@Transactional(noRollbackFor = ApiException.class)
	public AuthResponse refresh(String refreshToken) {
		RefreshToken stored = refreshTokens.findByTokenHash(hasher.hash(refreshToken))
				.orElseThrow(AuthService::refreshInvalid);
		if (stored.isRevoked()) {
			// Un token ya rotado se volvió a usar: posible robo. Se cierran todas las sesiones del usuario.
			refreshTokens.revokeAllByUserId(stored.getUser().getId());
			throw refreshInvalid();
		}
		if (!stored.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
			throw refreshInvalid();
		}
		stored.setRevoked(true);
		return issue(stored.getUser(), false);
	}

	@Transactional
	public void logout(UUID userId) {
		refreshTokens.revokeAllByUserId(userId);
	}

	private AuthResponse issue(AppUser user, boolean isNew) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		refreshTokens.save(RefreshToken.builder()
				.user(user)
				.tokenHash(hasher.hash(refresh))
				.expiresAt(OffsetDateTime.now(clock).plus(refreshTtl))
				.build());
		return new AuthResponse(jwt.accessToken(user), refresh, jwt.accessTtlSeconds(), isNew, UserDto.from(user));
	}

	// ---------------------------------------------------------------- utilidades

	private String newCode() {
		if (isDev()) {
			return otpProps.devFixedCode();
		}
		StringBuilder sb = new StringBuilder(otpProps.length());
		for (int i = 0; i < otpProps.length(); i++) {
			sb.append(random.nextInt(10));
		}
		return sb.toString();
	}

	private boolean isDev() {
		return dev;
	}

	private static UnauthorizedException otpInvalid() {
		return new UnauthorizedException(OTP_INVALID, "Código incorrecto. Revisa el SMS e inténtalo otra vez");
	}

	private static UnauthorizedException refreshInvalid() {
		return new UnauthorizedException(REFRESH_INVALID, "Tu sesión venció. Vuelve a ingresar con tu celular.");
	}
}
