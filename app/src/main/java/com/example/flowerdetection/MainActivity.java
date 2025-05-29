package com.example.flowerdetection;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

import android.graphics.Typeface;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final String LANGUAGE_KEY = "language";

    private ImageView imageView;
    private TextView resultView;
    private String currentPhotoPath;
    private List<String> labels;
    private LinearLayout classButtonContainer;
    private Set<Integer> detectedClassIds = new HashSet<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase, getSavedLanguage(newBase)));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        resultView = findViewById(R.id.resultView);
        classButtonContainer = findViewById(R.id.classButtonContainer);

        Button captureButton = findViewById(R.id.captureButton);
        Button galleryButton = findViewById(R.id.galleryButton);
        Button languageSwitchButton = findViewById(R.id.languageSwitchButton);

        captureButton.setOnClickListener(v -> dispatchTakePictureIntent());
        galleryButton.setOnClickListener(v -> dispatchChoosePictureIntent());

        copyModelFromAssets("best.onnx");

        String lang = getSavedLanguage(this);
        labels = loadLabels(lang.equals("uk") ? "labels_ukr.txt" : "labels.txt");

        resultView.setVisibility(View.GONE);

        currentPhotoPath = getIntent().getStringExtra("image_path");
        ArrayList<Integer> classIdsFromIntent = getIntent().getIntegerArrayListExtra("detected_class_ids");
        if (currentPhotoPath != null && classIdsFromIntent != null && !classIdsFromIntent.isEmpty()) {
            detectedClassIds.addAll(classIdsFromIntent);
            File imgFile = new File(currentPhotoPath);
            if (imgFile.exists()) {
                Bitmap originalBitmap = BitmapFactory.decodeFile(currentPhotoPath);
                Bitmap rotatedBitmap = originalBitmap;
                try {
                    rotatedBitmap = rotateImageIfRequired(originalBitmap, currentPhotoPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                final Bitmap finalBitmap = rotatedBitmap;
                imageView.setImageBitmap(finalBitmap);
                resultView.setVisibility(View.VISIBLE);
                new Thread(() -> runLocalInference(finalBitmap)).start();
            }
        }

        languageSwitchButton.setOnClickListener(v -> {
            Locale current = getResources().getConfiguration().getLocales().get(0);
            String newLang = current.getLanguage().equals("uk") ? "en" : "uk";
            saveLanguage(newLang);

            Intent refresh = new Intent(MainActivity.this, MainActivity.class);
            refresh.putExtra("image_path", currentPhotoPath);
            refresh.putIntegerArrayListExtra("detected_class_ids", new ArrayList<>(detectedClassIds));
            startActivity(refresh);
            finish();
        });
    }


    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile;
            try {
                photoFile = createImageFile();
                Uri photoURI = FileProvider.getUriForFile(this, "com.example.flowerdetection.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void dispatchChoosePictureIntent() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhotoIntent, REQUEST_IMAGE_PICK);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bitmap = null;
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            File imgFile = new File(currentPhotoPath);
            if (imgFile.exists()) {
                bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                try {
                    bitmap = rotateImageIfRequired(bitmap, currentPhotoPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                    bitmap = BitmapFactory.decodeStream(imageStream);

                    File imageFile = createImageFile();
                    try (FileOutputStream out = new FileOutputStream(imageFile)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                        currentPhotoPath = imageFile.getAbsolutePath();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (bitmap != null) {
            final Bitmap bitmapFinal = bitmap;
            imageView.setImageBitmap(bitmapFinal);
            resultView.setVisibility(View.VISIBLE);
            resultView.setText(getString(R.string.recognizing));
            new Thread(() -> runLocalInference(bitmapFinal)).start();
        }
    }

    private Bitmap rotateImageIfRequired(Bitmap img, String imagePath) throws IOException {
        ExifInterface ei = new ExifInterface(imagePath);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180: return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270: return rotateImage(img, 270);
            default: return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
    }

    private void runLocalInference(Bitmap bitmap) {
        final String TAG = "FlowerApp";
        long startTime = System.currentTimeMillis();

        Log.d(TAG, "🧠 Starting local inference...");

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
        float[] inputData = bitmapToFloatArray(resized);

        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession session = env.createSession(new File(getFilesDir(), "best.onnx").getAbsolutePath(), new OrtSession.SessionOptions());

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), new long[]{1, 3, 640, 640});
            String inputName = session.getInputNames().iterator().next();
            OrtSession.Result results = session.run(Collections.singletonMap(inputName, inputTensor));

            float[][][] output = (float[][][]) results.get(0).getValue();
            List<Detection> detections = postprocessYOLO(output, bitmap.getWidth(), bitmap.getHeight());
            Bitmap annotated = drawDetections(bitmap, detections);

            detectedClassIds.clear();
            for (Detection d : detections) detectedClassIds.add(d.classId);

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "✅ Inference completed in " + elapsed + " ms");

            if (elapsed > 5000) {
                Log.w(TAG, "⚠️ Inference time exceeded 5 seconds!");
            }

            runOnUiThread(() -> {
                imageView.setImageBitmap(annotated);
                classButtonContainer.removeAllViews();
                if (detectedClassIds.isEmpty()) {
                    resultView.setText(getString(R.string.no_flowers));
                    Log.d(TAG, "🚫 No flowers detected.");
                } else {
                    resultView.setText(getString(R.string.detected_flowers));
                    Log.d(TAG, "🌼 Detected flower class IDs: " + detectedClassIds);

                    for (int id : detectedClassIds) {
                        String className = labels.get(id);
                        Button classButton = new Button(this);
                        classButton.setText(capitalizeFirstLetter(className));
                        classButton.setTextColor(Color.parseColor("#6A0572"));
                        classButton.setBackground(ContextCompat.getDrawable(this, R.drawable.rounded_class_button));
                        classButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                        classButton.setPadding(50, 25, 50, 25);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0, 12, 0, 12);
                        classButton.setLayoutParams(params);
                        classButton.setAllCaps(false);
                        classButton.setTextSize(16);
                        classButton.setOnClickListener(v -> {
                            Intent intent = new Intent(MainActivity.this, FlowerDetailActivity.class);
                            intent.putExtra("flower_name", className);
                            intent.putExtra("flower_id", id);
                            startActivity(intent);
                        });
                        classButtonContainer.addView(classButton);
                    }
                }
            });

            session.close();
            env.close();
        } catch (Exception e) {
            Log.e(TAG, "❌ Inference error: " + e.getMessage(), e);
            runOnUiThread(() -> resultView.setText("Помилка інференції: " + e.getMessage()));
        }
    }


    private float[] bitmapToFloatArray(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        float[] data = new float[3 * width * height];
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;
                int index = y * width + x;
                data[index] = r;
                data[index + width * height] = g;
                data[index + 2 * width * height] = b;
            }
        return data;
    }

    private List<Detection> postprocessYOLO(float[][][] output, int origWidth, int origHeight) {
        List<Detection> detections = new ArrayList<>();
        float[][] tensor = output[0];
        int numClasses = tensor.length - 4;
        for (int i = 0; i < tensor[0].length; i++) {
            float x = tensor[0][i] * origWidth / 640f;
            float y = tensor[1][i] * origHeight / 640f;
            float w = tensor[2][i] * origWidth / 640f;
            float h = tensor[3][i] * origHeight / 640f;
            int bestClass = -1;
            float bestScore = 0f;
            for (int c = 0; c < numClasses; c++) {
                float score = tensor[4 + c][i];
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c;
                }
            }
            if (bestScore > 0.2f) {
                int x1 = Math.max(0, (int)(x - w/2)), y1 = Math.max(0, (int)(y - h/2));
                int x2 = Math.min(origWidth, (int)(x + w/2)), y2 = Math.min(origHeight, (int)(y + h/2));
                detections.add(new Detection(new Rect(x1, y1, x2, y2), bestScore, bestClass));
            }
        }
        return nonMaximumSuppression(detections, 0.4f);
    }

    private List<Detection> nonMaximumSuppression(List<Detection> detections, float iouThreshold) {
        List<Detection> result = new ArrayList<>();
        detections.sort((d1, d2) -> Float.compare(d2.confidence, d1.confidence));
        while (!detections.isEmpty()) {
            Detection best = detections.remove(0);
            result.add(best);
            detections.removeIf(d -> computeIoU(best.box, d.box) > iouThreshold);
        }
        return result;
    }

    private float computeIoU(Rect a, Rect b) {
        int x1 = Math.max(a.left, b.left), y1 = Math.max(a.top, b.top);
        int x2 = Math.min(a.right, b.right), y2 = Math.min(a.bottom, b.bottom);
        int interArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int unionArea = a.width() * a.height() + b.width() * b.height() - interArea;
        return (float) interArea / unionArea;
    }

    private Bitmap drawDetections(Bitmap bitmap, List<Detection> detections) {
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        float scale = (float) Math.sqrt(w * h) / 1000f;
        Paint boxPaint = new Paint(); boxPaint.setColor(Color.CYAN); boxPaint.setStyle(Paint.Style.STROKE); boxPaint.setStrokeWidth(Math.max(3f, 3f * scale));
        Paint textPaint = new Paint(); textPaint.setColor(Color.WHITE); textPaint.setTextSize(Math.max(15f, 30f * scale)); textPaint.setFakeBoldText(true); textPaint.setAntiAlias(true);
        Paint bgPaint = new Paint(); bgPaint.setColor(Color.argb(140, 0, 0, 0)); bgPaint.setStyle(Paint.Style.FILL);

        for (Detection d : detections) {
            canvas.drawRoundRect(new android.graphics.RectF(d.box), 20f * scale, 20f * scale, boxPaint);
            String label = d.classId >= 0 && d.classId < labels.size()
                    ? labels.get(d.classId) + " (" + String.format(Locale.US, "%.3f", d.confidence) + ")"
                    : "Class " + d.classId + " (" + String.format(Locale.US, "%.3f", d.confidence) + ")";
            float textWidth = textPaint.measureText(label);
            float textHeight = textPaint.getFontMetrics().bottom - textPaint.getFontMetrics().top;
            float x = d.box.left, y = Math.max(d.box.top - 10, textHeight);
            canvas.drawRect(x, y - textHeight, x + textWidth, y + 5, bgPaint);
            canvas.drawText(label, x, y, textPaint);
        }

        return mutable;
    }

    private static class Detection {
        Rect box; float confidence; int classId;
        Detection(Rect box, float confidence, int classId) {
            this.box = box;
            this.confidence = confidence;
            this.classId = classId;
        }
    }

    private List<String> loadLabels(String filename) {
        List<String> labelList = new ArrayList<>();
        try (InputStream is = getAssets().open(filename)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            for (String line : new String(buffer).split("\n")) labelList.add(line.trim());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return labelList;
    }

    private void copyModelFromAssets(String modelFileName) {
        File modelFile = new File(getFilesDir(), modelFileName);
        if (modelFile.exists()) return;
        try (InputStream is = getAssets().open(modelFileName);
             FileOutputStream fos = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveLanguage(String language) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString(LANGUAGE_KEY, language).apply();
    }

    private String getSavedLanguage(Context context) {
        return context.getSharedPreferences("settings", MODE_PRIVATE).getString(LANGUAGE_KEY, "en");
    }

    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
