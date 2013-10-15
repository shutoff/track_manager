package ru.shutoff.track_manager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.SpannedString;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.joda.time.LocalDateTime;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

public class MainActivity extends ActionBarActivity {

    SharedPreferences preferences;

    Vector<Tracks.Track> tracks;

    CaldroidFragment dialogCaldroidFragment;

    ProgressBar progressFirst;
    ProgressBar progressBar;
    TextView tvLoading;
    TextView tvStatus;
    ListView lvTracks;

    int task_id;
    int month;
    int year;
    String cur_task;

    int progress;
    boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.tracks);
        progressFirst = (ProgressBar) findViewById(R.id.first_progress);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        tvLoading = (TextView) findViewById(R.id.loading);
        tvStatus = (TextView) findViewById(R.id.status);
        lvTracks = (ListView) findViewById(R.id.tracks);

        lvTracks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TracksAdapter adapter = (TracksAdapter) lvTracks.getAdapter();
                if (adapter.selected == position) {
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

        changeDate(new Date());
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
            case R.id.day: {
                dialogCaldroidFragment = new CaldroidFragment() {

                    @Override
                    public void onAttach(Activity activity) {
                        super.onAttach(activity);
                        CaldroidListener listener = new CaldroidListener() {

                            @Override
                            public void onSelectDate(Date date, View view) {
                                dialogCaldroidFragment.dismiss();
                                dialogCaldroidFragment = null;
                                changeDate(date);
                            }
                        };
                        dialogCaldroidFragment = this;
                        setCaldroidListener(listener);
                    }

                };
                Bundle args = new Bundle();
                args.putString(CaldroidFragment.DIALOG_TITLE, getString(R.string.day));
                args.putInt(CaldroidFragment.MONTH, month);
                args.putInt(CaldroidFragment.YEAR, year);
                args.putInt(CaldroidFragment.START_DAY_OF_WEEK, 1);
                dialogCaldroidFragment.setArguments(args);
                LocalDateTime now = new LocalDateTime();
                dialogCaldroidFragment.setMaxDate(now.toDate());
                dialogCaldroidFragment.show(getSupportFragmentManager(), "TAG");
                break;
            }
        }
        return false;
    }


    void changeDate(final Date d) {
        loaded = false;
        progressFirst.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        tvLoading.setVisibility(View.VISIBLE);
        task_id++;
        cur_task = task_id + "";
        month = d.getMonth() + 1;
        year = d.getYear() + 1900;
        setTitle(format(d, "d MMMM yyyy"));
        AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                if (cur_task.equals(params[0]))
                    tracks = Tracks.load(d.getDate(), month, year);
                return params[0];
            }

            @Override
            protected void onPostExecute(String aVoid) {
                if (cur_task.equals(aVoid))
                    tracksDone();
            }
        };
        task.execute(cur_task);
    }

    void tracksDone() {
        progressFirst.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        if (tracks == null) {
            showError(getString(R.string.no_data));
            return;
        }
        if (tracks.size() == 0) {
            showError(getString(R.string.no_data));
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
            Date begin = new Date(track.points.get(0).time);
            Date end = new Date(track.points.get(track.points.size() - 1).time);
            tvTitle.setText(formatTime(begin) + "-" + formatTime(end));
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

    void showTrack(int index) {
        Intent intent = new Intent(this, TrackView.class);
        Tracks.Track track = tracks.get(index);

        Tracks.Point p = track.points.get(0);
        StringBuilder track_data = new StringBuilder();
        track_data.append(p.lat);
        track_data.append(",");
        track_data.append(p.lng);
        track_data.append(",");
        track_data.append(infoMark(p.time, track.start));
        Date begin = new Date(p.time);
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
        Date end = new Date(p.time);
        intent.putExtra(Names.TRACK, track_data.toString());
        intent.putExtra(Names.STATUS, String.format(getString(R.string.status),
                track.mileage / 1000,
                timeFormat((int) (track.getTime() / 60)),
                track.mileage * 3.6 / track.getTime()));
        intent.putExtra(Names.TITLE, format(begin, "d MMMM HH:mm") + "-" + format(end, "HH:mm"));
        startActivity(intent);
    }

    void showDay() {
        Intent intent = new Intent(this, TrackView.class);
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

        intent.putExtra(Names.TRACK, track_data.toString());
        intent.putExtra(Names.TITLE, getTitle());
        startActivity(intent);
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

    static String formatTime(Date d) {
        return String.format("%02d:%02d", d.getHours(), d.getMinutes());
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
}
