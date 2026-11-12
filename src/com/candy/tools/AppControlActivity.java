package com.candy.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 卸载与冰冻入口：卸载 / 冰冻 / 备份 / 恢复。 */
public class AppControlActivity extends Activity {

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
        title.setText("卸载与冰冻");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(getResources().getColor(R.color.neon));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        addCard(root, "卸载", "支持恢复的卸载，仅针对系统应用", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AppControlActivity.this, AppOpsListActivity.class);
                intent.putExtra(AppOpsListActivity.EXTRA_MODE, AppOpsListActivity.MODE_UNINSTALL);
                startActivity(intent);
            }
        });

        addCard(root, "冰冻", "停用应用，保留数据", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AppControlActivity.this, AppOpsListActivity.class);
                intent.putExtra(AppOpsListActivity.EXTRA_MODE, AppOpsListActivity.MODE_FREEZE);
                startActivity(intent);
            }
        });

        addCard(root, "备份", "导出冰冻与卸载列表为 XML", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AppControlActivity.this, BackupActivity.class));
            }
        });

        addCard(root, "恢复", "选择备份文件并执行", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AppControlActivity.this, RestoreActivity.class));
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getResources().getColor(R.color.bg));
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addCard(LinearLayout parent, String name, String desc,
                         View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        int p = dp(18);
        card.setPadding(p, p, p, p);
        card.setOnClickListener(listener);
        card.setClickable(true);
        card.setFocusable(true);

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
