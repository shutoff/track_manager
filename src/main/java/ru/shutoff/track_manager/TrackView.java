package ru.shutoff.track_manager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannedString;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

public class TrackView extends WebViewActivity {

    SharedPreferences preferences;
    Vector<Tracks.Track> tracks;

    class JsInterface {

        @JavascriptInterface
        public String getTrack() {
            StringBuilder track_data = new StringBuilder();
            for (Tracks.Track track : tracks) {
                Tracks.Point p = track.points.get(0);
                if (track_data.length() > 0)
                    track_data.append("|");
                track_data.append(p.lat);
                track_data.append(",");
                track_data.append(p.lng);
                track_data.append(",");
                track_data.append(infoMark(p.time, track.start));
                for (Tracks.Point point : track.points) {
                    track_data.append("|");
                    track_data.append(point.lat);
                    track_data.append(",");
                    track_data.append(point.lng);
                    track_data.append(",");
                    track_data.append((int) point.speed);
                    track_data.append(",");
                    track_data.append(point.time);
                }
                p = track.points.get(track.points.size() - 1);
                track_data.append("|");
                track_data.append(p.lat);
                track_data.append(",");
                track_data.append(p.lng);
                track_data.append(",");
                track_data.append(infoMark(p.time, track.finish));
            }
            return track_data.toString();
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
        try {
            byte[] track_data = null;
            String file_name = getIntent().getStringExtra(Names.TRACK_FILE);
            if (file_name != null) {
                File file = new File(file_name);
                FileInputStream in = new FileInputStream(file);
                track_data = new byte[(int) file.length()];
                in.read(track_data);
                in.close();
                file.delete();
            } else {
                track_data = getIntent().getByteArrayExtra(Names.TRACK);
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(track_data);
            ObjectInput in = new ObjectInputStream(bis);
            tracks = (Vector<Tracks.Track>) in.readObject();
            in.close();
            bis.close();
        } catch (Exception ex) {
            finish();
        }
        webView.addJavascriptInterface(new JsInterface(), "android");
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getString("map_type", "").equals("OSM"))
            return "file:///android_asset/html/otrack.html";
        return "file:///android_asset/html/track.html";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
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

            long begin = 0;
            long end = 0;
            for (Tracks.Track track : tracks) {
                for (Tracks.Point point : track.points) {
                    if ((point.lat < min_lat) || (point.lat > max_lat) || (point.lng < min_lon) || (point.lng > max_lon))
                        continue;
                    if (begin == 0)
                        begin = point.time;
                    end = point.time;
                }
            }

            Date d1 = new Date(begin);
            Date d2 = new Date(end);

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
            for (Tracks.Track track : tracks) {
                for (Tracks.Point point : track.points) {
                    if ((point.lat < min_lat) || (point.lat > max_lat) || (point.lng < min_lon) || (point.lng > max_lon)) {
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
                    writer.append("<trkpt lat=\"" + point.lat + "\" lon=\"" + point.lng + "\">\n");
                    Date t = new Date(point.time);
                    writer.append("<time>" + format(t, "yyyy-MM-dd") + "T" + format(t, "HH:mm:ss") + "Z</time>\n");
                    writer.append("</trkpt>\n");
                }
                if (trk) {
                    trk = false;
                    writer.append("</trkseg>");
                }
            }
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

    static String infoMark(long t, String address) {
        Date d = new Date(t);
        String time = String.format("%02d:%02d", d.getHours(), d.getMinutes());
        return "<b>" + time + "</b><br/>" + Html.toHtml(new SpannedString(address))
                .replaceAll(",", "&#x2C;")
                .replaceAll("\\|", "&#x7C;");
    }
}
