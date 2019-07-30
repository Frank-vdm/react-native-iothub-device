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
import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;


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

public class CallbackConnectionChange implements com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeCallback {

    private ReactContext reactContext;
    private IoTHubDeviceModule module;
    private Gson gson = new Gson();

    public CallbackConnectionChange(IoTHubDeviceModule module, ReactContext context) {
        super();
        this.module = module;
        this.reactContext = context;
    }

    @Override
    public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext) {
        WritableMap params = Arguments.createMap();
        params.putString("status", status.name());
        params.putString("statusChangeReason", statusChangeReason.name());

        if (throwable != null) {
            params.putString("statusChangeThrowable", throwable.getMessage());
            throwable.printStackTrace();
        }

        IoTHubDeviceModule.clientIsConnected.set((status == IotHubConnectionStatus.CONNECTED));

        if (status == IotHubConnectionStatus.DISCONNECTED) {
            //connection was lost, and is not being re-established. Look at provided exception for
            // how to resolve this issue. Cannot send messages until this issue is resolved, and you manually
            // re-open the device client
            EmitHelper.emit(module.getReactContext(), "onConnectionStatusChange", params);
        } else if (status == IotHubConnectionStatus.DISCONNECTED_RETRYING) {
            EmitHelper.emit(module.getReactContext(), "onConnectionStatusChange", params);
            EmitHelper.log(getReactContext(), "manually breaking connection and reconnecting");
            module.ReconnectToHub();
            //connection was lost, but is being re-established. Can still send messages, but they won't
            // be sent until the connection is re-established
        } else if (status == IotHubConnectionStatus.CONNECTED) {
            EmitHelper.emit(module.getReactContext(), "onConnectionStatusChange", params);
        }


    }
}