package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.view.Gravity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.Normalizer;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_ALL_FILES = 7102;
    private static final String ROOT_PATH = "/storage/emulated/0/bhilwara_forest_department";
    private WebView webView;
    private TextView storageStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        storageStatus = new TextView(this);
        storageStatus.setTextColor(Color.WHITE);
        storageStatus.setTextSize(10);
        storageStatus.setGravity(Gravity.CENTER);
        storageStatus.setPadding(10, 5, 10, 5);
        storageStatus.setBackgroundColor(0x990B3024);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        statusParams.bottomMargin = dp(8);
        root.addView(storageStatus, statusParams);
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true); s.setSupportZoom(false);
        webView.setWebChromeClient(new WebChromeClient()); webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new VideoBridge(), "AndroidVideo");
        webView.addJavascriptInterface(new ImageBridge(), "AndroidImage");
        webView.setBackgroundColor(0xFF04251D);
        webView.loadUrl("file:///android_asset/index.html");
        checkStorageAccess();
        updateStorageStatus();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private void checkStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQUEST_ALL_FILES);
            } catch (Exception e) {
                try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_ALL_FILES); }
                catch (Exception ignored) { Toast.makeText(this, "Please enable All files access for Bhilwara Wildlife Explorer in Android Settings.", Toast.LENGTH_LONG).show(); }
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        updateStorageStatus();
        if (webView != null) {
            webView.postDelayed(() -> webView.evaluateJavascript("if (typeof initializeJourneyPreview === 'function') initializeJourneyPreview();", null), 350);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALL_FILES) updateStorageStatus();
    }

    private File rootFolder() {
        String[] candidates = new String[] {
                ROOT_PATH,
                "/sdcard/bhilwara_forest_department",
                "/storage/self/primary/bhilwara_forest_department",
                new File(Environment.getExternalStorageDirectory(), "bhilwara_forest_department").getAbsolutePath()
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.isDirectory() && f.canRead()) return f;
        }
        return null;
    }

    private void updateStorageStatus() {
        if (storageStatus == null) return;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            storageStatus.setText("Allow file access to enable local media");
        } else if (rootFolder() == null) {
            storageStatus.setText("Local media folder not found: bhilwara_forest_department");
        } else {
            storageStatus.setText("Local media connected");
        }
    }

    private File findVideo(String title) {
        File root = rootFolder(); if (root == null) return null;
        if (isFeatured(title)) {
            File featured = findByName(root, "Iconic_Flora_and_Fauna_of_Bhilwara_with_credit", true);
            if (featured != null) return featured;
        }
        return findByName(root, normalize(title), false);
    }

    private boolean isFeatured(String title) {
        String n = normalize(title);
        return n.contains("bhilwarawildlifejourney") || n.contains("30minutewildlifejourney") || n.contains("iconicfloraandfaunaofbhilwara") || n.contains("featured") || n.contains("homevideo") || n.equals("30");
    }

    private File findByName(File dir, String expected, boolean recursive) {
        File[] children = dir.listFiles(); if (children == null) return null;
        for (File f : children) {
            if (f.isDirectory()) {
                File found = findByName(f, expected, true); if (found != null) return found;
            } else if (isImageOrVideo(f.getName())) {
                String base = f.getName().replaceFirst("(?i)\\.[^.]+$", "");
                if (normalize(base).equals(normalize(expected))) return f;
            }
        }
        return null;
    }

    private File findLocalImage(String title) {
        File root = rootFolder(); if (root == null) return null;
        File images = new File(root, "30 front images");
        if (!images.isDirectory()) return null;
        return findByName(images, normalize(title), true);
    }

    private boolean isImageOrVideo(String n) {
        String x = n.toLowerCase(Locale.ROOT);
        return x.endsWith(".mp4") || x.endsWith(".m4v") || x.endsWith(".mov") || x.endsWith(".mkv") || x.endsWith(".webm") || x.endsWith(".3gp") || x.endsWith(".jpg") || x.endsWith(".jpeg") || x.endsWith(".png") || x.endsWith(".webp");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public class VideoBridge {
        // Returns a file:// URI for a local video so the home-screen preview can autoplay silently.
        // An empty string means the configured local file is not currently accessible.
        @JavascriptInterface public String getLocalVideoUrl(String videoTitle) {
            File local = findVideo(videoTitle);
            if (local != null && local.isFile() && local.canRead()) {
                return Uri.fromFile(local).toString();
            }
            return "";
        }

        @JavascriptInterface public void playNativeVideo(String videoKeyOrUrl, String videoTitle) {
            JSONArray one = new JSONArray();
            try {
                JSONObject item = new JSONObject();
                item.put("title", videoTitle == null ? "Wildlife video" : videoTitle);
                item.put("key", videoKeyOrUrl == null ? "" : videoKeyOrUrl);
                one.put(item);
            } catch (Exception ignored) { }
            playNativeVideoPlaylist(one.toString(), 0, videoTitle);
        }

        @JavascriptInterface public void playNativeVideoPlaylist(String playlistJson, int startIndex, String videoTitle) {
            if (playlistJson == null || playlistJson.trim().isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    JSONArray input = new JSONArray(playlistJson);
                    JSONArray resolved = new JSONArray();
                    int resolvedStartIndex = -1;
                    boolean selectedLocalVideoMissing = false;
                    for (int i = 0; i < input.length(); i++) {
                        JSONObject source = input.getJSONObject(i);
                        String title = source.optString("title", "Wildlife video");
                        String key = source.optString("key", "");
                        boolean localOnly = source.optBoolean("localOnly", false);
                        JSONObject item = new JSONObject();
                        item.put("title", title);

                        if (key.startsWith("http://") || key.startsWith("https://")) {
                            item.put("url", key);
                        } else {
                            File local = findVideo(title);
                            if (local != null && local.isFile()) {
                                if (i == startIndex) resolvedStartIndex = resolved.length();
                                item.put("path", local.getAbsolutePath());
                            } else if (localOnly) {
                                if (i == startIndex) selectedLocalVideoMissing = true;
                                // A local-only category item must never be sent to Drive with a title as its ID.
                                continue;
                            } else {
                                if (i == startIndex) resolvedStartIndex = resolved.length();
                                item.put("url", "https://drive.google.com/uc?export=download&id=" + Uri.encode(key));
                            }
                        }
                        resolved.put(item);
                    }
                    if (selectedLocalVideoMissing) {
                        String missingTitle = videoTitle == null ? "selected category video" : videoTitle;
                        Toast.makeText(MainActivity.this, "Local video not found: " + missingTitle, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (resolved.length() == 0) {
                        Toast.makeText(MainActivity.this, "No local videos found in bhilwara_forest_department.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (resolvedStartIndex < 0) resolvedStartIndex = 0;
                    Intent intent = new Intent(MainActivity.this, NativeVideoActivity.class);
                    intent.putExtra("VIDEO_PLAYLIST_JSON", resolved.toString());
                    intent.putExtra("VIDEO_START_INDEX", Math.max(0, Math.min(resolvedStartIndex, resolved.length() - 1)));
                    intent.putExtra("VIDEO_TITLE", videoTitle == null ? "Wildlife video" : videoTitle);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to prepare video playlist.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    public class ImageBridge {
        @JavascriptInterface public void openNativeImage(String imageUrl, String imageTitle) {
            JSONArray a = new JSONArray();
            try { JSONObject o = new JSONObject(); o.put("title", imageTitle); o.put("url", imageUrl); a.put(o); }
            catch (Exception ignored) {}
            openNativeImageGallery(a.toString(), 0);
        }
        @JavascriptInterface public void openNativeImageGallery(String imagesJson, int startIndex) {
            runOnUiThread(() -> {
                try {
                    JSONArray input = new JSONArray(imagesJson);
                    JSONArray resolved = new JSONArray();
                    for (int i = 0; i < input.length(); i++) {
                        JSONObject item = input.getJSONObject(i);
                        String title = item.optString("title", "Photo " + (i + 1));
                        String url = item.optString("url", "");
                        File local = findLocalImage(title);
                        JSONObject out = new JSONObject();
                        out.put("title", title);
                        out.put("source", local != null ? local.getAbsolutePath() : url);
                        out.put("local", local != null);
                        resolved.put(out);
                    }
                    Intent intent = new Intent(MainActivity.this, NativeImageActivity.class);
                    intent.putExtra("IMAGE_ITEMS_JSON", resolved.toString());
                    intent.putExtra("IMAGE_START_INDEX", startIndex);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to open photo gallery.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (webView != null) { webView.loadUrl("about:blank"); webView.destroy(); webView = null; } super.onDestroy(); }
}
