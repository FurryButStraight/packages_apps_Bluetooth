/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Defines the native inteface that is used by state machine/service to
 * send or receive messages from the native stack. This file is registered
 * for the native methods in the corresponding JNI C++ file.
 */
package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothDevice;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.internal.annotations.GuardedBy;

/**
 * A2DP Native Interface to/from JNI.
 */
public class A2dpNativeInterface {
    private static final String TAG = "A2dpNativeInterface";
    private static final boolean DBG = true;
    private BluetoothAdapter mAdapter;

    @GuardedBy("INSTANCE_LOCK")
    private static A2dpNativeInterface sInstance;
    private static final Object INSTANCE_LOCK = new Object();

    static {
        classInitNative();
    }

    @VisibleForTesting
    private A2dpNativeInterface() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            Log.wtf(TAG, "No Bluetooth Adapter Available");
        }
    }

    /**
     * Get singleton instance.
     */
    public static A2dpNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new A2dpNativeInterface();
            }
            return sInstance;
        }
    }

    /**
     * Initializes the native interface.
     *
     * @param codecConfigPriorities an array with the codec configuration
     * priorities to configure.
     */
    public void init(BluetoothCodecConfig[] codecConfigPriorities) {
        initNative(codecConfigPriorities);
    }

    /**
     * Cleanup the native interface.
     */
    public void cleanup() {
        cleanupNative();
    }

    /**
     * Initiates A2DP connection to a remote device.
     *
     * @param device the remote device
     * @return true on success, otherwise false.
     */
    public boolean connectA2dp(BluetoothDevice device) {
        return connectA2dpNative(getByteAddress(device));
    }

    /**
     * Disconnects A2DP from a remote device.
     *
     * @param device the remote device
     * @return true on success, otherwise false.
     */
    public boolean disconnectA2dp(BluetoothDevice device) {
        return disconnectA2dpNative(getByteAddress(device));
    }

    /**
     * Sets the codec configuration preferences.
     *
     * @param codecConfigArray an array with the codec configurations to
     * configure.
     * @return true on success, otherwise false.
     */
    public boolean setCodecConfigPreference(BluetoothCodecConfig[] codecConfigArray) {
        return setCodecConfigPreferenceNative(codecConfigArray);
    }

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(address);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void sendMessageToService(A2dpStackEvent event) {
        A2dpService service = A2dpService.getA2dpService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "Event ignored, service not available: " + event);
        }
    }

    // Callbacks from the native stack back into the Java framework.
    // All callbacks are routed via the Service which will disambiguate which
    // state machine the message should be routed to.

    private void onConnectionStateChanged(int state, byte[] address) {
        A2dpStackEvent event =
                new A2dpStackEvent(A2dpStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);

        if (DBG) {
            Log.d(TAG, "onConnectionStateChanged: " + event);
        }
        sendMessageToService(event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        A2dpStackEvent event = new A2dpStackEvent(A2dpStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);

        if (DBG) {
            Log.d(TAG, "onAudioStateChanged: " + event);
        }
        sendMessageToService(event);
    }

    private void onCodecConfigChanged(BluetoothCodecConfig newCodecConfig,
            BluetoothCodecConfig[] codecsLocalCapabilities,
            BluetoothCodecConfig[] codecsSelectableCapabilities) {
        if (DBG) {
            Log.d(TAG, "onCodecConfigChanged: " + newCodecConfig);
        }
        // TODO: We need to use A2dpStackEvent instead of specialized service calls.
        A2dpService service = A2dpService.getA2dpService();
        if (service != null) {
            service.onCodecConfigChangedFromNative(newCodecConfig,
                                                   codecsLocalCapabilities,
                                                   codecsSelectableCapabilities);
        } else {
            Log.w(TAG, "onCodecConfigChanged ignored: service not available");
        }
    }

    // Native methods that call into the JNI interface
    private static native void classInitNative();

    private native void initNative(BluetoothCodecConfig[] codecConfigPriorities);

    private native void cleanupNative();

    private native boolean connectA2dpNative(byte[] address);

    private native boolean disconnectA2dpNative(byte[] address);

    private native boolean setCodecConfigPreferenceNative(BluetoothCodecConfig[] codecConfigArray);
}