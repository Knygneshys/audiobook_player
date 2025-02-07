package com.example.audiobookplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;

class Audiobook_RecyclerViewAdapter extends RecyclerView.Adapter<Audiobook_RecyclerViewAdapter.MyViewHolder>
{
    private final RecyclerViewInterface recyclerViewInterface;
    Context context;
    ArrayList<Audiobook> audiobooks;
    public Audiobook_RecyclerViewAdapter(Context context, ArrayList<Audiobook> books, RecyclerViewInterface recyclerViewInterface)
    {
        this.context = context;
        audiobooks = books;
        this.recyclerViewInterface = recyclerViewInterface;
    }

    @NonNull
    @Override
    public Audiobook_RecyclerViewAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // This is where you inflate the layout (Giving a look to our rows)
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.recycler_view_row, parent, false);

        return new Audiobook_RecyclerViewAdapter.MyViewHolder(view, recyclerViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull Audiobook_RecyclerViewAdapter.MyViewHolder holder, int position) {
        // assigning values to the views we created in the recycler_view_row layout file
        // based on the position of the recycler view
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(audiobooks.get(position).getCoverImageUri());
            Bitmap cover = BitmapFactory.decodeStream(inputStream);
            holder.cover.setImageBitmap(cover);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        String title = DocumentFile.fromSingleUri(context, audiobooks.get(position).getAudiobookUri()).getName();
        holder.title.setText(title);
    }

    @Override
    public int getItemCount() {
        return audiobooks.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder{

        ImageView cover;
        TextView title;
        public MyViewHolder(@NonNull View itemView, RecyclerViewInterface recyclerViewInterface) {
            super(itemView);

            cover = itemView.findViewById(R.id.bookCover);
            title = itemView.findViewById(R.id.bookTitle);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(recyclerViewInterface != null)
                    {
                        int pos = getAdapterPosition();

                        if(pos != RecyclerView.NO_POSITION)
                            recyclerViewInterface.onBookClick(pos);
                    }
                }
            });
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    int pos = getAdapterPosition();
                    if(pos != RecyclerView.NO_POSITION)
                        recyclerViewInterface.onLongBookClick(pos);
                    return false;
                }
            });
        }
    }
}
