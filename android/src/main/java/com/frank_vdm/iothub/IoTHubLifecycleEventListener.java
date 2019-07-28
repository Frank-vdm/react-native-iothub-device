package com.frank_vdm.iothub;

import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.frank_vdm.iothub.IoTHubDeviceModule;

import java.lang.Thread;

public class IoTHubLifecycleEventListener implements LifecycleEventListener {

    private IoTHubDeviceModule _client;

    IoTHubLifecycleEventListener(IoTHubDeviceModule client) {
        _client = client;
    }

    @Override
    public void onHostResume() {
        //Log.i(this.getClass().getSimpleName(), "onHostResume");
        _client.emitHelper.log(_client.getReactContext(), "onHostResume");
//        if(!_client.clientIsConnected){
//            _client.ConnectClient();
//        }
    }

    @Override
    public void onHostPause() {
        //Log.i(this.getClass().getSimpleName(), "onHostPause");
        _client.emitHelper.log(_client.getReactContext(), "onHostPause");
//        _client.disconnectFromHub();
    }

    @Override
    public void onHostDestroy() {
        //Log.i(this.getClass().getSimpleName(), "onHostDestroy");
        _client.emitHelper.log(_client.getReactContext(), "onHostDestroy");
        //_client.DisconnectFromHub();
    }
}
