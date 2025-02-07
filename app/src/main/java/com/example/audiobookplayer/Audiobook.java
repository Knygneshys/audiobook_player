package com.example.audiobookplayer;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.util.Objects;

class Audiobook implements Parcelable{
    private Uri audiobookUri;
    private Uri storageFolderUri;
    private Uri coverImageUri;
    private Uri dataUri;
    private String title;
    private String author;
    public Audiobook(Uri dataUri, Uri audiobookUri, Uri storageFolderUri, Uri coverImageUri) {
        this.dataUri = dataUri;
        this.audiobookUri = audiobookUri;
        this.storageFolderUri = storageFolderUri;
        this.coverImageUri = coverImageUri;
    }


    protected Audiobook(Parcel in) {
        audiobookUri = in.readParcelable(Uri.class.getClassLoader());
        storageFolderUri = in.readParcelable(Uri.class.getClassLoader());
        coverImageUri = in.readParcelable(Uri.class.getClassLoader());
        dataUri = in.readParcelable(Uri.class.getClassLoader());
    }

    public static final Creator<Audiobook> CREATOR = new Creator<Audiobook>() {
        @Override
        public Audiobook createFromParcel(Parcel in) {
            return new Audiobook(in);
        }

        @Override
        public Audiobook[] newArray(int size) {
            return new Audiobook[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Audiobook audiobook = (Audiobook) o;
        return Objects.equals(audiobookUri, audiobook.audiobookUri) && Objects.equals(storageFolderUri, audiobook.storageFolderUri) && Objects.equals(coverImageUri, audiobook.coverImageUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(audiobookUri, storageFolderUri, coverImageUri);
    }

    public Uri getAudiobookUri() {
        return audiobookUri;
    }

    public void setAudiobookUri(Uri audiobookUri) {
        this.audiobookUri = audiobookUri;
    }

    public Uri getStorageFolderUri() {
        return storageFolderUri;
    }

    public void setStorageFolderUri(Uri storageFolderUri) {
        this.storageFolderUri = storageFolderUri;
    }

    public Uri getCoverImageUri() {
        return coverImageUri;
    }

    public void setCoverImageUri(Uri coverImageUri) {
        this.coverImageUri = coverImageUri;
    }


    public Uri getDataUri() {
        return dataUri;
    }

    public void setDataUri(Uri dataUri) {
        this.dataUri = dataUri;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(audiobookUri, flags);
        dest.writeParcelable(storageFolderUri, flags);
        dest.writeParcelable(coverImageUri, flags);
        dest.writeParcelable(dataUri, flags);
    }
}
