package com.frank_vdm.iothub;

import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.frank_vdm.iothub.IoTHubDeviceModule;

public class IoTHubLifecycleEventListener implements LifecycleEventListener {

    private IoTHubDeviceModule _client;
    private Promise _promise;

    IoTHubLifecycleEventListener(IoTHubDeviceModule client, Promise promise) {
        _client = client;
        _promise = promise;
    }

    @Override
    public void onHostResume() {
        Log.i(this.getClass().getSimpleName(), "onHostResume");
        _client.OpenClient(_promise);
    }

    @Override
    public void onHostPause() {
        Log.i(this.getClass().getSimpleName(), "onHostPause");
        _client.CloseClient(_promise);
    }

    @Override
    public void onHostDestroy() {
        Log.i(this.getClass().getSimpleName(), "onHostDestroy");
        _client.CloseClient(_promise);
    }
}
