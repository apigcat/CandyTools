package com.candy.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class AboutActivity extends Activity {

    private static final String GITHUB_URL = "https://github.com/apigcat";
    private static final String COOLAPK_URL = "https://www.coolapk.com/u/3988811";
    private static final int REQ_STORAGE = 1001;

    private TextView chipSystem, chipLight, chipDark;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.tv_github).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(GITHUB_URL);
            }
        });
        findViewById(R.id.tv_coolapk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(COOLAPK_URL);
            }
        });
        findViewById(R.id.tv_coffee).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPayDialog();
            }
        });

        findViewById(R.id.tv_check_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdate();
            }
        });

        chipSystem = (TextView) findViewById(R.id.theme_system);
        chipLight = (TextView) findViewById(R.id.theme_light);
        chipDark = (TextView) findViewById(R.id.theme_dark);
        chipSystem.setOnClickListener(themeClick(ThemeHelper.SYSTEM));
        chipLight.setOnClickListener(themeClick(ThemeHelper.LIGHT));
        chipDark.setOnClickListener(themeClick(ThemeHelper.DARK));
        renderThemeChips();
    }

    private View.OnClickListener themeClick(final int mode) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ThemeHelper.setMode(AboutActivity.this, mode);
                // 重启整个任务，让主界面与说明页也立即换肤
                Intent intent = new Intent(AboutActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        };
    }

    private void renderThemeChips() {
        int mode = ThemeHelper.getMode(this);
        styleChip(chipSystem, mode == ThemeHelper.SYSTEM);
        styleChip(chipLight, mode == ThemeHelper.LIGHT);
        styleChip(chipDark, mode == ThemeHelper.DARK);
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setTextColor(getResources().getColor(
                selected ? R.color.neon : R.color.neon_dim));
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setBackgroundResource(R.drawable.chip_bg);
    }

    /** 仅在点击时检查一次，不做任何后台请求。 */
    private void checkUpdate() {
        final TextView btn = (TextView) findViewById(R.id.tv_check_update);
        btn.setEnabled(false);
        btn.setText(R.string.checking);

        UpdateChecker.check(new UpdateChecker.Callback() {
            @Override
            public void onResult(final UpdateChecker.Result r) {
                btn.setEnabled(true);
                btn.setText(R.string.check_update);

                if (!r.ok) {
                    Toast.makeText(AboutActivity.this, R.string.check_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                int currentCode = 0;
                String currentName = "";
                try {
                    android.content.pm.PackageInfo info =
                            getPackageManager().getPackageInfo(getPackageName(), 0);
                    currentCode = info.versionCode;
                    currentName = info.versionName;
                } catch (Throwable ignored) {
                }

                if (r.versionCode <= currentCode) {
                    Toast.makeText(AboutActivity.this,
                            getString(R.string.up_to_date) + " v" + currentName,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                new AlertDialog.Builder(AboutActivity.this)
                        .setTitle(getString(R.string.new_version) + " v" + r.versionName)
                        .setMessage(r.notes)
                        .setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openUrl(r.url);
                            }
                        })
                        .setNegativeButton(R.string.later, null)
                        .show();
            }
        });
    }

    private void showPayDialog() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(40, 24, 40, 0);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.donate);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wrapper.addView(image);

        TextView tip = new TextView(this);
        tip.setText(R.string.pay_tip);
        tip.setTextSize(12);
        tip.setGravity(android.view.Gravity.CENTER);
        tip.setTextColor(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 20;
        wrapper.addView(tip, lp);

        new AlertDialog.Builder(this)
                .setTitle(R.string.about_coffee)
                .setView(wrapper)
                .setPositiveButton(R.string.pay_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveQr();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void saveQr() {
        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_STORAGE);
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.donate);
        if (bitmap == null) {
            Toast.makeText(this, "保存失败：图片解码为空", Toast.LENGTH_LONG).show();
            return;
        }
        String name = "CandyTools_pay_" + System.currentTimeMillis() + ".png";

        if (Build.VERSION.SDK_INT >= 29) {
            saveToMediaStore(bitmap, name);
        } else {
            saveToPublicDir(bitmap, name);
        }
    }

    private void saveToMediaStore(Bitmap bitmap, String name) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/CandyTools");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = null;
        try {
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, "保存失败：无法创建相册记录", Toast.LENGTH_LONG).show();
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(this, "保存失败：无法写入", Toast.LENGTH_LONG).show();
                return;
            }
            boolean ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
            os.close();
            if (!ok) {
                Toast.makeText(this, "保存失败：编码失败", Toast.LENGTH_LONG).show();
                return;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            Toast.makeText(this, R.string.pay_saved + "\n已保存到 相册/Pictures/CandyTools",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Throwable ignored) {
                }
            }
            Toast.makeText(this, "保存失败：" + t.getClass().getSimpleName() + " " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveToPublicDir(Bitmap bitmap, String name) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "CandyTools");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "保存失败：无法创建目录", Toast.LENGTH_LONG).show();
                return;
            }
            File file = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(file);
            boolean ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            if (!ok) {
                Toast.makeText(this, "保存失败：编码失败", Toast.LENGTH_LONG).show();
                return;
            }
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            Toast.makeText(this, R.string.pay_saved + "\n已保存到 " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "保存失败：" + t.getClass().getSimpleName() + " " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveQr();
            } else {
                Toast.makeText(this, "需要存储权限才能保存", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }
}
