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

public class HIDHandshake extends HIDMessage {

    private static byte RESULT_SUCCESSFUL = (byte)0x00;
    private static byte RESULT_NOT_READY = (byte)0x01;
    private static byte RESULT_ERR_INVALID_REPORT = (byte)0x02;
    private static byte RESULT_ERR_UNSUPPORTED_REQUEST = (byte)0x03;
    private static byte RESULT_ERR_INVALID_PARAMETER = (byte)0x04;
    private static byte RESULT_ERR_UNKNOWN = (byte)0x0E;
    private static byte RESULT_ERR_FATAL = (byte)0x0F;

    private byte mResultCode;

    public HIDHandshake(byte resultCode) {
        super(HIDMessage.TYPE_HANDSHAKE, HIDMessage.CHANNEL_CONTROL, resultCode);

        // If the result code is a reserved message type then return RESULT_UNSUPPORTED_REQUEST.
        if(resultCode > RESULT_ERR_INVALID_PARAMETER && resultCode < RESULT_ERR_UNKNOWN) {
            mResultCode = RESULT_ERR_UNSUPPORTED_REQUEST;
        }

        // If the result code is out of range then return ERR_INVALID_PARAMETER.
        else if(resultCode > RESULT_ERR_FATAL || resultCode < 0) {
            mResultCode = RESULT_ERR_INVALID_PARAMETER;
        }

        else mResultCode = resultCode;
    }
}