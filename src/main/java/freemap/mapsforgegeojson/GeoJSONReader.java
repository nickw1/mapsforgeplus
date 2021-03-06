// GeoJSONReader
// reads some GeoJSON into a PoiWayBundle 

package freemap.mapsforgegeojson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.datastore.PointOfInterest;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class GeoJSONReader {

	private boolean poisOnly;

    static class FeatureTests {
        public static boolean isWaterFeature(String k, String v) {
            return k.equals("natural") && v.equals("water") ||
                    k.equals("waterway");
        }
    
        public static boolean isLandscapeFeature(String k, String v) {
            return k.equals("natural") && v.equals("wood") ||
                    k.equals("landuse") && v.equals("forest") ||
                    k.equals("natural") && v.equals("heath");
        }

        public static boolean isLand(String k, String v) {
            return k.equals("natural") && v.equals("nosea");
        }

        public static boolean isSea(String k, String v) {
            return k.equals("natural") && v.equals("sea");
        }
    }    

    public GeoJSONReader() {
		this(false);
    }

	public GeoJSONReader(boolean poisOnly) {
		this.poisOnly = poisOnly;
	}

    public MapReadResult read(InputStream is, DownloadCache cache,
                                Tile tile) throws IOException, JSONException {
        /*
        PoiWayBundle bundle = new PoiWayBundle
            (new ArrayList<PointOfInterest>(),
            new ArrayList<Way>());
        */
        MapReadResult result = new MapReadResult();
        String jsonString = readFromStream(is);

        JSONObject data = new JSONObject(jsonString);
        JSONArray features = data.getJSONArray("features");
        byte layer;
        if(cache!=null && features.length() > 0) {
            cache.write(tile, jsonString);
        }
        for (int i=0; i<features.length(); i++) {
            JSONObject currentFeature = features.getJSONObject(i);
            String type = currentFeature.getString("type");
            layer=(byte)6; // default for roads, paths etc
            if(type.equals("Feature")) {
                JSONObject geometry = currentFeature.getJSONObject("geometry"),
                    properties = currentFeature.getJSONObject("properties");
                String gType = geometry.getString("type");
				if(!poisOnly || gType.equals("Point")) {
                	ArrayList<Tag> tags = new ArrayList<Tag>();    
                	Iterator it = properties.keys();
                	while(it.hasNext()) {
                    	String k = (String)it.next(), v=properties.getString(k);
                    	if(k.equals("contour")) {
                        	layer = (byte)4; // contours under roads/paths
                    	} else if (k.equals("power")) {
                        	layer = (byte)7; // power lines above all else 
                    	} else if (FeatureTests.isLandscapeFeature(k,v)) {
                        	layer = (byte)3; // woods etc below contours
                    	} else if (FeatureTests.isWaterFeature(k,v)) {
                        	layer = (byte)5; // lakes above contours, below rds
                    	} else if (FeatureTests.isLand(k,v)) {
                        	layer = (byte)2; // land below everything else 
                    	} else if (FeatureTests.isSea(k,v)) {
                        	layer = (byte)1; // land below everything else 
                    	}
                    	tags.add(new Tag(k,v));                
                	}

                	JSONArray coords = geometry.getJSONArray("coordinates");
                	if(gType.equals("Point")) {
                    	LatLong ll = new LatLong
                        	( coords.getDouble(1), coords.getDouble(0) );
                    	PointOfInterest poi = new PointOfInterest
                        	((byte)6, tags, ll); // pois above all else
                    	result.pointOfInterests.add(poi);
                	} else if (gType.equals("LineString")) {
                    	LatLong[][] points = readWayFeature(coords);
                    	Way way = new Way(layer, tags, points, null);
                    	result.ways.add(way);
                	} else if (gType.equals("MultiLineString")) {
                    	LatLong[][] points = readMultiWayFeature(coords);
                    	Way way = new Way(layer, tags, points, null);
                    	result.ways.add(way);
                	} else if (gType.equals("Polygon")) {
                    	// polygons are 3-deep in geojson but only actually 
                    	// contain one line so we simplify them
                    	LatLong[][] points = readWayFeature
							(coords.getJSONArray(0));
                    	Way way = new Way(layer, tags, points, null);
                    	result.ways.add(way);
                	} else if (gType.equals("MultiPolygon")) {
                    	LatLong[][] points=readMultiWayFeature
                        	(coords.getJSONArray(0));
                    	Way way = new Way(layer, tags, points, null);
                    	result.ways.add(way);
                	} 
				}
            }
        }
        return result;
    }

    private String readFromStream (InputStream is) throws IOException {
        byte[] bytes = new byte[1024];
        StringBuffer text = new StringBuffer();

        int bytesRead;

        while((bytesRead = is.read(bytes,0,1024))>=0) {
            text.append(new String(bytes,0,bytesRead));
        }
        return text.toString();
    }


    private LatLong[][] readWayFeature(JSONArray coords) {
        LatLong[][] points = new LatLong[1][];
        points[0] = new LatLong[coords.length()];
        readLineString(points[0], coords);
        return points;
    }

    private void readLineString(LatLong[] points, JSONArray coords) {
        for(int j=0; j<coords.length(); j++) {
            JSONArray curPoint = coords.getJSONArray(j);
            points[j] = new LatLong(curPoint.getDouble(1),
                                        curPoint.getDouble(0));
        }    
    }

    private LatLong[][] readMultiWayFeature (JSONArray coords) {
        LatLong[][] points = new LatLong[coords.length()][];    
        for(int i=0; i<coords.length(); i++) {
            points[i]=new LatLong[coords.getJSONArray(i).length()];
            readLineString(points[i], coords.getJSONArray(i));
        }
        return points;
    }
}
