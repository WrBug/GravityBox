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

import java.util.List;

import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.IIconListAdapterItem;
import com.wrbug.gravitybox.nougat.adapters.IconListAdapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ListView;

public abstract class AMultiShortcut extends AShortcut {

    protected interface ExtraDelegate {
        void addExtraTo(Intent intent);
    }

    public AMultiShortcut(Context context) {
        super(context);
    }

    @Override
    protected ShortcutIconResource getIconResource() {
        return null;
    }

    protected abstract List<IIconListAdapterItem> getShortcutList();

    @Override
    protected void createShortcut(final CreateShortcutListener listener) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.multi_shortcut_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
            .setTitle(getText())
            .setView(view)
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        final AlertDialog dialog = builder.create();

        final CheckBox chkShowToast = (CheckBox) view.findViewById(R.id.chkShowToast);
        chkShowToast.setVisibility(supportsToast() ? View.VISIBLE : View.GONE);
        ListView listView = (ListView) view.findViewById(R.id.listview);
        final List<IIconListAdapterItem> list = getShortcutList();
        listView.setAdapter(new IconListAdapter(mContext, list));
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ShortcutItem item = (ShortcutItem) list.get(position);

                Intent launchIntent = new Intent(mContext, LaunchActivity.class);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                launchIntent.setAction(ShortcutActivity.ACTION_LAUNCH_ACTION);
                launchIntent.putExtra(ShortcutActivity.EXTRA_ACTION, getAction());
                launchIntent.putExtra(ShortcutActivity.EXTRA_ACTION_TYPE, getActionType());
                launchIntent.putExtra(EXTRA_SHOW_TOAST, (supportsToast() && chkShowToast.isChecked()));
                item.addExtraTo(launchIntent);

                Intent intent = new Intent();
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, item.getText());
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, item.getIconResource());

                AMultiShortcut.this.onShortcutCreated(intent, listener);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    static class ShortcutItem implements IIconListAdapterItem {
        private Context mContext;
        private int mLabelResId;
        private int mIconResId;
        private ExtraDelegate mExtraDelegate;

        public ShortcutItem(Context context, int labelResId, int iconResId, ExtraDelegate extraDelegate) {
            mContext = context;
            mLabelResId = labelResId;;
            mIconResId = iconResId;
            mExtraDelegate = extraDelegate;
        }

        @Override
        public String getText() {
            return mContext.getString(mLabelResId);
        }

        @Override
        public String getSubText() {
            return null;
        }

        @Override
        public Drawable getIconLeft() {
            return mContext.getResources().getDrawable(mIconResId, null);
        }

        @Override
        public Drawable getIconRight() {
            return null;
        }

        public ShortcutIconResource getIconResource() {
            return ShortcutIconResource.fromContext(mContext, mIconResId);
        }

        public void addExtraTo(Intent intent) {
            if (mExtraDelegate != null) {
                mExtraDelegate.addExtraTo(intent);
            }
        }
    }
}
