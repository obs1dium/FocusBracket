package com.obsidium.focusbracket;

import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

public class FocusActivity extends BaseActivity implements SurfaceHolder.Callback, CameraEx.ShutterListener
{
    private static final int COUNTDOWN_SECONDS = 5;

    private SurfaceHolder       m_surfaceHolder;
    private CameraEx            m_camera;

    private Handler             m_handler = new Handler();

    private FocusScaleView m_focusScaleView;
    private TextView            m_tvMsg;
    private TextView            m_tvInstructions;

    enum State { error, setMin, setMax, setNumPics, shoot }
    private State               m_state = State.setMin;

    private int                 m_minFocus;
    private int                 m_maxFocus;
    private int                 m_curFocus;
    private int                 m_focusBeforeDrive;

    private ArrayList<Integer>  m_pictureCounts;
    private int                 m_pictureCountIndex;

    private LinkedList<Integer> m_focusQueue;
    private boolean             m_waitingForFocus;

    private int                 m_countdown;
    private Runnable            m_countDownRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (--m_countdown > 0)
            {
                m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
                m_handler.postDelayed(this, 1000);
            }
            else
            {
                m_tvMsg.setVisibility(View.GONE);
                startShooting();
            }
        }
    };

    private Runnable            m_checkFocusRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (m_waitingForFocus && m_focusBeforeDrive == m_curFocus)
                focus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus);

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        m_surfaceHolder = surfaceView.getHolder();
        m_surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        m_focusScaleView = (FocusScaleView)findViewById(R.id.vFocusScale);

        m_tvMsg = (TextView)findViewById(R.id.tvMsg);
        m_tvInstructions = (TextView)findViewById(R.id.tvInstructions);
    }

    @Override
    protected void onResume()
    {
        //Logger.info("onResume");
        super.onResume();
        m_camera = CameraEx.open(0, null);
        m_surfaceHolder.addCallback(this);

        m_camera.setShutterListener(this);

        m_camera.setFocusDriveListener(new CameraEx.FocusDriveListener()
        {
            @Override
            public void onChanged(CameraEx.FocusPosition focusPosition, CameraEx cameraEx)
            {
                //Logger.info("FocusDriveListener: currentPosition " + focusPosition.currentPosition);
                m_focusScaleView.setMaxPosition(focusPosition.maxPosition);
                m_focusScaleView.setCurPosition(focusPosition.currentPosition);
                m_curFocus = focusPosition.currentPosition;
                if (m_waitingForFocus)
                {
                    if (m_curFocus == m_focusQueue.getFirst())
                    {
                        // Focused, take picture
                        //Logger.info("Taking picture (FocusDriveListener)");
                        takePicture();
                    }
                    else
                        focus();
                }
            }
        });

        setDefaults();
        setState(State.setMin);
    }

    // CameraEx.ShutterListener
    @Override
    public void onShutter(int i, CameraEx cameraEx)
    {
        // i: 0 = success, 1 = canceled, 2 = error
        //Logger.info("onShutter (i " + i + ")");
        m_camera.cancelTakePicture();
        if (i == 0)
        {
            m_focusQueue.removeFirst();
            if (m_focusQueue.isEmpty())
            {
                m_tvMsg.setText("\uE013 Done!");
                m_tvMsg.setVisibility(View.VISIBLE);
                m_tvInstructions.setText("Press \uE04C to start over...");
                m_tvInstructions.setVisibility(View.VISIBLE);
            }
            else
            {
                // Move to next focus position
                startFocusing();
            }
        }
    }

    private void takePicture()
    {
        m_tvMsg.setVisibility(View.GONE);
        m_waitingForFocus = false;
        m_handler.removeCallbacks(m_checkFocusRunnable);
        m_camera.burstableTakePicture();
    }

    private void focus()
    {
        final int nextFocus = m_focusQueue.getFirst();
        m_focusBeforeDrive = m_curFocus;
        if (m_curFocus == nextFocus)
        {
            //Logger.info("Taking picture (focus)");
            takePicture();
        }
        else
        {
            final int absDiff = Math.abs(m_curFocus - nextFocus);
            final int speed;
            if (absDiff > 25)
                speed = 4;
            else if (absDiff > 20)
                speed = 3;
            else if (absDiff > 15)
                speed = 2;
            else
                speed = 1;
            //Logger.info("Starting focus drive (speed " + speed + ")");
            m_camera.startOneShotFocusDrive(m_curFocus < nextFocus ? CameraEx.FOCUS_DRIVE_DIRECTION_FAR : CameraEx.FOCUS_DRIVE_DIRECTION_NEAR, speed);
            // startOneShotFocusDrive won't always trigger our FocusDriveListener
            m_handler.postDelayed(m_checkFocusRunnable, 50);
        }
    }

    private void startFocusing()
    {
        m_waitingForFocus = true;
        m_tvMsg.setText("Focusing...");
        m_tvMsg.setVisibility(View.VISIBLE);
        focus();
    }

    private void startShooting()
    {
        m_tvMsg.setVisibility(View.GONE);
        startFocusing();
    }

    private void initFocusQueue()
    {
        m_focusQueue = new LinkedList<Integer>();
        final int focusDiff = m_maxFocus - m_minFocus;
        final int pictureCount = m_pictureCounts.get(m_pictureCountIndex);
        final int step = (int)((float)focusDiff / (float)(pictureCount - 1));
        int focus = m_minFocus;
        for (int i = 0; i < pictureCount; ++i, focus += step)
            m_focusQueue.addLast(focus);
    }

    private void initPictureCounts()
    {
        m_pictureCounts = new ArrayList<Integer>();
        final int focusDiff = m_maxFocus - m_minFocus;
        int lastStep = 0;
        for (int i = 3; i < focusDiff; ++i)
        {
            final int step = (int)((float)focusDiff / (float)(i - 1));
            if (step > 1 && step != lastStep)
            {
                m_pictureCounts.add(i);
                lastStep = step;
            }
        }
        m_pictureCounts.add(focusDiff);
        m_pictureCountIndex = 0;
    }

    private void updatePictureCountMsg()
    {
        m_tvMsg.setText(String.valueOf(m_pictureCounts.get(m_pictureCountIndex)));
    }

    private void initControlsFromState()
    {
        switch (m_state)
        {
            case setMin:
                m_tvMsg.setVisibility(View.GONE);
                m_focusScaleView.setVisibility(View.VISIBLE);
                m_focusScaleView.setMinPosition(0);
                m_tvInstructions.setVisibility(View.VISIBLE);
                m_tvInstructions.setText("Set minimum focus distance, \uE04C to confirm");
                break;
            case setMax:
                m_tvInstructions.setText("Set maximum focus distance, \uE04C to confirm");
                break;
            case setNumPics:
                m_tvInstructions.setText("Use dial to select number of pictures, \uE04C to confirm");
                m_tvMsg.setVisibility(View.VISIBLE);
                m_focusScaleView.setVisibility(View.GONE);
                break;
            case shoot:
                m_tvMsg.setVisibility(View.VISIBLE);
                m_tvMsg.setText(String.format("Starting in %d...", m_countdown));
                m_tvInstructions.setVisibility(View.GONE);
                break;
        }
    }

    /*
        Sets camera default parameters (manual mode, single shooting, manual focus)
     */
    private void setDefaults()
    {
        final Camera.Parameters params = m_camera.createEmptyParameters();
        final CameraEx.ParametersModifier modifier = m_camera.createParametersModifier(params);
        params.setSceneMode(CameraEx.ParametersModifier.SCENE_MODE_MANUAL_EXPOSURE);
        modifier.setDriveMode(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE);
        params.setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
        modifier.setSelfTimer(0);
        m_camera.getNormalCamera().setParameters(params);

        /*
            modifier.isFocusDriveSupported() returns false on ILCE-5100, focus drive is working anyway...
            This is how SmartRemote checks for it:
         */
        final String platformVersion = ScalarProperties.getString(ScalarProperties.PROP_VERSION_PLATFORM);
        if (platformVersion != null)
        {
            final String[] split = platformVersion.split("\\Q.\\E");
            if (split.length >= 2)
            {
                if (Integer.parseInt(split[1]) < 3)
                {
                    m_state = State.error;
                    m_tvMsg.setVisibility(View.VISIBLE);
                    m_tvMsg.setText("ERROR: Focus drive not supported");
                }
            }
        }
    }

    private void abortShooting()
    {
        m_waitingForFocus = false;
        m_handler.removeCallbacks(m_checkFocusRunnable);
        m_handler.removeCallbacks(m_countDownRunnable);
        m_focusQueue = null;
        m_pictureCounts = null;
    }

    private void setState(State state)
    {
        m_state = state;
        switch (m_state)
        {
            case setMin:
                m_minFocus = 0;
                break;
            case setMax:
                m_maxFocus = 0;
                break;
            case setNumPics:
                initPictureCounts();
                updatePictureCountMsg();
                break;
            case shoot:
                initFocusQueue();
                m_countdown = COUNTDOWN_SECONDS;
                m_handler.postDelayed(m_countDownRunnable, 1000);
                break;
        }
        initControlsFromState();
    }

    @Override
    protected boolean onUpperDialChanged(int value)
    {
        if (m_state == State.setNumPics)
        {
            if (value < 0)
            {
                if (m_pictureCountIndex > 0)
                    --m_pictureCountIndex;
            }
            else
            {
                if (m_pictureCountIndex < m_pictureCounts.size() - 1)
                    ++m_pictureCountIndex;
            }
            updatePictureCountMsg();
        }
        return true;
    }

    @Override
    protected boolean onEnterKeyDown()
    {
        // Don't use onEnterKeyUp - we sometimes get an onEnterKeyUp event when launching the app
        switch (m_state)
        {
            case setMin:
                m_minFocus = m_curFocus;
                m_focusScaleView.setMinPosition(m_curFocus);
                setState(State.setMax);
                break;
            case setMax:
                if (m_curFocus > m_minFocus)
                {
                    m_maxFocus = m_curFocus;
                    setState(State.setNumPics);
                }
                break;
            case setNumPics:
                setState(State.shoot);
                break;
            case shoot:
                abortShooting();
                setState(State.setMin);
                break;
        }
        return true;
    }

    @Override
    protected boolean onMenuKeyUp()
    {
        onBackPressed();
        return true;
    }

    @Override
    protected void onPause()
    {
        //Logger.info("onPause");
        super.onPause();

        abortShooting();

        m_surfaceHolder.removeCallback(this);
        m_camera.getNormalCamera().stopPreview();
        m_camera.release();
        m_camera = null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            Camera cam = m_camera.getNormalCamera();
            cam.setPreviewDisplay(holder);
            cam.startPreview();
        }
        catch (IOException e)
        {
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected void setColorDepth(boolean highQuality)
    {
        super.setColorDepth(false);
    }
}
