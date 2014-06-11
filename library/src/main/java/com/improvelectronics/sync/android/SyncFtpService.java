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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.improvelectronics.sync.Config;
import com.improvelectronics.sync.obex.OBEXFtpFolderListingItem;
import com.improvelectronics.sync.obex.OBEXFtpHeader;
import com.improvelectronics.sync.obex.OBEXFtpRequest;
import com.improvelectronics.sync.obex.OBEXFtpResponse;
import com.improvelectronics.sync.obex.OBEXFtpUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * This service connects to the Boogie Board Sync devices and communicates with the Sync using the OBEX Bluetooth File Transfer protocol. All of the
 * connections are done automatically since this service is always running while Bluetooth is enabled. A client of this service can add a listener,
 * that implements SyncFtpListener, to listen for changes of the service as well as send FTP commands to the connected Boogie Board Sync.
 * <p/>
 * Currently, this service only allows one device to be connected at a time and only one client may send FTP commands at a time. When a client is
 * finished using FTP make sure to call {@link #disconnect() disconnect}.
 */

public class SyncFtpService extends Service {

    private static final UUID FTP_UUID = UUID.fromString("00001106-0000-1000-8000-00805f9b34fb");
    private static final String TAG = "SyncFtpService";
    private final IBinder mBinder = new SyncFtpBinder();
    private BluetoothAdapter mBluetoothAdapter;
    private int mState;
    private int mConnectionId;
    private List<SyncFtpListener> mListeners;
    private static final boolean DEBUG = Config.DEBUG;
    private OBEXFtpFolderListingItem deleteFile, fileBeingDownloaded;
    private Uri mDirectoryUri;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private List<BluetoothDevice> mPairedDevices;
    private static final String ACTION_BASE = "com.improvelectronics.sync.android.SyncFtpService.action";

    /**
     * Broadcast Action: The state of the Sync FTP service changed.
     */
    public static final String ACTION_STATE_CHANGED = ACTION_BASE + ".STATE_CHANGED";

    /**
     * Used as an int extra field in {@link #ACTION_STATE_CHANGED ACTION_STATE_CHANGED} intents for current state.
     */
    public static final String EXTRA_STATE = "EXTRA_STATE";

    /**
     * Used as an int extra field in {@link #ACTION_STATE_CHANGED ACTION_STATE_CHANGED} intents for previous state.
     */
    public static final String EXTRA_PREVIOUS_STATE = "EXTRA_PREVIOUS_STATE";

    // Communication with background thread.
    private MessageHandler mMessageHandler;
    private static final int MESSAGE_CONNECTED = 14;
    private static final int MESSAGE_CONNECTION_BROKEN = 15;
    private static final int MESSAGE_ACTION = 16;
    private static final int MESSAGE_STATE = 17;

    private static final int ACTION_CONNECT = 1;
    private static final int ACTION_DISCONNECT = 2;
    private static final int ACTION_PUT = 3;
    private static final int ACTION_SET_PATH = 4;
    private static final int ACTION_GET_FILE = 5;
    private static final int ACTION_GET_DIRECTORY = 6;

    /**
     * The Sync FTP service is in connected state.
     */
    public static final int STATE_CONNECTED = 0;

    /**
     * The Sync FTP service is in connecting state.
     */
    public static final int STATE_CONNECTING = 1;

    /**
     * The Sync FTP service is in disconnected state.
     */
    public static final int STATE_DISCONNECTED = 2;

    /**
     * Indicates a FTP command was successful.
     */
    public static final int RESULT_OK = 0;

    /**
     * Indicates a FTP command was not successful.
     */
    public static final int RESULT_FAIL = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");

        // Set the default properties.
        mState = STATE_DISCONNECTED;
        mMessageHandler = new MessageHandler(Looper.getMainLooper());
        mDirectoryUri = null;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mPairedDevices = new ArrayList<BluetoothDevice>();
        mListeners = new ArrayList<SyncFtpListener>();
        mConnectionId = -1;

        setupIntentFilter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth adapter isn't available.  The client of the service is supposed to
            // verify that it is available and activate before invoking this service.
            Log.e(TAG, "stopping sync ftp service, device does not have Bluetooth or Bluetooth is turned off");
            stopSelf();
        } else {
            findPairedDevices();
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

    /**
     * Adds a listener to the Sync FTP service. Listener is used for state changes and asynchronous callbacks from FTP commands. Remember to remove
     * the listener with {@link #removeListener(SyncFtpListener)} when finished.
     *
     * @param listener Class that implements SyncFtpListener for asynchronous callbacks.
     * @return false indicates listener has already been added
     */
    public boolean addListener(SyncFtpListener listener) {
        if (mListeners.contains(listener)) return false;
        else mListeners.add(listener);
        return true;
    }

    /**
     * Removes a listener that was previously added with {@link #addListener(SyncFtpListener)}.
     *
     * @param listener Class that implements SyncFtpListener for asynchronous callbacks.
     * @return false indicates listener was not originally added
     */
    public boolean removeListener(SyncFtpListener listener) {
        if (!mListeners.contains(listener)) return false;
        else mListeners.remove(listener);
        return true;
    }

    private void broadcastStateChange(int state, int previousState) {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra(EXTRA_PREVIOUS_STATE, previousState);
        sendBroadcast(intent);
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
     * Start the ConnectedThread to begin managing a Bluetooth connection.
     *
     * @param socket The BluetoothSocket on which the connection was made
     */
    private synchronized void connected(BluetoothSocket socket) {
        if (DEBUG) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        updateDeviceState(STATE_CONNECTED);
    }

    /**
     * Stop all threads.
     */
    private synchronized void stop() {
        if (DEBUG) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        updateDeviceState(STATE_DISCONNECTED);
    }

    private void findPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices == null || pairedDevices.size() == 0 || mState == STATE_CONNECTED) return;

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
            connect(mPairedDevices.get(0));
        }
    }

    /**
     * Connect to the FTP server running on the connected Boogie Board Sync. This is required command before executing any other FTP commands.
     * Should only be called when a device has been connected. Remember to call {@link #disconnect()} when finished. This is an asynchronous call.
     *
     * @return immediate check if command could be sent
     */
    public boolean connect() {
        boolean success = false;

        if (mState == STATE_CONNECTED) {
            success = true;
            OBEXFtpRequest request = new OBEXFtpRequest(OBEXFtpRequest.CONNECT);
            request.setVersion(OBEXFtpRequest.OBEX_VERSION);
            request.setFlags(OBEXFtpRequest.DEFAULT_FLAG);
            request.setMaxSize(OBEXFtpRequest.MAXIMUM_PACKET_SIZE);
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.TARGET, OBEXFtpUtils.OBEX_FTP_UUID));
            mConnectedThread.addRequest(request);
        }

        return success;
    }

    /**
     * Disconnect from the FTP server. This is required if a connection was established with {@link #connect()}. This is an asynchronous call.
     *
     * @return immediate check if command could be sent
     */
    public boolean disconnect() {
        boolean success = false;

        if (mState == STATE_CONNECTED) {
            // Create disconnect request
            OBEXFtpRequest request = new OBEXFtpRequest(OBEXFtpRequest.DISCONNECT);
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.CONNECTION_ID, mConnectionId));
            mConnectedThread.addRequest(request);
        }

        return success;
    }

    /**
     * Request the current folder listing of the connected Boogie Board Sync. This is an asynchronous call.
     *
     * @return immediate check if the command could be sent
     */
    public boolean listFolder() {
        boolean success = false;

        if (mState == STATE_CONNECTED) {
            // Create list folder request.
            OBEXFtpRequest request = new OBEXFtpRequest(OBEXFtpRequest.GET);
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.CONNECTION_ID, mConnectionId));
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.NAME));
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.TYPE, OBEXFtpUtils.FOLDER_LISTING_TYPE));
            mConnectedThread.addRequest(request);
        }

        return success;
    }

    /**
     * Changes the current folder of the connected Boogie Board Sync. This is an asynchronous call.
     *
     * @param folderName Name of the folder to change to. A blank ("") folder name will change to the root directory. Providing ".." will change
     *                   the directory to the parent directory.
     * @return immediate check if the command could be sent
     */
    public boolean changeFolder(String folderName) {
        boolean success = false;

        if (mState == STATE_CONNECTED && folderName != null) {
            // Create list folder request.
            OBEXFtpRequest request = new OBEXFtpRequest(OBEXFtpRequest.SET_PATH);
            if (folderName.equals("..")) { // Go up a directory if item is null.
                request.setFlags(OBEXFtpRequest.BACKUP_FLAG | OBEXFtpRequest.DONT_CREATE_FOLDER_FLAG);
            } else if (folderName.equals("")) {
                request.setFlags(OBEXFtpRequest.DONT_CREATE_FOLDER_FLAG);
                request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.NAME));
            } else {
                request.setFlags(OBEXFtpRequest.DONT_CREATE_FOLDER_FLAG);
                request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.NAME, folderName));
            }
            request.setContants(OBEXFtpRequest.DEFAULT_CONSTANT);
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.CONNECTION_ID, mConnectionId));
            mConnectedThread.addRequest(request);
        }

        return success;
    }

    /**
     * Delete the specified file/folder from the Boogie Board Sync. This is an asynchronous call.
     *
     * @param name Name of the file/folder to be deleted
     * @return immediate check if the command could be sent
     */
    public boolean deleteFile(String name) {
        boolean success = false;

        if (mState == STATE_CONNECTED) {
            // Create list folder request.
            OBEXFtpRequest request = new OBEXFtpRequest(OBEXFtpRequest.PUT);
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.NAME, name));
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.CONNECTION_ID, mConnectionId));
            mConnectedThread.addRequest(request);
        }

        return success;
    }

    /**
     * Get a file from the Boogie Board Sync. This is an asynchronous call.
     *
     * @param file File to be retrieved
     * @return immediate check if the command could be sent
     */
    public boolean getFile(OBEXFtpFolderListingItem file) {
        boolean success = false;

        if (mState == STATE_CONNECTED) {
            fileBeingDownloaded = file;
            // Create list folder request.
            OBEXFtpRequest request = new OBEXFtpRequest(OBEXFtpRequest.GET);
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.NAME, file.getName()));
            request.addHeader(new OBEXFtpHeader(OBEXFtpHeader.CONNECTION_ID, mConnectionId));
            mConnectedThread.addRequest(request);
        }

        return success;
    }

    /**
     * Returns the current directory of the Boogie Board Sync. This will return null if there is no current connection to a Boogie Board Sync.
     *
     * @return uri of the current directory.
     */
    public Uri getDirectoryUri() {
        return mDirectoryUri;
    }

    /**
     * Returns the current state of the Sync FTP service.
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

    private void updateDeviceState(int newState) {
        if (newState == mState) return;
        if (DEBUG) Log.d(TAG, "Device state changed from " + mState + " to " + newState);

        int oldState = mState;
        mState = newState;

        for (SyncFtpListener listener : mListeners) {
            listener.onFtpDeviceStateChange(oldState, newState);
        }

        broadcastStateChange(mState, oldState);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class SyncFtpBinder extends Binder {

        public SyncFtpService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SyncFtpService.this;
        }
    }

    private void setupIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(SyncStreamingService.ACTION_STATE_CHANGED);
        registerReceiver(mMessageReceiver, intentFilter);
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
                        findPairedDevices();
                    }
                } else if (prevState == BluetoothDevice.BOND_BONDED && newState == BluetoothDevice.BOND_NONE) {
                    // Remove device from list of paired devices.
                    if (mPairedDevices.contains(device)) {
                        mPairedDevices.remove(device);
                    }
                }
            } else if (intent.getAction().equals(SyncStreamingService.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(SyncStreamingService.EXTRA_STATE, -1);
                int previousState = intent.getIntExtra(SyncStreamingService.EXTRA_PREVIOUS_STATE, -1);
                BluetoothDevice device = intent.getParcelableExtra(SyncStreamingService.EXTRA_DEVICE);

                // Connect to the same device that the streaming service connected to.
                if (state == SyncStreamingService.STATE_CONNECTED && (previousState == SyncStreamingService.STATE_CONNECTING || previousState ==
                        SyncStreamingService.STATE_LISTENING)) {
                    if (device != null && mState != STATE_CONNECTED && mState != STATE_CONNECTING) {
                        connect(device);
                    }
                }
            }

            // Backwards compatibility for older firmware, instead of waiting for Streaming service to connect, watch the ACL event.
            // Also, will be used if developers don't want to use the streaming service.
            else if (intent.getAction().equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                if (DEBUG) Log.d(TAG, "ACTION_ACL_CONNECTED");

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mState != STATE_CONNECTED && mState != STATE_CONNECTING && device != null && device.getName().contains("Sync")) {
                    findPairedDevices();
                }
            }
        }
    };

    private class MessageHandler extends Handler {

        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            // Parse the message that was returned from the background thread.
            if (message.what == MESSAGE_ACTION) {
                int action = message.arg1;
                int result = message.arg2;

                // CONNECT action, returns an object with the connection id.
                if (action == ACTION_CONNECT) {
                    if (result == RESULT_OK) {
                        mConnectionId = (Integer) message.obj;
                    }

                    for (SyncFtpListener listener : mListeners) listener.onConnectComplete(result);
                }

                // DISCONNECT action.
                else if (action == ACTION_DISCONNECT) {
                    for (SyncFtpListener listener : mListeners) listener.onDisconnectComplete(result);
                }

                // PUT action.
                else if (action == ACTION_PUT) {
                    if (result == RESULT_OK) {
                        for (SyncFtpListener listener : mListeners) listener.onDeleteComplete(deleteFile, RESULT_OK);
                    } else {
                        for (SyncFtpListener listener : mListeners) listener.onDeleteComplete(null, RESULT_FAIL);
                    }
                    deleteFile = null;
                }

                // SET_PATH action, returns an object of the updated file path.
                else if (action == ACTION_SET_PATH) {
                    if (result == RESULT_OK) {
                        String filePath = (String) message.obj;

                        if (filePath.equals("/")) {
                            mDirectoryUri = Uri.parse("/");
                        } else if (filePath.equals("../")) {
                            List<String> pathSegments = mDirectoryUri.getPathSegments();
                            if (pathSegments.size() > 1) {
                                pathSegments.remove(pathSegments.size() - 1);
                                mDirectoryUri = Uri.parse(TextUtils.join("/", pathSegments));
                            } else {
                                mDirectoryUri = Uri.parse("/");
                            }
                        } else {
                            mDirectoryUri = Uri.withAppendedPath(mDirectoryUri, filePath);
                        }

                        for (SyncFtpListener listener : mListeners) listener.onChangeFolderComplete(mDirectoryUri, RESULT_OK);
                    } else {
                        for (SyncFtpListener listener : mListeners) listener.onChangeFolderComplete(null, RESULT_FAIL);
                    }
                }

                // GET_FILE action, returns a byte array of the file data retrieved.
                else if (action == ACTION_GET_FILE) {
                    if (result == RESULT_OK) {
                        OBEXFtpFolderListingItem tempFile = fileBeingDownloaded;
                        tempFile.setData((byte[]) message.obj);
                        fileBeingDownloaded = null;
                        for (SyncFtpListener listener : mListeners) listener.onGetFileComplete(tempFile, RESULT_OK);
                    } else {
                        for (SyncFtpListener listener : mListeners) listener.onGetFileComplete(null, RESULT_FAIL);
                    }
                }

                // GET_FILE action, returns a byte array of the directory listing.
                else if (action == ACTION_GET_DIRECTORY) {
                    if (result == RESULT_OK) {
                        List<OBEXFtpFolderListingItem> directory = OBEXFtpUtils.parseXML((byte[]) message.obj);
                        for (SyncFtpListener listener : mListeners) listener.onFolderListingComplete(directory, RESULT_OK);
                    } else {
                        for (SyncFtpListener listener : mListeners) listener.onFolderListingComplete(null, RESULT_FAIL);
                    }
                }
            }

            // Connected to a device from the connect thread.
            // Passed object will be a socket.
            else if (message.what == MESSAGE_CONNECTED) {
                connected((BluetoothSocket) message.obj);
            }

            // Disconnected from the device on a worker thread.
            else if (message.what == MESSAGE_CONNECTION_BROKEN) {
                // Update the state of the device.
                updateDeviceState(STATE_DISCONNECTED);
            } else if (message.what == MESSAGE_STATE) {
                updateDeviceState(STATE_CONNECTED);
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

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(FTP_UUID);
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
            synchronized (SyncFtpService.this) {
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
        private OBEXFtpRequest mRequest;
        private ArrayBlockingQueue<OBEXFtpRequest> requestQueue;
        private ByteArrayOutputStream tempDirectoryStream, tempFileStream;
        private boolean tryListingAgainForbiddenHack;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ");
            mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            tempDirectoryStream = new ByteArrayOutputStream();
            tempFileStream = new ByteArrayOutputStream();
            tryListingAgainForbiddenHack = false;

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

                    // Parse the response from the server.
                    byte[] packet = Arrays.copyOfRange(buffer, 0, bytes);
                    OBEXFtpResponse response = new OBEXFtpResponse(packet);

                    // First, check to see if the OBEX server returned an error.
                    switch (response.getResponseCode()) {
                        case OBEXFtpResponse.BAD_REQUEST:
                            Log.e(TAG, "Received bad request error.");
                            break;
                        case OBEXFtpResponse.UNAUTHORIZED:
                            Log.e(TAG, "Received unauthorized error.");
                            break;
                        case OBEXFtpResponse.FORBIDDEN:
                            Log.e(TAG, "Received forbidden error.");
                            break;
                        case OBEXFtpResponse.NOT_FOUND:
                            Log.e(TAG, "Received not found error.");
                            break;
                        case OBEXFtpResponse.METHOD_NOT_ALLOWED:
                            Log.e(TAG, "Received method not allowed error.");
                            break;
                        case OBEXFtpResponse.NOT_ACCEPTABLE:
                            Log.e(TAG, "Received not acceptable error.");
                            break;
                        case OBEXFtpResponse.PROXY_AUTHENTICATION_REQUIRED:
                            Log.e(TAG, "Received proxy authentication required error.");
                            break;
                        case OBEXFtpResponse.REQUEST_TIME_OUT:
                            Log.e(TAG, "Received request time out error.");
                            break;
                        case OBEXFtpResponse.GONE:
                            Log.e(TAG, "Received gone error.");
                            break;
                        case OBEXFtpResponse.LENGTH_REQUIRED:
                            Log.e(TAG, "Received length required error.");
                            break;
                        case OBEXFtpResponse.PRECONDITION_FAILED:
                            Log.e(TAG, "Received precondition error.");
                            break;
                        case OBEXFtpResponse.REQUEST_ENTITY_TOO_LARGE:
                            Log.e(TAG, "Received request entity too large error.");
                            break;
                        case OBEXFtpResponse.REQUEST_URL_TOO_LARGE:
                            Log.e(TAG, "Received request URL too large error.");
                            break;
                        case OBEXFtpResponse.UNSUPPORTED_MEDIA_TYPE:
                            Log.e(TAG, "Received unsupported media type error.");
                            break;
                        case OBEXFtpResponse.INTERNAL_SERVER_ERROR:
                            Log.e(TAG, "Received internal server error.");
                            break;
                        case OBEXFtpResponse.NOT_IMPLEMENTED:
                            Log.e(TAG, "Received not implemented error.");
                            break;
                        case OBEXFtpResponse.BAD_GATEWAY:
                            Log.e(TAG, "Received bad gateway error.");
                            break;
                        case OBEXFtpResponse.SERVICE_UNAVAILABLE:
                            Log.e(TAG, "Received service unavailable error.");
                            break;
                        case OBEXFtpResponse.GATEWAY_TIMEOUT:
                            Log.e(TAG, "Received gateway timeout error.");
                            break;
                        case OBEXFtpResponse.HTTP_VERSION_NOT_SUPPORTED:
                            Log.e(TAG, "Received HTTP version not supported error.");
                            break;
                    }

                    // Determine what the request was and take the appropriate action.
                    switch (mRequest.getOpCode()) {
                        case OBEXFtpRequest.CONNECT:
                            if (response.getResponseCode() == OBEXFtpResponse.SUCCESS) {
                                if (DEBUG) Log.d(TAG, "connected to ftp server");

                                // Parse out the connection id for future requests.
                                ByteBuffer bb = ByteBuffer.wrap(response.getHeader(OBEXFtpHeader.CONNECTION_ID).body());
                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_CONNECT, RESULT_OK, bb.getInt()).sendToTarget();
                            } else {
                                cancel();
                            }
                            break;
                        case OBEXFtpRequest.DISCONNECT:
                            if (response.getResponseCode() == OBEXFtpResponse.SUCCESS) {
                                if (DEBUG) Log.d(TAG, "disconnected from ftp server");
                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_DISCONNECT, RESULT_OK).sendToTarget();
                            } else {
                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_DISCONNECT, RESULT_FAIL).sendToTarget();
                            }
                            break;
                        case OBEXFtpRequest.PUT:
                            if (response.getResponseCode() == OBEXFtpResponse.SUCCESS) {
                                if (DEBUG) Log.d(TAG, "deleted file");
                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_PUT, RESULT_OK, deleteFile).sendToTarget();
                            } else {
                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_PUT, RESULT_FAIL).sendToTarget();
                            }
                            deleteFile = null;
                            break;
                        case OBEXFtpRequest.SET_PATH:
                            if (response.getResponseCode() == OBEXFtpResponse.SUCCESS) {
                                if (DEBUG) Log.d(TAG, "finished changing folder.");

                                OBEXFtpHeader nameHeader = mRequest.getHeaders().get(0);
                                String folderName = nameHeader.getName();

                                // Update the folder name.
                                if (folderName == null) {
                                    if ((mRequest.getFlags() & OBEXFtpRequest.BACKUP_FLAG) == OBEXFtpRequest.BACKUP_FLAG) folderName = "../";
                                    else folderName = "/";
                                }

                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_SET_PATH, RESULT_OK, folderName).sendToTarget();
                            } else {
                                mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_SET_PATH, RESULT_FAIL).sendToTarget();
                            }
                            break;
                        case OBEXFtpRequest.GET:
                            // Determine if the GET was for a directory or file.
                            boolean getDir = mRequest.getHeaders().size() == 3;

                            if (response.getResponseCode() == OBEXFtpResponse.SUCCESS) {
                                if (getDir) {
                                    if (DEBUG) Log.d(TAG, "finished retrieving folder.");

                                    // Parse the resulting byte array to get a list of items and clear out temporary object.
                                    tempDirectoryStream.write(response.getHeader(OBEXFtpHeader.END_OF_BODY).body());
                                    mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_GET_DIRECTORY, RESULT_OK, tempDirectoryStream
                                            .toByteArray()).sendToTarget();
                                    tempDirectoryStream.reset();
                                } else {
                                    if (DEBUG) Log.d(TAG, "finished retrieving file");
                                    tempFileStream.write(response.getHeader(OBEXFtpHeader.END_OF_BODY).body());

                                    mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_GET_FILE, RESULT_OK,
                                            tempFileStream.toByteArray()).sendToTarget();
                                    tempFileStream.reset();
                                }
                            } else if (response.getResponseCode() == OBEXFtpResponse.CONTINUE) {
                                if (getDir) { // Retrieve directory.
                                    tempDirectoryStream.write(response.getHeader(OBEXFtpHeader.BODY).body());
                                    writeRequest(mRequest);
                                } else { // Retrieve file.
                                    tempFileStream.write(response.getHeader(OBEXFtpHeader.BODY).body());
                                    writeRequest(mRequest);
                                }
                            } else {
                                if (getDir) {
                                    tempDirectoryStream.reset();
                                    if (!tryListingAgainForbiddenHack) {
                                        addRequest(mRequest);
                                    } else {
                                        mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_GET_DIRECTORY, RESULT_FAIL).sendToTarget();
                                    }
                                } else {
                                    tempFileStream.reset();
                                    mMessageHandler.obtainMessage(MESSAGE_ACTION, ACTION_GET_FILE, RESULT_FAIL).sendToTarget();
                                }
                            }
                            break;
                        case OBEXFtpRequest.ABORT:
                            break;
                    }
                    // Reset buffer.
                    buffer = new byte[1024];
                } catch (IOException e) {
                    mMessageHandler.obtainMessage(MESSAGE_CONNECTION_BROKEN).sendToTarget();
                    if (DEBUG) Log.d(TAG, "disconnected", e);
                    break;
                }
            }
        }

        public void addRequest(OBEXFtpRequest request) {
            if (requestQueue == null) requestQueue = new ArrayBlockingQueue<OBEXFtpRequest>(10);

            requestQueue.add(request);
            try {
                writeRequest(requestQueue.take());
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to get the first element in the queue.");
            }
        }

        private boolean writeRequest(OBEXFtpRequest request) {
            if (request == null || mOutputStream == null) return false;

            try {
                mRequest = request;
                mOutputStream.write(request.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}