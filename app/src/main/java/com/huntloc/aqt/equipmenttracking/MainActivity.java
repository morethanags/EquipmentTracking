package com.huntloc.aqt.equipmenttracking;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    static final int REQUEST_IMAGE_CAPTURE = 11;
    final int REQUEST_WRITE_EXTERNAL_STORAGE = 10;
    final int REQUEST_CAMERA = 11;
    Button saveButton, cancelButton, scanButton;
    Spinner type, location, status, condition;
    ImageView photo;
    JSONObject equipment;
    private NfcAdapter mNfcAdapter;
    private EditText mTagId, mServiceTag, mUserName;
    private String pictureImagePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTagId = (EditText) findViewById(R.id.editText_TagId);
        mServiceTag = (EditText) findViewById(R.id.editText_ServiceTagId);
        mUserName = (EditText) findViewById(R.id.editText_UserName);
        saveButton = (Button) findViewById(R.id.ib_save);
        cancelButton = (Button) findViewById(R.id.ib_cancel);
        photo = (ImageView) findViewById(R.id.imageView_photo);
        scanButton = (Button) findViewById(R.id.ib_scan);
        scanButton.setOnClickListener(this);
        saveButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);
        photo.setOnClickListener(this);
        photo.setImageResource(R.drawable.im_nophotoavailable);
        type = (Spinner) findViewById(R.id.spinner_type);
        location = (Spinner) findViewById(R.id.spinner_location);
        status = (Spinner) findViewById(R.id.spinner_status);
        condition = (Spinner) findViewById(R.id.spinner_condition);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        }
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!mNfcAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable NFC.", Toast.LENGTH_LONG)
                    .show();
        }
        String serverURL = getResources().getString(R.string.url_path);
        new QueryTypesTask().execute("typeId", R.id.spinner_type + "", serverURL + "/Types");
        new QueryTypesTask().execute("locationId", R.id.spinner_location + "", serverURL + "/Locations");
        new QueryTypesTask().execute("statusId", R.id.spinner_status + "", serverURL + "/Status");
        new QueryTypesTask().execute("conditionId", R.id.spinner_condition + "", serverURL + "/Conditions");

        equipment = new JSONObject();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupForegroundDispatch(this, mNfcAdapter);
    }

    @Override
    protected void onPause() {
        stopForegroundDispatch(this, mNfcAdapter);
        super.onPause();
    }

    public void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(),
                activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        final PendingIntent pendingIntent = PendingIntent.getActivity(
                activity.getApplicationContext(), 0, intent, 0);
        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};
        filters[0] = new IntentFilter();
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);

        filters[0].addAction(NfcAdapter.ACTION_TAG_DISCOVERED);

        /*filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
         try {
             filters[0].addDataType(MIME_TEXT_PLAIN);
         } catch (IntentFilter.MalformedMimeTypeException e) {
             throw new RuntimeException("Check your mime type.");
         }*/

       /* filters[0].addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        techList = new String[][]{new String[]{NfcA.class.getName()}, new String[]{MifareClassic.class.getName()}, new String[]{NdefFormatable.class.getName()}};*/

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    public void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        Log.d("action", action);

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            clear();
            Parcelable parcelable = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Tag tag = (Tag) parcelable;
            byte[] id = tag.getId();
            String code = getDec(id) + "";
            Log.d("Internal Code", code);
            mTagId.setText(code);
            load();
        }

         /*   if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
                NdefMessage ndefMessage = null;
                Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                if ((rawMessages != null) && (rawMessages.length > 0)) {
                    ndefMessage = (NdefMessage) rawMessages[0];
                    String result = "";
                    byte[] payload = ndefMessage.getRecords()[0].getPayload();
                    String textEncoding = ((payload[0] & 0200) == 0) ? "UTF-8" : "UTF-16";
                    int languageCodeLength = payload[0] & 0077;
                    //String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
                    String text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                    Log.d("Internal Code", text);
                    setCredentialId(text);

                }
            }*/

       /* if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Parcelable parcelable = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Tag tag = (Tag) parcelable;
            byte[] id = tag.getId();
            String code = getDec(id) + "";
            Log.d("Internal Code", code);
            setCredentialId(code);

        }*/

    }

    private void load() {
        new LoadEquipmentTask().execute(getResources().getString(R.string.url_path) + "/Equipments/" + mTagId.getText());
    }

    private void show(JSONObject jsonObject) {

        if (jsonObject != null && !jsonObject.isNull("equipmentId")) {

            Snackbar snackbar = Snackbar
                    .make(this.findViewById(R.id.mainCoordinatorLayout), "NFC already registered!", Snackbar.LENGTH_LONG);
            snackbar.show();
            Log.d("jsonObject", jsonObject.toString());
            try {
                this.equipment.put("equipmentId", jsonObject.optString("equipmentId"));
                this.equipment.put("base64Picture", jsonObject.optString("base64Picture"));
                this.equipment.put("userName", jsonObject.optString("userName"));

            } catch (JSONException jsone) {
            }
            mServiceTag.setText(jsonObject.optString("serviceTag"));
            mUserName.setText(jsonObject.optString("userName"));
            setSelected(jsonObject.optInt("typeId"), type);
            setSelected(jsonObject.optInt("locationId"), location);
            setSelected(jsonObject.optInt("conditionId"), condition);
            setSelected(jsonObject.optInt("statusId"), status);

            if (!jsonObject.isNull("base64Picture") && !jsonObject.optString("base64Picture").equals("null")) {
                byte[] byteArray;
                Bitmap bitmap;
                byteArray = Base64
                        .decode(jsonObject.optString("base64Picture"), 0);
                bitmap = BitmapFactory.decodeByteArray(byteArray, 0,
                        byteArray.length);
                photo.setImageBitmap(bitmap);
            } else {
                photo.setImageResource(R.drawable.im_nophotoavailable);
            }
        }
    }

    private void setSelected(int typeId, Spinner spinner) {
        Adapter typeAdapter = spinner.getAdapter();
        for (int i = 0; i < typeAdapter.getCount(); i++) {
            Type _type = (Type) typeAdapter.getItem(i);
            if (_type.getTypeId() == typeId) {
                spinner.setSelection(i);
            }
        }
    }

    private long getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ib_save:
                save();
                break;
            case R.id.ib_cancel:
                clear();
                break;

            case R.id.imageView_photo:
                pickImage();
                break;
            case R.id.ib_scan:
                scan();
                break;
        }
    }

    private void save() {
        try {
            if (TextUtils.isEmpty(mTagId.getText().toString().trim())) {
                mTagId.setError("Tap a NFC tag");
                return;
            }
            if (TextUtils.isEmpty(mServiceTag.getText().toString().trim())) {
                mServiceTag.setError("Enter service tag");
                return;
            }
            this.equipment.put("Nfctag", mTagId.getText());
            this.equipment.put("ServiceTag", mServiceTag.getText());
            this.equipment.put("UserName", mUserName.getText());

            this.equipment.put("TypeId", ((Type) type.getSelectedItem()).getTypeId());
            this.equipment.put("StatusId", ((Type) status.getSelectedItem()).getTypeId());

            this.equipment.put("ConditionId", ((Type) condition.getSelectedItem()).getTypeId());
            this.equipment.put("LocationId", ((Type) location.getSelectedItem()).getTypeId());
            if (this.equipment.isNull("equipmentId")) {
                new SaveEquipmentTask().execute(getResources().getString(R.string.url_path) + "/Equipments");
            } else {
                new SaveEquipmentTask().execute(getResources().getString(R.string.url_path) + "/Equipments/" + equipment.optString("equipmentId"));
            }

        } catch (JSONException jsone) {

        }

    }

    private void clear() {
        mTagId.setText(null);
        mTagId.setError(null);
        mServiceTag.setText(null);
        type.setSelection(0);
        location.setSelection(0);
        status.setSelection(0);
        condition.setSelection(0);
        mUserName.setText(null);
        photo.setImageResource(R.drawable.im_nophotoavailable);
        try {
            this.equipment.put("equipmentId", null);
        } catch (JSONException je) {
        }
    }

    private void scan() {

        Intent intent = new Intent(
                "com.google.zxing.client.android.SCAN");
        startActivityForResult(intent,
                IntentIntegrator.REQUEST_CODE);


    }

    private void pickImage() {
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String imageFileName = timeStamp + ".jpg";
        pictureImagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath() + "/" + imageFileName;
        //pictureImagePath = Environment.getExternalStorageDirectory().toString()+"/Pictures";
        Log.d("path", pictureImagePath);
        File file = new File(pictureImagePath);
        Uri outputFileUri = Uri.fromFile(file);
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
        startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Log.d("resultCode", resultCode + "");
        if (resultCode == 0) {
            return;
        }
        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(
                    requestCode, resultCode, intent);
            if (scanResult != null) {
                this.mServiceTag.setText(scanResult.getContents());
            }
        }
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            try {
                File imgFile = new File(pictureImagePath);
                Log.d("exists", imgFile.exists() + "");
                if (imgFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                    Bitmap resized = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * 0.5), (int) (bitmap.getHeight() * 0.5), true);
                    photo.setImageBitmap(resized);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    resized.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                    Log.d("Encoded", encoded);
                    this.equipment.put("Base64Picture", encoded);
                }

            } catch (Exception e) {
                Log.d("Exception", e.toString());
            }
        }
    }

    private void showTypes(String id, String spinner_id, JSONArray jsonArray) {
        List<Type> list = new ArrayList<>();
        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(new Type(jsonArray.getJSONObject(i).getString("description"), jsonArray.getJSONObject(i).getInt(id)));
            }
        } catch (Exception e) {
        }
        ArrayAdapter<Type> adapter = new ArrayAdapter<Type>(getApplicationContext(), android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(Integer.parseInt(spinner_id))).setAdapter(adapter);
    }

    private class QueryTypesTask extends AsyncTask<String, String, String[]> {
        HttpURLConnection urlConnection;

        @SuppressWarnings("unchecked")
        protected String[] doInBackground(String... args) {
            String[] toReturn = new String[3];
            StringBuilder result = new StringBuilder();
            try {
                toReturn[0] = args[0];
                toReturn[1] = args[1];
                URL url = new URL(args[2]);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                int code = urlConnection.getResponseCode();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                toReturn[2] = result.toString();
            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            } finally {
                urlConnection.disconnect();
            }
            return toReturn;
        }

        protected void onPostExecute(String[] result) {
            try {
                if (result != null && !result[1].equals("")) {
                    JSONArray jsonResponse = new JSONArray(result[2]);
                    if (jsonResponse.length() > 0) {
                        MainActivity.this.showTypes(result[0], result[1], jsonResponse);
                        return;
                    }
                }
            } catch (Exception ex) {
                Log.d("Exception", ex.getMessage());
            }
        }
    }

    private class SaveEquipmentTask extends AsyncTask<String, String, String> {
        HttpURLConnection urlConnection;

        @SuppressWarnings("unchecked")
        protected String doInBackground(String... args) {
            StringBuilder result = new StringBuilder();
            try {
                URL url = new URL(args[0]);
                Log.d("URL", url + ".");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                OutputStream os = urlConnection.getOutputStream();
                os.write(equipment.toString().getBytes("UTF-8"));
                os.close();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            } catch (IOException e) {
                Snackbar snackbar = Snackbar
                        .make(MainActivity.this.findViewById(R.id.mainCoordinatorLayout), "Couldn't save equipment! Check network!", Snackbar.LENGTH_LONG);
                snackbar.show();
            } finally {
                urlConnection.disconnect();
            }
            return result.toString();
        }

        protected void onPostExecute(String result) {
            try {
                if (result != null && !result.equals("")) {
                    Log.d("Result", result);
                    JSONObject jsonResponse = new JSONObject(result);
                    if (!jsonResponse.isNull("equipmentId")) {
                        Snackbar snackbar = Snackbar
                                .make(MainActivity.this.findViewById(R.id.mainCoordinatorLayout), "Equipment saved!", Snackbar.LENGTH_LONG);
                        snackbar.show();
                        MainActivity.this.clear();
                    }
                }
            } catch (Exception ex) {

            }
        }
    }

    private class LoadEquipmentTask extends AsyncTask<String, String, String> {
        HttpURLConnection urlConnection;

        @SuppressWarnings("unchecked")
        protected String doInBackground(String... args) {
            StringBuilder result = new StringBuilder();
            try {
                URL url = new URL(args[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                int code = urlConnection.getResponseCode();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            } finally {
                urlConnection.disconnect();
            }
            return result.toString();
        }

        protected void onPostExecute(String result) {
            try {
                if (result != null && !result.equals("")) {
                    JSONObject jsonObject = new JSONObject(result);
                    MainActivity.this.show(jsonObject);
                } else {

                    Snackbar snackbar = Snackbar
                            .make(MainActivity.this.findViewById(R.id.mainCoordinatorLayout), "NFC not registered!", Snackbar.LENGTH_LONG);
                    snackbar.show();
                }
            } catch (Exception ex) {
                Log.d("Exception", ex.getMessage());
            }
        }
    }
}