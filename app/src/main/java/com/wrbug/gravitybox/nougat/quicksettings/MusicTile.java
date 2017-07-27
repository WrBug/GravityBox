/*
 * Copyright (C) 2013 The SlimRoms Project
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wrbug.gravitybox.nougat.quicksettings;

import com.wrbug.gravitybox.nougat.ModHwKeys;
import com.wrbug.gravitybox.nougat.R;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.view.KeyEvent;

@SuppressWarnings("deprecation")
public class MusicTile extends QsTile {
    private boolean mActive = false;
    private boolean mClientIdLost = true;
    private int mMusicTileMode = 3;
    private Metadata mMetadata = new Metadata();
    private AudioManager mAudioManager;
    private RemoteController mRemoteController;

    public MusicTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    private void prepareRemoteController() {
        if (mRemoteController == null) {
            mRemoteController = new RemoteController(mContext, mRCClientUpdateListener);
            mRemoteController.setArtworkConfiguration(100, 80);
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            mAudioManager.registerRemoteController(mRemoteController);
            if (DEBUG) log(getKey() + ": prepareRemoteController");
        }
    }

    private void unprepareRemoteController() {
        if (mRemoteController != null) {
            mAudioManager.unregisterRemoteController(mRemoteController);
        }
        mRemoteController = null;
        mAudioManager = null;
        if (DEBUG) log(getKey() + ": unprepareRemoteController");
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            prepareRemoteController();
        } else {
            unprepareRemoteController();
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
//        final ImageView background =
//                (ImageView) mTile.findViewById(R.id.background);
//        if (background != null) {
//            if (mMetadata.bitmap != null && (mMusicTileMode == 1 || mMusicTileMode == 3)) {
//                background.setImageDrawable(new BitmapDrawable(mResources, mMetadata.bitmap));
//                background.setColorFilter(
//                    Color.rgb(123, 123, 123), android.graphics.PorterDuff.Mode.MULTIPLY);
//            } else {
//                background.setImageDrawable(null);
//                background.setColorFilter(null);
//            }
//        }
        mState.visible = true;
        if (mActive) {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_media_pause);
            mState.label = mMetadata.trackTitle != null && mMusicTileMode > 1
                ? mMetadata.trackTitle : mGbContext.getString(R.string.quick_settings_music_pause);
        } else {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_media_play);
            mState.label = mGbContext.getString(R.string.quick_settings_music_play);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        return true;
    }

    private void playbackStateUpdate(int state) {
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mActive) {
            mActive = active;
            refreshState();
            if (DEBUG) log(getKey() + ": playbackStateUpdate("+state+")");
        }
    }

    private void sendMediaButtonClick(int keyCode) {
        if (!mClientIdLost) {
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } else {
            Intent intent = new Intent(ModHwKeys.ACTION_MEDIA_CONTROL);
            intent.putExtra(ModHwKeys.EXTRA_MEDIA_CONTROL, keyCode);
            mContext.sendBroadcast(intent);
            if (DEBUG) log(getKey() + ": sendMediaButtonClick("+keyCode+") sent as broadcast");
        }
    }

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {
        private String mCurrentTrack = null;
        private Bitmap mCurrentBitmap = null;

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mCurrentTrack = null;
                mCurrentBitmap = null;
                mActive = false;
                mClientIdLost = true;
                refreshState();
                if (DEBUG) log(getKey() + ": onClientChange("+clearing+")");
            }
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            mMetadata.bitmap = data.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK,
                    mMetadata.bitmap);
            mClientIdLost = false;
            if ((mMetadata.trackTitle != null
                    && !mMetadata.trackTitle.equals(mCurrentTrack))
                || (mMetadata.bitmap != null && !mMetadata.bitmap.sameAs(mCurrentBitmap))) {
                mCurrentTrack = mMetadata.trackTitle;
                mCurrentBitmap = mMetadata.bitmap;
                refreshState();
                if (DEBUG) log(getKey() + ": onClientMetadataUpdate");
            }
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        private String trackTitle;
        private Bitmap bitmap;

        public void clear() {
            trackTitle = null;
            bitmap = null;
        }
    }
}
