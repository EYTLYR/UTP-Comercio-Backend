package eytlyr.com.utp_comercio_backend.posts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.zone.ZoneStatsRepository;

@Component
public class PostEventListener {

	private static final Logger log = LoggerFactory.getLogger(PostEventListener.class);

	private final ZoneStatsRepository zoneStats;

	public PostEventListener(ZoneStatsRepository zoneStats) {
		this.zoneStats = zoneStats;
	}

	@Async
	@EventListener
	@Transactional
	public void onPostResolved(PostResolvedEvent event) {
		if (event.zoneId() != null) {
			log.info("Incrementing favors_resolved in zone_stats for zone: {}", event.zoneId());
			zoneStats.incrementFavorsResolved(event.zoneId());
		}
	}

}
