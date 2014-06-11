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

public class HIDMessage {

    // Types of messages.
    public static final byte TYPE_HANDSHAKE = (byte) 0x00;
    public static final byte TYPE_CONTROL = (byte) 0x01;
    public static final byte TYPE_GET_REPORT = (byte) 0x04;
    public static final byte TYPE_SET_REPORT = (byte) 0x05;
    public static final byte TYPE_GET_PROTOCOL = (byte) 0x06;
    public static final byte TYPE_SET_PROTOCOL = (byte) 0x07;
    public static final byte TYPE_DATA = (byte) 0x0A;
    public static final byte TYPE_UNKNOWN = (byte) 0xFF;

    // Channels for the HID protocol.
    public static final byte CHANNEL_CONTROL = (byte) 0x00;
    public static final byte CHANNEL_INTERRUPT = (byte) 0x01;
    public static final byte CHANNEL_UNKNOWN = (byte) 0xFF;

    private byte mType;
    private byte mParameter;
    private byte mChannel;
    private byte mHeader;

    private static final String TAG = HIDMessage.class.getSimpleName();

    /**
     * Default constructor that sets the initial type and channel to unknown.
     */
    public HIDMessage() {
        mType = TYPE_UNKNOWN;
        mChannel = CHANNEL_UNKNOWN;
    }

    /**
     * Constructs a new HIDMessage with a specific message type and channel.
     *
     * @param messageType for the HIDMessage
     * @param channel     for the HIDMessage
     */
    public HIDMessage(byte messageType, byte channel, byte parameter) {
        mType = messageType;
        mParameter = parameter;
        mChannel = channel;
        mHeader = (byte)((getType() << 4) + parameter);
    }

    public byte getType() {
        return mType;
    }

    public byte getChannel() {
        return mChannel;
    }
}
