
package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class NativeVideoActivity extends Activity {

    private static final String TAG = "NativeVideoPlayer";

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enableImmersiveMode();

        String videoUrl = getIntent().getStringExtra("VIDEO_URL");
        String videoTitle = getIntent().getStringExtra("VIDEO_TITLE");

        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Video URL not available",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);

        root.addView(
                playerView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        try {

            player = new ExoPlayer.Builder(this).build();

            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {

                @Override
                public void onPlayerError(PlaybackException error) {

                    Log.e(TAG, "Playback error", error);

                    Toast.makeText(
                            NativeVideoActivity.this,
                            "Unable to play video: " + error.getErrorCodeName(),
                            Toast.LENGTH_LONG
                    ).show();
                }

                @Override
                public void onPlaybackStateChanged(int state) {

                    Log.d(TAG, "Playback state: " + state);
                }
            });

            MediaItem mediaItem = MediaItem.fromUri(videoUrl);

            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();

            Log.d(TAG, "Playing: " + videoTitle);
            Log.d(TAG, "Video URL: " + videoUrl);

        } catch (Exception e) {

            Log.e(TAG, "Player initialization failed", e);

            Toast.makeText(
                    this,
                    "Video player initialization failed",
                    Toast.LENGTH_LONG
            ).show();

            finish();
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
    protected void onDestroy() {

        if (playerView != null) {
            playerView.setPlayer(null);
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}
