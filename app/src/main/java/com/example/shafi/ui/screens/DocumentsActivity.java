package com.example.shafi.ui.screens;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityListBinding;
import com.example.shafi.ui.ActivityAdapter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DocumentsActivity extends AppCompatActivity {
    private ActivityListBinding binding;
    private WorkerRepository repository;
    private ActivityAdapter adapter;
    private String type = "QUALIFICATION";
    private final ActivityResultLauncher<String> picker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::upload);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        binding.tvTitle.setText("Documents");
        binding.tvSummary.setText("Certificates stay private. Verification is pending until an admin reviews them.");
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAction.setVisibility(View.VISIBLE);
        binding.btnAction.setText("Upload qualification");
        binding.btnAction.setOnClickListener(v -> chooseType());
        adapter = new ActivityAdapter(item -> open(item.id));
        binding.rvItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvItems.setAdapter(adapter);
        load();
    }

    private void chooseType() {
        String[] types = {"QUALIFICATION", "REGISTRATION", "IDENTITY", "CERTIFICATION", "OTHER"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Document type")
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, types), (dialog, which) -> {
                    type = types[which];
                    picker.launch("*/*");
                })
                .show();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                List<Dtos.DocItem> docs = repository.documents();
                runOnUiThread(() -> show(docs, null));
            } catch (Exception error) {
                runOnUiThread(() -> show(null, error.getMessage()));
            }
        });
    }

    private void show(List<Dtos.DocItem> docs, String error) {
        binding.progress.setVisibility(View.GONE);
        if (error != null) {
            binding.tvState.setVisibility(View.VISIBLE);
            binding.tvState.setText(error);
            return;
        }
        List<Dtos.ActivityItem> rows = new ArrayList<>();
        if (docs != null) {
            for (Dtos.DocItem doc : docs) {
                Dtos.ActivityItem row = new Dtos.ActivityItem();
                row.id = doc.id;
                row.title = doc.fileName;
                row.body = doc.type + " · " + Format.status(doc.status)
                        + (doc.rejectionReason == null || doc.rejectionReason.isEmpty() ? "" : " · " + doc.rejectionReason);
                row.read = !"PENDING".equals(doc.status);
                rows.add(row);
            }
        }
        adapter.submitList(rows);
        binding.tvState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvState.setText("No documents uploaded.");
    }

    private void upload(Uri uri) {
        if (uri == null) return;
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while (stream != null && (read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
                if (stream != null) stream.close();
                byte[] bytes = output.toByteArray();
                if (bytes.length > 5 * 1024 * 1024) throw new IllegalArgumentException("Files must be 5 MB or smaller");
                String mime = getContentResolver().getType(uri);
                if (mime == null) mime = "application/pdf";
                repository.upload(type, "document", mime, bytes);
                runOnUiThread(this::load);
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void open(String id) {
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                byte[] bytes = repository.downloadDocument(id);
                java.io.File file = new java.io.File(getCacheDir(), "document-" + id);
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    output.write(bytes);
                }
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", file);
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/octet-stream");
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    startActivity(android.content.Intent.createChooser(intent, "Open document"));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
