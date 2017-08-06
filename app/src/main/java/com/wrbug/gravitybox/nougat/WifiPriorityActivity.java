/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wrbug.gravitybox.nougat;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.wrbug.gravitybox.nougat.util.SharedPreferencesUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiPriorityActivity extends ListActivity implements GravityBoxResultReceiver.Receiver {

    public static final String PREF_KEY_WIFI_TRUSTED = "pref_wifi_trusted";
    public static final String ACTION_WIFI_TRUSTED_CHANGED = "gravitybox.intent.action.WIFI_TRUSTED_CHANGED";
    public static final String EXTRA_WIFI_TRUSTED = "wifiTrusted";

    private final TouchInterceptor.DropListener mDropListener =
            new TouchInterceptor.DropListener() {
                public void drop(int from, int to) {
                    if (from == to) return;

                    // Sort networks by user selection
                    List<WifiNetwork> networks = mAdapter.getNetworks();
                    WifiNetwork o = networks.remove(from);
                    networks.add(to, o);

                    // Set the new priorities of the networks
                    int cc = networks.size();
                    ArrayList<WifiConfiguration> configList = new ArrayList<>();
                    for (int i = 0; i < cc; i++) {
                        WifiNetwork network = networks.get(i);
                        network.config.priority = cc - i;
                        configList.add(network.config);
                    }

                    mNetworksListView.invalidateViews();

                    Intent intent = new Intent(ModHwKeys.ACTION_UPDATE_WIFI_CONFIG);
                    intent.putParcelableArrayListExtra(ModHwKeys.EXTRA_WIFI_CONFIG_LIST, configList);
                    intent.putExtra("receiver", mReceiver);
                    WifiPriorityActivity.this.sendBroadcast(intent);
                }
            };

    private WifiManager mWifiManager;
    private TouchInterceptor mNetworksListView;
    private WifiPriorityAdapter mAdapter;
    private SharedPreferences mPrefs;
    private GravityBoxResultReceiver mReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            this.setTheme(R.style.AppThemeDark);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_network_priority);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        String prefsName = getPackageName() + "_preferences";
        mPrefs = SharedPreferencesUtils.getSharedPreferences(this, prefsName);

        // Set the touchable listview
        mNetworksListView = (TouchInterceptor) getListView();
        mNetworksListView.setDropListener(mDropListener);
        mAdapter = new WifiPriorityAdapter(this, mWifiManager);
        setListAdapter(mAdapter);

        mReceiver = new GravityBoxResultReceiver(new Handler());
        mReceiver.setReceiver(this);
    }

    @Override
    public void onDestroy() {
        mNetworksListView.setDropListener(null);
        setListAdapter(null);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Reload the networks
        mAdapter.reloadNetworks();
        mNetworksListView.invalidateViews();
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        // Reload the networks
        mAdapter.reloadNetworks();
        mNetworksListView.invalidateViews();
    }

    private class WifiNetwork {
        WifiConfiguration config;
        boolean trusted;

        WifiNetwork(WifiConfiguration c) {
            config = c;
        }
    }

    private class WifiPriorityAdapter extends BaseAdapter {

        private final WifiManager mWifiManager;
        private final LayoutInflater mInflater;
        private List<WifiNetwork> mNetworks;

        public WifiPriorityAdapter(Context ctx, WifiManager wifiManager) {
            mWifiManager = wifiManager;
            mInflater = LayoutInflater.from(ctx);
            reloadNetworks();
        }

        private void reloadNetworks() {
            mNetworks = new ArrayList<WifiNetwork>();
            List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
            if (networks == null) return;

            // Sort network list by priority (or by network id if the priority is the same)
            Collections.sort(networks, new Comparator<WifiConfiguration>() {
                @Override
                public int compare(WifiConfiguration lhs, WifiConfiguration rhs) {
                    // > priority -- > lower position
                    if (lhs.priority < rhs.priority) return 1;
                    if (lhs.priority > rhs.priority) return -1;
                    // < network id -- > lower position
                    if (lhs.networkId < rhs.networkId) return -1;
                    if (lhs.networkId > rhs.networkId) return 1;
                    return 0;
                }
            });

            // read trusted SSIDs from prefs
            Set<String> trustedNetworks = mPrefs.getStringSet(PREF_KEY_WIFI_TRUSTED,
                    new HashSet<String>());
            for (WifiConfiguration c : networks) {
                WifiNetwork wn = new WifiNetwork(c);
                wn.trusted = trustedNetworks.contains(filterSSID(c.SSID));
                mNetworks.add(wn);
            }

            // remove forgotten networks from trusted list
            boolean shouldUpdatePrefs = false;
            for (String ssid : trustedNetworks) {
                if (!containsNetwork(ssid)) {
                    shouldUpdatePrefs = true;
                    break;
                }
            }
            if (shouldUpdatePrefs) {
                saveTrustedNetworks();
            }
        }

        private void saveTrustedNetworks() {
            Set<String> trustedNetworks = new HashSet<String>();
            for (WifiNetwork wn : mNetworks) {
                if (wn.trusted) {
                    trustedNetworks.add(filterSSID(wn.config.SSID));
                }
            }
            mPrefs.edit().putStringSet(PREF_KEY_WIFI_TRUSTED, trustedNetworks).commit();

            Intent intent = new Intent(ACTION_WIFI_TRUSTED_CHANGED);
            intent.putExtra(EXTRA_WIFI_TRUSTED, trustedNetworks.toArray(
                    new String[trustedNetworks.size()]));
            sendBroadcast(intent);
        }

        private boolean containsNetwork(String ssid) {
            for (WifiNetwork wn : mNetworks) {
                if (ssid.equals(filterSSID(wn.config.SSID))) {
                    return true;
                }
            }
            return false;
        }

        List<WifiNetwork> getNetworks() {
            return mNetworks;
        }

        @Override
        public int getCount() {
            return mNetworks.size();
        }

        @Override
        public Object getItem(int position) {
            return mNetworks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View v;
            if (convertView == null) {
                v = mInflater.inflate(R.layout.wifi_network_priority_list_item, null);
                final CheckBox trusted = (CheckBox) v.findViewById(R.id.chkTrusted);
                trusted.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View cv) {
                        WifiNetwork wn = (WifiNetwork) v.getTag();
                        wn.trusted = ((CheckBox) cv).isChecked();
                        saveTrustedNetworks();
                        mNetworksListView.invalidateViews();
                    }
                });
            } else {
                v = convertView;
            }

            WifiNetwork network = (WifiNetwork) getItem(position);
            v.setTag(network);

            final TextView name = (TextView) v.findViewById(R.id.name);
            // wpa_suplicant returns the SSID between double quotes. Remove them if are present.
            name.setText(filterSSID(network.config.SSID));
            final CheckBox trusted = (CheckBox) v.findViewById(R.id.chkTrusted);
            trusted.setChecked(network.trusted);
            final TextView info = (TextView) v.findViewById(R.id.info);
            info.setVisibility(network.trusted ? View.VISIBLE : View.GONE);

            return v;
        }

        private String filterSSID(String ssid) {
            // Filter only if has start and end double quotes
            if (ssid == null || !ssid.startsWith("\"") || !ssid.endsWith("\"")) {
                return ssid;
            }
            return ssid.substring(1, ssid.length() - 1);
        }
    }
}
