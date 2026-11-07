package eytlyr.com.utp_comercio_backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

/**
 * Hash con pepper para códigos SMS y tokens de refresco: HMAC-SHA256 con el secreto JWT.
 * Así, si alguien lee la base de datos, no puede usar los códigos ni los tokens.
 */
@Component
public class SecretHasher {

	private final SecretKey key;

	public SecretHasher(SecretKey jwtSecretKey) {
		this.key = jwtSecretKey;
	}

	public String hash(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("No se pudo calcular el hash", ex);
		}
	}

	/** Comparación en tiempo constante. */
	public boolean matches(String value, String expectedHash) {
		return MessageDigest.isEqual(hash(value).getBytes(StandardCharsets.UTF_8),
				expectedHash.getBytes(StandardCharsets.UTF_8));
	}
}
