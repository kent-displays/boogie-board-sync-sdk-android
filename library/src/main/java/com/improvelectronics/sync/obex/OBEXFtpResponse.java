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

import android.util.SparseArray;

import java.io.ByteArrayOutputStream;

/**
 * This object encapsulates a byte array returning from an OBEX connection. It
 * first parses the byte array to find all the response codes and all the other
 * fields that are passed back from the OBEX server.
 * <p/>
 * Responses consist of one or more packets - one per request packet in the
 * operation. Each packet consists of a one byte response code, a two byte
 * packet length, and required or optional data depending on the operation.
 *
 */

public class OBEXFtpResponse {

    /* RESPONSE CODES */
    public static final byte CONTINUE = (byte) 0x90;
    public static final byte SUCCESS = (byte) 0xA0;
    public static final byte CREATED = (byte) 0xA1;
    public static final byte ACCEPTED = (byte) 0xA2;
    public static final byte MULTIPLE_CHOICES = (byte) 0xB0;
    public static final byte MOVED_PERMANENTLY = (byte) 0xB1;
    public static final byte MOVED_TEMPORARILY = (byte) 0xB2;
    public static final byte SEE_OTHER = (byte) 0xB3;
    public static final byte NOT_MODIFIED = (byte) 0xB4;
    public static final byte USE_PROXY = (byte) 0xB5;
    public static final byte BAD_REQUEST = (byte) 0xC0;
    public static final byte UNAUTHORIZED = (byte) 0xC1;
    public static final byte FORBIDDEN = (byte) 0xC3;
    public static final byte NOT_FOUND = (byte) 0xC4;
    public static final byte METHOD_NOT_ALLOWED = (byte) 0xC5;
    public static final byte NOT_ACCEPTABLE = (byte) 0xC6;
    public static final byte PROXY_AUTHENTICATION_REQUIRED = (byte) 0xC7;
    public static final byte REQUEST_TIME_OUT = (byte) 0xC8;
    public static final byte CONFLICT = (byte) 0xC9;
    public static final byte GONE = (byte) 0xCA;
    public static final byte LENGTH_REQUIRED = (byte) 0xCB;
    public static final byte PRECONDITION_FAILED = (byte) 0xCC;
    public static final byte REQUEST_ENTITY_TOO_LARGE = (byte) 0xCD;
    public static final byte REQUEST_URL_TOO_LARGE = (byte) 0xCE;
    public static final byte UNSUPPORTED_MEDIA_TYPE = (byte) 0xCF;
    public static final byte INTERNAL_SERVER_ERROR = (byte) 0xD0;
    public static final byte NOT_IMPLEMENTED = (byte) 0xD1;
    public static final byte BAD_GATEWAY = (byte) 0xD2;
    public static final byte SERVICE_UNAVAILABLE = (byte) 0xD3;
    public static final byte GATEWAY_TIMEOUT = (byte) 0xD4;
    public static final byte HTTP_VERSION_NOT_SUPPORTED = (byte) 0xD5;
    public static final byte DATABASE_FULL = (byte) 0xE0;
    public static final byte DATABASE_LOCKED = (byte) 0xE1;
    private byte[] mByteArray;
    private byte responseCode;
    private int length = -1, maxLength = -1;
    private Byte version = null, flags = null;
    private SparseArray<OBEXFtpHeader> headers = new SparseArray<OBEXFtpHeader>();

    //private static final String TAG = "OBEXFtpResponse";

    public OBEXFtpResponse(byte[] byteArray) {
        this.mByteArray = byteArray;
        parseResponse();
    }

    private void parseResponse() {
        // Keep track of where we are in the array and the header id/length.
        int currentIndex = 3, headerId, headerLength;
        ByteArrayOutputStream tempStream = new ByteArrayOutputStream();

        // Save the response code of the response.
        responseCode = mByteArray[0];

        // Determine the length of the response.
        length = OBEXFtpUtils.getLength(mByteArray[1], mByteArray[2]);

        // Check the length to make sure it is long enough to keep parsing.
        if (length > 3) {

            // Weak check to see if this is a initial connection response. Since
            // this is the only time that the version, flag, and max length
            // would be checked.
            if (mByteArray[3] == 16) {
                // Determine the OBEX version number
                version = mByteArray[3];

                // Determine the flag being set.
                flags = mByteArray[4];

                // Determine the maximum length of the response.
                maxLength = OBEXFtpUtils.getLength(mByteArray[5], mByteArray[6]);

                // Move current index to the proper location.
                currentIndex = 7;
            }

            // Parse the rest of the byte array.
            while (currentIndex < mByteArray.length) {
                // Reset temp stream
                tempStream.reset();

                headerId = mByteArray[currentIndex];
                headerLength = OBEXFtpUtils.getLength(mByteArray[currentIndex + 1], mByteArray[currentIndex + 2]);

                // Must differentiate between different headers that have a
                // length attribute and ones that do not. So here save the
                // required byte array and then move the current index to the
                // correct position.
                if (headerId == OBEXFtpHeader.CONNECTION_ID || headerId == OBEXFtpHeader.LENGTH) {
                    tempStream.write(mByteArray, currentIndex + 1, 4);
                    currentIndex += 5;
                } else {
                    tempStream.write(mByteArray, currentIndex + 3, headerLength - 3);
                    currentIndex += headerLength;
                }

                // Add the new found header to the ArrayList
                headers.put(headerId, new OBEXFtpHeader((byte) headerId, tempStream.toByteArray()));
            }
        }
    }

    /**
     * Return all the headers that are contained in the response packet.
     *
     * @return ArrayList<OBEXFtpHeader>
     */
    public SparseArray<OBEXFtpHeader> getHeaders() {
        return headers;
    }

    /**
     * Retrieve the passed header id from the Hash Map of Header values. If the
     * header id is not in the hash map then return null.
     *
     * @param headerId - int
     * @return OBEXFtpHeader
     */
    public OBEXFtpHeader getHeader(int headerId) {
        return headers.get(headerId);
    }

    /**
     * Return the response code.
     *
     * @return byte
     */
    public byte getResponseCode() {
        return responseCode;
    }

    /**
     * Return the version of OBEX that is being used with the requested device.
     *
     * @return byte
     */
    public byte getVersion() {
        return version;
    }

    /**
     * Return the flag that was set in the response.
     *
     * @return byte
     */
    public byte getFlag() {
        return flags;
    }

    /**
     * Return the length of the response. -1 if the length of the response
     * wasn't found.
     *
     * @return int
     */
    public int length() {
        return length;
    }

    /**
     * Return the maximum length the response can handle. -1 is the max length
     * of the response wasn't found.
     *
     * @return
     */
    public int maxLength() {
        return maxLength;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Response Code: " + OBEXFtpUtils.byteToHex(responseCode) + '\n');
        if (version != null)
            sb.append("Version: " + OBEXFtpUtils.byteToHex(version) + '\n');
        if (flags != null)
            sb.append("Flags: " + OBEXFtpUtils.byteToHex(flags) + '\n');
        if (maxLength != -1)
            sb.append("Maximum Length: " + maxLength + '\n');
        if (length != -1)
            sb.append("Length: " + length + '\n');
        sb.append("Headers: \n");
        for(int i = 0; i < headers.size(); i++) {
            int key = headers.keyAt(i);
            sb.append("\n\t" + headers.get(key) + '\n');
        }
        return sb.toString();
    }
}
