package ru.shutoff.track_manager;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;
import java.util.Vector;

public class Tracks {

    static class Point {
        double lat;
        double lng;
        double speed;
        long time;
    }

    static class Track {
        Vector<Point> points;
        String start;
        String finish;
        double mileage;

        long getTime() {
            if (points.size() <= 1)
                return 0;
            return (points.get(points.size() - 1).time - points.get(0).time) / 1000;
        }
    }

    static Vector<Track> load(int d, int m, int y) {
        try {
            File file = Environment.getExternalStorageDirectory();
            file = new File(file, "CityGuide/Tracks");
            file = new File(file, String.format("%04d_%02d_%02d_gps.plt", y, m, d));
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            if (!line.equals("OziExplorer Track Point File Version 2.1"))
                return null;
            line = reader.readLine();
            if (!line.equals("WGS 84"))
                return null;
            reader.readLine();
            reader.readLine();
            reader.readLine();
            reader.readLine();
            double prev_lat = 0;
            double prev_lng = 0;
            double prev_alt = -777;
            long prev_time = 0;
            Vector<Point> points = new Vector<Point>();
            for (; ; ) {
                line = reader.readLine();
                if (line == null)
                    break;
                String[] parts = line.split(",");
                try {
                    double lat = Double.parseDouble(parts[0]);
                    double lng = Double.parseDouble(parts[1]);
                    double alt = Double.parseDouble(parts[2]);
                    String[] date = parts[5].split("-");
                    String[] times = parts[6].split("-");
                    int year = Integer.parseInt(date[0]);
                    int month = Integer.parseInt(date[1]);
                    int day = Integer.parseInt(date[2]);
                    int hour = Integer.parseInt(times[0]);
                    int min = Integer.parseInt(times[1]);
                    int sec = Integer.parseInt(times[2]);
                    Date dd = new Date(year - 1900, month - 1, day, hour, min, sec);
                    long time = dd.getTime();
                    if ((prev_alt != -777) && (alt != -777)) {
                        double da = Math.abs(alt - prev_alt);
                        if (da < 15) {
                            Point p = new Point();
                            p.lat = prev_lat;
                            p.lng = prev_lng;
                            p.time = prev_time;
                            points.add(p);
                        }
                    }
                    prev_lat = lat;
                    prev_lng = lng;
                    prev_alt = alt;
                    prev_time = time;
                } catch (Exception ex) {
                    // ignore
                }
            }
            if (prev_alt != -777) {
                Point p = new Point();
                p.lat = prev_lat;
                p.lng = prev_lng;
                p.time = prev_time;
                points.add(p);
            }
            int start = 0;
            if (points.size() > 0) {
                Point last = points.get(points.size() - 1);
                Point p = new Point();
                p.lat = last.lat;
                p.lng = last.lng;
                p.time = last.time + 10 * 60 * 1000;
                points.add(p);
            }
            Vector<Track> res = new Vector<Track>();
            for (int i = 1; i < points.size(); i++) {
                Point p1 = points.get(i - 1);
                Point p2 = points.get(i);
                if (p2.time - p1.time > 5 * 60 * 1000) {
                    if (i > start + 1) {
                        Point p = points.get(start);
                        Track t = new Track();
                        t.points = new Vector<Point>();
                        double distance = 0;
                        for (int n = start + 1; n < i; n++) {
                            Point c = points.get(n);
                            if (c.time < p.time)
                                continue;
                            distance = calc_distance(p.lat, p.lng, c.lat, c.lng);
                            double speed = (distance * 3600) / (c.time - p.time);
                            if (speed < 250) {
                                t.points.add(p);
                                t.mileage += distance;
                                p = c;
                                p.speed = speed;
                                continue;
                            }
                            if (t.points.size() == 0)
                                p = c;
                        }
                        t.points.add(p);
                        if ((t.points.size() > 4) && (t.mileage > 100))
                            res.add(t);
                    }
                    start = i;
                }
            }
            return res;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    static final double D2R = 0.017453; // Константа для преобразования градусов в радианы
    static final double a = 6378137.0; // Основные полуоси
    static final double e2 = 0.006739496742337; // Квадрат эксцентричности эллипсоида

    static double calc_distance(double lat1, double lon1, double lat2, double lon2) {

        if ((lat1 == lat2) && (lon1 == lon2))
            return 0;

        double fdLambda = (lon1 - lon2) * D2R;
        double fdPhi = (lat1 - lat2) * D2R;
        double fPhimean = ((lat1 + lat2) / 2.0) * D2R;

        double fTemp = 1 - e2 * (Math.pow(Math.sin(fPhimean), 2));
        double fRho = (a * (1 - e2)) / Math.pow(fTemp, 1.5);
        double fNu = a / (Math.sqrt(1 - e2 * (Math.sin(fPhimean) * Math.sin(fPhimean))));

        double fz = Math.sqrt(Math.pow(Math.sin(fdPhi / 2.0), 2) +
                Math.cos(lat2 * D2R) * Math.cos(lat1 * D2R) * Math.pow(Math.sin(fdLambda / 2.0), 2));
        fz = 2 * Math.asin(fz);

        double fAlpha = Math.cos(lat1 * D2R) * Math.sin(fdLambda) * 1 / Math.sin(fz);
        fAlpha = Math.asin(fAlpha);

        double fR = (fRho * fNu) / ((fRho * Math.pow(Math.sin(fAlpha), 2)) + (fNu * Math.pow(Math.cos(fAlpha), 2)));

        return fz * fR;
    }

}