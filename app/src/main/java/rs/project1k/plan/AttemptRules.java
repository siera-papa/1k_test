package rs.project1k.plan;

import org.json.*;
import java.util.*;
import java.text.*;

/** Entry suggestions only: saved history is never rewritten or interpreted as inferred state. */
final class AttemptRules {
    final JSONObject lib,test,meta,before,after,product,date,id;
    AttemptRules(JSONObject lib){
        this.lib=lib;
        test=find(lib,"K8[AVNG<nITr7UgWANY<","test");
        meta=find(lib,"NZ8%l]us#]VSp%u&Sk&Q","meta");
        before=find(lib,"DDC9M[bc4+lb*H*;H[Y>","Čvor pre pokušaja");
        after=find(lib,"]QNc0ixZ0gy)gsXN0-tY","Čvor posle pokušaja");
        product=find(lib,"ec776bff-217e-4e5c-8cfa-77a124867c67","Tip proizvoda");
        date=find(lib,"Kc0n%F;>-lgMXZ:NEF;K","datum i vreme");
        id=find(lib,"[>zDgn4;ouyEKGpI]fvT","id");
    }
    static JSONObject find(JSONObject lib,String stableId,String name){
        if(lib==null)return null;
        JSONArray fields=lib.optJSONArray("fields");if(fields==null)return null;
        for(int i=0;i<fields.length();i++){JSONObject f=fields.optJSONObject(i);if(f.optString("id").equals(stableId))return f;}
        for(int i=0;i<fields.length();i++){JSONObject f=fields.optJSONObject(i);JSONObject source=f.optJSONObject("source");
            if(f.optString("name").trim().equalsIgnoreCase(name)||source!=null&&source.optString("tt").trim().equalsIgnoreCase(name))return f;}
        return null;
    }
    static String fid(JSONObject f){return f==null?"":f.optString("id");}
    boolean enabled(){return (lib.optString("id").equals("MrCZ0vXU9[UKURqJXC)&")||lib.optString("name").equalsIgnoreCase("Pokušaj"))&&test!=null&&meta!=null;}
    boolean isNode(JSONObject f){String key=fid(f);return !key.isEmpty()&&(key.equals(fid(before))||key.equals(fid(after)));}
    boolean isSingle(JSONObject f){String key=fid(f);return isNode(f)||key.equals(fid(test))||key.equals(fid(meta));}
    static String single(JSONObject data,JSONObject field){
        if(data==null||field==null)return "";
        JSONArray a=data.optJSONArray(fid(field));return a!=null&&a.length()==1?a.optString(0):"";
    }
    Set<String> nodes(JSONObject testRecord,JSONObject testLib){
        Set<String> ids=new LinkedHashSet<>();
        JSONObject field=find(testLib,"3d379cc2-1b02-4659-be89-2a962d1dbe71","Čvorovi");
        if(testRecord==null||field==null||!field.optString("type").equals("link"))return ids;
        JSONObject data=testRecord.optJSONObject("data");JSONArray values=data==null?null:data.optJSONArray(fid(field));
        if(values!=null)for(int i=0;i<values.length();i++)ids.add(values.optString(i));
        return ids;
    }
    static JSONArray intersect(JSONArray selection,Set<String> allowed){
        JSONArray result=new JSONArray();if(selection==null)return result;
        for(int i=0;i<selection.length();i++)if(allowed==null||allowed.contains(selection.optString(i)))result.put(selection.optString(i));
        return result;
    }
    static boolean nonempty(Object value){return value!=null&&value!=JSONObject.NULL&&!(value instanceof String&&((String)value).trim().isEmpty())&&!(value instanceof JSONArray&&((JSONArray)value).length()==0);}
    long time(JSONObject data,long fallback){
        String text=date==null||data==null?"":data.optString(fid(date));
        if(!text.isEmpty()){
            SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT);format.setLenient(false);
            ParsePosition position=new ParsePosition(0);Date parsed=format.parse(text,position);
            if(parsed!=null&&position.getIndex()==text.length())return parsed.getTime();
        }
        return fallback;
    }
    JSONObject latest(JSONArray history,JSONObject current,String excludedId,JSONObject valueField,long now){
        String metaId=single(current,meta);if(metaId.isEmpty()||valueField==null)return null;
        long cutoff=time(current,now);JSONObject best=null;long bestTime=Long.MIN_VALUE;
        for(int i=0;i<history.length();i++){
            JSONObject row=history.optJSONObject(i),data=row.optJSONObject("data");
            if(row.optString("id").equals(excludedId)||!row.optString("library_id").equals(lib.optString("id"))||!single(data,meta).equals(metaId)||!nonempty(data==null?null:data.opt(fid(valueField))))continue;
            long rowTime=time(data,row.optLong("created_at"));
            if(rowTime>cutoff)continue;
            if(best==null||rowTime>bestTime||rowTime==bestTime&&laterTie(row,best)){
                best=row;bestTime=rowTime;
            }
        }
        return best;
    }
    boolean laterTie(JSONObject candidate,JSONObject previous){
        long a=candidate.optLong("created_at"),b=previous.optLong("created_at");
        if(a!=b)return a>b;
        if(id!=null){
            try{int compare=new java.math.BigDecimal(candidate.optJSONObject("data").optString(fid(id))).compareTo(new java.math.BigDecimal(previous.optJSONObject("data").optString(fid(id))));if(compare!=0)return compare>0;}
            catch(NumberFormatException ignored){}
        }
        return candidate.optString("id").compareTo(previous.optString("id"))>0;
    }
    String product(JSONArray history,JSONObject current,String excludedId,long now){
        JSONObject row=latest(history,current,excludedId,product,now);if(row==null)return "";
        String value=row.optJSONObject("data").optString(fid(product));JSONArray options=product.optJSONArray("options");
        if(options!=null)for(int i=0;i<options.length();i++)if(options.optString(i).equals(value))return value;
        return "";
    }
    JSONArray before(JSONArray history,JSONObject current,String excludedId,Set<String> allowed,long now){
        JSONObject row=latest(history,current,excludedId,after,now);
        return row==null?new JSONArray():intersect(row.optJSONObject("data").optJSONArray(fid(after)),allowed);
    }
}
