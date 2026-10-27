package protrader.funds.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API-key table, polled from the auth service's internal snapshot (or a file in dev) exactly like
 * order_gateway/auth/apikey.go: {"version":N,"keys":[{key_id, account_id, secret_sha256, disabled, label}]}.
 * Only the SHA-256 of each secret is known here; comparison is constant-time and an unknown key id is
 * compared against a random dummy hash, so timing doesn't reveal which key ids exist.
 */
public final class ApiKeys {

	private static final Logger log = LoggerFactory.getLogger(ApiKeys.class);
	private static final int MAX_BYTES = 4 << 20;
	private static final JsonMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	record Entry(long accountId, byte[] hash, boolean disabled) {
	}

	record Table(long version, Map<String, Entry> byId) {
	}

	record KeyJson(@JsonProperty("key_id") String keyId, @JsonProperty("account_id") long accountId,
			@JsonProperty("secret_sha256") String secretSha256, @JsonProperty("disabled") boolean disabled,
			@JsonProperty("label") String label) {
	}

	record SnapshotJson(@JsonProperty("version") long version, @JsonProperty("keys") java.util.List<KeyJson> keys) {
	}

	private final String source;
	private final AtomicReference<Table> current = new AtomicReference<>();
	private final byte[] dummy = new byte[32];

	public ApiKeys(String source) {
		this.source = source;
		new SecureRandom().nextBytes(dummy);
	}

	public boolean ready() {
		return current.get() != null;
	}

	/** Loads a newer snapshot if there is one. On failure keeps the previous table. Returns true on success. */
	public boolean refresh() {
		Table have = current.get();
		long haveVersion = have == null ? 0 : have.version();
		try {
			String url = source;
			if (source.startsWith("http://") || source.startsWith("https://")) {
				url += (source.contains("?") ? "&" : "?") + "if_newer_than=" + URLEncoder.encode(Long.toString(haveVersion), StandardCharsets.UTF_8);
			}
			byte[] body = SourceReader.read(url, MAX_BYTES);
			if (body == null) {
				return true; // 304: nothing new
			}
			Table table = parse(body);
			if (table.version() <= haveVersion) {
				return true;
			}
			current.set(table);
			log.info("api keys loaded: version {}, {} key(s)", table.version(), table.byId().size());
			return true;
		} catch (Exception e) {
			log.warn("api key refresh from {} failed; keeping previous list: {}", source, e.toString());
			return false;
		}
	}

	/** Same checks as the gateway's ParseKeySnapshot. */
	static Table parse(byte[] json) {
		SnapshotJson snap = JSON.readValue(json, SnapshotJson.class);
		if (snap.version() < 1) {
			throw new IllegalArgumentException("unsupported snapshot version");
		}
		Map<String, Entry> byId = new HashMap<>();
		if (snap.keys() != null) {
			for (KeyJson k : snap.keys()) {
				if (k.keyId() == null || k.keyId().isEmpty()) {
					throw new IllegalArgumentException("key_id must not be empty");
				}
				if (k.accountId() <= 0) {
					throw new IllegalArgumentException(k.keyId() + ": account_id must not be 0");
				}
				if (k.secretSha256() == null || k.secretSha256().length() != 64) {
					throw new IllegalArgumentException(k.keyId() + ": secret_sha256 must be 64 hex characters");
				}
				byte[] hash = HexFormat.of().parseHex(k.secretSha256());
				if (byId.put(k.keyId(), new Entry(k.accountId(), hash, k.disabled())) != null) {
					throw new IllegalArgumentException(k.keyId() + ": duplicate key_id");
				}
			}
		}
		return new Table(snap.version(), Map.copyOf(byId));
	}

	/** The key's account if id + secret match and the key is enabled, else empty. Call only when ready(). */
	public OptionalLong verify(String keyId, String secret) {
		Table table = current.get();
		if (table == null || keyId == null || secret == null || keyId.isEmpty() || secret.isEmpty()
				|| keyId.length() > 32 || secret.length() > 128) {
			return OptionalLong.empty();
		}
		byte[] got = sha256(secret);
		Entry entry = table.byId().get(keyId);
		byte[] want = entry == null ? dummy : entry.hash();
		boolean same = MessageDigest.isEqual(got, want);
		if (entry == null || !same || entry.disabled()) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(entry.accountId());
	}

	private static byte[] sha256(String s) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
