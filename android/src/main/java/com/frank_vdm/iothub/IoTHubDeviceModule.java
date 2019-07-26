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

public class IoTHubDeviceModule extends ReactContextBaseJavaModule {

    private TwinPropertyCallBack onDesiredPropertyUpdate;

    private Gson gson = new Gson();

    protected EmitHelper emitHelper = new EmitHelper();

    @Override
    public String getName() {
        return "IoTHubDeviceModule";
    }

    public ReactContext getReactContext() {
        return getReactApplicationContext();
    }

    public IoTHubDeviceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        onDesiredPropertyUpdate = new OnDesiredPropertyUpdate(this, getReactApplicationContext());
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        return constants;
    }


    ////--------------------------------------------------- HELPERS ----------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    private boolean hasInternetConnection() {
        ConnectivityManager mgr = (ConnectivityManager) getReactContext().getSystemService(getReactContext().CONNECTIVITY_SERVICE);
        boolean networkAvailible = mgr.isDefaultNetworkActive();
        if (!networkAvailible) {

        }
        return networkAvailible;
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

    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
////------------------------------------------------------- Connect To Hub ---------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    protected DeviceClient client;
    protected String _connectionString;
    protected ReadableArray _desiredPropertySubscriptions;
    protected Promise _promise;
    protected boolean _shouldRetry = true;

    @ReactMethod
    public void connectToHub(String connectionString, ReadableArray desiredPropertySubscriptions, Boolean shouldRetry, Boolean useTreading, Promise promise) {
        _connectionString = connectionString;
        _desiredPropertySubscriptions = desiredPropertySubscriptions;
        _promise = promise;

        if (shouldRetry != null) {
            _shouldRetry = shouldRetry;
        }
        if (hasInternetConnection()) {
            if (useTreading) {
                Thread t = new Thread(new IotHubDeviceClient());
                t.start();
            } else {
                ExecuteConnection();
            }
        } else {
            emitHelper.log(getReactContext(), "no network connection");
            promise.resolve("no network connection");
        }
    }

    //THREADING
    class IotHubDeviceClient implements Runnable {
        public IotHubDeviceClient() {
        }

        public void run() {
            emitHelper.log(getReactContext(), "IotHubDeviceClient Start");
            connectToIotHub();
            emitHelper.log(getReactContext(), "IotHubDeviceClient End");
        }
    }

    //NON-THREADING
    protected void ExecuteConnection() {
        emitHelper.log(getReactContext(), "ExecuteConnection Start");
        connectToIotHub();
        emitHelper.log(getReactContext(), "ExecuteConnection End");
    }


    public boolean clientBusy = false;

    //BASE INITIATION
    protected void connectToIotHub() {
        try {
            do {
                Thread.sleep(1000);
            }
            while (clientBusy);

            SetupClient();
            clientBusy = true;

            Connect();
            clientBusy = true;

            SetupDeviceMethod();
            clientBusy = true;

            StartDeviceTwin();
            clientBusy = true;

            System.out.println("Subscribe to Desired properties on device Twin...");
            emitHelper.log(getReactContext(), "Connection To IOT Hub Completed");
            clientBusy = false;

            _promise.resolve("Successfully connected!");
        } catch (URISyntaxException uriSyntaxException) {
            emitHelper.logError(getReactContext(), uriSyntaxException);
            String message = "The connection string is Malformed. " + uriSyntaxException.getMessage();
            Log.e(this.getClass().getSimpleName(), message, uriSyntaxException);

            _promise.reject(this.getClass().getSimpleName(), uriSyntaxException);
        } catch (IOException ioException) {
            emitHelper.logError(getReactContext(), ioException);
            String message = "There was a problem connecting to the IOT Hub. " + ioException.getMessage();
            Log.e(this.getClass().getSimpleName(), message, ioException);

            _promise.reject(this.getClass().getSimpleName(), ioException);
        } catch (InterruptedException interruptedException) {
            emitHelper.logError(getReactContext(), interruptedException);
            String message = "connecting to the IOT Hub was interrupted. " + interruptedException.getMessage();
            Log.e(this.getClass().getSimpleName(), message, interruptedException);

            _promise.reject(this.getClass().getSimpleName(), interruptedException);
        }
    }


    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
