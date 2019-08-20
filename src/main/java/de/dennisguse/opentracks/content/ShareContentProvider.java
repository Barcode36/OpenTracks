package de.dennisguse.opentracks.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import de.dennisguse.opentracks.BuildConfig;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.exporter.FileTrackExporter;
import de.dennisguse.opentracks.io.file.exporter.TrackWriter;

/**
 * A content provider that mimics the behavior of {@link androidx.core.content.FileProvider}, which shares virtual (non-existing) files.
 * The actual content is generated by accessing {@link CustomContentProvider} on request.
 */
public class ShareContentProvider extends ContentProvider {

    public static final String TAG = ShareContentProvider.class.getCanonicalName();

    public static final String SHAREPROVIDER = BuildConfig.APPLICATION_ID;

    private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    public static String MIME = "application/kml+xml";

    public static Uri createURI(long[] trackIds) {
        if (trackIds.length == 0) {
            throw new UnsupportedOperationException();
        }

        StringBuilder builder = new StringBuilder();
        for (long trackId : trackIds) {
            builder.append(trackId).append(",");
        }
        builder.deleteCharAt(builder.lastIndexOf(","));

        return Uri.parse("content://" + SHAREPROVIDER + "/" + builder + ".kml");
    }

    private static long[] parseURI(Uri uri) {
        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null) {
            return new long[]{};
        }

        String[] lastPathSegmentSplit = lastPathSegment.replace(".kml", "").split(",");
        long[] trackIds = new long[lastPathSegmentSplit.length];
        for (int i = 0; i < trackIds.length; i++) {
            trackIds[i] = Long.valueOf(lastPathSegmentSplit[i]);
        }
        return trackIds;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // ContentProvider has already checked granted permissions
        if (projection == null) {
            projection = COLUMNS;
        }

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = uri.getLastPathSegment();
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = -1;
            }
        }

        cols = Arrays.copyOf(cols, i);
        values = Arrays.copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return MIME;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        ContentProviderUtils contentProviderUtils = ContentProviderUtils.Factory.get(getContext());

        final Uri customContentProviderURI = Uri.parse("content://" + ContentProviderUtils.AUTHORITY + "/tracks");
        getContext().grantUriPermission(getCallingPackage(), customContentProviderURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        long[] trackIds = parseURI(uri);
        final Track[] tracks = new Track[trackIds.length];
        for (int i = 0; i < trackIds.length; i++) {
            tracks[i] = contentProviderUtils.getTrack(trackIds[i]);
        }

        TrackWriter kmlTrackWriter = TrackFileFormat.KML.newTrackWriter(getContext(), false);
        final FileTrackExporter fileTrackExporter = new FileTrackExporter(contentProviderUtils, tracks, kmlTrackWriter, null);

        PipeDataWriter pipeDataWriter = new PipeDataWriter<String>() {
            @Override
            public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri, @NonNull String mimeType, @Nullable Bundle opts, @Nullable String args) {
                try (FileOutputStream fileOutputStream = new FileOutputStream(output.getFileDescriptor())) {
                    fileTrackExporter.writeTrack(fileOutputStream);
                } catch (IOException e) {
                    Log.w(TAG, "Oops closing " + e);
                    Toast.makeText(getContext(), "", Toast.LENGTH_SHORT).show();
                } finally {

                    getContext().revokeUriPermission(customContentProviderURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
        };

        return openPipeHelper(uri, getType(uri), null, null, pipeDataWriter);
    }

    @Nullable
    @Override
    public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}