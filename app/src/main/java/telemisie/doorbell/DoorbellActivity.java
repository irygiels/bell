package telemisie.doorbell;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;

public class DoorbellActivity extends Activity {

    private static final String TAG = "BUTTON";
    /*
         * Driver for the doorbell button;
         */
    private ButtonInputDriver mButton;

    /**
     * The GPIO pin to activate for button presses.
     */
    private final String BUTTON_GPIO_PIN = "GPIO_39";

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
    }

    /**
     * Override key event callbacks.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            // Doorbell rang!
            Log.d(TAG, "button pressed");
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            mButton.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
    }
}
