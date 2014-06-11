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
 *************************************************************************** */

package com.improvelectronics.sync.android;

import android.net.Uri;

import com.improvelectronics.sync.obex.OBEXFtpFolderListingItem;

import java.util.List;

/**
 * Interface definition for a callback when an state or event occurs on the FTP server.
 */
public interface SyncFtpListener {

    /**
     * Called when the state of the FTP server has changed.
     *
     * @param prevState old state of FTP server
     * @param newState new state of FTP server
     */
    public void onFtpDeviceStateChange(int prevState, int newState);

    /**
     * Called when a connect command has been completed.
     *
     * @param result result of the connection
     */
    public void onConnectComplete(int result);

    /**
     * Called when a disconnect command has been completed.
     *
     * @param result result of the disconnection
     */
    public void onDisconnectComplete(int result);

    /**
     * Called when a folder listing command has been completed.
     *
     * @param items list of items in the folder, null if the command was unsuccessful
     * @param result result of the folder listing
     */
    public void onFolderListingComplete(List<OBEXFtpFolderListingItem> items, int result);

    /**
     * Called when a change folder command has been completed.
     *
     * @param uri current directory path, null if the command was unsuccessful
     * @param result result of the change folder
     */
    public void onChangeFolderComplete(Uri uri, int result);

    /**
     * Called when a delete command has been completed.
     *
     * @param file file that was deleted with no attached data, null if the command was unsuccessful
     * @param result result of the delete
     */
    public void onDeleteComplete(OBEXFtpFolderListingItem file, int result);

    /**
     * Called when a get file command has been completed.
     *
     * @param file file that was retrieved, null if the command was unsuccessful
     * @param result result of the get file
     */
    public void onGetFileComplete(OBEXFtpFolderListingItem file, int result);
}