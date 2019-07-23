package com.frank_vdm.iothub;

import android.os.SystemClock;
import android.util.Log;

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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class IoTHubDeviceModule extends ReactContextBaseJavaModule {

    private TwinPropertyCallBack onDesiredPropertyUpdate;

    private Gson gson = new Gson();

    @Override
    public String getName() {
        return "IoTHubDeviceModule";
    }

    public EmitHelper emitHelper = new EmitHelper();

    public ReactContext getReactContext() {
        return getReactApplicationContext();
    }

    public IoTHubDeviceModule(ReactApplicationContext reactContext) {
        super(reactContext);

        onDesiredPropertyUpdate = new OnDesiredPropertyUpdate(this, getReactApplicationContext());
    }

    @ReactMethod
    public void sendReportedProperties(ReadableArray array, Promise promise) {
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
            ;
        }
    }

    @ReactMethod
    public void sendReportedProperty(ReadableMap input, Callback success, Callback failure) {
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

    private IotHubEventCallback onDeviceTwinStatusCallback() {
        return new IotHubEventCallback() {
            @Override
            public void execute(IotHubStatusCode responseStatus, Object callbackContext) {
                Log.d(this.getClass().getSimpleName(), "onDeviceTwinStatusCallback: " + responseStatus);
                WritableMap params = Arguments.createMap();
                params.putString("responseStatus", responseStatus.name());
                emitHelper.emit(getReactContext(), "onDeviceTwinStatusCallback", params);
            }
        };
    }

    private TwinPropertyCallBack onDeviceTwinPropertyRetrieved() {
        return new TwinPropertyCallBack() {
            @Override
            public void TwinPropertyCallBack(Property property, Object context) {
                Log.d(this.getClass().getSimpleName(), gson.toJson(property));
                WritableMap params = Arguments.createMap();
                params.putString("propertyJson", gson.toJson(property));
                emitHelper.emit(getReactContext(), "onDeviceTwinPropertyRetrieved", params);
            }
        };
    }

    private MessageCallback onMessageCallback() {
        return new MessageCallback() {
            @Override
            public IotHubMessageResult execute(Message message, Object callbackContext) {
                String messageString = new String(message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET);
                Log.d(this.getClass().getSimpleName(), messageString);
                WritableMap params = Arguments.createMap();
                params.putString("message", messageString);
                params.putString("messageId", message.getMessageId());
                emitHelper.emit(getReactContext(), "onMessageReceived", params);
                return IotHubMessageResult.COMPLETE;
            }
        };
    }


    ////----------------------------------------------------------------------------------------------------------------////
////--------------------------------------- (STABLE VERSION) IOT HUB CONNECTION ----------------------------------------////
////--------------------------------------------------------------------------------------------------------------------////
    @ReactMethod
    public void connectToHub(String connectionString, ReadableArray desiredPropertySubscriptions, Boolean shouldRetry, Promise promise) {
        Thread connection = new Thread(new ConnectionRunnable(connectionString, desiredPropertySubscriptions, shouldRetry, promise));
        connection.start();
    }

    @ReactMethod
    public void disconnectFromHub(Promise promise) {
        CloseClient(promise);
        client = null;
        promise.resolve("Discconnected!");
    }


    ////----------------------------------------------------------------------------------------------------------------////
////------------------------------------------- (Runnable) Connection Client  ------------------------------------------////
////--------------------------------------------------------------------------------------------------------------------////

    class ConnectionRunnable implements Runnable {
        String _connectionString;
        ReadableArray _desiredPropertySubscriptions;
        Boolean _shouldRetry;
        Promise _promise;

        public ConnectionRunnable(String connectionString, ReadableArray desiredPropertySubscriptions, Boolean shouldRetry, Promise promise) {
            _connectionString = connectionString;
            _desiredPropertySubscriptions = desiredPropertySubscriptions;
            _shouldRetry = shouldRetry;
            _promise = promise;
        }

        public void run() {
            try {
                InitializeIotHubClient(_connectionString, _promise);
                AddLifeCycleListerner(_promise);
                setConnectionStatusChangeCallback();
                OpenConnection(_shouldRetry, _promise);
                StartIotDeviceTwin(_promise);
                SetMessageCallBack(_promise);
                SubscribeToDesiredProperties(_desiredPropertySubscriptions, _promise);
                initialized = true;
                _promise.resolve("Successfully connected!");
            } catch (Exception e) {
                if (client != null) {
                    CloseClient(_promise);
                }
                twinIsStarted = false;
                messageCallbackIsSet = false;
                isSubscribeToDesiredProperties = false;
                initialized = false;
                String message = "There was a problem connecting to the hub, but this should never be called. " + e.getMessage();
                Log.e(this.getClass().getSimpleName(), message, e);
                _promise.reject(this.getClass().getSimpleName(), e);
            }
        }
    }

    ////----------------------------------------------------------------------------------------------------------------////
////-------------------------------------------- IOT HUB CONNECTION PROCESS --------------------------------------------////
////--------------------------------------------------------------------------------------------------------------------////
    DeviceClient client;
    IotHubEventCallback onDeviceTwinStatusCallback = onDeviceTwinStatusCallback();
    TwinPropertyCallBack twinPropertyCallBack = onDeviceTwinPropertyRetrieved();
    MessageCallback onMessageCallback = onMessageCallback();

    long retryAfter = 1000;
    long retryMultiplier = 2;

    private boolean initialized = false;
    private boolean connectionStatusChangeCallbackIsSet = false;
    private boolean twinIsStarted = false;
    private boolean messageCallbackIsSet = false;
    private boolean isSubscribeToDesiredProperties = false;

    private static final String CLIENT_NULL = "IOT Hub Client Not Initialized.";

    private void InitializeIotHubClient(String connectionString, Promise promise) {
        try {
            if (client == null) {
                client = new DeviceClient(connectionString, IotHubClientProtocol.AMQPS_WS);
            }
        } catch (URISyntaxException uriSyntaxException) {
            client = null;
            String message = "The connection string is Malformed. " + uriSyntaxException.getMessage();
            Log.e(this.getClass().getSimpleName(), message, uriSyntaxException);
            promise.reject(this.getClass().getSimpleName(), uriSyntaxException);
        } catch (Exception e) {
            client = null;
            String message = "There was a problem initiating the IOT hub Device Client. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    private void AddLifeCycleListerner(Promise promise) {
        if (client != null) {
            getReactApplicationContext().addLifecycleEventListener(new IoTHubLifecycleEventListener(this, promise));
        } else {
            String message = CLIENT_NULL + ", Call 'connectToHub' first, before trying to user the client";
            Log.e(this.getClass().getSimpleName(), message);
            promise.reject(this.getClass().getSimpleName(), message);
        }
    }

    private void setConnectionStatusChangeCallback() {
        if (client != null && !connectionStatusChangeCallbackIsSet) {
            client.registerConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallback() {
                @Override
                public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext) {
                    Log.d(this.getClass().getSimpleName(), "status: " + status + " reason: " + statusChangeReason);
                    WritableMap params = Arguments.createMap();
                    params.putString("status", status.name());
                    params.putString("statusChangeReason", statusChangeReason.name());
                    emitHelper.emit(getReactContext(), "onConnectionStatusChange", params);
                }
            }, null);
            connectionStatusChangeCallbackIsSet = true;
        }
    }

    private void OpenConnection(Boolean shouldRetry, Promise promise) {
        try {
            if (client != null) {
                try {
                    OpenClient(promise);
                } catch (Exception e) {
                    if (shouldRetry) {
                        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "TransportException: Timed out waiting to connect to service")) {
                            Log.w(this.getClass().getSimpleName(), ExceptionUtils.getRootCauseMessage(e) + ". Reconnecting in " + (retryAfter / 1000) + " seconds.");
                            SystemClock.sleep(retryAfter);
                            retryAfter = retryAfter * retryMultiplier;
                        } else {
                            throw e;
                        }
                    } else {
                        throw e;
                    }
                }
            } else {
                String message = CLIENT_NULL + ", Call 'connectToHub' first, before trying to user the client";
                Log.e(this.getClass().getSimpleName(), message);
                promise.reject(this.getClass().getSimpleName(), message);
            }
        } catch (Exception e) {
            CloseClient(promise);
            String message = "There was a problem connecting to the IOT Hub. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    private void StartIotDeviceTwin(Promise promise) {
        try {
            if (client != null && !twinIsStarted) {
                client.startDeviceTwin(onDeviceTwinStatusCallback, null, twinPropertyCallBack, null);
                twinIsStarted = true;
            }
        } catch (Exception e) {
            twinIsStarted = false;
            String message = "There was a problem starting Device Twin. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    private void SetMessageCallBack(Promise promise) {
        try {
            if (client != null && !messageCallbackIsSet) {
                client.setMessageCallback(onMessageCallback, null);
                messageCallbackIsSet = true;
            }
        } catch (Exception e) {
            messageCallbackIsSet = false;
            String message = "There was a problem setting Message Callback. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    private void SubscribeToDesiredProperties(ReadableArray desiredPropertySubscriptions, Promise promise) {
        try {
            if (client != null && !isSubscribeToDesiredProperties) {
                subscribeToDesiredProperties(desiredPropertySubscriptions);
                isSubscribeToDesiredProperties = true;
            }
        } catch (Exception e) {
            isSubscribeToDesiredProperties = false;
            String message = "There was a problem subscribing to properties. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
    }

    ////----------------------------------------------------------------------------------------------------------------////
////-------------------------------------------- IOT HUB CONNECTION HELPERS --------------------------------------------////
////--------------------------------------------------------------------------------------------------------------------////

    public void CloseClient(Promise promise) {
        try {
            if (client != null) {
                client.closeNow();
            }
        } catch (IOException ioException) {
            String message = "cannot close client. " + ioException.getMessage();
            Log.e(this.getClass().getSimpleName(), message, ioException);
            promise.reject(this.getClass().getSimpleName(), ioException);
        }
    }

    public void OpenClient(Promise promise) {
        try {
            if (client != null) {
                client.open();
            } else {
                promise.reject(this.getClass().getSimpleName(), "IOT hub Client is not initialized");
            }
        } catch (IOException ioException) {
            String message = "cannot open client connection " + ioException.getMessage();
            Log.e(this.getClass().getSimpleName(), message, ioException);
            promise.reject(this.getClass().getSimpleName(), ioException);
        }
    }

    private void subscribeToDesiredProperties(ReadableArray desiredPropertySubscriptions) throws
            IOException {
        if (desiredPropertySubscriptions == null || desiredPropertySubscriptions.size() == 0) {
            return;
        }
        Map<Property, Pair<TwinPropertyCallBack, Object>> subscriptions = new HashMap<>();
        for (int i = 0; i < desiredPropertySubscriptions.size(); i++) {
            subscriptions.put(new Property(desiredPropertySubscriptions.getString(i), null),
                    new Pair<TwinPropertyCallBack, Object>(onDesiredPropertyUpdate, null));
        }
        if (!subscriptions.isEmpty()) {
            client.subscribeToTwinDesiredProperties(subscriptions);
        }
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        return constants;
    }

////--------------------------------------------------------------------------------------------------------------------////
////-------------------------------------------- NEW CONNECTION METHOD END ---------------------------------------------////
////--------------------------------------------------------------------------------------------------------------------////

    /// NEW METHODS IMPLEMENTED BY FRANCOIS VAN DER MERWE [START]


    private Message sendMessage;
    private int msgSentCount = 0;
    private int receiptsConfirmedCount = 0;
    private int sendFailuresCount = 0;


    @ReactMethod
    public void sendMessage(ReadableArray properties, String eventMessage, Promise promise) {
        try {
            OpenClient(promise);
            CreateMessageToSend(properties, eventMessage);
            EventCallback eventCallback = new EventCallback();
            client.sendEventAsync(sendMessage, eventCallback, msgSentCount);
            msgSentCount++;
            //handler.post(updateRunnable);
            promise.resolve("Event Message sent Successfully!");
        } catch (Exception e) {
//            if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "connections is closed")) {
//                OpenClient(promise);
//                sendMessage(properties, eventMessage, promise);
//            } else {
            String message = "There was a problem sending Event Message. " + e.getMessage();
            Log.e(this.getClass().getSimpleName(), message, e);
            promise.reject(this.getClass().getSimpleName(), e);
        }
        //}
    }

    private void CreateMessageToSend(ReadableArray properties, String eventMessage) {
        sendMessage = new Message(eventMessage);
        // set properties
        for (int i = 0; i < properties.size(); i++) {
            ReadableMap map = properties.getMap(i);
            String key = map.getString("key");
            Object value = getDynamicValue(map, "value");
            sendMessage.setProperty(key, value.toString());
        }
        sendMessage.setMessageId(java.util.UUID.randomUUID().toString());
    }


    class EventCallback implements IotHubEventCallback {
        public void execute(IotHubStatusCode status, Object context) {
            Integer i = context instanceof Integer ? (Integer) context : 0;
            Log.d(this.getClass().getSimpleName(), "IoT Hub responded to message " + i.toString() + " with status " + status.name());

            // ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            // String statusJson = ow.writeValueAsString(status);
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

//    private boolean isAppIsInBackground(Context context) {
//        boolean isInBackground = true;
//        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
//            List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
//            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
//                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
//                    for (String activeProcess : processInfo.pkgList) {
//                        if (activeProcess.equals(context.getPackageName())) {
//                            isInBackground = false;
//                        }
//                    }
//                }
//            }
//        } else {
//            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
//            ComponentName componentInfo = taskInfo.get(0).topActivity;
//            if (componentInfo.getPackageName().equals(context.getPackageName())) {
//                isInBackground = false;
//            }
//        }
//
//        return isInBackground;
//    }
/// NEW METHODS IMPLEMENTED BY FRANCOIS VAN DER MERWE[END]
////--------------------------------------------------------------------------------------------------------------------////
}
