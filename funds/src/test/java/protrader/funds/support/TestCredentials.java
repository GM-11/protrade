package protrader.funds.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Fake auth-service output for tests: a JWKS file with one Ed25519 key, and an API-key snapshot file
 * in the gateway's format. Tokens are signed with the matching private key.
 */
public final class TestCredentials {

	public static final String ISSUER = "protrader-identity";
	public static final String AUDIENCE = "order-gateway";

	/** API key that belongs to account 2000. */
	public static final String KEY_ID = "PKTESTKEY1";
	public static final String KEY_SECRET = "test-secret-1";
	public static final long KEY_ACCOUNT = 2000;
	/** Disabled key of account 2001. */
	public static final String DISABLED_KEY_ID = "PKTESTOFF1";
	public static final String DISABLED_KEY_SECRET = "test-secret-2";

	public static final OctetKeyPair KEY = newKey("test-kid-1");
	/** A second key that is NOT in the JWKS. */
	public static final OctetKeyPair OTHER_KEY = newKey("test-kid-1");

	public final Path jwksFile;
	public final Path apiKeysFile;

	private TestCredentials(Path jwksFile, Path apiKeysFile) {
		this.jwksFile = jwksFile;
		this.apiKeysFile = apiKeysFile;
	}

	public static TestCredentials writeToTempDir() {
		try {
			Path dir = Files.createTempDirectory("funds-test-auth");
			Path jwks = dir.resolve("jwks.json");
			Files.writeString(jwks, new JWKSet(KEY.toPublicJWK()).toString());
			Path keys = dir.resolve("keys.json");
			Files.writeString(keys, """
					{"version":1,"keys":[
					 {"key_id":"%s","account_id":%d,"secret_sha256":"%s","disabled":false,"label":"test"},
					 {"key_id":"%s","account_id":2001,"secret_sha256":"%s","disabled":true,"label":"off"}
					]}""".formatted(KEY_ID, KEY_ACCOUNT, sha256Hex(KEY_SECRET), DISABLED_KEY_ID, sha256Hex(DISABLED_KEY_SECRET)));
			return new TestCredentials(jwks, keys);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A valid access token for this account, as the auth service would issue it. */
	public static String token(long accountId) {
		return token(KEY, ISSUER, AUDIENCE, accountId, Instant.now(), Duration.ofMinutes(15));
	}

	public static String token(OctetKeyPair key, String issuer, String audience, long accountId, Instant issuedAt, Duration ttl) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(issuer)
					.audience(audience)
					.subject(Long.toString(accountId))
					.issueTime(Date.from(issuedAt))
					.notBeforeTime(Date.from(issuedAt))
					.expirationTime(Date.from(issuedAt.plus(ttl)))
					.build();
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(key.getKeyID()).build(), claims);
			jwt.sign(new Ed25519Signer(key));
			return jwt.serialize();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static OctetKeyPair newKey(String kid) {
		try {
			return new OctetKeyPairGenerator(Curve.Ed25519).keyID(kid).generate();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static String sha256Hex(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
