import {NativeEventEmitter, NativeModules} from 'react-native';

export const {IoTHubDeviceModule} = NativeModules;


export async function connectToHub(connectionString, desiredPropertySubscriptions, onConnectionStatusChange, onDeviceTwinPropertyRetrieved, onMessageReceived, onDeviceTwinStatusCallback, onEventCallback, shouldRetry = true) {
    new NativeEventEmitter(IoTHubDeviceModule).addListener('onDesiredPropertyUpdate', (event) => {
        if (event.propertyJson) {
            const property = JSON.parse(event.propertyJson);
            onDesiredPropertyUpdate(property.property);
        }
    });

    new NativeEventEmitter(IoTHubDeviceModule).addListener('onConnectionStatusChange', (event) => {
        onConnectionStatusChange(event);
    });

    /**
     * All properties retrieved when first connected, and retrieved when an update is made remotely.
     */
    new NativeEventEmitter(IoTHubDeviceModule).addListener('onDeviceTwinPropertyRetrieved', (event) => {
        if (event && event.propertyJson) {
            try {
                onDeviceTwinPropertyRetrieved(JSON.parse(event.propertyJson));
            } catch (error) {
                console.error(error);
            }
        }
    });

    /**
     * When device operations are invoked from this device, IoT Hub will send response messages.
     */
    new NativeEventEmitter(IoTHubDeviceModule).addListener('onDeviceTwinStatusCallback', (event) => {
        onDeviceTwinStatusCallback(event);
    });

    new NativeEventEmitter(IoTHubDeviceModule).addListener('onMessageReceived', (event) => {
        onMessageReceived(event);
    });

    new NativeEventEmitter(IoTHubDeviceModule).addListener('onEventCallback', (event) => {
        onEventCallback(event);
    });

    new NativeEventEmitter(IoTHubDeviceModule).addListener('log', (event) => {
        console.log("IOT HUB INTERNAL LOG:", event.message);
    });

    new NativeEventEmitter(IoTHubDeviceModule).addListener('debug', (event) => {
        console.log("IOT HUB INTERNAL DEBUG LOG:", event.message);
    });

    new NativeEventEmitter(IoTHubDeviceModule).addListener('error', (event) => {
        console.log("IOT HUB INTERNAL ERROR:", event);
    });
    console.log('test', IoTHubDeviceModule);

    return await IoTHubDeviceModule.connectToHub(connectionString, desiredPropertySubscriptions);
}

export async function disconnectFromHub() {
    return await IoTHubDeviceModule.disconnectFromHub();
}

// export async function requestTwinProperties() {
//     return await IoTHubDeviceModule.requestTwinProperties();
// }


/**
 * Returns Promise. Doesn't actually return device twin - it makes a request to your hub
 * to send you all Device Twin properties via the onDeviceTwinPropertyRetrieved callback.
 *
 * @param propertyKey
 * @param success
 * @param failure
 * @returns {*}
 */
export function subscribeToTwinDesiredProperties(propertyKey, success, failure) {
    return IoTHubDeviceModule.subscribeToTwinDesiredProperties(propertyKey, success, failure);
}

/**
 * @param {Object[]} properties - Example input: {testValue:12345, testValue2:"12345", testValue3: true}

 * @returns {Promise}
 */
export function reportProperties(properties) {
    //translate simple json map to a key/value array

    const keyValueArray = [];
    Object.keys(properties).forEach(key => {
        keyValueArray.push({
            key,
            value: properties[key]
        });
    });

    return IoTHubDeviceModule.sendReportedProperties(keyValueArray);
}


/**
 * @param {Object[]} properties - Example input: {testValue:12345, testValue2:"12345", testValue3: true}
 @param {Object} eventJson - Json object to send as a message
 * @returns {Promise}
 */
export function sendMessage(properties, eventJson) {
    const keyValueArray = [];
    Object.keys(properties).forEach(key => {
        keyValueArray.push({
            key,
            value: properties[key]
        });
    });
    let message = JSON.stringify(eventJson);

    return IoTHubDeviceModule.sendMessage(keyValueArray, message);
}

