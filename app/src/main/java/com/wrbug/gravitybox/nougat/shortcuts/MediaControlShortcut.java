/*
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

package com.wrbug.gravitybox.nougat.shortcuts;

import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.ModHwKeys;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;

public class MediaControlShortcut extends AMultiShortcut {
    protected static final String ACTION =  ModHwKeys.ACTION_MEDIA_CONTROL;

    public MediaControlShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.shortcut_media_control);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_media_play_pause, null);
    }

    @Override
    protected String getAction() {
        return ACTION;
    }

    @Override
    protected String getShortcutName() {
        return getText();
    }

    @Override
    protected List<IIconListAdapterItem> getShortcutList() {
        final List<IIconListAdapterItem> list = new ArrayList<IIconListAdapterItem>();
        list.add(new ShortcutItem(mContext, R.string.shortcut_media_play_pause, 
                R.drawable.shortcut_media_play_pause, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_MEDIA_CONTROL,
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_media_previous,
                R.drawable.shortcut_media_previous, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_MEDIA_CONTROL,
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    }
                }));
        list.add(new ShortcutItem(mContext, R.string.shortcut_media_next,
                R.drawable.shortcut_media_next, new ExtraDelegate() {
                    @Override
                    public void addExtraTo(Intent intent) {
                        intent.putExtra(ModHwKeys.EXTRA_MEDIA_CONTROL,
                                KeyEvent.KEYCODE_MEDIA_NEXT);
                    }
                }));

        return list;
    }

    public static void launchAction(final Context context, Intent intent) {
        Intent launchIntent = new Intent(ACTION);
        launchIntent.putExtra(ModHwKeys.EXTRA_MEDIA_CONTROL,
                intent.getIntExtra(ModHwKeys.EXTRA_MEDIA_CONTROL, 0));
        context.sendBroadcast(launchIntent);
    }
}
