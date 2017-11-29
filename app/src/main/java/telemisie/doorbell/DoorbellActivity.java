package telemisie.doorbell;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import static telemisie.doorbell.CloudVisionUtils.annotateImage;

public class DoorbellActivity extends Activity {


    private static final String TAG = "BUTTON";
    /*
         * Driver for the doorbell button;
         */
    private ButtonInputDriver mButtonInputDriver;

    /**
     * The GPIO pin to activate for button presses.
     */
    private final String BUTTON_GPIO_PIN = "GPIO_174";

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;

    /**
     * A Handler for running tasks in the background.
     */
    private Handler mCameraHandler;
    private DoorbellCamera mCamera;
    ImageView imageView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Doorbell Activity created.");
        setContentView(R.layout.activity_doorbell);
        imageView = findViewById(R.id.imageView);
        try {
            mButtonInputDriver = new ButtonInputDriver(
                    BUTTON_GPIO_PIN,
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);
            mButtonInputDriver.register();
            Log.d(TAG, mButtonInputDriver.toString());

        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }

        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);
    }

    /**
     * Override key event callbacks.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Doorbell rang!
            Log.d(TAG, "button pressed");
            mCamera.takePicture();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            mButtonInputDriver.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
        mCameraThread.quitSafely();
        mCamera.shutDown();
    }

    // Callback to receive captured camera image data
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Get the raw image bytes
                        Image image = reader.acquireLatestImage();
                        ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                        final byte[] imageBytes = new byte[imageBuf.remaining()];
                        imageBuf.get(imageBytes);
                        image.close();
                        onPictureTaken(imageBytes);

                }
            };



    private void onPictureTaken(final byte[] imageBytes) {
        if (imageBytes != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, null));
                }
            });
            try {
                // Process the image using Cloud Vision
                Map<String, Float> annotations = annotateImage(imageBytes);
                Log.d(TAG, "cloud vision annotations:" + annotations);
            } catch (IOException e) {
                Log.e(TAG, "Cloud Vison API error: ", e);
            }
        }
    }


}
