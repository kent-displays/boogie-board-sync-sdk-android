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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Class used to represent a item returned from an OBEX Folder Listing command that can either be a file or a folder.
 * </p>
 * To differentiate between a file and a folder, folder has a size of 0 where a file's size is greater than 0.
 */
public class OBEXFtpFolderListingItem implements Comparable<OBEXFtpFolderListingItem>, Parcelable {
    public static final Parcelable.Creator<OBEXFtpFolderListingItem> CREATOR = new Parcelable.Creator<OBEXFtpFolderListingItem>() {

        @Override
        public OBEXFtpFolderListingItem createFromParcel(Parcel source) {
            return new OBEXFtpFolderListingItem(source);
        }

        @Override
        public OBEXFtpFolderListingItem[] newArray(int size) {
            return new OBEXFtpFolderListingItem[size];
        }
    };
    private String name;
    private int size;
    private byte[] data;
    private Date time;

    public OBEXFtpFolderListingItem(String name, Date time, int size, byte[] data) {
        this.name = name;
        this.time = time;
        this.size = size;
        this.data = data;
    }

    private OBEXFtpFolderListingItem(Parcel source) {
        readFromParcel(source);
    }

    public String getName() {
        return name;
    }

    public Date getTime() {
        return time;
    }

    public int getSize() {
        return size;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String toString() {
        return "Name: " + name + " Time: " + time + " Size: " + size + " Data: " + data;
    }

    @Override
    public int compareTo(OBEXFtpFolderListingItem another) {
        /*
         * if (this.getCreated().before(another.getCreated())) return 1; else
		 * return -1;
		 */
        return getName().compareTo(another.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (!(obj instanceof OBEXFtpFolderListingItem)) return false;
        OBEXFtpFolderListingItem o = (OBEXFtpFolderListingItem) obj;
        return o.getName().equals(this.getName()) && o.getTime().equals(this.getTime()) && o.getSize() == this.getSize();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(size);
        dest.writeLong(time.getTime());
        dest.writeInt(data.length);
        dest.writeByteArray(data);
    }

    public void readFromParcel(Parcel source) {
        name = source.readString();
        size = source.readInt();
        time = new Date(source.readLong());
        data = new byte[source.readInt()];
        source.readByteArray(data);
    }
}