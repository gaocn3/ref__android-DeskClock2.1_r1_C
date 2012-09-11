package com.android.deskclock;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;


/**
 * TODO: Insert description here. (generated by isaackatz)
 */
public class StopwatchFragment extends DeskClockFragment {

    // Stopwatch states
    private static final int STOPWATCH_RESET = 0;
    private static final int STOPWATCH_RUNNING = 1;
    private static final int STOPWATCH_STOPPED = 2;

    private static final int MAX_LAPS = 99;

    int mState = STOPWATCH_RESET;


    // Stopwatch views that are accessed by the activity
    Button mLeftButton, mRightButton;
    CircleTimerView mTime;
    TimerView mTimeText;
    View mLapsTitle;
    ListView mLapsList;
    Button mShareButton;
    View mButtonSeperator;

    // Used for calculating the time from the start taking into account the pause times
    long mStartTime = 0;
    long mAccumulatedTime = 0;

    // Lap information
    class Lap {
        Lap () {
            mLapTime = 0;
            mTotalTime = 0;
        }

        Lap (long time, long total) {
            mLapTime = time;
            mTotalTime = total;
        }
        public long mLapTime;
        public long mTotalTime;
    }

    // Adapter for the ListView that shows the lap times.
    class LapsListAdapter extends BaseAdapter {

        Context mContext;
        ArrayList<Lap> mLaps = new ArrayList<Lap>();
        private final LayoutInflater mInflater;

        public LapsListAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (mLaps.size() == 0 || position >= mLaps.size()) {
                return null;
            }
            View lapInfo;
            if (convertView != null) {
                lapInfo = convertView;
            } else {
                lapInfo =  mInflater.inflate(R.layout.lap_view, parent, false);
            }
            TextView count = (TextView)lapInfo.findViewById(R.id.lap_number);
            TextView lapTime = (TextView)lapInfo.findViewById(R.id.lap_time);
            TextView toalTime = (TextView)lapInfo.findViewById(R.id.lap_total);
            lapTime.setText(getTimeText(mLaps.get(position).mLapTime));
            toalTime.setText(getTimeText(mLaps.get(position).mTotalTime));
            count.setText(getString(R.string.sw_current_lap_number, mLaps.size() - position));
            return lapInfo;

        }

        @Override
        public int getCount() {
            return mLaps.size();
        }

        @Override
        public Object getItem(int position) {
            if (mLaps.size() == 0 || position >= mLaps.size()) {
                return null;
            }
            return mLaps.get(position);
        }

        public void addLap(Lap l) {
            mLaps.add(0, l);
            notifyDataSetChanged();
        }

        public void clearLaps() {
            mLaps.clear();
            notifyDataSetChanged();
        }

        // Helper function used to get the lap data to be stored in the activitys's bundle
        public long [] getLapTimes() {
            int size = mLaps.size();
            if (size == 0) {
                return null;
            }
            long [] laps = new long[size];
            for (int i = 0; i < size; i ++) {
                laps[i] = mLaps.get(i).mLapTime;
            }
            return laps;
        }

