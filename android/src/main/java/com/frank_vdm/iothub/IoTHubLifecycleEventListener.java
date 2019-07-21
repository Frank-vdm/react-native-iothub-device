package com.frank_vdm.iothub;

import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.frank_vdm.iothub.IoTHubDeviceModule;

public class IoTHubLifecycleEventListener implements LifecycleEventListener {

    private IoTHubDeviceModule _client;

    IoTHubLifecycleEventListener(IoTHubDeviceModule client) {
        _client = client;
    }

    @Override
    public void onHostResume() {
        Log.i(this.getClass().getSimpleName(), "onHostResume");
        _client.OpenClient();
    }

    @Override
    public void onHostPause() {
        Log.i(this.getClass().getSimpleName(), "onHostPause");
        _client.CloseClient();
    }

    @Override
    public void onHostDestroy() {
        Log.i(this.getClass().getSimpleName(), "onHostDestroy");
        _client.CloseClient();
    }
}
