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
import java.util.ArrayList;
import java.util.List;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

public class IoTHubDeviceModule extends ReactContextBaseJavaModule {

    //Properties
    private Gson gson = new Gson();

    private CallbackConnectionChange onConnectionChange;
    private CallbackDesiredPropertyUpdate onDesiredPropertyUpdate;
    private CallbackDeviceMethod onDeviceMethodCall;
    private CallbackDeviceMethodStatus onDeviceMethodStatus;
    private CallbackDeviceTwinPropertyRetrieved onDeviceTwinPropertyRetrieved;
    private CallbackDeviceTwinStatusChange onDeviceTwinStatusChange;
    private CallbackMessageReceived onMessageReceived;
    private CallbackMessageSent onMessageSent;

    //Constructor
    public IoTHubDeviceModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "IoTHubDeviceModule";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        return constants;
    }

    public ReactContext getReactContext() {
        return getReactApplicationContext();
    }

    private Object getDynamicValue(ReadableMap input, String key) {
        if (input.getType(key) == ReadableType.String) {
            return input.getString(key);
        } else if (input.getType(key) == ReadableType.Boolean) {
            return input.getBoolean(key);
        } else if (input.getType(key) == ReadableType.Number) {
            return input.getDouble(key);
        } else {
            return input.getString(key);
        }
    }


    private static AtomicBoolean callbacksAreInitialized = new AtomicBoolean(false);

    private void InitCallbacks() {
        if (!callbacksAreInitialized.get()) {
            EmitHelper.log(getReactContext(), "Initiate Callbacks");
            onConnectionChange = new CallbackConnectionChange(this, getReactContext());
            onDesiredPropertyUpdate = new CallbackDesiredPropertyUpdate(this, getReactContext());
            onDeviceMethodCall = new CallbackDeviceMethod(this, getReactContext());
            onDeviceMethodStatus = new CallbackDeviceMethodStatus(this, getReactContext());
            onDeviceTwinPropertyRetrieved = new CallbackDeviceTwinPropertyRetrieved(this, getReactContext());
            onDeviceTwinStatusChange = new CallbackDeviceTwinStatusChange(this, getReactContext());
            onMessageReceived = new CallbackMessageReceived(this, getReactContext());
            onMessageSent = new CallbackMessageSent(this, getReactContext());
            getReactContext().addLifecycleEventListener(new IoTHubLifecycleEventListener(this));
            callbacksAreInitialized.set(true);
        }
    }

    @ReactMethod
    public void connectToHub(String connectionString, ReadableArray desiredPropertySubscriptions, Promise promise) {
        InitCallbacks();
        if (hasInternetConnection()) {
            try {
                if (client == null) {
                    client = CreateIotHubClient(connectionString, desiredPropertySubscriptions);
                    EmitHelper.log(getReactContext(), "Client Created");
                } else {
                    ConnectClient();
                    EmitHelper.log(getReactContext(), "Client Connected");
                }
                promise.resolve("Success");
            } catch (Exception e) {
                String temp = ExceptionUtils.getRootCauseMessage(e);

                EmitHelper.log(getReactContext(), temp);
                EmitHelper.logError(getReactContext(), e);
                promise.reject(this.getClass().getSimpleName(), e);
            }
        }
    }

    @ReactMethod
    public void disconnectFromHub(Promise promise) {
        try {
            EmitHelper.log(getReactContext(), "started disconnect from hub");
            if (client != null) {
                new Thread() {
                    public void run() {
                        try {
                            DeviceClient clientToClose = client;
                            client = null;
                            clientIsConnected.set(false);
                            clientToClose.closeNow();
                            EmitHelper.log(getReactContext(), "Client Closed");
                        } catch (IOException ioException) {
                            EmitHelper.logError(getReactContext(), ioException);
//                            promise.reject(this.getClass().getSimpleName(), ioException);
                        }
                    }
                }.start();
                
            } else {
                EmitHelper.log(getReactContext(), "Client is NUll");
            }
            EmitHelper.log(getReactContext(), "finished disconnect from hub");
            promise.resolve("Success");
        } catch (Exception e) {
            EmitHelper.logError(getReactContext(), e);

        }

    }

    public static AtomicBoolean clientIsConnected = new AtomicBoolean(false);

    private boolean hasInternetConnection() {
        ConnectivityManager mgr = (ConnectivityManager) getReactContext().getSystemService(getReactContext().CONNECTIVITY_SERVICE);
        boolean networkAvailable = mgr.isDefaultNetworkActive();
        if (!networkAvailable) {

        }
        return networkAvailable;
    }

    private boolean canInteractWithHub() {
        boolean clientExists = client != null;
        return clientExists && clientIsConnected.get() && hasInternetConnection();
    }

    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////--------------------------------------------------- SETUP ------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    private IotHubClientProtocol protocol = IotHubClientProtocol.MQTT_WS; //IotHubClientProtocol.AMQPS_WS
    private static DeviceClient client = null;

    private DeviceClient CreateIotHubClient(String connectionString, ReadableArray desiredPropertySubscriptions) throws URISyntaxException {
        try {
            EmitHelper.log(getReactContext(), "Creating Hub Client");
            DeviceClient newClient = new DeviceClient(connectionString, protocol);

            EmitHelper.log(getReactContext(), "register Connection Status Change Callback");
            newClient.registerConnectionStatusChangeCallback(onConnectionChange, new Object());

            EmitHelper.log(getReactContext(), "Connect Client");
            ConnectionResult result = ConnectClient(newClient);
            if (result == ConnectionResult.CONNECTION_OPEN) {
                EmitHelper.log(getReactContext(), "Subscribe Callbacks");
                SubscribeCallbacks(newClient, desiredPropertySubscriptions);
            }
            return newClient;
        } catch (URISyntaxException uriSyntaxException) {
            EmitHelper.logError(getReactContext(), uriSyntaxException);
            String message = "The connection string is Malformed. " + uriSyntaxException.getMessage();
            EmitHelper.log(getReactContext(), message);
            return null;
//        } catch (IOException ioException) {
//            EmitHelper.logError(getReactContext(), ioException);
//            String message = "Error Connecting to the hub. " + ioException.getMessage();
//            EmitHelper.log(getReactContext(), message);
//            return null;
        }
    }

    public enum ConnectionResult {
        CONNECTION_UNAVAILABLE(0),
        CONNECTION_OPEN(1),
        CONNECTION_CLOSED(2),
        CONNECTION_ERROR(3);

        private int value;

        private ConnectionResult(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private ConnectionResult ConnectClient(DeviceClient newClient) {
        if (hasInternetConnection()) {
            try {
                newClient.open();
                return ConnectionResult.CONNECTION_OPEN;
            } catch (IOException ioException) {
                EmitHelper.logError(getReactContext(), ioException);
                String message = "There was a problem connecting to the IOT Hub. " + ioException.getMessage();
                EmitHelper.log(getReactContext(), message);
                return ConnectionResult.CONNECTION_ERROR;
            }
        } else {
            EmitHelper.log(getReactContext(), "No Network Connection");
            return ConnectionResult.CONNECTION_UNAVAILABLE;
        }
    }

    private ConnectionResult ConnectClient() {
        if (hasInternetConnection()) {
            try {
                client.open();
                return ConnectionResult.CONNECTION_OPEN;
            } catch (IOException ioException) {
                EmitHelper.logError(getReactContext(), ioException);
                String message = "There was a problem connecting to the IOT Hub. " + ioException.getMessage();
                EmitHelper.log(getReactContext(), message);
                return ConnectionResult.CONNECTION_ERROR;
            }
        } else {
            EmitHelper.log(getReactContext(), "No Network Connection");
            return ConnectionResult.CONNECTION_UNAVAILABLE;
        }
    }

    private void SubscribeCallbacks(DeviceClient newClient, ReadableArray desiredPropertySubscriptions) {
        newClient.setMessageCallback(onMessageReceived, null);
        // newClient.subscribeToDeviceMethod(onDeviceMethodCall, null, onDeviceMehtodstatus, null);
        // StartDeviceTwin(newClient);
        // SubscribeToDesiredProperties(newClient, desiredPropertySubscriptions);
    }

    public static AtomicBoolean twinIsStarted = new AtomicBoolean(false);

    private void StartDeviceTwin(DeviceClient newClient) throws IOException {
        twinIsStarted.set(false);
        newClient.startDeviceTwin(onDeviceTwinStatusChange, null, onDeviceTwinPropertyRetrieved, null);
        try {
            do {
                Thread.sleep(1000);
            }
            while (!twinIsStarted.get());
        } catch (Exception exception) { //(InterruptedException interruptedException){
            EmitHelper.logError(getReactContext(), exception);
            String message = "Device Twin Could Not Be started. " + exception.getMessage();
            EmitHelper.log(getReactContext(), message);
        }
    }

    private void SubscribeToDesiredProperties(DeviceClient newClient, ReadableArray desiredPropertySubscriptions) throws IOException {
        Map<Property, Pair<TwinPropertyCallBack, Object>> subscriptions = CreateSubscriptions(desiredPropertySubscriptions);
        if (!subscriptions.isEmpty()) {
            newClient.subscribeToTwinDesiredProperties(subscriptions);
        }
    }

    private Map<Property, Pair<TwinPropertyCallBack, Object>> CreateSubscriptions(ReadableArray desiredPropertySubscriptions) {
        if (desiredPropertySubscriptions == null || desiredPropertySubscriptions.size() == 0) {
            return null;
        } else {
            Map<Property, Pair<TwinPropertyCallBack, Object>> subscriptions = new HashMap<>();
            for (int i = 0; i < desiredPropertySubscriptions.size(); i++) {
                subscriptions.put(new Property(desiredPropertySubscriptions.getString(i), null),
                        new Pair<TwinPropertyCallBack, Object>(onDesiredPropertyUpdate, null));
            }
            return subscriptions;
        }
    }

    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////--------------------------------------------------- ACTIONS ----------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////

    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
    ////--------------------------------------------------- Send Reported Property -------------------------------------////
    @ReactMethod
    public void sendReportedProperty(ReadableMap input, Callback success, Callback failure) {
        if (canInteractWithHub()) {
            EmitHelper.log(getReactContext(), "Send Reported Property");
            try {
                Property property = new Property(input.getString("key"), getDynamicValue(input, "value"));
                client.sendReportedProperties(Sets.newHashSet(property));
                if (success != null) {
                    success.invoke();
                }
            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(), e.getMessage(), e);
                if (failure != null) {
                    failure.invoke(e.getMessage());
                }
            }
        }
    }


    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
    ////--------------------------------------------------- Send Reported Properties -----------------------------------////
    @ReactMethod
    public void sendReportedProperties(ReadableArray array, Promise promise) {
        if (canInteractWithHub()) {
            EmitHelper.log(getReactContext(), "Send Reported Properties");
            Set<Property> propertiesToSet = new LinkedHashSet<>();
            for (int i = 0; i < array.size(); i++) {
                ReadableMap map = array.getMap(i);
                String key = map.getString("key");
                Object value = getDynamicValue(map, "value");
                propertiesToSet.add(new Property(key, value));
            }
            try {
                client.sendReportedProperties(propertiesToSet);
                if (promise != null) {
                    promise.resolve(true);
                }
            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(), e.getMessage(), e);
                if (promise != null) {
                    promise.reject(e);
                }
            }
        }
    }

    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
    ////--------------------------------------------------- Send Message -----------------------------------------------////
    private int msgSentCount = 0;
    private static final int D2C_MESSAGE_TIMEOUT = 2000; // 2 seconds
    public static List<String> failedMessageListOnClose = new ArrayList<String>(); // List of messages that failed on close

    @ReactMethod
    public void sendMessage(ReadableArray properties, String eventMessage, Promise promise) {
        if (canInteractWithHub()) {
            try {
                Message messagetoSend = CreateMessageToSend(properties, eventMessage);
                client.sendEventAsync(messagetoSend, onMessageSent, msgSentCount);
                msgSentCount++;
                promise.resolve("Event Message sent Successfully!");
            } catch (Exception e) {
                String message = "There was a problem sending Event Message. " + e.getMessage();
                //Log.e(this.getClass().getSimpleName(), message, e);
                EmitHelper.logError(getReactContext(), e);
                promise.reject(this.getClass().getSimpleName(), e);
            }
        }
    }

    private Message CreateMessageToSend(ReadableArray properties, String eventMessage) {
        Message newMessageToSend = new Message(eventMessage);
        // set properties
        for (int i = 0; i < properties.size(); i++) {
            ReadableMap map = properties.getMap(i);
            String key = map.getString("key");
            Object value = getDynamicValue(map, "value");
            newMessageToSend.setProperty(key, value.toString());
        }
        newMessageToSend.setMessageId(java.util.UUID.randomUUID().toString());
        return newMessageToSend;
    }
}