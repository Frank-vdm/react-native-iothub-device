package com.frank_vdm.iothub;

import android.support.annotation.Nullable;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.exception.ExceptionUtils;

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
        params.putString("timeStamp", getTimeStamp());
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("log", params);
    }

    public static void debug(ReactContext reactContext,
                             String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        params.putString("timeStamp", getTimeStamp());
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("debug", params);
    }


    public static void logError(ReactContext reactContext,
                                Exception exception) {


        String lineNumber = exception.getStackTrace() != null ? Integer.toString(exception.getStackTrace()[0].getLineNumber()) : "unknown line";
        String rootCause = ExceptionUtils.getRootCauseMessage(exception);
        String stackTrace = ExceptionUtils.getStackTrace(exception);

        WritableMap params = Arguments.createMap();
        params.putString("exception", exception.toString());
        params.putString("stackTrace", stackTrace);
        params.putString("message", exception.getMessage());
        params.putString("rootCause", rootCause);
        params.putString("line", lineNumber);
        params.putString("timeStamp", getTimeStamp());

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("error", params);
    }

    private static String getTimeStamp() {
        return new SimpleDateFormat("HH:mm.ss").format(Calendar.getInstance().getTime());
    }
}
