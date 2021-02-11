package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class CameraActivity extends Activity implements OnImageAvailableListener {
    private static final int PERMISSIONS_REQUEST = 1;
    private Handler handler;
    private HandlerThread handlerThread;
    private  int previewHeight=0;
    private  int previewWidth=0;
    private static final Logger LOGGER = new Logger();
    protected abstract int getLayoutId();
    protected abstract Size getWantedPreviewSize();
    private boolean computing = false;
    private Runnable imageConverter;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    public TextToSpeech tts;
    public float speechRate=1f;

    //create camera activity
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //to keep the screen on

        setContentView(R.layout.activity_main);

        //Toolbar appToolbar=findViewById(R.id.appToolbar);
        

        Button playStopButton = findViewById(R.id.btnPlaySpeech);
        Button speechRateButton = findViewById(R.id.button3);
        speechRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //openSpeechRateActivity();
                Intent intent= new Intent(CameraActivity.this, SpeechRateActivity.class);
                startActivityForResult(intent,1);


            }

        });
        //Button speechRateButton = findViewById(R.id.button2);
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Click button", Toast.LENGTH_SHORT).show();
                if (tts != null) {
                    if (tts.isSpeaking()) {
                        tts.stop(); //stop just stop the current recognition, so need shutdown
                        tts.shutdown();

                    }
                    else{
                        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                if (status == TextToSpeech.SUCCESS) {
                                    LOGGER.i("onCreate", "TextToSpeech is initialised");
                                    tts.setSpeechRate(speechRate);
                                } else {
                                    LOGGER.e("onCreate", "Cannot initialise text to speech!");
                                }
                            }
                        });
                    }
                }
            }
        });

        //if has permission
        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission(); //no permission then make request
        }


        this.tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    LOGGER.i("onCreate", "TextToSpeech is initialised");
                    tts.speak("Detector opened", TextToSpeech.QUEUE_FLUSH,null);
                } else {
                    LOGGER.e("onCreate", "Cannot initialise text to speech!");
                }
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, requestCode, data);
        if (requestCode==1){
            if(resultCode==RESULT_OK){
                speechRate= data.getFloatExtra("speechRateSelected",1);
                Toast.makeText(getApplicationContext(), "Speech Rate :"+speechRate, Toast.LENGTH_SHORT).show();
                tts.setSpeechRate(speechRate);
            }
        }
    }
/*
    public void openSpeechRateActivity(){
        Intent intent= new Intent(this, SpeechRateActivity.class);
        startActivity(intent);
    }*/

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        //We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }
            computing = true;
            Trace.beginSection("imageAvailable");
            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtilities.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            computing = false;
                        }
                    };

            sceneProcessing();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

    protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    @Override
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        super.onResume();
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
    }


    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);

        if (!isFinishing()) {
            LOGGER.d("Requesting finish");
            //finish();
        }

        if (!isFinishing()) {
            LOGGER.d("Requesting finish");
            onResume();
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        /*if (tts != null) {
            tts.stop();
            tts.shutdown();
        }*/

        super.onPause();
    }


    @Override
    public synchronized void onStop() {
        LOGGER.d("onStop " + this);
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        LOGGER.d("onDestroy " + this);
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable runnable) {
        if (handler != null) {
            handler.post(runnable);
        }
    }

    //request permission result
    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) { //if permission granted
                    setFragment();
                } else {
                    requestPermission(); //else request permission
                }
            }
        }
    }

    //if permission granted
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //check permission for camera and external storage
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // Returns true if the device supports the required hardware level
    private boolean isHardwareLevelSupported(CameraCharacteristics characteristics, int supportedLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL); //get device supported hardware level
        //if device level equal to required minimum level
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return supportedLevel == deviceLevel;
        }
        // deviceLevel is not legacy, can use numerical sort
        return supportedLevel <= deviceLevel;
    }

    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE); //get camera service
        try {
            //loop through each camera
            for (final String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // do not use a front facing camera
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                //if camera2API can be fully supported
                boolean useCamera2API = isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e("Not allowed to access camera");
        }
        return null;
    }

    protected void setFragment() {
        String cameraId = chooseCamera();

        Camera2BasicFragment camera2Fragment =
                Camera2BasicFragment.newInstance(
                        new Camera2BasicFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeSelected(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                CameraActivity.this.onPreviewSizeSelected(size, rotation);
                            }
                        },
                        this,
                        getLayoutId(),
                        getWantedPreviewSize());

        camera2Fragment.setCamera(cameraId);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, camera2Fragment)
                .commit();
    }

    //request permission for camera and external storage
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(CameraActivity.this, "Camera and storage permission are needed to run this application", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    public void requestRender() {
        @SuppressLint("WrongViewCast") final OverlayView overlay = (OverlayView) findViewById(R.id.overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }

    protected abstract void onPreviewSizeSelected(final Size size, final int rotation);

    protected abstract void sceneProcessing();

    private List<ImageClassifier.Recognition> currentRecognitions;

    protected void toSpeech(List<ImageClassifier.Recognition> recognitions) {
        if (recognitions.isEmpty() || tts.isSpeaking()) {
            currentRecognitions = Collections.emptyList();
            return;
        }

        if (currentRecognitions != null) {

            // Ignore if current and new are same.
            if (currentRecognitions.equals(recognitions)) {
                return;
            }
            final Set<ImageClassifier.Recognition> intersection = new HashSet<>(recognitions);
            intersection.retainAll(currentRecognitions);

            // Ignore if new is sub set of the current
            if (intersection.equals(recognitions)) {
                return;
            }
        }

        currentRecognitions = recognitions;

        speak();
    }

    private void speak() {

        //w=960, h=720 (bcs rotated)
        //horizontal start from right to left
        final double rightStart = 0;
        final double rightFinish = previewHeight/3;
        final double middleStart=rightFinish;
        final double middleEnd=previewHeight*2/3;
        final double leftStart = middleEnd;
        final double leftFinish = previewHeight;


        final double previewArea = previewWidth * previewHeight;

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < currentRecognitions.size(); i++) {
            ImageClassifier.Recognition recognition = currentRecognitions.get(i);
            stringBuilder.append(recognition.getTitle());

            float start = recognition.getLocation().bottom;

            float end = recognition.getLocation().top;

            /*Toast.makeText(getApplicationContext(), "left="+recognition.getLocation().left + " top=" + recognition.getLocation().top
                    +"\nright="+recognition.getLocation().right+" bottm="+recognition.getLocation().bottom+"\npreview width="+previewWidth+" height"+previewHeight, Toast.LENGTH_SHORT).show();*/
            float center= (start+end)/2; //to calculate center point of the object

            double objectArea = recognition.getLocation().width() * recognition.getLocation().height();

            //if object occupy 70% of screen
            if (objectArea > previewArea*0.70) {
                stringBuilder.append(" in front of you ");
            } else {

                if (center >= leftStart && center <= leftFinish) {
                    stringBuilder.append(" on the left ");
                }
                else if (center >= rightStart && center <= rightFinish) {
                    stringBuilder.append(" on the right ");
                }
                else if (center> middleStart && center< middleEnd){
                    stringBuilder.append(" is straight forward");
                }
                else {
                    stringBuilder.append(" in front of you ");
                }
            }

            if (i + 1 < currentRecognitions.size()) {
                stringBuilder.append(" and ");
            }
        }
        tts.speak(stringBuilder.toString(), TextToSpeech.QUEUE_FLUSH, null);
        tts.playSilentUtterance(3000,TextToSpeech.QUEUE_ADD, null); // to add 3 seconds interval to prevent annoying (also counted as "is speaking")
    }

}
