package rs.project1k.plan;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity {
    PlanStore store;LinearLayout root,body,toolbar;
    final Map<String,String> searches=new HashMap<>();
    final Map<String,String> sortFields=new HashMap<>();
    final Set<String> descendingTables=new HashSet<>();
    HorizontalScrollView activeTable;
    final Map<String,Integer> columnWidths=new HashMap<>();
    final Map<String,List<View>> columnViews=new HashMap<>();
    String renderedTable="";
boolean searchExpanded=false;
    String searchLibrary="";
    EditText toolbarSearch;
    TextView toolbarTitle;
    ImageButton toolbarSearchIcon;
    String current="";boolean table=true;JSONObject pendingBackup;
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new PlanStore(this);if(state!=null){current=state.getString("library","");table=state.getBoolean("table",true);searchExpanded=state.getBoolean("searchExpanded",false);searchLibrary=current;searches.put(current,state.getString("searchQuery",""));}render();AutoBackup.ensure(this);if(AutoBackup.needsPermission(this))requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},41);}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("library",current);b.putBoolean("table",table);b.putBoolean("searchExpanded",searchExpanded);b.putString("searchQuery",searches.getOrDefault(current,""));}
    void render(){root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));setContentView(root);
        // Apply system bar insets for Android 15 edge-to-edge without hiding controls.
        root.setOnApplyWindowInsetsListener((v,insets)->{int left,top,right,bottom;if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());left=i.left;top=i.top;right=i.right;bottom=i.bottom;}else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}v.setPadding(Ui.dp(this,12)+left,Ui.dp(this,8)+top,Ui.dp(this,12)+right,Ui.dp(this,8)+bottom);return insets;});root.requestApplyInsets();
        toolbar=Ui.row(this);
        JSONObject lib=current.isEmpty()?null:store.library(current);
        if(!searchLibrary.equals(current)){
            searchExpanded=false;searchLibrary=current;searches.put(current,"");
        }
        final JSONArray records=lib==null?new JSONArray():store.records(lib.optString("id"));
        if(lib!=null){
            ImageButton back=Ui.icon(this,R.drawable.ic_back,"Nazad",()->{
                if(searchExpanded)closeSearch();else {current="";render();}
            });
            LinearLayout.LayoutParams backLayout=new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48));
            backLayout.setMarginEnd(Ui.dp(this,16));
            toolbar.addView(back,backLayout);
        }
        toolbarTitle=Ui.text(this,lib==null?"Tabele":lib.optString("name"),20,Ui.TEXT);
        toolbarTitle.setTypeface(null,Typeface.NORMAL);
        toolbar.addView(toolbarTitle,new LinearLayout.LayoutParams(0,-2,1));
        toolbarSearch=null;toolbarSearchIcon=null;
        if(lib!=null){
            toolbarSearch=Ui.input(this,"",searches.getOrDefault(current,""));
            toolbarSearch.setContentDescription("Pretraga zapisa");
            toolbarSearch.setSingleLine(true);
            toolbarSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            toolbarSearch.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            toolbarSearch.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            toolbarSearch.setVisibility(searchExpanded?View.VISIBLE:View.GONE);
            toolbarTitle.setVisibility(searchExpanded?View.GONE:View.VISIBLE);
            toolbar.addView(toolbarSearch,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
            toolbarSearch.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence text,int start,int count,int after){}
                public void onTextChanged(CharSequence text,int start,int before,int count){
                    searches.put(lib.optString("id"),text.toString());renderRecords(lib,records);
                }
                public void afterTextChanged(android.text.Editable text){}
            });
            toolbarSearchIcon=Ui.icon(this,R.drawable.ic_search,"Pretraga",()->{
                searchExpanded=true;
                toolbarTitle.setVisibility(View.GONE);
                toolbarSearchIcon.setVisibility(View.GONE);
                toolbarSearch.setVisibility(View.VISIBLE);
                toolbarSearch.requestFocus();
                toolbarSearch.setSelection(toolbarSearch.length());
                toolbarSearch.post(()->{
                    android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    if(keyboard!=null)keyboard.showSoftInput(toolbarSearch,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            });
            toolbarSearchIcon.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));
            toolbarSearchIcon.setVisibility(searchExpanded?View.GONE:View.VISIBLE);
            toolbar.addView(toolbarSearchIcon);
        }
        toolbar.addView(Ui.icon(this,R.drawable.ic_more,"Opcije",()->menu(lib)));
        root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);
        body=Ui.column(this);scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(lib==null){current="";showLibraryGrid();}
        else renderRecords(lib,records);
        LinearLayout footer=Ui.row(this);footer.setGravity(Gravity.RIGHT);
        footer.setPadding(0,Ui.dp(this,8),0,0);
        ImageButton add=Ui.icon(this,R.drawable.ic_add,lib==null?"Nova tabela":"Novi zapis",()->{
            if(lib==null)newLibrary();else Ui.editRecord(this,store,lib,null,id->render());
        });
        add.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,56),Ui.dp(this,56)));
        add.setBackground(Ui.bg(Ui.CARD,12,this));
        footer.addView(add);root.addView(footer);
    }
    void showLibraryGrid(){
        JSONArray libraries=store.libraries();
        for(int start=0;start<libraries.length();start+=3){
            LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);
            for(int column=0;column<3;column++){
                int index=start+column;
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,Ui.dp(this,128),1);
                if(column<2)cell.rightMargin=Ui.dp(this,8);
                if(index>=libraries.length()){row.addView(new View(this),cell);continue;}
                JSONObject library=libraries.optJSONObject(index);
                LinearLayout tile=Ui.column(this);
                tile.setPadding(Ui.dp(this,8),Ui.dp(this,4),0,0);
                tile.setBackground(Ui.bg(Ui.CARD,12,this));
                tile.setContentDescription(library.optString("name"));
                tile.setOnClickListener(v->{current=library.optString("id");table=true;render();});

                LinearLayout header=Ui.row(this);
                TextView name=Ui.text(this,library.optString("name"),13,Ui.TEXT);
                name.setGravity(Gravity.CENTER_VERTICAL);
                name.setTypeface(null,Typeface.NORMAL);name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setPadding(0,0,0,0);
                header.addView(name,new LinearLayout.LayoutParams(0,-1,1));
                ImageButton edit=Ui.icon(this,R.drawable.ic_edit,"Uredi tabelu · "+library.optString("name"),()->
                    structure(store.library(library.optString("id"))));
                edit.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
                header.addView(edit,new LinearLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40)));
                tile.addView(header,new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));

                FrameLayout middle=new FrameLayout(this);
                ImageView symbol=new ImageView(this);
                symbol.setImageResource(R.drawable.ic_library);
                symbol.setScaleType(ImageView.ScaleType.FIT_CENTER);
                symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams symbolLayout=new FrameLayout.LayoutParams(Ui.dp(this,26),Ui.dp(this,26),Gravity.CENTER);
                symbolLayout.rightMargin=Ui.dp(this,8);
                middle.addView(symbol,symbolLayout);
                tile.addView(middle,new LinearLayout.LayoutParams(-1,0,1));

                LinearLayout bottom=Ui.row(this);
                TextView count=Ui.text(this,store.records(library.optString("id")).length()+" zapisa",11,Ui.MUTED);
                count.setPadding(0,0,0,0);count.setSingleLine(true);
                bottom.addView(count,new LinearLayout.LayoutParams(0,-2,1));
                ImageButton add=Ui.icon(this,R.drawable.ic_add,"Novi zapis · "+library.optString("name"),()->
                    Ui.editRecord(this,store,store.library(library.optString("id")),null,id->render()));
                add.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
                bottom.addView(add,new LinearLayout.LayoutParams(Ui.dp(this,40),Ui.dp(this,40)));
                tile.addView(bottom);
                row.addView(tile,cell);
            }
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
            layout.topMargin=Ui.dp(this,8);body.addView(row,layout);
        }
    }
    void showCard(JSONObject lib,JSONObject record){LinearLayout card=Ui.column(this);card.setBackground(Ui.bg(Ui.CARD,16,this));card.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));card.addView(Ui.text(this,store.label(record),16,Ui.TEXT));JSONArray fs=lib.optJSONArray("fields");JSONObject d=record.optJSONObject("data");int shown=0;for(int j=0;j<fs.length()&&shown<3;j++){JSONObject f=fs.optJSONObject(j);if(f.optBoolean("auto")||!d.has(f.optString("id")))continue;card.addView(Ui.text(this,f.optString("name")+": "+store.display(f,d.opt(f.optString("id"))),13,Ui.MUTED));shown++;}card.setOnClickListener(v->detail(lib,record));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=Ui.dp(this,10);body.addView(card,cp);}
    void renderRecords(JSONObject lib,JSONArray source){
        String libraryId=lib.optString("id");
        int horizontal=activeTable!=null&&renderedTable.equals(libraryId)?activeTable.getScrollX():0;
        activeTable=null;renderedTable=libraryId;
        body.removeAllViews();
        JSONArray fields=lib.optJSONArray("fields");
        columnWidths.clear();
        android.text.TextPaint numberPaint=new android.text.TextPaint();
        numberPaint.setTypeface(Typeface.DEFAULT);
        numberPaint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);
        android.text.TextPaint headerPaint=new android.text.TextPaint(numberPaint);
        headerPaint.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
        for(int j=0;j<fields.length();j++){
            JSONObject field=fields.optJSONObject(j);
            int titleWidth=(int)Math.ceil(headerPaint.measureText(field.optString("name")+" ↓"))+Ui.dp(this,20);
            int minimum=isIdField(field)?(int)Math.ceil(numberPaint.measureText("000"))+Ui.dp(this,20):Ui.dp(this,36);
            boolean numeric=field.optString("type").equals("integer")||field.optString("type").equals("decimal")||isIdField(field);
            columnWidths.put(field.optString("id"),Math.max(minimum,numeric?titleWidth:Math.min(Ui.dp(this,260),titleWidth)));
        }
        String query=searches.getOrDefault(libraryId,"").trim().toLowerCase(Locale.ROOT);
        ArrayList<JSONObject> visible=new ArrayList<>();
        Map<String,String> displayed=new HashMap<>();
        for(int i=0;i<source.length();i++){
            JSONObject record=source.optJSONObject(i),data=record.optJSONObject("data");
            StringBuilder text=new StringBuilder();
            for(int j=0;j<fields.length();j++){
                JSONObject field=fields.optJSONObject(j);
                String value=store.display(field,data.opt(field.optString("id")));
                displayed.put(record.optString("id")+"\n"+field.optString("id"),value);
                String fid=field.optString("id");
                if(!isIdField(field)){
                    int measured=0;
                    for(String part:value.split("\n"))measured=Math.max(measured,(int)Math.ceil(numberPaint.measureText(part)));
                    measured=Math.min(Ui.dp(this,260),measured+Ui.dp(this,20));
                    columnWidths.put(fid,Math.max(columnWidths.get(fid),measured));
                }
                text.append(value).append('\n');
            }
            if(query.isEmpty()||text.toString().toLowerCase(Locale.ROOT).contains(query))visible.add(record);
        }
        android.content.SharedPreferences savedWidths=getSharedPreferences("column_widths",MODE_PRIVATE);
        for(int j=0;j<fields.length();j++){
            String fid=fields.optJSONObject(j).optString("id");
            String key=widthPreferenceKey(libraryId,fid);
            if(savedWidths.contains(key)){
                int pixels=Math.round(savedWidths.getFloat(key,36f)*getResources().getDisplayMetrics().density);
                columnWidths.put(fid,Math.max(Ui.dp(this,32),Math.min(Ui.dp(this,800),pixels)));
            }
        }
        String sortId=sortFields.get(libraryId);JSONObject sortField=null;
        for(int i=0;i<fields.length();i++)if(fields.optJSONObject(i).optString("id").equals(sortId))sortField=fields.optJSONObject(i);
        if(sortField!=null){
            final JSONObject field=sortField;final boolean descending=descendingTables.contains(libraryId);
            final java.text.Collator collator=java.text.Collator.getInstance(Locale.forLanguageTag("sr-Latn-RS"));
            collator.setStrength(java.text.Collator.SECONDARY);
            visible.sort((left,right)->{
                String fid=field.optString("id"),type=field.optString("type");
                Object av=left.optJSONObject("data").opt(fid),bv=right.optJSONObject("data").opt(fid);
                boolean ae=emptySortValue(av),be=emptySortValue(bv);
                if(ae||be)return ae==be?0:ae?1:-1;
                int comparison;
                if(av instanceof Number&&bv instanceof Number){
                    comparison=new java.math.BigDecimal(av.toString()).compareTo(new java.math.BigDecimal(bv.toString()));
                }else if(av instanceof Boolean&&bv instanceof Boolean){
                    comparison=Boolean.compare((Boolean)av,(Boolean)bv);
                }else {
                    String at=displayed.get(left.optString("id")+"\n"+fid),bt=displayed.get(right.optString("id")+"\n"+fid);
                    comparison=type.equals("date")||type.equals("datetime")?at.compareTo(bt):collator.compare(at,bt);
                }
                return descending?-Integer.signum(comparison):Integer.signum(comparison);
            });
        }
        JSONArray rows=new JSONArray(visible);
        if(source.length()==0){
            body.addView(Ui.text(this,"Još nema zapisa",18,Ui.TEXT));
        }else if(rows.length()==0){
            body.addView(Ui.text(this,"Nema rezultata pretrage",14,Ui.MUTED));
        }
        // Keep the column headers available even when filtering yields no matches.
        if(table){showTable(lib,rows,source);HorizontalScrollView target=activeTable;target.post(()->target.scrollTo(horizontal,0));}
        else for(int i=0;i<rows.length();i++)showCard(lib,rows.optJSONObject(i));
    }
    static boolean emptySortValue(Object value){
        return value==null||value==JSONObject.NULL||value instanceof String&&((String)value).trim().isEmpty()
            ||value instanceof JSONArray&&((JSONArray)value).length()==0;
    }
    void showTable(JSONObject lib,JSONArray rows,JSONArray source){
        HorizontalScrollView hs=new HorizontalScrollView(this);activeTable=hs;columnViews.clear();
        LinearLayout grid=Ui.column(this);JSONArray fields=lib.optJSONArray("fields");
        String libraryId=lib.optString("id");LinearLayout header=Ui.row(this);
        for(int j=0;j<fields.length();j++){
            JSONObject field=fields.optJSONObject(j);String fid=field.optString("id");
            boolean sorted=fid.equals(sortFields.get(libraryId));
            String direction=sorted?(descendingTables.contains(libraryId)?" ↓":" ↑"):"";
            TextView heading=cell(field.optString("name")+direction,true,field);
            heading.setTooltipText(field.optString("name"));
            heading.setContentDescription(field.optString("name")+". Sortiraj "+(sorted&&!descendingTables.contains(libraryId)?"opadajuće":"rastuće"));
            heading.setOnClickListener(v->{
                if(fid.equals(sortFields.get(libraryId))){
                    if(descendingTables.contains(libraryId))descendingTables.remove(libraryId);else descendingTables.add(libraryId);
                }else {sortFields.put(libraryId,fid);descendingTables.remove(libraryId);}
                renderRecords(lib,source);
            });
            heading.setOnLongClickListener(v->{
                getSharedPreferences("column_widths",MODE_PRIVATE).edit().remove(widthPreferenceKey(libraryId,fid)).apply();
                renderRecords(lib,source);return true;
            });
            FrameLayout headerCell=new FrameLayout(this);
            int width=columnWidths.get(fid);
            headerCell.addView(heading,new FrameLayout.LayoutParams(width,-2));
            View divider=new View(this);
            divider.setBackgroundColor(android.graphics.Color.rgb(44,56,67));
            headerCell.addView(divider,new FrameLayout.LayoutParams(Ui.dp(this,1),Ui.dp(this,20),Gravity.RIGHT|Gravity.CENTER_VERTICAL));
            View grip=new View(this);
            grip.setContentDescription("Promeni širinu: "+field.optString("name"));
            headerCell.addView(grip,new FrameLayout.LayoutParams(Ui.dp(this,14),-1,Gravity.RIGHT));
            final float[] downX={0};final int[] downWidth={width};final boolean[] moved={false};
            grip.setOnTouchListener((v,event)->{
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX[0]=event.getRawX();downWidth[0]=columnWidths.get(fid);moved[0]=false;
                        v.getParent().requestDisallowInterceptTouchEvent(true);return true;
                    case MotionEvent.ACTION_MOVE:
                        float delta=event.getRawX()-downX[0];
                        if(Math.abs(delta)>ViewConfiguration.get(this).getScaledTouchSlop())moved[0]=true;
                        if(moved[0])resizeColumn(fid,Math.max(Ui.dp(this,32),Math.min(Ui.dp(this,800),downWidth[0]+Math.round(delta))));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(moved[0])getSharedPreferences("column_widths",MODE_PRIVATE).edit()
                            .putFloat(widthPreferenceKey(libraryId,fid),columnWidths.get(fid)/getResources().getDisplayMetrics().density).apply();
                        else v.performClick();
                        v.getParent().requestDisallowInterceptTouchEvent(false);return true;
                    case MotionEvent.ACTION_CANCEL:
                        resizeColumn(fid,downWidth[0]);
                        v.getParent().requestDisallowInterceptTouchEvent(false);return true;
                    default:return false;
                }
            });
            header.addView(headerCell,new LinearLayout.LayoutParams(width,-2));
            columnViews.get(fid).add(headerCell);
        }
        grid.addView(header);
        for(int i=0;i<rows.length();i++){
            JSONObject record=rows.optJSONObject(i);LinearLayout line=Ui.row(this);
            line.setBackgroundColor(i%2==0?Ui.CARD:Ui.BG);JSONObject data=record.optJSONObject("data");
            for(int j=0;j<fields.length();j++){
                JSONObject field=fields.optJSONObject(j);
                line.addView(cell(store.display(field,data.opt(field.optString("id"))),false,field));
            }
            line.setOnClickListener(v->detail(lib,record));grid.addView(line);
        }
        hs.addView(grid);body.addView(hs);
    }
    static boolean isIdField(JSONObject field){
        JSONObject original=field.optJSONObject("source");
        return field.optString("name").equalsIgnoreCase("id")||original!=null&&original.optString("tt").equalsIgnoreCase("id");
    }
    String widthPreferenceKey(String libraryId,String fieldId){
        return new JSONArray().put(libraryId).put(fieldId).toString();
    }
    void resizeColumn(String fieldId,int pixels){
        columnWidths.put(fieldId,pixels);
        List<View> views=columnViews.get(fieldId);if(views==null)return;
        for(View view:views){
            if(view instanceof TextView)((TextView)view).setWidth(pixels);
            ViewGroup.LayoutParams params=view.getLayoutParams();
            if(params!=null){params.width=pixels;view.setLayoutParams(params);}
        }
    }
    TextView cell(String value,boolean header,JSONObject field){
        TextView view=Ui.text(this,value,header?13:14,header?Ui.ACCENT:Ui.TEXT);
        String fid=field.optString("id");
        view.setPadding(Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,6));
        view.setWidth(columnWidths.get(fid));
        boolean numeric=field.optString("type").equals("integer")||field.optString("type").equals("decimal");
        if(numeric&&!header)view.setSingleLine(true);else view.setMaxLines(header?3:4);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL);view.setMinHeight(Ui.dp(this,48));
        if(!columnViews.containsKey(fid))columnViews.put(fid,new ArrayList<>());
        columnViews.get(fid).add(view);
        return view;
    }
    void detail(JSONObject lib,JSONObject r){LinearLayout b=Ui.column(this);b.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,12));JSONObject d=r.optJSONObject("data");JSONArray fs=lib.optJSONArray("fields");for(int i=0;i<fs.length();i++){JSONObject f=fs.optJSONObject(i);b.addView(Ui.text(this,f.optString("name"),12,Ui.MUTED));JSONArray linkedValues=d.optJSONArray(f.optString("id"));
        if(!f.optString("type").equals("link")||linkedValues==null||linkedValues.length()==0){
            b.addView(Ui.text(this,store.display(f,d.opt(f.optString("id"))),16,Ui.TEXT));
        }
        if(f.optString("type").equals("link")){JSONArray links=store.orderedLinks(f,d.optJSONArray(f.optString("id")));if(links!=null)for(int j=0;j<links.length();j++){JSONObject linked=store.record(links.optString(j));if(linked!=null)b.addView(Ui.button(this,"↗ "+store.label(linked),()->detail(store.library(linked.optString("library_id")),linked)));}}}
        // Reverse references use existing links only, with no inferred state or funnel calculations.
        JSONArray libs=store.libraries();LinearLayout reverse=Ui.column(this);for(int i=0;i<libs.length();i++){JSONObject l=libs.optJSONObject(i);JSONArray fields=l.optJSONArray("fields"),rows=store.orderedRecords(l.optString("id"),store.records(l.optString("id")));for(int j=0;j<rows.length();j++){JSONObject rr=rows.optJSONObject(j);boolean found=false;for(int k=0;k<fields.length();k++){JSONObject f=fields.optJSONObject(k);if(!f.optString("type").equals("link"))continue;JSONArray ids=rr.optJSONObject("data").optJSONArray(f.optString("id"));if(ids!=null)for(int x=0;x<ids.length();x++)if(ids.optString(x).equals(r.optString("id")))found=true;}if(found)reverse.addView(Ui.button(this,l.optString("name")+" · "+store.label(rr),()->detail(l,rr)));}}
        if(reverse.getChildCount()>0){b.addView(Ui.text(this,"Zapisi koji upućuju ovde",13,Ui.MUTED));b.addView(reverse);}ScrollView s=new ScrollView(this);s.addView(b);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(store.label(r)).setView(s).setNegativeButton("Zatvori",null).setPositiveButton("Uredi",(v,w)->Ui.editRecord(this,store,lib,r,id->render())).setNeutralButton("Obriši",(v,w)->new AlertDialog.Builder(this).setTitle("Obrisati zapis?").setMessage("Biće uklonjene i veze drugih zapisa prema njemu.").setNegativeButton("Ne",null).setPositiveButton("Obriši",(x,y)->{try{store.deleteRecord(r.optString("id"));render();}catch(Exception ex){Ui.error(this,ex);}}).show()).create();dialog.show();dialog.getWindow().setLayout(-1,Ui.dp(this,600));}
    void menu(JSONObject lib){String[] choices=lib==null?new String[]{"Backup","Automatski backup","Restore","Dashboard pristup"}:new String[]{table?"Prikaz kao kartice":"Prikaz kao tabela","Uredi strukturu","Preimenuj tabelu","Naslov zapisa","Jedinstvene kombinacije","Obriši tabelu","Backup","Automatski backup","Restore","Dashboard pristup"};new AlertDialog.Builder(this).setItems(choices,(d,i)->{String c=choices[i];switch(c){case "Prikaz kao kartice":case "Prikaz kao tabela":table=!table;render();break;case "Uredi strukturu":structure(lib);break;case "Preimenuj tabelu":rename(lib);break;case "Naslov zapisa":titleField(lib);break;case "Jedinstvene kombinacije":uniqueGroups(lib);break;case "Obriši tabelu":confirmDeleteLibrary(lib);break;case "Backup":backup();break;case "Automatski backup":autoBackupStatus();break;case "Restore":restore();break;case "Dashboard pristup":dashboard();break;}}).show();}
    void confirmDeleteLibrary(JSONObject library){
        String id=library.optString("id");
        int records=store.records(id).length();
        ArrayList<String> incoming=new ArrayList<>();
        JSONArray libraries=store.libraries();
        for(int i=0;i<libraries.length();i++){
            JSONObject other=libraries.optJSONObject(i);
            if(other.optString("id").equals(id))continue;
            JSONArray fields=other.optJSONArray("fields");
            for(int j=0;j<fields.length();j++){
                JSONObject field=fields.optJSONObject(j);
                if(PlanStore.referencesTarget(field,id))
                    incoming.add(other.optString("name")+" → "+field.optString("name"));
            }
        }
        String message="Biće obrisana tabela „"+library.optString("name")+"“ i svi njeni zapisi ("+records+").";
        if(!incoming.isEmpty())message+="\n\nPovezivanja iz sledećih kolona biće uklonjena. Obične kolone veza se brišu; kod uslovnih ostaju druge ciljne tabele:\n"+String.join("\n",incoming);
        message+="\n\nOstali zapisi ostaju. Brisanje možeš poništiti samo vraćanjem ranijeg backupa.";
        new AlertDialog.Builder(this).setTitle("Obrisati tabelu?").setMessage(message)
            .setNegativeButton("Zatvori",null)
            .setPositiveButton("Obriši",(d,w)->{
                try{
                    store.deleteLibrary(id);
                    if(current.equals(id))current="";
                    render();
                }catch(Exception e){Ui.error(this,e);}
            }).show();
    }
    void uniqueGroups(JSONObject library){
        JSONObject fresh=store.library(library.optString("id"));
        JSONArray fields=fresh.optJSONArray("fields"),groups=fresh.optJSONArray("uniqueGroups");
        if(groups==null)groups=new JSONArray();
        LinearLayout body=Ui.column(this);
        body.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,12));
        body.addView(Ui.text(this,"Duplikat je ista kombinacija svih izabranih kolona. Nepotpune kombinacije mogu da se ponavljaju.",13,Ui.MUTED));
        final AlertDialog[] holder=new AlertDialog[1];
        if(groups.length()==0)body.addView(Ui.text(this,"Nema jedinstvenih kombinacija",14,Ui.MUTED));
        for(int i=0;i<groups.length();i++){
            JSONArray group=groups.optJSONArray(i);ArrayList<String> names=new ArrayList<>();
            for(int j=0;j<group.length();j++)for(int k=0;k<fields.length();k++){
                JSONObject f=fields.optJSONObject(k);
                if(f.optString("id").equals(group.optString(j)))names.add(f.optString("name"));
            }
            int index=i;
            LinearLayout row=Ui.row(this);
            row.addView(Ui.text(this,String.join(" + ",names),14,Ui.TEXT),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(Ui.button(this,"Ukloni",()->{
                try{
                    JSONObject latest=store.library(fresh.optString("id"));
                    JSONArray currentGroups=latest.getJSONArray("uniqueGroups"),kept=new JSONArray();
                    for(int j=0;j<currentGroups.length();j++)if(j!=index)kept.put(currentGroups.getJSONArray(j));
                    latest.put("uniqueGroups",kept);store.saveLibrary(latest);
                    holder[0].dismiss();uniqueGroups(latest);
                }catch(Exception e){Ui.error(this,e);}
            }));body.addView(row);
        }
        body.addView(Ui.button(this,"＋ Nova kombinacija",()->{holder[0].dismiss();addUniqueGroup(fresh);}));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        holder[0]=new AlertDialog.Builder(this).setTitle("Jedinstvene kombinacije").setView(scroll).setNegativeButton("Gotovo",null).create();
        holder[0].show();
    }
    void addUniqueGroup(JSONObject library){
        JSONObject fresh=store.library(library.optString("id"));
        JSONArray fields=fresh.optJSONArray("fields");
        String[] labels=new String[fields.length()];boolean[] checked=new boolean[fields.length()];
        for(int i=0;i<fields.length();i++)labels[i]=fields.optJSONObject(i).optString("name");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Izaberi kolone za kombinaciju")
            .setMultiChoiceItems(labels,checked,(d,i,on)->checked[i]=on)
            .setNegativeButton("Zatvori",(d,w)->uniqueGroups(fresh)).setPositiveButton("Sačuvaj",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{
            try{
                JSONArray group=new JSONArray();
                for(int i=0;i<checked.length;i++)if(checked[i])group.put(fields.getJSONObject(i).getString("id"));
                if(group.length()<2)throw new IllegalArgumentException("Izaberi najmanje dve kolone");
                JSONObject latest=store.library(fresh.optString("id"));
                JSONArray groups=latest.optJSONArray("uniqueGroups");if(groups==null)groups=new JSONArray();
                groups.put(group);latest.put("uniqueGroups",groups);store.saveLibrary(latest);
                dialog.dismiss();uniqueGroups(latest);
            }catch(Exception e){Ui.error(this,e);}
        }));dialog.show();
    }
    void newLibrary(){EditText e=Ui.input(this,"Naziv tabele","");AlertDialog d=new AlertDialog.Builder(this).setTitle("Nova tabela").setView(e).setNegativeButton("Zatvori",null).setPositiveButton("Kreiraj",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{try{JSONObject l=new JSONObject().put("id",UUID.randomUUID().toString()).put("name",e.getText().toString().trim()).put("fields",new JSONArray());store.saveLibrary(l);current=l.getString("id");table=true;d.dismiss();render();structure(l);}catch(Exception ex){Ui.error(this,ex);}}));d.show();}
    void rename(JSONObject lib){EditText e=Ui.input(this,"Naziv",lib.optString("name"));AlertDialog d=new AlertDialog.Builder(this).setTitle("Naziv tabele").setView(e).setNegativeButton("Zatvori",null).setPositiveButton("Sačuvaj",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{try{JSONObject copy=new JSONObject(lib.toString());copy.put("name",e.getText().toString().trim());store.saveLibrary(copy);d.dismiss();render();}catch(Exception ex){Ui.error(this,ex);}}));d.show();}
    void titleField(JSONObject lib){JSONArray fields=lib.optJSONArray("fields");ArrayList<String>labels=new ArrayList<>(),ids=new ArrayList<>();labels.add("Automatski izbor");ids.add("");for(int i=0;i<fields.length();i++){JSONObject f=fields.optJSONObject(i);if(Arrays.asList("text","integer","choice").contains(f.optString("type"))){labels.add(f.optString("name"));ids.add(f.optString("id"));}}new AlertDialog.Builder(this).setTitle("Naslov zapisa").setItems(labels.toArray(new String[0]),(d,i)->{try{JSONObject l=new JSONObject(lib.toString());l.put("titleField",ids.get(i));store.saveLibrary(l);render();}catch(Exception ex){Ui.error(this,ex);}}).show();}
    void structure(JSONObject lib){
        JSONObject fresh=store.library(lib.optString("id"));
        LinearLayout body=Ui.column(this);
        body.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,12));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        ArrayList<LinearLayout> fieldRows=new ArrayList<>();
        final AlertDialog[] holder=new AlertDialog[1];
        final boolean[] changed={false};
        JSONArray fields=fresh.optJSONArray("fields");
        for(int i=0;i<fields.length();i++){
            JSONObject field=fields.optJSONObject(i);
            LinearLayout row=Ui.row(this);
            Button name=Ui.button(this,field.optString("name")+" · "+typeName(field.optString("type")),()->{
                holder[0].dismiss();editField(fresh,field);
            });
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button up=Ui.button(this,"↑",()->{
                int index=fieldRows.indexOf(row);
                if(index<=0)return;
                try{
                    JSONArray currentFields=fresh.getJSONArray("fields"),ordered=new JSONArray();
                    for(int j=0;j<currentFields.length();j++)
                        ordered.put(currentFields.getJSONObject(j==index-1?index:j==index?index-1:j));
                    JSONObject updated=new JSONObject(fresh.toString());updated.put("fields",ordered);
                    store.saveLibrary(updated);fresh.put("fields",ordered);changed[0]=true;
                    int position=scroll.getScrollY();
                    Collections.swap(fieldRows,index,index-1);
                    body.removeView(row);body.addView(row,index-1);
                    for(int j=0;j<fieldRows.size();j++)fieldRows.get(j).getChildAt(1).setEnabled(j>0);
                    scroll.post(()->scroll.scrollTo(0,position));
                }catch(Exception e){Ui.error(this,e);}
            });
            up.setEnabled(i>0);row.addView(up);fieldRows.add(row);body.addView(row);
        }
        body.addView(Ui.button(this,"Jedinstvene kombinacije",()->{holder[0].dismiss();uniqueGroups(fresh);}));
        body.addView(Ui.button(this,"＋ Dodaj polje",()->{holder[0].dismiss();editField(fresh,null);}));
        holder[0]=new AlertDialog.Builder(this).setTitle("Struktura · "+fresh.optString("name"))
            .setView(scroll).setNegativeButton("Gotovo",null)
            .setNeutralButton("Obriši tabelu",(d,w)->confirmDeleteLibrary(fresh)).create();
        // Refresh the table once after closing; arrow taps move only the existing row views.
        holder[0].setOnDismissListener(d->{if(changed[0])render();});
        holder[0].show();holder[0].getWindow().setLayout(-1,Ui.dp(this,600));
    }
    String typeName(String t){switch(t){case "text":return "Tekst";case "integer":return "Ceo broj";case "decimal":return "Decimalni broj";case "boolean":return "Da / Ne";case "date":return "Datum";case "datetime":return "Datum i vreme";case "choice":return "Jedan izbor";case "multichoice":return "Više izbora";case "link":return "Veza ka zapisima";default:return t;}}
    void editField(JSONObject lib,JSONObject field){LinearLayout b=Ui.column(this);b.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,16));EditText name=Ui.input(this,"Naziv polja",field==null?"":field.optString("name"));b.addView(name);String[] types={"text","integer","decimal","boolean","date","datetime","choice","multichoice","link"};ArrayList<String>typeLabels=new ArrayList<>();for(String t:types)typeLabels.add(typeName(t));Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,typeLabels));if(field!=null)type.setSelection(Math.max(0,Arrays.asList(types).indexOf(field.optString("type"))));type.setEnabled(field==null);b.addView(type);if(field!=null)b.addView(Ui.text(this,"Tip postojećeg polja je sačuvan radi zaštite podataka. Za drugi tip dodaj novo polje.",12,Ui.MUTED));
        CheckBox required=new CheckBox(this);required.setText("Obavezno pri unosu");required.setTextColor(Ui.TEXT);required.setChecked(field!=null&&field.optBoolean("required"));b.addView(required);
        CheckBox unique=new CheckBox(this);
        unique.setText("Jedinstvena vrednost (Unique)");unique.setTextColor(Ui.TEXT);
        unique.setChecked(field!=null&&field.optBoolean("unique"));b.addView(unique);
        b.addView(Ui.text(this,"Prazne vrednosti mogu da se ponavljaju. Tekst razlikuje velika i mala slova.",12,Ui.MUTED));
        EditText description=Ui.input(this,"Opis / uputstvo",field==null?"":field.optString("description"));b.addView(description);
        EditText options=Ui.input(this,"Opcije — jedna u svakom redu","");options.setMinLines(3);if(field!=null){JSONArray o=field.optJSONArray("options");ArrayList<String>lines=new ArrayList<>();if(o!=null)for(int i=0;i<o.length();i++)lines.add(o.optString(i));options.setText(String.join("\n",lines));}b.addView(options);
        b.addView(Ui.text(this,"Povezana tabela",12,Ui.MUTED));JSONArray libs=store.libraries();ArrayList<String>libNames=new ArrayList<>(),libIds=new ArrayList<>();for(int i=0;i<libs.length();i++){JSONObject l=libs.optJSONObject(i);libNames.add(l.optString("name"));libIds.add(l.optString("id"));}Spinner target=new Spinner(this);target.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,libNames));if(field!=null)target.setSelection(Math.max(0,libIds.indexOf(field.optString("target"))));b.addView(target);
        CheckBox maxIdDefault=new CheckBox(this);maxIdDefault.setText("Podrazumevano: zapis sa najvećim id");maxIdDefault.setTextColor(Ui.TEXT);
        maxIdDefault.setChecked(field!=null&&field.optString("linkDefault").equals("max_id"));b.addView(maxIdDefault);
        LinearLayout conditionalBox=Ui.column(this);b.addView(conditionalBox);
        CheckBox conditional=new CheckBox(this);conditional.setText("Ciljna tabela zavisi od jednog izbora");conditional.setTextColor(Ui.TEXT);conditional.setChecked(field!=null&&PlanStore.conditional(field));conditionalBox.addView(conditional);
        LinearLayout rules=Ui.column(this);conditionalBox.addView(rules);
        ArrayList<String> sourceNames=new ArrayList<>(),sourceIds=new ArrayList<>();
        JSONArray currentFields=lib.optJSONArray("fields");
        for(int i=0;i<currentFields.length();i++){JSONObject f=currentFields.optJSONObject(i);if(f.optString("type").equals("choice")){sourceNames.add(f.optString("name"));sourceIds.add(f.optString("id"));}}
        rules.addView(Ui.text(this,"Kolona sa jednim izborom",12,Ui.MUTED));
        Spinner source=new Spinner(this);source.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sourceNames));
        if(field!=null)source.setSelection(Math.max(0,sourceIds.indexOf(field.optString("conditionField"))));rules.addView(source);
        LinearLayout mappings=Ui.column(this);rules.addView(mappings);
        LinkedHashMap<String,Spinner> mappingInputs=new LinkedHashMap<>();
        ArrayList<String> mappingNames=new ArrayList<>(),mappingIds=new ArrayList<>();mappingNames.add("Bez veze");mappingIds.add("");mappingNames.addAll(libNames);mappingIds.addAll(libIds);
        final String[] renderedSource={""};
        Runnable rebuildMappings=()->{
            int index=source.getSelectedItemPosition();String sourceId=index<0||index>=sourceIds.size()?"":sourceIds.get(index);
            if(sourceId.equals(renderedSource[0])&&mappings.getChildCount()>0)return;
            renderedSource[0]=sourceId;mappings.removeAllViews();mappingInputs.clear();
            if(sourceId.isEmpty()){mappings.addView(Ui.text(this,"Prvo dodaj kolonu tipa Jedan izbor.",12,Ui.MUTED));return;}
            JSONObject controller=null;for(int i=0;i<currentFields.length();i++)if(currentFields.optJSONObject(i).optString("id").equals(sourceId))controller=currentFields.optJSONObject(i);
            JSONArray choices=controller==null?null:controller.optJSONArray("options");
            JSONObject saved=field!=null&&sourceId.equals(field.optString("conditionField"))?field.optJSONObject("targets"):null;
            if(choices!=null)for(int i=0;i<choices.length();i++){
                String option=choices.optString(i);mappings.addView(Ui.text(this,option,13,Ui.TEXT));
                Spinner tableChoice=new Spinner(this);tableChoice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,mappingNames));
                tableChoice.setSelection(saved==null?0:Math.max(0,mappingIds.indexOf(saved.optString(option))));
                mappings.addView(tableChoice);mappingInputs.put(option,tableChoice);
            }
            mappings.addView(Ui.text(this,"Promena izbora pri unosu uklanja veze koje pripadaju drugoj tabeli.",12,Ui.MUTED));
        };
        source.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){rebuildMappings.run();}public void onNothingSelected(android.widget.AdapterView<?> p){rebuildMappings.run();}});
        conditional.setOnCheckedChangeListener((view,checked)->{rules.setVisibility(checked?View.VISIBLE:View.GONE);target.setVisibility(checked?View.GONE:View.VISIBLE);if(checked)rebuildMappings.run();});
        rules.setVisibility(conditional.isChecked()?View.VISIBLE:View.GONE);rebuildMappings.run();
        Runnable visibility=()->{String t=types[type.getSelectedItemPosition()];options.setVisibility(t.equals("choice")||t.equals("multichoice")?View.VISIBLE:View.GONE);target.setVisibility(t.equals("link")&&!conditional.isChecked()?View.VISIBLE:View.GONE);conditionalBox.setVisibility(t.equals("link")?View.VISIBLE:View.GONE);maxIdDefault.setVisibility(t.equals("link")?View.VISIBLE:View.GONE);};type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){visibility.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});visibility.run();
        ScrollView s=new ScrollView(this);s.addView(b);AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(field==null?"Novo polje":"Uredi polje").setView(s).setNegativeButton("Zatvori",(d,w)->structure(lib)).setPositiveButton("Sačuvaj",null);if(field!=null)builder.setNeutralButton("Ukloni",(d,w)->new AlertDialog.Builder(this).setTitle("Ukloniti kolonu?").setMessage("Biće obrisane sve vrednosti ove kolone. Pre toga možeš napraviti backup iz menija.").setNegativeButton("Ne",(x,y)->structure(lib)).setPositiveButton("Ukloni",(x,y)->{try{store.removeField(lib.optString("id"),field.optString("id"));structure(lib);render();}catch(Exception ex){Ui.error(this,ex);}}).show());AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{try{
            JSONObject fresh=store.library(lib.optString("id"));JSONObject f=field==null?new JSONObject().put("id",UUID.randomUUID().toString()):new JSONObject(field.toString());String t=types[type.getSelectedItemPosition()];f.put("name",name.getText().toString().trim()).put("type",t).put("required",required.isChecked()).put("unique",unique.isChecked()).put("description",description.getText().toString().trim());if(t.equals("choice")||t.equals("multichoice")){JSONArray opts=new JSONArray();Set<String>seen=new LinkedHashSet<>();for(String line:options.getText().toString().split("\n"))if(!line.trim().isEmpty())seen.add(line.trim());for(String line:seen)opts.put(line);f.put("options",opts);}if(t.equals("link")){
                if(maxIdDefault.isChecked())f.put("linkDefault","max_id");else f.remove("linkDefault");
                if(conditional.isChecked()){
                    int selectedSource=source.getSelectedItemPosition();if(selectedSource<0||selectedSource>=sourceIds.size())throw new IllegalArgumentException("Prvo dodaj kolonu tipa Jedan izbor");
                    JSONObject targets=new JSONObject();for(Map.Entry<String,Spinner> entry:mappingInputs.entrySet()){int index=entry.getValue().getSelectedItemPosition();if(index>0)targets.put(entry.getKey(),mappingIds.get(index));}
                    f.put("conditionField",sourceIds.get(selectedSource)).put("targets",targets);f.remove("target");
                }else{f.put("target",libIds.get(target.getSelectedItemPosition()));f.remove("conditionField");f.remove("targets");}
            }JSONArray fields=fresh.getJSONArray("fields"),updated=new JSONArray();boolean found=false;for(int i=0;i<fields.length();i++){JSONObject old=fields.getJSONObject(i);if(old.getString("id").equals(f.getString("id"))){updated.put(f);found=true;}else updated.put(old);}if(!found)updated.put(f);fresh.put("fields",updated);store.saveLibrary(fresh);dialog.dismiss();render();structure(fresh);
        }catch(Exception ex){Ui.error(this,ex);}}));dialog.show();dialog.getWindow().setLayout(-1,Ui.dp(this,620));}
    void dashboard(){EditText e=Ui.input(this,"npr. com.example.dashboard",getSharedPreferences("access",MODE_PRIVATE).getString("dashboard_package",""));LinearLayout b=Ui.column(this);b.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),Ui.dp(this,12));b.addView(Ui.text(this,"Upiši applicationId svog dashboarda. Samo taj paket može da čita podatke. Prazno polje isključuje pristup.",14,Ui.MUTED));b.addView(e);AlertDialog d=new AlertDialog.Builder(this).setTitle("Dashboard pristup").setView(b).setNegativeButton("Zatvori",null).setPositiveButton("Sačuvaj",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{String pkg=e.getText().toString().trim();if(!pkg.isEmpty()&&!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")){Ui.toast(this,"Neispravan applicationId");return;}getSharedPreferences("access",MODE_PRIVATE).edit().putString("dashboard_package",pkg).commit();d.dismiss();Ui.toast(this,pkg.isEmpty()?"Dashboard pristup isključen":"Pristup dozvoljen: "+pkg);}));d.show();}
    void autoBackupStatus(){new AlertDialog.Builder(this).setTitle("Automatski backup").setMessage(AutoBackup.status(this)).setNegativeButton("Zatvori",null).setPositiveButton("Napravi sada",(d,w)->{if(AutoBackup.needsPermission(this))requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},41);else{AutoBackup.request(this);Ui.toast(this,"Backup je zakazan u pozadini.");}}).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)AutoBackup.request(this);}
    void backup(){try{pendingBackup=store.backup();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"1k-plan-"+Ui.now(false)+".json");startActivityForResult(i,10);}catch(Exception ex){Ui.error(this,ex);}}
    void restore(){new AlertDialog.Builder(this).setTitle("Restore zamenjuje sve podatke").setMessage("Izaberi 1K Test JSON backup. Provera fajla i vraćanje rade se u jednoj transakciji.").setNegativeButton("Zatvori",null).setPositiveButton("Izaberi backup",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),11)).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(request==10){if(pendingBackup==null)throw new IllegalStateException("Ponovo pokreni backup");try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("Fajl nije otvoren");out.write(pendingBackup.toString(2).getBytes(StandardCharsets.UTF_8));}pendingBackup=null;Ui.toast(this,"Backup sačuvan");}else if(request==11){JSONObject backup;try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException("Fajl nije otvoren");backup=new JSONObject(new String(PlanStore.readBytes(in),StandardCharsets.UTF_8));}store.restore(backup);current="";render();Ui.toast(this,"Backup vraćen");}}catch(Exception ex){Ui.error(this,ex);}}
    void closeSearch(){
        searchExpanded=false;
        if(toolbarSearch!=null){
            toolbarSearch.setText("");toolbarSearch.clearFocus();
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(toolbarSearch.getWindowToken(),0);
            toolbarSearch.setVisibility(View.GONE);
        }
        if(toolbarTitle!=null)toolbarTitle.setVisibility(View.VISIBLE);
        if(toolbarSearchIcon!=null)toolbarSearchIcon.setVisibility(View.VISIBLE);
    }
    @Override public void onBackPressed(){if(searchExpanded){closeSearch();}else if(!current.isEmpty()){current="";render();}else super.onBackPressed();}
    @Override protected void onDestroy(){store.close();super.onDestroy();}
}
