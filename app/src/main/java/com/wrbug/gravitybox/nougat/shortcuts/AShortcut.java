/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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

import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;

import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public abstract class AShortcut implements IIconListAdapterItem {

    public static final String EXTRA_ENABLE = "enable";
    public static final String EXTRA_SHOW_TOAST = "showToast";

    protected Context mContext;
    protected Resources mResources;

    public interface CreateShortcutListener {
        void onShortcutCreated(Intent intent);
    }

    public AShortcut(Context context) {
        mContext = context;
        mResources = mContext.getResources();
    }

    @Override
    public abstract String getText();

    @Override
    public String getSubText() {
        return null;
    }

    @Override
    public abstract Drawable getIconLeft();

    @Override
    public Drawable getIconRight() {
        return null;
    }

    protected abstract String getAction();

    // Default action type is broadcast. Can be overriden.
    protected String getActionType() { return "broadcast"; }

    protected abstract String getShortcutName();
    protected abstract ShortcutIconResource getIconResource();

    protected boolean supportsToast() {
        return false;
    }

    protected void createShortcut(CreateShortcutListener listener) {
        Intent launchIntent = new Intent(mContext, LaunchActivity.class);
        launchIntent.setAction(ShortcutActivity.ACTION_LAUNCH_ACTION);
        launchIntent.putExtra(ShortcutActivity.EXTRA_ACTION, getAction());
        launchIntent.putExtra(ShortcutActivity.EXTRA_ACTION_TYPE, getActionType());
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getShortcutName());
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, getIconResource());

        // descendants can override this to supply additional data
        onShortcutCreated(intent, listener);
    }

    protected void onShortcutCreated(Intent intent, CreateShortcutListener listener) {
        if (listener != null) {
            listener.onShortcutCreated(intent);
        }
    }
}
