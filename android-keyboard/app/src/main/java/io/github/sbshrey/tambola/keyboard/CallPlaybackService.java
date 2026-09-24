package io.github.sbshrey.tambola.keyboard;

import android.app.*;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.io.IOException;

/** Plays only the requested local clip, then releases focus and stops itself. */
public final class CallPlaybackService extends Service {
    private static final String CHANNEL = "tambola-voice";
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private MediaSession session;
    private ResultReceiver receiver;
    private int activeId;
    @Override public void onCreate() {
        super.onCreate(); audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL, "Tambola voice", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "Tambola voice"); session.setCallback(new MediaSession.Callback() { @Override public void onStop() { stopSelf(); } @Override public void onPause() { stopSelf(); } }); session.setActive(true);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        releasePlayer(); activeId = startId; receiver = intent.getParcelableExtra("result"); int number = intent.getIntExtra("number", 0); String language = intent.getStringExtra("language");
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, CallPlaybackService.class).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_tambola).setContentTitle("Tambola number " + number)
            .setContentText("Playing on this phone").setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", stop).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0)).build();
        try {
            startForeground(47, notification);
            AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> { if (change < 0 && activeId == startId) stopSelf(startId); }).build();
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail(startId); return START_NOT_STICKY; }
            MediaPlayer current = new MediaPlayer(); player = current;
            try (AssetFileDescriptor file = getAssets().openFd(CallAudio.asset(language, number))) { current.setAudioAttributes(attributes); current.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength()); }
            current.setOnPreparedListener(ready -> { if (player == ready) { ready.start(); session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_STOP | PlaybackState.ACTION_PAUSE).setState(PlaybackState.STATE_PLAYING, 0, 1).build()); signal(0); } });
            current.setOnCompletionListener(done -> { if (player == done) stopSelf(startId); });
            current.setOnErrorListener((failed, what, extra) -> { if (player == failed) fail(startId); return true; }); current.prepareAsync();
        } catch (IOException | RuntimeException error) { fail(startId); }
        return START_NOT_STICKY;
    }
    private void signal(int code) { if (receiver != null) receiver.send(code, Bundle.EMPTY); }
    private void fail(int startId) { signal(1); stopSelf(startId); }
    private void releasePlayer() { if (player != null) { player.release(); player = null; } if (focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; } }
    @Override public void onDestroy() { releasePlayer(); signal(2); session.release(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
