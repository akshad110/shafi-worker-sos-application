package com.example.shafi;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.shafi.core.Resource;
import com.example.shafi.ui.screens.OnboardingActivity;
import com.example.shafi.viewmodel.AuthViewModel;
import com.google.android.material.button.MaterialButton;

public class Signup extends AppCompatActivity {
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        SystemBars.apply(this, true);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        EditText name = findViewById(R.id.et_signup_name);
        EditText email = findViewById(R.id.et_signup_email);
        EditText role = findViewById(R.id.et_signup_role);
        EditText password = findViewById(R.id.et_signup_password);
        EditText confirm = findViewById(R.id.et_signup_confirm);
        CheckBox consent = findViewById(R.id.cb_consent);
        TextView error = findViewById(R.id.tv_signup_error);
        TextView loginLink = findViewById(R.id.tv_login_link);
        loginLink.setPaintFlags(loginLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        MaterialButton button = findViewById(R.id.btn_signup);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        viewModel.result().observe(this, resource -> {
            button.setEnabled(resource.state != Resource.State.LOADING);
            if (resource.state == Resource.State.ERROR) {
                error.setText(resource.message);
                error.setVisibility(View.VISIBLE);
            } else if (resource.state == Resource.State.SUCCESS && resource.data != null && resource.data.accessToken != null) {
                Intent intent = new Intent(this, OnboardingActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
        button.setOnClickListener(v -> {
            String nameValue = name.getText().toString().trim();
            String roleValue = role.getText().toString().trim();
            String emailValue = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            String confirmValue = confirm.getText().toString();
            if (nameValue.length() < 2 || roleValue.length() < 2 || !Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                show(error, "Enter your name, preferred healthcare role, and a valid email.");
                return;
            }
            if (passwordValue.length() < 8) {
                show(error, "Use a password of at least 8 characters.");
                return;
            }
            if (!passwordValue.equals(confirmValue)) {
                show(error, "Password and confirm password must match.");
                return;
            }
            if (!consent.isChecked()) {
                show(error, "Accept the terms and privacy policy to create an account.");
                return;
            }
            error.setVisibility(View.GONE);
            viewModel.register(nameValue, emailValue, passwordValue, roleValue);
        });
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, login.class));
            finish();
        });
        keepVisible(name);
        keepVisible(role);
        keepVisible(email);
        keepVisible(password);
        keepVisible(confirm);
    }

    private void show(TextView error, String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void keepVisible(View field) {
        ScrollView scroll = findViewById(R.id.main);
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            v.postDelayed(() -> {
                int y = 0;
                View node = v;
                View content = scroll.getChildAt(0);
                while (node != null && node != content && node.getParent() instanceof View) {
                    y += node.getTop();
                    node = (View) node.getParent();
                }
                scroll.smoothScrollTo(0, Math.max(0, y - 48));
            }, 240);
        });
    }
}
