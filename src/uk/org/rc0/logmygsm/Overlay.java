// Copyright (c) 2013, Richard P. Curnow
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the <organization> nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// Draw overlays onto map tiles - expected use is for depicting map of network coverage

package uk.org.rc0.logmygsm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.Math;
import java.util.ArrayList;

class Overlay {

  static final private String PREFDIR = "/sdcard/LogMyGsm/prefs";
  static final private String TAG = "Overlay";

  static final private int VERSION = 2;
  static final private int MAGIC_NUMBER = 0xfe4f5600 | VERSION;

  static Paint p_thick_red;
  static Paint p_thin_red;
  static Paint p_thin_red_noaa;
  static Paint p_trans_red;
  static Paint p_thick_black;
  static Paint p_thin_black;
  static Paint p_thick_black_dash;
  static Paint p_thin_black_dash;
  static Paint p_thin_blue_noaa;
  static Paint p_transparent_grey;
  static Paint p_wash;
  static Paint p_caption_left, p_caption_right;
  static Paint [] p_colours;
  static float [] anglx;
  static float [] angly;
  static float [] angsx;
  static float [] angsy;

  static final int [][] rgb_table = {
    { 0xFF, 0x2C, 0x2C },
    { 0x00, 0xD0, 0x00 },
    { 0x87, 0x47, 0xFF },
    { 0xD9, 0x78, 0x00 },
    { 0x08, 0xD6, 0xD2 },
    { 0xFF, 0x30, 0xF6 },
    { 0x96, 0xA6, 0x00 },
    { 0x0E, 0x7E, 0xDD },
    { 0xFF, 0x9A, 0x8C },
    { 0x91, 0xE5, 0x64 },
    { 0xF9, 0x8C, 0xFF },
    { 0x5E, 0x08, 0x57 },
    { 0x5A, 0x2C, 0x00 },
    { 0x08, 0x45, 0x00 },
    { 0x18, 0x25, 0x6B }
  };

  static class Recipe {

    // ---------------------------

    // scratch storage during building.
    // allocate just once.
    // Relies on only one Recipe being under construction at a time

    static int [][]missing = new int[16][16];

    // ---------------------------

    static class Feature {
      int sx;
      int sy;
      Feature(int _sx, int _sy) {
        sx = _sx;
        sy = _sy;
        missing[sx][sy] = 0;
      }

      void render(Canvas c) {
      }
    }

    static class Sector extends Feature {
      int size;
      int colour;
      int angle;
      Sector(int _sx, int _sy, int _size, int _colour, int _angle) {
        super(_sx, _sy);
        size = _size;
        colour = _colour;
        angle = _angle;
        //Log.i(TAG, "circle x=" + sx + " y=" + sy + " size=" + size + " colour=" + colour);
      }
      void render(Canvas c) {
        float xc = (float)(8 + (sx << 4));
        float yc = (float)(8 + (sy << 4));
        float sz = (float) size;
        float a = (float) angle * (360.0f / 32.0f);
        a -= 90.0f; // convention for where 0-degrees is
        a -= 120.0f; // start angle not centre of sweep to be provided
        RectF r = new RectF(xc-sz, yc-sz, xc+sz, yc+sz);
        c.drawArc(r, a, 240.0f, false, p_colours[colour]);
        c.drawArc(r, a, 240.0f, false, p_thin_black);
      }
    };

    static class Circle extends Feature {
      int size;
      int colour;
      int angle;
      Circle(int _sx, int _sy, int _size, int _colour) {
        super(_sx, _sy);
        size = _size;
        colour = _colour;
        //Log.i(TAG, "circle x=" + sx + " y=" + sy + " size=" + size + " colour=" + colour);
      }
      void render(Canvas c) {
        float xc, yc, radius;
        xc = sx * 16.0f + 8.0f;
        yc = sy * 16.0f + 8.0f;
        radius = (float) size;
        c.drawCircle(xc, yc, radius, p_colours[colour]);
        c.drawCircle(xc, yc, radius, p_thin_black);
      }
    };

