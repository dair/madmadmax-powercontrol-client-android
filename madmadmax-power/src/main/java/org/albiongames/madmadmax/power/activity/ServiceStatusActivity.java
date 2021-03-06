package org.albiongames.madmadmax.power.activity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.albiongames.madmadmax.power.data_storage.Settings;
import org.albiongames.madmadmax.power.network.NetworkTools;
import org.albiongames.madmadmax.power.service.NetworkingThread;
import org.albiongames.madmadmax.power.service.PowerService;
import org.albiongames.madmadmax.power.R;
import org.albiongames.madmadmax.power.service.StatusThread;
import org.albiongames.madmadmax.power.Tools;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceStatusActivity extends AppCompatActivity
{
    long mLastTimeChanged = 0;

    ScheduledThreadPoolExecutor mExecutor = null;

    private Settings settings;

    public Settings getSettings() {
        assert settings!=null;
        return settings;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_status);

        settings = new Settings(this);

        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ServiceStatusActivity.this.clickOnOff();
            }
        });

        Button mockButton = (Button)findViewById(R.id.mockButton);
        mockButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                long now = getSettings().getLong(Settings.KEY_MOCK_DATA);
                if (now == Settings.MOCK_DATA_PLAY)
                {
                    now = Settings.MOCK_DATA_OFF;
                }
                else
                    ++now;

                getSettings().setLong(Settings.KEY_MOCK_DATA, now);
            }
        });
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Button mockButton = (Button) findViewById(R.id.mockButton);
        if (getSettings().getLong(Settings.KEY_MOCK_AVAILABLE) == 1)
        {
            mockButton.setVisibility(View.VISIBLE);
        }
        else
        {
            mockButton.setVisibility(View.VISIBLE);
        }

        mExecutor = new ScheduledThreadPoolExecutor(1);
        mExecutor.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable()
                              {
                                  @Override
                                  public void run()
                                  {
                                      updateServiceState();
                                      updateNetworkState();
                                      updateText();
                                      updateThreadsState();
                                      updateCarState();
                                      updateAverageSpeed();
                                      updateDistance();
                                      updateQueueSizes();
                                      updateTraffic();
                                      updateHitPoints();
                                  }
                              }
                );
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onPause()
    {
//        unbindService();
        mExecutor.shutdownNow();
        mExecutor = null;
        super.onPause();
    }

    protected void clickOnOff()
    {
        long time = System.currentTimeMillis();
        if (time - mLastTimeChanged < 2500)
            return;

        mLastTimeChanged = time;

        if (isMyServiceRunning(PowerService.class))
        {
            // stop service
            PowerService.graciousStop();
        }
        else
        {
            startService(new Intent(this, PowerService.class));
        }
    }

    void updateServiceState()
    {
        Button mockButton = (Button) findViewById(R.id.mockButton);

        if (Tools.isMyServiceRunning(this))
        {
            mockButton.setEnabled(false);
        }
        else
        {
            mockButton.setEnabled(true);
        }
    }

    protected void updateText()
    {
        Button button = (Button)findViewById(R.id.button);
        TextView label = (TextView)findViewById(R.id.serviceStatusView);
        if (isMyServiceRunning(PowerService.class))
        {
            label.setText("ON");
            button.setText("Turn service OFF");
        }
        else
        {
            label.setText("OFF");
            button.setText("Turn service ON");
        }

        button = (Button)findViewById(R.id.mockButton);
        switch ((int) getSettings().getLong(Settings.KEY_MOCK_DATA))
        {
            case Settings.MOCK_DATA_OFF:
                button.setText("Mock OFF");
                break;
            case Settings.MOCK_DATA_RECORD:
                button.setText("Mock RECORD");
                break;
            case Settings.MOCK_DATA_PLAY:
                button.setText("Mock PLAY");
                break;
        }

    }

    void applyStatusToTextView(int status, TextView textView)
    {
        if (textView == null)
            return;

        int color = Color.GRAY;
        switch (status)
        {
            case StatusThread.STATUS_OFF:
                color = Color.RED;
                break;
            case StatusThread.STATUS_ON:
                color = Color.GREEN;
                break;
            case StatusThread.STATUS_STARTING:
                color = Color.BLUE;
                break;
            case StatusThread.STATUS_STOPPING:
                color = Color.YELLOW;
                break;
        }
        textView.setBackgroundColor(color);
    }

    protected void updateThreadsState()
    {
        int logicStatus = StatusThread.STATUS_OFF;
        int locationStatus = StatusThread.STATUS_OFF;
        int networkStatus = StatusThread.STATUS_OFF;

        if (PowerService.instance() != null)
        {
            logicStatus = PowerService.instance().getLogicThreadStatus();
            locationStatus = PowerService.instance().getLocationThreadStatus();
            networkStatus = PowerService.instance().getNetworkThreadStatus();
        }

        TextView logicText = (TextView)findViewById(R.id.logicThreadStatusText);
        applyStatusToTextView(logicStatus, logicText);
        TextView locationText = (TextView)findViewById(R.id.locationThreadStatusText);
        applyStatusToTextView(locationStatus, locationText);
        TextView networkingText = (TextView)findViewById(R.id.networkThreadStatusText);
        applyStatusToTextView(networkStatus, networkingText);
    }

    protected void updateNetworkState()
    {
        long success = getSettings().getLong(Settings.KEY_LATEST_SUCCESS_CONNECTION);
        long fail = getSettings().getLong(Settings.KEY_LATEST_FAILED_CONNECTION);

        TextView text = (TextView)findViewById(R.id.networkStatusText);
        if (text == null)
            return;

        if (success > fail)
        {
            text.setText("OK");
            if (System.currentTimeMillis() - success < 2* getSettings().getLong(Settings.KEY_GPS_IDLE_INTERVAL))
            {
                text.setTextColor(Color.GREEN);
            }
            else
            {
                text.setTextColor(Color.DKGRAY);
            }
        }
        else
        {
            text.setText("FAIL");
            text.setTextColor(Color.RED);
        }
    }

    protected void updateCarState()
    {
        TextView textView = (TextView)findViewById(R.id.carStatusTextLabel);
        if (textView == null)
            return;

        String text = "";
        int state = (int) getSettings().getLong(Settings.KEY_CAR_STATE);
        switch (state)
        {
            case Settings.CAR_STATE_OK:
                text = "Car state: OK";
                break;
            case Settings.CAR_STATE_MALFUNCTION_1:
                text = "Car break 1";
                break;
            case Settings.CAR_STATE_MALFUNCTION_2:
                text = "Car break 2";
                break;
        }

        textView.setText(text);
    }

    protected void updateAverageSpeed()
    {
        TextView textView = (TextView)findViewById(R.id.averageSpeedTextView);
        if (textView == null)
            return;

        double averageSpeed = Tools.getAverageSpeed(getSettings());
        double averageSpeedKmH = Tools.metersPerSecondToKilometersPerHour(averageSpeed);

        textView.setText(Double.toString(averageSpeedKmH));


        double lastSpeed = getSettings().getDouble(Settings.KEY_LAST_INSTANT_SPEED);
        double lastSpeedKmH = Tools.metersPerSecondToKilometersPerHour(lastSpeed);
        TextView lastSpeedTextView = (TextView)findViewById(R.id.lastInstantSpeedTextView);
        if (lastSpeedTextView != null)
        {
            lastSpeedTextView.setText(Double.toString(lastSpeedKmH));
        }

        TextView lastGpsTextView = (TextView)findViewById(R.id.lastGpsSignalTextView);
        long lastTime = getSettings().getLong(Settings.KEY_LAST_GPS_UPDATE);
        if (lastTime > 0)
        {
            long now = System.currentTimeMillis();
            long diff = now - lastTime;

            double diffS = (double) diff / 1000.0;
            lastGpsTextView.setText(Double.toString(diffS));
        }
        else
        {
            lastGpsTextView.setText("---");
        }
    }

    protected void updateDistance()
    {
        TextView textView = (TextView)findViewById(R.id.distanceTextView);
        if (textView == null)
            return;

        double distance = getSettings().getDouble(Settings.KEY_TRACK_DISTANCE);
        textView.setText(Double.toString(distance));
    }

    protected void updateQueueSizes()
    {
        if (Tools.isMyServiceRunning(this))
        {
            int nSize = -1;
            int lSize = -1;
            if (PowerService.instance() != null) {
                if (PowerService.instance().getNetworkStorage() != null)
                {
                    nSize = PowerService.instance().getNetworkStorage().size();
                }

                if (PowerService.instance().getLogicStorage() != null)
                {
                    lSize = PowerService.instance().getLogicStorage().size();
                }
            }

            String text = Integer.toString(lSize) + " / " + Integer.toString(nSize);
            TextView n = (TextView)findViewById(R.id.netQueueTextView);
            n.setText(text);
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (serviceClass.getName().equals(service.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        boolean ret = Tools.processMenu(item, this);

        if (ret)
            return ret;
        return super.onOptionsItemSelected(item);
    }

    void updateTraffic()
    {
        long rx = getSettings().getLong(Settings.KEY_RX_BYTES);
        long tx = getSettings().getLong(Settings.KEY_TX_BYTES);
        String value = Long.toString(rx) + "/" + Long.toString(tx) + " (" + Long.toString(rx + tx) + ")";
        TextView textView = (TextView)findViewById(R.id.trafficTextView);
        textView.setText(value);

        TextView ratio = (TextView)findViewById(R.id.ratioTextView);
        rx = NetworkTools.getRx();
        tx = NetworkTools.getTx();
        value = Long.toString(rx) + "/" + Long.toString(tx) + " (" + Long.toString(rx + tx) + ")";
        ratio.setText(value);

    }

    void updateHitPoints()
    {
        TextView hpView = (TextView)findViewById(R.id.hpTextView);
        double hp = getSettings().getDouble(Settings.KEY_HITPOINTS);
        hpView.setText(Double.toString(hp));
    }

}
