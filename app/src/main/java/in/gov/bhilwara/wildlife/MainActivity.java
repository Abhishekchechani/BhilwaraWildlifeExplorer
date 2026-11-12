
package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final String TAG = "WildlifeWebView";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enableImmersiveMode();

        WebView.setWebContentsDebuggingEnabled(true);

        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        setContentView(webView);

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

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        webView.setBackgroundColor(0xFF04251D);

        webView.addJavascriptInterface(
                new VideoBridge(),
                "AndroidVideo"
        );

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {

                Log.d(TAG,
                        "Console: " + message.message()
                                + " | Line: " + message.lineNumber()
                                + " | Source: " + message.sourceId()
                );

                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                Log.d(TAG, "Loading: " + progress + "%");
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                Log.d(TAG, "Page started: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished: " + url);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                Log.e(TAG,
                        "WebView error: " + error.getDescription()
                                + " | URL: " + request.getUrl()
                );
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class VideoBridge {

        @JavascriptInterface
        public void playNativeVideo(String videoUrl, String videoTitle) {

            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                Log.e(TAG, "Empty video URL");
                return;
            }

            Uri uri = Uri.parse(videoUrl.trim());

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                Log.e(TAG, "Only HTTPS video URLs are allowed");
                return;
            }

            runOnUiThread(() -> {

                Intent intent = new Intent(
                        MainActivity.this,
                        NativeVideoActivity.class
                );

                intent.putExtra("VIDEO_URL", videoUrl);
                intent.putExtra("VIDEO_TITLE", videoTitle);

                startActivity(intent);
            });
        }
    }

    private void enableImmersiveMode() {

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            enableImmersiveMode();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
