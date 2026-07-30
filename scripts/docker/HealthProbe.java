import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public final class HealthProbe {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private HealthProbe() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: HealthProbe URL [timeoutMillis] [expectedStatus|-]");
            System.exit(2);
        }

        String url = args[0];
        int timeoutMillis = args.length >= 2 ? parseTimeout(args[1]) : 3000;
        String expectedStatus = args.length >= 3 ? args[2] : "UP";

        try {
            URI uri = URI.create(url);
            validateLoopbackHttp(uri);

            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                System.err.printf("Health endpoint returned HTTP %d%n", statusCode);
                System.exit(1);
            }

            if (!"-".equals(expectedStatus)) {
                try (InputStream input = connection.getInputStream()) {
                    String body = new String(
                            input.readNBytes(MAX_BODY_BYTES),
                            StandardCharsets.UTF_8
                    );
                    String normalizedBody = body.replaceAll("\\s+", "");
                    String statusMarker = (
                            "\"status\":\"" + expectedStatus + "\""
                    ).toUpperCase(Locale.ROOT);
                    if (!normalizedBody.toUpperCase(Locale.ROOT).contains(statusMarker)) {
                        System.err.printf(
                                "Health endpoint did not report status %s%n",
                                expectedStatus
                        );
                        System.exit(1);
                    }
                }
            }

            connection.disconnect();
        } catch (Exception exception) {
            System.err.println("Health probe failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static int parseTimeout(String rawValue) {
        int timeoutMillis;
        try {
            timeoutMillis = Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("timeoutMillis must be an integer");
        }
        if (timeoutMillis < 100 || timeoutMillis > Duration.ofMinutes(1).toMillis()) {
            throw new IllegalArgumentException(
                    "timeoutMillis must be between 100 and 60000"
            );
        }
        return timeoutMillis;
    }

    private static void validateLoopbackHttp(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("health URL must use http");
        }
        String host = uri.getHost();
        if (!"127.0.0.1".equals(host)
                && !"localhost".equalsIgnoreCase(host)
                && !"::1".equals(host)) {
            throw new IllegalArgumentException("health URL must target loopback");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("health URL contains unsupported fields");
        }
    }
}
