package org.albiongames.madmadmax.power.service;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.albiongames.madmadmax.power.data_storage.Settings;
import org.albiongames.madmadmax.power.Tools;
import org.albiongames.madmadmax.power.data_storage.FuelQuality;
import org.albiongames.madmadmax.power.data_storage.Storage;
import org.albiongames.madmadmax.power.data_storage.StorageEntry;
import org.albiongames.madmadmax.power.data_storage.Upgrades;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class PowerService extends Service
{
    static PowerService mInstance = null;
    static final String COMPONENT = "Service";

    public static volatile boolean isServiceRunning = false; // It is bad, i know. But it is fast.

    public static PowerService instance()
    {
        return mInstance;
    }

    public static final int STATUS_OFF = 0;
    public static final int STATUS_ON = 1;
    public static final int STATUS_STARTING = 2;
    public static final int STATUS_CLOSING = 3;

    private int mStatus = STATUS_OFF;

    public int getStatus()
    {
        return mStatus;
    }

    private final LocalBinder mBinder = new LocalBinder();

    BluetoothThread mBluetoothThread = null;
    NetworkingThread mNetworkingThread = null;
    LocationThread mLocationThread = null;
    LogicThread mLogicThread = null;

    List<StorageEntry.Base> mPositions = new LinkedList<>();

    Activity mActivity = null;

    Storage mLogicStorage = null;
    Storage mNetworkStorage = null;
    Storage mLocationStorage = null;
    Storage mInfoStorage = null;

    Error mError = null;

    long mStartTime = 0;

    private Settings settings;

    public Settings getSettings() {
        assert settings!=null;
        return settings;
    }


    public class LocalBinder extends Binder
    {
        PowerService getService()
        {
            // Return this instance of LocalService so clients can call public methods
            return PowerService.this;
        }
    }

    public PowerService()
    {
        Tools.log("Service: ctor");
    }

    void setActivity(Activity activity)
    {
        mActivity = activity;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    Storage openStorage(final String name){
        Storage ret = null;

        try
        {
            ret = new Storage(name);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            Tools.log("Error opening NEW " + name + ": " + ex.toString());
        }

        if (ret == null)
        {
            try
            {
                deleteRecursive(new File(name));
                ret = new Storage(name);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                Tools.log("Error opening NEW " + name + ": " + ex.toString());
            }
        }

        return ret;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        settings = new Settings(this);

        Tools.log("Service: onStartCommand");
        mStartTime = System.currentTimeMillis();

        Upgrades.setPath(getFilesDir().getPath());
        FuelQuality.setPath(getFilesDir().getPath());

        if (mStatus == STATUS_OFF)
        {
            mInstance = this;

            mStatus = STATUS_STARTING;

            mLogicStorage = openStorage(getFilesDir() + "/logic");
            mNetworkStorage = openStorage(getFilesDir() + "/network");
            mLocationStorage = openStorage(getFilesDir() + "/location");
            mInfoStorage = openStorage(getFilesDir() + "/info");


            if (mLogicStorage != null &&
                mNetworkStorage != null &&
                mLocationStorage != null &&
                mInfoStorage != null) {

                dump(COMPONENT, "start");

                mLocationThread = new LocationThread(this, settings);
                mBluetoothThread = new BluetoothThread(this, settings);
                mNetworkingThread = new NetworkingThread(this, settings);
                mLogicThread = new LogicThread(this, settings);

                mLocationThread.start();
                mBluetoothThread.start();
                mNetworkingThread.start();
                mLogicThread.start();

                mStatus = STATUS_ON;
            }
            else
            {
                mStatus = STATUS_OFF;
                stopSelf();
            }
        }
        isServiceRunning = true;
        return START_STICKY;
    }

    public long getStartTime()
    {
        return mStartTime;
    }

    @Override
    public void onDestroy()
    {
        isServiceRunning = false;

        Tools.log("Service: onDestroy");
        mInstance = null;
        super.onDestroy();
    }

    public Error getError()
    {
        return mError;
    }

    public Storage getLogicStorage()
    {
        return mLogicStorage;
    }

    public Storage getNetworkStorage()
    {
        return mNetworkStorage;
    }

    public Storage getLocationStorage()
    {
        return mLocationStorage;
    }

    public Storage getInfoStorage()
    {
        return mInfoStorage;
    }

    protected void iGraciousStop()
    {
        Tools.log("Service iGraciousStop");
        dump(COMPONENT, "gracious stop called");
        mStatus = STATUS_CLOSING;

        mNetworkingThread.graciousStop();
        mBluetoothThread.graciousStop();
        mLogicThread.graciousStop();
        mLocationThread.graciousStop();

        while (mNetworkingThread.getStatus() != StatusThread.STATUS_OFF)
        {
            Tools.sleep(100);
        }

        if (mDumpBundle != null)
            mNetworkStorage.put(mDumpBundle);

        mStatus = STATUS_OFF;
        stopSelf();
    }

    public static void graciousStop()
    {
        Tools.log("Service graciousStop");
        if (mInstance == null)
            return;

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                mInstance.iGraciousStop();
            }
        }).start();
//
//        new AsyncTask()
//        {
//            @Override
//            protected Object doInBackground(Object[] params)
//            {
//                return null;
//            }
//        }.execute();
    }

//    public List<StorageEntry.Base> getPositions()
//    {
//        return mPositions;
//    }

    public int getStatusThreadStatus(StatusThread t)
    {
        if (t == null)
            return StatusThread.STATUS_OFF;
        return t.getStatus();
    }

    public int getNetworkThreadStatus()
    {
        return getStatusThreadStatus(mNetworkingThread);
    }

    public int getLocationThreadStatus()
    {
        return getStatusThreadStatus(mLocationThread);
    }

    public int getLogicThreadStatus()
    {
        return getStatusThreadStatus(mLogicThread);
    }

    public BaseThread getBluetoothThread()
    {
        return mBluetoothThread;
    }

    StorageEntry.Bundle mDumpBundle = null;


    public void dump(final String component, final String message)
    {
        if (getSettings().getLong(Settings.KEY_EXTRA_DEBUG) != 1)
            return;

        String string = component + ": " + message;
        synchronized (this)
        {
            try {
                String timeString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
                FileWriter writer = new FileWriter(getFilesDir() + "/dump.txt", true);
                writer.write(timeString + " " + string);
                writer.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        StorageEntry.Dump entry = new StorageEntry.Dump(component + ": " + message);

        if (mDumpBundle == null)
            mDumpBundle = new StorageEntry.Bundle();

        mDumpBundle.add(entry);

        if (mDumpBundle.size() >= 20)
        {
            mNetworkStorage.put(mDumpBundle);
            mDumpBundle = null;
        }
    }

    public void deleteRecursive(File fileOrDirectory) throws IOException
    {
        if (fileOrDirectory == null)
            return;

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        if (!fileOrDirectory.delete())
            throw new IOException();
    }
}