    static class Donut extends Feature {
      int size;
      int colour;
      Donut(int _sx, int _sy, int _size, int _colour) {
        super(_sx, _sy);
        size = _size;
        colour = _colour;
      }
      void render(Canvas c) {
        float xc, yc, radius;
        xc = sx * 16.0f + 8.0f;
        yc = sy * 16.0f + 8.0f;
        radius = (float) size;
        c.drawCircle(xc, yc, radius, p_thick_red);
      }
    };

    static class Tower extends Feature {
      int colour;
      boolean eno;
      String caption;
      static final int margin = 1;
      static final int margin2 = 2;
      Tower(int _sx, int _sy, int _eno, int _colour, String _caption) {
        super(_sx, _sy);
        colour = _colour;
        eno = (_eno == 1);
        caption = _caption;
      }
      void render(Canvas c) {
        float xc = (float)(8 + (sx << 4));
        float yc = (float)(8 + (sy << 4));
        RectF r = new RectF(xc-8.0f, yc-8.0f, xc+8.0f, yc+8.0f);
        c.drawRect(r, p_colours[colour]);
        if (eno) {
          c.drawRect(r, p_thick_black_dash);
        } else {
          c.drawRect(r, p_thick_black);
        }
        float width = p_caption_left.measureText(caption) + margin2;
        if (sx >= 8) {
          c.drawRect(xc+7-width, yc-margin2, xc+7, yc+7, p_wash);
          c.drawText(caption, xc+7-margin, yc+7-margin, p_caption_right);
        } else {
          c.drawRect(xc-8, yc-margin2, xc-8+width, yc+7, p_wash);
          c.drawText(caption, xc-8+margin, yc+7-margin, p_caption_left);
        }
      }
    };

    static class Adjacent extends Feature {
      int colour;
      boolean eno;
      Adjacent(int _sx, int _sy, int _eno, int _colour) {
        super(_sx, _sy);
        colour = _colour;
        eno = (_eno == 1);
      }
      void render(Canvas c) {
        float xc = (float)(8 + (sx << 4));
        float yc = (float)(8 + (sy << 4));
        RectF r = new RectF(xc-5.0f, yc-5.0f, xc+5.0f, yc+5.0f);
        c.drawRect(r, p_colours[colour]);
        if (eno) {
          c.drawRect(r, p_thin_black_dash);
        } else {
          c.drawRect(r, p_thin_black);
        }
      }
    };

    static class Multiple extends Feature {
      Multiple(int _sx, int _sy) {
        super(_sx, _sy);
      }
      void render(Canvas c) {
        float x0 = (float)((sx << 4));
        float y0 = (float)((sy << 4));
        RectF r1 = new RectF(x0+1, y0+1, x0+9, y0+9);
        RectF r2 = new RectF(x0+7, y0+3, x0+15, y0+11);
        RectF r3 = new RectF(x0+3, y0+6, x0+11, y0+14);
        c.drawRect(r1, p_trans_red);
        c.drawRect(r2, p_trans_red);
        c.drawRect(r3, p_trans_red);
        c.drawRect(r1, p_thin_black);
        c.drawRect(r2, p_thin_black);
        c.drawRect(r3, p_thin_black);
      }
    };

    static class Null extends Feature {
      Null(int _sx, int _sy) {
        super(_sx, _sy);
      }
      void render(Canvas c) {
        float xc = (float)(8 + (sx << 4));
        float yc = (float)(8 + (sy << 4));
        c.drawLine(xc-8, yc-4, xc  , yc-8, p_thin_red);
        c.drawLine(xc-8, yc+4, xc+8, yc-4, p_thin_red);
        c.drawLine(xc  , yc+8, xc+8, yc+4, p_thin_red);
        c.drawLine(xc-8, yc  , xc-4, yc+8, p_thin_red);
        c.drawLine(xc-4, yc-8, xc+4, yc+8, p_thin_red);
        c.drawLine(xc+4, yc-8, xc+8, yc  , p_thin_red);
      }

    };

