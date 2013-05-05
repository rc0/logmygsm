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

import android.util.FloatMath;
import android.util.Log;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import uk.org.rc0.logmygsm.Landmarks.Routing;

class Linkages {
  static final private String TAG = "Linkages";

  // Turns a set of points into a set of "linkages" (in the mechanical sense)
  //
  // Coherency (if the set of points changes) must be managed by the caller

  // ---------------------------

  static class Edge {
    Merc28 m0;
    Merc28 m1;
    Edge(Merc28 _m0, Merc28 _m1) {
      m0 = _m0;
      m1 = _m1;
    }
  };

  // ---------------------------

  private static class WorkingEdge extends Edge {
    int i0;
    int i1;
    float d;
    boolean alive;
    public WorkingEdge(int _a, int _b, Merc28[] points, float _d) {
      super(points[_a], points[_b]);
      i0 = _a;
      i1 = _b;
      d = _d;
      alive = true;
    }
  };

  // ---------------------------

  private static final Comparator<WorkingEdge> edge_comparator =
    new Comparator<WorkingEdge> () {

      public int compare(WorkingEdge e0, WorkingEdge e1) {
        if (e0.d > e1.d) return -1;
        else if (e0.d < e1.d) return +1;
        else if (e0.i0 < e1.i0) return -1;
        else if (e0.i0 > e1.i0) return +1;
        else if (e0.i1 < e1.i1) return -1;
        else if (e0.i1 > e1.i1) return +1;
        else return 0;
      }
    };

  // ---------------------------

  // Cached result for later reads
  private Edge[] edges;
  private WorkingEdge[] full_edges;

  private Merc28 [] points;
  private float[] distances;

  // ---------------------------

  private static Set<WorkingEdge> compute_mesh(Merc28[] p) {
    Set<WorkingEdge> result = new HashSet<WorkingEdge>();
    int n = p.length;
    int i, j, k;

    for (i=0; i<n; i++) {
      for (j=i+1; j<n; j++) {
        // candidate line is i, j
        boolean ok = true;
        for (k=0; k<n; k++) {
          if ((k != i) && (k != j)) {
            // work in FP to avoid integer overflow ?
            float dxi = (float)(p[k].X - p[i].X);
            float dyi = (float)(p[k].Y - p[i].Y);
            float dxj = (float)(p[k].X - p[j].X);
            float dyj = (float)(p[k].Y - p[j].Y);
            if ((dxi * dxj) + (dyi * dyj) < 0.0) {
              ok = false;
              break;
            }
          }
        }
        if (ok) {
          float d = (float) p[i].metres_away(p[j]);
          result.add(new WorkingEdge(i, j, p, d));
        }
      }
    }
    return result;
  }

  // ---------------------------

  private void compute_pruned(int np, Set<WorkingEdge> fussy) {
    // remove edges from the overly-'fussy' initial set.  An edge is surplus to
    // requirements <=> the nodes at both end have >=3 neighbours, and there is
    // another path from one to the other through the network even if this edge
    // is culled.
    //
    int n = fussy.size();
    int[] n_neigh = new int[np];
    int[] eqclass = new int[np];

    WorkingEdge[] working = fussy.toArray(new WorkingEdge[0]);
    Arrays.sort(working, edge_comparator);

    for (int i=0; i<np; i++) n_neigh[i] = 0;
    for (int i=0; i<working.length; i++) {
      ++n_neigh[working[i].i0];
      ++n_neigh[working[i].i1];
    }
    int n_alive = working.length;
    for (int candidate=0; candidate<working.length; candidate++) {
      int i0 = working[candidate].i0;
      int i1 = working[candidate].i1;
      if ((n_neigh[i0] >= 3) && (n_neigh[i1] >= 3)) {
        for (int i=0; i<np; i++) eqclass[i] = i;
        boolean active = false;
        do {
          active = false;
          for (int e = 0; e < working.length; e++) {
            if (e == candidate) continue;
            if (working[e].alive) {
              int e0 = working[e].i0;
              int e1 = working[e].i1;
              // Double dereference to get log-order iteration count (I think)
              // even if the edges are sorted in the worst possible order along
              // a chain
              int h0 = eqclass[e0];
              int h1 = eqclass[e1];
              int c0 = eqclass[h0];
              int c1 = eqclass[h1];
              int cc = (c0 < c1) ? c0 : c1;
              if (eqclass[e0] != cc) {
                eqclass[e0] = cc;
                active = true;
              }
              if (eqclass[e1] != cc) {
                eqclass[e1] = cc;
                active = true;
              }
            }
          }
        } while (active && (eqclass[i0] != eqclass[i1]));
        if (eqclass[i1] == eqclass[i0]) {
          working[candidate].alive = false;
          --n_neigh[i0];
          --n_neigh[i1];
          --n_alive;
        }
      }
    }

    //Log.i(TAG, "Start with " + working.length + ", " + n_alive + " remain");
    edges = new Edge[n_alive];
    full_edges = new WorkingEdge[n_alive];
    int i, j;
    for (i = j = 0; i<working.length; i++) {
      if (working[i].alive) {
        edges[j] = working[i];
        full_edges[j] = working[i];
        j++;
      }
    }
  }

