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

package com.improvelectronics.sync.android.samples;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.improvelectronics.sync.android.SyncCaptureReport;
import com.improvelectronics.sync.android.SyncPath;
import com.improvelectronics.sync.android.SyncStreamingListener;
import com.improvelectronics.sync.android.SyncStreamingService;

import java.util.List;

public class StreamingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streaming);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements SyncStreamingListener {

        private SyncStreamingService mStreamingService;
        private boolean mStreamingServiceBound;
        private TextView xTextView, yTextView, pressureTextView, stylusDownTetView;

        public PlaceholderFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Bind to the ftp service.
            Intent intent = new Intent(getActivity(), SyncStreamingService.class);
            getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_streaming, container, false);
            return rootView;
        }

        @Override
        public void onStart() {
            super.onStart();

            xTextView = (TextView)getView().findViewById(R.id.xTextView);
            yTextView = (TextView)getView().findViewById(R.id.yTextView);
            pressureTextView = (TextView)getView().findViewById(R.id.pressureTextView);
            stylusDownTetView = (TextView)getView().findViewById(R.id.stylusDownTextView);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mStreamingServiceBound) {
                // Put the Boogie Board Sync back into MODE_NONE.
                // This way it doesn't use Bluetooth and saves battery life.
                if(mStreamingService.getState() == SyncStreamingService.STATE_CONNECTED) mStreamingService.setSyncMode(SyncStreamingService.MODE_NONE);

                // Don't forget to remove the listener and unbind from the service.
                mStreamingService.removeListener(this);
                getActivity().unbindService(mConnection);
            }
        }

        @Override
        public void onStreamingStateChange(int prevState, int newState) {
            // Put the streaming service in capture mode to get data from Boogie Board Sync.
            if(newState == SyncStreamingService.STATE_CONNECTED) {
                mStreamingService.setSyncMode(SyncStreamingService.MODE_CAPTURE);
            }
        }

        @Override
        public void onErase() {
            Toast.makeText(getActivity(), "Erase button pushed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSave() {
            Toast.makeText(getActivity(), "Save button pushed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDrawnPaths(List<SyncPath> paths) {}

        @Override
        public void onCaptureReport(SyncCaptureReport captureReport) {
            xTextView.setText(captureReport.getX() + "");
            yTextView.setText(captureReport.getY() + "");
            pressureTextView.setText(captureReport.getPressure() + "");
            if(captureReport.hasTipSwitchFlag()) stylusDownTetView.setVisibility(View.VISIBLE);
            else stylusDownTetView.setVisibility(View.GONE);
        }

        private final ServiceConnection mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                // Set up the service
                mStreamingServiceBound = true;
                SyncStreamingService.SyncStreamingBinder binder = (SyncStreamingService.SyncStreamingBinder) service;
                mStreamingService = binder.getService();
                mStreamingService.addListener(PlaceholderFragment.this);// Add listener to retrieve events from streaming service.

                // Put the streaming service in capture mode to get data from Boogie Board Sync.
                if(mStreamingService.getState() == SyncStreamingService.STATE_CONNECTED) {
                    mStreamingService.setSyncMode(SyncStreamingService.MODE_CAPTURE);
                }
            }

            public void onServiceDisconnected(ComponentName name) {
                mStreamingService = null;
                mStreamingServiceBound = false;
            }
        };
    }
}
