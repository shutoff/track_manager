package ru.shutoff.track_manager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TrackView extends WebViewActivity {

    String track;
    SharedPreferences preferences;

    class JsInterface {

        @JavascriptInterface
        public String getTrack() {
            return track;
        }

        @JavascriptInterface
        public void save(double min_lat, double max_lat, double min_lon, double max_lon) {
            saveTrack(min_lat, max_lat, min_lon, max_lon, true);
        }

        @JavascriptInterface
        public void share(double min_lat, double max_lat, double min_lon, double max_lon) {
            shareTrack(min_lat, max_lat, min_lon, max_lon);
        }

        @JavascriptInterface
        public String kmh() {
            return getString(R.string.kmh);
        }

        @JavascriptInterface
        public String traffic() {
            return preferences.getBoolean("traffic", true) ? "1" : "";
        }
    }

    @Override
    String loadURL() {
        webView.addJavascriptInterface(new JsInterface(), "android");
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getString("map_type", "").equals("OSM"))
            return "file:///android_asset/html/otrack.html";
        return "file:///android_asset/html/track.html";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        track = getIntent().getStringExtra(Names.TRACK);
        super.onCreate(savedInstanceState);
        setTitle(getIntent().getStringExtra(Names.TITLE));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.track, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save: {
                webView.loadUrl("javascript:saveTrack()");
                break;
            }
            case R.id.share: {
                webView.loadUrl("javascript:shareTrack()");
                break;
            }
        }
        return false;
    }

    File saveTrack(double min_lat, double max_lat, double min_lon, double max_lon, boolean show_toast) {
        try {
            File path = Environment.getExternalStorageDirectory();
            if (path == null)
                path = getFilesDir();
            path = new File(path, "Tracks");
            path.mkdirs();

            String[] points = track.split("\\|");
            long begin = 0;
            long end = 0;
            for (String point : points) {
                String[] data = point.split(",");
                if (data.length != 4)
                    continue;
                double lat = Double.parseDouble(data[0]);
                double lon = Double.parseDouble(data[1]);
                long time = Long.parseLong(data[3]);
                if ((lat < min_lat) || (lat > max_lat) || (lon < min_lon) || (lon > max_lon))
                    continue;
                if (begin == 0)
                    begin = time;
                end = time;
            }
            Date d2 = new Date(begin);
            Date d1 = new Date(end);

            String name = format(d1, "dd.MM.yy_HH.mm-") + format(d2, "HH.mm") + ".gpx";
            File out = new File(path, name);
            out.createNewFile();

            FileOutputStream f = new FileOutputStream(out);
            OutputStreamWriter ow = new OutputStreamWriter(f);
            BufferedWriter writer = new BufferedWriter(ow);

            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.append("<gpx\n");
            writer.append(" version=\"1.0\"\n");
            writer.append(" creator=\"ExpertGPS 1.1 - http://www.topografix.com\"\n");
            writer.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            writer.append(" xmlns=\"http://www.topografix.com/GPX/1/0\"\n");
            writer.append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\n");
            writer.append("<time>");
            Date now = new Date();
            writer.append(format(now, "yyyy-MM-dd") + "T" + format(now, "HH:mm:ss") + "Z");
            writer.append("</time>\n");
            writer.append("<trk>\n");

            boolean trk = false;
            for (String point : points) {
                String[] data = point.split(",");
                if (data.length != 4)
                    continue;
                double lat = Double.parseDouble(data[0]);
                double lon = Double.parseDouble(data[1]);
                long time = Long.parseLong(data[3]);
                if ((lat < min_lat) || (lat > max_lat) || (lon < min_lon) || (lon > max_lon)) {
                    if (trk) {
                        trk = false;
                        writer.append("</trkseg>\n");
                    }
                    continue;
                }
                if (!trk) {
                    trk = true;
                    writer.append("<trkseg>\n");
                }
                writer.append("<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">\n");
                Date t = new Date(time);
                writer.append("<time>" + format(t, "yyyy-MM-dd") + "T" + format(t, "HH:mm:ss") + "Z</time>\n");
                writer.append("</trkpt>\n");
            }
            if (trk)
                writer.append("</trkseg>");
            writer.append("</trk>\n");
            writer.append("</gpx>");
            writer.close();
            if (show_toast) {
                Toast toast = Toast.makeText(this, getString(R.string.saved) + " " + out.toString(), Toast.LENGTH_LONG);
                toast.show();
            }
            return out;
        } catch (Exception ex) {
            Toast toast = Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
        return null;
    }

    void shareTrack(double min_lat, double max_lat, double min_lon, double max_lon) {
        File out = saveTrack(min_lat, max_lat, min_lon, max_lon, false);
        if (out == null)
            return;
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(out));
        shareIntent.setType("application/gpx+xml");
        startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share)));
    }

    String format(Date d, String format) {
        return new SimpleDateFormat(format).format(d);
    }
}
