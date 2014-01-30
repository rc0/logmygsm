// Copyright (c) 2012, Richard P. Curnow
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

package uk.org.rc0.logmygsm;

import android.graphics.Bitmap;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

class MapSource {
  private String menu_name = "";
  String path_segment = "";
  int index;
  private int code;
  final private static String last_hope_path = "/sdcard/LogMyGsm/tiles";
  final private static String possible_paths[] = {
    "/sdcard/external_sd/Maverick/tiles",
    "/sdcard/Maverick/tiles",
    "/sdcard/external_sd/LogMyGsm/tiles",
    last_hope_path
  };

  static final private String PREFS_DIR = "/sdcard/LogMyGsm/prefs/";
  static final private String URL_MAP_FILE = PREFS_DIR + "urls.txt";
  static HashMap<String,String> url_map;

  static final String KEY_MAPNIK = "MAPNIK";
  static final String KEY_OPENCYCLEMAP = "OPENCYCLEMAP";
  static final String KEY_OS_1 = "OS_1";
  static final String KEY_OS_2 = "OS_2";
  static final String KEY_AERIAL_1 = "AERIAL_1";
  static final String KEY_AERIAL_2 = "AERIAL_2";

  static String path_start = null;

  String get_menu_name() {
    return menu_name;
  }

  String get_tile_path(int zoom, int x, int y) {
    return String.format("%s/%s/%d/%d/%d.png.tile",
        path_start, path_segment, zoom, x, y);
  }

  // Override in sub-classes for map types that can support download
  String get_download_url(int zoom, int x, int y) {
    return null;
  }

  void apply_overlay(Bitmap bm, int zoom, int tile_x, int tile_y) {
    return;
  }

  int get_code() {
    return code;
  }

  MapSource(String _menu_name, String _path_segment, int _code) {
    menu_name = _menu_name;
    path_segment = _path_segment;
    code = _code;
    index = -1;
  }

  static void read_url_map() {
    // Store download URLs in a text file outside of the application
    url_map = new HashMap<String, String>();
    // Put dummy values in the map so at least something exists if URL_MAP_FILE
    // fails to load
    url_map.put(KEY_MAPNIK, "//127.0.0.1/%d/%d/%d.png");
    url_map.put(KEY_OPENCYCLEMAP, "//127.0.0.1/%d/%d/%d.png");
    url_map.put(KEY_OS_1, "//127.0.0.1/");
    url_map.put(KEY_OS_2, "");
    url_map.put(KEY_AERIAL_1, "//127.0.0.1/");
    url_map.put(KEY_AERIAL_2, "");

    File f = new File(URL_MAP_FILE);
    BufferedReader br;
    boolean is_open = false;
    if (f.exists()) {
      try {
        br = new BufferedReader(new FileReader(URL_MAP_FILE));
        is_open = true;

        try {
          String key, value;
          while (true) {
            key = br.readLine();
            if (key.compareTo("END") == 0) {
              break;
            }
            value = br.readLine();
            url_map.put(key, value);
          }
          // exception at EOF or any other error
        } catch (IOException e) {
        }
        if (is_open) {
          br.close();
        }
      } catch (IOException e) {
      }
    }
  }

  // Discover where the tiles are stored.  Share tiles with Maverick (-Pro) if
  // possible, otherwise use our own storage
  static void init() {
    for (int i = 0; i < possible_paths.length; i++) {
      File f = new File(possible_paths[i]);
      if (f.isDirectory()) {
        path_start = new String(possible_paths[i]);
        break;
      }
    }

    // We must force a path to exist otherwise there is nowhere to download new
    // tiles into
    if (path_start == null) {
      path_start = new String(last_hope_path);
      File dir = new File(path_start);
      dir.mkdirs();
    }

    read_url_map();
  }

  static final char [] qk03 = "0123" . toCharArray();
  static String get_quadkey(int zoom, int x, int y) {
    char [] quadkey = new char[zoom];
    int i;
    for (i=0; i<zoom; i++) {
      int j = zoom - 1 - i;
      int xx = (x >> j) & 1;
      int yy = (y >> j) & 1;
      quadkey[i] = qk03[xx + (yy<<1)];
    }
    return new String(quadkey);
  }

}

// ------------------------------------------------------------------

class MapSource_Mapnik extends MapSource {

  MapSource_Mapnik(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  String get_download_url(int zoom, int x, int y) {
    return String.format(url_map.get(KEY_MAPNIK), zoom, x, y);
  }

};

// ------------------------------------------------------------------

class MapSource_Cycle extends MapSource {

  MapSource_Cycle(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  String get_download_url(int zoom, int x, int y) {
    return String.format(url_map.get(KEY_OPENCYCLEMAP), zoom, x, y);
  }

};

// ------------------------------------------------------------------

class MapSource_OS extends MapSource {

  MapSource_OS(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  String get_download_url(int zoom, int x, int y) {
    return new String(url_map.get(KEY_OS_1) + get_quadkey(zoom, x, y) + url_map.get(KEY_OS_2));
  }

};

// ------------------------------------------------------------------

class MapSource_Bing_Aerial extends MapSource {

