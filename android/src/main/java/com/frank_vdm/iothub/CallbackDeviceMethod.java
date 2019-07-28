package com.frank_vdm.iothub;

import android.os.SystemClock;
import android.util.Log;
import android.support.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;

import com.facebook.common.internal.Sets;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.google.gson.Gson;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Pair;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.TwinPropertyCallBack;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodData;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeCallback;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.DeviceClientException;
import com.microsoft.azure.sdk.iot.device.exceptions.TransportException;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.device.transport.RetryPolicy;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.lang.InterruptedException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.Thread;

public class CallbackDeviceMethod implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback {

    private ReactContext reactContext;
    private IoTHubDeviceModule module;
    private Gson gson = new Gson();
    public CallbackDeviceMethod(IoTHubDeviceModule module, ReactContext context){
        super();
        this.module = module;
        this.reactContext = context;
    }

    private static final int METHOD_SUCCESS = 200;
    private static final int METHOD_NOT_DEFINED = 404;

    private int method_command(Object command) {
        System.out.println("invoking command on this device");
        emitHelper.log(getReactContext(), "invoking command on this device");
        // Insert code to invoke command here
        return METHOD_SUCCESS;
    }

    private int method_default(Object data) {
        System.out.println("invoking default method for this device");
        emitHelper.log(getReactContext(), "invoking default method for this device");
        // Insert device specific code here
        return METHOD_NOT_DEFINED;
    }

    @Override
    public DeviceMethodData call(String methodName, Object methodData, Object context) {
        DeviceMethodData deviceMethodData;
        switch (methodName) {
            case "command": {
                int status = method_command(methodData);

                deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                break;
            }
            default: {
                int status = method_default(methodData);
                deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
            }
        }
        EmitHelper.log(module.getReactContext(), "DeviceMethodData");
        return deviceMethodData;
    }
}