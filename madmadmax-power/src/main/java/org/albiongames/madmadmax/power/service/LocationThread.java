package org.albiongames.madmadmax.power.service;

import android.app.Service;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;

import org.albiongames.madmadmax.power.data_storage.Settings;
import org.albiongames.madmadmax.power.Tools;
import org.albiongames.madmadmax.power.data_storage.StorageEntry;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by dair on 31/03/16.
 */
public class LocationThread extends StatusThread implements LocationListener
{
    private PowerService mService = null;
    private long mLastUpdate = 0;
    private Looper mLooper = null;
    Timer mTimer = null;

    long mGpsTime = -100;
    long mGpsDistance = -100;

    int mLastLocationCount = 0;
    List<Location> mLastLocations = new LinkedList<>();
    List<Location> mLocations = new LinkedList<>();

    boolean mMockRun = true;

    final static String COMPONENT = "LocationThread";

    public LocationThread(PowerService service,Settings settings)
    {
        super(settings);

        mService = service;
    }

    @Override
    public void run()
    {
        if (getSettings().getLong(Settings.KEY_MOCK_DATA) == Settings.MOCK_DATA_PLAY)
            runFromMock();
        else
            runNormal();
    }

    void runNormal()
    {
        setStatus(STATUS_STARTING);
        Tools.log("LocationThread: start");
        Looper.prepare();
        mLooper = Looper.myLooper();
        mLocations.clear();

        onStart();

        setStatus(STATUS_ON);
        Looper.loop();

        setStatus(STATUS_STOPPING);
        onStop();
        mLooper = null;
        Tools.log("LocationThread: stop");
        setStatus(STATUS_OFF);
    }

    void runFromMock()
    {
        long sleepTime = 0;
        long lastLocationTime = 0;

        onStart();
        setStatus(STATUS_ON);

        FileInputStream stream = null;
        try
        {
            stream = new FileInputStream(mService.getFilesDir() + "/mock.dat");

            while (mMockRun)
            {
                int len = 0;
                int len1 = stream.read();
                int len2 = stream.read();

                len = (len2 << 8) + len1;

                if (len < 0)
                    break; // data finished?...

                byte[] data = new byte[len];
                stream.read(data);

                Parcel p = Parcel.obtain();
                p.unmarshall(data, 0, data.length);
                p.setDataPosition(0);

                Location l = Location.CREATOR.createFromParcel(p);

                long time = l.getTime();

                if (lastLocationTime == 0)
                {
                    sleepTime = 0;
                }
                else
                {
                    sleepTime = time - lastLocationTime;
                }

                if (sleepTime > 0)
                {
                    Tools.sleep(sleepTime);
                }

                l.setTime(System.currentTimeMillis());

                onLocationChanged(l);

                lastLocationTime = time;
            }
        }
        catch (IOException ex)
        {

        }
        finally
        {
            try
            {
                stream.close();
            }
            catch (IOException ex)
            {
            }
        }

        setStatus(STATUS_STOPPING);
        onStop();
        Tools.log("LocationThread: mock stop");
        setStatus(STATUS_OFF);

    }


//    @Override
//    protected void periodicTask()
//    {
//        if (mService == null || mService.getStatus() != PowerService.STATUS_ON)
//            return;
//
//        long now = System.currentTimeMillis();
//        if (now - mLastUpdate > Settings.getLong(Settings.KEY_GPS_IDLE_INTERVAL))
//        {
//            mService.getLogicStorage().put(new StorageEntry.Marker("ping"));
//            mLastUpdate = now;
//        }
//    }

