package com.example.audiobookplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileSelect extends AppCompatActivity implements RecyclerViewInterface {

    private DocumentFile audioBookFolder;
    private ArrayList<Audiobook> audiobookList;
    private RecyclerView recyclerView;
    private int pos;
    private final int NOTIF_CODE = 7;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_select);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_CODE);
        audiobookList = new ArrayList<>();
        recyclerView = findViewById(R.id.audiobookSelectionList);
        loadData();
        SharedPreferences prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        boolean bookIsSelected = prefs.getBoolean("book_selected", false);
        if (bookIsSelected)
            onBookClick(pos);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);

        if(requestCode == NOTIF_CODE)
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Thanks bro", Toast.LENGTH_SHORT).show();
    }


    private void loadData()
    {
        File file = getApplicationContext().getFileStreamPath("BookAddresses.dat");
        if(file.exists())
        {
            try {
                InputStreamReader reader = new InputStreamReader(openFileInput("BookAddresses.dat"));
                BufferedReader bf = new BufferedReader(reader);
                String line = bf.readLine();
                if(line != null) {
                    Uri uri = Uri.parse(line);
                    audioBookFolder = DocumentFile.fromTreeUri(FileSelect.this, uri);
                    scanFolder(false);
                }
                line = bf.readLine();
                if(line != null)
                    pos = Integer.parseInt(line);
            } catch (IOException e) {
                Toast.makeText(this, "Failed to read/find BookAddresses.dat", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void openFilePicker(View v)
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        activityResultLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == Activity.RESULT_OK)
                    {
                        Intent intent = result.getData();
                        Uri uri = intent.getData();

                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        FileOutputStream file;
                        try {
                            file = openFileOutput("BookAddresses.dat", MODE_PRIVATE);
                            OutputStreamWriter outputFile = new OutputStreamWriter(file);

                            audioBookFolder = DocumentFile.fromTreeUri(FileSelect.this, uri);
                            scanFolder(false);

                            outputFile.write((audioBookFolder.getUri().toString()) + "\n");
                            outputFile.flush();
                            outputFile.close();
                        } catch (IOException e) {
                            Toast.makeText(FileSelect.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }
    });

    public void refresh(View v)
    {
        Button refreshButton = (Button)v;
        ProgressBar progressBar = findViewById(R.id.refreshProgress);
        Button audiobookFolderSelect = findViewById(R.id.audiobookSelectButton);

        if (audioBookFolder != null)
        {
            refreshButton.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            audiobookFolderSelect.setEnabled(false);

            // Run scanFolder() on a background thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    scanFolder(true);  // Time-consuming operation

                    // Update UI after background work is done
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.INVISIBLE);
                            refreshButton.setVisibility(View.VISIBLE);
                            audiobookFolderSelect.setEnabled(true);
                        }
                    });
                }
            }).start();
        }
    }

    private void scanFolder(boolean lookingForM4bs)
    {
        DocumentFile[] files = audioBookFolder.listFiles();
        for(DocumentFile file : files)
        {
            String fileName = file.getName();
            Pattern pattern = Pattern.compile(".m4b");
            Matcher matcher = pattern.matcher(fileName);
            boolean isM4b = matcher.find();
            if(lookingForM4bs && isM4b)
            {
                DocumentFile newFolder = workWithM4b(file, fileName);
                Audiobook audiobook = convertFolderToAudiobookClass(newFolder);
                audiobookList.add(audiobook);
            }
            else if(!isM4b)
            {
                try {
                    Audiobook audiobook = convertFolderToAudiobookClass(file);
                    if(!audiobookList.contains(audiobook))
                        audiobookList.add(audiobook);
                } catch (Exception e) {
                    Toast.makeText(FileSelect.this, "Failed to convert " + file.getName(), Toast.LENGTH_LONG).show();
                }
            }
        }

        // is needed because this method gets called in refresh() and refresh() works in a background thread (UI can't get updated from background threads)
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Audiobook_RecyclerViewAdapter adapter = new Audiobook_RecyclerViewAdapter(FileSelect.this, audiobookList, FileSelect.this);
                recyclerView.setAdapter(adapter);
                recyclerView.setLayoutManager(new LinearLayoutManager(FileSelect.this));
            }
        });
    }

    private Audiobook convertFolderToAudiobookClass(DocumentFile folder)
    {
        DocumentFile[] files = folder.listFiles();
        Audiobook audiobook = null;
        DocumentFile audioFile = null;
        Uri coverImageUri = null;
        Uri dataUri = null;
        for(DocumentFile file : files)
        {
            String fileName = file.getName();
            if(Objects.equals(fileName, "cover.png"))
                coverImageUri = file.getUri();
            else if(Objects.equals(fileName, "data.txt"))
                dataUri = file.getUri();
            else
                audioFile = file;
        }
        audiobook = new Audiobook(dataUri, audioFile.getUri(), folder.getUri(), coverImageUri);

        return audiobook;
    }

    private DocumentFile workWithM4b(DocumentFile file, String fileName)
    {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, file.getUri());
        String bookName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        bookName = bookName != null ? bookName : fileName;
        DocumentFile storageFolder = audioBookFolder.createDirectory(bookName);
        // Read and write the cover image
        byte[] bookCover = retriever.getEmbeddedPicture();
        Bitmap coverImage = null;
        if(bookCover != null)
        {
            try {
                DocumentFile coverFile = storageFolder.createFile("image/png", "cover.png");
                OutputStream os = getContentResolver().openOutputStream(coverFile.getUri());

                coverImage = BitmapFactory.decodeByteArray(bookCover, 0, bookCover.length);
                coverImage.compress(Bitmap.CompressFormat.PNG, 100, os);

                os.flush();
                os.close();
            } catch (Exception e) {
                Toast.makeText(FileSelect.this, "Failed to load image", Toast.LENGTH_LONG).show();
            }
        }
        // Create a data storage file to save elapsed time and selected playback speed
        DocumentFile timeAndPlaybackSpeed = storageFolder.createFile("text/plain", "data.txt");
        try
        {
            OutputStream os = getContentResolver().openOutputStream(timeAndPlaybackSpeed.getUri());
            OutputStreamWriter writer = new OutputStreamWriter(os);
            // first it writes time and then playback speed
            writer.write("0\n");
            writer.append("1");
            writer.flush();
            writer.close();
            os.close();
        } catch (IOException e) {
            Toast.makeText(FileSelect.this, "Failed to create data file", Toast.LENGTH_SHORT).show();
        }
        // Copy over and move the .m4b audiobook file
        DocumentFile audiobookCopy = storageFolder.createFile("audio/m4b", file.getName());
        try {
            InputStream in = getContentResolver().openInputStream(file.getUri());
            OutputStream os = getContentResolver().openOutputStream(audiobookCopy.getUri());

            byte[] buffer = new byte[4096];
            int bytesRead;
            while((bytesRead = in.read(buffer)) != -1)
                os.write(buffer, 0, bytesRead);

            in.close();
            os.flush();
            os.close();

            file.delete();
        } catch (Exception e) {
            Toast.makeText(FileSelect.this, "Failed to move audiobook", Toast.LENGTH_SHORT).show();
        }

        return storageFolder;
    }

    @Override
    public void onBookClick(int position)
    {
        Intent intent = new Intent(FileSelect.this, AudiobookPlayerUI.class);
        Audiobook audiobook = audiobookList.get(position);
        intent.putExtra("Audiobook", audiobook);

        try
        {
            FileOutputStream file = openFileOutput("BookAddresses.dat", MODE_PRIVATE);
            OutputStreamWriter outputFile = new OutputStreamWriter(file);

            outputFile.write((audioBookFolder.getUri().toString()) + "\n" + position);
            outputFile.flush();
            outputFile.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        startActivity(intent);

        finish();
    }

    @Override
    public void onLongBookClick(int position)
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(FileSelect.this);
        alert.setMessage("Do you want to delete this audiobook?");
        alert.setTitle("Alert");
        alert.setCancelable(true);
        alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Audiobook audiobook = audiobookList.get(position);
                DocumentFile.fromSingleUri(FileSelect.this, audiobook.getStorageFolderUri()).delete();
                audiobookList.remove(audiobook);
                scanFolder(false);
            }
        });
        alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alert.create().show();
    }
}