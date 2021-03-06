package com.frank_vdm.iothub;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.google.gson.Gson;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.TwinPropertyCallBack;

import java.io.IOException;


public class CallbackDesiredPropertyUpdate implements com.microsoft.azure.sdk.iot.device.DeviceTwin.TwinPropertyCallBack {

    private ReactContext reactContext;
    private IoTHubDeviceModule module;
    private Gson gson = new Gson();

    public CallbackDesiredPropertyUpdate(IoTHubDeviceModule module, ReactContext context){
        super();
        this.module = module;
        this.reactContext = context;
    }

    @Override
    public void TwinPropertyCallBack(Property property, Object context) {
        Log.i(this.getClass().getSimpleName(), gson.toJson(property));
        WritableMap params = Arguments.createMap();
        params.putString("propertyJson", gson.toJson(property));
        EmitHelper.emit(module.getReactContext(), "onDesiredPropertyUpdate", params);
    }
}

