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

package com.wrbug.gravitybox.nougat.webserviceclient;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.wrbug.gravitybox.nougat.R;

import android.content.Context;
import android.net.Uri;

public class RequestParams {

    private Context mContext;
    private String mUrl;
    private String mAction;
    private Map<String,String> mParams;

    public RequestParams(Context context) {
        mContext = context;
        mUrl = mContext.getString(R.string.url_web_service);
        mParams = new HashMap<>();
    }

    public String getUrl() {
        return mUrl;
    }

    public String getAction() {
        return mAction;
    }

    public void setAction(String action) {
        mAction = action;
        mParams.put("action", mAction);
    }

    public Map<String,String> getParams() {
        return mParams;
    }

    public void addParam(String key, String value) {
        mParams.put(key, value);
    }

    public String getEncodedQuery() {
        Uri.Builder builder = new Uri.Builder();
        for (Entry<String,String> param : mParams.entrySet()) {
            builder.appendQueryParameter(param.getKey(), param.getValue());
        }
        return builder.build().getEncodedQuery();
    }
}
