package com.example.myapplication;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class TFObjectDetectionAPIModel implements ImageClassifier {
    private int inputSize;
    private static final String LOGGING_TAG = TFObjectDetectionAPIModel.class.getName();
    private static final float IMAGE_MEAN = 128f;
    private static final float IMAGE_STD = 128f;
    private static final int NUM_DETECTIONS = 10;

    private boolean isModelQuantized;

    private ByteBuffer imgData;
    private Interpreter tfLite;
    private int[] intValues;
    private float[][][] outputLocations;
    private float[][] outputClasses;
    private float[][] outputScores;
    private float[] numDetections;
    private Vector<String> labels = new Vector<String>();

    private TFObjectDetectionAPIModel() {

    }

    public static ImageClassifier create(AssetManager assetManager, String modelFilename, String labelFilename, int inputSize, boolean isQuantized)
            throws IOException {
        final TFObjectDetectionAPIModel model = new TFObjectDetectionAPIModel();

        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        InputStream labelsInput = assetManager.open(actualFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            Log.w(LOGGING_TAG,line);
            model.labels.add(line);
        }
        br.close();

        model.inputSize = inputSize;

        try {
            model.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        model.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }
        model.imgData = ByteBuffer.allocateDirect(1 * model.inputSize * model.inputSize * 3 * numBytesPerChannel);
        model.imgData.order(ByteOrder.nativeOrder());
        model.intValues = new int[model.inputSize * model.inputSize];

        model.outputLocations = new float[1][NUM_DETECTIONS][4];
        model.outputClasses = new float[1][NUM_DETECTIONS];
        model.outputScores = new float[1][NUM_DETECTIONS];
        model.numDetections = new float[1];
        return model;
    }

    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }



    @Override
    public List<ImageClassifier.Recognition> detectObjects(final Bitmap bitmap) {

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        final ArrayList<Recognition> recognitions = new ArrayList<>(NUM_DETECTIONS);
        for (int i = 0; i < NUM_DETECTIONS; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[0][i][1] * inputSize,
                            outputLocations[0][i][0] * inputSize,
                            outputLocations[0][i][3] * inputSize,
                            outputLocations[0][i][2] * inputSize);

            int labelOffset = 1;
            recognitions.add(
                    new Recognition(
                            "" + i,
                            labels.get((int) outputClasses[0][i] + labelOffset),
                            outputScores[0][i],
                            detection));
        }
        return recognitions;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {}

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {}

}
