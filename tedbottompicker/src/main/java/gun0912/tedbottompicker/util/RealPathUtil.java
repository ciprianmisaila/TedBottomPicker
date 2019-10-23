package gun0912.tedbottompicker.util;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.format.DateFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by TedPark on 2016. 11. 5..
 */

public class RealPathUtil {

    public static String getRealPath(Context context, Uri uri) {
        String realPath;
        // SDK < API11
        if (Build.VERSION.SDK_INT < 19) {
            realPath = RealPathUtil.getRealPathFromURI_API11to18(context, uri);
        }
        // SDK > 19 (Android 4.4)
        else {
            realPath = RealPathUtil.getRealPathFromURI_API19(context, uri);
        }

        return realPath;
    }

    @SuppressLint("NewApi")
    public static String getRealPathFromURI_API19(final Context context, final Uri uri) {

        // check here to KITKAT or new version
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/"
                            + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] { split[1] };

                return getDataColumn(context, contentUri, selection,
                        selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            if (isGooglePhotosUriExt(uri)){
                try {

                    Cursor cursor = null;
                    final String column1 = "datetaken";     String date = null;
                    final String column2 = "_display_name"; String fileName = null;
                    final String column3 = "_id";
                    final String column4 = "_data";
                    final String column5 = "latitude";      String lat = null;
                    final String column6 = "longitude";     String lon = null;
                    final String column7 = "special_type_id";

                   final String[] projection = { column1 , column2};

                    try {
                        cursor = context.getContentResolver().query(uri, null,
                                null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            final int index1 = cursor.getColumnIndexOrThrow(column1);
                            final int index2 = cursor.getColumnIndexOrThrow(column2);
                            final int index3 = cursor.getColumnIndexOrThrow(column3);
                            final int index4 = cursor.getColumnIndexOrThrow(column4);
                            final int index5 = cursor.getColumnIndexOrThrow(column5);
                            final int index6 = cursor.getColumnIndexOrThrow(column6);
                            final int index7 = cursor.getColumnIndexOrThrow(column7);
                            date = cursor.getString(index1);
                            fileName = cursor.getString(index2);
                            String id = cursor.getString(index3);
                            lat = cursor.getString(index5);
                            lon = cursor.getString(index6);
                        }
                    } finally {
                        if (cursor != null)
                            cursor.close();
                    }

                    InputStream is = context.getContentResolver().openInputStream(uri);
                    Bitmap pictureBitmap = BitmapFactory.decodeStream(is);
                    Uri newuri = getImageUri(context, pictureBitmap, fileName, date, lat, lon);
                    return newuri.getPath();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static Uri getImageUri(Context context, Bitmap bitmap, String name, String date, String latitude, String longitude) {
        File cacheDir = context.getCacheDir();
        String imageFileName = null;
        if (name == null) {
            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
            imageFileName = "JPEG_" + timeStamp + ".jpg";
        }else{
            imageFileName = name;
        }

        File f = new File(cacheDir, imageFileName);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        saveExifData(f, date, latitude, longitude);
        return Uri.fromFile(f);
    }

    public static void saveExifData(File photo, String date, String latitude, String longitude){
        ExifInterface exif = null;

        try{
            exif = new ExifInterface(photo.getCanonicalPath());
            if (exif != null) {
                if (date != null) exif.setAttribute(ExifInterface.TAG_DATETIME, getDate(Long.parseLong(date)));
                if (latitude != null) exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitude);
                if (longitude != null) exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitude);
                exif.saveAttributes();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static String getDate(long time) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time);
        String date = DateFormat.format("yyyy:MM:dd hh:mm:ss", cal).toString();
        return date;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context
     *            The context.
     * @param uri
     *            The Uri to query.
     * @param selection
     *            (Optional) Filter used in the query.
     * @param selectionArgs
     *            (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri,
                                       String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };

        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri
                .getAuthority());
    }

    public static boolean isGooglePhotosUriExt(Uri uri) {
        return  "com.google.android.apps.photos.contentprovider".equals(uri
                .getAuthority());
    }

    @SuppressLint("NewApi")
    public static String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        String result = null;

        CursorLoader cursorLoader = new CursorLoader(
                context,
                contentUri, proj, null, null, null);
        Cursor cursor = cursorLoader.loadInBackground();

        if(cursor != null){
            int column_index =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            result = cursor.getString(column_index);
        }
        return result;
    }

    public static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri){
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index
                = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}