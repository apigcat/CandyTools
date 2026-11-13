package com.candy.tools;

/** 网络唤醒锁：appops RUN_ANY_IN_BACKGROUND。 */
public class NetworkWakeActivity extends ClipboardActivity {

    @Override
    protected String[] ops() {
        return new String[]{"RUN_ANY_IN_BACKGROUND"};
    }
}
