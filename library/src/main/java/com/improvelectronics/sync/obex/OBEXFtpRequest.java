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

package com.improvelectronics.sync.obex;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Requests consist of one or more packets, each packet consisting of a one byte
 * opcode, a two byte packet length, and required or optional data depending on
 * the operation. Each request packet must be acknowledged by a response.
 */

public class OBEXFtpRequest {

    /* Request Codes. */
    // Choose your partner, negotiate capabilities
    public static final byte CONNECT = (byte) 0x80;
    // Signal the end of the session
    public static final byte DISCONNECT = (byte) 0x81;
    // Send an object
    public static final byte PUT = (byte) 0x82;
    // Get an object
    public static final byte GET = (byte) 0x83;
    // Modifies the current path on the receiving side
    public static final byte SET_PATH = (byte) 0x85;
    // Used for reliable session support
    public static final byte SESSION = (byte) 0x87;
    // Abort the current operation
    public static final byte ABORT = (byte) 0xFF;
    /* FLAGS AND CONSTANTS */
    public static final byte DEFAULT_FLAG = (byte) 0x00;
    public static final byte BACKUP_FLAG = (byte) 0x01;
    public static final byte DONT_CREATE_FOLDER_FLAG = (byte) 0x02;
    public static final byte DEFAULT_CONSTANT = (byte) 0x00;

    public static final byte[] MAXIMUM_PACKET_SIZE = {(byte) 0xFF, (byte) 0xDC};
    private static final String TAG = "OBEXFtpRequest";
    public static byte OBEX_VERSION = (byte) 0x10;
    private Byte opCode, version, flags, constants;
    private byte[] maxSize;
    private short length = -1;
    private ArrayList<OBEXFtpHeader> headers = new ArrayList<OBEXFtpHeader>();

    public OBEXFtpRequest(byte opCode) {
        this.opCode = opCode;
    }

	/* SETTER METHODS */

    /**
     * Set the OBEX version number for the request.
     *
     * @param version - OBEX version number
     */
    public void setVersion(byte version) {
        this.version = version;
    }

    /**
     * Set the connect flags. Usually in a connect request this will be set to 0
     * to allow for multiple connections.
     *
     * @param flags - Flags to be set for the request.
     */
    public void setFlags(int flags) {
        this.flags = (byte)flags;
    }

    /**
     * Set the maximum packet size that the device can receive. The largest
     * value is 64k bytes - 1.
     *
     * @param maxSize - Two byte unsigned integer.
     */
    public void setMaxSize(byte[] maxSize) {
        if (maxSize.length == 2)
            this.maxSize = maxSize;
        else
            Log.d(TAG, "You did not enter a valid maximum size.");
    }

    /**
     * Set the constants byte. Used for set path operation code.
     * <p/>
     * The constants byte is entirely reserved at this time, and must be set to
     * zero by sender and ignored by receiver.
     *
     * @param constants
     */

    public void setContants(byte constants) {
        this.constants = constants;
    }

    /**
     * Add a header to the OBEXFtpRequest.
     *
     * @param header
     */
    public void addHeader(OBEXFtpHeader header) {
        headers.add(header);
    }

    /**
     * Remove all the headers associated with this OBEXFtpRequest.
     */
    public void clearHeaders() {
        headers.clear();
    }

    /**
     * Return the operation code that was set on this request. Null if the
     * operation code was never set.
     *
     * @return byte
     */
    public byte getOpCode() {
        return opCode;
    }

    /**
     *
     * @return
     */
    public byte getFlags() {
        return flags;
    }

    /**
     * Set the operation code for the request. This operation code determines
     * what the client is trying to communicate with the server.
     *
     * @param opCode - Operation code that the request is going to perform.
     */
    public void setOpCode(byte opCode) {
        this.opCode = opCode;
    }

    public byte[] toByteArray() {
        // Create the stream to write the bytes to.
        ByteArrayOutputStream tempStream = new ByteArrayOutputStream();

        // Determine the length of the packet.
        calculatePacketLength();

        // Ensure an operation code is set and write to stream.
        if (length == -1)
            return null;
        else
            tempStream.write(opCode);

        try {
            // Write the length of the packet
            tempStream.write(OBEXFtpUtils.lengthToBytes(length));

            // Write the version number, flags, and maximum packet size if the
            // operation code is CONNECT

            if (opCode == OBEXFtpRequest.CONNECT) {
                tempStream.write(version);
                tempStream.write(flags);
                tempStream.write(maxSize);
            }

            // Write the version flags and constants if the operation code is
            // SET_PATH
            if (opCode == OBEXFtpRequest.SET_PATH) {
                tempStream.write(flags);
                tempStream.write(constants);
            }

            // Write any headers that have been added to the request.
            for (OBEXFtpHeader header : headers) {
                tempStream.write(header.toByteArray());
            }
        } catch (IOException e) {
            Log.e(TAG, "There was an error writing a byte array to the temporary stream.");
            e.printStackTrace();

        }

        return tempStream.toByteArray();
    }

    /**
     * Calculate the length of the packet to be used in the resulting request.
     */
    private void calculatePacketLength() {
        // Add one for the operation code
        if (opCode != null)
            length = 1;

        // Add two for the length of the packet
        length += 2;

        // If the operation code is connect then add 1 for version number, 1 for
        // flags, and 2 for maximum packet size.
        if (opCode == OBEXFtpRequest.CONNECT)
            length += 4;

        // If the operation code is set path then add 1 for flags and 1 for
        // constants.
        if (opCode == OBEXFtpRequest.SET_PATH)
            length += 2;

        // Add the length of all the headers
        for (OBEXFtpHeader header : headers)
            length += header.length();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Operation Code: " + OBEXFtpUtils.byteToHex(opCode) + '\n');
        sb.append("Length: " + length + '\n');
        if (version != null)
            sb.append("Version: " + OBEXFtpUtils.byteToHex(version) + '\n');
        if (flags != null)
            sb.append("Flags: " + OBEXFtpUtils.byteToHex(flags) + '\n');
        if (constants != null)
            sb.append("Constants: " + OBEXFtpUtils.byteToHex(constants) + '\n');
        if (maxSize != null)
            sb.append("Maximum Size: " + OBEXFtpUtils.bytesToHex(maxSize) + '\n');
        sb.append("Headers: \n");
        for (OBEXFtpHeader header : headers)
            sb.append("\n\t" + header + '\n');

        return sb.toString();
    }

    /**
     *
     * @return
     */
    public ArrayList<OBEXFtpHeader> getHeaders() {
        return headers;
    }
}
