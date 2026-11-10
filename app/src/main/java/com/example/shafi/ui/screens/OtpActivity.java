package com.example.shafi.ui.screens;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.shafi.SystemBars;
import com.example.shafi.core.Resource;
import com.example.shafi.databinding.ActivityOtpBinding;
import com.example.shafi.ui.Entry;
import com.example.shafi.viewmodel.AuthViewModel;

public class OtpActivity extends AppCompatActivity {
    private ActivityOtpBinding binding;
    private AuthViewModel viewModel;
    private String mobile;
    private String purpose;
    private boolean verifying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOtpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this, true);
        mobile = getIntent().getStringExtra("mobile");
        purpose = getIntent().getStringExtra("purpose");
        String debug = getIntent().getStringExtra("debugOtp");
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvOtpHint.setText("Enter the OTP sent to " + mobile);
        if (debug != null && !debug.isEmpty()) {
            binding.tvDebugOtp.setVisibility(View.VISIBLE);
            binding.tvDebugOtp.setText("Development OTP: " + debug + ". This is hidden when OTP_DEBUG is off.");
        }
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        viewModel.result().observe(this, resource -> {
            binding.progress.setVisibility(resource.state == Resource.State.LOADING ? View.VISIBLE : View.GONE);
            binding.btnVerify.setEnabled(resource.state != Resource.State.LOADING);
            if (resource.state == Resource.State.ERROR) {
                binding.tvError.setVisibility(View.VISIBLE);
                binding.tvError.setText(resource.message);
            } else if (resource.state == Resource.State.SUCCESS && verifying && resource.data != null && resource.data.accessToken != null) {
                Entry.afterAuth(this);
            } else if (resource.state == Resource.State.SUCCESS && resource.data != null && resource.data.debugOtp != null) {
                binding.tvDebugOtp.setVisibility(View.VISIBLE);
                binding.tvDebugOtp.setText("Development OTP: " + resource.data.debugOtp);
            }
        });
        binding.btnVerify.setOnClickListener(v -> {
            verifying = true;
            binding.tvError.setVisibility(View.GONE);
            viewModel.verify(mobile, binding.etCode.getText().toString().trim(), purpose);
        });
        binding.tvResend.setOnClickListener(v -> {
            verifying = false;
            viewModel.sendOtp(mobile, purpose);
        });
    }
}