  // ---------------------------

  private void do_meshing(ArrayList<Merc28> _points) {
    // defensive copy of the points fed in
    points = new Merc28[_points.size()];
    for (int i = 0; i < _points.size(); i++) {
      points[i] = new Merc28(_points.get(i));
    }

    Set<WorkingEdge> mesh = compute_mesh(points);
    compute_pruned(points.length, mesh); // sets up 'edges' and 'full_edges'
  }

  // ---------------------------


  // ---------------------------

  private static class Endpoint {
    int index; // point index number
    Endpoint[] siblings; // the other endpoints at the same physical point
    Endpoint peer; // the other end of the line segment
    Segment via; // the path to the other end
    BitSet downstream;
    boolean distance_ok; // valid distance coming into this endpoint from ref point
    float distance; // distance to ref point via this endpoint
    boolean pending; // already on todo queue

    float sibling_distance() {
      float d = 0.0f;
      boolean ok = false;
      for (int i=0; i<siblings.length; i++) {
        Endpoint sib = siblings[i];
        if (sib.distance_ok) {
          if (!ok || (sib.distance < d)) {
            d = sib.distance;
            ok = true;
          }
        }
      }
      // This should not get called unless one of the siblings has a defined distance!
      return d;
    }

    int nearest_sibling() {
      int best_index = -1;
      float d = 0.0f;
      boolean ok = false;
      for (int i=0; i<siblings.length; i++) {
        Endpoint sib = siblings[i];
        if (sib.distance_ok) {
          if (!ok || (sib.distance < d)) {
            d = sib.distance;
            best_index  = i;
            ok = true;
          }
        }
      }
      // This should not get called unless one of the siblings has a defined distance!
      return best_index;
    }

    boolean take_new_distance(float d, Segment via, BitSet _downstream) {
      if (!distance_ok || (d < distance)) {
        distance_ok = true;
        distance = d;
        downstream = new BitSet();
        downstream.or(_downstream);
        downstream.set(via.index, true);
        return true;
      } else {
        return false;
      }
    }

    boolean is_destination() {
      return (distance_ok && (distance == 0.0f)) ? true : false;
    }
  }

  private static class Segment {
    Endpoint e0;
    Endpoint e1;
    int index;
    float distance;
    Segment(Endpoint _e0, Endpoint _e1, int _index) {
      e0 = _e0;
      e1 = _e1;
      index = _index;
    }
  }

  // ---------------------------

  private Endpoint[][] endpoints;
  private Segment[] segments;

  // ---------------------------

  static float minimum_endpoint_distance(Endpoint[] eps) {
    float d = 0.0f;
    boolean ok = false;
    for (int i=0; i<eps.length; i++) {
      if (eps[i].distance_ok) {
        if (!ok || (eps[i].distance < d)) {
          d = eps[i].distance;
          ok = true;
        }
      }
    }
    return d;
  }

  // ---------------------------

