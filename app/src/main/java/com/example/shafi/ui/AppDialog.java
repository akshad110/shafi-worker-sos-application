package com.example.shafi.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import com.example.shafi.R;
import com.google.android.material.button.MaterialButton;

public final class AppDialog {

    private AppDialog() {
    }

    public static void confirm(Context context, String title, String message,
                               String action, Runnable onConfirm) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
        MaterialButton cancel = dialog.findViewById(R.id.btn_dialog_cancel);
        MaterialButton ok = dialog.findViewById(R.id.btn_dialog_ok);
        tvTitle.setText(title);
        tvMessage.setText(message);
        ok.setText(action);
        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        dialog.show();
    }

    public static void note(Context context, String title, String message) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
        MaterialButton cancel = dialog.findViewById(R.id.btn_dialog_cancel);
        MaterialButton ok = dialog.findViewById(R.id.btn_dialog_ok);
        tvTitle.setText(title);
        tvMessage.setText(message);
        cancel.setVisibility(android.view.View.GONE);
        ok.setText("Done");
        ok.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
