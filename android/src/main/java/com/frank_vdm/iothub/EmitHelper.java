package com.frank_vdm.iothub;

import android.support.annotation.Nullable;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class EmitHelper {

    public static void emit(ReactContext reactContext,
                            String eventName,
                            @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    public static void log(ReactContext reactContext,
                           String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        params.putString("TimeStamp", getTimeStamp());
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("log", params);
    }

    public static void debug(ReactContext reactContext,
                             String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        params.putString("TimeStamp", getTimeStamp());
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("debug", params);
    }


    public static void logError(ReactContext reactContext,
                                Exception exception) {
        WritableMap params = Arguments.createMap();
        params.putString("exception", exception.toString());
        params.putString("message", exception.getMessage());
        params.putString("TimeStamp", getTimeStamp());
        try {
            params.putString("cause", exception.getCause().toString());
        } catch (Exception e) {
            EmitHelper.log(reactContext, "SomethignWent Wrong Logging error: " + e.getMessage());
            //Nothing
        }

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("error", params);
    }

    private static String getTimeStamp() {
        return new SimpleDateFormat("HH:mm.ss").format(Calendar.getInstance().getTime());
    }
}
