package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen Media3 player with category playlist, swipe, previous/next and back navigation. */
public class NativeVideoActivity extends Activity {
    private static final String TAG = "NativeVideoPlayer";
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progressBar;
    private TextView titleView, counterView;
    private JSONArray playlist = new JSONArray();
    private int currentIndex = 0;
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();
    private GestureDetector gestureDetector;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        try {
            String json = getIntent().getStringExtra("VIDEO_PLAYLIST_JSON");
            if (json != null && !json.trim().isEmpty()) playlist = new JSONArray(json);
        } catch (Exception e) { Log.e(TAG, "Invalid playlist", e); }

        if (playlist.length() == 0) {
            JSONObject one = new JSONObject();
            try {
                String path = getIntent().getStringExtra("VIDEO_FILE_PATH");
                String url = getIntent().getStringExtra("VIDEO_URL");
                one.put("title", getIntent().getStringExtra("VIDEO_TITLE") == null ? "Wildlife video" : getIntent().getStringExtra("VIDEO_TITLE"));
                if (path != null && !path.isEmpty()) one.put("path", path); else if (url != null) one.put("url", url);
                playlist.put(one);
            } catch (Exception ignored) { }
        }
        if (playlist.length() == 0) { Toast.makeText(this, "Video is missing", Toast.LENGTH_LONG).show(); finish(); return; }
        currentIndex = Math.max(0, Math.min(getIntent().getIntExtra("VIDEO_START_INDEX", 0), playlist.length() - 1));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        TextView back = overlayButton("‹  BACK", 15);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bp.setMargins(dp(16), dp(16), 0, 0);
        root.addView(back, bp);
        back.setOnClickListener(v -> finish());

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE); titleView.setTextSize(15); titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(dp(12), dp(10), dp(12), dp(10)); titleView.setBackgroundColor(0x88000000);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.setMargins(dp(110), dp(16), dp(110), 0); root.addView(titleView, tp);

        counterView = new TextView(this);
        counterView.setTextColor(Color.WHITE); counterView.setTextSize(13); counterView.setGravity(Gravity.CENTER);
        counterView.setPadding(dp(12), dp(7), dp(12), dp(7)); counterView.setBackgroundColor(0x77000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = dp(22); root.addView(counterView, cp);

        TextView previous = overlayButton("‹", 38);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(58), dp(76), Gravity.CENTER_VERTICAL | Gravity.START);
        pp.leftMargin = dp(8); root.addView(previous, pp); previous.setOnClickListener(v -> playAt(currentIndex - 1));
        TextView next = overlayButton("›", 38);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(dp(58), dp(76), Gravity.CENTER_VERTICAL | Gravity.END);
        np.rightMargin = dp(8); root.addView(next, np); next.setOnClickListener(v -> playAt(currentIndex + 1));

        progressBar = new ProgressBar(this);
        root.addView(progressBar, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        setContentView(root);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(dy) * 1.25f && Math.abs(velocityX) > dp(100)) {
                    playAt(currentIndex + (dx < 0 ? 1 : -1));
                    return true;
                }
                return false;
            }
        });
        initializePlayer(null);
        playAt(currentIndex);
    }

    private TextView overlayButton(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(size);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(8), dp(12), dp(8)); v.setBackgroundColor(0x99000000);
        return v;
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void initializePlayer(String cookieHeader) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(20000).setReadTimeoutMs(30000);
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Cookie", cookieHeader);
            httpFactory.setDefaultRequestProperties(headers);
        }
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, httpFactory)))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (progressBar != null) progressBar.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Playback error", error);
                Toast.makeText(NativeVideoActivity.this, "Playback failed: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void playAt(int requestedIndex) {
        if (playlist.length() == 0) return;
        if (requestedIndex < 0) requestedIndex = playlist.length() - 1;
        if (requestedIndex >= playlist.length()) requestedIndex = 0;
        currentIndex = requestedIndex;
        try {
            JSONObject item = playlist.getJSONObject(currentIndex);
            String title = item.optString("title", "Wildlife video");
            String path = item.optString("path", "");
            String url = item.optString("url", "");
            titleView.setText(title);
            counterView.setText((currentIndex + 1) + " / " + playlist.length() + "   •   Swipe left/right for next video");
            progressBar.setVisibility(View.VISIBLE);
            if (!path.isEmpty()) {
                File file = new File(path);
                if (!file.isFile()) throw new IllegalStateException("Local video file not found: " + path);
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
                player.prepare(); player.play();
            } else if (!url.isEmpty() && url.contains("drive.google.com")) {
                resolveDriveAndPlay(url);
            } else if (!url.isEmpty()) {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
                player.prepare(); player.play();
            } else throw new IllegalStateException("No local path or video URL");
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Unable to open video: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resolveDriveAndPlay(String url) {
        resolverExecutor.execute(() -> {
            try {
                DriveVideoResolver.ResolvedVideo resolved = DriveVideoResolver.resolve(url);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (player != null) { player.release(); player = null; }
                    initializePlayer(resolved.cookieHeader);
                    player.setMediaItem(MediaItem.fromUri(Uri.parse(resolved.url)));
                    player.prepare(); player.play();
                });
            } catch (Exception e) {
                Log.e(TAG, "Drive fallback failed", e);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { progressBar.setVisibility(View.GONE); Toast.makeText(this, "Online backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); } });
            }
        });
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override protected void onDestroy() { if (player != null) { player.release(); player = null; } resolverExecutor.shutdownNow(); super.onDestroy(); }
}