    static class Missing extends Feature {
      int count;
      Missing(int _sx, int _sy, int _count) {
        super(_sx, _sy);
        count = _count;
      }
      void render(Canvas c) {
        float x0 = (float)((sx << 4));
        float y0 = (float)((sy << 4));
        RectF r1 = new RectF(x0+1, y0+1, x0+(count<<4)-2, y0+(count<<4)-2);
        c.drawRect(r1, p_thin_blue_noaa);
      }
    };

    static class HighMissing extends Feature {
      int count;
      HighMissing(int _sx, int _sy, int _count) {
        super(_sx, _sy);
        count = _count;
      }
      void render(Canvas c) {
        float x0 = (float)((sx << 4));
        float y0 = (float)((sy << 4));
        RectF r1 = new RectF(x0+1, y0+1, x0+(count<<4)-2, y0+(count<<4)-2);
        c.drawRect(r1, p_thin_red_noaa);
      }
    };

    static class PlainBox extends Feature {
      PlainBox(int _sx, int _sy) {
        super(_sx, _sy);
      }
      void render(Canvas c) {
        float x0 = (float)((sx << 4));
        float y0 = (float)((sy << 4));
        RectF r1 = new RectF(x0, y0, x0+16, y0+16);
        c.drawRect(r1, p_transparent_grey);
      }
    };

    static class VaneLong extends Feature {
      int angle;
      VaneLong(int _sx, int _sy, int _angle) {
        super(_sx, _sy);
        angle = _angle;
      }
      void render(Canvas c) {
        float xc = (float)(8 + (sx << 4));
        float yc = (float)(8 + (sy << 4));
        c.drawLine(xc, yc, xc + anglx[angle], yc + angly[angle], p_thin_black);
      }
    };

    static class VaneShort extends Feature {
      int angle;
      VaneShort(int _sx, int _sy, int _angle) {
        super(_sx, _sy);
        angle = _angle;
      }
      void render(Canvas c) {
        float xc = (float)(8 + (sx << 4));
        float yc = (float)(8 + (sy << 4));
        c.drawLine(xc, yc, xc + angsx[angle], yc + angsy[angle], p_thin_black);
      }
    };


    // ---------------------------

    ArrayList<Feature> content1;
    ArrayList<Feature> content2;

    // ---------------------------

    private static int read8(RandomAccessFile in, long loc) throws IOException {
      in.seek(loc);
      return in.readUnsignedByte();
    }

    private static byte [] read8_multi(RandomAccessFile in, long loc, int len) throws IOException {
      byte [] result = new byte[len];
      in.seek(loc);
      in.read(result);
      return result;
    }

    private static int read16(RandomAccessFile in, long loc) throws IOException {
      in.seek(loc);
      return in.readUnsignedShort();
    }

    private static int read32(RandomAccessFile in, long loc) throws IOException {
      in.seek(loc);
      return in.readInt();
    }

    static int table1[] = {
      -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0,
      -1, -1, 0, 1, -1, -1, 0, 1, -1, -1, 0, 1, -1, -1, 0, 1,
      -1, -1, -1, -1, 0, 1, 1, 2, -1, -1, -1, -1, 0, 1, 1, 2,
      -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 1, 2, 1, 2, 2, 3
    };

    static int table2[] = {
      0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,
      1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5
    };

