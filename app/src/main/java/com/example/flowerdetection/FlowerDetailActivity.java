package com.example.flowerdetection;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.flowerdetection.adapter.FlowerImageAdapter;
import com.example.flowerdetection.db.AppDatabase;
import com.example.flowerdetection.model.Flower;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FlowerDetailActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private ImageView leftArrow, rightArrow;

    private TextView nameTextView;
    private TextView colorsTextView;
    private TextView infoTextView;
    private TextView lightingTextView;
    private TextView temperatureTextView;
    private TextView soilTextView;
    private TextView bloomingTextView;
    private TextView factTextView;
    private TextView funFactTitle;

    private boolean isFactVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flower_detail);

        final long startTime = System.currentTimeMillis();
        final String TAG = "FlowerApp";

        Log.d(TAG, "FlowerDetailActivity started");

        viewPager = findViewById(R.id.imageViewPager);
        leftArrow = findViewById(R.id.leftArrow);
        rightArrow = findViewById(R.id.rightArrow);

        nameTextView = findViewById(R.id.nameTextView);
        colorsTextView = findViewById(R.id.colorsTextView);
        infoTextView = findViewById(R.id.infoTextView);
        lightingTextView = findViewById(R.id.lightingTextView);
        temperatureTextView = findViewById(R.id.temperatureTextView);
        soilTextView = findViewById(R.id.soilTextView);
        bloomingTextView = findViewById(R.id.bloomingTextView);
        factTextView = findViewById(R.id.factTextView);
        funFactTitle = findViewById(R.id.funFactTitle);

        factTextView.setVisibility(View.GONE);
        funFactTitle.setText("Fun Fact ▼");
        funFactTitle.setOnClickListener(v -> {
            isFactVisible = !isFactVisible;
            factTextView.setVisibility(isFactVisible ? View.VISIBLE : View.GONE);
            funFactTitle.setText(isFactVisible ? "Fun Fact ▲" : "Fun Fact ▼");
        });

        int flowerId = getIntent().getIntExtra("flower_id", -1);
        Log.d(TAG, "Loading flower with ID: " + flowerId);

        String lang = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "en");
        String dbName = lang.equals("uk") ? "flowers_info_ukr.db" : "flowers_info.db";

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Flower flower = AppDatabase.getInstance(this, dbName)
                    .flowerDao()
                    .getFlowerById(flowerId);

            if (flower == null) {
                Log.w(TAG, "Flower not found in DB for ID: " + flowerId);
                return;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Flower loaded from DB in " + elapsed + " ms");

            if (elapsed > 5000) {
                Log.w(TAG, "⚠️ Flower loading took more than 5 seconds!");
            }

            runOnUiThread(() -> {
                Log.d(TAG, "Displaying flower info on UI");

                nameTextView.setText(capitalizeFirstLetter(flower.getClassName()));
                colorsTextView.setText(flower.getColor());
                infoTextView.setText(flower.getSize());
                lightingTextView.setText(flower.getLighting());
                temperatureTextView.setText(flower.getTemperature());
                soilTextView.setText(flower.getSoil());
                bloomingTextView.setText(flower.getBlooming());
                factTextView.setText(flower.getFunFact());

                List<String> imagePaths = Arrays.asList(flower.getImageList().split(",\\s*"));
                FlowerImageAdapter adapter = new FlowerImageAdapter(this, imagePaths);
                viewPager.setAdapter(adapter);

                updateArrowVisibility(0, adapter.getItemCount());

                viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        updateArrowVisibility(position, adapter.getItemCount());
                    }
                });

                leftArrow.setOnClickListener(v -> {
                    int currentItem = viewPager.getCurrentItem();
                    if (currentItem > 0) {
                        viewPager.setCurrentItem(currentItem - 1, true);
                    }
                });

                rightArrow.setOnClickListener(v -> {
                    int currentItem = viewPager.getCurrentItem();
                    if (currentItem < adapter.getItemCount() - 1) {
                        viewPager.setCurrentItem(currentItem + 1, true);
                    }
                });
            });
        });
    }


    private void updateArrowVisibility(int position, int total) {
        leftArrow.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        rightArrow.setVisibility(position == total - 1 ? View.INVISIBLE : View.VISIBLE);
    }

    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
