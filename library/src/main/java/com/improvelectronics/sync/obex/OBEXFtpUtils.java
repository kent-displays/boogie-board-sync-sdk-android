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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class OBEXFtpUtils {

    /* UUIDS */
    // OBEX FTP UUID = F9EC7BC4-953C-11d2-984E-525400DC9E09
    public static byte[] OBEX_FTP_UUID = {(byte) 0xF9, (byte) 0xEC, (byte) 0x7B, (byte) 0xC4, (byte) 0x95,
            (byte) 0x3C, (byte) 0x11, (byte) 0xD2, (byte) 0x98, (byte) 0x4E, (byte) 0x52, (byte) 0x54, (byte) 0x00,
            (byte) 0xDC, (byte) 0x9E, (byte) 0x09};
    /* MIME TYPES */
    // "x-bluetooth/folder-listing" and null terminator
    public static byte[] FOLDER_LISTING_TYPE = {(byte) 0x78, (byte) 0x2D, (byte) 0x6F, (byte) 0x62, (byte) 0x65,
            (byte) 0x78, (byte) 0x2F, (byte) 0x66, (byte) 0x6F, (byte) 0x6C, (byte) 0x64, (byte) 0x65, (byte) 0x72,
            (byte) 0x2D, (byte) 0x6C, (byte) 0x69, (byte) 0x73, (byte) 0x74, (byte) 0x69, (byte) 0x6E, (byte) 0x67,
            (byte) 0x00};
    private static final String TAG = "OBEXFtpUtils";

    /**
     * Converts a OBEX server response to an ArrayList of Items
     *
     * @param response - Response from the server.
     * @return ArrayList<OBEXFtpFolderListingItem>
     */

    public static ArrayList<OBEXFtpFolderListingItem> parseXML(byte[] response) {

        String rawXML = new String(response);

        // Cut out anything before the XML document actually starts.
        rawXML = rawXML.substring(rawXML.indexOf("<?xml"), rawXML.length());

        // get the factory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;

        try {

            // Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            // parse using builder to get DOM representation of the XML file
            dom = db.parse(new InputSource(new StringReader(rawXML)));

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        if (dom != null)
            return parseDocument(dom);

        return null;
    }

    /**
     * Parses the XML document and returns an array list of items.
     *
     * @param d Document of XML
     * @return ArrayList<OBEXFtpFolderListingItem>
     */

    public static ArrayList<OBEXFtpFolderListingItem> parseDocument(Document d) {
        ArrayList<OBEXFtpFolderListingItem> bluetoothFtpFolderListingItems = new ArrayList<OBEXFtpFolderListingItem>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

        Element docEle = d.getDocumentElement();

        // Make this code more efficient possibly another method call
        NodeList nl = docEle.getElementsByTagName("folder");
        try {
            if (nl != null && nl.getLength() > 0) {
                for (int i = 0; i < nl.getLength(); i++) {
                    // get the the folder element
                    Element el = (Element) nl.item(i);
                    String name = el.getAttribute("name");
                    Date time = null;

                    if (el.getAttribute("modified").compareTo("") != 0) {
                        time = sdf.parse(el.getAttribute("modified"));
                    } else if (el.getAttribute("created").compareTo("") != 0)
                        time = sdf.parse(el.getAttribute("created"));
                    int size = 0;
                    bluetoothFtpFolderListingItems.add(new OBEXFtpFolderListingItem(name, time, size, new byte[]{}));
                }
            }
            nl = docEle.getElementsByTagName("file");
            if (nl != null && nl.getLength() > 0) {
                for (int i = 0; i < nl.getLength(); i++) {
                    // get the the folder element
                    Element el = (Element) nl.item(i);
                    String name = el.getAttribute("name");
                    Date time = null;
                    if (el.getAttribute("modified").compareTo("") != 0)
                        time = sdf.parse(el.getAttribute("modified"));
                    else if (el.getAttribute("created").compareTo("") != 0)
                        time = sdf.parse(el.getAttribute("created"));
                    int size = Integer.parseInt(el.getAttribute("size"));
                    bluetoothFtpFolderListingItems.add(new OBEXFtpFolderListingItem(name, time, size, new byte[]{}));
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date.");
        }

        // Sort the items by date.
        Collections.sort(bluetoothFtpFolderListingItems, new Comparator<OBEXFtpFolderListingItem>() {
            @Override
            public int compare(OBEXFtpFolderListingItem lhs, OBEXFtpFolderListingItem rhs) {
                // First compare based on type of item. This will rank folders above files.
                if(lhs.getSize() == 0 && rhs.getSize() > 0) return -1;
                else if(lhs.getSize() > 0 && rhs.getSize() == 0) return 1;

                // If the items are the same type then compare based on date.
                if (lhs.getTime().before(rhs.getTime())) {
                    return 1;
                } else if (lhs.getTime().after(rhs.getTime())) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        return bluetoothFtpFolderListingItems;
    }

    /**
     * Convenience method to convert a byte array to a hex string.
     *
     * @param data the byte[] to convert
     * @return String the converted byte[]
     */
    public static String bytesToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            buf.append(byteToHex(data[i]).toUpperCase());
        }
        return (buf.toString());
    }

    /**
     * method to convert a byte to a hex string.
     *
     * @param data the byte to convert
     * @return String the converted byte
     */
    public static String byteToHex(byte data) {
        StringBuffer buf = new StringBuffer();
        buf.append(toHexChar((data >>> 4) & 0x0F));
        buf.append(toHexChar(data & 0x0F) + " ");
        return buf.toString();
    }

    /**
     * Convenience method to convert an int to a hex char.
     *
     * @param i the int to convert
     * @return char the converted char
     */
    public static char toHexChar(int i) {
        if ((0 <= i) && (i <= 9)) {
            return (char) ('0' + i);
        } else {
            return (char) ('a' + (i - 10));
        }
    }

    /**
     * Create a two byte array from a integer.
     *
     * @param length
     * @return int
     */
    public static byte[] lengthToBytes(short length) {
        byte[] bytes = new byte[2];
        bytes[1] = (byte)(length & 0xff);
        bytes[0] = (byte)((length >> 8) & 0xff);
        return bytes;
    }

    /**
     * Converts the string to a hex representation with leading 0 byte and 2
     * null terminating byte
     *
     * @param String string (String to be converted to hex.)
     * @return byte[] with converted string
     */
    public static byte[] stringToHex(String string) {
        // Convert the string to hex
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        byte name_bytes_temp[] = string.getBytes();
        int length = name_bytes_temp.length;
        for (int i = 0; i < (length + 1) * 2; ++i) {
            if (i % 2 == 1 && (i - 1) / 2 < length) {
                stream.write(name_bytes_temp[(i - 1) / 2]);
            } else
                stream.write((byte) 0);
        }

        return stream.toByteArray();
    }

    /**
     * Method to find the decimal representation of the two bytes
     *
     * @param byte a
     * @param byte b
     * @return int containing the decimal representation of the bytes
     */
    public static int getLength(byte a, byte b) {
        int first = a;
        int second = b;
        if (first < 0)
            first += 256;
        if (second < 0)
            second += 256;
        return (first << 8) + second;
    }
}
