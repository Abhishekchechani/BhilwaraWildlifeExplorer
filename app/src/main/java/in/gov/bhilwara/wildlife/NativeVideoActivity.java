package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Media3 player: local app-internal file first; Google Drive remains a fallback for files not imported yet. */
public class NativeVideoActivity extends Activity {
    private static final String TAG = "NativeVideoPlayer";
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progressBar;
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();

        String localPath = getIntent().getStringExtra("VIDEO_FILE_PATH");
        String contentUri = getIntent().getStringExtra("VIDEO_CONTENT_URI");
        String videoUrl = getIntent().getStringExtra("VIDEO_URL");
        String videoTitle = getIntent().getStringExtra("VIDEO_TITLE");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(96, 96, Gravity.CENTER);
        root.addView(progressBar, progressParams);
        setContentView(root);

        if (contentUri != null && !contentUri.trim().isEmpty()) {
            Log.i(TAG, "Playing local content URI: " + contentUri);
            startPlayer(android.net.Uri.parse(contentUri), null);
        } else if (localPath != null && !localPath.trim().isEmpty()) {
            File file = new File(localPath);
            if (!file.isFile()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Local video file is missing. Import it again from USB.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            Log.i(TAG, "Playing local video: " + file.getAbsolutePath());
            startPlayer(android.net.Uri.fromFile(file), null);
        } else if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            String requestedUrl = videoUrl.trim();
            if (requestedUrl.contains("drive.google.com")) resolveDriveAndPlay(requestedUrl);
            else startPlayer(android.net.Uri.parse(requestedUrl), null);
        } else {
            Toast.makeText(this, "Video file or URL is missing", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void resolveDriveAndPlay(String url) {
        progressBar.setVisibility(View.VISIBLE);
        resolverExecutor.execute(() -> {
            try {
                DriveVideoResolver.ResolvedVideo resolved = DriveVideoResolver.resolve(url);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Log.i(TAG, "Drive fallback resolved; content type=" + resolved.contentType);
                    startPlayer(android.net.Uri.parse(resolved.url), resolved.cookieHeader);
                });
            } catch (Exception error) {
                Log.e(TAG, "Google Drive fallback failed", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Video is not stored locally and Drive fallback failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startPlayer(android.net.Uri mediaUri, String cookieHeader) {
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
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, httpFactory)))
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
            player.setMediaItem(MediaItem.fromUri(mediaUri));
            player.prepare();
            player.play();
        } catch (Exception error) {
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, "Could not initialize player", error);
            Toast.makeText(this, "Could not start video: " + error.getMessage(), Toast.LENGTH_LONG).show();
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
        if (player != null) { player.release(); player = null; }
        resolverExecutor.shutdownNow();
        super.onDestroy();
    }
}
