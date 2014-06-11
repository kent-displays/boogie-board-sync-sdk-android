/*******************************************************************************
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
 ******************************************************************************/

package com.improvelectronics.sync.android;

import com.improvelectronics.sync.hid.HIDInputReport;

/**
 * Represents a single capture report that was returned from the Boogie Board Sync.
 */
public class SyncCaptureReport extends HIDInputReport {

    /**
     * Flag for when the erase button on the device is being pushed.
     */
    private static final byte SW_ERASE_FLAG = (byte) (0x01 << 7);

    /**
     * Flag for when the save button on the device is being pushed.
     */
    private static final byte SW_SAVE_FLAG = (byte) (0x01 << 6);

    /**
     * Flag for when an erase has been completed.
     */
    private static final byte ERASE_FLAG = (byte) (0x01 << 5);

    /**
     * Flag for when a save has been completed.
     */
    private static final byte SAVE_FLAG = (byte) (0x01 << 4);

    /**
     * Flag for when the stylus is in detectable range.
     */
    private static final byte RDY_FLAG = (byte) (0x01 << 2);

    /**
     * Flag for when barrel switch on the stylus is being pressed.
     */
    private static final byte BSW_FLAG = (byte) (0x01 << 1);

    /**
     * Flag for when the stylus is down on the surface.
     */
    private static final byte TSW_FLAG = (byte) 0x01;

    /**
     * Maximum value for x coordinate returned from the Boogie Board Sync.
     */
    public static final float MAX_X = 20280.0f;

    /**
     * Maximum value for y coordinate returned from the Boogie Board Sync.
     */
    public static final float MAX_Y = 13942.0f;

    private long mX;
    private long mY;
    private long mPressure;
    private byte mFlags;

    /**
     * Constructor that creates a {@link #SyncCaptureReport} with a buffer of data to parse.
     *
     * @param reportType type of report
     * @param reportId   Id of the report
     * @param payload    buffer containing all the capture data
     */
    public SyncCaptureReport(byte reportType, byte reportId, byte[] payload) {
        super(reportType, reportId, payload);
        mX = 0;
        mY = 0;
        mPressure = 0;
        mFlags = 0;
        parse(payload);
    }

    private void parse(byte[] payload) {
        // Parse the x-coordinate.
        mX = payload[0] & 0xFF;
        mX += (payload[1] & 0xFF) << 8;

        // Parse the y-coordinate.
        mY = payload[2] & 0xFF;
        mY += (payload[3] & 0xFF) << 8;

        // Parse the pressure.
        mPressure = payload[4] & 0xFF;
        mPressure += (payload[5] & 0xFF) << 8;

        // Parse the flags.
        mFlags = payload[6];
    }

    public byte getFlags() {
        return mFlags;
    }

    public boolean hasSaveFlag() {
        return (mFlags & SAVE_FLAG) == SAVE_FLAG;
    }

    public boolean hasEraseFlag() {
        return (mFlags & ERASE_FLAG) == ERASE_FLAG;
    }

    public boolean hasSaveSwitchFlag() {
        return (mFlags & SW_SAVE_FLAG) == SW_SAVE_FLAG;
    }

    public boolean hasEraseSwitchFlag() {
        return (mFlags & SW_ERASE_FLAG) == SW_ERASE_FLAG;
    }

    public boolean hasReadyFlag() {
        return (mFlags & RDY_FLAG) == RDY_FLAG;
    }

    public boolean hasBarrelSwitchFlag() {
        return (mFlags & BSW_FLAG) == BSW_FLAG;
    }

    public boolean hasTipSwitchFlag() {
        return (mFlags & TSW_FLAG) == TSW_FLAG;
    }

    public long getX() {
        return mX;
    }

    public long getY() {
        return mY;
    }

    public long getPressure() {
        return mPressure;
    }
}
