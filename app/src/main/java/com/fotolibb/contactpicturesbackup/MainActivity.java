package com.fotolibb.contactpicturesbackup;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static java.lang.String.format;

public class MainActivity extends AppCompatActivity {

    static final String[] CONTACTS_ROWS = new String[]
            {ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI};
    String TAG = "CONTACTS";
    private AsyncTask task = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = (Button) findViewById(R.id.btCancel);
        btn.setBackgroundColor(Color.RED);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btCancelPressed(v);
            }
        });

        btn = (Button) findViewById(R.id.btRun);
        btn.setBackgroundColor(Color.GREEN);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run(v);
            }
        });

        askAll();
    }


    private void run(View view) {
        task = new DownloadFilesTask().execute(1);
    }

    private void btCancelPressed(View view) {
        if (task != null) {
            task.cancel(true);
        }
    }

    private void setProgressPercent(String a, String p) {
        TextView tvA = findViewById(R.id.tvAction);
        tvA.setText(a);

        TextView tvP = findViewById(R.id.tvId);
        tvP.setText(p);
    }

    private void askAll() {
        askRights(Manifest.permission.READ_CONTACTS);
        askRights(Manifest.permission.WRITE_CONTACTS);
        askRights(Manifest.permission.GET_ACCOUNTS);
        askRights(Manifest.permission.READ_EXTERNAL_STORAGE);
        askRights(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void askRights(String r) {
        if (ContextCompat.checkSelfPermission(this, r) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, r)) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{r}, 5554);
            }
        }
    }

    private class DownloadFilesTask extends AsyncTask<Integer, Integer, Integer> {
        ArrayList<Contact> contacts = new ArrayList();

        protected Integer doInBackground(Integer... urls) {
            if (LoadContacts()) {
                ProcessContacts();
            }
            return 100;
        }

        protected void onProgressUpdate(Integer... progress) {
            String action = "";
            if (progress[1] == 0) {
                action = "LOADING";
            } else {
                action = "PROCESSING";
            }
            Integer p = progress[0];
            String prog = format("%d: %s", p, contacts.get(p - 1).Name);
            setProgressPercent(action, prog);
        }

        protected void onPostExecute(Integer result) {
            setProgressPercent("", "Done.");
        }

        protected void onCancelled(Integer result) {
            setProgressPercent("", "Canceled.");
        }

        private boolean LoadContacts() {
            boolean b = true;
            ContentResolver cr = getContentResolver();
            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

            if ((cur != null ? cur.getCount() : 0) > 0) {
                while (cur != null && cur.moveToNext()) {
                    if (isCancelled()) {
                        b = false;
                        break;
                    }

                    String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    String contactPictureUri = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.PHOTO_URI));

                    String phoneNo = "";
                    if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        phoneNo = getPhoneNumber(cr, id);
                    }

                    Bitmap bmp = getBitmapFromContact(contactPictureUri);
                    if (bmp != null) {
                        String filename = format("/storage/emulated/0/u/%s.png", name);
                        writeBitmapToFile(bmp, filename);
                    }

                    Contact contact = new Contact();
                    contact.Name = name;
                    contact.Id = id;
                    contacts.add(contact);

                    Log.i(TAG, format("[%s]: %s (%s) <%s>", id, name, phoneNo, contactPictureUri));
                    publishProgress(contacts.size(), 0);
                }
            }
            if (cur != null) {
                cur.close();
            }
            return b;
        }

        private void ProcessContacts() {
            int counter = 0;
            for (Contact c : contacts) {
                if (isCancelled()) break;

                Log.i(TAG, format("PROCESS: [%s] %s", c.Id, c.Name));
                String filenameA = format("/storage/emulated/0/u/%s.png", c.Name);
                String filenameB = format("/storage/emulated/0/users/%s.jpg", c.Name);
                File fileA = new File(filenameA);
                File fileB = new File(filenameB);
                Bitmap bmpA = null;
                Bitmap bmpB = null;

                if (fileA.exists()) {
                    bmpA = readBitmapFromFile(filenameA);
                }
                if (fileB.exists()) {
                    bmpB = readBitmapFromFile(filenameB);
                }

                if (bmpA != null && bmpB != null) {

                    Log.i(TAG, format("A: %d, B: %d", bmpA.getWidth(), bmpB.getWidth()));

                    if (bmpA.getWidth() < bmpB.getWidth()) {
                        Log.i(TAG, format("RESTORE: %s", c.Name));
                        updateContactPicture(c, bmpB);
                    }
                }

                if (bmpA == null && bmpB != null) {
                    Log.i(TAG, format("NOCHANCE: %s", c.Name));
                }
                publishProgress(counter++, 1);
            }
        }

        private void updateContactPicture(Contact c, Bitmap bmp) {
            updateContact(c.Id, bmp);
        }

        boolean updateContact(String contactID, Bitmap bitmap) {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            try {
                ByteArrayOutputStream image = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, image);

                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " +
                                ContactsContract.Data.MIMETYPE + "=?", new String[]{contactID, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE})
                        .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                        .withValue(ContactsContract.Contacts.Photo.PHOTO, image.toByteArray())
                        .build());
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (Exception ex) {
                ex.printStackTrace();
                Log.e("EX", ex.getMessage());
                return false;
            }
            return true;
        }

        private Bitmap readBitmapFromFile(String filename) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(filename, options);
            return bitmap;
        }

        private void writeBitmapToFile(Bitmap bmp, String filename) {

            File f = new File(filename);
            if (f.exists()) {
                f.delete();
            }

            FileOutputStream outStream = null;
            try {
                outStream = new FileOutputStream(filename);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (outStream != null) {
                        outStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private Bitmap getBitmapFromContact(String hasPicture) {
            Bitmap bmp = null;
            if (hasPicture != null) {
                InputStream bitmapStream = null;
                try {
                    bitmapStream = getContentResolver().openInputStream(Uri.parse(hasPicture));
                    if (bitmapStream != null) {
                        bmp = BitmapFactory.decodeStream(bitmapStream);
                    }
                } catch (FileNotFoundException e) {
                    Log.i("ERR", "FileNotFoundException");
                }
            }
            return bmp;
        }

        @NonNull
        private String getPhoneNumber(ContentResolver cr, String id) {
            String phoneNo = "";
            Cursor phoneCursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);
            while (phoneCursor.moveToNext()) {
                if (phoneNo.length() > 0) {
                    phoneNo += ",";
                }
                phoneNo += phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            }
            phoneCursor.close();
            return phoneNo;
        }
    }

}
