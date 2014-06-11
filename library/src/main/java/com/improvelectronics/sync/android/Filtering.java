/*******************************************************************************
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
 ******************************************************************************/

package com.improvelectronics.sync.android;

import java.util.ArrayList;
import java.util.List;

public class Filtering {
    public enum PathState {
        NO_POINTS, ONE_POINT, MULTIPLE_POINTS
    }

    // Switch states.
    private static final byte TSW_FLAG = 0x01;
    private static final byte RDY_FLAG = 0x01 << 2;

    // Set distance threshold for drawing a new segment (10*0.01mm = 0.1mm).
    private static final int DISTANCE_THRESHOLD_SQUARED = (10 * 10);

    private static PathState mPathState = PathState.NO_POINTS;
    private static Filter mFilter = new Filter();

    // State of line width filter.
    private static float mOldLineWidth = -1.0f;

    private static SyncCaptureReport mLastCapture;

    public static List<SyncPath> filterSyncCaptureReport(SyncCaptureReport captureReport) {
        float lineWidth;
        int distSquared;
        float velAvg, pressAvg;
        int i;
        List<SyncPath> paths = new ArrayList<SyncPath>();

        // Process based on number of points already received in current trace.
        switch (mPathState) {
            case NO_POINTS:
                if ((captureReport.getFlags() & (RDY_FLAG + TSW_FLAG)) == (RDY_FLAG + TSW_FLAG))  // Contact?
                {
                    // Have first point.
                    mPathState = PathState.ONE_POINT;

                    // Initialize the dynamic filter.
                    setFilterPosition(mFilter, captureReport);

                    // Reset filter for line width.
                    resetLineWidthFilter();
                }
                break;

            case ONE_POINT:
                if ((captureReport.getFlags() & (RDY_FLAG + TSW_FLAG)) == (RDY_FLAG + TSW_FLAG))  // Contact?
                {
                    // Apply filter and get distance**2 of filtered position from last rendered position.
                    distSquared = applyFilter(mFilter, captureReport);

                    // Render new position to PDF if sufficiently far from last rendered position.
                    if (distSquared >= DISTANCE_THRESHOLD_SQUARED) {
                        mPathState = PathState.MULTIPLE_POINTS;

                        // Compute/draw the first segment of the trace to PDF.
                        velAvg = (float) Math.sqrt(distSquared) / mFilter.time;
                        pressAvg = ((float) mFilter.last.pressure + mFilter.current.pressure) / 2;
                        lineWidth = computeLineWidth(velAvg, pressAvg);

                        paths.add(createPathWithLineWidth(lineWidth));

                        // Reset "last" point for filter.
                        setLastFilter(mFilter);
                    }
                } else  // No contact.
                {
                    mPathState = PathState.NO_POINTS;

                    // Draw the dot/period for the single point to PDF.
                    velAvg = -1.0f;
                    pressAvg = mFilter.current.pressure;
                    lineWidth = computeLineWidth(velAvg, pressAvg);

                    paths.add(createPathWithLineWidth(lineWidth));
                }
                break;

            case MULTIPLE_POINTS:
                if ((captureReport.getFlags() & (RDY_FLAG + TSW_FLAG)) == (RDY_FLAG + TSW_FLAG))  // Contact?
                {
                    // Apply filter and get distance**2 of filtered position from last rendered position.
                    distSquared = applyFilter(mFilter, captureReport);

                    // Render new position to PDF if sufficiently far from last rendered position.
                    if (distSquared >= DISTANCE_THRESHOLD_SQUARED) {
                        // Compute/draw the next trace segment to PDF.
                        velAvg = (float)Math.sqrt(distSquared) / mFilter.time;
                        pressAvg = ((float) mFilter.last.pressure + mFilter.current.pressure) / 2;
                        lineWidth = computeLineWidth(velAvg, pressAvg);

                        paths.add(createPathWithLineWidth(lineWidth));

                        // Reset "last" point for filter.
                        setLastFilter(mFilter);
                    }
                } else  // No contact.
                {
                    mPathState = PathState.NO_POINTS;

                    // Will use fixed (current) velocity to compute line width during final convergence
                    // to prevent artificial blobbing at the end of traces (due to artificial slowdown
                    // induced by repeating final digitizer coordinate).
                    velAvg = (float)Math.sqrt(mFilter.velocity.x * mFilter.velocity.x + mFilter.velocity.y * mFilter.velocity.y);

                    // Provide filter final coordinate multiple times to converge on pen up point.
                    for (i = 0; i < 4; i++) {
                        // Apply filter and get distance**2 of filtered position from last rendered position.
                        distSquared = applyFilter(mFilter, mLastCapture);

                        // Render new position to PDF if sufficiently far from last rendered position.
                        if (distSquared >= DISTANCE_THRESHOLD_SQUARED) {
                            // Compute line width.
                            pressAvg = ((float) mFilter.last.pressure + mFilter.current.pressure) / 2;
                            lineWidth = computeLineWidth(velAvg, pressAvg);

                            paths.add(createPathWithLineWidth(lineWidth));

                            // Reset "last" point for filter.
                            setLastFilter(mFilter);
                        }
                    }
                }
                break;
        }

        // Store coordinate for finalizing trace at pen up.
        mLastCapture = captureReport;

        return paths;
    }