    // return position in the file where the required overlay data starts, or -1 if there is none
    private long lookup(RandomAccessFile in, int overlay_param, int zoom, int tile_x, int tile_y) {
      int param_offset;
      switch (overlay_param) {
        case 2:
          param_offset = 0;
          break;
        case 3:
          param_offset = 16;
          break;
        default:
          return -1;
      }

      try {
        int offset;
        long len;
        long loc;
        int magic;

        magic = read32(in, 0);
        if (magic != MAGIC_NUMBER) {
          return -1;
        }

        loc = in.length() - 4;
        offset = read32(in, loc);
        //in.seek(loc);
        //offset = in.readInt();
        loc -= offset; // looking at level 0 table
        while (zoom > 0) {
          zoom -= 1;
          int xz = (tile_x >> zoom) & 1;
          int yz = (tile_y >> zoom) & 1;
          int p = read8(in, loc);
          int idx = (p & 15) + ((xz + xz + yz) << 4);
          int t = table1[idx];
          if (t < 0) {
            return -1;
          }
          if ((p & 0x80) != 0) {
            offset = read32(in, loc + 1 + 4*t);
          } else {
            offset = read16(in, loc + 1 + 2*t);
          }
          loc -= offset;
        }
        int p = read8(in, loc);
        if ((p & 0x3f) != 0) {
          int idx = (p & 15) + param_offset;
          int t = table2[idx];
          if (t < 0) {
            return -1;
          } else {
            if ((p & 0x80) != 0) {
              offset = read32(in, loc + 1 + 4*t);
            } else {
              offset = read16(in, loc + 1 + 2*t);
            }
            loc -= offset;
            return loc;
          }
        } else {
          return -1;
        }
      }
      catch (IOException e) {
        return -1;
      }
    }

    private int SX(int coord) {
      return (coord >> 4) & 15;
    }

    private int SY(int coord) {
      return (coord) & 15;
    }

    private void decode(RandomAccessFile in, long pos, boolean simplify) {
      int b;
      int coord = -1;
      try {
        while (true) {
          b = read8(in, pos);
          int opc = (b >> 4) & 15;
          int displacement = b & 15;
          int opc2;
          int b1, eno, len, colour, size, angle;
          switch (opc) {
            case 0:
            case 1:
              // long vane
              angle = b & 0x1f;
              if (!simplify) {
                content1.add(new VaneLong(SX(coord), SY(coord), angle));
              }
              pos += 1;
              break;
            case 2:
            case 3:
              // short vane
              angle = b & 0x1f;
              if (!simplify) {
                content1.add(new VaneShort(SX(coord), SY(coord), angle));
              }
              pos += 1;
              break;
            case 4:
            case 5:
              // sector
              coord += 1;
              angle = b & 0x1f;
              b1 = read8(in, pos+1);
              size = (b1 >> 4) & 15;
              colour = (b1) & 15;
              if (simplify) {
                content1.add(new PlainBox(SX(coord), SY(coord)));
              } else {
                content1.add(new Sector(SX(coord), SY(coord), size, colour, angle));
              }
              pos += 2;
              break;
            case 6: // regular circle
              coord += 1 + displacement; // embedded skip
              b1 = read8(in, pos+1);
              size = (b1 >> 4) & 15;
              colour = (b1) & 15;
              if (simplify) {
                content1.add(new PlainBox(SX(coord), SY(coord)));
              } else {
                content1.add(new Circle(SX(coord), SY(coord), size, colour));
              }
              pos += 2;
              break;
            case 7: // fat circle
              coord += 1 + displacement; // embedded skip
              b1 = read8(in, pos+1);
              size = (b1 >> 4) & 15;
              colour = (b1) & 15;
              if (simplify) {
                content1.add(new PlainBox(SX(coord), SY(coord)));
              } else {
                content1.add(new Donut(SX(coord), SY(coord), size, colour));
              }
              pos += 2;
              break;
            case 8: // small square (adjacent towers)
              coord += 1 + displacement; // embedded skip
              b1 = read8(in, pos+1);
              eno = (b1 >> 7) & 1;
              colour = (b1) & 15;
              content2.add(new Adjacent(SX(coord), SY(coord), eno, colour));
              pos += 2;
              break;
            case 9: // big square + caption (isolated tower)
              coord += 1 + displacement; // embedded skip
              b1 = read8(in, pos+1);
              eno = (b1 >> 7) & 1;
              len = (b1 >> 4) & 7;
              colour = (b1) & 15;
              byte [] caption = read8_multi(in, pos+2, len);
              content2.add(new Tower(SX(coord), SY(coord), eno, colour, new String(caption)));
              pos += 2 + len;
              break;
            case 10: // multiple squares (several towers in same block)
              coord += 1 + displacement; // embedded skip
              content2.add(new Multiple(SX(coord), SY(coord)));
              pos += 1;
              break;
            case 11: // gate symbol (null coverage)
              coord += 1 + displacement; // embedded skip
              if (simplify) {
                content1.add(new PlainBox(SX(coord), SY(coord)));
              } else {
                content1.add(new Null(SX(coord), SY(coord)));
              }
              pos += 1;
              break;
            case 12: // skip
              coord += 1 + displacement; // embedded skip
              pos += 1;
              break;
            case 13:
            case 14:
              // reserved
              break;
            case 15:
              opc2 = b & 15;
              switch (opc2) {
                case 0: // wide skip
                  b1 = read8(in, pos+1);
                  coord += b1;
                  pos += 2;
                  break;
                case 15: // end of stream
                  return;
                default:
              }
              break;
          }
          if (coord >= 256) {
            // crude safety net against mis-parsing the file and getting
            // trapped in a long-running loop
            break;
          }
        }
      }
      catch (IOException e) {
        return;
      }
    }

