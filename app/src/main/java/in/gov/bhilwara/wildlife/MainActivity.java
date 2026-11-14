package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Uses the user's existing /storage/emulated/0/bhilwara_forest_department folder.
 * The user selects that folder once through Android's Storage Access Framework.
 * Android then grants this app persistent read access without broad storage permissions.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_SELECT_WILDLIFE_FOLDER = 7101;
    private static final String PREFS = "wildlife_local_storage";
    private static final String PREF_TREE_URI = "wildlife_tree_uri";
    private static final String EXPECTED_FOLDER = "bhilwara_forest_department";

    private WebView webView;
    private TextView storageStatus;
    private Button selectFolderButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        selectFolderButton = new Button(this);
        selectFolderButton.setText("Select local video folder");
        selectFolderButton.setAllCaps(false);
        selectFolderButton.setTextSize(12);
        selectFolderButton.setOnClickListener(v -> chooseWildlifeFolder());
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                dp(205), dp(50), Gravity.BOTTOM | Gravity.END);
        buttonParams.setMargins(dp(12), dp(12), dp(18), dp(78));
        root.addView(selectFolderButton, buttonParams);

        storageStatus = new TextView(this);
        storageStatus.setTextColor(0xFFFFFFFF);
        storageStatus.setTextSize(10);
        storageStatus.setGravity(Gravity.CENTER);
        storageStatus.setPadding(dp(8), dp(4), dp(8), dp(4));
        storageStatus.setBackgroundColor(0xAA102A20);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                dp(240), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        statusParams.setMargins(dp(12), dp(12), dp(18), dp(132));
        root.addView(storageStatus, statusParams);
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.addJavascriptInterface(new VideoBridge(), "AndroidVideo");
        webView.addJavascriptInterface(new ImageBridge(), "AndroidImage");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.setBackgroundColor(0xFF04251D);
        webView.loadUrl("file:///android_asset/index.html");
        updateStorageStatus();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void chooseWildlifeFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_SELECT_WILDLIFE_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SELECT_WILDLIFE_FOLDER || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri treeUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "Could not keep folder permission. Please select it again.", Toast.LENGTH_LONG).show();
            return;
        }

        DocumentFile folder = DocumentFile.fromTreeUri(this, treeUri);
        if (folder == null || !folder.isDirectory()) {
            Toast.makeText(this, "Please select the bhilwara_forest_department folder.", Toast.LENGTH_LONG).show();
            return;
        }
        String folderName = folder.getName();
        if (folderName == null || !EXPECTED_FOLDER.equalsIgnoreCase(folderName.trim())) {
            Toast.makeText(this,
                    "Please select the folder named bhilwara_forest_department (not an individual animal folder).",
                    Toast.LENGTH_LONG).show();
            return;
        }

        getPreferences(MODE_PRIVATE).edit().putString(PREF_TREE_URI, treeUri.toString()).apply();
        updateStorageStatus();
        Toast.makeText(this, "Wildlife folder connected. Videos will play from local storage.", Toast.LENGTH_LONG).show();
    }

    private Uri getSavedTreeUri() {
        String saved = getPreferences(MODE_PRIVATE).getString(PREF_TREE_URI, null);
        if (saved == null || saved.isEmpty()) return null;
        return Uri.parse(saved);
    }

    private DocumentFile getSavedRoot() {
        Uri tree = getSavedTreeUri();
        if (tree == null) return null;
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, tree);
            return root != null && root.canRead() ? root : null;
        } catch (SecurityException e) {
            return null;
        }
    }

    private void updateStorageStatus() {
        if (storageStatus == null) return;
        DocumentFile root = getSavedRoot();
        if (root == null) {
            storageStatus.setText("Local folder not selected");
        } else {
            storageStatus.setText("Local folder connected: " + root.getName());
        }
    }

    private DocumentFile findLocalVideo(String title) {
        DocumentFile root = getSavedRoot();
        if (root == null) return null;

        // The 30-minute featured home-screen film has this exact local filename.
        if (isFeaturedTitle(title)) {
            DocumentFile featured = findByExactBaseName(root, "Iconic_Flora_and_Fauna_of_Bhilwara_with_credit");
            if (featured != null) return featured;
        }

        String titleKey = normalize(title);
        if (titleKey.isEmpty()) return null;
        return findByNormalizedBaseName(root, titleKey);
    }

    private boolean isFeaturedTitle(String title) {
        String key = normalize(title);
        return key.contains("bhilwarawildlifejourney")
                || key.contains("iconicfloraandfaunaofbhilwara")
                || key.contains("featured")
                || key.contains("homevideo");
    }

    private DocumentFile findByExactBaseName(DocumentFile folder, String expectedBase) {
        String expected = normalize(expectedBase);
        DocumentFile[] children = folder.listFiles();
        if (children == null) return null;
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                DocumentFile found = findByExactBaseName(child, expectedBase);
                if (found != null) return found;
            } else if (isVideo(child.getName()) && normalize(removeExtension(child.getName())).equals(expected)) {
                return child;
            }
        }
        return null;
    }

    private DocumentFile findByNormalizedBaseName(DocumentFile folder, String expected) {
        DocumentFile[] children = folder.listFiles();
        if (children == null) return null;
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                DocumentFile found = findByNormalizedBaseName(child, expected);
                if (found != null) return found;
            } else if (isVideo(child.getName())
                    && normalize(removeExtension(child.getName())).equals(expected)) {
                return child;
            }
        }
        return null;
    }

    private boolean isVideo(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".mov")
                || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".3gp");
    }

    private String removeExtension(String name) {
        return name == null ? "" : name.replaceFirst("(?i)\\.[^.]+$", "");
    }

    private String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        return decomposed.replaceAll("[^a-z0-9]", "");
    }

    public class VideoBridge {
        @JavascriptInterface public void selectLocalFolder() {
            runOnUiThread(() -> chooseWildlifeFolder());
        }

        @JavascriptInterface public void playNativeVideo(String videoKeyOrUrl, String videoTitle) {
            if (videoKeyOrUrl == null || videoKeyOrUrl.trim().isEmpty()) return;
            String key = videoKeyOrUrl.trim();
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, NativeVideoActivity.class);
                intent.putExtra("VIDEO_TITLE", videoTitle == null ? "Wildlife video" : videoTitle);

                // Sample/test or other direct URL: preserve normal URL handling.
                if (key.startsWith("http://") || key.startsWith("https://")) {
                    intent.putExtra("VIDEO_URL", key);
                } else {
                    DocumentFile local = findLocalVideo(videoTitle);
                    if (local != null && local.isFile()) {
                        intent.putExtra("VIDEO_CONTENT_URI", local.getUri().toString());
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        // Drive fallback is retained only when the local file is absent.
                        intent.putExtra("VIDEO_URL",
                                "https://drive.google.com/uc?export=download&id=" + Uri.encode(key));
                        if (getSavedRoot() == null) {
                            Toast.makeText(MainActivity.this,
                                    "Select the bhilwara_forest_department folder to enable local playback.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Local video not found for " + videoTitle + ". Trying Google Drive.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                startActivity(intent);
            });
        }
    }

    public class ImageBridge {
        @JavascriptInterface public void openNativeImage(String imageUrl, String imageTitle) {
            if (imageUrl == null || imageUrl.trim().isEmpty()) return;
            Uri uri = Uri.parse(imageUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().toLowerCase(Locale.ROOT).endsWith("google.com")) return;
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, NativeImageActivity.class);
                intent.putExtra("IMAGE_URL", imageUrl.trim());
                intent.putExtra("IMAGE_TITLE", imageTitle);
                startActivity(intent);
            });
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
