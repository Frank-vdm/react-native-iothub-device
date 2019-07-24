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

    public void logError(ReactContext reactContext,
                         Exception exception) {
        WritableMap params = Arguments.createMap();
        params.putString("exception", exception.toString());
        params.putString("message", exception.getMessage());
        params.putString("cause", exception.getCause().toString());
        params.putString("stacktrace", exception.getStackTrace().toString());
        params.putString("filledstacktrace", exception.fillInStackTrace().toString());


        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("error", params);
    }
}
