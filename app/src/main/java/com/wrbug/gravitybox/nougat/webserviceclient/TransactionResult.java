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

public class TransactionResult extends WebServiceResult {

    public enum TransactionStatus { 
        TRANSACTION_VALID,
        TRANSACTION_INVALID,
        TRANSACTION_VIOLATION,
        TRANSACTION_BLOCKED
    }

    private TransactionStatus mTransStatus;

    public TransactionResult(Context context) {
        super(context);
    }

    @Override
    public void setData(JSONObject data) {
        super.setData(data);
        if (mStatus == ResultStatus.ERROR) return;

        try {
            String transStatus = data.getString("trans_status");
            mTransStatus = Enum.valueOf(TransactionStatus.class, transStatus);
        } catch (JSONException e) {
            e.printStackTrace();
            setStatus(ResultStatus.ERROR);
            setMessage(mContext.getString(R.string.wsc_trans_parse_response_error));
        }
    }

    public TransactionStatus getTransactionStatus() {
        return mTransStatus;
    }

    public String getTransactionStatusMessage() {
        switch (mTransStatus) {
            case TRANSACTION_VALID: return mContext.getString(R.string.wsc_trans_valid);
            case TRANSACTION_INVALID: return mContext.getString(R.string.wsc_trans_invalid);
            case TRANSACTION_VIOLATION: return mContext.getString(R.string.wsc_trans_violation);
            case TRANSACTION_BLOCKED: return mContext.getString(R.string.wsc_trans_blocked);
            default: return "N/A";
        }
    }
}
