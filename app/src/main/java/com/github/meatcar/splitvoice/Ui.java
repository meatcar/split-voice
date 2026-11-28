package com.github.meatcar.splitvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

final class Ui {
    private static final int CONTENT_MAX_WIDTH_DP = 600;

    private Ui() {}

    static void edgeToEdge(Activity activity) {
        activity.getWindow().setDecorFitsSystemWindows(false);
        activity.findViewById(R.id.scroll).setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int width = activity.getResources().getConfiguration().screenWidthDp;
            int gutter = Math.max(dp(activity, 16), dp(activity, width - CONTENT_MAX_WIDTH_DP) / 2);
            view.setPadding(bars.left + gutter, bars.top, bars.right + gutter, bars.bottom);
            return insets;
        });
    }

    static void screen(Activity activity, CharSequence title) {
        activity.setContentView(R.layout.screen);
        edgeToEdge(activity);
        activity.findViewById(R.id.back).setOnClickListener(view -> activity.finish());
        ((TextView) activity.findViewById(R.id.screen_title)).setText(title);
    }

    static LinearLayout section(Activity activity, int title, CharSequence hint) {
        ViewGroup content = activity.findViewById(R.id.content);
        View section = LayoutInflater.from(activity).inflate(R.layout.section, content, false);
        ((TextView) section.findViewById(R.id.section_title)).setText(title);
        TextView hintView = section.findViewById(R.id.section_hint);
        hintView.setText(hint);
        hintView.setVisibility(hint == null ? View.GONE : View.VISIBLE);
        content.addView(section);
        return section.findViewById(R.id.section_card);
    }

    static View row(ViewGroup card, CharSequence title, CharSequence summary, View.OnClickListener onClick) {
        View row = LayoutInflater.from(card.getContext()).inflate(R.layout.list_row, card, false);
        TextView text = row.findViewById(R.id.row_text);
        text.setText(summary == null ? title : twoLine(card.getContext(), title, summary));
        if (onClick == null) row.setBackground(null);
        else {
            row.setOnClickListener(onClick);
            row.findViewById(R.id.row_chevron).setVisibility(View.VISIBLE);
        }
        card.addView(row);
        return row;
    }

    static View action(ViewGroup card, int title, View.OnClickListener onClick) {
        View row = row(card, card.getContext().getString(title), null, onClick);
        TextView text = row.findViewById(R.id.row_text);
        text.setTextColor(card.getContext().getColor(R.color.primary));
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_add, 0, 0, 0);
        row.findViewById(R.id.row_chevron).setVisibility(View.GONE);
        return row;
    }

    static void askName(Activity activity, int title, String initial, int confirm, Consumer<String> onName) {
        EditText name = new EditText(activity);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setHint(R.string.profile_name_hint);
        name.setSingleLine();
        name.setText(initial);
        name.setSelection(initial.length());
        LinearLayout frame = new LinearLayout(activity);
        frame.setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), 0);
        frame.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity).setTitle(title).setView(frame)
                .setPositiveButton(confirm, (dialog, which) -> onName.accept(name.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    static CharSequence twoLine(Context context, CharSequence title, CharSequence summary) {
        SpannableStringBuilder text = new SpannableStringBuilder(title).append('\n');
        int start = text.length();
        text.append(summary);
        text.setSpan(new RelativeSizeSpan(0.875f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(context.getColor(R.color.on_surface_variant)), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
