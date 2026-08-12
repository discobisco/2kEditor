package com.disco.mystecetusnarrator;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.disco.mystecetusnarrator.databinding.ActivityMainBinding;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private final LinkedHashMap<String, EditText> inputs = new LinkedHashMap<>();
    private DetectionRecord record = new DetectionRecord();
    private HistoryStore history;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<String> choosePhotos = registerForActivityResult(
        new ActivityResultContracts.GetMultipleContents(), uris -> { if (uris != null && !uris.isEmpty()) processImages(uris); });

    private final ActivityResultLauncher<Uri> takePhoto = registerForActivityResult(
        new ActivityResultContracts.TakePicture(), ok -> { if (ok && pendingCameraUri != null) processImages(Collections.singletonList(pendingCameraUri)); });

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> { if (granted) launchCamera(); else toast("Camera permission was not granted."); });

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        history = new HistoryStore(this);
        buildForm();
        bindActions();
    }

    private void buildForm() {
        for (Map.Entry<String, String> field : DetectionRecord.LABELS.entrySet()) {
            TextInputLayout wrapper = new TextInputLayout(this);
            wrapper.setHint(field.getValue());
            wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            wrapper.setPadding(0, 5, 0, 5);
            EditText edit = new EditText(this);
            edit.setSingleLine(!field.getKey().equals("rawOcr") && !field.getKey().equals("behaviors") && !field.getKey().equals("pilingStatus"));
            edit.setMinLines(field.getKey().equals("rawOcr") ? 4 : 1);
            wrapper.addView(edit);
            binding.formContainer.addView(wrapper);
            inputs.put(field.getKey(), edit);
        }
        fillForm(record);
    }

    private void bindActions() {
        binding.photoButton.setOnClickListener(v -> choosePhotos.launch("image/*"));
        binding.cameraButton.setOnClickListener(v -> requestCamera());
        binding.clearButton.setOnClickListener(v -> { record = new DetectionRecord(); fillForm(record); });
        binding.generateButton.setOnClickListener(v -> generate());
        binding.historyButton.setOnClickListener(v -> showHistory());
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "mystecetus_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingCameraUri == null) toast("Could not create a camera image.");
        else takePhoto.launch(pendingCameraUri);
    }

    private void processImages(List<Uri> uris) {
        syncFromForm();
        binding.progress.setVisibility(View.VISIBLE);
        binding.progress.setIndeterminate(true);
        binding.generateButton.setEnabled(false);
        binding.reminderText.setText("PROCESSING PHOTOS — REMINDER: Make an effort line for the start and end of the detection.");

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        StringBuilder allText = new StringBuilder(record.get("rawOcr"));
        List<String> failures = new ArrayList<>();
        AtomicInteger remaining = new AtomicInteger(uris.size());
        for (Uri uri : uris) {
            try {
                InputImage image = InputImage.fromFilePath(this, uri);
                recognizer.process(image)
                    .addOnSuccessListener(text -> allText.append("\n\n--- PHOTO ---\n").append(text.getText()))
                    .addOnFailureListener(error -> failures.add(error.getMessage() == null ? "Unreadable photo" : error.getMessage()))
                    .addOnCompleteListener(task -> {
                        if (remaining.decrementAndGet() == 0) {
                            recognizer.close();
                            finishOcr(allText.toString(), failures);
                        }
                    });
            } catch (Exception error) {
                failures.add(error.getMessage() == null ? "Unreadable photo" : error.getMessage());
                if (remaining.decrementAndGet() == 0) { recognizer.close(); finishOcr(allText.toString(), failures); }
            }
        }
    }

    private void finishOcr(String rawText, List<String> failures) {
        OcrFieldExtractor.Result result = OcrFieldExtractor.extract(rawText);
        mergeNonBlank(record, result.record);
        fillForm(record);
        binding.progress.setVisibility(View.GONE);
        binding.generateButton.setEnabled(true);
        binding.reminderText.setText("REMINDER: Make an effort line for the start and end of the detection.");
        List<String> messages = new ArrayList<>(result.warnings);
        if (!failures.isEmpty()) messages.add(failures.size() + " photo(s) could not be read.");
        new AlertDialog.Builder(this)
            .setTitle("Photo extraction complete")
            .setMessage(String.join("\n\n", messages))
            .setPositiveButton("Review values", null)
            .show();
    }

    private void generate() {
        syncFromForm();
        List<String> missing = NarrativeGenerator.missingRequired(record);
        if (!missing.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Required values are missing")
                .setMessage(String.join("\n", missing)).setPositiveButton("Return to form", null).show();
            return;
        }
        binding.reminderText.setText("GENERATING — REMINDER: Make an effort line for the start and end of the detection.");
        AlertDialog processing = new AlertDialog.Builder(this)
            .setTitle("Generating narrative")
            .setMessage("REMINDER: Make an effort line for the start and end of the detection.")
            .setCancelable(false)
            .create();
        processing.show();
        new Thread(() -> {
            String narrative = NarrativeGenerator.generate(record);
            runOnUiThread(() -> {
                processing.dismiss();
                binding.reminderText.setText("REMINDER: Make an effort line for the start and end of the detection.");
                showResult(narrative);
            });
        }).start();
    }

    private void showResult(String narrative) {
        EditText result = new EditText(this);
        result.setText(narrative);
        result.setMinLines(8);
        result.setPadding(36, 18, 36, 18);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(record.get("vNumber").isEmpty() ? "Generated narrative" : record.get("vNumber") + " narrative")
            .setMessage("REMINDER: Make an effort line for the start and end of the detection.")
            .setView(result)
            .setPositiveButton("Save", null)
            .setNeutralButton("Copy", null)
            .setNegativeButton("Share", null)
            .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                history.save(record); toast("Detection saved.");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Mystecetus narrative", result.getText()));
                toast("Narrative copied.");
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result.getText().toString());
                startActivity(Intent.createChooser(share, "Share narrative"));
            });
        });
        dialog.show();
    }

    private void showHistory() {
        List<DetectionRecord> records = history.load();
        if (records.isEmpty()) { toast("No saved detections."); return; }
        String[] labels = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            DetectionRecord item = records.get(i);
            labels[i] = (item.get("vNumber").isEmpty() ? "Unnumbered" : item.get("vNumber")) + " — " + item.get("species");
        }
        new AlertDialog.Builder(this).setTitle("Saved detections").setItems(labels, (d, which) -> {
            record = records.get(which); fillForm(record);
        }).setNegativeButton("Close", null).show();
    }

    private void syncFromForm() { for (Map.Entry<String, EditText> item : inputs.entrySet()) record.set(item.getKey(), item.getValue().getText().toString()); }
    private void fillForm(DetectionRecord value) { for (Map.Entry<String, EditText> item : inputs.entrySet()) item.getValue().setText(value.get(item.getKey())); }
    private void mergeNonBlank(DetectionRecord target, DetectionRecord source) { for (String key : DetectionRecord.LABELS.keySet()) if (target.get(key).isEmpty() && !source.get(key).isEmpty()) target.set(key, source.get(key)); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
