package e.kerry.kerobotnrf;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class JoystickActivity extends Activity  implements JoystickView.JoystickListener
{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JoystickView joystick = new JoystickView(this);
        setContentView(R.layout.joystick_view);
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
    }
}
