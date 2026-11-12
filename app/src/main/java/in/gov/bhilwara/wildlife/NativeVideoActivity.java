package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class NativeVideoActivity extends Activity {

    private static final String TAG = "NativeVideoPlayer";

    private ExoPlayer player;
    private PlayerView playerView;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enableImmersiveMode();

        String videoUrl =
                getIntent().getStringExtra("VIDEO_URL");

        String videoTitle =
                getIntent().getStringExtra("VIDEO_TITLE");

        if (videoUrl == null ||
                videoUrl.trim().isEmpty()) {

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

        playerView.setShowBuffering(
                PlayerView.SHOW_BUFFERING_ALWAYS
        );

        root.addView(
                playerView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        diagnoseVideoUrl(videoUrl);

        initializePlayer(videoUrl, videoTitle);
    }

    private void diagnoseVideoUrl(String videoUrl) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(videoUrl);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                connection.setRequestProperty(
                        "Range",
                        "bytes=0-1023"
                );

                connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0"
                );

                int statusCode =
                        connection.getResponseCode();

                String contentType =
                        connection.getContentType();

                String finalUrl =
                        connection.getURL().toString();

                Log.d(TAG,
                        "HTTP status: " + statusCode);

                Log.d(TAG,
                        "Content-Type: " + contentType);

                Log.d(TAG,
                        "Final URL: " + finalUrl);

                boolean htmlResponse =
                        contentType != null &&
                        contentType.toLowerCase(Locale.US)
                                .contains("text/html");

                if (htmlResponse) {

                    Log.e(TAG,
                            "Google Drive returned HTML, not video");

                    mainHandler.post(() ->

                            Toast.makeText(
                                    NativeVideoActivity.this,
                                    "Google Drive returned a webpage instead of video",
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }

            } catch (Exception e) {

                Log.e(TAG,
                        "Video URL diagnostic failed",
                        e);

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    private void initializePlayer(
            String videoUrl,
            String videoTitle
    ) {

        try {

            DefaultHttpDataSource.Factory httpFactory =
                    new DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(15000)
                            .setReadTimeoutMs(15000);

            DefaultMediaSourceFactory mediaSourceFactory =
                    new DefaultMediaSourceFactory(httpFactory);

            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build();

            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {

                @Override
                public void onPlayerError(
                        PlaybackException error
                ) {

                    Log.e(TAG,
                            "Playback error code: "
                                    + error.getErrorCodeName(),
                            error);

                    Toast.makeText(
                            NativeVideoActivity.this,
                            "Video error: "
                                    + error.getErrorCodeName(),
                            Toast.LENGTH_LONG
                    ).show();
                }

                @Override
                public void onPlaybackStateChanged(int state) {

                    Log.d(TAG,
                            "Playback state: " + state);
                }
            });

            MediaItem mediaItem =
                    MediaItem.fromUri(videoUrl);

            player.setMediaItem(mediaItem);

            player.prepare();
            player.play();

            Log.d(TAG, "Video title: " + videoTitle);

        } catch (Exception e) {

            Log.e(TAG,
                    "Player initialization failed",
                    e);

            Toast.makeText(
                    this,
                    "Unable to initialize video player",
                    Toast.LENGTH_LONG
            ).show();
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
    protected void onStop() {

        if (player != null) {
            player.pause();
        }

        super.onStop();
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
