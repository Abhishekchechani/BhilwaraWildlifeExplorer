package in.gov.bhilwara.wildlife;

import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves public Google Drive download/virus-scan confirmation pages to a media URL. */
public final class DriveVideoResolver {
    private static final String TAG = "DriveVideoResolver";
    private static final int MAX_REDIRECTS = 12;
    private static final int MAX_HTML_BYTES = 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36";

    private DriveVideoResolver() {}

    public static final class ResolvedVideo {
        public final String url;
        public final String cookieHeader;
        public final String contentType;

        ResolvedVideo(String url, String cookieHeader, String contentType) {
            this.url = url;
            this.cookieHeader = cookieHeader;
            this.contentType = contentType;
        }
    }

    public static ResolvedVideo resolve(String input) throws IOException {
        String fileId = extractFileId(input);
        if (fileId == null) throw new IOException("Could not find a Google Drive file ID in the video URL.");

        String current = "https://drive.google.com/uc?export=download&id=" + Uri.encode(fileId);
        Map<String, String> cookies = new LinkedHashMap<>();
        String lastType = "";

        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "video/*,application/octet-stream,*/*;q=0.8");
            connection.setRequestProperty("Range", "bytes=0-0");
            String cookieHeader = makeCookieHeader(cookies);
            if (!cookieHeader.isEmpty()) connection.setRequestProperty("Cookie", cookieHeader);

            int status;
            try {
                status = connection.getResponseCode();
                saveCookies(connection, cookies);
                lastType = connection.getContentType() == null ? "" : connection.getContentType();

                if (status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (location == null || location.trim().isEmpty()) throw new IOException("Google Drive returned a redirect without a destination.");
                    current = new URL(new URL(current), location).toString();
                    continue;
                }

                if (status < 200 || status >= 300) {
                    String error = readSmallBody(connection.getErrorStream());
                    connection.disconnect();
                    throw new IOException("Google Drive returned HTTP " + status + (error.isEmpty() ? "" : ": " + error));
                }

                if (lastType.toLowerCase().contains("text/html") || lastType.toLowerCase().contains("application/xhtml")) {
                    String html = readSmallBody(connection.getInputStream());
                    connection.disconnect();
                    String token = extractConfirmToken(html, cookies);
                    if (token == null || token.isEmpty()) {
                        throw new IOException("Google Drive returned an HTML page instead of video data. Check that the file is public and downloadable; the page did not contain a supported confirmation token.");
                    }
                    current = addQueryParameter(current, "confirm", token);
                    continue;
                }

                connection.disconnect();
                String finalCookies = makeCookieHeader(cookies);
                return new ResolvedVideo(current, finalCookies, lastType);
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
        }
        throw new IOException("Google Drive download could not be resolved after multiple redirects/confirmation steps. Last content type: " + lastType);
    }

    private static String extractFileId(String input) {
        if (input == null) return null;
        Uri uri = Uri.parse(input.trim());
        String id = uri.getQueryParameter("id");
        if (id != null && !id.trim().isEmpty()) return id.trim();
        Matcher matcher = Pattern.compile("/file/d/([A-Za-z0-9_-]+)").matcher(input);
        if (matcher.find()) return matcher.group(1);
        matcher = Pattern.compile("([A-Za-z0-9_-]{20,})").matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void saveCookies(HttpURLConnection connection, Map<String, String> cookies) {
        for (Map.Entry<String, java.util.List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                for (String cookie : entry.getValue()) {
                    String pair = cookie.split(";", 2)[0];
                    int equals = pair.indexOf('=');
                    if (equals > 0) cookies.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
                }
            }
        }
    }

    private static String makeCookieHeader(Map<String, String> cookies) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (result.length() > 0) result.append("; ");
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    private static String extractConfirmToken(String html, Map<String, String> cookies) {
        String fromCookie = null;
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getKey().startsWith("download_warning")) { fromCookie = entry.getValue(); break; }
        }
        if (fromCookie != null && !fromCookie.isEmpty()) return fromCookie;

        Pattern[] patterns = new Pattern[] {
            Pattern.compile("name=[\\\"']confirm[\\\"'][^>]*value=[\\\"']([^\\\"']+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("value=[\\\"']([^\\\"']+)[\\\"'][^>]*name=[\\\"']confirm[\\\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[?&]confirm=([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }

    private static String addQueryParameter(String url, String name, String value) {
        Uri parsed = Uri.parse(url);
        Uri.Builder builder = parsed.buildUpon().clearQuery();
        for (String key : parsed.getQueryParameterNames()) {
            if (!key.equals(name)) {
                for (String oldValue : parsed.getQueryParameters(key)) builder.appendQueryParameter(key, oldValue);
            }
        }
        builder.appendQueryParameter(name, value);
        return builder.build().toString();
    }

    private static String readSmallBody(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0, count;
            while ((count = in.read(buffer)) != -1 && total < MAX_HTML_BYTES) {
                int keep = Math.min(count, MAX_HTML_BYTES - total);
                out.write(buffer, 0, keep);
                total += keep;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
