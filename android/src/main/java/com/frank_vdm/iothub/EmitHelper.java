package com.frank_vdm.iothub;

import android.support.annotation.Nullable;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class EmitHelper {

    public void emit(ReactContext reactContext,
                     String eventName,
                     @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    public void log(ReactContext reactContext,
                    String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("log", params);
    }

    public void debug(ReactContext reactContext,
                    String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("debug", params);
    }


    public void logError(ReactContext reactContext,
                         Exception exception) {
        WritableMap params = Arguments.createMap();
        params.putString("exception", exception.toString());
        params.putString("message", exception.getMessage());
        try {
            params.putString("cause", exception.getCause().toString());
        }
        catch{
            //Nothing
        }

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("error", params);
    }
}
