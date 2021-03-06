package org.albiongames.madmadmax.power;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.LocationManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.albiongames.madmadmax.power.activity.AboutActivity;
import org.albiongames.madmadmax.power.activity.BluetoothDeviceActivity;
import org.albiongames.madmadmax.power.activity.ServiceStatusActivity;
import org.albiongames.madmadmax.power.activity.SettingsActivity;
import org.albiongames.madmadmax.power.data_storage.FuelQuality;
import org.albiongames.madmadmax.power.data_storage.Settings;
import org.albiongames.madmadmax.power.data_storage.Upgrades;
import org.albiongames.madmadmax.power.service.PowerService;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;

/**
 * Created by dair on 28/05/16.
 */
public class Tools {
    public static void messageBox(Activity activity, int id) {
        messageBox(activity, id, null);
    }

    public static void messageBox(Activity activity, int id, Runnable runnable) {
        String msg = activity.getString(id);
        messageBox(activity, msg, runnable);
    }

    public static void messageBox(final Activity activity, final String message) {
        messageBox(activity, message, null);
    }

    public static void messageBox(final Activity activity, final String message, final Runnable runnable) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dlgAlert = new AlertDialog.Builder(activity).create();
                dlgAlert.setMessage(message);
                dlgAlert.setTitle(R.string.app_name);
                dlgAlert.setButton(AlertDialog.BUTTON_POSITIVE,
                        "OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                if (runnable != null) {
                                    runnable.run();
                                }
                            }
                        });
                dlgAlert.setCancelable(true);
                dlgAlert.show();
            }
        });
    }

    public static void log(String message) {
        Log.e("MadMax", message);
    }

    public static double clamp(double value, double min, double max) {
        if (value < min)
            value = min;

        if (value > max)
            value = max;

        return value;
    }

    public static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
        }
    }

    public static double kilometersPerHourToMetersPerSecond(double kmh) {
        return kmh / 3.6;
    }

    public static double metersPerSecondToKilometersPerHour(double mps) {
        return mps * 3.6;
    }

    public static boolean isMyServiceRunning(Activity activity) {
//        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
//            if (PowerService.class.getName().equals(service.service.getClassName())) {
//                return true;
//            }
//        }
//        return false;
        // Sory, but no!

        return PowerService.isServiceRunning;

    }

    public static boolean processMenu(MenuItem item, Activity activity) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_bluetooth:
                intent = new Intent(activity, BluetoothDeviceActivity.class);
                activity.startActivity(intent);
                return true;
            case R.id.menu_status:
                intent = new Intent(activity, ServiceStatusActivity.class);
                activity.startActivity(intent);
                return true;
            case R.id.menu_settings:
                intent = new Intent(activity, SettingsActivity.class);
                activity.startActivity(intent);
                return true;
            case R.id.menu_about:
                intent = new Intent(activity, AboutActivity.class);
                activity.startActivity(intent);
                return true;
        }

        return false;
    }

    static int[] splitColors(int c) {
        int[] ret = new int[4];
        ret[0] = (c & 0xFF000000) >> 24;
        ret[1] = (c & 0x00FF0000) >> 16;
        ret[2] = (c & 0x0000FF00) >> 8;
        ret[3] = (c & 0x000000FF);
        return ret;
    }

    public static int colorMiddle(int c1, int c2, double ratio) {
        int[] p1 = splitColors(c1);
        int[] p2 = splitColors(c2);
        int[] p = new int[4];

        for (int i = 0; i < 4; ++i) {
            p[i] = p1[i] + (int) Math.round((p2[i] - p1[i]) * ratio);
        }

        return Color.argb(p[0], p[1], p[2], p[3]);
    }

    public static void showKeyboard(Activity activity)
    {
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,InputMethodManager.HIDE_IMPLICIT_ONLY);
    }


    public static void hideKeyboard(Activity activity)
    {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static boolean checkServerStart(Activity activity)
    {
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
        {
            messageBox(activity, R.string.tools_check_gps_unavailable);
            return false;
        }

        if (locationManager.isProviderEnabled(android.provider.Settings.Secure.LOCATION_MODE))
        {
            messageBox(activity, R.string.tools_check_gps_disabled);
            return false;
        }

        BluetoothSPP bt = new BluetoothSPP(activity);
        if (!bt.isBluetoothAvailable())
        {
            messageBox(activity, R.string.tools_check_bluetooth_unavailable);
            return false;
        }

        if (!bt.isBluetoothEnabled())
        {
            messageBox(activity, R.string.tools_check_bluetooth_disabled);
            // and it is fine
        }

        return true;
    }

    public static double getAverageSpeed(Settings settings)
    {
        double real = settings.getDouble(Settings.KEY_AVERAGE_SPEED);
//        double mock = Tools.kilometersPerHourToMetersPerSecond(Settings.getDouble(Settings.KEY_MOCK_AVERAGE_SPEED));
//        if (mock < 0.0)
        return real;
//        return mock;
    }

    public static boolean isCarMoving(Settings settings)
    {
        double mps = getAverageSpeed(settings);
        double kmh = metersPerSecondToKilometersPerHour(mps);

        return kmh > 1.5;
    }

    public static String convertStreamToString(InputStream is) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static JSONObject readFileToJson(final String filename)
    {
        JSONObject ret = null;
        try
        {
            FileInputStream fin = new FileInputStream(filename);
            String jsonString = convertStreamToString(fin);
            fin.close();
            ret = new JSONObject(jsonString);
        }
        catch (IOException ex)
        {

        }
        catch (JSONException ex)
        {

        }

        return ret;
    }

    public static void writeJsonToFile(final String filename, JSONObject object)
    {
        FileOutputStream outputStream;

        try
        {
            outputStream = new FileOutputStream(filename);
            outputStream.write(object.toString().getBytes());
            outputStream.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }


    public static void showTimer(final Activity activity, final long time, int stringId, final RunnableArg periodic, final Runnable runnable)
    {
        final TextView timerView = new TextView(activity);
        timerView.setBackgroundColor(Color.BLACK);
        timerView.setTextColor(Color.WHITE);
        timerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        timerView.setTextSize(100);
        timerView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);

        final FrameLayout layout = (FrameLayout)activity.findViewById(android.R.id.content);
        layout.addView(timerView);



        final TextView commentView = new TextView(activity);
        commentView.setBackgroundColor(Color.BLACK);
        commentView.setTextColor(Color.WHITE);
        commentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        commentView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        commentView.setText(stringId);
        commentView.setTextSize(24);
        layout.addView(commentView);

        final long now = System.currentTimeMillis();
        final long finishTime = now + time;

        new Thread(new Runnable()
        {
            long mFinishTime = finishTime;
            @Override
            public void run()
            {
                long lastSecondUpdate = 0;
                while (true)
                {
                    long remain = mFinishTime - System.currentTimeMillis();
                    if (remain <= 0)
                        break;

                    long secondBorder = 1000* (remain / 1000);

                    if (secondBorder != lastSecondUpdate)
                    {
                        lastSecondUpdate = secondBorder;
                        double percent = 1.0 - ((double)remain / (double)time);

                        periodic.setArgs(new Double(percent));
                        activity.runOnUiThread(periodic);
                    }

                    final String remainString = remain < 60000 ? String.format("%d.%03d", remain / 1000, remain % 1000) : String.format("%d:%02d", remain / 1000 / 60, (remain / 1000) % 60);

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            timerView.setText(remainString);
                        }
                    });
                    Tools.sleep(30);
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layout.removeView(timerView);
                    }
                });

                activity.runOnUiThread(runnable);
            }
        }).start();
    }

    public static double getCurrentRedZone(Settings settings)
    {
        double ret = 0.0;
        String key = null;

        if (settings.getLong(Settings.KEY_SIEGE_STATE) == Settings.SIEGE_STATE_OFF)
        {
            switch ((int) settings.getLong(Settings.KEY_CAR_STATE))
            {
            case Settings.CAR_STATE_OK:
                key = Settings.KEY_RED_ZONE;
                break;
            case Settings.CAR_STATE_MALFUNCTION_1:
                key = Settings.KEY_MALFUNCTION1_RED_ZONE;
                break;
            case Settings.CAR_STATE_MALFUNCTION_2:
                ret = 0.0;
                break;
            }
        }

        if (key == null)
            return 0.0;

        ret = settings.getDouble(key);
        ret = Upgrades.upgradeValue(key, ret);
        ret = FuelQuality.upgradeValue(key, ret);

        return ret;
    }

    public static int getDamageForCode(int code, Settings settings) {
        String json = settings.getString(Settings.KEY_DAMAGE_CODE);
        int ret = 1;
        if (json != null) {
            try {
                JSONObject object = new JSONObject(json);
                String codeString = Integer.toString(code);
                ret = object.getInt(codeString);
            } catch (JSONException ex) {

            }
        }

        return ret;
    }
}
