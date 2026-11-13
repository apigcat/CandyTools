package com.candy.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.Keyboard;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Typeface;

/** 权限管理入口：剪贴板权限 / 唤醒锁权限 / 备份 / 恢复。 */
public class PermissionHubActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg));
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("权限管理");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(getResources().getColor(R.color.neon));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        addCard(root, "剪贴板权限", "管理 READ_CLIPBOARD 允许/禁用", ClipboardActivity.class);
        addCard(root, "唤醒锁权限", "管理 WAKE_LOCK 允许/禁用", WakeLockActivity.class);
        addCard(root, "网络唤醒锁", "限制后台联网与唤醒", NetworkWakeActivity.class);
        addCard(root, "备份", "导出剪贴板与唤醒锁的禁用列表", PermBackupActivity.class);
        addCard(root, "恢复", "选择备份文件并恢复禁用", PermRestoreActivity.class);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(getResources().getColor(R.color.bg));
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addCard(LinearLayout parent, String name, String desc, final Class<?> target) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        int p = dp(18);
        card.setPadding(p, p, p, p);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PermissionHubActivity.this, target));
            }
        });

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        t.setTextColor(getResources().getColor(R.color.text_primary));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        d.setTextColor(getResources().getColor(R.color.text_tertiary));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(4);
        card.addView(d, dLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(12);
        parent.addView(card, cardLp);
        card.setAlpha(0f);
        card.setTranslationY(dp(16));
        card.animate().alpha(1f).translationY(0f)
                .setStartDelay(parent.getChildCount() * 45L)
                .setDuration(240).start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
