package com.fieldbook.tracker.Utilities;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.fieldbook.tracker.R;

/**
 * Created by matarredona on 22/03/17.
 */

public class DialogHelper {

    public View getDialogView(int layout, Context context) {

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(layout, null);

        return view;
    }

    public AlertDialog buildDialog(String title, View view, Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

        builder.setTitle(title)
                .setCancelable(true)
                .setView(view);

        AlertDialog dialog = builder.create();

        android.view.WindowManager.LayoutParams langParams = dialog.getWindow().getAttributes();
        langParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        dialog.getWindow().setAttributes(langParams);

        return dialog;
    }
}
