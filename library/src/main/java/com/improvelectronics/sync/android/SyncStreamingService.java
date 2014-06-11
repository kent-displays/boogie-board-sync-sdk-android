/*****************************************************************************
 Copyright © 2014 Kent Displays, Inc.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ****************************************************************************/

package com.improvelectronics.sync.android;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.improvelectronics.sync.Config;
import com.improvelectronics.sync.androidsdk.R;
import com.improvelectronics.sync.hid.HIDMessage;
import com.improvelectronics.sync.hid.HIDSetReport;
import com.improvelectronics.sync.hid.HIDUtilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * This service connects to the Boogie Board Sync devices and communicates with the Sync using a custom implementation of the HID protocol. All of the
 * connections are done automatically since this service is always running while Bluetooth is enabled. A client of this service can add a listener,
 * that implements {@link com.improvelectronics.sync.android.SyncStreamingListener #SyncStreamingListener},
 * to listen for changes of the streaming service as well as send commands to the connected Boogie Board Sync.
 * </p>
 * This service also handles all the notifications that are displayed when the Sync connects and disconnects. It is necessary to display these
 * notifications since the Android OS does not show a current Bluetooth connection with the Bluetooth icon in the status bar. Class also handles
 * the case when the user has outdated firmware and will direct them to a site with instructions on how to update the firmware.
 */
public class SyncStreamingService extends Service {

    private static final UUID LISTEN_UUID = UUID.fromString("d6a56f81-88f8-11e3-baa8-0800200c9a66");
    private static final UUID CONNECT_UUID = UUID.fromString("d6a56f80-88f8-11e3-baa8-0800200c9a66");
    private static final int NOTIFICATION_ID = 1313;
    private static final int FIRMWARE_NOTIFICATION_ID = 1316;
    private static final String TAG = SyncStreamingService.class.getSimpleName();
    private static final boolean DEBUG = Config.DEBUG;
    private BluetoothAdapter mBluetoothAdapter;
    private final IBinder mBinder = new SyncStreamingBinder();
    private List<SyncStreamingListener> mListeners;
    private int mState, mMode;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private AcceptThread mAcceptThread;
    private List<BluetoothDevice> mPairedDevices;
    private List<SyncPath> mPaths;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;

    // Used for updating the local time of the Sync.
    private static final int YEAR_OFFSET = 1980;

    // Communication with background thread.
    private MessageHandler mMessageHandler;
    private static final int MESSAGE_DATA = 13;
    private static final int MESSAGE_CONNECTED = 14;
    private static final int MESSAGE_CONNECTION_BROKEN = 15;
    private static final int MESSAGE_BLUETOOTH_HACK = 16;

    /**
     * The Sync streaming service is in connected state.
     */
    public static final int STATE_CONNECTED = 0;

    /**
     * The Sync streaming service is in connecting state.
     */
    public static final int STATE_CONNECTING = 1;

    /**
     * The Sync streaming service is in disconnected state.
     */
    public static final int STATE_DISCONNECTED = 2;

    /**
     * The Sync streaming service is in listening state.
     */
    public static final int STATE_LISTENING = 4;

    /**
     * This mode tells the Sync to not report any information and be silent to the client. This greatly saves battery life of the Sync and the Android
     * device.
     */
    public static final int MODE_NONE = 1;

    /**
     * This mode tells the Sync to report every button push and path to the client.
     */
    public static final int MODE_CAPTURE = 4;

    /**
     * This mode tells the Sync to only inform the client when it has saved a file.
     */
    public static final int MODE_FILE = 5;

    private static final String ACTION_BASE = "com.improvelectronics.sync.android.SyncStreamingService.action";

    /**
     * Broadcast Action: Button was pushed on the Sync.
     */
    public static final String ACTION_BUTTON_PUSHED = ACTION_BASE + ".BUTTON_PUSHED";

    /**
     * Broadcast Action: The state of the Sync streaming service changed.
     */
    public static final String ACTION_STATE_CHANGED = ACTION_BASE + ".STATE_CHANGED";

    /**
     * Used as an int extra field in {@link #ACTION_BUTTON_PUSHED} intents for button push from the Sync.
     */
    public static final String EXTRA_BUTTON_PUSHED = "EXTRA_BUTTON_PUSHED";

    /**
     * Used as an int extra field in {@link #ACTION_STATE_CHANGED} intents for current state.
     */
    public static final String EXTRA_STATE = "EXTRA_STATE";

    /**
     * Used as an int extra field in {@link #ACTION_STATE_CHANGED} intents for previous state.
     */
    public static final String EXTRA_PREVIOUS_STATE = "PREVIOUS_STATE";

    /**
     * Used as an BluetoothDevice extra field in {@link #ACTION_STATE_CHANGED} intents for when streaming service reports a
     * connected state.
     */
    public static final String EXTRA_DEVICE = "EXTRA_DEVICE";

    /**
     * Used as an int extra for when the save button is pushed.
     */
    public static final int SAVE_BUTTON = 13;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");

        // Set the default properties.
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mMessageHandler = new MessageHandler(Looper.getMainLooper());
        mPairedDevices = new ArrayList<BluetoothDevice>();
        mPaths = new ArrayList<SyncPath>();
        mListeners = new ArrayList<SyncStreamingListener>();
        mState = STATE_DISCONNECTED;
        mMode = MODE_NONE;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationBuilder = new NotificationCompat.Builder(this);
        mNotificationBuilder.setSmallIcon(R.drawable.ic_stat_notify_default);
        removeNotification();
        setupIntentFilter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth adapter isn't available.  The client of the service is supposed to
            // verify that it is available and activate before invoking this service.
            Log.e(TAG, "stopping sync streaming service, device does not have Bluetooth or Bluetooth is turned off");
            stopSelf();
        } else {
            updatePairedDevices();
            start();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy");

        // Stop all running threads.
        stop();

        // Clean up receivers.
        unregisterReceiver(mMessageReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void broadcastStateChange(int state, int previousState) {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra(EXTRA_PREVIOUS_STATE, previousState);
        if (mPairedDevices.size() > 0) {
            intent.putExtra(EXTRA_DEVICE, mPairedDevices.get(0));
        }
        sendBroadcast(intent);
    }

    private void broadcastButtonPush(int button) {
        Intent intent = new Intent(ACTION_BUTTON_PUSHED);
        intent.putExtra(EXTRA_BUTTON_PUSHED, button);
        sendBroadcast(intent);
    }

    /**
     * Returns the current state of the Sync streaming service.
     *
     * @return state
     */
    public int getState() {
        return mState;
    }

    /**
     * Returns the currently connected {@link BluetoothDevice}.
     *
     * @return device that is connected, returns null if there is no device connected
     */
    public BluetoothDevice getConnectedDevice() {
        if (mState != STATE_CONNECTED) return null;
        else return mPairedDevices.get(0);
    }

    /**
     * Start the streaming service. Check to see if we have paired devices and connect if necessary.
     */
    private synchronized void start() {
        if (DEBUG) Log.d(TAG, "start");

        if(mPairedDevices.size() > 0) {
            // Start the thread to listen on a BluetoothServerSocket
            if (mAcceptThread == null) {
                mAcceptThread = new AcceptThread();
                mAcceptThread.start();
            }

            // Only change state to listening if we are disconnected.
            if(mState == STATE_DISCONNECTED) updateDeviceState(STATE_LISTENING);

            if(mState != STATE_CONNECTED && mState != STATE_CONNECTING) {
                connect(mPairedDevices.get(0));
            }
        } else {
            stop();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    private synchronized void connect(BluetoothDevice device) {
        if (DEBUG) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        updateDeviceState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     */
    private synchronized void connected(BluetoothSocket socket) {
        if (DEBUG) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection.
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection.
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start listening thread if there is no one already running.
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }

        // Start the thread to manage the connection and perform transmissions.
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        startBluetoothHack();

        updateDeviceState(STATE_CONNECTED);
    }

    /**
     * Stop all threads.
     */
    private synchronized void stop() {
        if (DEBUG) Log.d(TAG, "stop");

        stopBluetoothHack();

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        updateDeviceState(STATE_DISCONNECTED);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    private boolean write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return false;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
        return true;
    }

    /**
     * Erases the Boogie Board Sync's screen.
     *
     * @return an immediate check if the message could be sent.
     */
    public boolean eraseSync() {
        if (mState != STATE_CONNECTED) return false;

        if (DEBUG) Log.d(TAG, "writing message to erase Boogie Board Sync's screen");

        // Clean up paths.
        mPaths.clear();

        // Create the HID message to be sent to the Sync to erase the screen.
        byte ERASE_MODE = 0x01;
        HIDSetReport setReport = new HIDSetReport(HIDSetReport.TYPE_FEATURE, HIDSetReport.ID_OPERATION_REQUEST, new byte[]{ERASE_MODE});

        return write(setReport.getPacketBytes());
    }

    /**
     * Updates the Boogie Board Sync's local time with the time of the device currently connected to it.
     *
     * @return an immediate check if the message could be sent.
     */
    private boolean updateSyncTimeWithLocalTime() {
        if (mState != STATE_CONNECTED) return false;

        // Construct the byte array for the time.
        Calendar calendar = Calendar.getInstance();
        int second = calendar.get(Calendar.SECOND) / 2;
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH) + 1;
        int year = calendar.get(Calendar.YEAR) - YEAR_OFFSET;

        byte byte1 = (byte) ((minute << 5) | second);
        byte byte2 = (byte) ((hour << 3) | (minute >> 3));
        byte byte3 = (byte) ((month << 5) | day);
        byte byte4 = (byte) ((year << 1) | (month >> 3));

        // Create the HID message to be sent to the Sync to set the time.
        HIDSetReport setReport = new HIDSetReport(HIDSetReport.TYPE_FEATURE, HIDSetReport.ID_DATE, new byte[]{byte1, byte2, byte3,
                byte4});
        if (DEBUG) Log.d(TAG, "writing message to update Boogie Board Sync's time");
        return write(setReport.getPacketBytes());
    }

    /**
     * Sets the Boogie Board Sync into the specified mode.
     *
     * @param mode to put the Boogie Board Sync in.
     * @return an immediate check if the message could be sent.
     */
    public boolean setSyncMode(int mode) {
        // Check to see if a valid mode was sent.
        if (mMode == mode || mode < MODE_NONE || mode > MODE_FILE || mState != STATE_CONNECTED)
            return false;

        // Create the HID message to be sent to the Sync to change its mode.
        HIDSetReport setReport = new HIDSetReport(HIDSetReport.TYPE_FEATURE, HIDSetReport.ID_MODE, new byte[]{(byte) mode});
        if (DEBUG) Log.d(TAG, "writing message to set Boogie Board Sync into different mode");
        if (write(setReport.getPacketBytes())) {
            mMode = mode;
            return true;
        } else {
            return false;
        }
    }

    public List<BluetoothDevice> getPairedDevices() {
        return mPairedDevices;
    }

    /**
     * Returns a list of paths that the Sync currently have drawn on it.
     *
     * @return paths
     */
    public List<SyncPath> getPaths() {
        return mPaths;
    }

    /**
     * Tells the Boogie Board Sync what device is currently connected to it.
     *
     * @return an immediate check if the message could be sent.
     */
    private boolean informSyncOfDevice() {
        if (mState != STATE_CONNECTED) return false;

        // Create the HID message to be sent to the Sync to tell the Sync what device this is.
        byte ANDROID_DEVICE = 8;
        HIDSetReport setReport = new HIDSetReport(HIDSetReport.TYPE_FEATURE, HIDSetReport.ID_DEVICE, new byte[]{ANDROID_DEVICE, 0x00,
                0x00, 0x00});
        if (DEBUG) Log.d(TAG, "writing message to inform Boogie Board Sync what device we are");
        return write(setReport.getPacketBytes());
    }

    private void updatePairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices == null || pairedDevices.size() == 0) return;

        if (DEBUG) Log.d(TAG, "searching for paired Syncs");

        mPairedDevices.clear();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName() != null && device.getName().equals("Sync")) {
                if (DEBUG) Log.d(TAG, "found a Boogie Board Sync");
                mPairedDevices.add(device);
            }
        }

        // Connect to the first device that was found in the list of paired devices.
        if (mPairedDevices.size() > 0 && mState != STATE_CONNECTED) {
            BluetoothDevice device = mPairedDevices.get(0);

            // Device must first be checked to see if it has the most up to date firmware.
            // This check is done by ensuring the device has all the correct UUIDs.
            // If it doesn't have the CONNECT_UUID then the firmware is not the latest.
            boolean foundUuid = false;
            for (ParcelUuid uuid : device.getUuids()) {
                if (uuid.getUuid().equals(CONNECT_UUID)) foundUuid = true;
            }
            if (!foundUuid) {
                showUpdateFirmwareNotification();
            }
        }
    }

    /**
     * Adds a listener to the Sync streaming service. Listener is used for state changes and asynchronous callbacks from streaming commands.
     * Remember to remove
     * the listener with {@link #removeListener(SyncStreamingListener)} when finished.
     *
     * @param listener Class that implements SyncStreamingListener for asynchronous callbacks.
     * @return false indicates listener has already been added
     */
    public boolean addListener(SyncStreamingListener listener) {
        if (mListeners.contains(listener)) return false;
        else mListeners.add(listener);
        return true;
    }

    /**
     * Removes a listener that was previously added with {@link #addListener(SyncStreamingListener)}.
     *
     * @param listener Class that implements SyncStreamingListener for asynchronous callbacks.
     * @return false indicates listener was not originally added
     */
    public boolean removeListener(SyncStreamingListener listener) {
        if (!mListeners.contains(listener)) return false;
        else mListeners.remove(listener);
        return true;
    }

    private void setupIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mMessageReceiver, intentFilter);
    }

    public class SyncStreamingBinder extends Binder {
        public SyncStreamingService getService() {
            // Return this instance of LocalService so clients can call public methods.
            return SyncStreamingService.this;
        }
    }

    private void updateDeviceState(int newState) {
        if (newState == mState) return;
        if (DEBUG) Log.d(TAG, "device state changed from " + mState + " to " + newState);

        int oldState = mState;
        mState = newState;

        // Clean up objects when there is a disconnection.
        if (newState == STATE_DISCONNECTED) {
            // Reset the mode of the Boogie Board Sync.
            mMode = MODE_NONE;
            mPaths.clear();

            if (oldState == STATE_CONNECTED) showDisconnectionNotification();
        } else if (newState == STATE_CONNECTED) {
            setSyncMode(MODE_FILE);
            updateSyncTimeWithLocalTime();
            informSyncOfDevice();
            showConnectionNotification(true);
        }

        broadcastStateChange(mState, oldState);

        for (SyncStreamingListener listener : mListeners) {
            listener.onStreamingStateChange(oldState, newState);
        }
    }

    private class MessageHandler extends Handler {

        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            // Parse the message that was returned from the background thread.
            if (message.what == MESSAGE_DATA) {
                byte[] buffer = (byte[]) message.obj;
                int numBytes = message.arg1;

                List<HIDMessage> hidMessages = HIDUtilities.parseBuffer(buffer, numBytes);

                if (hidMessages == null) return;

                // Received a capture report.
                for (HIDMessage hidMessage : hidMessages) {
                    if (hidMessage == null) {
                        Log.e(TAG, "was unable to parse the returned message from the Sync");
                    } else if (hidMessage instanceof SyncCaptureReport) {
                        SyncCaptureReport captureReport = (SyncCaptureReport) hidMessage;
                        for (SyncStreamingListener listener : mListeners) listener.onCaptureReport(captureReport);

                        // Filter the paths that are returned from the Boogie Board Sync.
                        List<SyncPath> paths = Filtering.filterSyncCaptureReport(captureReport);
                        if (paths.size() > 0) {
                            for (SyncStreamingListener listener : mListeners) listener.onDrawnPaths(paths);
                            mPaths.addAll(paths);
                        }

                        // Erase button was pushed.
                        if (captureReport.hasEraseSwitchFlag()) {
                            mPaths.clear();
                            for (SyncStreamingListener listener : mListeners) listener.onErase();
                        }

                        // Save button was pushed.
                        if (captureReport.hasSaveFlag()) {
                            for (SyncStreamingListener listener : mListeners) listener.onSave();

                            // Dispatch a broadcast.
                            broadcastButtonPush(SAVE_BUTTON);
                        }
                    }
                }
            }

            // Connected to a device from the accept or connect thread.
            // Passed object will be a socket.
            else if (message.what == MESSAGE_CONNECTED) {
                connected((BluetoothSocket) message.obj);
            }

            // Disconnected from the device on a worker thread.
            else if (message.what == MESSAGE_CONNECTION_BROKEN) {
                // Update the state of the device, want to show the disconnection notification and then pop into listening mode since the accept
                // thread should still be running.
                updateDeviceState(STATE_DISCONNECTED);
                updateDeviceState(STATE_LISTENING);

                stopBluetoothHack(); // Don't need to keep transmitting hack.
            }

            // Bluetooth hack, see reference below.
            else if (message.what == MESSAGE_BLUETOOTH_HACK) {
                // Only transmit, if we are in capture mode.
                if (mMode != MODE_CAPTURE) return;

                if (DEBUG) Log.d(TAG, "transmitting bluetooth hack");

                if (!write(DUMMY_PACKET)) stopBluetoothHack();
            }
        }
    }

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;

            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                int prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);

                if (prevState == BluetoothAdapter.STATE_ON && newState == BluetoothAdapter.STATE_TURNING_OFF) {
                    stopSelf();
                }
            } else if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // Bonded to new device. Check to see if it is a Sync device.
                if (prevState == BluetoothDevice.BOND_BONDING && newState == BluetoothDevice.BOND_BONDED) {
                    if (device != null && device.getName() != null && device.getName().equals("Sync")) {
                        updatePairedDevices();
                        start();
                    }
                } else if (prevState == BluetoothDevice.BOND_BONDED && newState == BluetoothDevice.BOND_NONE) {
                    if (device != null && device.getName() != null && device.getName().equals("Sync")) {
                        updatePairedDevices();
                        start();
                    }
                }
            }
        }
    };

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket.
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("Sync Streaming Profile", LISTEN_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            mServerSocket = tmp;
        }

        public void run() {
            // Server socket could be null if Bluetooth was turned off and it threw an IOException
            if (mServerSocket == null) {
                Log.e(TAG, "server socket is null, finish the accept thread");
                return;
            }

            if (DEBUG) Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");

            BluetoothSocket socket;
            while (true) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (SyncStreamingService.this) {
                        // Normal operation.
                        if (mState == STATE_LISTENING || mState == STATE_DISCONNECTED) {
                            mMessageHandler.obtainMessage(MESSAGE_CONNECTED, socket).sendToTarget();
                        }

                        // Either not ready or already connected. Terminate new socket.
                        else if (mState == STATE_CONNECTED) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                        }
                    }
                }
            }
            if (DEBUG) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (DEBUG) Log.d(TAG, "cancel " + this);
            try {
                if (mServerSocket != null) mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(CONNECT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                mMessageHandler.obtainMessage(MESSAGE_CONNECTION_BROKEN).sendToTarget();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (SyncStreamingService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            mMessageHandler.obtainMessage(MESSAGE_CONNECTED, mSocket).sendToTarget();
        }

        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ");
            mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mInputStream = tmpIn;
            mOutputStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mInputStream.read(buffer);

                    // Send the obtained bytes to the main thread to be processed.
                    mMessageHandler.obtainMessage(MESSAGE_DATA, bytes, -1, buffer).sendToTarget();

                    // Reset buffer.
                    buffer = new byte[1024];
                } catch (IOException e) {
                    mMessageHandler.obtainMessage(MESSAGE_CONNECTION_BROKEN).sendToTarget();
                    if (DEBUG) Log.d(TAG, "disconnected", e);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutputStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private void showConnectionNotification(boolean showTicker) {
        // Send notification that we are now connected.
        mNotificationBuilder.setProgress(0, 0, false);
        mNotificationBuilder.setContentTitle(getResources().getString(R.string.connection_notification_title))
                .setContentText(getResources().getString(R.string.connection_notification_text))
                .setOngoing(true);

        if (showTicker) {
            mNotificationBuilder.setTicker(getResources().getString(R.string.connection_notification_ticker));
        } else {
            mNotificationBuilder.setTicker(null);
        }

        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void showDisconnectionNotification() {
        mNotificationBuilder.setProgress(0, 0, false);
        mNotificationBuilder.setContentTitle(getResources().getString(R.string.disconnection_notification_title))
                .setTicker(getResources().getString(R.string.disconnection_notification_ticker))
                .setContentText(getResources().getString(R.string.disconnection_notification_ticker))
                .setOngoing(false);
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void showUpdateFirmwareNotification() {
        mNotificationBuilder.setSmallIcon(R.drawable.ic_stat_notify_default);
        mNotificationBuilder.setTicker(getResources().getString(R.string.update_firmware_notification_title));
        mNotificationBuilder.setContentTitle(getResources().getString(R.string.update_firmware_notification_title));
        mNotificationBuilder.setContentText(getResources().getString(R.string.update_firmware_notification_text));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://improvelectronics.com/support/boogie-board-firmware-update.html"));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        mNotificationBuilder.setContentIntent(pendingIntent);
        mNotificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(getResources().getString(R.string
                .update_firmware_notification_big_text)));
        mNotificationManager.notify(FIRMWARE_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void removeNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

    /**
     * On Android v0.4.3+, if you are constantly reading data from an input stream (using Bluetooth API) and are never sending any data over
     * the output stream this shows up in the console log.
     * <p/>
     * W/bt-btif﹕ dm_pm_timer expires
     * W/bt-btif﹕ dm_pm_timer expires 0
     * W/bt-btif﹕ proc dm_pm_timer expires
     * <p/>
     * One can assume that there is a timer set to ensure there is back and forth communication between a Bluetooth device. Once it is hit, the
     * input stream drops a lot of frames and some of the frames read are even corrupted.
     * <p/>
     * To combat this, every few seconds a FEND is sent to keep this timer alive and to ensure it does not expire. A.K.A. Bluetooth Hack
     * <p/>
     * Similar problem: http://stackoverflow.com/a/18508694
     */

    private Timer mBluetoothHackTimer;
    private TimerTask mBluetoothHackTimerTask;
    private final byte[] DUMMY_PACKET = new byte[]{(byte) 0xC0}; // Dummy packet just contains a frame end.

    private void startBluetoothHack() {
        if (Build.VERSION.SDK_INT < 18) return;

        mBluetoothHackTimer = new Timer();
        mBluetoothHackTimerTask = new TimerTask() {
            public void run() {
                mMessageHandler.obtainMessage(MESSAGE_BLUETOOTH_HACK).sendToTarget();
            }
        };

        int DELAY = 3000;
        mBluetoothHackTimer.scheduleAtFixedRate(mBluetoothHackTimerTask, DELAY, DELAY);
    }

    private void stopBluetoothHack() {
        if (Build.VERSION.SDK_INT < 18) return;

        if (mBluetoothHackTimer != null) {
            mBluetoothHackTimer.cancel();
            mBluetoothHackTimer.purge();
        }
    }
}