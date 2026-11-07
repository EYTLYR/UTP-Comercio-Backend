package eytlyr.com.utp_comercio_backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Emite el token de acceso (JWT HS256) con sub = userId, phone, role y zoneId. */
@Service
public class JwtService {

	public static final String ISSUER = "nearby-api";

	public static final String CLAIM_PHONE = "phone";

	public static final String CLAIM_ROLE = "role";

	public static final String CLAIM_ZONE = "zoneId";

	private final JwtEncoder encoder;

	private final Clock clock;

	private final Duration accessTtl;

	public JwtService(JwtEncoder encoder, Clock clock, NearbyProperties props) {
		this.encoder = encoder;
		this.clock = clock;
		this.accessTtl = Duration.ofMinutes(props.accessTokenMinutes());
	}

	public String accessToken(AppUser user) {
		Instant now = clock.instant();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(accessTtl))
				.claim(CLAIM_PHONE, user.getPhone())
				.claim(CLAIM_ROLE, user.getRole().name());
		if (user.getZone() != null) {
			claims.claim(CLAIM_ZONE, user.getZone().getId().toString());
		}
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	public long accessTtlSeconds() {
		return accessTtl.toSeconds();
	}
}
