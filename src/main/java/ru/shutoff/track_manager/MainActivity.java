package ru.shutoff.track_manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
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
import android.widget.ListView;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Vector;

public class MainActivity extends ActionBarActivity {

    CaldroidFragment caldroidFragment;
    boolean mode;
    Menu topSubMenu;
    SharedPreferences preferences;

    File path;
    Object[] entries;
    Date current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            File file = new File(getIntent().getData().getPath());
            if (file != null) {
                Intent intent = new Intent(MainActivity.this, TracksActivity.class);
                intent.putExtra(Names.TITLE, file.getName());
                intent.putExtra(Names.PATH, file.getAbsolutePath());
                startActivity(intent);
                finish();
            }
        } catch (Exception ex) {
            // ignore
        }

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
        setContentView(R.layout.main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mode = preferences.getBoolean(Names.MODE, false);
        path = Environment.getExternalStorageDirectory();
        current = new Date();

        caldroidFragment = new CaldroidFragment();
        if (savedInstanceState != null) {
            caldroidFragment.restoreStatesFromKey(savedInstanceState,
                    "CALDROID_SAVED_STATE");
            mode = savedInstanceState.getBoolean(Names.MODE, mode);
            String saved_path = savedInstanceState.getString(Names.PATH);
            if (saved_path != null)
                path = new File(saved_path);
            current = new Date(savedInstanceState.getLong(Names.CURRENT, current.getTime()));
        } else {
            Bundle args = new Bundle();
            LocalDateTime d = new LocalDateTime();
            int year = d.getYear();
            int month = d.getMonthOfYear();
            args.putInt(CaldroidFragment.MONTH, month);
            args.putInt(CaldroidFragment.YEAR, year);
            args.putInt(CaldroidFragment.START_DAY_OF_WEEK, 1);
            caldroidFragment.setArguments(args);
        }

        LocalDateTime now = new LocalDateTime();
        caldroidFragment.setMaxDate(now.toDate());
        caldroidFragment.setSelectedDates(current, current);
        caldroidFragment.setCaldroidListener(new CaldroidListener() {
            @Override
            public void onSelectDate(Date date, View view) {
                Intent intent = new Intent(MainActivity.this, TracksActivity.class);
                LocalDateTime d = new LocalDateTime(date);
                intent.putExtra(Names.TITLE, d.toString("dd MMMM"));
                File file = Environment.getExternalStorageDirectory();
                file = new File(file, Tracks.TRACK_FOLDER);
                file = new File(file, String.format("%04d_%02d_%02d_gps.plt", d.getYear(), d.getMonthOfYear(), d.getDayOfMonth()));
                intent.putExtra(Names.PATH, file.getAbsolutePath());
                startActivity(intent);
            }

            @Override
            public boolean isDateEnabled(DateTime date) {
                File file = Environment.getExternalStorageDirectory();
                file = new File(file, Tracks.TRACK_FOLDER);
                file = new File(file, String.format("%04d_%02d_%02d_gps.plt", date.getYear(), date.getMonthOfYear(), date.getDayOfMonth()));
                return file.exists();
            }
        });

        FragmentTransaction t = getSupportFragmentManager().beginTransaction();
        t.replace(R.id.calendar, caldroidFragment);
        t.commit();

        ListView list = (ListView) findViewById(R.id.files);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DirEntry entry = (DirEntry) entries[position];
                if (entry.type == 0) {
                    Intent intent = new Intent(MainActivity.this, TracksActivity.class);
                    intent.putExtra(Names.TITLE, entry.name);
                    File file = new File(path, entry.name);
                    intent.putExtra(Names.PATH, file.getAbsolutePath());
                    startActivity(intent);
                    return;
                }
                if (entry.type == 2) {
                    path = path.getParentFile();
                } else {
                    path = new File(path, entry.name);
                }
                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                findViewById(R.id.files).setVisibility(View.GONE);
                entries = null;
                loadDirectory();
            }
        });

        setMode();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        caldroidFragment.saveStatesToKey(outState, "CALDROID_SAVED_STATE");
        outState.putBoolean(Names.MODE, mode);
        outState.putString(Names.PATH, path.getAbsolutePath());
        outState.putLong(Names.CURRENT, current.getTime());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        topSubMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(mode ? R.menu.files : R.menu.calendar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.file:
                mode = true;
                setMode();
                return true;
            case R.id.day:
                mode = false;
                setMode();
                return true;
            case R.id.preferences: {
                Intent i = new Intent(this, Preferences.class);
                startActivity(i);
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    void setMode() {
        findViewById(R.id.calendar).setVisibility(mode ? View.GONE : View.VISIBLE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.files).setVisibility(View.GONE);
        if (topSubMenu != null) {
            topSubMenu.clear();
            onCreateOptionsMenu(topSubMenu);
        }
        SharedPreferences.Editor ed = preferences.edit();
        ed.putBoolean(Names.MODE, mode);
        ed.commit();
        if (mode) {
            if (entries == null) {
                loadDirectory();
                return;
            }
            ListView list = (ListView) findViewById(R.id.files);
            list.setAdapter(new FilesAdapter());
            list.setVisibility(View.VISIBLE);
        }
    }

    void loadDirectory() {
        if (!path.exists())
            path = Environment.getExternalStorageDirectory();
        AsyncTask<String, Void, Object[]> task = new AsyncTask<String, Void, Object[]>() {

            @Override
            protected Object[] doInBackground(String... params) {
                File dir = new File(params[0]);

                Object[] root_dir = new Object[0];
                if (!dir.getAbsolutePath().equals("/")) {
                    DirEntry entry = new DirEntry();
                    entry.name = "..";
                    entry.type = 2;
                    root_dir = new Object[1];
                    root_dir[0] = entry;
                }

                File[] files = dir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if (pathname.isHidden())
                            return false;
                        return pathname.isDirectory();
                    }
                });
                Vector<DirEntry> dirs = new Vector<DirEntry>();
                if (files != null) {
                    for (File f : files) {
                        DirEntry entry = new DirEntry();
                        entry.name = f.getName();
                        entry.type = 1;
                        dirs.add(entry);
                    }
                    Collections.sort(dirs, new Comparator<DirEntry>() {
                        @Override
                        public int compare(DirEntry lhs, DirEntry rhs) {
                            return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
                        }
                    });
                }
                Object[] res_dir = dirs.toArray();

                files = dir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if (pathname.isHidden() || pathname.isDirectory())
                            return false;
                        String name = pathname.getName();
                        int pos = name.lastIndexOf(".");
                        if (pos < 0)
                            return false;
                        name = name.substring(pos + 1).toLowerCase();
                        return name.equals("plt") || name.equals("gpx");
                    }
                });
                dirs = new Vector<DirEntry>();
                if (files != null) {
                    for (File f : files) {
                        DirEntry entry = new DirEntry();
                        entry.name = f.getName();
                        entry.type = 0;
                        dirs.add(entry);
                    }
                    Collections.sort(dirs, new Comparator<DirEntry>() {
                        @Override
                        public int compare(DirEntry lhs, DirEntry rhs) {
                            return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
                        }
                    });
                }
                Object[] res_files = dirs.toArray();

                Object[] res = new Object[root_dir.length + res_dir.length + res_files.length];
                System.arraycopy(root_dir, 0, res, 0, root_dir.length);
                System.arraycopy(res_dir, 0, res, root_dir.length, res_dir.length);
                System.arraycopy(res_files, 0, res, root_dir.length + res_dir.length, res_files.length);
                return res;
            }

            @Override
            protected void onPostExecute(Object[] dirEntries) {
                entries = dirEntries;
                if (mode) {
                    ListView list = (ListView) findViewById(R.id.files);
                    list.setAdapter(new FilesAdapter());
                    list.setVisibility(View.VISIBLE);
                    findViewById(R.id.progress).setVisibility(View.GONE);
                }
            }
        };
        task.execute(path.getAbsolutePath());
    }

    class FilesAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return entries.length;
        }

        @Override
        public Object getItem(int position) {
            return entries[position];
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
                v = inflater.inflate(R.layout.file_item, null);
            }
            TextView tvName = (TextView) v.findViewById(R.id.name);
            DirEntry entry = (DirEntry) entries[position];
            tvName.setText(entry.name);
            tvName.setTypeface(null, (entry.type > 0) ? Typeface.BOLD : Typeface.NORMAL);
            return v;
        }
    }

    static class DirEntry {
        String name;
        int type;
    }

    ;

}
