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

import java.util.List;

/**
 * Interface definition for a callback when an state or event occurs on the streaming connection to the Boogie Board Sync.
 */
public interface SyncStreamingListener {

    /**
     * Called when the state of the streaming connection has changed.
     *
     * @param prevState old state of streaming connection
     * @param newState new state of streaming connection
     */
    public void onStreamingStateChange(int prevState, int newState);

    /**
     * Called when the Boogie Board Sync was erased from the device.
     */
    public void onErase();

    /**
     * Called when the Boogie Board Sync saved a file.
     */
    public void onSave();

    /**
     * Called when paths were drawn to the Boogie Board Sync.
     */
    public void onDrawnPaths(List<SyncPath> paths);

    /**
     * Called when the Boogie Board Sync returned a {@link com.improvelectronics.sync.android.SyncCaptureReport #SyncCaptureReport}.
     */
    public void onCaptureReport(SyncCaptureReport captureReport);
}