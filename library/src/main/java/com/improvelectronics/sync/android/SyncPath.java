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
 THE SOFTWARE..
 ****************************************************************************/

package com.improvelectronics.sync.android;

import android.graphics.Path;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension of the Android graphics {@link android.graphics.Path Path} class that stores all the points which are added to it and a stroke width.
 */
public class SyncPath extends Path {

    private float mStrokeWidth;
    private List<PointF> mPoints;

    public SyncPath() {
        super();
        mStrokeWidth = 0;
        mPoints = new ArrayList<PointF>();
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

    public List<PointF> getPoints() {
        return mPoints;
    }
}
