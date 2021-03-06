package org.albiongames.madmadmax.power.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.albiongames.madmadmax.power.RunnableArg;
import org.albiongames.madmadmax.power.data_storage.Settings;
import org.albiongames.madmadmax.power.network.NetworkTools;
import org.albiongames.madmadmax.power.service.NetworkingThread;
import org.albiongames.madmadmax.power.R;
import org.albiongames.madmadmax.power.Tools;
import org.albiongames.madmadmax.power.data_storage.Upgrades;
import org.json.JSONException;
import org.json.JSONObject;

public class RepairLoadActivity extends Activity
{
    float mMultiplier = 0;
    boolean mTimerActive = false;

    private Settings settings;

    public Settings getSettings() {
        assert settings!=null;
        return settings;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repair_load);

        settings = new Settings(this);

        Button sendButton = (Button)findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                handleSend();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();

        setEditText(R.id.repairCodeText);
    }

    void setEditText(int id)
    {
        EditText text = (EditText)findViewById(id);
        text.requestFocus();
        Tools.showKeyboard(this);
    }

    void handleSend()
    {
        EditText text = (EditText)findViewById(R.id.repairCodeText);
        String code = text.getText().toString().trim();

        if (code.length() != 8)
        {
            Tools.messageBox(this, R.string.repair_load_mistype_code);
            return;
        }

        EditText player = (EditText)findViewById(R.id.playerCodeText);
        String playerCode = player.getText().toString().trim();
        if (playerCode.length() != 8)
        {
            Tools.messageBox(this, R.string.repair_load_mistype_player_code);
            return;
        }

        mMultiplier = parsePlayerCode(playerCode);
        if (mMultiplier < 0)
        {
            Tools.messageBox(this, R.string.repair_load_mistype_player_code);
            return;
        }

        sendRepairCode(code);
    }

    JSONObject mReturnObject = null;
    String mCode = null;
    ProgressDialog mProgressDialog = null;

    private class SendCode extends AsyncTask<String, Void, JSONObject>
    {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mProgressDialog = ProgressDialog.show(RepairLoadActivity.this,
                    null, getString(R.string.wait_dialog));
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            if (mCode == null)
                return null;

            JSONObject object = new JSONObject();
            JSONObject ret = null;
            try {
                object.put("code", mCode);
                object.put("dev_id", getSettings().getString(Settings.KEY_DEVICE_ID));
                NetworkTools.Request request = new NetworkTools.Request("POST", NetworkTools.repairUrl(getSettings()), object);
                NetworkTools.Response response = NetworkTools.one(request, NetworkTools.ZIP_AUTO, getSettings());

                ret = response.getObject();
            } catch (JSONException ex) {
                // wat?
            } catch (Exception ex) {
                ret = new JSONObject();
                try {
                    ret.put("code", 0);
                    ret.put("error", ex.getMessage());
                } catch (JSONException ex2) {
                }
            }
            return ret;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);

            mProgressDialog.dismiss();

            processResponse(result);
        }
    }

    void sendRepairCode(final String code)
    {
        mCode = code;

        new SendCode().execute(code);
    }

    void processResponse(JSONObject object)
    {
        if (object == null)
        {
            Tools.messageBox(this, R.string.repair_load_unknown);
        }
        else
        {
            try {
                if (object.getBoolean("code")) {
                    // got some repair
                    mReturnObject = object;
                    int amount = object.getInt("amount");

                    double remain = getSettings().getDouble(Settings.KEY_MAXHITPOINTS) - getSettings()
                        .getDouble(Settings.KEY_HITPOINTS);
                    double actualHP = Math.min(remain, amount);

                    long timeRatio = getSettings().getLong(Settings.KEY_HP_LOAD_SPEED);
                    long timeout = Math.round(actualHP * timeRatio);

                    EditText text = (EditText)findViewById(R.id.repairCodeText);
                    EditText player = (EditText)findViewById(R.id.playerCodeText);

                    text.setEnabled(false);
                    player.setEnabled(false);

                    mTimerActive = true;
                    Tools.showTimer(this, timeout, R.string.repair_load_comment,
                            new RunnableArg()
                            {
                                @Override
                                public void run()
                                {

                                }
                            },
                            new Runnable()
                            {
                                @Override
                                public void run() {
                                    afterTimer();
                                }
                            });
                }
                else
                {
                    switch (object.getInt("amount")) {
                        case -1:
                            // invalid code
                            Tools.messageBox(this, R.string.repair_load_invalid_code);
                            break;

                        case 0:
                            // used code
                            Tools.messageBox(this, R.string.repair_load_used_code);
                            break;

                    }
                }
            } catch (JSONException ex) {
                Tools.messageBox(this, R.string.repair_load_unknown);
            }
        }

        setEditText(R.id.repairCodeText);
    }

    void afterTimer()
    {
        int amount = 0;
        try
        {
            amount = mReturnObject.getInt("amount");
        }
        catch (JSONException ex)
        {
        }

        double repairNow = getSettings().getDouble(Settings.KEY_HITPOINTS);
        double repairMax = getSettings().getDouble(Settings.KEY_MAXHITPOINTS);
        repairMax = Upgrades.upgradeValue(Settings.KEY_MAXHITPOINTS, repairMax);

        long amountFinal = Math.round(amount * mMultiplier);

        double repairBecome = Tools.clamp(repairNow + amountFinal, 0, repairMax);
        getSettings().setDouble(Settings.KEY_HITPOINTS, repairBecome);

        int newState = Settings.CAR_STATE_OK;
        switch ((int) getSettings().getLong(Settings.KEY_CAR_STATE))
        {
            case Settings.CAR_STATE_OK:
                // do nothing
                break;
            case Settings.CAR_STATE_MALFUNCTION_1:
                newState = Settings.CAR_STATE_OK;
                break;
            case Settings.CAR_STATE_MALFUNCTION_2:
                newState = Settings.CAR_STATE_MALFUNCTION_1;
                break;
        }

        if (mMultiplier >= 2.0)
        {
            newState = Settings.CAR_STATE_OK;
        }

        getSettings().setLong(Settings.KEY_CAR_STATE, (long)newState);

        Tools.messageBox(this, R.string.repair_load_success, new Runnable() {
            @Override
            public void run() {
                Tools.hideKeyboard(RepairLoadActivity.this);
                RepairLoadActivity.this.finish();
                return;
            }
        });
    }

    float parsePlayerCode(String code)
    {
        float ret = -1;
        // third digit is 7 - mech
        String third = code.substring(2, 3);
        String prelast = code.substring(6, 7);
        if (third.equals("7"))
        {
            // mech
            ret = 2.0f;
        }
        else if (prelast.equals("2"))
        {
            // animal
            ret = 0;
        }
        else if (prelast.equals("3"))
        {
            // warrior
            ret = 0.5f;
        }
        else if (prelast.equals("4"))
        {
            // realist
            ret = 1.0f;
        }
        else if (prelast.equals("5"))
        {
            // humanist
            ret = 1.5f;
        }
        else if (prelast.equals("6"))
        {
            // immune
            ret = 1.5f;
        }

        return ret;
    }

    @Override
    public void onBackPressed()
    {
        if (mTimerActive)
            return;

        super.onBackPressed();
    }
}
