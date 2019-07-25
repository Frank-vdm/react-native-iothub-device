package com.frank_vdm.iothub;

import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.frank_vdm.iothub.IoTHubDeviceModule;

import java.lang.Thread;

public class IoTHubLifecycleEventListener implements LifecycleEventListener {

    private IoTHubDeviceModule _client;
    private Promise _promise;

    IoTHubLifecycleEventListener(IoTHubDeviceModule client) {
        _client = client;
    }

    @Override
    public void onHostResume() {
        Log.i(this.getClass().getSimpleName(), "onHostResume");
        _client.emitHelper.log(_client.getReactContext(), "onHostResume");
        _client.startClient();
    }

    @Override
    public void onHostPause() {
        Log.i(this.getClass().getSimpleName(), "onHostPause");
        _client.emitHelper.log(_client.getReactContext(), "onHostPause");
        Thread t = new Thread() {
            public void run() {
                _client.stopClient();
            }
        }.start();

    }

    @Override
    public void onHostDestroy() {
        Log.i(this.getClass().getSimpleName(), "onHostDestroy");
        _client.emitHelper.log(_client.getReactContext(), "onHostDestroy");
        _client.stopClient();
    }
}
