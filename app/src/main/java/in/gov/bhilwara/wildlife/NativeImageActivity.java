package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen native viewer for public Google Drive wildlife photos. */
public class NativeImageActivity extends Activity {
    private static final String TAG = "NativeImageViewer";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ImageView imageView;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();

        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        String imageTitle = getIntent().getStringExtra("IMAGE_TITLE");
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Toast.makeText(this, "Image URL is missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(96, 96, Gravity.CENTER);
        root.addView(progress, progressParams);

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(28);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(64, 64,
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(16, 16, 16, 16);
        root.addView(close, closeParams);
        close.setOnClickListener(v -> finish());

        if (imageTitle != null && !imageTitle.trim().isEmpty()) {
            TextView title = new TextView(this);
            title.setText(imageTitle);
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);
            title.setMaxLines(2);
            title.setPadding(20, 14, 20, 14);
            title.setBackgroundColor(0x99000000);
            FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            root.addView(title, titleParams);
        }
        setContentView(root);
        loadImage(imageUrl.trim());
    }

    private void loadImage(String originalUrl) {
        executor.execute(() -> {
            try {
                String resolvedUrl = originalUrl;
                String cookieHeader = "";
                if (originalUrl.contains("drive.google.com")) {
                    DriveVideoResolver.ResolvedVideo resolved = DriveVideoResolver.resolve(originalUrl);
                    resolvedUrl = resolved.url;
                    cookieHeader = resolved.cookieHeader;
                    Log.i(TAG, "Drive image resolved; content type=" + resolved.contentType);
                }
                Bitmap bitmap = downloadScaledBitmap(resolvedUrl, cookieHeader);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    imageView.setImageBitmap(bitmap);
                });
            } catch (Exception error) {
                Log.e(TAG, "Image load failed", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Unable to load this photo: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Bitmap downloadScaledBitmap(String url, String cookieHeader) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = openStream(url, cookieHeader)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalStateException("The returned file is not a supported image.");
        }

        int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds, targetWidth, targetHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = openStream(url, cookieHeader)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IllegalStateException("Android could not decode the image.");
            return bitmap;
        }
    }

    private InputStream openStream(String urlString, String cookieHeader) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "image/*,application/octet-stream,*/*;q=0.8");
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            connection.setRequestProperty("Cookie", cookieHeader);
        }
        int status = connection.getResponseCode();
        String contentType = connection.getContentType();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Image server returned HTTP " + status);
        }
        if (contentType != null && (contentType.toLowerCase().contains("text/html")
                || contentType.toLowerCase().contains("application/xhtml"))) {
            connection.disconnect();
            throw new IllegalStateException("Google Drive returned an HTML page instead of an image.");
        }
        return new DisconnectingInputStream(connection);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int sample = 1;
        while ((height / (sample * 2)) >= reqHeight && (width / (sample * 2)) >= reqWidth) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private static final class DisconnectingInputStream extends java.io.FilterInputStream {
        private final HttpURLConnection connection;
        DisconnectingInputStream(HttpURLConnection connection) throws Exception {
            super(connection.getInputStream());
            this.connection = connection;
        }
        @Override public void close() throws java.io.IOException {
            try { super.close(); } finally { connection.disconnect(); }
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
