package eytlyr.com.utp_comercio_backend.services;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.ConflictException;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.EndorsementResult;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/**
 * Visto Bueno Vecinal. El contador se actualiza en la misma transacción con bloqueo optimista
 * (@Version en NeighborService); el controlador reintenta una vez si hay conflicto.
 */
@Service
public class EndorsementService {

	public static final String NOT_VERIFIED = "ENDORSEMENT_NOT_VERIFIED";

	public static final String OTHER_ZONE = "ENDORSEMENT_OTHER_ZONE";

	public static final String SELF = "ENDORSEMENT_SELF";

	public static final String DUPLICATE = "ENDORSEMENT_DUPLICATE";

	private final ServiceCatalogService catalog;

	private final EndorsementRepository endorsements;

	private final AppUserRepository users;

	public EndorsementService(ServiceCatalogService catalog, EndorsementRepository endorsements,
			AppUserRepository users) {
		this.catalog = catalog;
		this.endorsements = endorsements;
		this.users = users;
	}

	@Transactional
	public EndorsementResult endorse(UUID userId, UUID serviceId) {
		NeighborService s = catalog.service(serviceId);
		AppUser me = users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
		AppUser owner = s.getOwner();
		if (!me.isVerified()) {
			throw new ForbiddenException(NOT_VERIFIED, "Solo vecinos verificados pueden dar visto bueno");
		}
		if (owner.getId().equals(userId)) {
			throw new BusinessRuleException(SELF, "No puedes darte visto bueno a ti mismo");
		}
		if (me.getZone() == null || owner.getZone() == null
				|| !Objects.equals(me.getZone().getId(), owner.getZone().getId())) {
			throw new ForbiddenException(OTHER_ZONE, "Solo los vecinos de la misma zona pueden dar visto bueno");
		}
		if (endorsements.existsByServiceIdAndUserId(serviceId, userId)) {
			throw new ConflictException(DUPLICATE, "Ya diste visto bueno a este servicio");
		}
		endorsements.save(Endorsement.builder().service(s).user(me).build());
		s.setEndorsementCount(s.getEndorsementCount() + 1);
		return new EndorsementResult(serviceId, s.getEndorsementCount(), true);
	}

	@Transactional
	public EndorsementResult withdraw(UUID userId, UUID serviceId) {
		NeighborService s = catalog.service(serviceId);
		Endorsement e = endorsements.findByServiceIdAndUserId(serviceId, userId)
				.orElseThrow(() -> NotFoundException.of("tu visto bueno"));
		endorsements.delete(e);
		s.setEndorsementCount(Math.max(0, s.getEndorsementCount() - 1));
		return new EndorsementResult(serviceId, s.getEndorsementCount(), false);
	}
}
