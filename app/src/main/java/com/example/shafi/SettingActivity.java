package com.example.shafi;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.core.AppExecutors;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.HashMap;
import java.util.Map;

public class SettingActivity extends AppCompatActivity {
    private boolean ready;
    private SwitchMaterial jobs;
    private SwitchMaterial offers;
    private SwitchMaterial shifts;
    private SwitchMaterial payments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        SystemBars.apply(this);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        TextView tvUser = findViewById(R.id.tv_setting_user);
        tvUser.setText(SessionManager.getUsername(this));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        jobs = findViewById(R.id.switch_jobs);
        offers = findViewById(R.id.switch_offers);
        shifts = findViewById(R.id.switch_shifts);
        payments = findViewById(R.id.switch_payments);
        CompoundButton.OnCheckedChangeListener listener = (button, checked) -> {
            if (ready) save();
        };
        jobs.setOnCheckedChangeListener(listener);
        offers.setOnCheckedChangeListener(listener);
        shifts.setOnCheckedChangeListener(listener);
        payments.setOnCheckedChangeListener(listener);
        load();
    }

    private void load() {
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.ProfileResponse profile = new WorkerRepository(this).profile();
                runOnUiThread(() -> show(profile == null ? null : profile.preferences));
            } catch (Exception error) {
                runOnUiThread(() -> show(null));
            }
        });
    }

    private void show(Dtos.Preferences preferences) {
        ready = false;
        jobs.setChecked(preferences == null || preferences.jobs);
        offers.setChecked(preferences == null || preferences.offers);
        shifts.setChecked(preferences == null || preferences.shifts);
        payments.setChecked(preferences == null || preferences.payments);
        ready = true;
    }

    private void save() {
        Map<String, Object> body = new HashMap<>();
        body.put("jobs", jobs.isChecked());
        body.put("offers", offers.isChecked());
        body.put("shifts", shifts.isChecked());
        body.put("payments", payments.isChecked());
        AppExecutors.IO.execute(() -> {
            try {
                new WorkerRepository(this).preferences(body);
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
}
