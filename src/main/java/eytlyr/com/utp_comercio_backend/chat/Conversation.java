package eytlyr.com.utp_comercio_backend.chat;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.services.NeighborService;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Chat entre dos vecinos, opcionalmente sobre una publicación o un servicio. */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id")
	private Post post;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "service_id")
	private NeighborService service;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_a_id", nullable = false)
	private AppUser userA;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_b_id", nullable = false)
	private AppUser userB;

	@Column(name = "context_text", length = 200)
	private String contextText;

	@Column(name = "user_a_read_at")
	private OffsetDateTime userAReadAt;

	@Column(name = "user_b_read_at")
	private OffsetDateTime userBReadAt;

	public boolean isParticipant(UUID userId) {
		return userA.getId().equals(userId) || userB.getId().equals(userId);
	}

	public AppUser other(UUID userId) {
		return userA.getId().equals(userId) ? userB : userA;
	}

	public OffsetDateTime readAt(UUID userId) {
		return userA.getId().equals(userId) ? userAReadAt : userBReadAt;
	}

	public void markRead(UUID userId, OffsetDateTime when) {
		if (userA.getId().equals(userId)) {
			userAReadAt = when;
		}
		else {
			userBReadAt = when;
		}
	}

}
