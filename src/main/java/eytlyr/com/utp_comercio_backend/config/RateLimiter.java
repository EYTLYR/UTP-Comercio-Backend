package eytlyr.com.utp_comercio_backend.config;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite simple en memoria con ventana deslizante. Suficiente para una sola instancia;
 * con varias réplicas habría que moverlo a Redis.
 */
public class RateLimiter {

	private static final int MAX_KEYS = 50_000;

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	private final Clock clock;

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** @return true si la petición entra; false si superó el límite en la ventana */
	public boolean tryAcquire(String key, int limit, Duration window) {
		if (hits.size() > MAX_KEYS) {
			hits.clear(); // protección de memoria ante muchas claves distintas
		}
		long now = clock.millis();
		long from = now - window.toMillis();
		Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (q) {
			while (!q.isEmpty() && q.peekFirst() <= from) {
				q.pollFirst();
			}
			if (q.size() >= limit) {
				return false;
			}
			q.addLast(now);
			return true;
		}
	}
}
