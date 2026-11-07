package eytlyr.com.utp_comercio_backend.storage;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import static eytlyr.com.utp_comercio_backend.storage.StorageService.contentTypeOf;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Fotos en un bucket S3 compatible (Neon Object Storage, AWS, R2, MinIO). El bucket es privado: la API
 * sirve cada foto en GET /files/{nombre}, así la URL guardada no caduca y no se exponen credenciales.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

	private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

	private final S3Client s3;

	private final String bucket;

	private final String prefix;

	public S3StorageService(NearbyProperties props) {
		NearbyProperties.Storage cfg = props.storage();
		if (cfg.bucket() == null || cfg.bucket().isBlank()) {
			throw new IllegalStateException(
					"Falta el bucket de fotos: define NEARBY_S3_BUCKET (y las credenciales AWS_*) en .env");
		}
		this.bucket = cfg.bucket();
		this.prefix = cfg.prefix() == null ? "" : cfg.prefix();
		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(cfg.region()))
				// Los S3 compatibles no aceptan los checksums CRC que el SDK agrega por defecto.
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.credentialsProvider(hasText(cfg.accessKey()) && hasText(cfg.secretKey())
						? StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey()))
						: DefaultCredentialsProvider.builder().build());
		if (hasText(cfg.endpoint())) {
			builder.endpointOverride(URI.create(cfg.endpoint())).forcePathStyle(true);
		}
		this.s3 = builder.build();
		log.info("Fotos en S3: bucket={} endpoint={}", bucket, hasText(cfg.endpoint()) ? cfg.endpoint() : "AWS");
	}

	@Override
	public String store(byte[] bytes, String contentType) {
		String name = UUID.randomUUID() + ("image/png".equals(contentType) ? ".png" : ".jpg");
		s3.putObject(b -> b.bucket(bucket).key(prefix + name).contentType(contentTypeOf(name)),
				RequestBody.fromBytes(bytes));
		return "/files/" + name;
	}

	@Override
	public Optional<StoredFile> load(String name) {
		if (!SAFE_NAME.matcher(name == null ? "" : name).matches()) {
			return Optional.empty();
		}
		try {
			byte[] bytes = s3.getObjectAsBytes(b -> b.bucket(bucket).key(prefix + name)).asByteArray();
			return Optional.of(new StoredFile(bytes, contentTypeOf(name)));
		}
		catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
	}

	@PreDestroy
	void close() {
		s3.close();
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
