package telemisie.doorbell;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.cognito.CognitoSyncManager;
import com.amazonaws.mobileconnectors.s3.transferutility.*;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import com.amazonaws.mobile.client.AWSMobileClient;

import static com.amazonaws.regions.Regions.US_EAST_1;
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
    private static CognitoCachingCredentialsProvider sCredProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AWSMobileClient.getInstance().initialize(this).execute();
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
            final Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, null);
            //runOnUiThread(new Runnable() {
            //    @Override
            //    public void run() {
            //        imageView.setImageBitmap(bmp);
            //    }
            //});
            try {
                Map<String, Float> annotations = CloudVisionUtils.annotateImage(imageBytes);
                saveToInternalStorage(bmp);
                beginUpload();
                Log.d(TAG, "cloud vision annotations:" + annotations);
            } catch (IOException e) {
                Log.e(TAG, "Cloud Vison API error: ", e);
            }
        }

    }
    private void beginUpload() {

        final File file = new File(getFilesDir().getAbsolutePath()+"/img.png");
        TransferUtility transferUtility =
                TransferUtility.builder()
                        .context(getApplicationContext())
                        .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                        .s3Client(new AmazonS3Client(getCredProvider(getApplicationContext())))
                        .build();

        TransferObserver uploadObserver =
                transferUtility.upload(
                       "telemisie.doorbell",
                        file);

        uploadObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(int id, TransferState state) {
                if (TransferState.COMPLETED == state) {
                    // Handle a completed upload.
                    Log.d(TAG, "Transfer completed");
                }
            }

            @Override
            public void onProgressChanged(
                    int id, long bytesCurrent, long bytesTotal) {
                float percentDonef = ((float)bytesCurrent/(float)bytesTotal) * 100;
                int percentDone = (int)percentDonef;

                Log.d("MainActivity", "   ID:" + id + "   bytesCurrent: " + bytesCurrent + "   bytesTotal: " + bytesTotal + " " + percentDone + "%");
            }

            @Override
            public void onError(int id, Exception ex) {
                // Handle errors
            }

        });
        
        //tu jest moj kod do pobierania
        /*
        final File file2 = new File(getFilesDir().getAbsolutePath()+"/aaa.png");
        TransferObserver downloadObserver =
                transferUtility.download(
                        "telemisie.doorbell",
                        file2);
        downloadObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(int id, TransferState state) {
                if (TransferState.COMPLETED == state) {
                    Log.d(TAG, "Download completed");
                    //imageView.setImageBitmap(null);
                    imageView.setImageBitmap(BitmapFactory.decodeFile(file2.getPath()));
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                float percentDonef = ((float)bytesCurrent/(float)bytesTotal) * 100;
                int percentDone = (int)percentDonef;

                Log.d("MainActivity", "   ID:" + id + "   bytesCurrent: " + bytesCurrent + "   bytesTotal: " + bytesTotal + " " + percentDone + "%");
            }

            @Override
            public void onError(int id, Exception ex) {
                // Handle errors
            }

        });*/
    }
        /*
         * Note that usually we set the transfer listener after initializing the
         * transfer. However it isn't required in this sample app. The flow is
         * click upload button -> start an activity for image selection
         * startActivityForResult -> onActivityResult -> beginUpload -> onResume
         * -> set listeners to in progress transfers.
         */
        // observer.setTransferListener(new UploadListener());




        private void saveToInternalStorage(Bitmap bmp) {
            File file = new File(getFilesDir(), "img.png");
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    private static CognitoCachingCredentialsProvider getCredProvider(Context context) {
        if (sCredProvider == null) {
            sCredProvider = new CognitoCachingCredentialsProvider(
                    context.getApplicationContext(),
                    "us-east-1:32afefa3-74d2-44fc-ae99-15557a33f418",
                    US_EAST_1);
        }
        return sCredProvider;
    }

}
