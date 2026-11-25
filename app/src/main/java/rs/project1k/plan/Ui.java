package rs.project1k.plan;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class Ui {
    static final int BG=Color.rgb(16,21,29),CARD=Color.rgb(25,34,45),TEXT=Color.rgb(236,243,248),MUTED=Color.rgb(153,172,187),ACCENT=Color.rgb(120,220,195);
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static TextView text(Context c,String s,int size,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(c,4),dp(c,6),dp(c,4),dp(c,6));return v;}
    static GradientDrawable bg(int color,int radius,Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
    static Button button(Context c,String s,Runnable r){Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));b.setTextColor(ACCENT);b.setMinHeight(dp(c,48));b.setOnClickListener(v->r.run());return b;}
    static ImageButton icon(Context c,int resource,String description,Runnable action){
        ImageButton b=new ImageButton(c);
        b.setImageResource(resource);
        b.setContentDescription(description);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));
        b.setOnClickListener(v->action.run());
        return b;
    }
    static EditText input(Context c,String hint,String value){EditText e=new EditText(c);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));return e;}
    static void toast(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_LONG).show();}
    static void error(Context c,Exception e){toast(c,e.getMessage()==null?"Unos nije sačuvan":e.getMessage());}
    static String now(boolean time){return new SimpleDateFormat(time?"yyyy-MM-dd HH:mm":"yyyy-MM-dd",Locale.ROOT).format(new Date());}
    static void date(Activity a,Button b,boolean time,String initial,java.util.function.Consumer<String> setter){Calendar cal=Calendar.getInstance();try{Date parsed=new SimpleDateFormat(time?"yyyy-MM-dd HH:mm":"yyyy-MM-dd",Locale.ROOT).parse(initial);if(parsed!=null)cal.setTime(parsed);}catch(Exception ignored){}
        new DatePickerDialog(a,(view,y,m,d)->{cal.set(y,m,d);if(time)new TimePickerDialog(a,(v,h,min)->{cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,min);String s=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(cal.getTime());setter.accept(s);b.setText(s);},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();else {String s=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(cal.getTime());setter.accept(s);b.setText(s);}},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();}
    interface Saved { void run(String id); }
    interface Reader { Object read() throws Exception; }
    static final class Form {
        final Activity a;final PlanStore store;final JSONObject lib,original;final String rid;final LinearLayout body;final LinkedHashMap<String,Reader> readers=new LinkedHashMap<>();final ArrayList<Runnable> linkRefreshers=new ArrayList<>();
        final LinkedHashMap<String,java.util.function.Consumer<Object>> writers=new LinkedHashMap<>();
        final Set<String> touched=new HashSet<>();final AttemptRules attempts;final JSONArray history;
        boolean ready=false,applying=false,nodeContextChanged=false,autoProduct=false,autoBefore=false;
        String lastMeta="",lastTest="";
        Form(Activity a,PlanStore store,JSONObject lib,JSONObject record){this.a=a;this.store=store;this.lib=lib;this.rid=record==null||!record.has("id")?null:record.optString("id");this.original=record==null?new JSONObject():record.optJSONObject("data");attempts=new AttemptRules(lib);history=attempts.enabled()?store.records(lib.optString("id")):new JSONArray();body=column(a);body.setPadding(dp(a,16),0,dp(a,16),dp(a,16));JSONArray fs=lib.optJSONArray("fields");for(int i=0;i<fs.length();i++)add(fs.optJSONObject(i));
            for(Iterator<String> it=original.keys();it.hasNext();)touched.add(it.next());
            refreshLinks();ready=true;updateSmart("");}
        void add(JSONObject f){String fid=f.optString("id"),type=f.optString("type");Object old=original.opt(fid);boolean auto=f.optBoolean("auto");if(auto){readers.put(fid,()->old);return;}
            body.addView(text(a,f.optString("name")+(f.optBoolean("required")?" *":""),13,MUTED));String desc=f.optString("description");if(!desc.isEmpty())body.addView(text(a,desc,12,MUTED));
            if(type.equals("link")||type.equals("multichoice")){
                JSONArray initial=old instanceof JSONArray?(JSONArray)old:f.optJSONArray("default");
                final JSONArray[] selection={initial==null?new JSONArray():initial};
                Button b=button(a,store.display(f,selection[0]),()->{});
                final String[] activeTarget={""};final boolean[] initialized={false};
                Runnable refresh=()->{
                    if(!type.equals("link"))return;
                    String target=targetFor(f);boolean changed=!target.equals(activeTarget[0]);
                    JSONArray kept=new JSONArray();
                    for(int i=0;i<selection[0].length();i++){JSONObject r=store.record(selection[0].optString(i));if(r!=null&&r.optString("library_id").equals(target))kept.put(r.optString("id"));}
                    if(rid==null&&kept.length()==0&&(!initialized[0]&&old==null||initialized[0]&&changed)){
                        String defaultId=store.defaultLink(f,target);if(!defaultId.isEmpty())kept.put(defaultId);
                    }
                    Set<String> allowed=allowedLinks(f);
                    if(allowed!=null&&(rid==null||nodeContextChanged))kept=AttemptRules.intersect(kept,allowed);
                    selection[0]=kept;activeTarget[0]=target;initialized[0]=true;
                    JSONObject targetLib=store.library(target);
                    b.setEnabled(targetLib!=null&&(allowed==null||!allowed.isEmpty()));
                    b.setText(allowed!=null&&allowed.isEmpty()?(selectedTest().isEmpty()?"Prvo izaberi test":"Izabrani test nema čvorove"):targetLib==null?"Prvo izaberi opciju sa podešenom tabelom":kept.length()==0?"Izaberi · "+targetLib.optString("name"):store.display(f,kept));
                };
                if(type.equals("link"))linkRefreshers.add(refresh);
                b.setOnClickListener(v->{if(type.equals("link")){
                    refresh.run();String target=activeTarget[0];if(target.isEmpty())return;
                    Set<String> allowed=allowedLinks(f);
                    chooseLinks(a,store,target,selection[0],!(attempts.enabled()&&attempts.isSingle(f)),allowed,newValue->{if(!target.equals(targetFor(f)))return;selection[0]=newValue;changed(fid);});
                }else chooseOptions(a,f.optJSONArray("options"),selection[0],newValue->{selection[0]=newValue;b.setText(store.display(f,newValue));});});
                body.addView(b);readers.put(fid,()->selection[0]);
                writers.put(fid,value->{selection[0]=value instanceof JSONArray?(JSONArray)value:new JSONArray();});
            }else if(type.equals("choice")){
                JSONArray opts=f.optJSONArray("options");ArrayList<String>values=new ArrayList<>();values.add("—");if(opts!=null)for(int i=0;i<opts.length();i++)values.add(opts.optString(i));String selected=old==null?"":old.toString();if(selected.isEmpty()&&f.optJSONArray("default")!=null)selected=f.optJSONArray("default").optString(0);if(!selected.isEmpty()&&!values.contains(selected))values.add(selected);Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(Math.max(0,values.indexOf(selected)));body.addView(s);readers.put(fid,()->s.getSelectedItemPosition()==0?null:values.get(s.getSelectedItemPosition()));
                final String[] shown={s.getSelectedItemPosition()==0?"":values.get(s.getSelectedItemPosition())};
                writers.put(fid,value->{String text=value==null?"":value.toString();int index=values.indexOf(text);shown[0]=index<=0?"":text;s.setSelection(Math.max(0,index));});
                s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String next=pos<=0?"":values.get(pos);if(!next.equals(shown[0])){shown[0]=next;changed(fid);}}public void onNothingSelected(AdapterView<?> p){}});
            }else if(type.equals("boolean")){
                Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"—","Da","Ne"}));s.setSelection(old instanceof Boolean?((Boolean)old?1:2):0);body.addView(s);readers.put(fid,()->s.getSelectedItemPosition()==0?null:s.getSelectedItemPosition()==1);
            }else if(type.equals("date")||type.equals("datetime")){
                boolean time=type.equals("datetime");final String[] value={old==null?"":old.toString()};JSONObject source=f.optJSONObject("source");JSONArray cnt=source==null?null:source.optJSONArray("cnt");if(value[0].isEmpty()&&rid==null&&cnt!=null&&cnt.optJSONObject(0).optInt("i")==1)value[0]=now(time);
                LinearLayout r=row(a);Button b=button(a,value[0].isEmpty()?"Izaberi datum":value[0],()->{});b.setOnClickListener(v->date(a,b,time,value[0],s->{value[0]=s;changed(fid);}));r.addView(b,new LinearLayout.LayoutParams(0,-2,1));r.addView(button(a,"×",()->{value[0]="";b.setText("Izaberi datum");changed(fid);}));body.addView(r);readers.put(fid,()->value[0].isEmpty()?null:value[0]);
            }else{
                EditText e=input(a,"",old==null?"":old.toString());if(type.equals("integer"))e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);else if(type.equals("decimal"))e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);else {e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setMaxLines(6);}body.addView(e);readers.put(fid,()->{String s=e.getText().toString().trim();if(s.isEmpty())return null;try{if(type.equals("integer"))return Long.parseLong(s);if(type.equals("decimal"))return Double.parseDouble(s.replace(',','.'));}catch(NumberFormatException ex){throw new IllegalArgumentException("Neispravan broj: "+f.optString("name"));}return s;});
            }
        }
        Object value(JSONObject field){Reader reader=readers.get(AttemptRules.fid(field));if(reader==null)return null;try{return reader.read();}catch(Exception e){return null;}}
        JSONObject contextData(){
            JSONObject data=new JSONObject();
            try{for(JSONObject f:new JSONObject[]{attempts.test,attempts.meta,attempts.date,attempts.product,attempts.before,attempts.after}){
                Object v=value(f);if(f!=null&&v!=null)data.put(AttemptRules.fid(f),v);
            }}catch(JSONException e){throw new IllegalStateException(e);}
            return data;
        }
        String selectedTest(){return attempts.enabled()?AttemptRules.single(contextData(),attempts.test):"";}
        Set<String> allowedLinks(JSONObject f){
            if(!attempts.enabled()||!attempts.isNode(f))return null;
            JSONObject test=store.record(selectedTest());
            return attempts.nodes(test,test==null?null:store.library(test.optString("library_id")));
        }
        void write(JSONObject field,Object value){java.util.function.Consumer<Object> writer=writers.get(AttemptRules.fid(field));if(writer!=null)writer.accept(value);}
        void changed(String fieldId){
            if(!ready||applying)return;
            touched.add(fieldId);
            if(attempts.enabled()&&fieldId.equals(AttemptRules.fid(attempts.test)))nodeContextChanged=true;
            updateSmart(fieldId);
        }
        void updateSmart(String changedField){
            if(applying)return;applying=true;
            try{
                if(attempts.enabled()&&rid==null){
                    JSONObject current=contextData();String meta=AttemptRules.single(current,attempts.meta),test=AttemptRules.single(current,attempts.test);
                    boolean metaChanged=!meta.equals(lastMeta)&&!lastMeta.isEmpty();
                    boolean testChanged=!test.equals(lastTest)&&!lastTest.isEmpty();
                    if(metaChanged){
                        touched.remove(AttemptRules.fid(attempts.product));write(attempts.product,"");autoProduct=false;
                        write(attempts.after,new JSONArray());
                    }
                    if(metaChanged||testChanged){touched.remove(AttemptRules.fid(attempts.before));write(attempts.before,new JSONArray());autoBefore=false;}
                    if(!touched.contains(AttemptRules.fid(attempts.product))){
                        String suggestion=attempts.product(history,current,rid,System.currentTimeMillis());
                        if(!suggestion.isEmpty()||autoProduct){write(attempts.product,suggestion);autoProduct=!suggestion.isEmpty();}
                    }
                    refreshLinks();current=contextData();
                    if(!touched.contains(AttemptRules.fid(attempts.before))){
                        JSONArray suggestion=attempts.before(history,current,rid,allowedLinks(attempts.before),System.currentTimeMillis());
                        if(suggestion.length()>0||autoBefore){write(attempts.before,suggestion);autoBefore=suggestion.length()>0;}
                    }
                    lastMeta=meta;lastTest=test;
                }
                refreshLinks();
            }finally{applying=false;}
        }
        String targetFor(JSONObject field){
            if(!PlanStore.conditional(field))return field.optString("target");
            Reader source=readers.get(field.optString("conditionField"));
            if(source==null)return "";
            try{Object value=source.read();JSONObject targets=field.optJSONObject("targets");return value==null||targets==null?"":targets.optString(value.toString());}
            catch(Exception e){return "";}
        }
        void refreshLinks(){for(Runnable refresh:linkRefreshers)refresh.run();}
        JSONObject data() throws Exception{JSONObject d=new JSONObject();for(Map.Entry<String,Reader> e:readers.entrySet()){Object v=e.getValue().read();if(v!=null)d.put(e.getKey(),v);}return d;}
    }
    static void editRecord(Activity a,PlanStore store,JSONObject lib,JSONObject record,Saved callback){Form f=new Form(a,store,lib,record);ScrollView scroll=new ScrollView(a);scroll.addView(f.body);AlertDialog d=new AlertDialog.Builder(a).setTitle(record==null?"Novi zapis · "+lib.optString("name"):"Uredi zapis").setView(scroll).setNegativeButton("Zatvori",null).setPositiveButton("Sačuvaj",null).create();d.setOnShowListener(v->{d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);d.getButton(-1).setOnClickListener(x->{try{String id=store.saveRecord(lib.optString("id"),f.rid,f.data());d.dismiss();callback.run(id);}catch(Exception ex){error(a,ex);}});});d.show();}
    static void chooseOptions(Activity a,JSONArray options,JSONArray selected,java.util.function.Consumer<JSONArray> callback){if(options==null)options=new JSONArray();String[] labels=new String[options.length()];boolean[] checked=new boolean[labels.length];for(int i=0;i<labels.length;i++){labels[i]=options.optString(i);for(int j=0;j<selected.length();j++)if(selected.optString(j).equals(labels[i]))checked[i]=true;}new AlertDialog.Builder(a).setTitle("Izaberi").setMultiChoiceItems(labels,checked,(d,i,b)->checked[i]=b).setNegativeButton("Zatvori",null).setPositiveButton("Potvrdi",(d,w)->{JSONArray n=new JSONArray();for(int i=0;i<labels.length;i++)if(checked[i])n.put(labels[i]);callback.accept(n);}).show();}
    static String linkDetails(PlanStore store, JSONObject lib, JSONObject record) {
        ArrayList<String> lines = new ArrayList<>();
        JSONObject data = record.optJSONObject("data");
        JSONArray fields = lib.optJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i);
            Object value = data.opt(field.optString("id"));
            if (value == null || value == JSONObject.NULL) continue;
            if (value instanceof String && ((String)value).trim().isEmpty()) continue;
            if (value instanceof JSONArray && ((JSONArray)value).length() == 0) continue;
            lines.add(field.optString("name") + ": " + store.display(field, value));
        }
        // Tables without a visible ID still need a way to distinguish identical-looking records.
        if (PlanStore.field(lib, "id") == null) lines.add("ID zapisa: " + record.optString("id"));
        return String.join("\n", lines);
    }

    static void chooseLinks(Activity a, PlanStore store, String target, JSONArray selected,
                            boolean multiple, java.util.function.Consumer<JSONArray> callback) {
        chooseLinks(a,store,target,selected,multiple,null,callback);
    }
    static void chooseLinks(Activity a, PlanStore store, String target, JSONArray selected,
                            boolean multiple, Set<String> allowed, java.util.function.Consumer<JSONArray> callback) {
        JSONObject lib = store.library(target);
        if (lib == null) { toast(a, "Povezana tabela ne postoji"); return; }
        JSONArray allRows=store.orderedRecords(target,store.records(target)),filtered=new JSONArray();
        for(int i=0;i<allRows.length();i++){JSONObject row=allRows.optJSONObject(i);if(allowed==null||allowed.contains(row.optString("id")))filtered.put(row);}
        final JSONArray rows=filtered;
        // Build labels once per opening, not on every keypress.
        ArrayList<String> titles = new ArrayList<>(), details = new ArrayList<>(), searchText = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject record = rows.optJSONObject(i);
            String title = store.label(record), detail = linkDetails(store, lib, record);
            titles.add(title); details.add(detail);
            searchText.add((title + "\n" + detail).toLowerCase(Locale.ROOT));
        }
        LinearLayout content = column(a);
        content.setPadding(dp(a,8),0,dp(a,8),0);
        EditText search = input(a, "Pretraga po svim poljima", "");
        content.addView(search);
        ScrollView scroll = new ScrollView(a);
        LinearLayout list = column(a); scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1,dp(a,340)));
        Set<String> picked = new LinkedHashSet<>();
        for (int i = 0; i < selected.length(); i++) if(allowed==null||allowed.contains(selected.optString(i)))picked.add(selected.optString(i));
        final AlertDialog[] holder = new AlertDialog[1];
        Runnable render = () -> {
            list.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < rows.length(); i++) {
                if (!searchText.get(i).contains(query)) continue;
                JSONObject record = rows.optJSONObject(i);
                String id = record.optString("id");
                LinearLayout item = row(a);
                item.setGravity(Gravity.TOP);
                item.setPadding(0,dp(a,8),dp(a,8),dp(a,8));
                LinearLayout.LayoutParams itemLayout = new LinearLayout.LayoutParams(-1,-2);
                itemLayout.bottomMargin = dp(a,6);
                item.setBackground(bg(CARD,10,a));
                CompoundButton marker = multiple ? new CheckBox(a) : new RadioButton(a);
                marker.setChecked(picked.contains(id));
                marker.setContentDescription(titles.get(i) + ". " + details.get(i));
                item.addView(marker,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
                LinearLayout description = column(a);
                description.addView(text(a,titles.get(i),14,TEXT));
                TextView extra = text(a,details.get(i),12,MUTED);
                description.addView(extra);
                item.addView(description,new LinearLayout.LayoutParams(0,-2,1));
                if (multiple) {
                    marker.setOnCheckedChangeListener((button,on) -> {
                        if (on) picked.add(id); else picked.remove(id);
                    });
                    item.setOnClickListener(v -> marker.setChecked(!marker.isChecked()));
                } else {
                    Runnable choose = () -> { callback.accept(new JSONArray().put(id)); holder[0].dismiss(); };
                    marker.setOnClickListener(v -> choose.run());
                    item.setOnClickListener(v -> choose.run());
                }
                list.addView(item,itemLayout);
            }
            if (list.getChildCount() == 0) list.addView(text(a,
                rows.length() == 0 ? (allowed==null?"Nema zapisa. Dodaj novi.":"Izabrani test nema ponuđenih čvorova.") : "Nema rezultata pretrage.",14,MUTED));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { render.run(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(a).setTitle(lib.optString("name"))
            .setView(content).setNegativeButton("Zatvori",null);
        if(allowed==null)builder.setNeutralButton("＋ Novi",(d,w) -> editRecord(a,store,lib,null,id -> {
                JSONArray values = multiple ? new JSONArray(picked) : new JSONArray();
                values.put(id); callback.accept(values);
            }));
        if (multiple) builder.setPositiveButton("Potvrdi",(d,w) -> callback.accept(new JSONArray(picked)));
        else builder.setPositiveButton("Bez izbora",(d,w) -> callback.accept(new JSONArray()));
        holder[0] = builder.create();render.run();holder[0].show();
    }
}
