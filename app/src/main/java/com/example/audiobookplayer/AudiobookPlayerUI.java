package com.example.audiobookplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class AudiobookPlayerUI extends AppCompatActivity implements AdapterView.OnItemSelectedListener, SeekBar.OnSeekBarChangeListener {
    private ImageButton stateToggleButton;
    private TextView stateText, currentTimeView, remainingTimeView, progressPercentageView;
    private Spinner spinner;
    private int duration;
    private static Timer timer;
    private float playbackSpeed;
    private SeekBar timeSlider;
    private SharedPreferences.Editor editor;
    private Audiobook audiobook;
    private Intent serviceIntent;
    private boolean isBound = false;
    private AudiobookPlayerService audiobookService;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudiobookPlayerService.AudiobookBinder binder = (AudiobookPlayerService.AudiobookBinder) service;
            audiobookService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audiobook_player);

        SharedPreferences prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        Intent intent = getIntent();
        try {
            audiobook = intent.getParcelableExtra("Audiobook");
            editor = prefs.edit();
            editor.putBoolean("book_selected", true);
            editor.apply();

        } catch (Exception e) {
            editor.putBoolean("book_selected", false);
            editor.apply();
        }



        findViews();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.speed_values, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        spinner.setAdapter(adapter);
        //  spinner.setSelection(3);
        spinner.setOnItemSelectedListener(this);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        retriever.setDataSource(this, audiobook.getAudiobookUri());
        String bookName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        ((TextView)findViewById(R.id.audiobookName)).setText(bookName);
        audiobook.setTitle(bookName);
        String author = "author";
        String key_author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
        String key_artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String key_album_artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
        if(key_author != null)
            author = key_author;
        else if (key_artist != null)
            author = key_artist;
        else if (key_album_artist != null)
            author = key_album_artist;

        audiobook.setAuthor(author);

        InputStream inputStream = null;
        try {
            inputStream = this.getContentResolver().openInputStream(audiobook.getCoverImageUri());
            Bitmap cover = BitmapFactory.decodeStream(inputStream);
            stateToggleButton.setImageBitmap(cover);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Could not find book cover image", Toast.LENGTH_SHORT).show();
            editor.putBoolean("book_selected", false);
            editor.apply();
        }


        serviceIntent = new Intent(this, AudiobookPlayerService.class);
        serviceIntent.putExtra("Audiobook", audiobook);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        duration = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        try {
            retriever.release();
        } catch (IOException e) {
            editor.putBoolean("book_selected", false);
            editor.apply();
        }

        remainingTimeView.setText(formatTime(duration));
        timeSlider.setMax(duration);
        timeSlider.setOnSeekBarChangeListener(this);


        try {
            inputStream = this.getContentResolver().openInputStream(audiobook.getDataUri());
            // first it reads time and then playback speed
            BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream));
            String time = bf.readLine();
            String playback = bf.readLine();
            playbackSpeed = Float.parseFloat(playback);
            int pos = adapter.getPosition(String.valueOf(playbackSpeed));
            spinner.setSelection(pos);
            bf.close();
            inputStream.close();
            updateTimerAndPercentageViews(Integer.parseInt(time));
        } catch (Exception e) {
            Toast.makeText(this, "Could not read data.txt", Toast.LENGTH_SHORT).show();
            editor.putBoolean("book_selected", false);
            editor.apply();
        }
    }


    private void findViews()
    {
        stateToggleButton = findViewById(R.id.togglePlayPause);
        stateText = findViewById(R.id.stateText);
        currentTimeView = findViewById(R.id.currentTime);
        remainingTimeView = findViewById(R.id.timeRemaining);
        progressPercentageView = findViewById(R.id.progressPercentage);
        timeSlider = findViewById(R.id.timeSlider);
        spinner = findViewById(R.id.speedSelector);
    }

    private String formatTime(int ms)
    {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        int hours = ms / (1000 * 60 * 60);

        return hours + ":" + minutes + ":" + seconds;
    }

    private void beginTimer()
    {
        final Handler handler = new Handler();
        timer = new Timer();
        TimerTask controlTimeViews = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        int currentPos = audiobookService.getCurrentPosition();
                        if(currentPos < duration)
                        {
                            updateTimerAndPercentageViews(currentPos);
                        }
                        else
                        {
                            stateText.setText("▶");
                            timer.cancel();
                        }
                    }
                });
            }
        };

        timer.schedule(controlTimeViews, 0, 1000);
    }

    private void updateTimerAndPercentageViews(int currentPos)
    {
        String percentage = String.format("%.2f", (currentPos * 100.0 / duration)) + "%";
        progressPercentageView.setText(percentage);
        currentTimeView.setText(formatTime(currentPos));
        remainingTimeView.setText(formatTime(duration-currentPos));
        timeSlider.setProgress(currentPos);
    }

    public void toggleState(View v)
    {
        if(stateText.getText().equals("▶"))
        {
            beginTimer();
            audiobookService.play();
            stateText.setText("❚❚");
        }
        else
        {
            timer.cancel();
            audiobookService.pause();
            stateText.setText("▶");
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
    {
        playbackSpeed = Float.parseFloat((String)parent.getItemAtPosition(position));
        parent.setSelection(position);
        audiobookService.setPlaybackSpeed(playbackSpeed);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {

    }

    public void skip(View v)
    {
        int id = v.getId();
        if(timer != null)
            timer.cancel();
        int currentPos = audiobookService.getCurrentPosition();
        if(id == R.id.skipBackward)
        {
            audiobookService.seekTo(currentPos-10000);
        }
        else if (id == R.id.doubleSkipBackward)
        {
            audiobookService.seekTo(currentPos-60000);
        }
        else if(id == R.id.skipForward)
        {
            audiobookService.seekTo(currentPos+10000);
        }
        else if(id == R.id.doubleSkipForward)
        {
            audiobookService.seekTo(currentPos+60000);
        }
        if(audiobookService.isPlaying())
            beginTimer();
        updateTimerAndPercentageViews(currentPos);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
        if(fromUser)
        {
            if (timer != null)
                timer.cancel();
            audiobookService.seekTo(progress);
            if (audiobookService.isPlaying())
                beginTimer();
            updateTimerAndPercentageViews(audiobookService.getCurrentPosition());
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void goToFileSelect(View v)
    {
        Intent intent = new Intent(this, FileSelect.class);
        editor.putBoolean("book_selected", false);
        editor.apply();
        if(timer != null)
            timer.cancel();
        startActivity(intent);
        finish();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        if(isBound)
        {
            unbindService(serviceConnection);
            stopService(serviceIntent);
            isBound = false;
        }
        if(timer != null)
            timer.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(isBound)
        {
            unbindService(serviceConnection);
            stopService(serviceIntent);
            isBound = false;
        }
    }
}