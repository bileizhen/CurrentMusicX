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
        if (id.equals("denied")) throw new SecurityException("Fixture read permission revoked");
        if (!mode.equals("r") || !(id.equals("track") || id.equals("nestedTrack") || id.equals("outsideTrack")))
            throw new FileNotFoundException();
        boolean artwork = id.equals("track");
        File file = new File(getContext().getCacheDir(), "local-folder-artwork-v1-" + artwork + ".mp3");
        if (!file.exists()) {
            try (BufferedInputStream in = new BufferedInputStream(getContext().getAssets().open("download/fixture.mp3"));
                 OutputStream out = new FileOutputStream(file)) {
                if (artwork) {
                    // Replace the fixture's original ID3 tag instead of stacking two headers.
                    in.mark(10);
                    byte[] header = new byte[10];
                    new DataInputStream(in).readFully(header);
                    if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                        int tagSize = 0;
                        for (int i = 6; i < 10; i++) tagSize = (tagSize << 7) | (header[i] & 127);
                        for (int i = 0; i < tagSize; i++) if (in.read() < 0) throw new EOFException();
                    } else in.reset();
                    out.write(artworkTag());
                }
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Synthetic blue PNG in an ID3v2.3 APIC frame; nestedTrack remains coverless. */
    private static byte[] artworkTag() throws IOException {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(24, 24,
            android.graphics.Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream picture = new ByteArrayOutputStream();
        try {
            bitmap.eraseColor(android.graphics.Color.BLUE);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, picture);
        } finally { bitmap.recycle(); }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0); // ISO-8859-1 description
        payload.write("image/png".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        payload.write(0); payload.write(3); payload.write(0); // Front cover, empty description
        payload.write(picture.toByteArray());
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(tag);
        out.writeBytes("ID3"); out.write(new byte[]{3, 0, 0});
        int size = 10 + payload.size();
        out.write(new byte[]{(byte)((size >> 21) & 127), (byte)((size >> 14) & 127),
            (byte)((size >> 7) & 127), (byte)(size & 127)});
        out.writeBytes("APIC"); out.writeInt(payload.size()); out.writeShort(0);
        payload.writeTo(out);
        return tag.toByteArray();
    }
}
