package com.prangesoftwaresolutions.audioanchor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.util.ArrayList;


public class PlayActivity extends AppCompatActivity {

    public static final String Broadcast_PLAY_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.PlayNewAudio";
    public static final String Broadcast_PAUSE_AUDIO = "com.prangesoftwaresolutions.audioanchor.PauseAudio";

    private MediaPlayerService mPlayer;
    boolean serviceBound = false;
    BroadcastReceiver mPlayStatusReceiver;
    BroadcastReceiver mNewAudioFileReceiver;

    // Audio File variables
    AudioFile mAudioFile;
    int mAlbumId;
    private Uri mCurrentUri;

    // The Views
    ImageView mCoverIV;
    TextView mTitleTV;
    TextView mAlbumTV;
    ImageView mPlayIV;
    ImageView mBackIV;
    ImageView mBack10IV;
    ImageView mForwardIV;
    ImageView mForward10IV;
    SeekBar mSeekBar;
    TextView mCompletedTimeTV;
    TextView mTimeTV;
    TextView mSleepCountDownTV;

    // SeekBar variables
    Handler mHandler;
    Runnable mRunnable;

    // SleepTimer variables
    CountDownTimer mSleepTimer;

    // Bookmark Adapter and Bookmark ListView
    BookmarkCursorAdapter mBookmarkAdapter;
    ListView mBookmarkListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        // Get the current uri from the intent
        mCurrentUri = getIntent().getData();
        mAlbumId = getIntent().getIntExtra("albumId", -1);

        mCoverIV = findViewById(R.id.play_cover);
        mTitleTV = findViewById(R.id.play_audio_file_title);
        mAlbumTV = findViewById(R.id.play_album_title);
        mPlayIV = findViewById(R.id.play_play);
        mBackIV = findViewById(R.id.play_backward);
        mBack10IV = findViewById(R.id.play_backward_10);
        mForwardIV = findViewById(R.id.play_forward);
        mForward10IV = findViewById(R.id.play_forward_10);
        mSeekBar = findViewById(R.id.play_seekbar);
        mCompletedTimeTV = findViewById(R.id.play_completed_time);
        mTimeTV = findViewById(R.id.play_time);
        mSleepCountDownTV = findViewById(R.id.play_sleep_time);

        mAudioFile = AudioFile.getAudioFile(this, mCurrentUri);
        setNewAudioFile();
        setAlbumCover();

        mHandler = new Handler();

        // Start the play service
        startService();

        mPlayStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String s = intent.getStringExtra(MediaPlayerService.SERVICE_MESSAGE_PLAY_STATUS);
                if (s != null) {
                    if (s.equals(MediaPlayerService.MSG_PLAY)) {
                        mPlayIV.setImageResource(R.drawable.pause_button);
                    } else if (s.equals(MediaPlayerService.MSG_PAUSE)) {
                        mPlayIV.setImageResource(R.drawable.play_button);
                    }
                }
            }
        };

        mNewAudioFileReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int audioIndex = intent.getIntExtra(MediaPlayerService.SERVICE_MESSAGE_NEW_AUDIO, -1);
                if (audioIndex > -1) {
                    loadAudioFile(audioIndex);
                    initializeSeekBar();
                }
            }
        };

        mPlayIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (!mPlayer.isPlaying()) {
                    playAudio();
                } else {
                    pauseAudio();
                }

            }
        });

        mBackIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.backward(30);
            }
        });
        mBack10IV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.backward(10);
            }
        });

        mForwardIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.forward(30);

            }
        });
        mForward10IV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.forward(10);

            }
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mPlayer.setCurrentPosition(progress*1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((mPlayStatusReceiver),
                new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE)
        );
        LocalBroadcastManager.getInstance(this).registerReceiver((mNewAudioFileReceiver),
                new IntentFilter(MediaPlayerService.SERVICE_NEW_AUDIO)
        );
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayIV.setImageResource(R.drawable.pause_button);
        } else{
            mPlayIV.setImageResource(R.drawable.play_button);
        }
        if (mPlayer != null) {
            mAudioFile = mPlayer.getCurrentAudioFile();
            setNewAudioFile();
            initializeSeekBar();
        }
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlayStatusReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNewAudioFileReceiver);
        super.onStop();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sleep_timer:
                showSleepTimerDialog();
                return true;
            case R.id.menu_goto:
                showGoToDialog();
                return true;
            case R.id.menu_set_bookmark:
                showSetBookmarkDialog(null);
                return true;
            case R.id.menu_show_bookmarks:
                showShowBookmarksDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return(super.onOptionsItemSelected(item));
    }


    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayer = binder.getService();
            serviceBound = true;

            // Perform actions that can only be performed once the service is connected
            // Initialize the seek bar for the current audio file
            initializeSeekBar();

            // Initialize the mPlayer for the current audio file
            mPlayer.loadAudioFile(mAudioFile.getPath(), mAudioFile.getCompletedTime());

            // Update the time column of the audiofiles table if it has not yet been set
            if (mAudioFile.getTime() == 0) {
                mAudioFile.setTime(mPlayer.getDuration());
                ContentValues values = new ContentValues();
                values.put(AnchorContract.AudioEntry.COLUMN_TIME, mAudioFile.getTime());
                getContentResolver().update(mCurrentUri, values, null, null);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private void startService() {
        //Check is service is active
        if (!serviceBound) {
            storeAudioFiles();
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void playAudio() {
        //Send a broadcast to the service -> PLAY_NEW_AUDIO
        startService();
        Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
        sendBroadcast(broadcastIntent);
    }

    private void pauseAudio() {
        //Send a broadcast to the service -> PLAY_NEW_AUDIO
        Intent broadcastIntent = new Intent(Broadcast_PAUSE_AUDIO);
        sendBroadcast(broadcastIntent);
    }

    private void storeAudioFiles() {
        //Store Serializable audioList to SharedPreferences
        String sortOrder = "LOWER(" + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";
        ArrayList<AudioFile> audioList = AudioFile.getAllAudioFilesFromAlbum(this, mAlbumId, sortOrder);
        int audioIndex = AudioFile.getIndex(audioList, mAudioFile.getId());
        StorageUtil storage = new StorageUtil(getApplicationContext());
        storage.storeAudio(audioList);
        storage.storeAudioIndex(audioIndex);
    }

    private void loadAudioFile(int audioIndex) {
        //Load data from SharedPreferences
        StorageUtil storage = new StorageUtil(getApplicationContext());
        ArrayList<AudioFile> audioList = new ArrayList<>(storage.loadAudio());
        mAudioFile = audioList.get(audioIndex);
        setNewAudioFile();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            //service is active
            mPlayer.stopSelf();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /*
     * Set up the views for the current audio file
     */
    void setNewAudioFile() {
        // Set TextViews
        mTitleTV.setText(mAudioFile.getTitle());
        mTimeTV.setText(Utils.formatTime(mAudioFile.getTime(), mAudioFile.getTime()));
        mAlbumTV.setText(mAudioFile.getAlbumTitle());

        // Update the time column of the audiofiles table if it has not yet been set
        if (mPlayer != null && mAudioFile.getTime() == 0) {
            mAudioFile.setTime(mPlayer.getDuration());
            Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, mAudioFile.getId());
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AudioEntry.COLUMN_TIME, mAudioFile.getTime());
            getContentResolver().update(uri, values, null, null);
        }
    }

    /*
     * Set album cover view for the current cover path
     */
    void setAlbumCover() {
        // Set the album cover to the ImageView and the album title to the TextView
            Point size = new Point();
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            Display display;
            int reqSize = getResources().getDimensionPixelSize(R.dimen.default_device_width);
            if (wm != null) {
                display = wm.getDefaultDisplay();
                display.getSize(size);
                reqSize = java.lang.Math.max(size.x, size.y);
            }
            BitmapUtils.setImage(mCoverIV, mAudioFile.getCoverPath(), reqSize);
    }

    /*
     * Initialize the SeekBar
     */
    void initializeSeekBar(){
        mSeekBar.setMax(mPlayer.getDuration()/1000);

        mRunnable = new Runnable() {
            @Override
            public void run() {
                if(mPlayer!=null){
                    int currentPosition = mPlayer.getCurrentPosition();
                    mSeekBar.setProgress(currentPosition/1000);
                    mCompletedTimeTV.setText(Utils.formatTime(currentPosition, mAudioFile.getTime()));
                }
                mHandler.postDelayed(mRunnable,100);
            }
        };
        mHandler.postDelayed(mRunnable,100);
    }

    void showSleepTimerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_sleep_timer, null);
        builder.setView(dialogView);
        final EditText setTime = dialogView.findViewById(R.id.sleep_timer_set_time);

        builder.setTitle(R.string.sleep_timer);
        builder.setMessage(R.string.dialog_msg_sleep_timer);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            // User clicked the OK button so set the sleep timer
            public void onClick(DialogInterface dialog, int id) {
                // If a sleep timer was already set, cancel the previous one and start a new one
                if (mSleepTimer != null) {
                    mSleepTimer.cancel();
                    mSleepCountDownTV.setVisibility(View.GONE);
                    mPlayer.setVolume(1.0f);
                }

                String minutesString = setTime.getText().toString();
                if (minutesString.isEmpty() || minutesString.equals("0")) {
                    return;
                }
                final int minutes = Integer.parseInt(minutesString);
                mSleepCountDownTV.setVisibility(View.VISIBLE);

                mSleepTimer = new CountDownTimer(minutes*60*1000, 1000) {

                    @Override
                    public void onTick(long l) {
                        String timeString = Utils.formatTime(l, minutes*60*1000);
                        String sleepTimeLeft = getResources().getString(R.string.sleep_time_left, timeString);
                        mSleepCountDownTV.setText(sleepTimeLeft);
                        int totalSteps = 10;
                        if ((l/1000) < totalSteps) {
                            mPlayer.decreaseVolume((int) (totalSteps - (l/1000)), totalSteps);
                        }
                    }

                    @Override
                    public void onFinish() {
                        pauseAudio();
                        mPlayer.setVolume(1.0f);
                        mSleepCountDownTV.setVisibility(View.GONE);
                    }
                };
                mSleepTimer.start();
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    void showGoToDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_goto, null);
        builder.setView(dialogView);

        final EditText gotoHours = dialogView.findViewById(R.id.goto_hours);
        final EditText gotoMinutes = dialogView.findViewById(R.id.goto_minutes);
        final EditText gotoSeconds = dialogView.findViewById(R.id.goto_seconds);

        int currPos = mPlayer.getCurrentPosition();
        String[] currPosArr = Utils.formatTime(currPos, 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setTitle(R.string.go_to);
        builder.setMessage(R.string.dialog_msg_goto);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            // User clicked the OK button so set the sleep timer
            public void onClick(DialogInterface dialog, int id) {
                String hours = gotoHours.getText().toString();
                String minutes = gotoMinutes.getText().toString();
                String seconds = gotoSeconds.getText().toString();
                String timeString = hours + ":" + minutes + ":" + seconds;

                try {
                    long millis = Utils.getMillisFromString(timeString);
                    mPlayer.setCurrentPosition((int) millis);
                } catch (NumberFormatException e) {
                    Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Show a dialog that lets the user specify a title for the bookmark. Let the user confirm
     * that they want to create the bookmark and save the bookmark.
     * If uri is null, a new bookmark is created. Otherwise the bookmark with the corresponding uri
     * is updated.
     */
    void showSetBookmarkDialog(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_bookmark, null);
        builder.setView(dialogView);

        final EditText bookmarkTitleET = dialogView.findViewById(R.id.bookmark_title_et);
        final EditText gotoHours = dialogView.findViewById(R.id.goto_hours);
        final EditText gotoMinutes = dialogView.findViewById(R.id.goto_minutes);
        final EditText gotoSeconds = dialogView.findViewById(R.id.goto_seconds);

        int currPos;
        if (uri != null) {
            builder.setTitle(R.string.edit_bookmark);
            // Get the position of the bookmark
            String[] projection = {
                    AnchorContract.BookmarkEntry.COLUMN_TITLE,
                    AnchorContract.BookmarkEntry.COLUMN_POSITION};
            Cursor c = getContentResolver().query(uri, projection, null, null, null);
            if (c != null) {
                c.moveToFirst();
                currPos = c.getInt(c.getColumnIndex(AnchorContract.BookmarkEntry.COLUMN_POSITION));
                String title = c.getString(c.getColumnIndex(AnchorContract.BookmarkEntry.COLUMN_TITLE));
                bookmarkTitleET.setText(title);
                c.close();
            } else {
                currPos = 0;
            }
        } else {
            builder.setTitle(R.string.set_bookmark);
            currPos = mPlayer.getCurrentPosition();
        }

        // Set the edit text views to the current position
        String[] currPosArr = Utils.formatTime(currPos, 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            // User clicked the OK button so save the bookmark
            public void onClick(DialogInterface dialog, int id) {
                String title = bookmarkTitleET.getText().toString();

                String hours = gotoHours.getText().toString();
                String minutes = gotoMinutes.getText().toString();
                String seconds = gotoSeconds.getText().toString();
                String timeString = hours + ":" + minutes + ":" + seconds;

                if (title.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.empty_title_error, Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        long millis = Utils.getMillisFromString(timeString);
                        int audioFileId = mAudioFile.getId();
                        ContentValues values = new ContentValues();
                        values.put(AnchorContract.BookmarkEntry.COLUMN_TITLE, title);
                        values.put(AnchorContract.BookmarkEntry.COLUMN_POSITION, millis);
                        values.put(AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE, audioFileId);
                        if (uri != null) {
                            // Update the bookmark in the bookmarks table
                            String sel = AnchorContract.BookmarkEntry._ID + "=?";
                            String[] selArgs = {Long.toString(ContentUris.parseId(uri))};
                            getContentResolver().update(uri, values, sel, selArgs);

                            // Reload the ListView
                            Cursor c = getBookmarks();
                            mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
                            mBookmarkListView.setAdapter(mBookmarkAdapter);
                        } else {
                            getContentResolver().insert(AnchorContract.BookmarkEntry.CONTENT_URI, values);
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        if (uri != null) {
            builder.setNeutralButton(R.string.dialog_msg_delete, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User clicked the "Delete" button, so delete the bookmark
                    deleteBookmarkWithConfirmation(uri);
                }
            });
        }
        builder.setNegativeButton(R.string.dialog_msg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Show a dialog that shows all bookmarks for the current audio file. If the user clicks on a
     * book mark, the current position of the player is set to that bookmark.
     */
    void showShowBookmarksDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_show_bookmarks, null);
        builder.setView(dialogView);

        mBookmarkListView = dialogView.findViewById(R.id.list_bookmarks);

        // Get the cursor adapter and set it to the list view
        Cursor c = getBookmarks();
        mBookmarkAdapter = new BookmarkCursorAdapter(this, c, mAudioFile.getTime());
        mBookmarkListView.setAdapter(mBookmarkAdapter);

        // Set the EmptyView for the ListView
        TextView emptyTV = dialogView.findViewById(R.id.emptyView_bookmarks);
        mBookmarkListView.setEmptyView(emptyTV);

        // Implement onItemClickListener for the list view
        mBookmarkListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long rowId) {
                // Set the current position of the player to the clicked bookmark
                TextView positionTV = view.findViewById(R.id.bookmark_position_tv);
                String positionString = positionTV.getText().toString();
                int millis = (int)Utils.getMillisFromString(positionString);
                mPlayer.setCurrentPosition(millis);

                // Notify the user about the time jump via a toast
                TextView nameTV = view.findViewById(R.id.bookmark_title_tv);
                String name = nameTV.getText().toString();
                String jumpToast = getResources().getString(R.string.jumped_to_bookmark, name, positionString);
                Toast.makeText(getApplicationContext(), jumpToast, Toast.LENGTH_SHORT).show();
            }
        });

        mBookmarkListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                Uri uri = ContentUris.withAppendedId(AnchorContract.BookmarkEntry.CONTENT_URI, l);
                showSetBookmarkDialog(uri);
                return true;
            }
        });

        builder.setTitle(R.string.bookmarks);
        builder.setPositiveButton(R.string.dialog_msg_close, new DialogInterface.OnClickListener() {
            // User clicked the OK button so set the sleep timer
            public void onClick(DialogInterface dialog, int id) {
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Get a cursor with all bookmarks for the current audio file
     */
    Cursor getBookmarks() {
        String[] projection = {
                AnchorContract.BookmarkEntry._ID,
                AnchorContract.BookmarkEntry.COLUMN_TITLE,
                AnchorContract.BookmarkEntry.COLUMN_POSITION};

        String sel = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
        String[] selArgs = {Long.toString(mAudioFile.getId())};
        String sortOrder = AnchorContract.BookmarkEntry.COLUMN_POSITION + " ASC";
        return getContentResolver().query(AnchorContract.BookmarkEntry.CONTENT_URI, projection, sel, selArgs, sortOrder);
    }

    /**
     * Show the delete bookmark confirmation dialog and let the user decide whether to delete the bookmark
     */
    void deleteBookmarkWithConfirmation(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_bookmark);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Ok" button, so delete the bookmark.
                getContentResolver().delete(uri, null, null);

                // Reload the ListView
                Cursor c = getBookmarks();
                mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
                mBookmarkListView.setAdapter(mBookmarkAdapter);
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}