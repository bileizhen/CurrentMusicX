package io.github.currencortex.music;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.io.*;

/** Read-only test tree. Java keeps the separate provider process independent of app Kotlin classes. */
public final class LocalFolderFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "audio/mpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { return 0; }
    @Override public int delete(Uri uri, String where, String[] args) { return 0; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String id = DocumentsContract.getDocumentId(uri);
        String[] ids;
        if (!"children".equals(uri.getLastPathSegment())) ids = new String[]{id};
        else if ("selected".equals(id)) ids = new String[]{"track", "nested", "picture"};
        else if ("nested".equals(id)) ids = new String[]{"nestedTrack"};
        else if ("outside".equals(id)) ids = new String[]{"outsideTrack"};
        else throw new IllegalArgumentException(id);
        String[] columns = projection == null ? new String[]{Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        for (String child : ids) {
            boolean folder = child.equals("selected") || child.equals("nested") || child.equals("outside");
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                switch (columns[i]) {
                    case Document.COLUMN_DOCUMENT_ID: row[i] = child; break;
                    case Document.COLUMN_DISPLAY_NAME:
                        row[i] = folder ? child : child.equals("picture") ? "cover.jpg" : child + ".mp3"; break;
                    case Document.COLUMN_MIME_TYPE:
                        row[i] = folder ? Document.MIME_TYPE_DIR : child.equals("picture") ? "image/jpeg" : "audio/mpeg"; break;
                }
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String id = DocumentsContract.getDocumentId(uri);
        if (!mode.equals("r") || !(id.equals("track") || id.equals("nestedTrack") || id.equals("outsideTrack")))
            throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), "local-folder-fixture.mp3");
        if (!file.exists()) {
            try (InputStream in = getContext().getAssets().open("download/fixture.mp3");
                 OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
