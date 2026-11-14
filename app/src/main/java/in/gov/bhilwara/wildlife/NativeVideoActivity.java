package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;

/** Full-screen player. Accepts local file:// URIs only; no Drive/network fallback. */
public class NativeVideoActivity extends Activity {
    private static final String TAG = "NativeVideoPlayer";
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progressBar;
    private boolean closing = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        String raw = getIntent().getStringExtra("VIDEO_URL");
        String title = getIntent().getStringExtra("VIDEO_TITLE");
        if (raw == null || !raw.startsWith("file://")) {
            Toast.makeText(this, "Only videos saved in local storage can be played.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        Uri uri = Uri.parse(raw);
        File file = new File(uri.getPath() == null ? "" : uri.getPath());
        if (!file.isFile() || !file.canRead()) {
            Toast.makeText(this, "Local video file is missing or cannot be read.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(96, 96, android.view.Gravity.CENTER);
        root.addView(progressBar, pp);
        setContentView(root);
        try {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (progressBar != null) progressBar.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Local playback error for " + title, error);
                    Toast.makeText(NativeVideoActivity.this, "Local video playback failed: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
                }
            });
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();
            player.play();
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize local player", e);
            Toast.makeText(this, "Could not start local video: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override public void onBackPressed() { if (closing) return; closing = true; if (player != null) player.pause(); finish(); }
    @Override protected void onStop() { if (player != null) player.pause(); super.onStop(); }
    @Override protected void onDestroy() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
