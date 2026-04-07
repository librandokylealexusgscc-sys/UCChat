package com.example.uccchat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

public class IosDialog {

    private final Dialog dialog;
    private final TextView tvTitle;
    private final TextView tvMessage;
    private final TextView btnCancel;
    private final TextView btnOk;

    public IosDialog(Context context) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(
                LayoutInflater.from(context)
                        .inflate(R.layout.dialog_ios, null));

        // Transparent background so rounded corners show
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT));
        }

        tvTitle   = dialog.findViewById(R.id.dialogTitle);
        tvMessage = dialog.findViewById(R.id.dialogMessage);
        btnCancel = dialog.findViewById(R.id.dialogCancel);
        btnOk     = dialog.findViewById(R.id.dialogOk);

        // Default: dismiss on Cancel
        btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    // ── Builder-style setters ─────────────────────────────────

    public IosDialog setTitle(String title) {
        tvTitle.setText(title);
        return this;
    }

    public IosDialog setMessage(String message) {
        if (message != null && !message.isEmpty()) {
            tvMessage.setVisibility(View.VISIBLE);
            tvMessage.setText(message);
        } else {
            tvMessage.setVisibility(View.GONE);
        }
        return this;
    }

    public IosDialog setCancelText(String text) {
        btnCancel.setText(text);
        return this;
    }

    public IosDialog setOkText(String text) {
        btnOk.setText(text);
        return this;
    }

    /** Makes OK button red — for destructive actions */
    public IosDialog setDestructive() {
        btnOk.setTextColor(0xFFE53935);
        return this;
    }

    public IosDialog onOk(Runnable action) {
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return this;
    }

    public IosDialog onCancel(Runnable action) {
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return this;
    }

    /** Hide cancel button — for single action dialogs */
    public IosDialog hideCancelButton() {
        btnCancel.setVisibility(View.GONE);
        View divider = dialog.findViewById(R.id.dialogTitle)
                .getRootView().findViewWithTag("verticalDivider");
        return this;
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }
}