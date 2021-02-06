package com.example.myapplication;

import android.graphics.Matrix;
import android.media.Image;

import java.nio.ByteBuffer;

//utility class to manipulate image
public class ImageUtilities {
    @SuppressWarnings("unused")
    private static Logger LOGGER = new Logger();

    // it is 2 ^ 18 - 1, used to clamp the RGB values before their ranges are normalized to eight bits
    private static int kMaxChannelValue = 262143;

    public static void convertYUV420SPToARGB8888(
            byte[] input,
            int width,
            int height,
            int[] output) {

        // Java implementation of YUV420SP to ARGB8888 converting
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width;
            int u = 0;
            int v = 0;

            for (int i = 0; i < width; i++, yp++) {
                int y = 0xff & input[yp];
                if ((i & 1) == 0) {
                    v = 0xff & input[uvp++];
                    u = 0xff & input[uvp++];
                }

                output[yp] = YUV2RGB(y, u, v);
            }
        }
    }

    private static int YUV2RGB(int y, int u, int v) {
        // Adjust and check YUV values
        if ((y-16)<0)
            y=0;
        else
            y = y-16;

        u -= 128;
        v -= 128;

        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        // Clip RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }


    //convert YUV420 to ARGB8888
    public static void convertYUV420ToARGB8888(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            int[] out) {
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YUV2RGB(
                        0xff & yData[pY + i],
                        0xff & uData[uv_offset],
                        0xff & vData[uv_offset]);
            }
        }
    }

     //returns a transformation matrix, do cropping and rotation
    public static Matrix getTransformationMatrix(int sourceWidth, int sourceHeight, int destinationWidth, int destinationHeight, int rotationApplied, boolean maintainAspectRatio) {
        //rotationApplied - amount of rotation to apply, must change by 90
        //maintainAspectRatio - true when scaling remain same
        Matrix matrix = new Matrix();

        if (rotationApplied != 0) {
            if (rotationApplied % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", rotationApplied);
            }

            // Do translate to let center of image as origin
            matrix.postTranslate(-sourceWidth / 2.0f, -sourceHeight / 2.0f);

            // Rotate around origin
            matrix.postRotate(rotationApplied);
        }

        boolean transpose = (Math.abs(rotationApplied) + 90) % 180 == 0;

        int inWidth = transpose ? sourceHeight : sourceWidth;
        int inHeight = transpose ? sourceWidth : sourceHeight;

        // Do scaling if needed
        if (inWidth != destinationWidth || inHeight != destinationHeight) {
            float scaleFactorX = destinationWidth / (float) inWidth;
            float scaleFactorY = destinationHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor to allow destination to be filled fully
                float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill destination from source
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (rotationApplied != 0) {
            // Translate back from origin centered reference to destination frame
            matrix.postTranslate(destinationWidth / 2.0f, destinationHeight / 2.0f);
        }

        return matrix; //return transformation fulfilling the desired requirements
    }
}
