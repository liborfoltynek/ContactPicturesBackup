package com.fotolibb.contactpicturesbackup;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
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
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
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

        ProgressBar pb = findViewById(R.id.progressBar);
        pb.setMax(1061);
        pb.setProgress(0, false);

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
        if (task == null) {

            CheckBox ch = findViewById(R.id.chbEmptyOnly);

            task = new DownloadFilesTask().execute(ch.isChecked() ? 1 : 0);
        }
    }

    private void btCancelPressed(View view) {
        if (task != null) {
            task.cancel(true);
        }
    }

    private void setProgressPercent(String a, String p, int amount) {
        TextView tvA = findViewById(R.id.tvAction);
        tvA.setText(a);

        TextView tvP = findViewById(R.id.tvId);
        tvP.setText(p);

        ProgressBar pb = findViewById(R.id.progressBar);
        pb.setProgress(amount, true);
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

        protected Integer doInBackground(Integer... par) {
            if (LoadContacts(par[0] == 1)) {

                WriteContactsToFile();

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
            String prog = format("%d: %s", p, contacts.get(p).Name);
            setProgressPercent(action, prog, contacts.size());
        }

        protected void onPostExecute(Integer result) {
            setProgressPercent("", "Done.", 1060);
            task = null;
        }

        protected void onCancelled(Integer result) {
            setProgressPercent("", "Canceled.", 0);
            task = null;
        }

        private boolean LoadContacts(boolean emptyOnly) {
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
                    if (bmp != null && !emptyOnly) {
                        String filename = format("/storage/emulated/0/u/%s.png", name);
                        writeBitmapToFile(bmp, filename);
                    }

                    if ((emptyOnly && bmp == null) || !emptyOnly) {
                        Contact contact = new Contact();
                        contact.Name = name;
                        contact.Id = id;
                        contact.Phone = phoneNo;
                        contact.HasImage = bmp != null;
                        contacts.add(contact);
                    }

                    publishProgress(contacts.size() - 1, 0);
                    Log.i(TAG, format("[%s]: %s (%s) <%s>", id, name, phoneNo, contactPictureUri));
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

                try {
                    //Log.i(TAG, format("PROCESS: [%s] %s", c.Id, c.Name));
                    String filenameA = format("/storage/emulated/0/u/%s.png", c.Name);
                    String filenameB = format("/storage/0000-0000/Pictures/users/%s.png", c.Name);
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
                      //  Log.i(TAG, format("A: %d, B: %d", bmpA.getWidth(), bmpB.getWidth()));
                        if (bmpA.getWidth() < bmpB.getWidth()) {
                            Log.i(TAG, format("RESTORE: %s", c.Name));
                            updateContactPicture(c, bmpB);
                        }
                    }

                    if (bmpA == null && bmpB != null) {
                        Log.i(TAG, format("***** INSERT NEW ***** : %s", c.Name));
                        updateContactPicture(c, bmpB);
                    }
                    publishProgress(counter++, 1);
                } catch (Exception ex) {
                    Log.e("EX", ex.getMessage());
                }
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
            boolean doWrite = false;

            File f = new File(filename);
            if (f.exists()) {
                Bitmap discBmp = readBitmapFromFile(filename);
                if ((discBmp.getWidth() < bmp.getWidth()) ||
                        (discBmp.getHeight() < bmp.getHeight())) {
                    f.delete();
                    doWrite = true;
                }
            } else {
                doWrite = true;
            }

            if (doWrite) {
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

        private void WriteContactsToFile() {
            String fileContents = "";
            for (Contact c : contacts) {
                fileContents += String.format("[%s] %s (%s) <%s>\r\n", c.Id, c.Name, c.Phone, c.HasImage ? "A" : "N");
            }

            String fileName = "/storage/emulated/0/u/log.txt";
            try {
                FileWriter out = new FileWriter(new File(fileName));
                out.write(fileContents);
                out.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }
}
