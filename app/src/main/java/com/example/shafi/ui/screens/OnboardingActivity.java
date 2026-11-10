package com.example.shafi.ui.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.MainActivity;
import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityOnboardingBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {
    private static final String[] SHIFTS = {"Day", "Night", "Day and night"};
    private static final String[] DAYS = {"Weekdays", "Weekends", "All days"};
    private static final String[] DEPARTMENTS = {
            "ICU", "Emergency", "General Ward", "Operation Theatre", "Laboratory", "Radiology", "Other"
    };

    private ActivityOnboardingBinding binding;
    private WorkerRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this, true);
        repository = new WorkerRepository(this);
        dropdown(binding.etShift, SHIFTS);
        dropdown(binding.etDays, DAYS);
        dropdown(binding.etDepartments, DEPARTMENTS);
        binding.btnBack.setOnClickListener(v -> openMain());
        binding.btnSave.setOnClickListener(v -> save(false));
        binding.btnSubmit.setOnClickListener(v -> {
            if (!readyToSubmit()) return;
            save(true);
        });
        load();
    }

    private void dropdown(AutoCompleteTextView view, String[] items) {
        view.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
        view.setKeyListener(null);
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) view.showDropDown();
        });
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.ProfileResponse profile = repository.profile();
                runOnUiThread(() -> fill(profile));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    showError(error.getMessage());
                });
            }
        });
    }

    private void fill(Dtos.ProfileResponse response) {
        binding.progress.setVisibility(View.GONE);
        if (response == null) return;
        if (response.user != null && response.user.mobile != null && !response.user.mobile.isEmpty()) {
            binding.etMobile.setText(response.user.mobile);
        }
        if (response.profile == null) return;
        Dtos.WorkerProfile profile = response.profile;
        binding.etRole.setText(profile.healthcareRole);
        binding.etSpecialization.setText(profile.specialization);
        binding.etQualifications.setText(join(profile.qualifications));
        binding.etSkills.setText(join(profile.skills));
        if (profile.experienceYears != null) binding.etExperience.setText(String.valueOf(profile.experienceYears));
        binding.etDepartments.setText(first(profile.preferredDepartments), false);
        binding.etShift.setText(shiftLabel(profile.preferredShifts), false);
        binding.etCity.setText(profile.city);
        binding.etAddress.setText(profile.address);
        binding.etEmergencyName.setText(profile.emergencyContactName);
        binding.etEmergencyPhone.setText(profile.emergencyContactPhone);
    }

    private boolean readyToSubmit() {
        if (text(binding.etAddress).isEmpty() || text(binding.etCity).isEmpty()
                || text(binding.etQualifications).isEmpty() || text(binding.etShift).isEmpty()) {
            showError("Add your current address, city, qualification, and preferred shift before submitting.");
            return false;
        }
        binding.tvError.setVisibility(View.GONE);
        return true;
    }

    private void save(boolean submit) {
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        List<String> shifts = shiftValues(text(binding.etShift));
        Map<String, Object> body = new HashMap<>();
        body.put("healthcareRole", text(binding.etRole));
        body.put("specialization", text(binding.etSpecialization));
        body.put("qualifications", split(text(binding.etQualifications)));
        body.put("skills", split(text(binding.etSkills)));
        body.put("preferredDepartments", split(text(binding.etDepartments)));
        body.put("preferredShifts", shifts);
        body.put("city", text(binding.etCity));
        body.put("address", text(binding.etAddress));
        body.put("preferredLocations", split(text(binding.etCity)));
        body.put("emergencyContactName", text(binding.etEmergencyName));
        body.put("emergencyContactPhone", text(binding.etEmergencyPhone));
        String mobile = text(binding.etMobile);
        if (!mobile.isEmpty()) body.put("mobile", mobile);
        String years = text(binding.etExperience);
        if (!years.isEmpty()) body.put("experienceYears", Integer.parseInt(years));
        if (submit) body.put("onboardingSubmitted", true);
        Map<String, Object> availability = new HashMap<>();
        availability.put("preferredShifts", shifts);
        availability.put("preferredLocations", split(text(binding.etCity)));
        availability.put("weeklyAvailability", weekly(text(binding.etDays), shifts));
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.ProfileResponse saved = repository.updateProfile(body);
                repository.updateAvailability(availability);
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    if (submit && saved != null && saved.canApply) openMain();
                    else if (submit && saved != null && !saved.canApply) showError("Still needed: " + missing(saved));
                    else openMain();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    showError(error.getMessage());
                });
            }
        });
    }

    private List<Map<String, Object>> weekly(String days, List<String> shifts) {
        int[] selected;
        if ("Weekends".equals(days)) selected = new int[]{0, 6};
        else if ("All days".equals(days)) selected = new int[]{0, 1, 2, 3, 4, 5, 6};
        else selected = new int[]{1, 2, 3, 4, 5};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int day : selected) {
            Map<String, Object> row = new HashMap<>();
            row.put("day", day);
            row.put("shifts", shifts);
            rows.add(row);
        }
        return rows;
    }

    private List<String> shiftValues(String label) {
        if ("Night".equals(label)) return Arrays.asList("NIGHT");
        if ("Day and night".equals(label)) return Arrays.asList("DAY", "NIGHT");
        if ("Day".equals(label)) return Arrays.asList("DAY");
        return new ArrayList<>();
    }

    private String shiftLabel(List<String> shifts) {
        if (shifts == null || shifts.isEmpty()) return "";
        boolean day = shifts.contains("DAY");
        boolean night = shifts.contains("NIGHT");
        if (day && night) return "Day and night";
        if (night) return "Night";
        if (day) return "Day";
        return "";
    }

    private String first(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.get(0);
    }

    private String missing(Dtos.ProfileResponse saved) {
        if (saved.missing == null || saved.missing.isEmpty()) return "required profile details";
        StringBuilder builder = new StringBuilder();
        for (String item : saved.missing) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(item);
        }
        return builder.toString();
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message == null || message.isEmpty() ? "Could not save your profile." : message);
    }

    private String text(android.widget.TextView field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private List<String> split(String value) {
        List<String> items = new ArrayList<>();
        if (value.isEmpty()) return items;
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) items.add(trimmed);
        }
        return items;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(value);
        }
        return builder.toString();
    }
}