////------------------------------------------------------- Disconnect From Hub ----------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    @ReactMethod
    public void disconnectFromHub(Promise promise) {
        try {
            Disconnect();
            promise.resolve("Successfully disconnected!");
        } catch (Exception e) {
            emitHelper.logError(getReactContext(), e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }


    public void ConnectToHub() {
        try {
            Connect();
            _promise.resolve("Successfully connected!");
        } catch (Exception e) {
            emitHelper.logError(getReactContext(), e);
            _promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    public void DisconnectFromHub() {
        try {
            Disconnect();
            _promise.resolve("Successfully disconnected!");
        } catch (Exception e) {
            emitHelper.logError(getReactContext(), e);
            _promise.reject(this.getClass().getSimpleName(), e);
        }
    }


    ////--------------------------------------------------- Disconnect -------------------------------------------------////
    private void Disconnect() {
        if (client != null && clientIsConnected) {
            clientBusy = true;
            emitHelper.debug(getReactContext(), "Connection Close Started");
            CloseConnectionToIotHub();
            emitHelper.debug(getReactContext(), "Connection Close Ended");
            clientBusy = false;
        }
    }

    private void CloseConnectionToIotHub() throws IOException {
        new Thread() {
            public void run() {
                client.closeNow();
            }
        }.start();
    }


    ////--------------------------------------------------- Setup Client -----------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    private boolean clientIsSetup = false;

    private void SetupClient() throws URISyntaxException {
        emitHelper.log(getReactContext(), "Setting up IOT Hub Client");

        if (client == null) {
            emitHelper.debug(getReactContext(), "Initialize new Device Client");
            client = new DeviceClient(_connectionString, IotHubClientProtocol.AMQPS_WS);
            clientIsSetup = false;
        }

        if (!clientIsSetup) {
            emitHelper.debug(getReactContext(), "Seting up Connection Status Change Callback");
            client.registerConnectionStatusChangeCallback(new ConnectionChangedCallback(), null);

            emitHelper.debug(getReactContext(), "Adding Lifecycle Event Listener");
            getReactContext().addLifecycleEventListener(new IoTHubLifecycleEventListener(this));

            emitHelper.debug(getReactContext(), "Setting Message Callback");
            client.setMessageCallback(new MessageCallback(), null);

            clientIsSetup = true;
            resetClientIndicators();
        } else {
            emitHelper.debug(getReactContext(), "Client is already set up");
        }
    }

    //TODO MAYBE MOVE INTO A SEPERATE FILE
    protected class ConnectionChangedCallback implements com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeCallback {
        @Override
        public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext) {
            Log.d(this.getClass().getSimpleName(), "status: " + status + " reason: " + statusChangeReason);
            WritableMap params = Arguments.createMap();
            params.putString("status", status.name());
            params.putString("statusChangeReason", statusChangeReason.name());
            clientIsConnected = status.name() == "CONNECTED";
            emitHelper.emit(getReactContext(), "onConnectionStatusChange", params);
        }
    }

    //TODO MAYBE MOVE INTO A SEPERATE FILE
    protected class MessageCallback implements com.microsoft.azure.sdk.iot.device.MessageCallback {
        @Override
        public IotHubMessageResult execute(Message message, Object context) {
            String messageString = new String(message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET);
            Log.d(this.getClass().getSimpleName(), messageString);
            WritableMap params = Arguments.createMap();
            params.putString("message", messageString);
            params.putString("messageId", message.getMessageId());
            emitHelper.emit(getReactContext(), "onMessageReceived", params);
            return IotHubMessageResult.COMPLETE;
        }
    }

    private void resetClientIndicators() {
        emitHelper.debug(getReactContext(), "Resetting Client Indicators...");
        clientIsConnected = false;
        deviceMethodIsSetup = false;
        twinIsStarted = false;
    }

    ////--------------------------------------------------- Connect ----------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    private boolean clientIsConnected = false;

    private void Connect() throws IOException {
        if (hasInternetConnection()) {
            emitHelper.log(getReactContext(), "Openning connection To IOT Hub ");
            if (client != null && !clientIsConnected) {
                clientBusy = true;
                emitHelper.debug(getReactContext(), "Connection Open Started");
                OpenConnectionToIotHub();
                emitHelper.debug(getReactContext(), "Connection Open Ended");
                clientBusy = false;
            }
        } else {
            _promise.resolve("No Network Availible");
        }
    }

    private void OpenConnectionToIotHub() throws IOException {
        try {
            new Thread() {
                public void run() {
                    client.open();
                }
            }.start();
        } catch (Exception e) {
            if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "TransportException: Timed out waiting to connect to service") && _shouldRetry) {
                Thread.sleep(2000);
                _shouldRetry = false;
                OpenConnectionToIotHub();
            } else {
                emitHelper.logError(getReactContext(), e);
                System.err.println("Exception while opening IoTHub connection: " + e.getMessage());
                client.closeNow();
                clientBusy = false;
                throw e;
            }
        }
    }

    ////--------------------------------------------------- Setup Device Method ----------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    private boolean deviceMethodIsSetup = false;

    private void SetupDeviceMethod() throws IOException {
        emitHelper.log(getReactContext(), "Setup Device Method");

        if (client != null && clientIsConnected && !deviceMethodIsSetup) {
            clientBusy = true;
            client.subscribeToDeviceMethod(new SampleDeviceMethodCallback(), null, new DeviceMethodStatusCallback(), null);
            deviceMethodIsSetup = true;
            clientBusy = false;
        } else {
            emitHelper.log(getReactContext(), "Iot Hub Device Client is not initilized");
        }
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

    protected class SampleDeviceMethodCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback {
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

            return deviceMethodData;
        }
    }

    protected class DeviceMethodStatusCallback implements com.microsoft.azure.sdk.iot.device.IotHubEventCallback {
        public void execute(IotHubStatusCode status, Object context) {
            System.out.println("IoT Hub responded to device method operation with status " + status.name());
            emitHelper.log(getReactContext(), "IoT Hub responded to device method operation with status " + status.name());
        }
    }

    ////--------------------------------------------------- Start Device Twin ------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    private boolean twinIsStarted = false;
    private static AtomicBoolean Succeed = new AtomicBoolean(false);

    private void StartDeviceTwin() throws IOException {
        emitHelper.log(getReactContext(), "Start Device Twin");
        Succeed.set(false);

        if (client != null && clientIsConnected && !twinIsStarted) {
            clientBusy = true;
            client.startDeviceTwin(new DeviceTwinStatusCallback(), null, new onPropertyCallback(), null);

            do {
                Thread.sleep(1000);
            }
            while (!Succeed.get());

            SubscribeToDesiredProperties();
            twinIsStarted = true;
            clientBusy = false;
        }
    }

    protected class DeviceTwinStatusCallback implements com.microsoft.azure.sdk.iot.device.IotHubEventCallback {
        @Override
        public void execute(IotHubStatusCode status, Object context) {
            if ((status == IotHubStatusCode.OK) || (status == IotHubStatusCode.OK_EMPTY)) {
                Succeed.set(true);
            } else {
                Succeed.set(false);
            }
            Log.d(this.getClass().getSimpleName(), "onDeviceTwinStatusCallback: " + status);
            WritableMap params = Arguments.createMap();
            params.putString("responseStatus", status.name());
            emitHelper.emit(getReactContext(), "onDeviceTwinStatusCallback", params);
        }

    }

    protected class onPropertyCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.TwinPropertyCallBack {
        @Override
        public void TwinPropertyCallBack(Property property, Object context) {
            Log.d(this.getClass().getSimpleName(), gson.toJson(property));
            WritableMap params = Arguments.createMap();
            params.putString("propertyJson", gson.toJson(property));
            emitHelper.emit(getReactContext(), "onDeviceTwinPropertyRetrieved", params);
        }
    }

    private void SubscribeToDesiredProperties() throws IOException {
        emitHelper.log(getReactContext(), "Subscribing To Desired Properties");
        if (_desiredPropertySubscriptions == null || _desiredPropertySubscriptions.size() == 0) {
            return;
        }
        Map<Property, Pair<TwinPropertyCallBack, Object>> subscriptions = new HashMap<>();
        for (int i = 0; i < _desiredPropertySubscriptions.size(); i++) {
            subscriptions.put(new Property(_desiredPropertySubscriptions.getString(i), null),
                    new Pair<TwinPropertyCallBack, Object>(onDesiredPropertyUpdate, null));
        }
        if (!subscriptions.isEmpty()) {
            client.subscribeToTwinDesiredProperties(subscriptions);
        }
    }


    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
////------------------------------------------------------- Send Reported Properties -----------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    @ReactMethod
    public void sendReportedProperties(ReadableArray array, Promise promise) {
        if (client != null && hasInternetConnection()) {
            SendReportedProperties(array, promise);
        }
    }

    private void SendReportedProperties(ReadableArray array, Promise promise) {
        emitHelper.log(getReactContext(), "SendReportedProperties");
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


    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
////------------------------------------------------------- Send Reported Property -------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    @ReactMethod
    public void sendReportedProperty(ReadableMap input, Callback success, Callback failure) {
        if (client != null && hasInternetConnection()) {
            SendReportedProperty(input, success, failure);
        }
    }

    private void SendReportedProperty(ReadableMap input, Callback success, Callback failure) {
        try {
            emitHelper.log(getReactContext(), "SendReportedProperty");
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


    ////--------------------------------------------------- @ReactMethod -----------------------------------------------////
////------------------------------------------------------- Send Message -----------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    ////----------------------------------------------------------------------------------------------------------------////
    @ReactMethod
    public void sendMessage(ReadableArray properties, String eventMessage, Promise promise) {
        try {
            if (client != null && !clientIsConnected && hasInternetConnection()) {
                Connect();
            }
            if (client != null && hasInternetConnection()) {
                Message sendMessage = CreateMessageToSend(properties, eventMessage);
                EventCallback eventCallback = new EventCallback();
                client.sendEventAsync(sendMessage, eventCallback, msgSentCount);
                msgSentCount++;
                //handler.post(updateRunnable);
                promise.resolve("Event Message sent Successfully!");
            } else {
                promise.resolve("unable to send Event Message!");
            }
        } catch (Exception e) {
            String message = "There was a problem sending Event Message. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            emitHelper.logError(getReactContext(), e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    private int msgSentCount = 0;
    private int receiptsConfirmedCount = 0;
    private int sendFailuresCount = 0;

    private Message CreateMessageToSend(ReadableArray properties, String eventMessage) {
        Message newMessageToSend = new Message(eventMessage);
        // set properties
        for (int i = 0; i < properties.size(); i++) {
            ReadableMap map = properties.getMap(i);
            String key = map.getString("key");
            Object value = getDynamicValue(map, "value");
            sendMessage.setProperty(key, value.toString());
        }
        newMessageToSend.setMessageId(java.util.UUID.randomUUID().toString());
        return newMessageToSend;
    }

    protected class EventCallback implements IotHubEventCallback {
        public void execute(IotHubStatusCode status, Object context) {
            Integer i = context instanceof Integer ? (Integer) context : 0;
            Log.d(this.getClass().getSimpleName(), "IoT Hub responded to message " + i.toString() + " with status " + status.name());

            String statusJson = status.toString();

            if ((status == IotHubStatusCode.OK) || (status == IotHubStatusCode.OK_EMPTY)) {
                receiptsConfirmedCount++;
                WritableMap params = Arguments.createMap();
                params.putString("status", statusJson);
                params.putString("receiptsConfirmedCount", Integer.toString(receiptsConfirmedCount));
                emitHelper.emit(getReactContext(), "onEventCallback", params);
            } else {
                sendFailuresCount++;
                WritableMap params = Arguments.createMap();
                params.putString("status", statusJson);
                params.putString("sendFailuresCount", Integer.toString(sendFailuresCount));
                emitHelper.emit(getReactContext(), "onEventCallback", params);
            }
        }
    }
}