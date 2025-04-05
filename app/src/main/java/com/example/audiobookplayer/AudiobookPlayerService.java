package com.example.audiobookplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.service.quicksettings.PendingIntentActivityWrapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Objects;

public class AudiobookPlayerService extends Service
{
    private final String CHANNEL_ID = "AudiobookChannel";
    private final IBinder binder = new AudiobookBinder();
    private MediaPlayer audiobookPlayer;
    private Audiobook audiobook;
    private SharedPreferences.Editor editor;
    private float playbackSpeed;
    private Bitmap bookCover;
    private MediaSessionCompat mediaSession;


    private BroadcastReceiver mediaButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if(intentAction.equals(Intent.ACTION_MEDIA_BUTTON))
            {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if(event != null)
                {
                    if(event.getAction() == KeyEvent.ACTION_DOWN)
                    {
                        switch (event.getKeyCode())
                        {
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                if(isPlaying())
                                {
                                    pause();
                                }
                                else
                                {
                                    play();
                                }
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                play();
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                pause();
                                break;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                seekTo(getCurrentPosition() + 10000);
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                seekTo(getCurrentPosition() - 10000);
                                break;
                        }
                    }
                }
            }
        }
    };

    public class AudiobookBinder extends Binder
    {
        public AudiobookPlayerService getService()
        {
            return AudiobookPlayerService.this;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        createNotificationChannel();
        SharedPreferences prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        editor = prefs.edit();

        // Initialize MediaSession
        mediaSession = new MediaSessionCompat(this, "AudiobookPlayerService");
        mediaSession.setCallback(new MediaSessionCallback());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(mediaButtonReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        }

        // Register media button receiver
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, mediaButtonReceiver.getClass());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent,
                PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setMediaButtonReceiver(pendingIntent);
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback
    {
        @Override
        public void onPlay()
        {
            play();
        }

        @Override
        public void onPause()
        {
            pause();
        }
    }

    private void createNotificationChannel()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audiobook states",
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if(Objects.equals(action, "ACTION_PLAY"))
            play();
        else if(Objects.equals(action, "ACTION_PAUSE"))
            pause();
        else if(Objects.equals(action, "ACTION_FORWARD"))
            seekTo(getCurrentPosition() + 10000);
        else if(Objects.equals(action, "ACTION_REWIND"))
            seekTo(getCurrentPosition() - 10000);
        else {
            audiobook = intent.getParcelableExtra("Audiobook");
            audiobookPlayer = MediaPlayer.create(this, audiobook.getAudiobookUri());
            audiobookPlayer.setLooping(false);
            try {
                InputStream inputStream = inputStream = this.getContentResolver().openInputStream(audiobook.getDataUri());
                // first it reads time and then playback speed
                BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream));
                String time = bf.readLine();
                String playback = bf.readLine();
                audiobookPlayer.seekTo(Integer.parseInt(time));
                playbackSpeed = Float.parseFloat(playback);
                setPlaybackSpeed(playbackSpeed);

                inputStream = this.getContentResolver().openInputStream(audiobook.getCoverImageUri());
                bookCover = BitmapFactory.decodeStream(inputStream);

                bf.close();
                inputStream.close();
            } catch (Exception e) {
                Toast.makeText(this, "Could not read data.txt", Toast.LENGTH_SHORT).show();
                editor.putBoolean("book_selected", false);
                editor.apply();
            }

            try {
                Notification notification = createNotification();
                startForeground(1, notification);
            } catch (Exception e) {
                editor.putBoolean("book_selected", false);
                editor.apply();
            }
        }
        return START_STICKY;
    }

    private Notification createNotification()
    {
        Intent playIntent = new Intent(this, AudiobookPlayerService.class);
        playIntent.setAction("ACTION_PLAY");
        PendingIntent playPendingIntent = PendingIntent.getService(this, 4, playIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent pauseIntent = new Intent(this, AudiobookPlayerService.class);
        pauseIntent.setAction("ACTION_PAUSE");
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 4, pauseIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent forwardIntent = new Intent(this, AudiobookPlayerService.class);
        forwardIntent.setAction("ACTION_FORWARD");
        PendingIntent forwardPendingIntent = PendingIntent.getService(this, 4, forwardIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent rewindIntent = new Intent(this, AudiobookPlayerService.class);
        rewindIntent.setAction("ACTION_REWIND");
        PendingIntent rewindPendingIntent = PendingIntent.getService(this, 4, rewindIntent,
                PendingIntent.FLAG_IMMUTABLE);


        androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(audiobook.getTitle())
                .setContentText(audiobook.getAuthor())
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(bookCover)
                .setOngoing(true)
                .addAction(R.drawable.rewind, "Rewind", rewindPendingIntent)
                .addAction(R.drawable.play, "Play", playPendingIntent)
                .addAction(R.drawable.pause, "Pause", pausePendingIntent)
                .addAction(R.drawable.fast_forward, "Fast_forward", forwardPendingIntent)
                .setStyle(mediaStyle)
                .build();

    }

    public void play()
    {
        if(audiobookPlayer != null && !audiobookPlayer.isPlaying())
        {
            audiobookPlayer.seekTo(audiobookPlayer.getCurrentPosition()-2000);
            audiobookPlayer.start();
            saveTime();
        }
    }

    public void pause()
    {
        if(audiobookPlayer != null && audiobookPlayer.isPlaying())
        {
            audiobookPlayer.pause();
            saveTime();
        }
    }

    public void seekTo(int ms)
    {
        audiobookPlayer.seekTo(ms);
    }

    public boolean isPlaying()
    {
        return audiobookPlayer.isPlaying();
    }

    public int getCurrentPosition()
    {
        return audiobookPlayer.getCurrentPosition();
    }

    public int getDuration()
    {
        return audiobookPlayer.getDuration();
    }

    public void setPlaybackSpeed(float speed)
    {
        playbackSpeed = speed;
        PlaybackParams params = new PlaybackParams();
        params.setSpeed(playbackSpeed);
        if(audiobookPlayer.isPlaying())
            audiobookPlayer.setPlaybackParams(params);
        else
        {
            audiobookPlayer.setPlaybackParams(params);
            audiobookPlayer.pause();
        }
    }

    private void saveTime()
    {
        try {
            OutputStream outputStream = this.getContentResolver().openOutputStream(audiobook.getDataUri(), "wt");
            BufferedWriter writer;
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            int currentPosition = audiobookPlayer.getCurrentPosition() - 8000;
            writer.write(currentPosition + "\n" + playbackSpeed);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Toast.makeText(this, "Could not find data.txt", Toast.LENGTH_SHORT).show();
            editor.putBoolean("book_selected", false);
            editor.apply();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(audiobookPlayer != null)
            audiobookPlayer.release();

        unregisterReceiver(mediaButtonReceiver);

        if(mediaSession != null)
        {
            mediaSession.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
