package ru.shutoff.track_manager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannedString;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.joda.time.LocalDateTime;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

public class TrackView extends WebViewActivity {

    SharedPreferences preferences;
    Vector<Tracks.Track> tracks;
    Menu topSubMenu;

    static final long START_TIME = 2209161600L;

    class JsInterface {

        @JavascriptInterface
        public String getTrack() {
            Vector<Marker> markers = new Vector<Marker>();
            StringBuilder track_data = new StringBuilder();
            try {
                for (int i = 0; i < tracks.size(); i++) {
                    Tracks.Track track = tracks.get(i);
                    Tracks.Point start = track.points.get(0);
                    Tracks.Point finish = track.points.get(track.points.size() - 1);
                    int n_start = markers.size();
                    double d_best = 100.;
                    for (int n = 0; n < markers.size(); n++) {
                        Marker marker = markers.get(n);
                        double delta = Tracks.calc_distance(start.lat, start.lng, marker.latitude, marker.longitude);
                        if (delta < d_best) {
                            d_best = delta;
                            n_start = n;
                        }
                    }
                    if (n_start >= markers.size()) {
                        Marker marker = new Marker();
                        marker.latitude = start.lat;
                        marker.longitude = start.lng;
                        marker.address = track.start;
                        marker.times = new Vector<TimeInterval>();
                        markers.add(marker);
                    }
                    Marker marker = markers.get(n_start);
                    if ((marker.times.size() == 0) || (marker.times.get(marker.times.size() - 1).end > 0)) {
                        TimeInterval interval = new TimeInterval();
                        marker.times.add(interval);
                    }
                    marker.times.get(marker.times.size() - 1).end = track.points.get(0).time;

                    if (i > 0) {
                        Tracks.Track prev = tracks.get(i - 1);
                        Tracks.Point last = prev.points.get(prev.points.size() - 1);
                        double delta = Tracks.calc_distance(start.lat, start.lng, last.lat, last.lng);
                        if (delta > 200)
                            track_data.append("|");
                    }
                    for (Tracks.Point p : track.points) {
                        track_data.append(p.lat);
                        track_data.append(",");
                        track_data.append(p.lng);
                        track_data.append(",");
                        track_data.append(p.speed);
                        track_data.append(",");
                        track_data.append(p.time);
                        track_data.append("|");
                    }

                    int n_finish = markers.size();
                    d_best = 100;
                    for (int n = 0; n < markers.size(); n++) {
                        if (n == n_start)
                            continue;
                        marker = markers.get(n);
                        double delta = Tracks.calc_distance(finish.lat, finish.lng, marker.latitude, marker.longitude);
                        if (delta < d_best) {
                            n_finish = n;
                            d_best = delta;
                        }
                    }
                    if (n_finish >= markers.size()) {
                        marker = new Marker();
                        marker.latitude = finish.lat;
                        marker.longitude = finish.lng;
                        marker.address = track.finish;
                        marker.times = new Vector<TimeInterval>();
                        markers.add(marker);
                    }
                    marker = markers.get(n_finish);
                    TimeInterval interval = new TimeInterval();
                    interval.begin = track.points.get(track.points.size() - 1).time;
                    marker.times.add(interval);
                }
                track_data.append("|");
                for (Marker marker : markers) {
                    track_data.append("|");
                    track_data.append(marker.latitude);
                    track_data.append(",");
                    track_data.append(marker.longitude);
                    track_data.append(",<b>");
                    for (TimeInterval interval : marker.times) {
                        if (interval.begin > 0) {
                            LocalDateTime begin = new LocalDateTime(interval.begin);
                            track_data.append(begin.toString("HH:mm"));
                            if (interval.end > 0)
                                track_data.append("-");
                        }
                        if (interval.end > 0) {
                            LocalDateTime end = new LocalDateTime(interval.end);
                            track_data.append(end.toString("HH:mm"));
                        }
                        track_data.append(" ");
                    }
                    track_data.append("</b><br/>");
                    track_data.append(Html.toHtml(new SpannedString(marker.address))
                            .replaceAll(",", "&#x2C;")
                            .replaceAll("\\|", "&#x7C;"));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            String res = track_data.toString();
            return res;
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
        public void screenshot(double min_lat, double max_lat, double min_lon, double max_lon, double lat, double lon) {
            takeScreenshot(min_lat, max_lat, min_lon, max_lon, lat, lon);
        }

        @JavascriptInterface
        public String kmh() {
            return getString(R.string.kmh);
        }

        @JavascriptInterface
        public String traffic() {
            return preferences.getBoolean(Names.TRAFFIC, true) ? "1" : "";
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
        return getURL();
    }

    String getURL() {
        if (preferences.getString("map_type", "").equals("OSM"))
            return "file:///android_asset/html/otrack.html";
        return "file:///android_asset/html/track.html";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // Ignore
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        setTitle(getIntent().getStringExtra(Names.TITLE));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        topSubMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.track, menu);
        menu.findItem(R.id.traffic).setTitle(getCheckedText(R.string.traffic, preferences.getBoolean(Names.TRAFFIC, true)));
        boolean isOSM = preferences.getString("map_type", "").equals("OSM");
        menu.findItem(R.id.google).setTitle(getCheckedText(R.string.google, !isOSM));
        menu.findItem(R.id.osm).setTitle(getCheckedText(R.string.osm, isOSM));
        return super.onCreateOptionsMenu(menu);
    }

    String getCheckedText(int id, boolean check) {
        String check_mark = check ? "\u2714" : "";
        return check_mark + getString(id);
    }

    void updateMenu() {
        topSubMenu.clear();
        onCreateOptionsMenu(topSubMenu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
                webView.loadUrl("javascript:saveTrack()");
                break;
            case R.id.share:
                webView.loadUrl("javascript:shareTrack()");
                break;
            case R.id.traffic: {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putBoolean(Names.TRAFFIC, !preferences.getBoolean(Names.TRAFFIC, true));
                ed.commit();
                updateMenu();
                webView.loadUrl(getURL());
                break;
            }
            case R.id.google: {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putString(Names.MAP_TYPE, "Google");
                ed.commit();
                updateMenu();
                webView.loadUrl(getURL());
                break;
            }
            case R.id.osm: {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putString(Names.MAP_TYPE, "OSM");
                ed.commit();
                updateMenu();
                webView.loadUrl(getURL());
                break;
            }
            case R.id.shot: {
                webView.loadUrl("javascript:screenshot()");
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

            File out;

            String save_format = preferences.getString(Names.SAVE_FORMAT, "GPX");
            if (save_format.equals("PLT")) {

                String name = format(d1, "dd.MM.yy_HH.mm-") + format(d2, "HH.mm") + ".plt";
                out = new File(path, name);
                out.createNewFile();

                FileOutputStream f = new FileOutputStream(out);
                OutputStreamWriter ow = new OutputStreamWriter(f);
                BufferedWriter writer = new BufferedWriter(ow);

                writer.append("OziExplorer Track Point File Version 2.1\n");
                writer.append("WGS 84\n");
                writer.append("Altitude is in Feet\n");
                writer.append("Reserved 3\n");
                writer.append("0,2,255,");
                writer.append(name);
                writer.append(",0,0,0,255\n");
                writer.append("0\n");

                for (Tracks.Track track : tracks) {
                    for (Tracks.Point point : track.points) {
                        if ((point.lat < min_lat) || (point.lat > max_lat) || (point.lng < min_lon) || (point.lng > max_lon))
                            continue;
                        writer.append(point.lat + "," + point.lng + ",0," + (int) point.altitude + ",");
                        Date d = new Date(point.time);
                        long time = (d.getTime() / 1000) + START_TIME;
                        double t = time / 86400.;
                        writer.append(String.format("%.7f", t));
                        writer.append(",");
                        LocalDateTime ld = new LocalDateTime(d);
                        writer.append(ld.toString("yyyy-MM-dd,HH-mm-ss"));
                        writer.append("\n");
                    }
                }
                writer.close();
            } else {

                String name = format(d1, "dd.MM.yy_HH.mm-") + format(d2, "HH.mm") + ".gpx";
                out = new File(path, name);
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
            }
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

    void takeScreenshot(double min_lat, double max_lat, double min_lon, double max_lon, double lat, double lon) {
        try {
            File path = Environment.getExternalStorageDirectory();
            if (path == null)
                path = getFilesDir();
            path = new File(path, "Tracks");
            path.mkdirs();
            long time = 0;
            if (tracks != null) {
                for (Tracks.Track track : tracks) {
                    for (Tracks.Point point : track.points) {
                        if ((point.lat >= min_lat) && (point.lat <= max_lat) && (point.lng >= min_lon) && (point.lng <= max_lon)) {
                            time = point.time;
                            break;
                        }
                    }
                    if (time > 0)
                        break;
                }
            }
            if (time == 0)
                time = (new Date()).getTime();
            Date t = new Date(time);
            String name = format(t, "yyyy.MM.dd_HH.mm.ss") + ".png";
            Bitmap bitmap = webView.getScreenshot();
            path = new File(path, name);
            path.createNewFile();
            OutputStream stream = new FileOutputStream(path);
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream);
            stream.close();
            Toast toast = Toast.makeText(this, getString(R.string.saved) + " " + path.toString(), Toast.LENGTH_LONG);
            toast.show();
        } catch (Exception ex) {
            Toast toast = Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    String format(Date d, String format) {
        return new SimpleDateFormat(format).format(d);
    }

    static class TimeInterval {
        long begin;
        long end;
    }

    static class Marker {
        double latitude;
        double longitude;
        String address;
        Vector<TimeInterval> times;
    }

}
