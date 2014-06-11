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

import android.util.Log;

import com.google.common.primitives.Bytes;
import com.improvelectronics.sync.misc.CRC8;
import com.improvelectronics.sync.android.SyncCaptureReport;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HIDUtilities {

    private static byte FEND = (byte) 0xC0;
    private static byte FESC = (byte) 0xDB;
    private static byte TFEND = (byte) 0xDC;
    private static byte TFESC = (byte) 0xDD;
    private static String TAG = HIDUtilities.class.getSimpleName();

    /**
     * Return a packet that has the proper framing and added CRC for error correctness. If a packet that is null or has zero length is sent the
     * function will return null.
     *
     * @param packet that will be sent.
     * @return a framed packet.
     */
    public static byte[] framePacket(byte[] packet) {
        if (packet == null || packet.length == 0) return null;

        byte[] escapedPacket = escapePacket(packet);
        return Bytes.concat(new byte[]{FEND}, escapedPacket, CRC8.calculate(packet), new byte[]{FEND});
    }

    /**
     * Returns an escaped packet.
     *
     * @param packet to be escaped.
     * @return escaped packet.
     */
    private static byte[] escapePacket(byte[] packet) {
        byte[] escapedPacket = new byte[]{};

        for (int i = 0; i < packet.length; i++) {
            byte currentByte = packet[i];

            if (currentByte == FEND) {
                escapedPacket = Bytes.concat(escapedPacket, new byte[]{FESC, TFEND});
                continue;
            } else if (currentByte == FESC) {
                escapedPacket = Bytes.concat(escapedPacket, new byte[]{FESC, TFESC});
                continue;
            }
            escapedPacket = Bytes.concat(escapedPacket, new byte[]{currentByte});
        }
        return escapedPacket;
    }

    public static List<HIDMessage> parseBuffer(byte[] buffer, int numBytes) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        List<HIDMessage> messages = new ArrayList<HIDMessage>();

        for (int i = 0; i < numBytes; i++) {
            byte currentByte = buffer[i];

            if (outputStream.size() >= 0 && currentByte != HIDUtilities.FEND) {
                if (currentByte == HIDUtilities.FESC) {
                    currentByte = buffer[++i];
                    if (currentByte == HIDUtilities.TFEND) {
                        currentByte = HIDUtilities.FEND;
                    } else if (currentByte == HIDUtilities.TFESC) {
                        currentByte = HIDUtilities.FESC;
                    }
                    outputStream.write(currentByte);
                } else {
                    outputStream.write(currentByte);
                }
            } else if (currentByte == HIDUtilities.FEND && outputStream.size() > 0) {
                byte[] packet = outputStream.toByteArray();

                // Length of the packet has to be at least four bytes.
                if (packet.length >= 4) {
                    // Check CRC.
                    byte[] CRC = CRC8.calculate(packet);
                    if (CRC[0] == 0 && CRC[1] == 0) {
                        byte channel = packet[0];
                        byte type = (byte) ((packet[1] & 0xFF) >>> 4);
                        byte parameter = (byte) ((type << 4) ^ packet[1]);

                        switch (channel) {
                            case HIDMessage.CHANNEL_CONTROL:
                                if (type == HIDMessage.TYPE_HANDSHAKE && packet.length == 2) {
                                    messages.add(new HIDHandshake(parameter));
                                } else {
                                    messages.add(new HIDMessage(channel, type, parameter));
                                }
                            case HIDMessage.CHANNEL_INTERRUPT:
                                if (type == HIDMessage.TYPE_DATA) {
                                    messages.add(new SyncCaptureReport(parameter, packet[2], Arrays.copyOfRange(packet, 3,
                                            packet.length)));
                                } else {
                                    messages.add(new HIDMessage(channel, type, parameter));
                                }
                        }
                    } else {
                        Log.e(TAG, "Invalid CRC.");
                    }
                } else {
                    Log.e(TAG, "Packet does not have a valid length.");
                }

                outputStream.reset();
            }
        }
        return messages;
    }
}
