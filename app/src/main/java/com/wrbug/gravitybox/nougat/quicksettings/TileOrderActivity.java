/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wrbug.gravitybox.nougat.GravityBoxSettings;
import com.wrbug.gravitybox.nougat.R;
import com.wrbug.gravitybox.nougat.TouchInterceptor;
import com.wrbug.gravitybox.nougat.Utils;
import com.wrbug.gravitybox.nougat.ledcontrol.LedSettings;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

public class TileOrderActivity extends ListActivity implements View.OnClickListener {
    private static final String PREF_KEY_TILE_ORDER = "pref_qs_tile_order3";
    public static final String PREF_KEY_TILE_ENABLED = "pref_qs_tile_enabled";
    public static final String PREF_KEY_TILE_LOCKED = "pref_qs_tile_locked";
    public static final String PREF_KEY_TILE_LOCKED_ONLY = "pref_qs_tile_locked_only";
    public static final String PREF_KEY_TILE_SECURED = "pref_qs_tile_secured";
    public static final String PREF_KEY_TILE_DUAL = "pref_qs_tile_dual";
    public static final String EXTRA_QS_ORDER_CHANGED = "qsTileOrderChanged";
    public static final String EXTRA_HAS_MSIM_SUPPORT = "qsHasMsimSupport";
    public static final String EXTRA_IS_OOS_35_ROM = "qsIsOxygenOs35Rom";

    private ListView mTileList;
    private TileAdapter mTileAdapter;
    private Context mContext;
    private Resources mResources;
    private SharedPreferences mPrefs;
    private Map<String, String> mTileTexts;
    private List<TileInfo> mOrderedTileList;
    private Button mBtnSave;
    private Button mBtnCancel;

