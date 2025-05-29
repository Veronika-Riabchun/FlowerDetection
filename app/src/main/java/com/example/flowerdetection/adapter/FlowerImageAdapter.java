package com.example.flowerdetection.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flowerdetection.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FlowerImageAdapter extends RecyclerView.Adapter<FlowerImageAdapter.ImageViewHolder> {

    private final Context context;
    private final List<String> imageList;

    public FlowerImageAdapter(Context context, List<String> imageList) {
        this.context = context;
        this.imageList = imageList;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_flower_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        if (imageList == null || imageList.isEmpty()) return;

        String imageName = imageList.get(position).trim();
        try (InputStream is = context.getAssets().open("flowers_images/" + imageName)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            holder.imageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            // fallback
            holder.imageView.setImageResource(R.drawable.placeholder_image);
        }
    }

    @Override
    public int getItemCount() {
        return imageList != null ? imageList.size() : 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}
