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

package com.wrbug.gravitybox.nougat.ledcontrol;

import java.util.ArrayList;
import java.util.List;

import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.adapters.BaseListAdapterFilter;
import com.wrbug.gravitybox.nougat.adapters.BaseListAdapterFilter.IBaseListAdapterFilterable;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings.LedMode;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

public class LedListAdapter extends ArrayAdapter<LedListItem>
                            implements IBaseListAdapterFilterable<LedListItem> {

    private Context mContext;
    private List<LedListItem> mData = null;
    private List<LedListItem> mFilteredData = null;
    private ListItemActionHandler mActionHandler;
    private android.widget.Filter mFilter;

    protected interface ListItemActionHandler {
        void onItemCheckedChanged(LedListItem item, boolean checked);
    }

    protected LedListAdapter(Context context, List<LedListItem> objects, ListItemActionHandler handler) {
        super(context, R.layout.led_control_list_item, objects);

        mContext = context;
        mData = new ArrayList<LedListItem>(objects);
        mFilteredData = new ArrayList<LedListItem>(objects);
        mActionHandler = handler;
    }

    static class ViewHolder {
        ImageView appIconView;
        TextView appNameView;
        TextView pkgNameView;
        TextView appDescView;
        LedColorView colorView;
        CheckBox enabledView;
        ImageView insistentView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ViewHolder holder = null;

        if(row == null) {
            LayoutInflater inflater = 
                    (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(R.layout.led_control_list_item, parent, false);

            holder = new ViewHolder();
            holder.appIconView = (ImageView) row.findViewById(R.id.led_app_icon);
            holder.appNameView = (TextView) row.findViewById(R.id.led_app_name);
            holder.pkgNameView = (TextView) row.findViewById(R.id.led_pkg_name);
            holder.appDescView = (TextView) row.findViewById(R.id.led_app_desc);
            holder.colorView = (LedColorView) row.findViewById(R.id.led_color);
            holder.enabledView = (CheckBox) row.findViewById(R.id.led_checkbox);
            holder.enabledView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mActionHandler != null) {
                        mActionHandler.onItemCheckedChanged((LedListItem) v.getTag(),
                                ((CheckBox)v).isChecked());
                    }
                }
            });
            holder.insistentView = (ImageView) row.findViewById(R.id.led_alert);

            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        LedListItem item = mFilteredData.get(position);
        holder.appIconView.setImageDrawable(item.getAppIcon());
        holder.appNameView.setText(item.getAppName());
        holder.pkgNameView.setText(item.getAppInfo().packageName);
        holder.pkgNameView.setVisibility(item.getAppName() != null &&
                item.getAppName().equals(item.getAppInfo().packageName) ?
                        View.GONE : View.VISIBLE);
        holder.appDescView.setText(item.getAppDesc());
        holder.colorView.setColor(item.getLedSettings().getColor());
        holder.colorView.setVisibility(item.isEnabled() && 
                item.getLedSettings().getLedMode() == LedMode.OVERRIDE ? View.VISIBLE : View.GONE);
        holder.enabledView.setChecked(item.isEnabled());
        holder.enabledView.setTag(item);
        holder.insistentView.setVisibility(item.isEnabled() && 
                item.getLedSettings().getInsistent() ? 
                View.VISIBLE : View.GONE);

        return row;
    }

    @Override
    public android.widget.Filter getFilter() {
        if(mFilter == null) {
            mFilter = new BaseListAdapterFilter<LedListItem>(this);
        }

        return mFilter;
    }

    @Override
    public List<LedListItem> getOriginalData() {
        return mData;
    }

    @Override
    public List<LedListItem> getFilteredData() {
        return mFilteredData;
    }

    @Override
    public void onFilterPublishResults(List<LedListItem> results) {
        mFilteredData = results;
        clear();
        for (int i = 0; i < mFilteredData.size(); i++) {
            LedListItem item = mFilteredData.get(i);
            add(item);
        }
    }
}
