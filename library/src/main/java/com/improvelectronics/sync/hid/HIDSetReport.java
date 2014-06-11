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
package com.improvelectronics.sync.hid;

import com.google.common.primitives.Bytes;

public class HIDSetReport extends HIDMessage {

    private byte mSetReportId;
    private byte mSetReportType;
    private byte[] mPayload;
    private byte mHeader;

    // Types of HID set report messages.
    public static final byte TYPE_INPUT = (byte)0x01;
    public static final byte TYPE_OUTPUT = (byte)0x02;
    public static final byte TYPE_FEATURE = (byte)0x03;

    // Ids for set reports.
    public static final byte ID_OPERATION_REQUEST = (byte)0x04;
    public static final byte ID_MODE = (byte)0x05;
    public static final byte ID_DATE = (byte)0x06;
    public static final byte ID_DEVICE = (byte)0x08;

    /**
     * Constructs a HIDSetReport message with a report Id and sets the message type to SET_REPORT and creates the header used for packet creation.
     * @param reportType to be associated with the HIDSetReport.
     * @param reportId to be associated with the HIDSetReport.
     * @param payload to be associated with the HIDSetReport.
     */
    public HIDSetReport(byte reportType, byte reportId, byte[] payload) {
        super(HIDMessage.TYPE_SET_REPORT, HIDMessage.CHANNEL_CONTROL, reportType);
        mSetReportId = reportId;
        mSetReportType = reportType;
        mPayload = payload;
        mHeader = (byte)((getType() << 4) + mSetReportType);
    }

    /**
     * Returns a byte array that has the CRC computed and is properly framed to be sent to a HID device.
     * @return byte[]
     */
    public byte[] getPacketBytes() {
        // Create the initial packet without the payload.
        // NOTE: For our implementation we need to repeat the report id and then add a zero-byte.
        byte[] initialBytes = new byte[]{getChannel(), mHeader, mSetReportId, mSetReportId, 0x00 };

        // Add the packet.
        byte[] packet = Bytes.concat(initialBytes, mPayload);

        return HIDUtilities.framePacket(packet);
    }
}