    /**
     * Clears line width filter for start of a new trace.
     */
    private static void resetLineWidthFilter() {
        mOldLineWidth = -1.0f;
    }

    /**
     * Initializes a provided dynamic filter with the first point in a trace.
     *
     * @param f
     * @param captureReport
     */
    private static void setFilterPosition(Filter f, SyncCaptureReport captureReport) {
        f.last.x = f.current.x = (int) captureReport.getX();
        f.last.y = f.current.y = (int) captureReport.getY();
        f.last.pressure = f.current.pressure = (int) captureReport.getPressure();
        f.velocity.x = f.velocity.y = f.velocity.pressure = 0;
        f.time = 0;
    }

    /**
     * Notifies a provided dynamic filter that a new segment has been drawn.
     *
     * @param f
     */
    private static void setLastFilter(Filter f) {
        f.last.x = f.current.x;
        f.last.y = f.current.y;
        f.last.pressure = f.current.pressure;
        f.time = 0;
    }

    // Dynamic filter Proportional and Derivative controller gains
    // (includes effects of mass and sample time (K*T/mass)).
    private static final int KPP = 1229;   // 1229/8192 = 0.1500 ~0.15f
    private static final int KDD = 4915;   // 4915/8192 = 0.6000 ~0.6f

    // Updates dynamic filter state based on new reference coordinate.
    private static int applyFilter(Filter f, SyncCaptureReport captureReport) {
        int ax, ay, ap;
        int dist_sq;

        // Update delta time (samples) since last segment drawn (threshold met).
        if (f.time < 255)
            f.time++;

        // Calculate 8192 (= 2^13) x acceleration.
        ax = KPP * ((int) captureReport.getX() - f.current.x) - KDD * f.velocity.x;
        ay = KPP * ((int) captureReport.getY() - f.current.y) - KDD * f.velocity.y;
        ap = KPP * ((int) captureReport.getPressure() - f.current.pressure) - KDD * f.velocity.pressure;

        // Calculate new position.
        f.current.x += f.velocity.x;
        f.current.y += f.velocity.y;
        f.current.pressure += f.velocity.pressure;

        // Calculate new velocity.
        f.velocity.x = (((int) f.velocity.x << 13) + ax) >> 13;
        f.velocity.y = (((int) f.velocity.y << 13) + ay) >> 13;
        f.velocity.pressure = (((int) f.velocity.pressure << 13) + ap) >> 13;

        // Calculate squared distance of current point from "last" point.
        dist_sq = ((f.current.x - f.last.x) * (f.current.x - f.last.x) + (f.current.y - f.last.y) * (f.current.y - f.last.y));

        return dist_sq;
    }

