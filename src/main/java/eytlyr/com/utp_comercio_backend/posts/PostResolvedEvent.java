package eytlyr.com.utp_comercio_backend.posts;

import java.util.UUID;

public record PostResolvedEvent(UUID postId, UUID authorId, UUID zoneId) {
}

