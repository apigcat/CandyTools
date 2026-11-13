package com.candy.tools;

/** 唤醒锁权限：appops WAKE_LOCK。 */
public class WakeLockActivity extends ClipboardActivity {

    @Override
    protected String[] ops() {
        return new String[]{"WAKE_LOCK"};
    }
}
