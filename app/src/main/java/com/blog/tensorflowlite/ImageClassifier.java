package com.blog.tensorflowlite;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;


@SuppressWarnings("deprecation")
class ImageClassifier {

    static final int DIM_IMG_SIZE_X = 224;
    static final int DIM_IMG_SIZE_Y = 224;
    private static final String TAG = "ImageClassifier";
    /**
     * Name of the model file stored in Assets.
     */
    private static final String MODEL_PATH = "mobilenet_quant_v1_224.tflite";
    /**
     * Name of the label file stored in Assets.
     */
    private static final String LABEL_PATH = "labels.txt";
    /**
     * Number of results to show in the UI.
     */
    private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    /* Preallocated buffers for storing image data in. */
    private final int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    private Interpreter tflite;

    /**
     * Labels corresponding to the output of the vision model.
     */
    private List<String> labelList;

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    private ByteBuffer imgData;

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs.
     */
    private byte[][] labelProbArray;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    (o1, o2) -> (o1.getValue()).compareTo(o2.getValue()));

    /**
     * Initializes an {@code ImageClassifier}.
     */
    ImageClassifier(Activity activity) throws IOException {
        tflite = new Interpreter(loadModelFile(activity));
        labelList = loadLabelList(activity);
        imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        labelProbArray = new byte[1][labelList.size()];
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
    }

    /**
     * Classifies a frame from the preview stream.
     */
    String classifyFrame(Bitmap bitmap) {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            return "Uninitialized Classifier.";
        }
        convertBitmapToByteBuffer(bitmap);
        // Here's where the magic happens!!!
        long startTime = SystemClock.uptimeMillis();
        tflite.run(imgData, labelProbArray);
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to run model inference: " + (endTime - startTime));
        String textToShow = printTopKLabels();
        textToShow = (endTime - startTime) + "ms" + textToShow;
        return textToShow;
    }

    /**
     * Reads label list from Assets.
     */
    private List<String> loadLabelList(Activity activity) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    /**
     * Memory-map the model file in Assets.
     */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        long startTime = SystemClock.uptimeMillis();
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.put((byte) ((val >> 16) & 0xFF));
                imgData.put((byte) ((val >> 8) & 0xFF));
                imgData.put((byte) (val & 0xFF));
            }
        }
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to put values into ByteBuffer: " + (endTime - startTime));
    }


    /**
     * Prints top-K labels, to be shown in UI as the results.
     */
    private String printTopKLabels() {
        for (int i = 0; i < labelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(labelList.get(i), (labelProbArray[0][i] & 0xff) / 255.0f));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        StringBuilder textToShow = new StringBuilder();
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            textToShow.insert(0, "\n" + Objects.requireNonNull(label).getKey() + ":" + label.getValue());
        }
        return textToShow.toString();
    }

    void close() {
        tflite.close();
        tflite = null;
    }
}
