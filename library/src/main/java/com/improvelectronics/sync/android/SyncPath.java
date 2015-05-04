/**
 * **************************************************************************
 * Copyright © 2014 Kent Displays, Inc.
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE..
 * **************************************************************************
 */

package com.improvelectronics.sync.android;

import android.graphics.Path;
import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extension of the Android graphics {@link android.graphics.Path Path} class that stores all the
 * points which are added to it and a stroke width.
 */
public class SyncPath extends Path implements Parcelable {

    private float mStrokeWidth;
    private List<PointF> mPoints;

    public SyncPath() {
        super();
        mStrokeWidth = 0;
        mPoints = new ArrayList<>();
    }

    public SyncPath(Path path) {
        super(path);

        if (path instanceof SyncPath) {
            mStrokeWidth = ((SyncPath) path).getStrokeWidth();
            mPoints = new ArrayList<>(((SyncPath) path).getPoints());
        }
    }

    private SyncPath(Parcel in) {
        super();

        mStrokeWidth = in.readFloat();
        in.readList(mPoints, null);
        drawFromPoints();
    }

    private void drawFromPoints() {
        for (int i = 0; i < mPoints.size(); i++) {
            PointF point = mPoints.get(i);
            if (i == 0) {
                moveTo(point.x, point.y);
            } else {
                lineTo(point.x, point.y);
            }
        }
    }

    public void setStrokeWidth(float strokeWidth) {
        mStrokeWidth = strokeWidth;
    }

    public float getStrokeWidth() {
        return mStrokeWidth;
    }

    @Override
    public void moveTo(float x, float y) {
        mPoints.add(new PointF(x, y));
        super.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        mPoints.add(new PointF(x, y));
        super.lineTo(x, y);
    }

    @Override
    public void reset() {
        super.reset();
        mStrokeWidth = 0.0f;
        mPoints.clear();
    }

    public List<PointF> getPoints() {
        return mPoints;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mStrokeWidth);
        dest.writeList(mPoints);
    }

    public static final Parcelable.Creator<SyncPath> CREATOR
            = new Parcelable.Creator<SyncPath>() {
        public SyncPath createFromParcel(Parcel in) {
            return new SyncPath(in);
        }

        public SyncPath[] newArray(int size) {
            return new SyncPath[size];
        }
    };


    @Override
    public boolean equals(Object o) {
        // Return true if the objects are identical.
        // (This is just an optimization, not required for correctness.)
        if (this == o) {
            return true;
        }

        // Return false if the other object has the wrong type.
        // This type may be an interface depending on the interface's specification.
        if (!(o instanceof SyncPath)) {
            return false;
        }

        // Cast to the appropriate type.
        // This will succeed because of the instanceof, and lets us access private fields.
        SyncPath lhs = (SyncPath) o;

        // Check each field. Primitive fields, reference fields, and nullable reference
        // fields are all treated differently.
        if (mStrokeWidth != lhs.getStrokeWidth()) {
            return false;
        }

        if (mPoints == null && lhs.getPoints() == null) {
            return true;
        } else if (mPoints == null) {
            return false;
        }

        if (mPoints.size() != lhs.getPoints().size()) {
            return false;
        }

        for (int i = 0; i < mPoints.size(); i++) {
            PointF point = mPoints.get(i);
            PointF lhsPoint = lhs.getPoints().get(i);
            if (!point.equals(lhsPoint.x, lhsPoint.y)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        // Start with a non-zero constant.
        int result = 17;

        // Include a hash for each field.
        result = 31 * result + (int)mStrokeWidth;

        result = 31 * result + mPoints.hashCode();

        return result;
    }
}
