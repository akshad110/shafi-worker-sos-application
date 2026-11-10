package com.example.shafi;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.core.SecureStore;
import com.example.shafi.core.SessionBus;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityMainBinding;
import com.example.shafi.ui.AppDialog;
import com.example.shafi.ui.screens.NotificationsActivity;
import com.example.shafi.viewmodel.HomeDashViewModel;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.isLoggedIn(this)) {
            openLogin();
            return;
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        binding.navView.setOnItemSelectedListener(item -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == item.getItemId()) {
                return true;
            }
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                    .build();
            navController.navigate(item.getItemId(), null, options);
            return true;
        });

        binding.customToolbar.imgProfile.setOnClickListener(this::showProfileMenu);
        binding.customToolbar.btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        binding.customToolbar.imgProfile.setText(Format.initials(SessionManager.getUsername(this)));

        HomeDashViewModel dashboard = new ViewModelProvider(this).get(HomeDashViewModel.class);
        dashboard.dashboard().observe(this, resource -> {
            if (resource.data == null) return;
            binding.customToolbar.imgProfile.setText(Format.initials(resource.data.greetingName));
            String role = resource.data.healthcareRole;
            binding.customToolbar.tvRole.setText(role == null || role.isEmpty() ? "Healthcare worker" : role);
        });
        dashboard.unread().observe(this, count -> {
            int value = count == null ? 0 : count;
            binding.customToolbar.tvNotificationBadge.setVisibility(value > 0 ? View.VISIBLE : View.GONE);
            binding.customToolbar.tvNotificationBadge.setText(value > 9 ? "9+" : String.valueOf(value));
        });
        SessionBus.expired.observe(this, expired -> {
            if (Boolean.TRUE.equals(expired)) {
                SessionBus.expired.setValue(false);
                SecureStore.clear();
                openLogin();
            }
        });
    }

    private void showProfileMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_profile, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_profile) {
                binding.navView.setSelectedItemId(R.id.navigation_profile);
                return true;
            } else if (itemId == R.id.menu_setting) {
                startActivity(new Intent(this, SettingActivity.class));
                return true;
            } else if (itemId == R.id.menu_help) {
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            } else if (itemId == R.id.menu_logout) {
                confirmLogout();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    public void confirmLogout() {
        AppDialog.confirm(this,
                "Log out",
                "You will need to sign in again to see shifts and applications.",
                "Log out",
                () -> {
                    AppExecutors.IO.execute(() -> new WorkerRepository(this).logout());
                    openLogin();
                });
    }

    private void openLogin() {
        Intent intent = new Intent(this, login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