  private void do_distances(int _destination) {
    int [] n_ep;
    int np = points.length;
    int ne = edges.length;
    n_ep = new int[np];
    for (int i=0; i<np; i++) {
      n_ep[i] = 0;
    }
    for (int i=0; i<ne; i++) {
      ++n_ep[full_edges[i].i0];
      ++n_ep[full_edges[i].i1];
    }

    // Now build siblings
    endpoints = new Endpoint[np][];
    for (int i=0; i<np; i++) {
      endpoints[i] = new Endpoint[n_ep[i]];
      for (int j=0; j<n_ep[i]; j++) {
        Endpoint e = endpoints[i][j] = new Endpoint();
        e.index = i;
        e.siblings = new Endpoint[n_ep[i] - 1];
        e.distance_ok = false;
        e.pending = false;
      }
      for (int j=0; j<n_ep[i]; j++) {
        int k, m;
        for (k=m=0; k<n_ep[i]; k++) {
          if (j != k) {
            endpoints[i][j].siblings[m++] = endpoints[i][k];
          }
        }
      }
    }
    int [] used_ep = new int[np];
    for (int i=0; i<np; i++) {
      used_ep[i] = 0;
    }

    segments = new Segment[ne];
    for (int i=0; i<ne; i++) {
      int i0 = full_edges[i].i0;
      int i1 = full_edges[i].i1;
      int u0 = used_ep[i0]++;
      int u1 = used_ep[i1]++;
      Endpoint e0 = endpoints[i0][u0];
      Endpoint e1 = endpoints[i1][u1];
      Segment s = segments[i] = new Segment(e0, e1, i);
      e0.peer = e1;
      e1.peer = e0;
      e0.via = e1.via = s;
      s.distance = (float) full_edges[i].d;
    }

    LinkedList<Endpoint> todo = new LinkedList<Endpoint> ();

    for (int i=0; i<n_ep[_destination]; i++) {
      Endpoint e = endpoints[_destination][i];
      e.distance = 0.0f;
      e.distance_ok = true;
      e.downstream = new BitSet();
      e.pending = true;
      todo.add(e);
    }

    int count = 0;

    // Propagate distances through the mesh
    while (todo.size() > 0) {
      count++;
      if (count > 16384) {
        // safety net
        break;
      }
      Endpoint e = todo.removeFirst();
      e.pending = false;

      Endpoint e2 = e.peer;
      boolean expand_e2 = false;
      int best_index = e.nearest_sibling();
      if (best_index >= 0) {
        Endpoint best_sibling = e.siblings[best_index];
        float distance = best_sibling.distance + e.via.distance;
        if (!best_sibling.downstream.get(e.via.index) &&
            e2.take_new_distance(distance, e.via, best_sibling.downstream)) {
          expand_e2 = true;
        }
      } else {
        float distance = e.via.distance;
        if (e2.take_new_distance(distance, e.via, new BitSet())) {
          expand_e2 = true;
        }
      }
      if (expand_e2) {
        for (int i=0; i<e2.siblings.length; i++) {
          Endpoint ee = e2.siblings[i];
          if (!ee.pending) {
            todo.add(ee);
            ee.pending = true;
          }
        }
      }
    }

    distances = new float[np];
    for (int i=0; i<np; i++) {
      distances[i] = minimum_endpoint_distance(endpoints[i]);
    }

  }

  // ---------------------------


  // ---------------------------

  public Linkages(ArrayList<Merc28> _points) {
    do_meshing(_points);
  }

  // ---------------------------
  // ---------------------------

  public Linkages(ArrayList<Merc28> _points, int _destination) {
    do_meshing(_points);
    do_distances(_destination);
  }

  // ---------------------------

  public Edge[] get_edges() {
    // note - allows corruption of the shared Merc28 points underneath
    return edges;
  }


  // ---------------------------
  // ---------------------------

  // Code to work out the distances through the mesh to a given 'destination' node.

  static float calculate_distance(Endpoint e) {
    boolean found = false;
    float dist = 0.0f;;
    for (int i=0; i<e.siblings.length; i++) {
      if (e.siblings[i].distance_ok) {
        if (!found || (e.siblings[i].distance < dist)) {
          dist = e.siblings[i].distance;
          found = true;
        }
      }
    }
    return found ? dist : -1.0f;
  }

  static float calculate_distance(Endpoint e, Segment exclude_seg) {
    boolean found = false;
    float dist = 0.0f;;
    for (int i=0; i<e.siblings.length; i++) {
      if (e.siblings[i].distance_ok &&
          !e.siblings[i].downstream.get(exclude_seg.index)) {
        if (!found || (e.siblings[i].distance < dist)) {
          dist = e.siblings[i].distance;
          found = true;
        }
      }
    }
    return found ? dist : -1.0f;
  }

  // Return cosine of angle subtended at p by the lines from p to p0, p1 respectively
  static float cos_subtended(Merc28 p, Merc28 p0, Merc28 p1) {
    float dx0 = (float) p0.X - (float) p.X;
    float dx1 = (float) p1.X - (float) p.X;
    float dy0 = (float) p0.Y - (float) p.Y;
    float dy1 = (float) p1.Y - (float) p.Y;
    float ca = (dx0 * dx1 + dy0 * dy1) / FloatMath.sqrt((dx0*dx0 + dy0*dy0) * (dx1*dx1 + dy1*dy1));
    return ca;
  }

  private Routing[] gather(Routing r0, Routing r1) {
    Routing[] result;
    if (r0 != null) {
      if (r1 != null) {
        result = new Routing[2];
        result[0] = r0;
        result[1] = r1;
      } else {
        result = new Routing[1];
        result[0] = r0;
      }
    } else {
      if (r1 != null) {
        result = new Routing[1];
        result[0] = r1;
      } else {
        result = null;
      }
    }
    return result;
  }

