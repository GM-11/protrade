package protrader.funds.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Reads a small document from an http(s) URL, a file: URL or a plain file path (like the gateway's fetchURL). */
final class SourceReader {

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private SourceReader() {
	}

	/** Returns the body, or null on HTTP 304 (not modified). Throws on anything else that isn't 200. */
	static byte[] read(String source, int maxBytes) throws IOException {
		if (source.startsWith("http://") || source.startsWith("https://")) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(source)).timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<InputStream> response;
			try {
				response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted", e);
			}
			try (InputStream body = response.body()) {
				if (response.statusCode() == 304) {
					return null;
				}
				if (response.statusCode() != 200) {
					throw new IOException("unexpected HTTP status " + response.statusCode() + " from " + source);
				}
				return capped(body, maxBytes);
			}
		}
		Path path = source.startsWith("file:") ? Path.of(URI.create(source)) : Path.of(source);
		try (InputStream in = Files.newInputStream(path)) {
			return capped(in, maxBytes);
		}
	}

	private static byte[] capped(InputStream in, int maxBytes) throws IOException {
		byte[] data = in.readNBytes(maxBytes + 1);
		if (data.length > maxBytes) {
			throw new IOException("document larger than " + maxBytes + " bytes");
		}
		return data;
	}
}
