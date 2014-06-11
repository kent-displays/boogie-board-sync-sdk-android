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
import java.nio.ByteBuffer;

public class OBEXFtpHeader {

    // name of service that operation is targeted to
    public static final byte TARGET = (byte) 0x46;
    // an identifier used for OBEX connection multiplexing
    public static final byte CONNECTION_ID = (byte) 0xCB;
    // name of the object (often a file name)
    public static final byte NAME = (byte) 0x01;
    // type of object - e.g. text, html, binary, manufacturer specific
    public static final byte TYPE = (byte) 0x42;
    // identifies the OBEX application, used to tell if talking to a peer
    public static final byte WHO = (byte) 0x4A;
    // a chunk of the object body.
    public static final byte BODY = (byte) 0x48;
    // the final chunk of the object body
    public static final byte END_OF_BODY = (byte) 0x49;
    // the length of the object in bytes
    public static final byte LENGTH = (byte) 0xC3;
    // text description of the object
    public static final byte DESCRIPTION = (byte) 0x05;
    private static final String TAG = "OBEXFtpHeader";
    private Byte id;
    private short length;
    private byte[] body = null;
    private String name;

    public OBEXFtpHeader(byte id, byte[] body) {
        this.id = id;
        this.body = body;
        calculateLength();
    }

    public OBEXFtpHeader(byte id, int body) {
        this.id = id;
        // Convert the int to a byte array.
        ByteBuffer dbuf = ByteBuffer.allocate(4);
        dbuf.putInt(body);
        byte[] bytes = dbuf.array();
        this.body = bytes;
        calculateLength();
    }

    public OBEXFtpHeader(byte id, String name) {
        this.id = id;
        this.body = OBEXFtpUtils.stringToHex(name);
        this.name = name;
        calculateLength();
    }

    public OBEXFtpHeader(byte id) {
        this.id = id;
        this.body = null;
        calculateLength();
    }

    /**
     * Return the byte array that the header translates to.
     *
     * @return byte[]
     */
    public byte[] toByteArray() {
        // Create the stream to write the bytes to.
        ByteArrayOutputStream tempStream = new ByteArrayOutputStream();
        tempStream.write(id);
        try {
            // Don't write length of connection id, it is always 5 bytes total.
            if (id != CONNECTION_ID)
                tempStream.write(OBEXFtpUtils.lengthToBytes(length));
            // Check to ensure body isn't null
            if (body != null && !body.equals(""))
                tempStream.write(body);
        } catch (IOException e) {
            Log.e(TAG, "There was an error writing the body to the temporary stream.");
            e.printStackTrace();
        }
        return tempStream.toByteArray();
    }

    /**
     * Returns the body of the header.
     *
     * @return byte[]
     */
    public byte[] body() {
        return body;
    }

    /**
     * Return the length of the OBEXFtpHeader.
     *
     * @return int
     */
    public int length() {
        return length;
    }

    /**
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Determine the length of the packet. Sometimes the body will be null of a
     * header so the length of that header is going to be three.
     */
    private void calculateLength() {
        // This is needed so when all the lengths are added together the length
        // of the connection id is 5;
        if (id == CONNECTION_ID)
            length = 5;
        else {
            // Add 3 for id and 2 bytes for length
            length = 3;
            if (body != null && !body.equals(""))
                length += body.length;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OBEXFtpHeader other = (OBEXFtpHeader) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Id: ");

        switch (id) {
            case TARGET:
                sb.append("Target");
                break;
            case CONNECTION_ID:
                sb.append("Connection Id");
                break;
            case NAME:
                sb.append("Name");
                break;
            case TYPE:
                sb.append("Type");
                break;
            case WHO:
                sb.append("Who");
                break;
            case BODY:
                sb.append("Body");
                break;
            case END_OF_BODY:
                sb.append("End Of Body");
                break;
            case LENGTH:
                sb.append("Length");
                break;
            case DESCRIPTION:
                sb.append("Description");
                break;
        }

        sb.append(" (" + OBEXFtpUtils.byteToHex((byte) id) + ")");
        if (body != null)
            sb.append("\n\tBody: " + OBEXFtpUtils.bytesToHex(body));
        sb.append("\n\tLength: " + length);
        return sb.toString();
    }
}
