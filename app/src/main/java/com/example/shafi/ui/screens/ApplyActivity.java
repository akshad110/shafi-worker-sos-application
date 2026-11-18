package com.example.shafi.ui.screens;

import android.database.Cursor;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.R;
import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityApplyBinding;
import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ApplyActivity extends AppCompatActivity {
    private ActivityApplyBinding binding;
    private WorkerRepository repository;
    private String jobId;
    private int pendingIndex = -1;
    private boolean loaded;
    private final List<Slot> slots = new ArrayList<>();
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::chosen);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityApplyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        jobId = getIntent().getStringExtra("id");
        if (jobId == null || jobId.isEmpty()) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra("title");
        if (title != null && !title.isEmpty()) binding.tvJob.setText(title);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSubmit.setOnClickListener(v -> submit());
        loadRequirements();
    }

    private void loadRequirements() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        binding.btnSubmit.setEnabled(false);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.JobItem job = repository.job(jobId);
                runOnUiThread(() -> showRequirements(job));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    binding.tvDocsHint.setText("The hospital's document list could not be loaded.");
                    fail(error.getMessage());
                });
            }
        });
    }

    private void showRequirements(Dtos.JobItem job) {
        binding.progress.setVisibility(View.GONE);
        loaded = true;
        if (job != null && job.title != null && !job.title.isEmpty()) binding.tvJob.setText(job.title);
        binding.docList.removeAllViews();
        slots.clear();
        List<Dtos.NeededDocument> required = job == null ? null : job.requiredDocuments;
        if (required != null) {
            for (Dtos.NeededDocument document : required) {
                if (document == null || document.name == null || document.name.trim().isEmpty()) continue;
                addSlot(document);
            }
        }
        if (slots.isEmpty()) {
            binding.tvDocsHint.setText("This hospital did not ask for extra documents. You can submit the application.");
        } else {
            binding.tvDocsHint.setText("Upload each document below. JPG, PNG, or PDF, up to 5 MB.");
        }
        binding.btnSubmit.setEnabled(true);
    }

    private void addSlot(Dtos.NeededDocument document) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_needed_doc, binding.docList, false);
        Slot slot = new Slot();
        slot.name = document.name.trim();
        slot.fileLabel = row.findViewById(R.id.tv_file);
        TextView name = row.findViewById(R.id.tv_name);
        TextView instruction = row.findViewById(R.id.tv_instruction);
        MaterialButton upload = row.findViewById(R.id.btn_upload);
        name.setText(slot.name);
        String note = document.instruction == null ? "" : document.instruction.trim();
        if (note.isEmpty()) {
            instruction.setVisibility(View.GONE);
        } else {
            instruction.setText(note);
        }
        int index = slots.size();
        upload.setOnClickListener(v -> {
            pendingIndex = index;
            picker.launch(new String[]{"image/jpeg", "image/png", "application/pdf"});
        });
        slots.add(slot);
        binding.docList.addView(row);
    }

    private void chosen(Uri uri) {
        if (uri == null || pendingIndex < 0 || pendingIndex >= slots.size()) return;
        Slot slot = slots.get(pendingIndex);
        slot.uri = uri;
        slot.fileName = nameOf(uri);
        slot.fileLabel.setText(slot.fileName);
        slot.fileLabel.setTextColor(getColor(R.color.emerald_deep));
        binding.tvError.setVisibility(View.GONE);
    }

    private void submit() {
        if (!loaded) return;
        for (Slot slot : slots) {
            if (slot.uri == null) {
                fail("Upload " + slot.name + " before submitting.");
                return;
            }
        }
        binding.btnSubmit.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        AppExecutors.IO.execute(() -> {
            try {
                for (Slot slot : slots) {
                    byte[] bytes = read(slot.uri);
                    if (bytes.length > 5 * 1024 * 1024) {
                        throw new IllegalStateException(slot.name + " must be 5 MB or smaller");
                    }
                    String mime = getContentResolver().getType(slot.uri);
                    if (mime == null || mime.isEmpty()) {
                        mime = slot.fileName.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
                    }
                    String type = slot.name.length() > 80 ? slot.name.substring(0, 80) : slot.name;
                    repository.upload(type, slot.fileName.isEmpty() ? "document" : slot.fileName, mime, bytes);
                }
                Dtos.ApplicationItem application = repository.apply(jobId);
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle("Application sent")
                            .setMessage("Reference " + application.reference + ". The hospital can open the documents you uploaded.")
                            .setPositiveButton("Done", (DialogInterface dialog, int which) -> finish())
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.btnSubmit.setEnabled(true);
                    fail(error.getMessage());
                });
            }
        });
    }

    private byte[] read(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Couldn't read that file");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private String nameOf(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return "document";
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message == null || message.isEmpty() ? "Couldn't send the application." : message);
    }

    private static final class Slot {
        String name;
        Uri uri;
        String fileName = "";
        TextView fileLabel;
    }
}
