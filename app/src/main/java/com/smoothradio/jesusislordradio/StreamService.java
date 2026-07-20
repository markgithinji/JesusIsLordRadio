package com.smoothradio.jesusislordradio;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;

public class StreamService extends Service {
    //instances
    Context context;
    EventListener eventListener;
    PlayProcess playProcess;
    ExoPlayer player;
    AudioManager audioManager;
    AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;
    MediaMetadata mediaMetadataOR;

    String url;
    int logoResource;
    Uri link;
    String stationName = "JESUSISLORD RADIO";
    //For Sending Broadcasts
    Intent eventIntent;
    public static final String Event_Change_Action_Name = "JILEventChangeListener";
    Intent metadataIntent;
    public static final String Metadata_Change_Action_Name = "JILMetadataChangeListener";
    String stateChange = "";
    String metadataTitle = "";
    //Notification
    NotificationCompat.Builder notificationBuilder;
    PendingIntent stopPI;
    PendingIntent playPausePI;
    static Boolean isPlaying = false;
    Intent playPauseIntent = new Intent();
    //receivers
    StopPlayReceiver stopPlayReceiver = new StopPlayReceiver();
    StopPlayerFromTimer stopPlayerFromTimerReceiver;
    RestoreUIReceiver restoreUIReceiver;
    PLayPauseReceiver pLayPauseReceiver;
    RequestFocusReceiver requestFocusReceiver;
    public static final String ACTION_START = "SmoothService:Start";
    public static final String ACTION_SHOW_AD = "SmoothService:Stop";
    public static final String EXTRA_RESULT_DATA = "SmoothService:ShowAd";


    public static final String channelId = "serviceChannel";

    @Override
    public void onCreate() {
        super.onCreate();

        playProcess = new PlayProcess();
        eventListener = new EventListener();
        context = this;
//For sending Broadcasts
        eventIntent = new Intent();
        eventIntent.setAction(Event_Change_Action_Name);
        eventIntent.setPackage(getPackageName());
        metadataIntent = new Intent();
        metadataIntent.setAction(Metadata_Change_Action_Name);
        metadataIntent.setPackage(getPackageName());
        //Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "LIVE", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, FLAG_IMMUTABLE);//content action

        String stopAction = "JILstop";
        Intent stopIntent = new Intent();
        stopIntent.setPackage(getPackageName());
        stopIntent.setAction(stopAction);
        stopPI = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);//stop play

        //Pause/Play Notification Button
        String playPauseAction = "JILplaypause";
        playPauseIntent.setAction(playPauseAction);
        playPauseIntent.setPackage(getPackageName());
        playPausePI = PendingIntent.getBroadcast(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE);//pause play

        notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setSound(null)
                .setSmallIcon(R.drawable.exo_icon_play)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.exo_notification_play, "pause", playPausePI)
                .addAction(R.drawable.exo_notification_stop, "Stop", stopPI)
                .setColor(ContextCompat.getColor(context, R.color.notification_color))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0)
                );
        //Receivers
        IntentFilter stopIntentFilter = new IntentFilter();
        stopIntentFilter.addAction(stopAction);

        IntentFilter restoreUIFilter = new IntentFilter();
        restoreUIFilter.addAction("JILgetState");
        restoreUIReceiver = new RestoreUIReceiver();

        IntentFilter stopPlayFromTimer = new IntentFilter();
        stopPlayFromTimer.addAction("JILSetTimer");
        stopPlayerFromTimerReceiver = new StopPlayerFromTimer();

        IntentFilter playPauseFilter = new IntentFilter();
        playPauseFilter.addAction(playPauseAction);
        pLayPauseReceiver = new PLayPauseReceiver();

        IntentFilter requestReceiverFilter = new IntentFilter();
        requestReceiverFilter.addAction("JILRequestAudioFocus");
        requestFocusReceiver = new RequestFocusReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopPlayReceiver, stopIntentFilter, RECEIVER_NOT_EXPORTED);//stop play
            registerReceiver(restoreUIReceiver, restoreUIFilter, RECEIVER_NOT_EXPORTED);//onResume restore UI
            registerReceiver(stopPlayerFromTimerReceiver, stopPlayFromTimer, RECEIVER_NOT_EXPORTED);//stopped From Timer
            registerReceiver(pLayPauseReceiver, playPauseFilter, RECEIVER_NOT_EXPORTED);//play/pause
            registerReceiver(requestFocusReceiver, requestReceiverFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopPlayReceiver, stopIntentFilter);//stop play
            registerReceiver(restoreUIReceiver, restoreUIFilter);//onResume restore UI
            registerReceiver(stopPlayerFromTimerReceiver, stopPlayFromTimer);//stopped From Timer
            registerReceiver(pLayPauseReceiver, playPauseFilter);//play/pause
            registerReceiver(requestFocusReceiver, requestReceiverFilter);
        }

        // AudioFocus
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int i) {
                if (i == AudioManager.AUDIOFOCUS_GAIN) {
                    player.play();
                } else if (i == AudioManager.AUDIOFOCUS_LOSS) {
                    playPauseIntent.putExtra("source", "audiofocus");
                    sendBroadcast(playPauseIntent);
                } else if (i == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    playPauseIntent.putExtra("source", "audiofocus");
                    sendBroadcast(playPauseIntent);
                }
            }
        };
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {

            url = intent.getStringExtra("url");
            if (url == null) {
                url = "";
            }
            logoResource = intent.getIntExtra("logo", 0);

            //Notification
            notificationBuilder.setContentTitle(stationName);
            notificationBuilder.setContentText(stateChange);
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), logoResource));
            startForeground(1, notificationBuilder.build());

            if (intent.getAction().equals(ACTION_START)) {//start play

                ///start play
                playProcess.playNormal();
                isPlaying = true;
                return START_NOT_STICKY;
            } else if (intent.getAction().equals(ACTION_SHOW_AD)) {
                prepareShowAd();
                return Service.START_NOT_STICKY;
            } else {
                throw new IllegalArgumentException("Unexpected action received: " + intent.getAction());
            }
        } else {
            return Service.START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        audioManager.abandonAudioFocus(onAudioFocusChangeListener);
        if (player != null) {
            player.stop();
            player.removeListener(eventListener);
            player.release();
        }
        isPlaying = false;
        stateChange = "";
        eventIntent.putExtra("state", stateChange);
        sendBroadcast(eventIntent);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        unregisterReceiver(stopPlayReceiver);
        unregisterReceiver(stopPlayerFromTimerReceiver);
        unregisterReceiver(restoreUIReceiver);
        unregisterReceiver(pLayPauseReceiver);
        unregisterReceiver(requestFocusReceiver);
    }

    class PlayProcess {
        void playNormal() {
            link = Uri.parse(url);
            stateChange = "Preparing Audio";
            //Set Notification
            notificationBuilder.setContentText(stateChange);
            notificationBuilder.mActions.clear();
            notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
            notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0));//Show only stop Notification Button
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, notificationBuilder.build());

            //Update PLayer Frag
            eventIntent.putExtra("state", stateChange);
            sendBroadcast(eventIntent);
            prepareNormal();
            player.addListener(eventListener);
            player.setWakeMode(C.WAKE_MODE_NETWORK);
            audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            player.play();
        }

        void prepareNormal() {
            if (player != null) {
                player.release();
            }
            MediaItem mediaItem = MediaItem.fromUri(link);
            player = new ExoPlayer.Builder(context).build();
            player.setMediaItem(mediaItem);
            player.prepare();
        }


    }

    void prepareShowAd() {
        audioManager.abandonAudioFocus(onAudioFocusChangeListener);
        if (player != null) {
            player.stop();
            player.release();
        }
        isPlaying = false;
        stateChange = "Preparing Audio";
        eventIntent.putExtra("state", stateChange);
        sendBroadcast(eventIntent);
    }

    class EventListener implements Player.Listener {
        @Override
        public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            getSendMetadata(mediaMetadata);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            int Error = 1000;
            ExoPlaybackException exception = StreamService.this.player.getPlayerError();
            if (exception != null) {
                Error = exception.type;
                switch (Error) {
                    case ExoPlaybackException.TYPE_SOURCE:
                        Toast.makeText(StreamService.this, "station experiencing playback issues", Toast.LENGTH_SHORT).show();
                        break;

                    case ExoPlaybackException.TYPE_UNEXPECTED:
                        Toast.makeText(StreamService.this, "Unexpected Error", Toast.LENGTH_SHORT).show();
                        break;
                }
            }

        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                stateChange = "Playing";
                StreamService.isPlaying = true;
                eventIntent.putExtra("state", stateChange);
                sendBroadcast(eventIntent);
                //Notification
                notificationBuilder.mActions.clear();
                notificationBuilder.setContentText(stateChange);
                notificationBuilder.addAction(R.drawable.exo_notification_pause, "pause", playPausePI);
                notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
                notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1));
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(1, notificationBuilder.build());
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) {
                audioManager.abandonAudioFocus(onAudioFocusChangeListener);
                stateChange = "Buffering";
                eventIntent.putExtra("state", stateChange);
                sendBroadcast(eventIntent);
                //Show only stop Notification Button
                notificationBuilder.mActions.clear();
                notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
                notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0));

            } else if (state == Player.STATE_IDLE) {
                stateChange = "Idle";
                isPlaying = false;
                audioManager.abandonAudioFocus(onAudioFocusChangeListener);
                eventIntent.putExtra("state", stateChange);
                sendBroadcast(eventIntent);
                //Show only stop Notification Button
                notificationBuilder.mActions.clear();
                notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
                notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0));

            } else if (state == Player.STATE_READY) {
                stateChange = "Playing";
                isPlaying = true;
                eventIntent.putExtra("state", stateChange);
                sendBroadcast(eventIntent);
            } else if (state == Player.STATE_ENDED) {
                stateChange = "Ended";
                isPlaying = false;
                audioManager.abandonAudioFocus(onAudioFocusChangeListener);
                eventIntent.putExtra("state", stateChange);
                sendBroadcast(eventIntent);
                Toast.makeText(context, "station experiencing playback issues", Toast.LENGTH_SHORT).show();
                //Show only stop Notification Button
                notificationBuilder.mActions.clear();
                notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
                notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0));
            }
            notificationBuilder.setContentText(stateChange);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, notificationBuilder.build());
        }
    }

    //Broadcast Receivers............
    class PLayPauseReceiver extends BroadcastReceiver {
        String source;

        @Override
        public void onReceive(Context context, Intent intent) {
            source = intent.getStringExtra("source");
            if (isPlaying) {
                player.pause();
                if (source == null) {
                    audioManager.abandonAudioFocus(onAudioFocusChangeListener);
                }
                isPlaying = false;
                //update UI
                if (stateChange.equals("Preparing Audio")) {
                    //Toast.makeText(context, "preparing audio", Toast.LENGTH_SHORT).show();
                } else {
                    stateChange = "Idle";
                    eventIntent.putExtra("state", stateChange);
                    sendBroadcast(eventIntent);
                    //Update Notification
                    notificationBuilder.setContentText("Paused");
                    notificationBuilder.mActions.clear();
                    notificationBuilder.addAction(R.drawable.exo_notification_play, "pause", playPausePI);
                    notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
                    notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1));
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(1, notificationBuilder.build());
                    Toast.makeText(context, "paused", Toast.LENGTH_SHORT).show();
                }
            } else {
                audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                player.play();
                isPlaying = true;
                //Update Notification
                notificationBuilder.setContentText(stateChange);
                notificationBuilder.mActions.clear();
                notificationBuilder.addAction(R.drawable.exo_notification_pause, "pause", playPausePI);
                notificationBuilder.addAction(R.drawable.exo_notification_stop, "Stop", stopPI);
                notificationBuilder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1));
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(1, notificationBuilder.build());
            }
        }
    }


    class StopPlayReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(context, "stopped", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    class StopPlayerFromTimer extends BroadcastReceiver {
        Intent stopPlayFromTimerIntent = new Intent();

        @Override
        public void onReceive(Context context, Intent intent) {
            Long timeInMillis = intent.getLongExtra("timeInMillis", 0);
            stopPlayFromTimerIntent.setAction("JILstop");
            stopPlayFromTimerIntent.setPackage(getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(StreamService.this, 0, stopPlayFromTimerIntent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        }
    }

    class RequestFocusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            player.play();
            audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    class RestoreUIReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent restoreIntent = new Intent();
            restoreIntent.setAction("JILstateFromService");
            restoreIntent.putExtra("stateUI", stateChange);
            restoreIntent.putExtra("info", true);
            restoreIntent.setPackage(getPackageName());
            sendBroadcast(restoreIntent);
            mediaMetadataOR = player.getMediaMetadata();
            getSendMetadata(mediaMetadataOR);
        }
    }

    void getSendMetadata(MediaMetadata mediaMetadata) {
        if (mediaMetadata.title != null && isPlaying) {
            metadataTitle = (String) mediaMetadata.title;
            metadataIntent.putExtra("title", metadataTitle);
            sendBroadcast(metadataIntent);
        }
    }
}