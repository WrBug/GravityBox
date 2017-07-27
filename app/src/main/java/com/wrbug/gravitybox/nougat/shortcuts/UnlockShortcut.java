/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

import com.wrbug.gravitybox.nougat.R;

import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.graphics.drawable.Drawable;

public class UnlockShortcut extends AShortcut {
    public UnlockShortcut(Context context) {
        super(context);
    }

    @Override
    protected String getActionType() { 
        return "unlock"; 
    }

    @Override
    public String getText() {
        return mContext.getString(R.string.shortcuts_icon_picker_unlock);
    }

    @Override
    public Drawable getIconLeft() {
        return mResources.getDrawable(R.drawable.ic_lockscreen_unlock);
    }

    @Override
    protected String getAction() {
        return null;
    }

    @Override
    protected String getShortcutName() {
        return getText();
    }

    @Override
    protected ShortcutIconResource getIconResource() {
        return ShortcutIconResource.fromContext(mContext, R.drawable.ic_lockscreen_unlock);
    }
}
