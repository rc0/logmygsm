package uk.org.rc0.helloandroid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

// Storage for the the waypoints that the user can define

public class Landmarks {

  private ArrayList<Merc28> points;
  private Paint marker_paint;

  static final private String TAIL = "markers.txt";
  public Landmarks() {
    restore_state_from_file();

    marker_paint = new Paint();
    marker_paint.setStrokeWidth(4);
    marker_paint.setColor(Color.argb(0xc0, 0x80, 0x00, 0x20));
    marker_paint.setStyle(Paint.Style.STROKE);

  }

  public void save_state_to_file() {
    File dir = new File("/sdcard/LogMyGsm/prefs");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    File file = new File(dir, TAIL);
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(file));
      int n = points.size();
      bw.write(String.format("%d\n", n));
      for (int i=0; i < n; i++) {
        bw.write(String.format("%d\n", points.get(i).X));
        bw.write(String.format("%d\n", points.get(i).Y));
      }
      bw.close();
    } catch (IOException e) {
    }
  }

  private void restore_state_from_file() {
    points = new ArrayList<Merc28> ();
    File file = new File("/sdcard/LogMyGsm/prefs/" + TAIL);
    boolean failed = false;
    if (file.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        line = br.readLine();
        int n = Integer.parseInt(line);
        for (int i = 0; i < n; i++) {
          line = br.readLine();
          int x = Integer.parseInt(line);
          line = br.readLine();
          int y = Integer.parseInt(line);
          points.add(new Merc28(x, y));
        }
        br.close();
      } catch (IOException e) {
        failed = true;
      } catch (NumberFormatException n) {
        failed = true;
      }
    }
    if (failed) {
      points = new ArrayList<Merc28> ();
    }

  }

  public void add(Merc28 pos) {
    points.add(new Merc28(pos.X, pos.Y));
  }

  // Return value is true if a deletion successfully occurred, false if no point was
  // close enough to 'pos' to qualify.  Only delete the point that is 'closest'
  public boolean delete(Merc28 pos) {
    return false;
  }

  public void delete_all() {
    points = new ArrayList<Merc28> ();
  }

  private final static int RADIUS = 8;


  // pos is the position of the centre-screen
  public void draw(Canvas c, Merc28 pos, int w, int h, int pixel_shift) {
    int n = points.size();
    for (int i = 0; i < n; i++) {
      Merc28 p = points.get(i);
      int dx = (p.X - pos.X) >> pixel_shift;
      int x = (w>>1) + dx;
      int dy = (p.Y - pos.Y) >> pixel_shift;
      int y = (h>>1) + dy;
      c.drawCircle(x, y, (float) RADIUS, marker_paint);
      c.drawPoint(x, y, marker_paint);
    }
  }
}

// vim:et:sw=2:sts=2
//
