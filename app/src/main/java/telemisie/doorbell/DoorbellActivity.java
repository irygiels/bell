package telemisie.doorbell;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;

import static android.content.ContentValues.TAG;

public class DoorbellActivity extends Activity {

    /*
     * Driver for the doorbell button;
     */
    private ButtonInputDriver mButton;

    /**
     * The GPIO pin to activate for button presses.
     */
    private final String BUTTON_GPIO_PIN = ""; //tutaj wpisac numer pinu GPIO

    /**
     * A Handler for running tasks in the background.
     */
    private Handler mCameraHandler;
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Doorbell Activity created.");

        // Initialize the doorbell button driver
        try {
            mButton = new ButtonInputDriver(BUTTON_GPIO_PIN,
                    Button.LogicState.PRESSED_WHEN_LOW, KeyEvent.KEYCODE_ENTER); // The keycode to send
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }

        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    /**
     * Override key event callbacks.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            // Doorbell rang!
            Log.d(TAG, "button pressed");
            Context cont = getApplicationContext();
            CharSequence text = "Button is pressed!";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(cont, text, duration);
            toast.show();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraThread.quitSafely();
        try {
            mButton.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
    }
}
