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
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import java.util.Arrays;
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
    private FrameLayout rootContainer;
    private PlayerView journeyPreviewView;
    private View journeyPreviewTapLayer;
    private ExoPlayer journeyPreviewPlayer;
    private File journeyPreviewFile;
    private volatile boolean launchingMediaActivity = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        FrameLayout root = new FrameLayout(this);
        rootContainer = root;
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALL_FILES) {
            updateStorageStatus();
            if (webView != null) {
                webView.evaluateJavascript(
                        "if(window.refreshHomeMedia){window.refreshHomeMedia();}", null);
            }
        }
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
        return n.contains("bhilwarawildlifejourney") || n.contains("30minutewildlifejourney") || n.contains("iconicfloraandfaunaofbhilwara") || n.contains("featured") || n.contains("homevideo");
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
        File root = rootFolder();
        if (root == null || title == null || title.trim().isEmpty()) return null;

        // Check the expected image folder first, but support common folder-name variations.
        String[] imageFolderNames = new String[] {
                "30 front images", "30 front image", "30_front_images", "images",
                "photos", "photo gallery", "gallery", "wildlife images"
        };
        for (String folderName : imageFolderNames) {
            File folder = new File(root, folderName);
            if (folder.isDirectory() && folder.canRead()) {
                File match = findBestImageMatch(folder, title);
                if (match != null) return match;
            }
        }
        // Some installations keep photos directly in the main media folder or nested elsewhere.
        return findBestImageMatch(root, title);
    }

    private File findBestImageMatch(File folder, String title) {
        java.util.ArrayList<File> files = new java.util.ArrayList<>();
        collectImageFiles(folder, files, 0);
        String wanted = normalize(title);
        if (wanted.isEmpty()) return null;
        File best = null;
        int bestScore = 0;
        for (File file : files) {
            String base = file.getName().replaceFirst("(?i)\\.[^.]+$", "")
                    .replaceFirst("^[0-9]+[\\s._-]*", "");
            String candidate = normalize(base);
            if (candidate.isEmpty()) continue;
            int score = 0;
            if (candidate.equals(wanted)) score = 1000;
            else if (candidate.contains(wanted) || wanted.contains(candidate)) score = 700 + Math.min(candidate.length(), wanted.length());
            else {
                java.util.Set<String> wantedTokens = new java.util.HashSet<>();
                for (String token : title.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (token.length() > 2) wantedTokens.add(token);
                java.util.Set<String> candidateTokens = new java.util.HashSet<>();
                for (String token : base.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (token.length() > 2) candidateTokens.add(token);
                int overlap = 0;
                for (String token : wantedTokens) if (candidateTokens.contains(token)) overlap++;
                if (overlap > 0) score = 100 * overlap / Math.max(1, wantedTokens.size());
            }
            if (score > bestScore) { bestScore = score; best = file; }
        }
        return bestScore >= 50 ? best : null;
    }

    private void collectImageFiles(File folder, java.util.List<File> output, int depth) {
        if (folder == null || depth > 8) return;
        File[] children = folder.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (child.isDirectory()) collectImageFiles(child, output, depth + 1);
            else {
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                        name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".gif")) output.add(child);
            }
        }
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
        @JavascriptInterface public void playFeaturedJourney() {
            try {
                JSONArray one = new JSONArray();
                JSONObject item = new JSONObject();
                item.put("title", "Bhilwara Wildlife Journey");
                item.put("key", "local");
                item.put("localOnly", true);
                one.put(item);
                playNativeVideoPlaylist(one.toString(), 0, "Bhilwara Wildlife Journey");
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Unable to open the local journey video.", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface public void startJourneyPreview() {
            runOnUiThread(() -> ensureJourneyPreview());
        }

        @JavascriptInterface public void updateJourneyPreviewBounds(
                float left, float top, float width, float height) {
            runOnUiThread(() -> positionJourneyPreview(left, top, width, height));
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
            if (playlistJson == null || playlistJson.trim().isEmpty() || launchingMediaActivity) return;
            launchingMediaActivity = true;
            new Thread(() -> {
                String selectedTitle = videoTitle == null ? "Wildlife video" : videoTitle;
                File local = null;
                try {
                    JSONArray input = new JSONArray(playlistJson);
                    if (input.length() == 0) throw new Exception("No video was selected.");
                    int safeIndex = Math.max(0, Math.min(startIndex, input.length() - 1));
                    JSONObject selected = input.getJSONObject(safeIndex);
                    selectedTitle = selected.optString("title", selectedTitle);
                    // Ignore every URL or Drive ID. The app only plays files found in local storage.
                    local = findVideo(selectedTitle);
                    if (local == null || !local.isFile() || !local.canRead()) {
                        throw new Exception("Local video not found: " + selectedTitle);
                    }
                    JSONArray resolvedPlaylist = new JSONArray();
                    int resolvedStart = 0;
                    for (int i = 0; i < input.length(); i++) {
                        JSONObject sourceItem = input.getJSONObject(i);
                        String itemTitle = sourceItem.optString("title", "Wildlife video");
                        File videoFile = (i == safeIndex) ? local : findVideo(itemTitle);
                        if (videoFile != null && videoFile.isFile() && videoFile.canRead()) {
                            JSONObject resolvedItem = new JSONObject();
                            resolvedItem.put("title", itemTitle);
                            resolvedItem.put("source", Uri.fromFile(videoFile).toString());
                            if (i < safeIndex) resolvedStart++;
                            resolvedPlaylist.put(resolvedItem);
                        }
                    }
                    if (resolvedPlaylist.length() == 0) throw new Exception("No local videos are available.");
                    final String finalTitle = selectedTitle;
                    final String finalPlaylist = resolvedPlaylist.toString();
                    final int finalStart = Math.min(resolvedStart, resolvedPlaylist.length() - 1);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) { launchingMediaActivity = false; return; }
                        try {
                            Intent intent = new Intent(MainActivity.this, NativeVideoActivity.class);
                            intent.putExtra("VIDEO_PLAYLIST_JSON", finalPlaylist);
                            intent.putExtra("VIDEO_START_INDEX", finalStart);
                            intent.putExtra("VIDEO_TITLE", finalTitle);
                            startActivity(intent);
                        } catch (Exception e) {
                            launchingMediaActivity = false;
                            Toast.makeText(MainActivity.this, "Unable to open local video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "Local video is unavailable." : e.getMessage();
                    runOnUiThread(() -> {
                        launchingMediaActivity = false;
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            }, "local-video-lookup").start();
        }

    }

    public class ImageBridge {
        @JavascriptInterface public String getLocalImageUri(String imageTitle) {
            File local = findLocalImage(imageTitle);
            return local != null && local.isFile() && local.canRead() ? Uri.fromFile(local).toString() : "";
        }

        @JavascriptInterface public String getHomeSlideshowImages() {
            JSONArray images = new JSONArray();
            File root = rootFolder();
            if (root == null) return images.toString();
            File folder = new File(root, "30 front images");
            if (!folder.isDirectory() || !folder.canRead()) return images.toString();
            collectSlideshowImages(folder, images);
            return images.toString();
        }

        @JavascriptInterface public void openNativeImage(String imageUrl, String imageTitle) {
            JSONArray a = new JSONArray();
            try { JSONObject o = new JSONObject(); o.put("title", imageTitle); o.put("url", imageUrl); a.put(o); }
            catch (Exception ignored) {}
            openNativeImageGallery(a.toString(), 0);
        }
        @JavascriptInterface public void openNativeImageGallery(String imagesJson, int startIndex) {
            if (launchingMediaActivity) return;
            launchingMediaActivity = true;
            new Thread(() -> {
                try {
                    JSONArray input = new JSONArray(imagesJson);
                    JSONArray resolved = new JSONArray();
                    java.util.ArrayList<File> orderedLocalImages = new java.util.ArrayList<>();
                    File root = rootFolder();
                    if (root != null) {
                        File imageFolder = new File(root, "30 front images");
                        if (imageFolder.isDirectory()) collectImageFiles(imageFolder, orderedLocalImages, 0);
                        if (orderedLocalImages.isEmpty()) collectImageFiles(root, orderedLocalImages, 0);
                        java.util.Collections.sort(orderedLocalImages, (a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
                    }
                    for (int i = 0; i < input.length(); i++) {
                        JSONObject item = input.getJSONObject(i);
                        String title = item.optString("title", "Photo " + (i + 1));
                        File local = findLocalImage(title);
                        // Many panel folders use numbered or generic filenames. In that case,
                        // preserve gallery order and use the matching local image position.
                        if ((local == null || !local.isFile() || !local.canRead()) && i < orderedLocalImages.size()) {
                            local = orderedLocalImages.get(i);
                        }
                        if (local == null || !local.isFile() || !local.canRead()) {
                            throw new Exception("Local photo not found: " + title + ". Check the 30 front images folder.");
                        }
                        JSONObject out = new JSONObject();
                        out.put("title", title);
                        out.put("source", local.getAbsolutePath());
                        out.put("local", true);
                        resolved.put(out);
                    }
                    int selected = Math.max(0, Math.min(startIndex, Math.max(0, resolved.length() - 1)));
                    runOnUiThread(() -> {
                        try {
                            Intent intent = new Intent(MainActivity.this, NativeImageActivity.class);
                            intent.putExtra("IMAGE_ITEMS_JSON", resolved.toString());
                            intent.putExtra("IMAGE_START_INDEX", selected);
                            startActivity(intent);
                        } catch (Exception e) {
                            launchingMediaActivity = false;
                            Toast.makeText(MainActivity.this, "Unable to open local photo gallery.", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    String message = e.getMessage() == null ? "Local photos are unavailable." : e.getMessage();
                    runOnUiThread(() -> {
                        launchingMediaActivity = false;
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            }, "local-image-lookup").start();
        }

    }

    private void collectSlideshowImages(File folder, JSONArray output) {
        File[] children = folder.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (child.isDirectory()) {
                collectSlideshowImages(child, output);
            } else {
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".webp")) {
                    output.put(Uri.fromFile(child).toString());
                }
            }
        }
    }

    private void ensureJourneyPreview() {
        if (rootContainer == null || webView == null) return;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            updateStorageStatus();
            return;
        }
        File video = findVideo("Bhilwara Wildlife Journey");
        if (video == null || !video.isFile()) {
            updateStorageStatus();
            return;
        }
        journeyPreviewFile = video;
        if (journeyPreviewView != null && journeyPreviewPlayer != null) {
            journeyPreviewView.setVisibility(View.VISIBLE);
            return;
        }
        try {
            journeyPreviewPlayer = new ExoPlayer.Builder(this).build();
            journeyPreviewView = new PlayerView(this);
            journeyPreviewView.setUseController(false);
            journeyPreviewView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            journeyPreviewView.setShutterBackgroundColor(Color.BLACK);
            journeyPreviewView.setBackgroundColor(Color.BLACK);
            journeyPreviewView.setPlayer(journeyPreviewPlayer);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            rootContainer.addView(journeyPreviewView, params);

            // A transparent native tap layer sits above the video surface so taps
            // reliably open the full-screen native player on older Android panels.
            journeyPreviewTapLayer = new View(this);
            journeyPreviewTapLayer.setBackgroundColor(Color.TRANSPARENT);
            journeyPreviewTapLayer.setOnClickListener(v -> {
                if (launchingMediaActivity) return;
                // Release the small preview player before opening the same file full-screen.
                // Re-create it after the full-screen player is closed to avoid concurrent reads.
                releaseJourneyPreview();
                new VideoBridge().playFeaturedJourney();
            });
            rootContainer.addView(journeyPreviewTapLayer, new FrameLayout.LayoutParams(1, 1));
            if (storageStatus != null) storageStatus.bringToFront();

            journeyPreviewPlayer.setVolume(0f);
            journeyPreviewPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            journeyPreviewPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)));
            journeyPreviewPlayer.prepare();
            journeyPreviewPlayer.play();
            updateStorageStatus();
        } catch (Exception e) {
            if (journeyPreviewView != null) {
                rootContainer.removeView(journeyPreviewView);
                journeyPreviewView = null;
            }
            if (journeyPreviewPlayer != null) {
                journeyPreviewPlayer.release();
                journeyPreviewPlayer = null;
            }
            Toast.makeText(this, "Journey preview could not start: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void positionJourneyPreview(float cssLeft, float cssTop, float cssWidth, float cssHeight) {
        if (journeyPreviewView == null || webView == null || rootContainer == null) return;
        if (cssWidth <= 1 || cssHeight <= 1) {
            journeyPreviewView.setVisibility(View.GONE);
            if (journeyPreviewTapLayer != null) journeyPreviewTapLayer.setVisibility(View.GONE);
            return;
        }
        int[] webLocation = new int[2];
        int[] rootLocation = new int[2];
        webView.getLocationOnScreen(webLocation);
        rootContainer.getLocationOnScreen(rootLocation);
        float scale = webView.getScale();
        int left = Math.round(webLocation[0] - rootLocation[0] + cssLeft * scale);
        int top = Math.round(webLocation[1] - rootLocation[1] + cssTop * scale);
        int width = Math.max(1, Math.round(cssWidth * scale));
        int height = Math.max(1, Math.round(cssHeight * scale));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) journeyPreviewView.getLayoutParams();
        if (params == null) params = new FrameLayout.LayoutParams(width, height);
        params.width = width;
        params.height = height;
        params.leftMargin = left;
        params.topMargin = top;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        journeyPreviewView.setLayoutParams(params);
        journeyPreviewView.setVisibility(View.VISIBLE);
        journeyPreviewView.bringToFront();
        if (journeyPreviewTapLayer != null) {
            FrameLayout.LayoutParams tapParams = new FrameLayout.LayoutParams(width, height);
            tapParams.leftMargin = left;
            tapParams.topMargin = top;
            tapParams.gravity = Gravity.TOP | Gravity.LEFT;
            journeyPreviewTapLayer.setLayoutParams(tapParams);
            journeyPreviewTapLayer.setVisibility(View.VISIBLE);
            journeyPreviewTapLayer.bringToFront();
        }
        if (storageStatus != null) storageStatus.bringToFront();
    }

    private void releaseJourneyPreview() {
        if (journeyPreviewPlayer != null) {
            journeyPreviewPlayer.release();
            journeyPreviewPlayer = null;
        }
        if (journeyPreviewView != null && rootContainer != null) {
            rootContainer.removeView(journeyPreviewView);
            journeyPreviewView = null;
        }
        if (journeyPreviewTapLayer != null && rootContainer != null) {
            rootContainer.removeView(journeyPreviewTapLayer);
            journeyPreviewTapLayer = null;
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override protected void onPause() {
        if (journeyPreviewPlayer != null) journeyPreviewPlayer.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        launchingMediaActivity = false;
        updateStorageStatus();
        // Rebuild the preview player after returning from the full-screen player.
        if (journeyPreviewFile != null && journeyPreviewFile.isFile()) {
            ensureJourneyPreview();
            if (webView != null) webView.post(() -> webView.evaluateJavascript(
                    "if(window.refreshHomeMedia){window.refreshHomeMedia();}", null));
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript(
                "(function(){return window.handleAndroidBack ? window.handleAndroidBack() : false;})()",
                value -> { if ("false".equals(value) || value == null) MainActivity.super.onBackPressed(); }
        );
    }
    @Override protected void onDestroy() {
        releaseJourneyPreview();
        if (webView != null) { webView.loadUrl("about:blank"); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
