package com.example.shafi;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.graphics.Paint;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.shafi.core.Resource;
import com.example.shafi.ui.Entry;
import com.example.shafi.viewmodel.AuthViewModel;

public class login extends AppCompatActivity {
    private EditText etUsername;
    private EditText etPassword;
    private TextView tvError;
    private Button btnLogin;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionManager.isLoggedIn(this)) {
            Entry.afterAuth(this);
            return;
        }
        setContentView(R.layout.activity_login);
        SystemBars.apply(this, true);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etUsername = findViewById(R.id.et_login_username);
        etPassword = findViewById(R.id.et_login_password);
        tvError = findViewById(R.id.tv_login_error);
        btnLogin = findViewById(R.id.btn_login);
        TextView signup = findViewById(R.id.tv_signup_link);
        signup.setPaintFlags(signup.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        viewModel.result().observe(this, resource -> {
            btnLogin.setEnabled(resource.state != Resource.State.LOADING);
            if (resource.state == Resource.State.ERROR) {
                showError(resource.message);
            } else if (resource.state == Resource.State.SUCCESS && resource.data != null && resource.data.accessToken != null) {
                Entry.afterAuth(this);
            }
        });
        btnLogin.setOnClickListener(v -> attemptLogin());
        signup.setOnClickListener(v -> startActivity(new Intent(this, Signup.class)));
        keepVisible(etUsername);
        keepVisible(etPassword);
    }

    private void attemptLogin() {
        String email = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || TextUtils.isEmpty(password)) {
            showError("Enter your email and password.");
            return;
        }
        tvError.setVisibility(View.GONE);
        viewModel.login(email, password);
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

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
