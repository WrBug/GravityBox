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

import com.wrbug.gravitybox.nougat.ModHwKeys;
import com.wrbug.gravitybox.nougat.R;

import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.graphics.drawable.Drawable;

public class GoHomeShortcut extends AShortcut {
    protected static final String ACTION =  ModHwKeys.ACTION_GO_HOME;
    public static final String ACTION_DEFAULT_URI = "#Intent;action=gravitybox.intent.action.LAUNCH_ACTION;launchFlags=0x10008000;component=com.wrbug.gravitybox.nougat/.shortcuts.LaunchActivity;S.action=gravitybox.intent.action.GO_HOME;S.prefLabel=GravityBox%20Actions%3A%20Go%20home;i.mode=1;S.label=Go%20home;S.iconResName=com.wrbug.gravitybox.nougat%3Adrawable%2Fshortcut_home;S.actionType=broadcast;end";

    public GoHomeShortcut(Context context) {
        super(context);
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.hwkey_action_home);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.shortcut_home, null);
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
    protected ShortcutIconResource getIconResource() {
        return ShortcutIconResource.fromContext(mContext, R.drawable.shortcut_home);
    }

    public static void launchAction(final Context context, Intent intent) {
        Intent launchIntent = new Intent(ACTION);
        context.sendBroadcast(launchIntent);
    }
}
