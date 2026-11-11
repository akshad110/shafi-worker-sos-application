package com.example.shafi.ui.screens;

import android.database.Cursor;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityApplyBinding;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ApplyActivity extends AppCompatActivity {
    private static final String[] LABELS = {"Qualification", "Registration", "Certificate", "Identity"};
    private static final String[] TYPES = {"QUALIFICATION", "REGISTRATION", "CERTIFICATION", "IDENTITY"};

    private ActivityApplyBinding binding;
    private WorkerRepository repository;
    private String jobId;
    private Uri fileUri;
    private String fileName = "";
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
        binding.spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LABELS));
        binding.btnFile.setOnClickListener(v -> picker.launch(new String[]{"image/jpeg", "image/png", "application/pdf"}));
        binding.btnSubmit.setOnClickListener(v -> submit());
    }

    private void chosen(Uri uri) {
        if (uri == null) return;
        fileUri = uri;
        fileName = nameOf(uri);
        binding.tvFile.setText(fileName);
        binding.tvError.setVisibility(View.GONE);
    }

    private void submit() {
        if (fileUri == null) {
            fail("Choose an image or PDF to send with this application.");
            return;
        }
        String type = TYPES[binding.spType.getSelectedItemPosition()];
        binding.btnSubmit.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        AppExecutors.IO.execute(() -> {
            try {
                byte[] bytes = read(fileUri);
                if (bytes.length > 5 * 1024 * 1024) throw new IllegalStateException("Files must be 5 MB or smaller");
                String mime = getContentResolver().getType(fileUri);
                if (mime == null || mime.isEmpty()) mime = fileName.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
                repository.upload(type, fileName.isEmpty() ? "document" : fileName, mime, bytes);
                Dtos.ApplicationItem application = repository.apply(jobId);
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle("Application sent")
                            .setMessage("Reference " + application.reference + ". The hospital can open the document you uploaded.")
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
}
