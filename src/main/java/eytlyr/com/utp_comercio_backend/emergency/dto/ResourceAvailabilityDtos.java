package eytlyr.com.utp_comercio_backend.emergency.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.emergency.CandidateStatus;
import eytlyr.com.utp_comercio_backend.emergency.ResourceRequestStatus;
import eytlyr.com.utp_comercio_backend.emergency.ResourceType;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;

public final class ResourceAvailabilityDtos {

	private ResourceAvailabilityDtos() {
	}

	public record CreateAvailabilityPingRequest(
			ResourceType resourceType,
			MitigationType mitigationType,
			Double lat,
			Double lng,
			@Size(max = 500) String note) {
	}

	public record AvailabilityPingResponseDto(
			UUID id,
			UUID requesterId,
			String requesterName,
			String resourceType,
			String mitigationType,
			String resourceLabel,
			ResourceRequestStatus status,
			double lat,
			double lng,
			String note,
			UUID acceptedById,
			String acceptedByName,
			int candidatesCount,
			OffsetDateTime createdAt) {
	}

	public record CandidatePingDto(
			UUID requestId,
			UUID candidateId,
			String requesterName,
			String resourceLabel,
			String note,
			double lat,
			double lng,
			CandidateStatus status,
			OffsetDateTime notifiedAt) {
	}

}