        // Helper function to restore adapter's data from the activity's bundle
        public void setLapTimes(long [] laps) {
            if (laps == null || laps.length == 0) {
                return;
            }

            int size = laps.length;
            mLaps.clear();
            for (int i = 0; i < size; i ++) {
                mLaps.add(new Lap (laps[i], 0));
            }
            long totalTime = 0;
            for (int i = size -1; i >= 0; i --) {
                totalTime += laps[i];
                mLaps.get(i).mTotalTime = totalTime;
            }
            notifyDataSetChanged();
        }
    }

    // Keys for data stored in the activity's bundle
    private static final String START_TIME_KEY = "start_time";
    private static final String ACCUM_TIME_KEY = "accum_time";
    private static final String STATE_KEY = "state";
    private static final String LAPS_KEY = "laps";

    LapsListAdapter mLapsAdapter;

    public StopwatchFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.stopwatch_fragment, container, false);
        mLeftButton = (Button)v.findViewById(R.id.stopwatch_left_button);
        mLeftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("---------------------------- on click");
                buttonClicked(true);
                switch (mState) {
                    case STOPWATCH_RUNNING:
                        // Save lap time
                        addLapTime(System.currentTimeMillis()/10);
                        showLaps();
                        setButtons(STOPWATCH_RUNNING);
                        break;
                    case STOPWATCH_STOPPED:
                        // do reset
                        mAccumulatedTime = 0;
                        mLapsAdapter.clearLaps();
                        showLaps();
                        mTime.stopIntervalAnimation();
                        mTime.reset();
                        mTimeText.setTime(mAccumulatedTime);
                        mTimeText.blinkTimeStr(false);
                        setButtons(STOPWATCH_RESET);
                        mState = STOPWATCH_RESET;
                        break;
                    default:
                        Log.wtf("Illegal state " + mState
                                + " while pressing the left stopwatch button");
                        break;
                }
            }
        });


        mRightButton = (Button)v.findViewById(R.id.stopwatch_right_button);
        mRightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("---------------------------- on click");
                buttonClicked(true);
                switch (mState) {
                    case STOPWATCH_RUNNING:
                        // do stop
                        stopUpdateThread();
                        mTime.pauseIntervalAnimation();
                        long curTime = System.currentTimeMillis()/10;
                        mAccumulatedTime += (curTime - mStartTime);
                        mTimeText.setTime(mAccumulatedTime);
                        mTimeText.blinkTimeStr(true);
                        updateCurrentLap(curTime, mAccumulatedTime);
                        setButtons(STOPWATCH_STOPPED);
                        mState = STOPWATCH_STOPPED;
                        break;
                    case STOPWATCH_RESET:
                    case STOPWATCH_STOPPED:
                        // do start
                        mStartTime = System.currentTimeMillis()/10;
                        startUpdateThread();
                        mTimeText.blinkTimeStr(false);
                        if (mTime.isAnimating()) {
                            mTime.startIntervalAnimation();
                        }
                        setButtons(STOPWATCH_RUNNING);
                        mState = STOPWATCH_RUNNING;
                        break;
                    default:
                        Log.wtf("Illegal state " + mState
                                + " while pressing the right stopwatch button");
                        break;
                }
            }
        });
        mShareButton = (Button)v.findViewById(R.id.stopwatch_share_button);
        mButtonSeperator = v.findViewById(R.id.stopwatch_button_seperator);

        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                // Add data to the intent, the receiving app will decide what to
                // do with it.
                intent.putExtra(Intent.EXTRA_SUBJECT, mContext.getResources().getString(R.string.sw_share_title));
                intent.putExtra(Intent.EXTRA_TEXT, buildShareResults());
                startActivity(Intent.createChooser(intent, null));
            }
        });

        mTime = (CircleTimerView)v.findViewById(R.id.stopwatch_time);
        mTimeText = (TimerView)v.findViewById(R.id.stopwatch_time_text);
        mLapsTitle = v.findViewById(R.id.laps_title);
        mLapsList = (ListView)v.findViewById(R.id.laps_list);
        mLapsList.setDividerHeight(0);
        mLapsAdapter = new LapsListAdapter(getActivity());
        if (mLapsList != null) {
            mLapsList.setAdapter(mLapsAdapter);
        }

        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt(STATE_KEY, STOPWATCH_RESET);
            mStartTime = savedInstanceState.getLong(START_TIME_KEY, 0);
            mAccumulatedTime = savedInstanceState.getLong(ACCUM_TIME_KEY, 0);
            mLapsAdapter.setLapTimes(savedInstanceState.getLongArray(LAPS_KEY));
        }
        return v;
    }

    @Override
    public void onResume() {
        setButtons(mState);
        mTimeText.setTime(mAccumulatedTime);
        if (mState == STOPWATCH_RUNNING) {
            startUpdateThread();
        }
        showLaps();
        super.onResume();
    }

    @Override
    public void onPause() {
        if (mState == STOPWATCH_RUNNING) {
            stopUpdateThread();
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState (Bundle outState) {
        outState.putInt(STATE_KEY, mState);
        outState.putLong(START_TIME_KEY, mStartTime);
        outState.putLong(ACCUM_TIME_KEY, mAccumulatedTime);
        if (mLapsAdapter != null) {
            long [] laps = mLapsAdapter.getLapTimes();
            if (laps != null) {
                outState.putLongArray(LAPS_KEY, laps);
            }
        }
        super.onSaveInstanceState(outState);
    }

    private void showShareButton(boolean show) {
        if (mShareButton != null) {
            mShareButton.setVisibility(show ? View.VISIBLE : View.GONE);
            mButtonSeperator.setVisibility(show ? View.VISIBLE : View.GONE);
            mShareButton.setEnabled(show);
        }
    }

    /***
     * Update the buttons on the stopwatch according to the watch's state
     */
    private void setButtons(int state) {
        switch (state) {
            case STOPWATCH_RESET:
                setButton(mLeftButton, R.string.sw_lap_button, false, View.INVISIBLE);
                setButton(mRightButton, R.string.sw_start_button, true, View.VISIBLE);
                showShareButton(false);
                break;
            case STOPWATCH_RUNNING:
                setButton(mLeftButton, R.string.sw_lap_button, !reachedMaxLaps(), View.VISIBLE);
                setButton(mRightButton, R.string.sw_stop_button, true, View.VISIBLE);
                showShareButton(false);
                break;
            case STOPWATCH_STOPPED:
                setButton(mLeftButton, R.string.sw_reset_button, true, View.VISIBLE);
                setButton(mRightButton, R.string.sw_start_button, true, View.VISIBLE);
                showShareButton(true);
                break;
            default:
                break;
        }
    }
    private boolean reachedMaxLaps() {
        return mLapsAdapter.getCount() >= MAX_LAPS;
    }

    /***
     * Set a single button with the string and states provided.
     * @param b - Button view to update
     * @param text - Text in button
     * @param enabled - enable/disables the button
     * @param visibility - Show/hide the button
     */
    private void setButton (Button b, int text, boolean enabled, int visibility) {
        b.setText(text);
        b.setVisibility(visibility);
        b.setEnabled(enabled);
    }

    /***
     * Sets the string of the time running on the stopwatch up to hundred of a second accuracy
     * @param time - in hundreds of a second since the stopwatch started
     */
    private String getTimeText(long time) {
        if (time < 0) {
            time = 0;
        }
        long hundreds, seconds, minutes, hours;
        seconds = time / 100;
        hundreds = (time - seconds * 100);
        minutes = seconds / 60;
        seconds = seconds - minutes * 60;
        hours = minutes / 60;
        minutes = minutes - hours * 60;
        if (hours > 99) {
            hours = 0;
        }
        // TODO: must build to account for localization
    	String timeStr;
		if (hours >= 10) {
			timeStr = String.format("%02dh %02dm %02ds .%02d", hours, minutes,
					seconds, hundreds);
		} else if (hours > 0) {
			timeStr = String.format("%01dh %02dm %02ds .%02d", hours, minutes,
					seconds, hundreds);
		} else if (minutes >= 10) {
			timeStr = String.format("%02dm %02ds .%02d", minutes, seconds,
					hundreds);
		} else {
			timeStr = String.format("%02dm %02ds .%02d", minutes, seconds,
					hundreds);
		}
        return timeStr;
    }

    /***
     *
     * @param time - in hundredths of a second
     */
    private void addLapTime(long time) {
        int size = mLapsAdapter.getCount();
        long curTime = time - mStartTime + mAccumulatedTime;
        if (size == 0) {
            // Always show the ending lap and a new one
            mLapsAdapter.addLap(new Lap(curTime, curTime));
            mLapsAdapter.addLap(new Lap(0, curTime));
            mTime.setIntervalTime(curTime * 10);
        } else {
            long lapTime = curTime - ((Lap) mLapsAdapter.getItem(1)).mTotalTime;
            ((Lap)mLapsAdapter.getItem(0)).mLapTime = lapTime;
            ((Lap)mLapsAdapter.getItem(0)).mTotalTime = curTime;
            mLapsAdapter.addLap(new Lap(0, 0));
            mTime.setMarkerTime(lapTime * 10);
        //    mTime.setIntervalTime(lapTime * 10);
        }
        mLapsAdapter.notifyDataSetChanged();
        // Start lap animation starting from the second lap
         mTime.stopIntervalAnimation();
         if (!reachedMaxLaps()) {
             mTime.startIntervalAnimation();
         }
    }

    private void updateCurrentLap(long curTime, long totalTime) {
        if (mLapsAdapter.getCount() > 0) {
            Lap curLap = (Lap)mLapsAdapter.getItem(0);
            curLap.mLapTime = totalTime - ((Lap)mLapsAdapter.getItem(1)).mTotalTime;
            curLap.mTotalTime = totalTime;
            mLapsAdapter.notifyDataSetChanged();
        }
    }

    private void showLaps() {
        if (mLapsAdapter.getCount() > 0) {
            mLapsList.setVisibility(View.VISIBLE);
            mLapsTitle.setVisibility(View.VISIBLE);
        } else {
            mLapsList.setVisibility(View.INVISIBLE);
            mLapsTitle.setVisibility(View.INVISIBLE);
        }
    }

    private void startUpdateThread() {
        mTime.post(mTimeUpdateThread);
    }

    private void stopUpdateThread() {
        mTime.removeCallbacks(mTimeUpdateThread);
    }

    Runnable mTimeUpdateThread = new Runnable() {
        @Override
        public void run() {
            long curTime = System.currentTimeMillis()/10;
            long totalTime = mAccumulatedTime + (curTime - mStartTime);
            if (mTime != null) {
            	mTimeText.setTime(totalTime);
            }
            if (mLapsAdapter.getCount() > 0) {
                updateCurrentLap(curTime, totalTime);
            }
            mTime.postDelayed(mTimeUpdateThread, 10);
        }
    };

    private String buildShareResults() {
        return getString(R.string.sw_share_main, mTimeText.getTimeString());
    }

}