    /**
     * Convert stylus pressure/speed into a line width value expressed in digitizer units. If vel < 0, the stylus was lifted after a single contact
     * point.
     *
     * @param vel        velocity expressed in digitizer units per sample interval
     * @param pressure   digitizer pressure reading
     */
    private static float computeLineWidth(float vel, float pressure) {
        int i, j;
        float dist;
        float lwa, lwb, lw;

        // Compute distance btw. successive samples in digitizer units.
        if (vel < 0)
            dist = velocityToDistance(75.0f);   // Don't know real speed if only have one point => Assume a mid-level.
        else
            dist = vel;

        // Saturate distance at range we have data for.
        if (dist < lineWidthMapArray[0].distance)
            dist = lineWidthMapArray[0].distance;
        else if (dist > lineWidthMapArray[lineWidthMapArray.length - 1].distance)
            dist = lineWidthMapArray[lineWidthMapArray.length - 1].distance;

        // Saturate pressure at range we have data for.
        if (pressure < mass[0])
            pressure = mass[0];
        else if (pressure > mass[mass.length - 1])
            pressure = mass[mass.length - 1];

        // Find the indices for distance (velocity).
        for (i = 1; i < lineWidthMapArray.length; i++) {
            if (dist <= lineWidthMapArray[i].distance)
                break;
        }

        // Find the indices for mass (pressure).
        for (j = 1; i < mass.length; j++) {
            if (pressure <= mass[j])
                break;
        }

        // Interpolate based on mass (pressure) first.
        lwa = lineWidthMapArray[i - 1].lineWidth[j - 1] + (pressure - mass[j - 1]) * (lineWidthMapArray[i - 1].lineWidth[j] - lineWidthMapArray[i - 1].lineWidth[j - 1]) / (mass[j] - mass[j - 1]);
        lwb = lineWidthMapArray[i].lineWidth[j - 1] + (pressure - mass[j - 1]) * (lineWidthMapArray[i].lineWidth[j] - lineWidthMapArray[i].lineWidth[j - 1]) / (mass[j] - mass[j - 1]);

        // Interpolate based on speed (distance) second.
        lw = lwa + (dist - lineWidthMapArray[i - 1].distance) * (lwb - lwa) / (lineWidthMapArray[i].distance - lineWidthMapArray[i - 1].distance);

        // Initialize filter if needed.
        // (The max value helps eliminate ink blobs at the start of traces due to impact pressures and/or low speeds.)
        if (mOldLineWidth < 0)
            mOldLineWidth = (lw > 45.0f ? 45.0f : lw);

        //  Filter A:  (LW changes too quickly for close samples and too slowly for far samples.)
        //  lw = (lw + 7*oldLW)/8;

        //  Filter B:
        //  if (dist <= oldLW)
        //    {
        //      lw = 0.1*lw + 0.9*oldLW;
        //    }
        //  else if (dist <= 5*oldLW)
        //    {
        //      float alpha = 0.1 + 0.9*(dist-oldLW)/(4*oldLW);
        //      lw = alpha*lw + (1 - alpha)*oldLW;
        //    }

        //  Filter C:  ** Seems to perform the best.
        lw = (2 * dist * lw + mOldLineWidth * mOldLineWidth) / (2 * dist + mOldLineWidth);

        //  Filter D:
        //  lw = (2*dist + oldLW)/(2*dist + lw)*lw;

        // Remember last linewidth for filtering.
        mOldLineWidth = lw;

        // Store the line width.
        return lw;
    }

    private static SyncPath createPathWithLineWidth(float lineWidth) {
        SyncPath path = new SyncPath();
        path.moveTo(mFilter.last.x, mFilter.last.y);
        path.setStrokeWidth(lineWidth);
        path.lineTo(mFilter.current.x, mFilter.current.y);
        return path;
    }

    // Digitizer resolution is 0.01 mm.
    private static final int TICKS_PER_MM = 100;
    // 144.425 samples per second
    private static final float MS_PER_SAMPLE = 6.924f;
    // Assuming stylus held at 30 deg angle.
    private static final float PEN_ANGLE_COS = 0.866f;
    // Scale factor for reported linewidth (to make recorded lines sharper than actual device).
    private static final float SCALE = 0.75f;

    // Array of digitizer pressure readings for which line widths are provided.
    private static final int mass[] = {massToPressure(10.0f), massToPressure(25.0f), massToPressure(50.0f), massToPressure(100.0f),
            massToPressure(150.0f), massToPressure(200.0f), massToPressure(250.0f), massToPressure(300.0f), massToPressure(350.0f),
            massToPressure(400.0f), massToPressure(450.0f), massToPressure(500.0f), massToPressure(550.0f), massToPressure(600.0f)};

