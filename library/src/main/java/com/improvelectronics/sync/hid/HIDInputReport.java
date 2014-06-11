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

public class HIDInputReport extends HIDMessage {

    // Types of input reports.
    private static final byte TYPE_OTHER = (byte)0x00;
    private static final byte TYPE_INPUT = (byte)0x01;
    private static final byte TYPE_OUTPUT = (byte)0x02;
    private static final byte TYPE_FEATURE = (byte)0x02;

    private byte mInputReortId;
    private byte[] mPayload;

    public HIDInputReport(byte reportType, byte reportId, byte[] payload) {
        super(HIDMessage.CHANNEL_INTERRUPT, HIDMessage.TYPE_DATA, reportType);
        mInputReortId = reportId;
        mPayload = payload;
    }

}
