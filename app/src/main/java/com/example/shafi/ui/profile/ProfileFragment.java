package com.example.shafi.ui.profile;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.shafi.HelpActivity;
import com.example.shafi.MainActivity;
import com.example.shafi.R;
import com.example.shafi.SettingActivity;
import com.example.shafi.core.Format;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.FragmentProfileBinding;
import com.example.shafi.ui.screens.DocumentsActivity;
import com.example.shafi.ui.screens.EarningsActivity;
import com.example.shafi.ui.screens.OnboardingActivity;
import com.example.shafi.viewmodel.ProfileViewModel;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private Dtos.ProfileResponse current;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        binding.btnEdit.setOnClickListener(v -> showEdit());
        binding.btnDocuments.setOnClickListener(v -> startActivity(new Intent(requireContext(), DocumentsActivity.class)));
        binding.btnEarnings.setOnClickListener(v -> startActivity(new Intent(requireContext(), EarningsActivity.class)));
        binding.btnAvailability.setOnClickListener(v -> startActivity(new Intent(requireContext(), OnboardingActivity.class)));
        binding.btnSettings.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingActivity.class)));
        binding.btnHelp.setOnClickListener(v -> startActivity(new Intent(requireContext(), HelpActivity.class)));
        binding.btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).confirmLogout();
        });
        viewModel.profile().observe(getViewLifecycleOwner(), this::render);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void render(Resource<Dtos.ProfileResponse> resource) {
        binding.progress.setVisibility(resource.state == Resource.State.LOADING && resource.data == null ? View.VISIBLE : View.GONE);
        if (resource.state == Resource.State.ERROR) {
            binding.tvError.setText(resource.message);
            binding.tvError.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvError.setVisibility(View.GONE);
        if (resource.data == null || resource.data.user == null) return;
        current = resource.data;
        Dtos.UserAccount user = resource.data.user;
        Dtos.WorkerProfile profile = resource.data.profile;
        binding.tvInitials.setText(Format.initials(user.fullName));
        binding.tvName.setText(user.fullName);
        binding.tvEmail.setText(user.email == null || user.email.isEmpty() ? "Email not added" : user.email);
        binding.tvPhone.setText(user.mobile == null || user.mobile.isEmpty() ? "Mobile not added" : user.mobile);
        binding.tvRole.setText(profile == null || profile.healthcareRole == null || profile.healthcareRole.isEmpty()
                ? "Healthcare professional" : profile.healthcareRole);
        binding.tvCity.setText(profile == null || profile.city == null || profile.city.isEmpty() ? "City not added" : profile.city);
        String verification = profile == null ? "" : profile.verificationStatus;
        boolean verified = "VERIFIED".equals(verification);
        binding.tvStatus.setText(verified ? "Verified" : "Verification " + Format.status(verification));
        binding.tvStatus.setBackgroundResource(verified ? R.drawable.bg_pill_green : R.drawable.bg_pill_pending);
        binding.tvStatus.setTextColor(requireContext().getColor(verified ? R.color.emerald_deep : R.color.pending));
        binding.tvCompletion.setText("Profile " + resource.data.completion + "% complete");
        binding.progressProfile.setProgress(resource.data.completion);
    }

    private void showEdit() {
        if (current == null || current.user == null) return;
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_profile);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        EditText name = dialog.findViewById(R.id.et_edit_name);
        EditText phone = dialog.findViewById(R.id.et_edit_phone);
        EditText city = dialog.findViewById(R.id.et_edit_city);
        name.setText(current.user.fullName);
        phone.setText(current.user.mobile);
        phone.setEnabled(false);
        city.setText(current.profile == null ? "" : current.profile.city);
        dialog.findViewById(R.id.btn_edit_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_edit_save).setOnClickListener(v -> {
            Map<String, Object> body = new HashMap<>();
            body.put("fullName", name.getText().toString().trim());
            body.put("city", city.getText().toString().trim());
            viewModel.save(body);
            dialog.dismiss();
        });
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
