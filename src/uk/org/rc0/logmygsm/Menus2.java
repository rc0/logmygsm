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

package uk.org.rc0.logmygsm;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.util.Log;

class Menus2 {

  static private final int OPTION_GROUP_MASK_HI    = 0xf0;
  static private final int OPTION_GROUP_MASK_LO    = 0x0f;

  static final int OPTION_LOCAL_BASE       = 0x10;
  static final int OPTION_MAP_BASE         = 0x20;
  static final int OPTION_DOWNLOAD_BASE    = 0x30;

  static final int OPTION_TOGGLE_TOWERLINE = OPTION_LOCAL_BASE | 0xf;
  static final int TILE_SCALING            = 0xf;
  static final int OPTION_TILE_SCALING     = OPTION_MAP_BASE | TILE_SCALING;

  static private final int DOWNLOAD_SINGLE      = 0;
  static private final int DOWNLOAD_MISSING     = 1;
  static private final int DOWNLOAD_33          = 2;
  static private final int DOWNLOAD_55          = 3;
  static private final int DOWNLOAD_LEV_0       = 4;
  static private final int DOWNLOAD_LEV_1       = 5;
  static private final int DOWNLOAD_LEV_2       = 6;
  static private final int DOWNLOAD_LEV_0_FORCE = 7;

  static MenuItem[] insert_maps_menu(Menu parent) {
    SubMenu sub = parent.addSubMenu(0, 0, Menu.NONE, "Maps");
    MenuItem toggle;
    MenuItem[] toggles = new MenuItem[2];
    sub.setIcon(android.R.drawable.ic_menu_mapmode);
    for (MapSource source : MapSources.sources) {
      sub.add (Menu.NONE, OPTION_MAP_BASE + source.get_code(), Menu.NONE, source.get_menu_name());
    }
    toggles[0] = sub.add (Menu.NONE, OPTION_TILE_SCALING, Menu.NONE,
        String.format("Scale tiles by %.1fx", Map.TILE_SCALING));
    toggles[0].setCheckable(true);
    toggles[1] = sub.add (Menu.NONE, OPTION_TOGGLE_TOWERLINE, Menu.NONE, "Show towerline");
    toggles[1].setCheckable(true);
    return toggles;
  }

  static void insert_download_menu(Menu parent) {
      SubMenu m_download =
        parent.addSubMenu (0, 0, Menu.NONE, "Download(s)");
      m_download.setIcon(android.R.drawable.ic_menu_view);
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_SINGLE, Menu.NONE, "Central tile");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_0, Menu.NONE, "Missing ..,0 levels");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_1, Menu.NONE, "Missing ..,0,1 levels");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_2, Menu.NONE, "Missing ..,0,1,2 levels");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_MISSING, Menu.NONE, "Recent missing");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_LEV_0_FORCE, Menu.NONE, "Force ..,0 levels");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_33, Menu.NONE, "3x3 region");
      m_download.add (Menu.NONE, OPTION_DOWNLOAD_BASE + DOWNLOAD_55, Menu.NONE, "5x5 region");
  }

   static boolean decode_download_option(int subcode, Context context, Map map) {
     switch (subcode) {
       case DOWNLOAD_SINGLE:
         map.trigger_fetch_around(0, context);
         return true;
       case DOWNLOAD_MISSING:
         TileStore.trigger_fetch(context);
         return true;
       case DOWNLOAD_33:
         map.trigger_fetch_around(1, context);
         return true;
       case DOWNLOAD_55:
         map.trigger_fetch_around(2, context);
         return true;
       case DOWNLOAD_LEV_0:
         map.trigger_fetch_tree(0, false, context);
         return true;
       case DOWNLOAD_LEV_1:
         map.trigger_fetch_tree(1, false, context);
         return true;
       case DOWNLOAD_LEV_2:
         map.trigger_fetch_tree(2, false, context);
         return true;
       case DOWNLOAD_LEV_0_FORCE:
         map.trigger_fetch_tree(0, true, context);
         return true;
       default:
         return false;
     }
   }

   static boolean decode_map_option(int subcode, Map map) {
     MapSource source;
     switch (subcode) {
       case TILE_SCALING:
         Log.i("Menus", "Tile scaling decoded");
         map.toggle_scaled();
         return true;
       default:
         source = MapSources.lookup(subcode);
         if (source != null) {
           //Log.i("Menus", "Match " + source.get_menu_name());
           map.select_map_source(source);
           return true;
         } else {
           Log.i("Menus", "No match for code " + subcode);
           return false;
         }
     }
   }


    static int group(int code) {
      return code & OPTION_GROUP_MASK_HI;
    }

    static int option(int code) {
      return code & OPTION_GROUP_MASK_LO;
    }
}

// vim:et:sw=2:sts=2
