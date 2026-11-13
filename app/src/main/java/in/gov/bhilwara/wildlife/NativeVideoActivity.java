package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Media3 player with Google Drive public-download confirmation resolution. */
public class NativeVideoActivity extends Activity {
    private static final String TAG = "NativeVideoPlayer";
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progressBar;
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();

        String videoUrl = getIntent().getStringExtra("VIDEO_URL");
        String videoTitle = getIntent().getStringExtra("VIDEO_TITLE");
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            Toast.makeText(this, "Video URL is missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(96, 96,
                android.view.Gravity.CENTER);
        root.addView(progressBar, progressParams);
        setContentView(root);

        String requestedUrl = videoUrl.trim();
        if (requestedUrl.contains("drive.google.com")) {
            resolveDriveAndPlay(requestedUrl, videoTitle);
        } else {
            startPlayer(requestedUrl, null, videoTitle);
        }
    }

    private void resolveDriveAndPlay(String url, String title) {
        progressBar.setVisibility(View.VISIBLE);
        resolverExecutor.execute(() -> {
            try {
                DriveVideoResolver.ResolvedVideo resolved = DriveVideoResolver.resolve(url);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Log.i(TAG, "Drive URL resolved; content type=" + resolved.contentType);
                    startPlayer(resolved.url, resolved.cookieHeader, title);
                });
            } catch (Exception error) {
                Log.e(TAG, "Google Drive URL resolution failed", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Unable to access this Drive video: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startPlayer(String url, String cookieHeader, String title) {
        try {
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(30000);
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Cookie", cookieHeader);
                httpFactory.setDefaultRequestProperties(headers);
            }
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                    .build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    progressBar.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                    Log.d(TAG, "Playback state=" + state);
                }
                @Override public void onPlayerError(PlaybackException error) {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Media3 playback error", error);
                    Toast.makeText(NativeVideoActivity.this,
                            "Video playback failed: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
                }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
            Log.i(TAG, "Playing title=" + title + " url=" + url);
        } catch (Exception error) {
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, "Could not initialize player", error);
            Toast.makeText(this, "Could not start video: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        resolverExecutor.shutdownNow();
        super.onDestroy();
    }
}
