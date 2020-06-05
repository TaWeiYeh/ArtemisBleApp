package e.kerry.kerobotnrf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

public class JoystickActivity extends Activity  implements JoystickView.JoystickListener
{
    private UartService mService = null;
    public static final String TAG = "nRFUART";
    boolean mBound = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JoystickView joystick = new JoystickView(this);
        setContentView(R.layout.joystick_view);

        // Bind UartService
        service_init();
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        TextView textViewLeft = findViewById(R.id.textViewLeft);
        TextView textViewRight = findViewById(R.id.textViewRight);
        String result;

        switch (id)
        {
            case R.id.joystickRight:
                Log.d("Right Joystick", "X percent: " + xPercent + " Y percent: " + yPercent);
                result = "Right Joystick:" + " X: " + xPercent + " Y: " + yPercent;
                textViewRight.setText(result);
                break;
            case R.id.joystickLeft:
                Log.d("Left Joystick", "X percent: " + xPercent + " Y percent: " + yPercent);
                result = "Left Joystick:" + " X: " + xPercent + " Y: " + yPercent;
                textViewLeft.setText(result);
                break;
        }

        if (mBound) {
            try {
                String message = String.format(Locale.getDefault(), "%1.3f", xPercent);
                byte [] value = message.getBytes("UTF-8");
                mService.writeRXCharacteristic(value);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    // Extending the Binder class
    // https://developer.android.com/guide/components/bound-services
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
            mBound = false;
        }
    };
}
