package com.example.shafi.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.shafi.R;
import com.example.shafi.databinding.SheetDutyBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Duty details opened from a hospital post. The cross closes it.
 * Apply is only offered when the officer has not already applied.
 */
public class DutySheet extends BottomSheetDialogFragment {

    private static final String HOSPITAL = "hospital";
    private static final String TITLE = "title";
    private static final String PLACE = "place";
    private static final String TIMING = "timing";
    private static final String PAY = "pay";
    private static final String ADDRESS = "address";
    private static final String NOTE = "note";
    private static final String STATUS = "status";

    public static void show(Fragment host, String hospital, String title, String place,
                            String timing, String pay, String address, String note, String status) {
        DutySheet sheet = new DutySheet();
        Bundle args = new Bundle();
        args.putString(HOSPITAL, hospital);
        args.putString(TITLE, title);
        args.putString(PLACE, place);
        args.putString(TIMING, timing);
        args.putString(PAY, pay);
        args.putString(ADDRESS, address);
        args.putString(NOTE, note);
        args.putString(STATUS, status);
        sheet.setArguments(args);
        sheet.show(host.getParentFragmentManager(), "duty");
    }

    @Override
    public int getTheme() {
        return R.style.Theme_Shafi_BottomSheet;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundResource(R.drawable.bg_sheet);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        SheetDutyBinding binding = SheetDutyBinding.inflate(inflater, container, false);
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        String status = args.getString(STATUS, "open");

        binding.tvSheetHospital.setText(args.getString(HOSPITAL));
        binding.tvSheetTitle.setText(args.getString(TITLE));
        binding.tvSheetPlace.setText(args.getString(PLACE));
        binding.tvSheetTiming.setText(args.getString(TIMING));
        binding.tvSheetPay.setText(args.getString(PAY));
        binding.tvSheetAddress.setText(args.getString(ADDRESS));
        binding.tvSheetNote.setText(args.getString(NOTE));
        binding.btnClose.setOnClickListener(v -> dismiss());

        if ("open".equals(status)) {
            binding.tvSheetStatus.setText("Open hospital post");
            binding.btnSheetAction.setText("Apply");
            binding.btnSheetAction.setOnClickListener(v -> {
                Context host = requireContext();
                AppDialog.confirm(host,
                        "Apply for this duty",
                        "Your profile goes to the hospital. It stays Pending until they review it.",
                        "Apply",
                        () -> {
                            dismissAllowingStateLoss();
                            AppDialog.note(host, "Marked as pending",
                                    "The hospital has your application. Track it under Applied.");
                        });
            });
        } else {
            binding.tvSheetStatus.setText(status);
            binding.btnSheetAction.setText("Close");
            binding.btnSheetAction.setOnClickListener(v -> dismiss());
        }
        return binding.getRoot();
    }
}