    class TileInfo {
        String key;
        String name;
        boolean enabled;
        boolean locked;
        boolean lockedOnly;
        boolean secured;
        boolean dual;
        void showMenu(final ListView listView, final View anchorView) {
            final PopupMenu menu = new PopupMenu(listView.getContext(), anchorView);
            menu.inflate(R.menu.tile_menu);
            menu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch(item.getItemId()) {
                        case R.id.tile_dual:
                            dual = !dual;
                            break;
                        case R.id.tile_locked:
                            locked = !locked;
                            if (locked) {
                                secured = true;
                                lockedOnly = false;
                            }
                            break;
                        case R.id.tile_locked_only:
                            lockedOnly = !lockedOnly;
                            break;
                        case R.id.tile_secured:
                            secured = !secured;
                            break;
                    }
                    updateMenu(menu.getMenu());
                    listView.invalidateViews();
                    return true;
                }
            });
            updateMenu(menu.getMenu());
            menu.show();
        }
        private void updateMenu(Menu menu) {
            if (supportsDualMode()) {
                menu.findItem(R.id.tile_dual).setChecked(dual);
            } else {
                menu.removeItem(R.id.tile_dual);
            }
            MenuItem miLocked = menu.findItem(R.id.tile_locked);
            MenuItem miLockedOnly = menu.findItem(R.id.tile_locked_only);
            MenuItem miSecured = menu.findItem(R.id.tile_secured);
            miLocked.setChecked(!locked);
            miLockedOnly.setChecked(lockedOnly);
            miLockedOnly.setEnabled(!locked);
            miSecured.setChecked(!secured && !"gb_tile_lock_screen".equals(key));
            miSecured.setEnabled(!locked && !"gb_tile_lock_screen".equals(key));
        }
        private boolean supportsDualMode() {
            return !isOxygenOs35Rom() &&
                    ("aosp_tile_cell".equals(key) ||
                    "aosp_tile_wifi".equals(key) ||
                    "aosp_tile_bluetooth".equals(key) ||
                    "gb_tile_gps_slimkat".equals(key));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(R.style.AppThemeDark);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.order_tile_list_activity);

        mContext = this;
        mResources = mContext.getResources();
        final String prefsName = mContext.getPackageName() + "_preferences";
        mPrefs = mContext.getSharedPreferences(prefsName, Context.MODE_WORLD_READABLE);

        mBtnSave = (Button) findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(this);
        mBtnCancel = (Button) findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(this);

        mTileList = getListView();
        ((TouchInterceptor) mTileList).setDropListener(mDropListener);
        mTileAdapter = new TileAdapter(mContext);

        String[] allTileKeys = mResources.getStringArray(R.array.qs_tile_values);
        String[] allTileNames = mResources.getStringArray(R.array.qs_tile_entries);
        mTileTexts = new HashMap<String, String>();
        for (int i = 0; i < allTileKeys.length; i++) {
            mTileTexts.put(allTileKeys[i], allTileNames[i]);
        }

        if (mPrefs.getString(PREF_KEY_TILE_ORDER, null) == null) {
            createDefaultTileList();
        } else {
            updateDefaultTileList();
        }
    }

    private boolean isOxygenOs35Rom() {
        if (GravityBoxSettings.sSystemProperties != null)
            return GravityBoxSettings.sSystemProperties.isOxygenOs35Rom;
        else if (getIntent() != null && getIntent().hasExtra(EXTRA_IS_OOS_35_ROM))
            return getIntent().getBooleanExtra(EXTRA_IS_OOS_35_ROM, false);
        else
            return false;
    }

    private boolean hasMsimSupport() {
        if (GravityBoxSettings.sSystemProperties != null)
            return GravityBoxSettings.sSystemProperties.hasMsimSupport;
        else if (getIntent() != null && getIntent().hasExtra(EXTRA_HAS_MSIM_SUPPORT))
            return getIntent().getBooleanExtra(EXTRA_HAS_MSIM_SUPPORT, false);
        else
            return false;
    }

    public static String getDefaultTileList(Context gbContext) {
        try {
            return Utils.join(gbContext.getResources().getStringArray(
                    R.array.qs_tile_default_values), ",");
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    private void createDefaultTileList() {
        String[] tileKeys = mResources.getStringArray(R.array.qs_tile_values);
        String newList = "";

        for (String key : tileKeys) {
            if (supportedTile(key)) {
                if (!newList.isEmpty()) newList += ",";
                newList += key;
            }
        }

        mPrefs.edit().putString(PREF_KEY_TILE_ORDER, newList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_ENABLED,Utils.join(mResources.getStringArray(
                R.array.qs_tile_default_values), ",")).commit();
    }

    private void updateDefaultTileList() {
        List<String> list = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_ORDER, "").split(",")));
        List<String> enabledList = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_ENABLED, "").split(",")));
        boolean listChanged = false;
        boolean enabledListChanged = false;
        String[] tileKeys = mResources.getStringArray(R.array.qs_tile_values);
        for (String key : tileKeys) {
            if (supportedTile(key)) {
                if (!list.contains(key)) {
                    list.add(key);
                    listChanged = true;
                }
            } else {
                if (list.contains(key)) {
                    list.remove(key);
                    listChanged = true;
                }
                if (enabledList.contains(key)) {
                    enabledList.remove(key);
                    enabledListChanged = true;
                }
            }
        }
        if (listChanged) {
            mPrefs.edit().putString(PREF_KEY_TILE_ORDER, Utils.join(
                    list.toArray(new String[list.size()]), ",")).commit();
        }
        if (enabledListChanged) {
            mPrefs.edit().putString(PREF_KEY_TILE_ENABLED, Utils.join(
                    enabledList.toArray(new String[enabledList.size()]), ",")).commit();
        }
    }

    private boolean supportedTile(String key) {
        // TODO: Music Tile
        if (key.equals("gb_tile_music"))
            return false;
        if (key.equals("gb_tile_torch") && !Utils.hasFlash(mContext))
            return false;
        if (key.equals("gb_tile_gps_alt") && !Utils.hasGPS(mContext))
            return false;
        if ((key.equals("aosp_tile_cell") || key.equals("aosp_tile_cell2") ||
                key.equals("gb_tile_network_mode") || key.equals("gb_tile_smart_radio") ||
                key.equals("mtk_tile_mobile_data")) && Utils.isWifiOnly(mContext))
            return false;
        if (key.equals("gb_tile_nfc") && !Utils.hasNfc(mContext))
            return false;
        if (key.equals("gb_tile_quiet_hours") && LedSettings.isUncLocked(mContext))
            return false;
        if (key.equals("gb_tile_compass") && !Utils.hasCompass(mContext))
            return false;
        if (key.equals("aosp_tile_cell2") && (!Utils.isMotoXtDevice() || !hasMsimSupport()))
            return false;
        if ((key.equals("mtk_tile_mobile_data") || key.equals("mtk_tile_audio_profile")
                || key.equals("mtk_tile_hotknot") || key.equals("mtk_tile_timeout")) &&
                !Utils.isMtkDevice())
            return false;
        if (key.equals("gb_tile_smart_radio") && !mPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false))
            return false;
        if ((key.startsWith("xperia_tile") && !Utils.isXperiaDevice()) ||
               key.equals("xperia_tile_stamina"))
            return false;
        if (key.equals("aosp_tile_hotspot") && Utils.isXperiaDevice())
            return false;
        if (key.startsWith("moto_tile") && !Utils.isMotoXtDevice())
            return false;
        if (key.startsWith("op3t_tile") && !isOxygenOs35Rom())
            return false;
        if ((key.equals("gb_tile_battery") || key.equals("aosp_tile_dnd")) && 
                isOxygenOs35Rom())
            return false;

        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        mOrderedTileList = getOrderedTileList();
        setListAdapter(mTileAdapter);
    }

    @Override
    public void onStop() {
        super.onStop();
        setListAdapter(null);
        mOrderedTileList = null;
    }

    @Override
    public void onDestroy() {
        ((TouchInterceptor) mTileList).setDropListener(null);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        // reload our tiles and invalidate the views for redraw
        mTileList.invalidateViews();
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnSave) {
            saveOrderedTileList();
            finish();
        } else if (v == mBtnCancel) {
            finish();
        }
    }

    private TouchInterceptor.DropListener mDropListener = new TouchInterceptor.DropListener() {
        public void drop(int from, int to) {
            // move the tile
            if (from < mOrderedTileList.size()) {
                TileInfo tile = mOrderedTileList.remove(from);
                if (to <= mOrderedTileList.size()) {
                    mOrderedTileList.add(to, tile);
                    mTileList.invalidateViews();
                }
            }
        }
    };

    private List<TileInfo> getOrderedTileList() {
        String[] orderedTiles = mPrefs.getString(PREF_KEY_TILE_ORDER, "").split(",");
        List<String> enabledTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_ENABLED, "").split(",")));
        List<String> lockedTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_LOCKED, "").split(",")));
        List<String> lockedOnlyTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_LOCKED_ONLY, "").split(",")));
        List<String> securedTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_SECURED, "").split(",")));
        List<String> dualTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(PREF_KEY_TILE_DUAL,
                        "aosp_tile_wifi,aosp_tile_bluetooth").split(",")));

        List<TileInfo> tiles = new ArrayList<TileInfo>();
        for (int i = 0; i < orderedTiles.length; i++) {
            TileInfo ti = new TileInfo();
            ti.key = orderedTiles[i];
            ti.name = mTileTexts.get(ti.key);
            ti.enabled = enabledTiles.contains(ti.key);
            ti.locked = lockedTiles.contains(ti.key);
            ti.lockedOnly = lockedOnlyTiles.contains(ti.key);
            ti.secured = securedTiles.contains(ti.key);
            ti.dual = dualTiles.contains(ti.key);
            tiles.add(ti);
        }

        return tiles;
    }

    private void saveOrderedTileList() {
        String newOrderedList = "";
        String newEnabledList = "";
        String newLockedList = "";
        String newLockedOnlyList = "";
        String newSecuredList = "";
        String newDualList = "";

        for (TileInfo ti : mOrderedTileList) {
            if (!newOrderedList.isEmpty()) newOrderedList += ",";
            newOrderedList += ti.key;

            if (ti.locked) {
                if (!newLockedList.isEmpty()) newLockedList += ",";
                newLockedList += ti.key;
            }

            if (ti.lockedOnly) {
                if (!newLockedOnlyList.isEmpty()) newLockedOnlyList += ",";
                newLockedOnlyList += ti.key;
            }

            if (ti.enabled) {
                if (!newEnabledList.isEmpty()) newEnabledList += ",";
                newEnabledList += ti.key;
            }

            if (ti.secured) {
                if (!newSecuredList.isEmpty()) newSecuredList += ",";
                newSecuredList += ti.key;
            }

            if (ti.dual) {
                if (!newDualList.isEmpty()) newDualList += ",";
                newDualList += ti.key;
            }
        }

        mPrefs.edit().putString(PREF_KEY_TILE_ORDER, newOrderedList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_ENABLED, newEnabledList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_LOCKED, newLockedList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_LOCKED_ONLY, newLockedOnlyList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_SECURED, newSecuredList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_DUAL, newDualList).commit();
        Intent intent = new Intent(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED);
        intent.putExtra(EXTRA_QS_ORDER_CHANGED, true);
        mContext.sendBroadcast(intent);
    }

    private class TileAdapter extends BaseAdapter {
        private Context mContext;
        private LayoutInflater mInflater;

        public TileAdapter(Context c) {
            mContext = c;
            mInflater = LayoutInflater.from(mContext);
        }

        public int getCount() {
            return mOrderedTileList.size();
        }

        public Object getItem(int position) {
            return mOrderedTileList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final View itemView;
            final TileInfo tileInfo = mOrderedTileList.get(position);

            if (convertView == null) {
                itemView = mInflater.inflate(R.layout.order_tile_list_item, null);
                final CheckBox enabled = (CheckBox) itemView.findViewById(R.id.chkEnable);
                final ImageView menu = (ImageView) itemView.findViewById(R.id.menu);
                enabled.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TileInfo ti = (TileInfo) itemView.getTag();
                        ti.enabled = ((CheckBox)v).isChecked();
                        menu.setEnabled(ti.enabled);
                        mTileList.invalidateViews();
                    }
                });
                menu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TileInfo ti = (TileInfo) itemView.getTag();
                        ti.showMenu(mTileList, menu);
                    }
                });
            } else {
                itemView = convertView;
            }

            itemView.setTag(tileInfo);
            final TextView name = (TextView) itemView.findViewById(R.id.name);
            final TextView info = (TextView) itemView.findViewById(R.id.info);
            final CheckBox enabled = (CheckBox) itemView.findViewById(R.id.chkEnable);
            final ImageView menu = (ImageView) itemView.findViewById(R.id.menu);
            name.setText(tileInfo.name);
            if (tileInfo.enabled) {
                String txt = (tileInfo.locked ? getText(R.string.tile_hidden_locked) :
                    tileInfo.secured ? getText(R.string.tile_hidden_secured) : "").toString();
                if (tileInfo.lockedOnly) {
                    if (!txt.isEmpty()) txt += "; ";
                    txt += getText(R.string.tile_shown_locked_only);
                }
                info.setText(txt);
            } else {
                info.setText(null);
            }
            info.setVisibility(info.getText() == null || info.getText().length() == 0 ?
                    View.GONE : View.VISIBLE);
            enabled.setChecked(tileInfo.enabled);
            menu.setVisibility(tileInfo.enabled ? View.VISIBLE : View.INVISIBLE);

            return itemView;
        }
    }
}
