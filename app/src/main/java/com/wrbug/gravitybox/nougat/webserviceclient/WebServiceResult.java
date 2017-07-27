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

package com.wrbug.gravitybox.nougat.webserviceclient;

import org.json.JSONException;
import org.json.JSONObject;

import com.wrbug.gravitybox.nougat.R;
import android.content.Context;

public class WebServiceResult {
    public enum ResultStatus { OK, ERROR };

    protected Context mContext;
    protected String mAction;
    protected ResultStatus mStatus;
    protected String mMessage;

    public WebServiceResult(Context context) {
        mContext = context;
        mStatus = ResultStatus.OK;
    }

    public String getAction() {
        return mAction;
    }

    public void setAction(String action) {
        mAction = action;
    }

    public ResultStatus getStatus() {
        return mStatus;
    }

    public void setStatus(ResultStatus status) {
        mStatus = status;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        mMessage = message;
    }

    public void setData(JSONObject data) {
        try {
            if (data.has("message")) {
                setMessage(data.getString("message"));
            }
            final String status = data.getString("status");
            if (status.equals("INVALID_HASH")) {
                setStatus(ResultStatus.ERROR);
                setMessage(mContext.getString(R.string.wsc_hash_invalid));
            } else if (status.equals("ERROR")) {
                setStatus(ResultStatus.ERROR);
            } else {
                setStatus(ResultStatus.OK);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            setStatus(ResultStatus.ERROR);
            setMessage(mContext.getString(R.string.wsc_parse_response_error));
        }
    }
}