  MapSource_Bing_Aerial(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  String get_download_url(int zoom, int x, int y) {
    return new String(url_map.get(KEY_AERIAL_1) + get_quadkey(zoom, x, y) + url_map.get(KEY_AERIAL_2));
  }

  String get_tile_path(int zoom, int x, int y) {
    return String.format("%s/%s/%d/%d/%d.jpg.tile",
        path_start, path_segment, zoom, x, y);
  }

};

// ------------------------------------------------------------------

class MapSource_Overlay extends MapSource_Mapnik {

  private String overlay_file;
  private int overlay_param;

  MapSource_Overlay(String _menu_name, String _path_segment, int _code, String _overlay_file, int _overlay_param) {
    super(_menu_name, _path_segment, _code);
    overlay_file = _overlay_file;
    overlay_param = _overlay_param;
  }

  void apply_overlay(Bitmap bm, int zoom, int tile_x, int tile_y) {
    Overlay.apply(bm, overlay_file, overlay_param, zoom, tile_x, tile_y);
  }

};

// ------------------------------------------------------------------

class MapSources {
  static final int MAP_OSM     =  2;
  static final int MAP_OS      =  3;
  static final int MAP_OPEN_CYCLE = 4;
  static final int MAP_BING_AERIAL = 5;
  static final int MAP_A_2G_OVL  = 16;
  static final int MAP_A_3G_OVL  = 17;
  static final int MAP_A_2G_TODO_OVL  = 20;
  static final int MAP_A_3G_TODO_OVL  = 21;
  static final int MAP_B_2G_OVL  = 24;
  static final int MAP_B_3G_OVL  = 25;
  static final int MAP_B_2G_TODO_OVL  = 28;
  static final int MAP_B_3G_TODO_OVL  = 29;
  static final int MAP_C_2G_OVL  = 32;
  static final int MAP_C_3G_OVL  = 33;
  static final int MAP_C_2G_TODO_OVL  = 36;
  static final int MAP_C_3G_TODO_OVL  = 37;

  static final MapSource [] sources = {
    new MapSource_Cycle("Open Cycle Map", "OSM Cycle Map", MAP_OPEN_CYCLE),
    new MapSource_Bing_Aerial("Bing Aerial", "Microsoft Hybrid", MAP_BING_AERIAL),
    new MapSource_OS("Ordnance Survey", "Ordnance Survey Explorer Maps (UK)", MAP_OS),
    new MapSource_Mapnik("OpenStreetMap", "mapnik", MAP_OSM),
    new MapSource_Overlay("NetA 2G",      "mapnik", MAP_A_2G_OVL,      "overlay_a.db", 2),
    new MapSource_Overlay("NetA 3G",      "mapnik", MAP_A_3G_OVL,      "overlay_a.db", 3),
    new MapSource_Overlay("NetA 2G todo", "mapnik", MAP_A_2G_TODO_OVL, "overlay_a.db", 8|2),
    new MapSource_Overlay("NetA 3G todo", "mapnik", MAP_A_3G_TODO_OVL, "overlay_a.db", 8|3),
    new MapSource_Overlay("NetB 2G",      "mapnik", MAP_B_2G_OVL,      "overlay_b.db", 2),
    new MapSource_Overlay("NetB 3G",      "mapnik", MAP_B_3G_OVL,      "overlay_b.db", 3),
    new MapSource_Overlay("NetB 2G todo", "mapnik", MAP_B_2G_TODO_OVL, "overlay_b.db", 8|2),
    new MapSource_Overlay("NetB 3G todo", "mapnik", MAP_B_3G_TODO_OVL, "overlay_b.db", 8|3),
    new MapSource_Overlay("NetC 2G",      "mapnik", MAP_C_2G_OVL,      "overlay_c.db", 2),
    new MapSource_Overlay("NetC 3G",      "mapnik", MAP_C_3G_OVL,      "overlay_c.db", 3),
    new MapSource_Overlay("NetC 2G todo", "mapnik", MAP_C_2G_TODO_OVL, "overlay_c.db", 8|2),
    new MapSource_Overlay("NetC 3G todo", "mapnik", MAP_C_3G_TODO_OVL, "overlay_c.db", 8|3),
  };

  static MapSource lookup(int code) {
    for (MapSource source: sources) {
      if (source.get_code() == code) {
        return source;
      }
    }
    return null;
  }

  static final void init_indices() {
    for (int i = 0; i < sources.length; i++) {
      sources[i].index = i;
    }
  }

  static MapSource successor(MapSource current) {
    if (current.index < 0) {
      init_indices();
    }
    int index = current.index + 1;
    if (index >= sources.length) {
      index = 0;
    }
    return sources[index];
  }

  static MapSource predecessor(MapSource current) {
    if (current.index < 0) {
      init_indices();
    }
    int index = current.index - 1;
    if (index  < 0) {
      index = sources.length - 1;
    }
    return sources[index];
  }

  static MapSource get_default() {
    return lookup(MAP_OSM);
  }

}

//
// vim:et:sw=2:sts=2

