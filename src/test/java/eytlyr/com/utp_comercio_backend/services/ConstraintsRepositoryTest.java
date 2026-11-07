package eytlyr.com.utp_comercio_backend.services;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;
import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListing;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListingRepository;
import eytlyr.com.utp_comercio_backend.emergency.ResourceType;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.posts.PostCategory;
import eytlyr.com.utp_comercio_backend.posts.PostRepository;
import eytlyr.com.utp_comercio_backend.posts.PostType;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restricciones del esquema V1 contra PostgreSQL real. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ConstraintsRepositoryTest {

	@Autowired
	ZoneRepository zones;

	@Autowired
	AppUserRepository users;

	@Autowired
	NeighborServiceRepository services;

	@Autowired
	EndorsementRepository endorsements;

	@Autowired
	PostRepository posts;

	@Autowired
	ResourceListingRepository listings;

	Zone zone;

	AppUser owner;

	AppUser neighbor;

	@BeforeEach
	void setUp() {
		zone = zones.save(Zone.builder().name("Zona de prueba").district("SJL").centerLat(-11.979).centerLng(-77.003)
				.radiusM(1500).build());
		owner = users.save(AppUser.builder().phone("900000001").name("Dueño").zone(zone).build());
		neighbor = users.save(AppUser.builder().phone("900000002").name("Vecina").zone(zone).build());
	}

	@Test
	void duplicateEndorsementIsRejected() {
		NeighborService service = services.save(NeighborService.builder().owner(owner).title("Gasfitería")
				.category(ServiceCategory.PLUMBING).lat(-11.979).lng(-77.003).build());
		endorsements.saveAndFlush(Endorsement.builder().service(service).user(neighbor).build());

		assertThatThrownBy(() -> endorsements.saveAndFlush(Endorsement.builder().service(service).user(neighbor).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void serviceStartsWithVersionAndDefaults() {
		NeighborService service = services.saveAndFlush(NeighborService.builder().owner(owner).title("Electricidad")
				.category(ServiceCategory.ELECTRICITY).lat(-11.979).lng(-77.003).build());
		assertThat(service.getVersion()).isZero();
		assertThat(service.getEndorsementCount()).isZero();
		assertThat(service.isActive()).isTrue();
		assertThat(service.getCreatedAt()).isNotNull();
	}

	@Test
	void sameClientIdTwiceIsRejected() {
		UUID clientId = UUID.randomUUID();
		posts.saveAndFlush(post(clientId));
		assertThatThrownBy(() -> posts.saveAndFlush(post(clientId))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void postsWithoutClientIdDoNotCollide() {
		posts.saveAndFlush(post(null));
		posts.saveAndFlush(post(null));
		assertThat(posts.count()).isEqualTo(2);
	}

	@Test
	void listingWithBothResourceKindsIsRejected() {
		ResourceListing both = listing();
		both.setResourceType(ResourceType.WATER);
		both.setMitigationType(MitigationType.SANDBAGS);
		assertThatThrownBy(() -> listings.saveAndFlush(both)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void listingWithoutResourceKindIsRejected() {
		assertThatThrownBy(() -> listings.saveAndFlush(listing())).isInstanceOf(DataIntegrityViolationException.class);
	}

	private Post post(UUID clientId) {
		return Post.builder().author(neighbor).zone(zone).type(PostType.NEED).category(PostCategory.TOOL_LOAN)
				.text("Necesito un taladro").lat(-11.979).lng(-77.003).clientId(clientId).build();
	}

	private ResourceListing listing() {
		return ResourceListing.builder().user(neighbor).zone(zone).kind(ListingKind.HAVE).text("Agua").lat(-11.979)
				.lng(-77.003).build();
	}
}