    private synchronized void askRequests()
    {
        long newGpsTime = getSettings().getLong(Settings.KEY_MIN_GPS_TIME);
        long newGpsDistance = getSettings().getLong(Settings.KEY_MIN_GPS_DISTANCE);

        if (newGpsTime != mGpsTime || newGpsDistance != mGpsDistance)
        {
            LocationManager locationManager = (LocationManager) mService.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager == null)
                return;

            try
            {
                locationManager.removeUpdates(this);

                mGpsTime = newGpsTime;
                mGpsDistance = newGpsDistance;

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, mGpsTime, mGpsDistance, this);
            }
            catch (SecurityException ex)
            {

            }
        }

        long averageSpeedTime = getSettings().getLong(Settings.KEY_AVERAGE_SPEED_TIME);
        getSettings().setDouble(Settings.KEY_AVERAGE_SPEED, averageSpeed(averageSpeedTime));
    }

    protected void onStart()
    {
        if (!isMockPlay())
        {
            LocationManager locationManager = (LocationManager) mService.getSystemService(Context.LOCATION_SERVICE);


            if (locationManager == null)
                return;
        }

        mGpsTime = -100;
        mGpsDistance = -100;

        mLastUpdate = System.currentTimeMillis();
        mLocations.clear();
        
        getSettings().setDouble(Settings.KEY_AVERAGE_SPEED, 0.0);

        mService.getLogicStorage().put(new StorageEntry.MarkerStart());

        if (!isMockPlay())
        {
            askRequests();

            mTimer = new Timer("updates");
            mTimer.scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    askRequests();
                }
            }, 1000, 1000);
        }

        getSettings().setDouble(Settings.KEY_LAST_INSTANT_SPEED, 0.0);
        getSettings().setLong(Settings.KEY_LAST_GPS_UPDATE, 0);

        getSettings().setLong(Settings.KEY_LOCATION_THREAD_STATUS, STATUS_STARTING);
        getSettings().setLong(Settings.KEY_LOCATION_THREAD_LAST_QUALITY, -1);

        Tools.log("Location Thread started");

        if (getSettings().getLong(Settings.KEY_MOCK_DATA) == Settings.MOCK_DATA_RECORD)
        {
            try
            {
                FileWriter writer = new FileWriter(mService.getFilesDir() + "/mock.dat");
                writer.close();
            }
            catch (IOException ex)
            {

            }
        }
    }

    protected void onStop()
    {
        if (mTimer != null)
        {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }

        if (!isMockPlay())
        {
            LocationManager locationManager = (LocationManager) mService.getSystemService(Context.LOCATION_SERVICE);
            try
            {
                locationManager.removeUpdates(this);

                mService.getLogicStorage().put(new StorageEntry.MarkerStop());
            } catch (SecurityException exception)
            {
                // cry out loud
            }
            mService = null;
        }

        getSettings().setLong(Settings.KEY_LOCATION_THREAD_STATUS, STATUS_OFF);
        getSettings().setLong(Settings.KEY_LOCATION_THREAD_LAST_QUALITY, -1);
    }

    public void graciousStop()
    {
        if (mLooper != null)
            mLooper.quit();
        else
            mMockRun = false;
    }

    /// Location Listener methods
    @Override
    public void onLocationChanged(Location location)
    {
        // Called when a new location is found by the network location provider.
        if (location == null)
            return;

        long localTime = System.currentTimeMillis();
        location.setTime(localTime);

        if (getSettings().getLong(Settings.KEY_MOCK_DATA) == Settings.MOCK_DATA_RECORD)
        {
            Parcel p = Parcel.obtain();
            location.writeToParcel(p, 0);
            final byte[] b = p.marshall();
            p.recycle();

            try
            {
                FileOutputStream output = new FileOutputStream(mService.getFilesDir() + "/mock.dat", true);
                int len = b.length;
                output.write(len & 0xFF);
                output.write((len >> 8) & 0xFF);

                output.write(b);
                output.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }

        int satellites = -1;

        if (location.getExtras().containsKey("satellites"))
        {
            satellites = location.getExtras().getInt("satellites");

            if (satellites < getSettings().getLong(Settings.KEY_MIN_SATELLITES))
            {
                mService.dump(COMPONENT, "rejecting location because of satellites: " + location.toString());
                getSettings().setLong(Settings.KEY_LOCATION_THREAD_LAST_QUALITY, 0);
                return;
            }
        }

        float acc = -1;
        if (location.hasAccuracy())
        {
            acc = location.getAccuracy();

            if (acc > getSettings().getLong(Settings.KEY_MIN_ACCURACY))
            {
                mService.dump(COMPONENT, "rejecting location because of accuracy: " + location.toString());
                getSettings().setLong(Settings.KEY_LOCATION_THREAD_LAST_QUALITY, 0);
                return;
            }
        }

        getSettings().setLong(Settings.KEY_LOCATION_THREAD_LAST_QUALITY, 1);

        Tools.log("Got location: " + Double.toString(location.getLatitude()) + ", " + Double.toString(location.getLongitude()));

        double lat = location.getLatitude();
        double lon = location.getLongitude();
        float speed = 0;
        float speed1 = 0;

        getSettings().setDouble(Settings.KEY_LAST_INSTANT_SPEED, speed);
        getSettings().setLong(Settings.KEY_LAST_GPS_UPDATE, localTime);

        double localDistance = 0;
        long timeDiff = 0;

        Location lastLocation = getAverage();

        if (lastLocation != null)
        {
            Tools.log("Average last location is " + Double.toString(lastLocation.getLatitude()) + ", " + Double.toString(lastLocation.getLongitude()));

            localDistance = location.distanceTo(lastLocation);
            timeDiff = location.getTime() - lastLocation.getTime();
            if (location.hasSpeed())
            {
                speed = location.getSpeed();
                speed1 = (float)(localDistance / (timeDiff / 1000.0));
            }
            else
            {
                speed1 = speed = (float)(localDistance / (timeDiff / 1000.0));
            }

            Tools.log("localDistance is " + Double.toString(localDistance) + ", dt = " + Long.toString(timeDiff) + ", speed = " + Float.toString(speed) + ", " + Float.toString(speed1));

            speed = Math.min(speed, speed1);
        }
        else
        {
            location.setSpeed(0);
            lastLocation = location;
        }

        double speedFilter = Tools.kilometersPerHourToMetersPerSecond(getSettings().getDouble(Settings.KEY_GPS_FILTER_SPEED));
        double distanceFilter = getSettings().getDouble(Settings.KEY_GPS_FILTER_DISTANCE);
        if (localDistance < distanceFilter &&
                (lastLocation.getSpeed() < (float)speedFilter && speed < (float)speedFilter))
        {
            //skip it but gently
            speed = 0;
            location.setSpeed(0);
            localDistance = 0;

            mLocations.add(location);
        }
        else
        {
            // really "else". If the user starts driving or even walking then range someday will increase
            localDistance = mLocations.get(0).distanceTo(location);
            mLocations.clear();
            mLocations.add(location);

            Tools.log("accepting location");
        }

        StorageEntry.Location location1 = new StorageEntry.Location(localTime, lat, lon, acc, speed, localDistance, satellites);

        mService.getLogicStorage().put(location1);
        mLastUpdate = localTime;

        addLocation(location);

        long averageSpeedTime = getSettings().getLong(Settings.KEY_AVERAGE_SPEED_TIME);
        getSettings().setDouble(Settings.KEY_AVERAGE_SPEED, averageSpeed(averageSpeedTime));
    }

    void addLocation(Location location)
    {
        mLastLocations.add(location);

        long now = System.currentTimeMillis();
        long minTime = now - getSettings().getLong(Settings.KEY_AVERAGE_SPEED_TIME);
        boolean haveBorder = false;

        int i = mLastLocations.size() - 1;
        for (; i >= 0; --i)
        {
            Location l = mLastLocations.get(i);
            if (l.getTime() < minTime)
            {
                if (haveBorder)
                    break;
                else
                    haveBorder = true; // we should have ONE value less than our border
            }
        }

        while (i > 0)
        {
            mLastLocations.remove(0);
            --i;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
//        int satellites = extras.getInt("satellites");

//        Tools.log("LocationTread::onStatusChanged: "+ provider + ": " + Integer.toString(status) + ": satellites = " + Integer.toString(satellites));
    }

    @Override
    public void onProviderEnabled(String provider)
    {
        Tools.log("LocationThread::onProviderEnabled: " + provider);
        getSettings().setLong(Settings.KEY_LOCATION_THREAD_STATUS, STATUS_ON);
    }

    @Override
    public void onProviderDisabled(String provider)
    {
        Tools.log("LocationThread::onProviderDisabled: " + provider);
        getSettings().setLong(Settings.KEY_LOCATION_THREAD_STATUS, STATUS_OFF);
    }

    public synchronized float averageSpeed(long duration)
    {
        if (mLastLocations.isEmpty())
            return 0.0f;

        float speed = 0;
        long now = System.currentTimeMillis(); //mLastLocations.get(mLastLocations.size()-1).getTime();//
        long minTime = now - duration;

        LinkedList<Long> xs = new LinkedList<>();
        LinkedList<Float> ys = new LinkedList<>();
        boolean haveBorder = false;

        Location prevLocation = null;

        String dump = "";

        for (int i = mLastLocations.size() - 1; i >= 0; --i)
        {
            Location l = mLastLocations.get(i);

//            System.out.println("position: " + Integer.toString(i) + ", location: " + l.toString());

            long x = l.getTime() - minTime;
            float y = l.getSpeed();

            dump += Long.toString(x) + ", " + Float.toString(y) + "; ";

            if (prevLocation == null)
            {
                xs.add(duration);
                ys.add(y);
            }

            if (x < 0)
            {
//                System.out.println("x < minTime");
                if (haveBorder)
                {
//                    System.out.println("not processing");
                    break;
                }
                else
                {
//                    System.out.println("set as border");
                    haveBorder = true;
                }

                if (prevLocation == null)
                    return l.getSpeed();

                float df = prevLocation.getSpeed() - y;
                long dt = (prevLocation.getTime() - minTime) - x;

                long mt = -x;

                float s = y + (mt * df) / dt;

                x = 0;
                y = s;
            }

            xs.add(0, x);
            ys.add(0, y);

            prevLocation = l;
        }

//        Tools.log(dump);

        if (!haveBorder)
        {
//            System.out.println("adding x: " + Long.toString(minTime));
//            System.out.println("adding y: " + Float.toString(0.0f));

            xs.add(0, 0L);
            ys.add(0, 0.0f);
        }

        float totalSquare = 0.0f;
        for (int i = 1; i < xs.size(); ++i)
        {
            long x0 = xs.get(i - 1);
            float y0 = ys.get(i - 1);
            long x1 = xs.get(i);
            float y1 = ys.get(i);

//            System.out.println("iteration: " + Integer.toString(i) + ", x0: " + Long.toString(x0) + ", y0: " + Float.toString(y0) + ", x1: " + Long.toString(x1) + ", y1: " + Float.toString(y1));

            float yMin = Math.min(y0, y1);
            float yMax = Math.max(y0, y1);

            float square = yMin * (x1 - x0) + ((yMax - yMin) * (x1 - x0)) / 2;

            totalSquare += square;
        }

        speed = totalSquare / duration;

        return speed;
    }

    boolean isMockPlay()
    {
        return getSettings().getLong(Settings.KEY_MOCK_DATA) == Settings.MOCK_DATA_PLAY;
    }

    Location getAverage()
    {
        if (mLocations.isEmpty())
            return null;

        double lat = 0;
        double lon = 0;
        long time = 0;
        float speed = 0;

        for (Location l: mLocations)
        {
            lat += l.getLatitude();
            lon += l.getLongitude();
            speed += l.getSpeed();

            time = l.getTime();
        }

        lat /= mLocations.size();
        lon /= mLocations.size();
        speed /= mLocations.size();

        Location location = new Location("");
        location.setLatitude(lat);
        location.setLongitude(lon);
        location.setTime(time);
        location.setSpeed(speed);

        return location;
    }
}