    static void clear_missing() {
      for (int i = 0; i < 16; i++) {
        for (int j = 0; j < 16; j++) {
          missing[i][j] = 1;
        }
      }
    }

    private void gather_missing(boolean simplify) {
      for (int lvl = 0; lvl <= 3; lvl++) {
        int step0 = 1<<lvl;
        int step1 = 2<<lvl;
        int count = 1<<lvl;
        int i = 0;
        while (i < 16) {
          int j = 0;
          while (j < 16) {
            if ((missing[i][j] == count) &&
                (missing[i+step0][j] == count) &&
                (missing[i][j+step0] == count) &&
                (missing[i+step0][j+step0] == count))
            {

              missing[i][j] = count + count;
              missing[i+step0][j] = 0;
              missing[i][j+step0] = 0;
              missing[i+step0][j+step0] = 0;
            }
            j += step1;
          }
          i += step1;
        }
      }
      for (int i = 0; i < 16; i++) {
        for (int j = 0; j < 16; j++) {
          int count = missing[i][j];
          if (count > 0) {
            if (simplify) {
              content1.add(new HighMissing(i, j, count));
            } else {
              content1.add(new Missing(i, j, count));
            }
          }
        }
      }
    }

    Recipe(String overlay_file, int overlay_param,
        int zoom, int tile_x, int tile_y, boolean simplify) {

      // simplify is the option to reduce covered areas to grey squares, to 
      // highlight the 'to do' areas better

      RandomAccessFile in;

      content1 = new ArrayList<Feature>();
      content2 = new ArrayList<Feature>();
      clear_missing();

      try {
        in = new RandomAccessFile(PREFDIR + "/" + overlay_file, "r");
      }
      catch (IOException e) {
        in = null;
      }

      if (in != null) {
        long pos = lookup(in, overlay_param, zoom, tile_x, tile_y);
        // Log.i(TAG, "z=" + zoom + " x=" + tile_x + " y=" + tile_y + " pos=" + pos);
        if (pos >= 0) {
          decode(in, pos, simplify);
        }
        try {
          in.close();
        }
        catch (IOException e) {
        }
      }
      gather_missing(simplify);
    }

    void apply2(Canvas c, ArrayList<Feature> content) {
      int n = content.size();
      for (int i = 0; i < n; i++) {
        Feature f = content.get(i);
        f.render(c);
      }
    }

    void apply(Bitmap bm) {
      Canvas c = new Canvas(bm);
      apply2(c, content1);
      apply2(c, content2);
      return;
    }

  }

