package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;
import android.graphics.Color;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

/** Full-screen player. Accepts local file:// URIs only; no Drive/network fallback. */
public class NativeVideoActivity extends Activity {
    private static final String TAG = "NativeVideoPlayer";
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progressBar;
    private boolean closing = false;
    private JSONArray playlist;
    private int playlistIndex = 0;
    private float touchDownX, touchDownY;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        String title = getIntent().getStringExtra("VIDEO_TITLE");
        try {
            String playlistJson = getIntent().getStringExtra("VIDEO_PLAYLIST_JSON");
            if (playlistJson != null && !playlistJson.trim().isEmpty()) {
                playlist = new JSONArray(playlistJson);
                playlistIndex = Math.max(0, Math.min(getIntent().getIntExtra("VIDEO_START_INDEX", 0), playlist.length() - 1));
            }
        } catch (Exception ignored) { playlist = null; }
        if (playlist == null || playlist.length() == 0) {
            String raw = getIntent().getStringExtra("VIDEO_URL");
            if (raw == null || !raw.startsWith("file://")) {
                Toast.makeText(this, "Only videos saved in local storage can be played.", Toast.LENGTH_LONG).show();
                finish(); return;
            }
            playlist = new JSONArray();
            try { JSONObject item = new JSONObject(); item.put("title", title == null ? "Wildlife Video" : title); item.put("source", raw); playlist.put(item); }
            catch (Exception ignored) {}
        }
        if (!isPlayableItem(playlistIndex)) {
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

        // Keep an always-visible Back control on top of the native video surface.
        FrameLayout topBar = new FrameLayout(this);
        topBar.setBackgroundColor(0xAA082C20);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(-1, dp(60), android.view.Gravity.TOP);
        root.addView(topBar, topBarParams);
        TextView back = new TextView(this);
        back.setText("‹  Back");
        back.setTextColor(Color.WHITE);
        back.setTextSize(18);
        back.setGravity(android.view.Gravity.CENTER);
        back.setPadding(dp(16), 0, dp(16), 0);
        topBar.addView(back, new FrameLayout.LayoutParams(dp(112), -1, android.view.Gravity.START));
        back.setOnClickListener(v -> closePlayer());
        TextView heading = new TextView(this);
        heading.setText(title == null ? "Wildlife Video" : title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(15);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        heading.setSingleLine(true);
        heading.setPadding(dp(8), 0, dp(16), 0);
        FrameLayout.LayoutParams headingParams = new FrameLayout.LayoutParams(-1, -1);
        topBar.addView(heading, headingParams);
        back.bringToFront();

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
            playPlaylistItem(playlistIndex, true);
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize local player", e);
            Toast.makeText(this, "Could not start local video: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private boolean isPlayableItem(int index) {
        try {
            JSONObject item = playlist.getJSONObject(index);
            String raw = item.optString("source", "");
            if (!raw.startsWith("file://")) return false;
            Uri uri = Uri.parse(raw);
            File file = new File(uri.getPath() == null ? "" : uri.getPath());
            return file.isFile() && file.canRead();
        } catch (Exception e) { return false; }
    }

    private void playPlaylistItem(int index, boolean autoplay) {
        if (playlist == null || index < 0 || index >= playlist.length() || !isPlayableItem(index) || player == null) return;
        try {
            JSONObject item = playlist.getJSONObject(index);
            Uri uri = Uri.parse(item.getString("source"));
            playlistIndex = index;
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();
            if (autoplay) player.play();
            super.setTitle(item.optString("title", "Wildlife Video"));
            Toast.makeText(this, (index + 1) + " / " + playlist.length(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to play selected local video.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX(); touchDownY = event.getY();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - touchDownX;
            float dy = event.getY() - touchDownY;
            if (Math.abs(dx) > dp(75) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                int next = playlistIndex + (dx < 0 ? 1 : -1);
                if (next >= 0 && next < playlist.length()) playPlaylistItem(next, true);
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void closePlayer() {
        if (closing) return;
        closing = true;
        if (player != null) player.pause();
        finish();
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override public void onBackPressed() { closePlayer(); }
    @Override protected void onStop() { if (player != null) player.pause(); super.onStop(); }
    @Override protected void onDestroy() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