    // Array of line widths vs. pressure at various velocities.
    private static final LineWidthMap lineWidthMapArray[] = new LineWidthMap[]
            {
                    //   v(mm/s)       10g*               25g*               50g               100g               150g               200g
                    //     250g               300g               350g               400g               450g               500g               550g*
                    //             600g*
                    new LineWidthMap(velocityToDistance(1.0f), new float[]{mmToDigitizer(0.720000f), mmToDigitizer(0.800000f),
                            mmToDigitizer(0.908937f), mmToDigitizer(1.108957f), mmToDigitizer(1.266351f), mmToDigitizer(1.388042f),
                            mmToDigitizer(1.462073f), mmToDigitizer(1.540000f), mmToDigitizer(1.618852f), mmToDigitizer(1.701938f),
                            mmToDigitizer(1.793265f), mmToDigitizer(1.860000f), mmToDigitizer(1.920000f), mmToDigitizer(1.954108f)}),
                    new LineWidthMap(velocityToDistance(5.0f), new float[]{mmToDigitizer(0.490000f), mmToDigitizer(0.530000f),
                            mmToDigitizer(0.614119f), mmToDigitizer(0.758321f), mmToDigitizer(0.868824f), mmToDigitizer(0.910000f),
                            mmToDigitizer(0.942034f), mmToDigitizer(1.000218f), mmToDigitizer(1.047881f), mmToDigitizer(1.083052f),
                            mmToDigitizer(1.155148f), mmToDigitizer(1.196536f), mmToDigitizer(1.250000f), mmToDigitizer(1.286546f)}),
                    new LineWidthMap(velocityToDistance(30.0f), new float[]{mmToDigitizer(0.300000f), mmToDigitizer(0.340000f),
                            mmToDigitizer(0.387672f), mmToDigitizer(0.493372f), mmToDigitizer(0.565948f), mmToDigitizer(0.620261f),
                            mmToDigitizer(0.673648f), mmToDigitizer(0.710716f), mmToDigitizer(0.746997f), mmToDigitizer(0.777846f),
                            mmToDigitizer(0.815101f), mmToDigitizer(0.837235f), mmToDigitizer(0.880000f), mmToDigitizer(0.926857f)}),
                    new LineWidthMap(velocityToDistance(75.0f), new float[]{mmToDigitizer(0.290000f), mmToDigitizer(0.295000f),
                            mmToDigitizer(0.320000f), mmToDigitizer(0.374948f), mmToDigitizer(0.422921f), mmToDigitizer(0.473530f),
                            mmToDigitizer(0.508386f), mmToDigitizer(0.541358f), mmToDigitizer(0.577623f), mmToDigitizer(0.600577f),
                            mmToDigitizer(0.621771f), mmToDigitizer(0.651861f), mmToDigitizer(0.670000f), mmToDigitizer(0.690000f)}),
                    new LineWidthMap(velocityToDistance(100.0f), new float[]{mmToDigitizer(0.280000f), mmToDigitizer(0.290000f),
                            mmToDigitizer(0.302881f), mmToDigitizer(0.338898f), mmToDigitizer(0.387231f), mmToDigitizer(0.433664f),
                            mmToDigitizer(0.452389f), mmToDigitizer(0.482745f), mmToDigitizer(0.516970f), mmToDigitizer(0.534589f),
                            mmToDigitizer(0.557370f), mmToDigitizer(0.581577f), mmToDigitizer(0.610000f), mmToDigitizer(0.620000f)}),
                    new LineWidthMap(velocityToDistance(180.0f), new float[]{mmToDigitizer(0.250000f), mmToDigitizer(0.260000f),
                            mmToDigitizer(0.280375f), mmToDigitizer(0.311056f), mmToDigitizer(0.362906f), mmToDigitizer(0.390511f),
                            mmToDigitizer(0.414745f), mmToDigitizer(0.436406f), mmToDigitizer(0.463840f), mmToDigitizer(0.478165f),
                            mmToDigitizer(0.501515f), mmToDigitizer(0.521805f), mmToDigitizer(0.540000f), mmToDigitizer(0.550000f)})
            };

    /**
     * Convert from velocity in mm/s to distance (in digitizer units) between successive samples.
     *
     * @param velocity
     * @return
     */
    private static float velocityToDistance(float velocity) {
        return ((velocity) * TICKS_PER_MM * MS_PER_SAMPLE / 1000);
    }

    /**
     * Convert from line width expressed in mm to scaled line width expressed in digitizer units.
     *
     * @param mm
     * @return
     */
    private static float mmToDigitizer(float mm) {
        return ((mm) * TICKS_PER_MM * SCALE);
    }

    /**
     * Convert from mass in grams (normal to surface) to corresponding digitizer pressure reading (along stylus).
     *
     * @param mass
     * @return
     */
    private static int massToPressure(float mass) {
        return (int) ((mass) * PEN_ANGLE_COS * 1023.0f / 600.0f + 0.5f);
    }

    private static class Filter {
        public Coordinate last;
        public Coordinate current;
        public Coordinate velocity;
        public int time;

        public Filter() {
            last = new Coordinate();
            current = new Coordinate();
            velocity = new Coordinate();
        }
    }

    private static class LineWidthMap {
        public float distance;          // In digitizer units (speed ~ distance between consecutive points).
        public float lineWidth[];     // In digitizer units.

        public LineWidthMap(float distance, float lineWidth[]) {
            this.distance = distance;
            this.lineWidth = lineWidth;
        }
    }

    private static class Coordinate {
        public int x;
        public int y;
        public int pressure;
    }
}