  Routing[] get_routings(Merc28 pos) {

    if (endpoints == null) {
      return null;
    }

    if (points.length == 0) {
      return null;
    } else if (points.length == 1) {
      // points[0] is necessarily the destination
      Routing r0 = new Landmarks.Routing(pos, points[0], 0.0f);
      return gather(r0, null);
    } else {
      // find the segment such that 'pos' subtends the largest angle at its endpoints
      int best_index = -1;
      float best_ca = 1.0f;
      for (int i = 0; i < segments.length; i++) {
        int i0 = segments[i].e0.index;
        int i1 = segments[i].e1.index;
        float ca = cos_subtended(pos, points[i0], points[i1]);
        if (ca < best_ca) {
          best_ca = ca;
          best_index = i;
        }
      }

      if (best_ca < 0.0) {
        // The best segment subtends > 90 degrees at 'pos' : assume we're close to it

        Landmarks.Routing r0=null, r1=null;
        Endpoint e0 = segments[best_index].e0;
        Endpoint e1 = segments[best_index].e1;
        if (e0.is_destination()) {
          r0 = new Routing(pos, points[e0.index], 0.0f);
        } else {
          float d0 = calculate_distance(e0, segments[best_index]);
          if (d0 >= 0.0f) {
            r0 = new Routing(pos, points[e0.index], d0);
          }
        }
        if (e1.is_destination()) {
          r1 = new Routing(pos, points[e1.index], 0.0f);
        } else {
          float d1 = calculate_distance(e1, segments[best_index]);
          if (d1 >= 0.0f) {
            r1 = new Routing(pos, points[e1.index], d1);
          }
        }
        return gather(r0, r1);
      } else {
        // The so-called 'best' segment subtends less than 90 degrees.  We're
        // too far away from it, or too close to one of its ends.
        //
        // For this case, seek the closest point in the mesh.  Then find which
        // pair of its neighbours subtend the maximum angle at 'pos', and show
        // the routings through those

        int best_pt_index = -1;
        float best_distance = 0.0f;
        for (int i=0; i<points.length; i++) {
          float distance = (float) pos.metres_away(points[i]);
          if ((best_pt_index < 0) ||
              (distance < best_distance)) {
            best_distance = distance;
            best_pt_index = i;
              }
        }

        Routing r0=null, r1=null;

        if (endpoints[best_pt_index].length == 1) {
          // at the end of the chain
          Endpoint nearest = endpoints[best_pt_index][0];
          Endpoint next = nearest.peer;
          if (nearest.is_destination()) {
            r0 = new Routing(pos, points[nearest.index], 0.0f);
          } else if (next.is_destination()) {
            r0 = new Routing(pos, points[next.index], 0.0f);
          } else {
            float dist = calculate_distance(next, nearest.via);
            r0 = new Routing(pos, points[next.index], dist);
          }
        } else {
          Endpoint[] ea = endpoints[best_pt_index];
          if (ea[0].is_destination()) { // could check any index
            r0 = new Routing(pos, points[best_pt_index], 0.0f);
          } else {
            Endpoint best_ep0, best_ep1;
            best_ca = 1.0f;
            best_ep0 = best_ep1 = null;
            int n = ea.length;
            for (int i=0; i<n; i++) {
              for (int j=i+1; j<n; j++) {
                int ix0 = ea[i].peer.index;
                int ix1 = ea[j].peer.index;
                float ca = cos_subtended(pos, points[ix0], points[ix1]);
                if ((best_ep0 == null) ||
                    (ca < best_ca)) {
                  best_ca = ca;
                  best_ep0 = ea[i].peer;
                  best_ep1 = ea[j].peer;
                }
              }
            }

            if (best_ep0.is_destination()) {
              r0 = new Routing(pos, points[best_ep0.index], 0.0f);
            } else {
              float d0;
              d0 = calculate_distance(best_ep0);
              r0 = (d0 >= 0.0f) ? new Routing(pos, points[best_ep0.index], d0) : null;
            }
            if (best_ep1.is_destination()) {
              r1 = new Routing(pos, points[best_ep1.index], 0.0f);
            } else {
              float d1;
              d1 = calculate_distance(best_ep1);
              r1 = (d1 >= 0.0f) ? new Routing(pos, points[best_ep1.index], d1) : null;
            }
          }
        }

        return gather(r0, r1);

      }
    }
  }

}


// vim:et:sw=2:sts=2
//

