package protrader.funds.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;

/**
 * The auth service's public signing keys (JWKS), by kid. Refreshed on a timer, and early when a token
 * names a kid we don't know (auth rotated its key) — but never more often than MIN_GAP, so junk tokens
 * can't make us hammer the auth service.
 */
public final class JwksKeys {

	private static final Logger log = LoggerFactory.getLogger(JwksKeys.class);
	private static final Duration MIN_GAP = Duration.ofSeconds(30);
	private static final int MAX_BYTES = 1 << 20;

	private final String source;
	private final Clock clock;
	private final AtomicReference<Map<String, OctetKeyPair>> keys = new AtomicReference<>();
	private volatile Instant lastFetch = Instant.EPOCH;

	public JwksKeys(String source, Clock clock) {
		this.source = source;
		this.clock = clock;
	}

	public boolean ready() {
		return keys.get() != null;
	}

	/** The key for this kid; on an unknown kid, one early refresh (rate-limited) and a second look. */
	public Optional<OctetKeyPair> key(String kid) {
		Map<String, OctetKeyPair> current = keys.get();
		if (current != null && current.containsKey(kid)) {
			return Optional.of(current.get(kid));
		}
		if (Duration.between(lastFetch, clock.instant()).compareTo(MIN_GAP) >= 0) {
			refresh();
			current = keys.get();
			if (current != null && current.containsKey(kid)) {
				return Optional.of(current.get(kid));
			}
		}
		return Optional.empty();
	}

	/** Downloads and swaps in the key set. On failure keeps the old one. Returns true on success. */
	public synchronized boolean refresh() {
		lastFetch = clock.instant();
		try {
			byte[] body = SourceReader.read(source, MAX_BYTES);
			Map<String, OctetKeyPair> parsed = parse(new String(body, StandardCharsets.UTF_8));
			keys.set(parsed);
			log.debug("jwks loaded: {} key(s)", parsed.size());
			return true;
		} catch (Exception e) {
			log.warn("jwks refresh from {} failed; keeping previous keys: {}", source, e.toString());
			return false;
		}
	}

	/** Same filter as the gateway: OKP/Ed25519 signing keys with a kid; at least one required. */
	static Map<String, OctetKeyPair> parse(String json) throws Exception {
		Map<String, OctetKeyPair> out = new HashMap<>();
		for (JWK jwk : JWKSet.parse(json).getKeys()) {
			if (!(jwk instanceof OctetKeyPair okp) || !Curve.Ed25519.equals(okp.getCurve())) {
				continue;
			}
			if (jwk.getKeyUse() != null && !KeyUse.SIGNATURE.equals(jwk.getKeyUse())) {
				continue;
			}
			if (jwk.getAlgorithm() != null && !"EdDSA".equals(jwk.getAlgorithm().getName())) {
				continue;
			}
			if (jwk.getKeyID() == null || jwk.getKeyID().isEmpty()) {
				throw new IllegalArgumentException("JWKS key with empty kid");
			}
			if (out.put(jwk.getKeyID(), okp.toPublicJWK()) != null) {
				throw new IllegalArgumentException("duplicate JWKS kid " + jwk.getKeyID());
			}
		}
		if (out.isEmpty()) {
			throw new IllegalArgumentException("JWKS has no usable Ed25519 keys");
		}
		return Map.copyOf(out);
	}
}
