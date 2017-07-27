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

package com.wrbug.gravitybox.nougat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

public class PickImageActivity extends Activity {

    private static final int REQ_PICK_IMAGE = 1;
    private static final int REQ_CROP_IMAGE = 2;
    private static final String ACTION_CROP = "com.android.camera.action.CROP";

    public static final String EXTRA_CROP = "crop";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_SCALE_UP = "scaleUpIfNeeded";
    public static final String EXTRA_ASPECT_X = "aspectX";
    public static final String EXTRA_ASPECT_Y = "aspectY";
    public static final String EXTRA_OUTPUT_X = "outputX";
    public static final String EXTRA_OUTPUT_Y = "outputY";
    public static final String EXTRA_SPOTLIGHT_X = "spotlightX";
    public static final String EXTRA_SPOTLIGHT_Y = "spotlightY";
    public static final String EXTRA_FILE_PATH = "filePath";

    private ProgressDialog mProgressDialog;
    private LoadResult mLoadResult;
    private boolean mCropImage;
    private boolean mScale;
    private boolean mScaleUp;
    private Point mAspectSize;
    private Point mOutputSize;
    private Point mSpotlightSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProgressDialog = new ProgressDialog(PickImageActivity.this);
        mProgressDialog.setMessage(getString(R.string.lc_please_wait));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);

        Intent startIntent = getIntent();
        if (savedInstanceState == null && startIntent != null) {
            mCropImage = startIntent.getBooleanExtra(EXTRA_CROP, false);
            mScale = startIntent.getBooleanExtra(EXTRA_SCALE, false);
            mScaleUp = startIntent.getBooleanExtra(EXTRA_SCALE_UP, false);
            if (startIntent.hasExtra(EXTRA_ASPECT_X) || startIntent.hasExtra(EXTRA_ASPECT_Y)) {
                mAspectSize = new Point(startIntent.getIntExtra(EXTRA_ASPECT_X, 0),
                        startIntent.getIntExtra(EXTRA_ASPECT_Y, 0));
            }
            if (startIntent.hasExtra(EXTRA_OUTPUT_X) || startIntent.hasExtra(EXTRA_OUTPUT_Y)) {
                mOutputSize = new Point(startIntent.getIntExtra(EXTRA_OUTPUT_X, 0),
                        startIntent.getIntExtra(EXTRA_OUTPUT_Y, 0));
            }
            if (startIntent.hasExtra(EXTRA_SPOTLIGHT_X) || startIntent.hasExtra(EXTRA_SPOTLIGHT_Y)) {
                mSpotlightSize = new Point(startIntent.getIntExtra(EXTRA_SPOTLIGHT_X, 0),
                        startIntent.getIntExtra(EXTRA_SPOTLIGHT_Y, 0));
            }

            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.imgpick_dialog_title)),
                    REQ_PICK_IMAGE);
        } else {
            finish();
        }
    }

    @Override
    public void onDestroy() {
        dismissProgressDialog();
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQ_PICK_IMAGE) {
                new ImageLoader().execute(data.getData());
            } else if (requestCode == REQ_CROP_IMAGE) {
                new File(mLoadResult.filePath).delete();
                mLoadResult.filePath += "_cropped";
                setResult(Activity.RESULT_OK, 
                        new Intent().putExtra(EXTRA_FILE_PATH, mLoadResult.filePath));
                finish();
            }
        } else {
            setResult(Activity.RESULT_CANCELED);
            cleanup();
            finish();
        }
    }

    private void onImageLoadedResult(LoadResult result) {
        if (isDestroyed()) {
            cleanup();
            return;
        } else if (result.exception != null) {
            Toast.makeText(this, String.format("%s: %s", getString(R.string.imgpick_choose_error),
                    result.exception.getMessage()), Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            cleanup();
            finish();
            return;
        }

        mLoadResult = result;
        if (mCropImage) {
            cropImage();
        } else {
            setResult(Activity.RESULT_OK, 
                    new Intent().putExtra(EXTRA_FILE_PATH, mLoadResult.filePath));
            finish();
        }
    }

    private void cropImage() {
        try {
            File srcFile = new File(mLoadResult.filePath);
            Uri uri = Uri.fromFile(srcFile);
            Intent cropIntent = new Intent(ACTION_CROP);
            cropIntent.setDataAndType(uri, "image/*");
            cropIntent.putExtra("crop", "true");
            if (mAspectSize != null) {
                cropIntent.putExtra("aspectX", mAspectSize.x);
                cropIntent.putExtra("aspectY", mAspectSize.y);
            }
            if (mOutputSize != null) {
                cropIntent.putExtra("outputX", mOutputSize.x);
                cropIntent.putExtra("outputY", mOutputSize.y);
            }
            if (mSpotlightSize != null) {
                cropIntent.putExtra("spotlightX", mSpotlightSize.x);
                cropIntent.putExtra("spotlightY", mSpotlightSize.y);
            }
            cropIntent.putExtra("scale", mScale);
            cropIntent.putExtra("scaleUpIfNeeded", mScaleUp);
            cropIntent.putExtra("return-data", false);
            cropIntent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());

            File out = new File(getCacheDir() + "/" + srcFile.getName() + "_cropped");
            out.createNewFile();
            out.setReadable(true, false);
            out.setWritable(true, false);
            cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(out));
            startActivityForResult(cropIntent, REQ_CROP_IMAGE);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, String.format("%s: %s", getString(R.string.imgpick_crop_error),
                    e.getMessage()), Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            cleanup();
            finish();
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    private void cleanup() {
        if (mLoadResult != null && mLoadResult.filePath != null) {
            new File(mLoadResult.filePath).delete();
            new File(mLoadResult.filePath + "_cropped").delete();
        }
        mLoadResult = null;
    }

    class LoadResult {
        String filePath;
        Exception exception;
    }

    class ImageLoader extends AsyncTask<Uri, Integer, LoadResult> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        @Override
        protected LoadResult doInBackground(Uri... params) {
            File outFile = new File(getCacheDir() + "/" + UUID.randomUUID().toString());
            LoadResult result = new LoadResult();
            InputStream in = null;
            FileOutputStream out = null;
            try {
                in = getContentResolver().openInputStream(params[0]);
                out = new FileOutputStream(outFile);
                final byte[] buffer = new byte[1024];
                int read;
                while((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                outFile.setReadable(true, false);
                outFile.setWritable(true, false);
                result.filePath = outFile.getAbsolutePath();
            } catch (Exception e) {
                e.printStackTrace();
                result.exception = e;
            } finally {
                try { in.close(); } catch (Exception e) { }
                try { out.close(); } catch (Exception e) { }
            }

            return result;
        }

        @Override
        protected void onPostExecute(LoadResult result) {
            dismissProgressDialog();
            onImageLoadedResult(result);
        }
    }
}
