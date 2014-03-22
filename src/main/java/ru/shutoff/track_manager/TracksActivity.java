package ru.shutoff.track_manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.joda.time.LocalDateTime;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

public class TracksActivity extends ActionBarActivity {

    SharedPreferences preferences;

    Vector<Tracks.Track> tracks;

    ProgressBar progressFirst;
    ProgressBar progressBar;
    TextView tvLoading;
    TextView tvStatus;
    ListView lvTracks;
    TextView tvRemove;
    Button btnRemove;

    int task_id;
    File track_file;
    int progress;
    boolean loaded;

    static String formatTime(Date d) {
        return String.format("%02d:%02d", d.getHours(), d.getMinutes());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        setContentView(R.layout.tracks);
        progressFirst = (ProgressBar) findViewById(R.id.first_progress);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        tvLoading = (TextView) findViewById(R.id.loading);
        tvStatus = (TextView) findViewById(R.id.status);
        lvTracks = (ListView) findViewById(R.id.tracks);
        tvRemove = (TextView) findViewById(R.id.remove_warn);
        btnRemove = (Button) findViewById(R.id.delete);

        lvTracks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TracksAdapter adapter = (TracksAdapter) lvTracks.getAdapter();
                if (adapter.selected == position) {
                    showTrack(position);
                    return;
                }
                Tracks.Track track = tracks.get(position);
                if (track.points.get(0).time == 0) {
                    showTrack(position);
                    return;
                }
                adapter.selected = position;
                adapter.notifyDataSetChanged();
            }
        });

        tvStatus.setClickable(true);
        tvStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loaded)
                    showDay();
            }
        });

        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                track_file.delete();
                track_file = null;
                tvRemove.setVisibility(View.GONE);
                btnRemove.setVisibility(View.GONE);
            }
        });

        setTitle(getIntent().getStringExtra(Names.TITLE));

        tracks = (Vector<Tracks.Track>) getLastCustomNonConfigurationInstance();
        if (tracks != null) {
            if (tracks.size() == 0) {
                tracksDone();
                return;
            }
            double mileage = 0;
            long time = 0;
            for (Tracks.Track track : tracks) {
                mileage += track.mileage;
                time += track.getTime();
            }
            tvStatus.setText(String.format(getString(R.string.status), mileage / 1000, timeFormat((int) (time / 60)), mileage * 3.6 / time));
            tvStatus.setVisibility(View.VISIBLE);
            allDone();
            return;
        }

        AsyncTask<String, Void, Void> task = new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                try {
                    track_file = new File(params[0]);
                    tracks = Tracks.loadPlt(track_file, false);
                    if (tracks == null)
                        tracks = Tracks.loadGpx(track_file);
                } catch (Exception ex) {
                    // ignore
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                tracksDone();
            }
        };
        task.execute(getIntent().getStringExtra(Names.PATH));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (loaded)
            return tracks;
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.tracks, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
                saveTrack(true);
                break;
            case R.id.share:
                shareTrack();
                break;
        }
        return true;
    }

    void tracksDone() {
        progressFirst.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        if ((tracks == null) || (tracks.size() == 0)) {
            showError(getString(R.string.no_data));
            if (track_file != null) {
                tvRemove.setVisibility(View.VISIBLE);
                btnRemove.setVisibility(View.VISIBLE);
            }
            return;
        }
        double mileage = 0;
        long time = 0;
        for (Tracks.Track track : tracks) {
            mileage += track.mileage;
            time += track.getTime();
        }
        if (time == 0) {
            tvStatus.setText(String.format(getString(R.string.status_notime), mileage / 1000));
        } else {
            tvStatus.setText(String.format(getString(R.string.status), mileage / 1000, timeFormat((int) (time / 60)), mileage * 3.6 / time));
        }
        tvStatus.setVisibility(View.VISIBLE);
        progressBar.setMax(tracks.size() * 2 + 1);
        progress = 1;
        progressBar.setProgress(1);
        TrackStartPositionFetcher fetcher = new TrackStartPositionFetcher();
        fetcher.update(task_id, 0);
    }

    void allDone() {
        progressFirst.setVisibility(View.GONE);
        tvLoading.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        lvTracks.setVisibility(View.VISIBLE);
        lvTracks.setAdapter(new TracksAdapter());
        loaded = true;
    }

    void showError(String text) {
        tvStatus.setText(text);
        tvStatus.setVisibility(View.VISIBLE);
        tvLoading.setVisibility(View.GONE);
        progressFirst.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    void showTrack(int index) {
        Intent intent = new Intent(this, TrackView.class);
        Tracks.Track track = tracks.get(index);
        Vector<Tracks.Track> track_one = new Vector<Tracks.Track>();
        track_one.add(track);
        setTrack(track_one, intent);
        if (track.points.get(0).time != 0) {
            Date begin = new Date(track.points.get(0).time);
            Date end = new Date(track.points.get(track.points.size() - 1).time);
            intent.putExtra(Names.TITLE, format(begin, "d MMMM HH:mm") + "-" + format(end, "HH:mm"));
            intent.putExtra(Names.TRAFFIC, true);
        } else {
            intent.putExtra(Names.TITLE, getTitle());
        }
        startActivity(intent);
    }

    void showDay() {
        Intent intent = new Intent(this, TrackView.class);
        if (!setTrack(tracks, intent))
            finish();
        intent.putExtra(Names.TITLE, getTitle());
        Tracks.Track track = tracks.get(0);
        if (track.points.get(0).time != 0)
            intent.putExtra(Names.TRAFFIC, true);
        startActivity(intent);
    }

    boolean setTrack(Vector<Tracks.Track> tracks, Intent intent) {
        byte[] data = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(tracks);
            data = bos.toByteArray();
            out.close();
            bos.close();
        } catch (Exception ex) {
            // ignore
        }
        if (data.length > 500000) {
            try {
                File outputDir = getCacheDir();
                File file = File.createTempFile("track", "dat", outputDir);
                FileOutputStream f = new FileOutputStream(file);
                f.write(data);
                intent.putExtra(Names.TRACK_FILE, file.getAbsolutePath());
                f.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        } else {
            intent.putExtra(Names.TRACK, data);
        }
        return true;
    }

    String format(Date d, String format) {
        return new SimpleDateFormat(format).format(d);
    }

    String timeFormat(int minutes) {
        if (minutes < 60) {
            String s = getString(R.string.m_format);
            return String.format(s, minutes);
        }
        int hours = minutes / 60;
        minutes -= hours * 60;
        String s = getString(R.string.hm_format);
        return String.format(s, hours, minutes);
    }

    File saveTrack(boolean show_toast) {
        if (!loaded)
            return null;
        if (tracks.size() == 0)
            return null;
        try {
            File path = Environment.getExternalStorageDirectory();
            if (path == null)
                path = getFilesDir();
            path = new File(path, "Tracks");
            path.mkdirs();

            LocalDateTime d = new LocalDateTime(tracks.get(0).points.get(0).time);
            String name = d.toString("dd MMMM yyyy") + ".txt";
            File out = new File(path, name);
            out.createNewFile();

            FileOutputStream f = new FileOutputStream(out);
            OutputStreamWriter ow = new OutputStreamWriter(f);
            BufferedWriter writer = new BufferedWriter(ow);

            if (!save(writer)) {
                writer.close();
                return null;
            }


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

    void shareTrack() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStreamWriter ow = new OutputStreamWriter(out);
            BufferedWriter writer = new BufferedWriter(ow);
            if (!save(writer)) {
                writer.close();
                return;
            }
            writer.close();
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT, out.toString());
            LocalDateTime d = new LocalDateTime(tracks.get(0).points.get(0).time);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, d.toString("dd MMMM yyyy"));
            shareIntent.setType("text/plain");
            startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share)));
        } catch (Exception ex) {
            // ignore
        }
    }

    boolean save(BufferedWriter writer) {
        if (!loaded)
            return false;
        if (tracks.size() == 0)
            return false;
        try {
            for (Tracks.Track track : tracks) {
                Date begin = new Date(track.points.get(0).time);
                Date end = new Date(track.points.get(track.points.size() - 1).time);
                writer.append(formatTime(begin));
                writer.append("-");
                writer.append(formatTime(end));
                writer.append("   ");
                writer.append(String.format(getString(R.string.mileage), track.mileage / 1000));
                writer.append("\r\n");
                writer.append(track.start);
                writer.append(" - ");
                writer.append(track.finish);
                writer.append("\r\n");
                writer.append(String.format(getString(R.string.short_status),
                        timeFormat((int) (track.getTime() / 60)),
                        track.mileage * 3.6 / track.getTime()));
                writer.append("\r\n");
            }
            return true;
        } catch (Exception ex) {
            // ignore
        }
        return false;
    }

    abstract class TrackPositionFetcher extends AddressRequest {

        int id;
        int pos;

        abstract Tracks.Point getPoint(Tracks.Track track);

        abstract TrackPositionFetcher create();

        abstract void process(String[] address);

        abstract void done();

        @Override
        void addressResult(String[] address) {
            if (id != task_id)
                return;
            if (address == null) {
                showError(getString(R.string.error));
                return;
            }

            process(address);
            progressBar.setProgress(++progress);

            if (++pos >= tracks.size()) {
                // All tracks done
                done();
                return;
            }
            TrackPositionFetcher fetcher = create();
            fetcher.update(id, pos);
        }

        void update(int track_id, int track_pos) {
            id = track_id;
            pos = track_pos;
            Tracks.Track track = tracks.get(pos);
            Tracks.Point p = getPoint(track);
            getAddress(preferences, p.lat + "", p.lng + "");
        }

    }

    class TrackStartPositionFetcher extends TrackPositionFetcher {

        @Override
        Tracks.Point getPoint(Tracks.Track track) {
            return track.points.get(0);
        }

        @Override
        TrackPositionFetcher create() {
            return new TrackStartPositionFetcher();
        }

        @Override
        void process(String[] parts) {
            String address = parts[0];
            for (int i = 1; i < parts.length; i++)
                address += ", " + parts[i];
            tracks.get(pos).start = address;
        }

        @Override
        void done() {
            TrackPositionFetcher fetcher = new TrackEndPositionFetcher();
            fetcher.update(id, 0);
        }
    }

    class TrackEndPositionFetcher extends TrackPositionFetcher {

        @Override
        Tracks.Point getPoint(Tracks.Track track) {
            return track.points.get(track.points.size() - 1);
        }

        @Override
        TrackPositionFetcher create() {
            return new TrackEndPositionFetcher();
        }

        @Override
        void process(String[] finish_parts) {
            Tracks.Track track = tracks.get(pos);

            String[] start_parts = track.start.split(", ");

            int s = start_parts.length - 1;
            int f = finish_parts.length - 1;

            while ((s > 2) && (f > 2)) {
                if (!start_parts[s].equals(finish_parts[f]))
                    break;
                s--;
                f--;
            }

            String address = start_parts[0];
            for (int i = 1; i < s; i++) {
                address += ", " + start_parts[i];
            }
            track.start = address;

            address = finish_parts[0];
            for (int i = 1; i < f; i++) {
                address += ", " + finish_parts[i];
            }
            track.finish = address;
        }

        @Override
        void done() {
            allDone();
        }
    }

    class TracksAdapter extends BaseAdapter {

        int selected;

        TracksAdapter() {
            selected = -1;
        }

        @Override
        public int getCount() {
            return tracks.size();
        }

        @Override
        public Object getItem(int position) {
            return tracks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater inflater = (LayoutInflater) getBaseContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.track_item, null);
            }
            TextView tvTitle = (TextView) v.findViewById(R.id.title);
            Tracks.Track track = (Tracks.Track) getItem(position);
            if (track.points.get(0).time != 0) {
                Date begin = new Date(track.points.get(0).time);
                Date end = new Date(track.points.get(track.points.size() - 1).time);
                tvTitle.setText(formatTime(begin) + "-" + formatTime(end));
            } else {
                tvTitle.setText("");
            }

            TextView tvMileage = (TextView) v.findViewById(R.id.mileage);
            String s = String.format(getString(R.string.mileage), track.mileage / 1000);
            tvMileage.setText(s);
            TextView tvAddress = (TextView) v.findViewById(R.id.address);
            tvAddress.setText(track.start + " - " + track.finish);
            TextView tvStatus = (TextView) v.findViewById(R.id.status);
            String text = "";

            if (position == selected) {
                text = String.format(getString(R.string.short_status),
                        timeFormat((int) (track.getTime() / 60)),
                        track.mileage * 3.6 / track.getTime());
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvMileage.setTypeface(null, Typeface.BOLD);
            } else {
                tvTitle.setTypeface(null, Typeface.NORMAL);
                tvMileage.setTypeface(null, Typeface.NORMAL);
            }
            tvStatus.setText(text);
            return v;
        }
    }

}
