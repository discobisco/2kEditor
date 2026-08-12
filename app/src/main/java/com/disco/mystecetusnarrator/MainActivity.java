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
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private final LinkedHashMap<String, DetectionRecord> capturedRecords = new LinkedHashMap<>();
    private boolean selectingSpinner;
    private static final String ALL_DETECTIONS = "All detections";

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom);
            return insets;
        });
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
        binding.clearButton.setOnClickListener(v -> {
            record = new DetectionRecord();
            capturedRecords.clear();
            binding.detectionSpinner.setVisibility(View.GONE);
            binding.formContainer.setVisibility(View.VISIBLE);
            binding.generateButton.setText("Generate narrative");
            fillForm(record);
        });
        binding.generateButton.setOnClickListener(v -> generate());
        binding.historyButton.setOnClickListener(v -> showHistory());
        binding.detectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectingSpinner) return;
                if (binding.formContainer.getVisibility() == View.VISIBLE && record != null) syncFromForm();
                String selected = parent.getItemAtPosition(position).toString();
                if (selected.equals(ALL_DETECTIONS)) {
                    binding.formContainer.setVisibility(View.GONE);
                    binding.generateButton.setText("Generate all narratives separately");
                } else {
                    binding.formContainer.setVisibility(View.VISIBLE);
                    binding.generateButton.setText("Generate narrative");
                    record = capturedRecords.get(selected);
                    if (record != null) fillForm(record);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
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
        List<StructuredOcrExtractor.Page> pages = Collections.synchronizedList(new ArrayList<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(uris.size());
        for (Uri uri : uris) {
            try {
                InputImage image = InputImage.fromFilePath(this, uri);
                recognizer.process(image)
                    .addOnSuccessListener(text -> pages.add(StructuredOcrExtractor.Page.from(text)))
                    .addOnFailureListener(error -> failures.add(error.getMessage() == null ? "Unreadable photo" : error.getMessage()))
                    .addOnCompleteListener(task -> {
                        if (remaining.decrementAndGet() == 0) {
                            recognizer.close();
                            chooseDetectionAndFinish(pages, failures);
                        }
                    });
            } catch (Exception error) {
                failures.add(error.getMessage() == null ? "Unreadable photo" : error.getMessage());
                if (remaining.decrementAndGet() == 0) { recognizer.close(); chooseDetectionAndFinish(pages, failures); }
            }
        }
    }

    private void chooseDetectionAndFinish(List<StructuredOcrExtractor.Page> pages, List<String> failures) {
        runOnUiThread(() -> {
            List<String> ids = StructuredOcrExtractor.findDetectionIds(pages);
            if (ids.isEmpty()) { stopProcessing(); new AlertDialog.Builder(this).setTitle("No detection row found").setMessage("Enter the V-number manually, then select the photos again.").setPositiveButton("OK", null).show(); return; }
            finishAllOcr(pages, failures, ids);
        });
    }

    private void finishAllOcr(List<StructuredOcrExtractor.Page> pages, List<String> failures, List<String> ids) {
        capturedRecords.clear();
        List<String> warnings = new ArrayList<>();
        for (String id : ids) {
            StructuredOcrExtractor.Result result = StructuredOcrExtractor.extract(pages, id);
            capturedRecords.put(id, result.record);
            warnings.addAll(result.warnings);
        }
        List<String> choices = new ArrayList<>();
        choices.add(ALL_DETECTIONS);
        choices.addAll(ids);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
        selectingSpinner = true;
        binding.detectionSpinner.setAdapter(adapter);
        binding.detectionSpinner.setVisibility(View.VISIBLE);
        binding.detectionSpinner.setSelection(ids.size() == 1 ? 1 : 0);
        selectingSpinner = false;
        if (ids.size() == 1) {
            record = capturedRecords.get(ids.get(0));
            fillForm(record);
            binding.formContainer.setVisibility(View.VISIBLE);
            binding.generateButton.setText("Generate narrative");
        } else {
            binding.formContainer.setVisibility(View.GONE);
            binding.generateButton.setText("Generate all narratives separately");
        }
        stopProcessing();
        String message = ids.size() + " separate detection(s) captured: " + String.join(", ", ids) + ".\n\nUse the dropdown to review one detection or generate all separately.";
        if (!failures.isEmpty()) message += "\n\n" + failures.size() + " photo(s) could not be read.";
        new AlertDialog.Builder(this).setTitle("Photo extraction complete").setMessage(message).setPositiveButton("Review", null).show();
    }

    private void finishOcr(List<StructuredOcrExtractor.Page> pages, List<String> failures, String targetId) {
        StructuredOcrExtractor.Result result = StructuredOcrExtractor.extract(pages, targetId);
        mergeNonBlank(record, result.record);
        fillForm(record);
        stopProcessing();
        List<String> messages = new ArrayList<>(result.warnings);
        if (!failures.isEmpty()) messages.add(failures.size() + " photo(s) could not be read.");
        new AlertDialog.Builder(this)
            .setTitle("Photo extraction complete")
            .setMessage(String.join("\n\n", messages))
            .setPositiveButton("Review values", null)
            .show();
    }

    private void stopProcessing() {
        binding.progress.setVisibility(View.GONE);
        binding.generateButton.setEnabled(true);
        binding.reminderText.setText("REMINDER: Make an effort line for the start and end of the detection.");
    }

    private void generate() {
        if (binding.detectionSpinner.getVisibility() == View.VISIBLE && binding.detectionSpinner.getSelectedItem() != null && binding.detectionSpinner.getSelectedItem().toString().equals(ALL_DETECTIONS)) {
            generateAll();
            return;
        }
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

    private void generateAll() {
        StringBuilder output = new StringBuilder();
        List<String> incomplete = new ArrayList<>();
        for (Map.Entry<String, DetectionRecord> item : capturedRecords.entrySet()) {
            List<String> missing = NarrativeGenerator.missingRequired(item.getValue());
            if (!missing.isEmpty()) { incomplete.add(item.getKey() + " (" + missing.size() + " missing required fields)"); continue; }
            if (output.length() > 0) output.append("\n\n");
            output.append(item.getKey()).append("\n").append(NarrativeGenerator.generate(item.getValue()));
        }
        if (!incomplete.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Some detections need review")
                .setMessage(String.join("\n", incomplete) + "\n\nChoose each one from the dropdown and complete its missing values.")
                .setPositiveButton(output.length() == 0 ? "Return" : "Show completed", (d, w) -> { if (output.length() > 0) showResult(output.toString()); }).show();
        } else showResult(output.toString());
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
