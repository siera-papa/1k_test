package rs.project1k.plan;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.net.Uri;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Dynamic schema; stable field IDs are independent of display names. All writes are transactional. */
public final class PlanStore extends SQLiteOpenHelper {
    public static final Uri BASE = Uri.parse("content://rs.project1k.plan.data");
    private final Context context;
    public PlanStore(Context c) { super(c.getApplicationContext(), "plan.db", null, 1); context=c.getApplicationContext(); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE libraries(id TEXT PRIMARY KEY, definition TEXT NOT NULL, position INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY, library_id TEXT NOT NULL REFERENCES libraries(id) ON DELETE CASCADE, data TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX records_library ON records(library_id, updated_at)");
        try (InputStream in=context.getAssets().open("schema.json")) {
            JSONObject schema=new JSONObject(new String(readBytes(in), StandardCharsets.UTF_8));
            JSONArray libs=schema.getJSONArray("libraries");
            for(int i=0;i<libs.length();i++) putLibrary(db, libs.getJSONObject(i), i);
        } catch(Exception e) { throw new IllegalStateException("Cannot load initial schema", e); }
    }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Unsupported database upgrade"); }
    private static void putLibrary(SQLiteDatabase db,JSONObject lib,int pos) throws JSONException {
        ContentValues v=new ContentValues();v.put("id",lib.getString("id"));v.put("definition",lib.toString());v.put("position",pos);
        if(db.insertWithOnConflict("libraries",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0) throw new IllegalStateException("Library write failed");
    }
    public static byte[] readBytes(InputStream in) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>64*1024*1024)throw new IOException("Backup je prevelik (maks. 64 MB)");out.write(b,0,n);}return out.toByteArray(); }
    public JSONArray libraries() {
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT definition FROM libraries ORDER BY position",null)) {
            while(c.moveToNext()) a.put(new JSONObject(c.getString(0)));
        } catch(JSONException e) { throw new IllegalStateException(e); }return a;
    }
    public JSONObject library(String id) { try(Cursor c=getReadableDatabase().rawQuery("SELECT definition FROM libraries WHERE id=?",new String[]{id})) { return c.moveToFirst()?new JSONObject(c.getString(0)):null; }catch(JSONException e){throw new IllegalStateException(e);} }
    public JSONObject byName(String name) {JSONArray a=libraries();for(int i=0;i<a.length();i++){JSONObject l=a.optJSONObject(i);if((l.optString("name").equals(name)||l.optJSONObject("source")!=null&&l.optJSONObject("source").optString("title").equals(name)))return l;}return null;}
    public static JSONObject field(JSONObject lib,String name) {if(lib==null)return null;JSONArray a=lib.optJSONArray("fields");for(int i=0;i<a.length();i++){JSONObject f=a.optJSONObject(i);if((f.optString("name").equals(name)||f.optJSONObject("source")!=null&&f.optJSONObject("source").optString("tt").equals(name)))return f;}return null;}
    public JSONArray records(String libId) {
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,data,created_at,updated_at FROM records WHERE library_id=? ORDER BY created_at DESC,id",new String[]{libId})) {
            while(c.moveToNext()) a.put(new JSONObject().put("id",c.getString(0)).put("library_id",libId).put("data",new JSONObject(c.getString(1))).put("created_at",c.getLong(2)).put("updated_at",c.getLong(3)));
        }catch(JSONException e){throw new IllegalStateException(e);}return a;
    }
    public JSONObject record(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT library_id,data,created_at,updated_at FROM records WHERE id=?",new String[]{id})) {
            return c.moveToFirst()?new JSONObject().put("id",id).put("library_id",c.getString(0)).put("data",new JSONObject(c.getString(1))).put("created_at",c.getLong(2)).put("updated_at",c.getLong(3)):null;
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    public String label(JSONObject r) {
        if(r==null)return "Obrisan zapis";
        JSONObject lib=library(r.optString("library_id")),d=r.optJSONObject("data");JSONArray fs=lib.optJSONArray("fields");
        String titleField=lib.optString("titleField");
        if(!titleField.isEmpty()&&!d.optString(titleField).isEmpty())return d.optString(titleField);
        for(int i=0;i<fs.length();i++){JSONObject f=fs.optJSONObject(i);String n=f.optString("name").toLowerCase(Locale.ROOT);if(n.equals("naziv")||n.equals("ime ili nadimak")||n.equals("opis")){String s=d.optString(f.optString("id"));if(!s.isEmpty())return s;}}
        for(int i=0;i<fs.length();i++){JSONObject f=fs.optJSONObject(i);if(f.optString("type").equals("text")){String s=d.optString(f.optString("id"));if(!s.isEmpty())return s;}}
        JSONObject idf=field(lib,"id");return lib.optString("name")+" #"+(idf==null?r.optString("id").substring(0,Math.min(6,r.optString("id").length())):d.optString(idf.optString("id"),"?"));
    }
    private JSONObject nodeOrderField(String libraryId){
        JSONObject lib=library(libraryId);
        if(lib==null)return null;
        JSONObject source=lib.optJSONObject("source");
        if(!lib.optString("name").equals("Čvor")&&(source==null||!source.optString("title").equals("Čvor")))return null;
        return field(lib,"redosled");
    }
    private static java.math.BigDecimal nodeOrder(JSONObject record,String fieldId){
        if(record==null)return null;
        Object value=record.optJSONObject("data").opt(fieldId);
        if(value==null||value==JSONObject.NULL)return null;
        try{return new java.math.BigDecimal(value.toString());}catch(NumberFormatException e){return null;}
    }
    private static int compareNodeOrder(JSONObject left,JSONObject right,String fieldId){
        java.math.BigDecimal a=nodeOrder(left,fieldId),b=nodeOrder(right,fieldId);
        if(a==null||b==null)return a==b?0:a==null?1:-1;
        return a.compareTo(b);
    }
    public JSONArray orderedRecords(String libraryId,JSONArray rows){
        JSONObject order=nodeOrderField(libraryId);
        if(order==null||rows==null||rows.length()<2)return rows;
        ArrayList<JSONObject> sorted=new ArrayList<>();
        for(int i=0;i<rows.length();i++)sorted.add(rows.optJSONObject(i));
        String fieldId=order.optString("id");
        sorted.sort((a,b)->compareNodeOrder(a,b,fieldId));
        return new JSONArray(sorted);
    }
    /** Returns the record UUID whose visible numeric id is largest; never compares UUIDs as numbers. */
    public static String maxIdRecord(JSONObject lib,JSONArray rows){
        if(lib==null||rows==null)return "";
        JSONObject idField=null;JSONArray fields=lib.optJSONArray("fields");
        if(fields==null)return "";
        for(int i=0;i<fields.length();i++){
            JSONObject f=fields.optJSONObject(i);
            if(f.optString("name").equalsIgnoreCase("id")){idField=f;break;}
        }
        if(idField==null)idField=field(lib,"id");
        if(idField==null)return "";
        String fieldId=idField.optString("id"),picked="";java.math.BigDecimal max=null;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i),data=row==null?null:row.optJSONObject("data");
            Object value=data==null?null:data.opt(fieldId);
            if(value==null||value==JSONObject.NULL)continue;
            try{
                java.math.BigDecimal number=new java.math.BigDecimal(value.toString().trim());
                String id=row.optString("id");
                if(!id.isEmpty()&&(max==null||number.compareTo(max)>0||number.compareTo(max)==0&&id.compareTo(picked)>0)){
                    max=number;picked=id;
                }
            }catch(NumberFormatException ignored){}
        }
        return picked;
    }
    public String defaultLink(JSONObject field,String target){
        if(!field.optString("type").equals("link")||!field.optString("linkDefault").equals("max_id")||target.isEmpty())return "";
        return maxIdRecord(library(target),records(target));
    }
    public static boolean conditional(JSONObject field) {
        return field.optString("type").equals("link") && !field.optString("conditionField").isEmpty();
    }
    public static String linkTarget(JSONObject field, JSONObject data) {
        if (!conditional(field)) return field.optString("target");
        JSONObject targets = field.optJSONObject("targets");
        String choice = data == null ? "" : data.optString(field.optString("conditionField"));
        return targets == null || choice.isEmpty() ? "" : targets.optString(choice);
    }
    public static boolean referencesTarget(JSONObject field,String target){
        if(!field.optString("type").equals("link"))return false;
        if(!conditional(field))return field.optString("target").equals(target);
        JSONObject targets=field.optJSONObject("targets");if(targets==null)return false;
        for(Iterator<String> it=targets.keys();it.hasNext();)if(targets.optString(it.next()).equals(target))return true;
        return false;
    }
    private static JSONObject fieldById(JSONObject lib, String id) {
        JSONArray fields = lib.optJSONArray("fields");
        for (int i=0;i<fields.length();i++) if(fields.optJSONObject(i).optString("id").equals(id))return fields.optJSONObject(i);
        return null;
    }
    private void validateTargets(JSONObject lib) throws JSONException {
        JSONArray fields=lib.getJSONArray("fields");
        for(int i=0;i<fields.length();i++){
            JSONObject f=fields.getJSONObject(i);
            if(!f.optString("type").equals("link"))continue;
            if(conditional(f)){
                JSONObject targets=f.getJSONObject("targets");
                for(Iterator<String> it=targets.keys();it.hasNext();){String target=targets.getString(it.next());
                    if(!target.equals(lib.optString("id"))&&library(target)==null)throw new IllegalArgumentException("Povezana tabela ne postoji");}
            } else if(!f.optString("target").equals(lib.optString("id"))&&library(f.optString("target"))==null)throw new IllegalArgumentException("Povezana tabela ne postoji");
        }
    }
    public JSONArray orderedLinks(JSONObject field,JSONArray ids){
        if(ids==null||ids.length()<2)return ids;
        JSONObject first=conditional(field)?record(ids.optString(0)):null;
        JSONObject order=nodeOrderField(first==null?field.optString("target"):first.optString("library_id"));
        if(order==null)return ids;
        ArrayList<String> sorted=new ArrayList<>();Map<String,JSONObject> targets=new HashMap<>();
        for(int i=0;i<ids.length();i++){String id=ids.optString(i);sorted.add(id);targets.put(id,record(id));}
        String fieldId=order.optString("id");
        sorted.sort((a,b)->compareNodeOrder(targets.get(a),targets.get(b),fieldId));
        return new JSONArray(sorted);
    }
    public String display(JSONObject f,Object val) {
        if(val==null||val==JSONObject.NULL)return "—";
        if(val instanceof JSONArray){JSONArray a=f.optString("type").equals("link")?orderedLinks(f,(JSONArray)val):(JSONArray)val;ArrayList<String>s=new ArrayList<>();for(int i=0;i<a.length();i++)s.add(f.optString("type").equals("link")?label(record(a.optString(i))):a.optString(i));return s.isEmpty()?"—":String.join(", ",s);}
        if(val instanceof Boolean)return (Boolean)val?"Da":"Ne";
        return val.toString().isEmpty()?"—":val.toString();
    }
    private void changed(){context.getContentResolver().notifyChange(BASE,null);AutoBackup.request(context);}
    public void saveLibrary(JSONObject lib) throws JSONException {
        validateLibrary(lib);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            validateTargets(lib);
            JSONArray existingRows=records(lib.getString("id"));
            for(int i=0;i<existingRows.length();i++)validateData(lib,existingRows.getJSONObject(i).getJSONObject("data"),true,false);
            validateUniqueLibrary(lib, null, null);
            // Update preserves records: never REPLACE an existing parent with ON DELETE CASCADE.
            ContentValues v=new ContentValues();v.put("definition",lib.toString());
            if(db.update("libraries",v,"id=?",new String[]{lib.getString("id")})==0){int pos=(int)DatabaseUtils.longForQuery(db,"SELECT COALESCE(MAX(position),-1)+1 FROM libraries",null);putLibrary(db,lib,pos);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}changed();
    }
    public void removeField(String libId,String fid) throws JSONException {
        JSONObject lib=library(libId);
        JSONArray dependencies=lib.getJSONArray("fields");
        for(int i=0;i<dependencies.length();i++){JSONObject f=dependencies.getJSONObject(i);if(conditional(f)&&f.optString("conditionField").equals(fid))throw new IllegalArgumentException("Prvo isključi uslovnu vezu u koloni: "+f.optString("name"));}
        JSONArray fs=lib.getJSONArray("fields"),next=new JSONArray();for(int i=0;i<fs.length();i++)if(!fs.getJSONObject(i).getString("id").equals(fid))next.put(fs.getJSONObject(i));
        pruneUniqueGroups(lib, Collections.singleton(fid));
        lib.put("fields",next);if(lib.optString("titleField").equals(fid))lib.remove("titleField");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            ContentValues v=new ContentValues();v.put("definition",lib.toString());db.update("libraries",v,"id=?",new String[]{libId});
            JSONArray rows=records(libId);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i),d=r.getJSONObject("data");d.remove(fid);updateData(db,r.getString("id"),d);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}changed();
    }
    public String saveRecord(String libId,String id,JSONObject data) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();String rid=id==null?UUID.randomUUID().toString():id;try {
            JSONObject lib=library(libId);if(lib==null)throw new IllegalArgumentException("Tabela ne postoji");
            JSONObject existing=id==null?null:record(id);if(id!=null&&(existing==null||!existing.optString("library_id").equals(libId)))throw new IllegalArgumentException("Zapis ne postoji u tabeli");
            JSONArray fs=lib.getJSONArray("fields");for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);String fid=f.getString("id");if(f.optBoolean("auto")&&id==null){long max=0;JSONArray rows=records(libId);for(int j=0;j<rows.length();j++)max=Math.max(max,rows.getJSONObject(j).getJSONObject("data").optLong(fid));data.put(fid,max+1);}}
            validateData(lib,data,true,true);
            validateUniqueLibrary(lib, id, data);
            long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("id",rid);v.put("library_id",libId);v.put("data",data.toString());v.put("created_at",existing==null?now:existing.getLong("created_at"));v.put("updated_at",now);
            if(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Record write failed");db.setTransactionSuccessful();
        }finally{db.endTransaction();}changed();return rid;
    }
    private static void updateData(SQLiteDatabase db,String rid,JSONObject d){ContentValues v=new ContentValues();v.put("data",d.toString());v.put("updated_at",System.currentTimeMillis());db.update("records",v,"id=?",new String[]{rid});}
    public void deleteLibrary(String id) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            if(library(id)==null)throw new IllegalArgumentException("Tabela ne postoji");
            JSONArray libs=libraries();
            for(int i=0;i<libs.length();i++){
                JSONObject other=libs.getJSONObject(i);
                String otherId=other.getString("id");
                if(otherId.equals(id))continue;
                JSONArray fields=other.getJSONArray("fields"),kept=new JSONArray();
                Set<String> removed=new HashSet<>(), conditionalFields=new HashSet<>();
                for(int j=0;j<fields.length();j++){
                    JSONObject f=fields.getJSONObject(j);
                    if(conditional(f)){
                        JSONObject targets=f.getJSONObject("targets");ArrayList<String> keys=new ArrayList<>();
                        for(Iterator<String> it=targets.keys();it.hasNext();){String key=it.next();if(targets.optString(key).equals(id))keys.add(key);}
                        for(String key:keys)targets.remove(key);
                        if(!keys.isEmpty())conditionalFields.add(f.getString("id"));
                        kept.put(f);
                    }else if(f.optString("type").equals("link")&&f.optString("target").equals(id))removed.add(f.getString("id"));
                    else kept.put(f);
                }
                if(removed.isEmpty()&&conditionalFields.isEmpty())continue;
                pruneUniqueGroups(other, removed);
                other.put("fields",kept);
                if(removed.contains(other.optString("titleField")))other.remove("titleField");
                ContentValues definition=new ContentValues();definition.put("definition",other.toString());
                db.update("libraries",definition,"id=?",new String[]{otherId});
                JSONArray rows=records(otherId);
                for(int j=0;j<rows.length();j++){
                    JSONObject row=rows.getJSONObject(j),data=row.getJSONObject("data");
                    for(String fid:removed)data.remove(fid);
                    for(String fid:conditionalFields){JSONArray old=data.optJSONArray(fid),keep=new JSONArray();
                        if(old!=null)for(int k=0;k<old.length();k++){JSONObject linked=record(old.optString(k));if(linked!=null&&!linked.optString("library_id").equals(id))keep.put(old.optString(k));}
                        data.put(fid,keep);
                    }
                    updateData(db,row.getString("id"),data);
                }
            }
            // Explicit records deletion also keeps the operation clear if a future schema changes the FK.
            db.delete("records","library_id=?",new String[]{id});
            db.delete("libraries","id=?",new String[]{id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        changed();
    }
    public void deleteRecord(String id) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            JSONArray libs=libraries();for(int i=0;i<libs.length();i++){JSONObject l=libs.getJSONObject(i);JSONArray rows=records(l.getString("id")),fs=l.getJSONArray("fields");for(int j=0;j<rows.length();j++){JSONObject r=rows.getJSONObject(j),d=r.getJSONObject("data");boolean dirty=false;for(int k=0;k<fs.length();k++){JSONObject f=fs.getJSONObject(k);if(!f.optString("type").equals("link"))continue;String fid=f.getString("id");JSONArray old=d.optJSONArray(fid);if(old==null)continue;JSONArray n=new JSONArray();for(int x=0;x<old.length();x++)if(!old.optString(x).equals(id))n.put(old.optString(x));if(n.length()!=old.length()){d.put(fid,n);dirty=true;}}if(dirty)updateData(db,r.getString("id"),d);}}
            db.delete("records","id=?",new String[]{id});
            for(int i=0;i<libs.length();i++)validateUniqueLibrary(libs.getJSONObject(i),null,null);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}changed();
    }
    private static Object uniqueValue(Object value) throws JSONException {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            ArrayList<Object> selection = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) selection.add(uniqueValue(array.get(i)));
            return selection;
        }
        return value;
    }

    private void validateUniqueLibrary(JSONObject lib, String ignoredRecordId, JSONObject proposed) throws JSONException {
        JSONArray fields = lib.getJSONArray("fields");
        JSONArray groups = lib.optJSONArray("uniqueGroups");
        boolean needsCheck = groups != null && groups.length() > 0;
        for (int i = 0; i < fields.length(); i++) needsCheck |= fields.getJSONObject(i).optBoolean("unique");
        if (!needsCheck) return;
        ArrayList<JSONObject> data = new ArrayList<>();
        JSONArray rows = records(lib.getString("id"));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (!row.getString("id").equals(ignoredRecordId)) data.add(row.getJSONObject("data"));
        }
        if (proposed != null) data.add(proposed);
        Map<String,String> names = new HashMap<>();
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            String fid = field.getString("id");
            names.put(fid, field.getString("name"));
            if (!field.optBoolean("unique")) continue;
            ArrayList<Object> values = new ArrayList<>();
            for (JSONObject row : data) values.add(uniqueValue(row.opt(fid)));
            UniqueValues.requireDistinct(values, field.getString("name"));
        }
        if (groups == null) return;
        for (int i = 0; i < groups.length(); i++) {
            JSONArray group = groups.getJSONArray(i);
            ArrayList<String> labels = new ArrayList<>();
            for (int j = 0; j < group.length(); j++) labels.add(names.get(group.getString(j)));
            ArrayList<List<?>> tuples = new ArrayList<>();
            for (JSONObject row : data) {
                ArrayList<Object> tuple = new ArrayList<>();
                for (int j = 0; j < group.length(); j++) tuple.add(uniqueValue(row.opt(group.getString(j))));
                tuples.add(tuple);
            }
            UniqueValues.requireDistinctTuples(tuples, String.join(" + ", labels));
        }
    }

    private static void pruneUniqueGroups(JSONObject lib, Set<String> removedFields) throws JSONException {
        JSONArray groups = lib.optJSONArray("uniqueGroups");
        if (groups == null) return;
        JSONArray kept = new JSONArray();
        for (int i = 0; i < groups.length(); i++) {
            JSONArray group = groups.getJSONArray(i);
            boolean affected = false;
            for (int j = 0; j < group.length(); j++) affected |= removedFields.contains(group.getString(j));
            // Do not silently turn a combination into a stricter constraint on remaining columns.
            if (!affected) kept.put(group);
        }
        lib.put("uniqueGroups", kept);
    }

    public static void validateLibrary(JSONObject l) throws JSONException {
        if(l.getString("id").trim().isEmpty()||l.getString("name").trim().isEmpty())throw new IllegalArgumentException("Naziv tabele je obavezan");
        Set<String>ids=new HashSet<>(),names=new HashSet<>();JSONArray fs=l.getJSONArray("fields");
        for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);if(f.getString("id").trim().isEmpty()||!ids.add(f.getString("id")))throw new IllegalArgumentException("Dupli ID polja");if(f.getString("name").trim().isEmpty()||!names.add(f.getString("name").toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Naziv polja mora biti jedinstven");if(!Arrays.asList("text","integer","decimal","boolean","date","datetime","choice","multichoice","link").contains(f.getString("type")))throw new IllegalArgumentException("Nepodržan tip polja");if(f.getString("type").equals("link")&&!conditional(f)&&f.optString("target").isEmpty())throw new IllegalArgumentException("Izaberi povezanu tabelu");}
        for(int i=0;i<fs.length();i++){
            JSONObject f=fs.getJSONObject(i);if(!conditional(f))continue;
            JSONObject source=fieldById(l,f.getString("conditionField"));
            if(source==null||!source.optString("type").equals("choice"))throw new IllegalArgumentException("Uslovna veza zahteva kolonu sa jednim izborom: "+f.getString("name"));
            JSONObject targets=f.getJSONObject("targets");
            for(Iterator<String> it=targets.keys();it.hasNext();){String key=it.next();if(key.isEmpty()||targets.getString(key).isEmpty())throw new IllegalArgumentException("Neispravna ciljna tabela");}
        }
        JSONArray groups = l.optJSONArray("uniqueGroups");
        if (l.has("uniqueGroups") && groups == null) throw new IllegalArgumentException("Neispravne unique kombinacije");
        Set<Set<String>> seenGroups = new HashSet<>();
        if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONArray group = groups.getJSONArray(i);
            if (group.length() < 2) throw new IllegalArgumentException("Izaberi najmanje dve kolone");
            Set<String> members = new HashSet<>();
            for (int j = 0; j < group.length(); j++) {
                String fid = group.getString(j);
                if (!ids.contains(fid) || !members.add(fid)) throw new IllegalArgumentException("Neispravna kolona u unique kombinaciji");
            }
            if (!seenGroups.add(members)) throw new IllegalArgumentException("Ta unique kombinacija već postoji");
        }
    }
    private void validateData(JSONObject lib,JSONObject d,boolean checkLinks,boolean required) throws JSONException {
        Set<String>known=new HashSet<>();JSONArray fs=lib.getJSONArray("fields");for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);String fid=f.getString("id"),type=f.getString("type");known.add(fid);Object v=d.opt(fid);boolean empty=v==null||v==JSONObject.NULL||v instanceof String&&((String)v).trim().isEmpty()||v instanceof JSONArray&&((JSONArray)v).length()==0;if(empty){if(required&&f.optBoolean("required"))throw new IllegalArgumentException("Obavezno polje: "+f.getString("name"));continue;}
            if(type.equals("integer")&&!(v instanceof Integer||v instanceof Long))throw new IllegalArgumentException("Potreban ceo broj: "+f.getString("name"));
            if(type.equals("decimal")&&(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue())))throw new IllegalArgumentException("Potreban broj: "+f.getString("name"));
            if(type.equals("boolean")&&!(v instanceof Boolean))throw new IllegalArgumentException("Potreban Da/Ne izbor");
            if(Arrays.asList("text","date","datetime","choice").contains(type)&&!(v instanceof String))throw new IllegalArgumentException("Neispravan tekst: "+f.getString("name"));
            if(Arrays.asList("link","multichoice").contains(type)&&!(v instanceof JSONArray))throw new IllegalArgumentException("Neispravan izbor: "+f.getString("name"));
            if(type.equals("link")&&checkLinks){JSONArray a=(JSONArray)v;for(int j=0;j<a.length();j++){JSONObject target=record(a.getString(j));if(target==null||!target.getString("library_id").equals(linkTarget(f,d)))throw new IllegalArgumentException("Nepostojeća ili pogrešna veza: "+f.getString("name"));}}
        }for(Iterator<String>it=d.keys();it.hasNext();)if(!known.contains(it.next()))throw new IllegalArgumentException("Podatak ima nepoznato polje");
    }
    public JSONObject backup() throws JSONException {
        SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();
        try{
            JSONArray libs=libraries(),rows=new JSONArray();for(int i=0;i<libs.length();i++){JSONArray r=records(libs.getJSONObject(i).getString("id"));for(int j=0;j<r.length();j++)rows.put(r.getJSONObject(j));}
            JSONObject result=new JSONObject().put("format","1k-plan").put("version",1).put("libraries",libs).put("records",rows);
            db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
    public void restore(JSONObject backup) throws JSONException {
        if(!backup.getString("format").equals("1k-plan")||backup.getInt("version")!=1)throw new IllegalArgumentException("Nepodržan backup");
        JSONArray libs=backup.getJSONArray("libraries"),rows=backup.getJSONArray("records");Set<String>lids=new HashSet<>(),rids=new HashSet<>();
        for(int i=0;i<libs.length();i++){JSONObject l=libs.getJSONObject(i);validateLibrary(l);if(!lids.add(l.getString("id")))throw new IllegalArgumentException("Dupla tabela");}
        for(int i=0;i<libs.length();i++){JSONArray fs=libs.getJSONObject(i).getJSONArray("fields");for(int j=0;j<fs.length();j++){JSONObject f=fs.getJSONObject(j);if(conditional(f)){JSONObject targets=f.getJSONObject("targets");for(Iterator<String> it=targets.keys();it.hasNext();)if(!lids.contains(targets.getString(it.next())))throw new IllegalArgumentException("Nedostaje povezana tabela");}else if(f.optString("type").equals("link")&&!lids.contains(f.optString("target")))throw new IllegalArgumentException("Nedostaje povezana tabela");}}
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!lids.contains(r.getString("library_id"))||!rids.add(r.getString("id")))throw new IllegalArgumentException("Neispravan zapis");r.getJSONObject("data");r.getLong("created_at");r.getLong("updated_at");}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            db.delete("records",null,null);db.delete("libraries",null,null);for(int i=0;i<libs.length();i++)putLibrary(db,libs.getJSONObject(i),i);
            for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);validateData(library(r.getString("library_id")),r.getJSONObject("data"),false,false);ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("library_id",r.getString("library_id"));v.put("data",r.getJSONObject("data").toString());v.put("created_at",r.getLong("created_at"));v.put("updated_at",r.getLong("updated_at"));if(db.insertOrThrow("records",null,v)<0)throw new IllegalArgumentException("Neispravan zapis");}
            for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);validateData(library(r.getString("library_id")),r.getJSONObject("data"),true,false);}
            for(int i=0;i<libs.length();i++)validateUniqueLibrary(libs.getJSONObject(i),null,null);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}changed();
    }
}
