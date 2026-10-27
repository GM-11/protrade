package protrader.funds.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tiny HTTP/1.1 client for the integration tests: nothing between the test and the wire. */
public final class Http {

	public record Response(int status, String body) {

		public JsonNode json() {
			return MAPPER.readTree(body);
		}

		public String error() {
			return json().get("error").asString();
		}

		public String field() {
			JsonNode f = json().get("field");
			return f == null || f.isNull() ? null : f.asString();
		}
	}

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	private final String base;
	private final Map<String, String> auth;

	public Http(String base, Map<String, String> auth) {
		this.base = base;
		this.auth = auth;
	}

	public Response get(String path) {
		return send("GET", path, null, null);
	}

	public Response post(String path, String json) {
		return send("POST", path, "application/json", json);
	}

	public Response send(String method, String path, String contentType, String body) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		if (contentType != null) {
			b.header("Content-Type", contentType);
		}
		auth.forEach(b::header);
		try {
			HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
			return new Response(r.statusCode(), r.body());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
