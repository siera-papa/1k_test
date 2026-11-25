package rs.project1k.plan;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.Binder;import org.json.*;import java.util.*;

/** Read-only provider. Default denied; the user authorizes the dashboard's applicationId in Settings. */
public final class PlanProvider extends ContentProvider {
    PlanStore store;
    @Override public boolean onCreate(){store=new PlanStore(getContext());return true;}
    void authorize(){if(Binder.getCallingUid()==android.os.Process.myUid())return;String allowed=getContext().getSharedPreferences("access",Context.MODE_PRIVATE).getString("dashboard_package","");String caller=getCallingPackage();if(allowed.isEmpty()||caller==null||!allowed.equals(caller))throw new SecurityException("Dashboard pristup nije dozvoljen za ovaj paket");}
    @Override public String getType(Uri uri){authorize();return "vnd.android.cursor.dir/vnd.rs.project1k.plan.json";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){authorize();if(selection!=null||sortOrder!=null)throw new IllegalArgumentException("SQL selection/sort not supported");if(projection!=null)throw new IllegalArgumentException("Use null projection; response columns are fixed");List<String> p=uri.getPathSegments();try {
        MatrixCursor result;
        if(p.size()==1&&p.get(0).equals("schema")){result=new MatrixCursor(new String[]{"version","json"});result.addRow(new Object[]{1,new JSONObject().put("version",1).put("libraries",store.libraries()).toString()});}
        else if(p.size()==1&&p.get(0).equals("libraries")){result=new MatrixCursor(new String[]{"id","name","json"});JSONArray libs=store.libraries();for(int i=0;i<libs.length();i++){JSONObject l=libs.getJSONObject(i);result.addRow(new Object[]{l.getString("id"),l.getString("name"),l.toString()});}}
        else if(p.size()==3&&p.get(0).equals("libraries")&&p.get(2).equals("records")){result=new MatrixCursor(new String[]{"id","library_id","label","json","created_at","updated_at"});JSONArray rows=store.records(p.get(1));for(int i=0;i<rows.length();i++)addRecord(result,rows.getJSONObject(i));}
        else if(p.size()==2&&p.get(0).equals("records")){result=new MatrixCursor(new String[]{"id","library_id","label","json","created_at","updated_at"});JSONObject r=store.record(p.get(1));if(r!=null)addRecord(result,r);}
        else if(p.size()==2&&p.get(0).equals("references")){result=new MatrixCursor(new String[]{"library_id","field_id","record_id","json"});JSONArray libs=store.libraries();for(int i=0;i<libs.length();i++){JSONObject l=libs.getJSONObject(i);JSONArray fs=l.getJSONArray("fields"),rows=store.records(l.getString("id"));for(int j=0;j<rows.length();j++){JSONObject r=rows.getJSONObject(j);for(int k=0;k<fs.length();k++){JSONObject f=fs.getJSONObject(k);if(!f.getString("type").equals("link"))continue;JSONArray ids=r.getJSONObject("data").optJSONArray(f.getString("id"));if(ids==null)continue;for(int x=0;x<ids.length();x++)if(ids.getString(x).equals(p.get(1))){result.addRow(new Object[]{l.getString("id"),f.getString("id"),r.getString("id"),r.toString()});break;}}}}}
        else throw new IllegalArgumentException("Unknown URI: "+uri);
        result.setNotificationUri(getContext().getContentResolver(),PlanStore.BASE);return result;
    }catch(JSONException e){throw new IllegalStateException(e);}}
    void addRecord(MatrixCursor c,JSONObject r)throws JSONException{c.addRow(new Object[]{r.getString("id"),r.getString("library_id"),store.label(r),r.toString(),r.getLong("created_at"),r.getLong("updated_at")});}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException("Read only");}
}