  static void init() {
    if (p_thick_red != null)
      return;

    p_thin_red_noaa = new Paint();
    p_thin_red_noaa.setStrokeWidth(1);
    p_thin_red_noaa.setColor(Color.RED);
    p_thin_red_noaa.setStyle(Paint.Style.STROKE);

    p_thin_red = new Paint(p_thin_red_noaa);
    p_thin_red.setFlags(Paint.ANTI_ALIAS_FLAG);

    p_thick_red = new Paint(p_thin_red);
    p_thick_red.setStrokeWidth(2);

    p_trans_red = new Paint(Paint.ANTI_ALIAS_FLAG);
    p_trans_red.setStrokeWidth(2);
    p_trans_red.setColor(Color.argb(0x80, 0xff, 0x00, 0x00));
    p_trans_red.setStyle(Paint.Style.FILL);

    p_thick_black = new Paint(Paint.ANTI_ALIAS_FLAG);
    p_thick_black.setStrokeWidth(2);
    p_thick_black.setColor(Color.BLACK);
    p_thick_black.setStyle(Paint.Style.STROKE);

    p_thin_black = new Paint(Paint.ANTI_ALIAS_FLAG);
    p_thin_black.setStrokeWidth(1);
    p_thin_black.setColor(Color.BLACK);
    p_thin_black.setStyle(Paint.Style.STROKE);

    p_thin_blue_noaa = new Paint();
    p_thin_blue_noaa.setStrokeWidth(1);
    p_thin_blue_noaa.setColor(Color.argb(0x80, 0x00, 0x00, 0xff));
    p_thin_blue_noaa.setStyle(Paint.Style.STROKE);

    p_transparent_grey = new Paint(Paint.ANTI_ALIAS_FLAG);
    p_transparent_grey.setColor(Color.argb(0x60, 0x70, 0x70, 0xa0));
    p_transparent_grey.setStyle(Paint.Style.FILL);

    DashPathEffect dpe = new DashPathEffect(new float[] {2.0f,2.0f}, 0.0f);
    p_thick_black_dash = new Paint(p_thick_black);
    p_thick_black_dash.setPathEffect(dpe);

    p_thin_black_dash = new Paint(p_thin_black);
    p_thin_black_dash.setPathEffect(dpe);

    p_caption_left = new Paint(Paint.ANTI_ALIAS_FLAG);
    p_caption_left.setColor(Color.BLACK);
    p_caption_left.setTextSize(9);
    p_caption_left.setTextAlign(Paint.Align.LEFT);
    p_caption_left.setTypeface(Typeface.DEFAULT_BOLD);

    p_caption_right = new Paint(p_caption_left);
    p_caption_right.setTextAlign(Paint.Align.RIGHT);

    p_wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    p_wash.setColor(Color.argb(0x80, 0xff, 0xff, 0xff));
    p_wash.setStyle(Paint.Style.FILL);

    int n = rgb_table.length;
    int alpha = 0x90;
    p_colours = new Paint[n];
    for (int i=0; i < n; i++) {
      p_colours[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
      p_colours[i].setColor(Color.argb(alpha, rgb_table[i][0], rgb_table[i][1], rgb_table[i][2]));
      p_colours[i].setStyle(Paint.Style.FILL);
    }

    anglx = new float[32];
    angly = new float[32];
    angsx = new float[32];
    angsy = new float[32];
    for (int i=0; i<32; i++) {
      double ang = i * (360.0/32.0);
      ang = Math.toRadians(ang);
      anglx[i] = (float)(8.0 * Math.cos(ang));
      angly[i] = (float)(8.0 * Math.sin(ang));
      angsx[i] = (float)(5.0 * Math.cos(ang));
      angsy[i] = (float)(5.0 * Math.sin(ang));
    }
  }

  static void apply(Bitmap bm, String overlay_file, int overlay_param,
      int zoom, int tile_x, int tile_y) {

    init();
    Recipe the_recipe = new Recipe(overlay_file, overlay_param & 7,
        zoom, tile_x, tile_y,
        ((overlay_param & 8) != 0));
    if (the_recipe != null) {
      the_recipe.apply(bm);
    }
    return;
  }
}



// vim:et:sw=2:sts=2

