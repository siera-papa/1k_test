package rs.project1k.plan;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;

/** One launcher widget; no main Activity behind the translucent entry window. */
public final class QuickEntryActivity extends Activity {
    Ui.Form smartForm;PlanStore store;JSONObject attempts,tests,metas,nodes;String testId="",metaId="",beforeId="",afterId="",date;EditText cost,duration;Button testButton,metaButton,beforeButton,afterButton,dateButton;JSONObject testField,metaField,beforeField,afterField;
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new PlanStore(this);AutoBackup.ensure(this);attempts=store.byName("Pokušaj");if(attempts==null){Ui.toast(this,"Tabela Pokušaj nije dostupna");finish();return;}
        if(new AttemptRules(attempts).enabled()){openSmartEntry(state);return;}
        JSONArray attemptFields=attempts.optJSONArray("fields");
        for(int i=0;i<attemptFields.length();i++)if(PlanStore.conditional(attemptFields.optJSONObject(i))||customDefault(attemptFields.optJSONObject(i))){
            openConditionalEntry();
            return;
        }
        testField=PlanStore.field(attempts,"test");metaField=PlanStore.field(attempts,"meta");beforeField=PlanStore.field(attempts,"Čvor pre pokušaja");afterField=PlanStore.field(attempts,"Čvor posle pokušaja");
        if(testField==null||metaField==null||beforeField==null||afterField==null){Ui.toast(this,"Polja za brzi unos su uklonjena. Koristi unos u aplikaciji.");finish();return;}
        tests=store.library(testField.optString("target"));metas=store.library(metaField.optString("target"));nodes=store.library(beforeField.optString("target"));date=Ui.now(true);
        testId=getPreferences(MODE_PRIVATE).getString("last_test","");if(store.record(testId)==null)testId="";
        if(state==null){
            if(testField.optString("linkDefault").equals("max_id"))testId=store.defaultLink(testField,testField.optString("target"));
            metaId=store.defaultLink(metaField,metaField.optString("target"));
            beforeId=store.defaultLink(beforeField,beforeField.optString("target"));afterId=store.defaultLink(afterField,afterField.optString("target"));
        }
        if(state!=null){testId=state.getString("test",testId);metaId=state.getString("meta","");beforeId=state.getString("before","");afterId=state.getString("after","");date=state.getString("date",date);}
        LinearLayout panel=Ui.column(this);panel.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,14));android.graphics.drawable.GradientDrawable background=Ui.bg(Ui.CARD,0,this);float radius=Ui.dp(this,24);background.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});panel.setBackground(background);TextView handle=Ui.text(this,"━━━━",14,Ui.MUTED);handle.setGravity(Gravity.CENTER);panel.addView(handle);
        testButton=Ui.button(this,label(testId,"Izaberi test"),()->Ui.chooseLinks(this,store,testField.optString("target"),selected(testId),false,a->{testId=a.optString(0);testButton.setText(label(testId,"Izaberi test"));}));panel.addView(testButton);
        metaButton=Ui.button(this,label(metaId,"Izaberi ili dodaj metu"),()->Ui.chooseLinks(this,store,metaField.optString("target"),selected(metaId),false,a->{metaId=a.optString(0);metaButton.setText(label(metaId,"Izaberi ili dodaj metu"));}));panel.addView(metaButton);
        LinearLayout r=Ui.row(this);beforeButton=Ui.button(this,"Pre: "+label(beforeId,"izaberi čvor"),()->pickNode(true));afterButton=Ui.button(this,"Posle: "+label(afterId,"izaberi čvor"),()->pickNode(false));r.addView(beforeButton,new LinearLayout.LayoutParams(0,-2,1));r.addView(afterButton,new LinearLayout.LayoutParams(0,-2,1));panel.addView(r);
        LinearLayout extra=Ui.column(this);cost=Ui.input(this,"Trošak · din.",state==null?"":state.getString("cost",""));duration=Ui.input(this,"Trajanje · min.",state==null?"":state.getString("duration",""));cost.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);duration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);LinearLayout numbers=Ui.row(this);numbers.addView(cost,new LinearLayout.LayoutParams(0,-2,1));numbers.addView(duration,new LinearLayout.LayoutParams(0,-2,1));cost.setVisibility(PlanStore.field(attempts,"Trošak (din.)")==null?View.GONE:View.VISIBLE);duration.setVisibility(PlanStore.field(attempts,"Vreme (min.)")==null?View.GONE:View.VISIBLE);extra.addView(numbers);
        dateButton=Ui.button(this,date,()->Ui.date(this,dateButton,true,date,s->date=s));dateButton.setVisibility(PlanStore.field(attempts,"datum i vreme")==null?View.GONE:View.VISIBLE);extra.addView(dateButton);extra.setVisibility(View.GONE);panel.addView(Ui.button(this,"Trošak, trajanje i datum ▾",()->extra.setVisibility(extra.getVisibility()==View.GONE?View.VISIBLE:View.GONE)));panel.addView(extra);
        // Additional fields added by the user remain editable from this overlay.
        panel.addView(Ui.button(this,"Sva polja…",()->{try{Ui.editRecord(this,store,attempts,newDraft(),id->{remember();finish();});}catch(Exception e){Ui.error(this,e);}}));panel.addView(Ui.button(this,"✓ Sačuvaj pokušaj",()->save()));
        presentPanel(panel);
    }
    void openSmartEntry(Bundle state){
        JSONObject draft=null;
        if(state!=null&&state.getString("smart_draft")!=null)try{draft=new JSONObject().put("data",new JSONObject(state.getString("smart_draft")));}catch(JSONException ignored){}
        smartForm=new Ui.Form(this,store,attempts,draft);
        LinearLayout panel=smartForm.body;panel.setBackground(Ui.bg(Ui.CARD,24,this));
        panel.addView(Ui.button(this,"✓ Sačuvaj pokušaj",()->{try{
            store.saveRecord(attempts.optString("id"),null,smartForm.data());Ui.toast(this,"Pokušaj sačuvan");finish();
        }catch(Exception e){Ui.error(this,e);}}));
        presentPanel(panel);
    }
    boolean customDefault(JSONObject field){
        if(!field.optString("linkDefault").equals("max_id"))return false;
        for(String name:new String[]{"test","meta","Čvor pre pokušaja","Čvor posle pokušaja"}){
            JSONObject standard=PlanStore.field(attempts,name);if(standard!=null&&standard.optString("id").equals(field.optString("id")))return false;
        }
        return true;
    }
    void openConditionalEntry(){
        Ui.Form form=new Ui.Form(this,store,attempts,null);
        LinearLayout panel=form.body;panel.setBackground(Ui.bg(Ui.CARD,24,this));
        panel.addView(Ui.button(this,"✓ Sačuvaj pokušaj",()->{try{
            store.saveRecord(attempts.optString("id"),null,form.data());Ui.toast(this,"Pokušaj sačuvan");finish();
        }catch(Exception e){Ui.error(this,e);}}));
        presentPanel(panel);
    }
    void presentPanel(LinearLayout panel){
        // Transparent full-window touch surface: the launcher remains visible behind the panel.
        // Outside taps are handled here rather than relying on floating-window ACTION_OUTSIDE delivery.
        FrameLayout surface=new FrameLayout(this);
        surface.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        surface.setOnClickListener(v->finish());
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOnClickListener(v->{});
        scroll.addView(panel);
        surface.addView(scroll,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        Window w=getWindow();
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=29)w.setNavigationBarContrastEnforced(false);
        if(Build.VERSION.SDK_INT>=30)w.setDecorFitsSystemWindows(false);
        else w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        surface.setOnApplyWindowInsetsListener((view,insets)->{
            int bottom;
            if(Build.VERSION.SDK_INT>=30){
                int navigation=insets.getInsets(WindowInsets.Type.systemBars()).bottom;
                int keyboard=insets.getInsets(WindowInsets.Type.ime()).bottom;
                bottom=Math.max(navigation,keyboard);
            }else {
                // Older adjustResize windows already shrink above the IME.
                bottom=insets.getSystemWindowInsetBottom();
                if(bottom>Ui.dp(this,100))bottom=0;
            }
            // Insets belong inside the panel: its background continues to the physical bottom edge.
            panel.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,14)+bottom);
            return insets;
        });
        setContentView(surface);
        w.setLayout(-1,-1);
        surface.requestApplyInsets();
    }
    JSONArray selected(String id){return id.isEmpty()?new JSONArray():new JSONArray().put(id);}
    String label(String id,String empty){return id.isEmpty()?empty:store.label(store.record(id));}
    void pickNode(boolean before){JSONObject field=before?beforeField:afterField;Ui.chooseLinks(this,store,field.optString("target"),selected(before?beforeId:afterId),false,a->{if(before){beforeId=a.optString(0);beforeButton.setText("Pre: "+label(beforeId,"izaberi čvor"));}else {afterId=a.optString(0);afterButton.setText("Posle: "+label(afterId,"izaberi čvor"));}});}
    JSONObject data()throws Exception{JSONObject d=new JSONObject();put(d,testField,selected(testId));put(d,metaField,selected(metaId));put(d,beforeField,selected(beforeId));put(d,afterField,selected(afterId));put(d,PlanStore.field(attempts,"datum i vreme"),date);if(!cost.getText().toString().isEmpty())put(d,PlanStore.field(attempts,"Trošak (din.)"),Long.parseLong(cost.getText().toString()));if(!duration.getText().toString().isEmpty())put(d,PlanStore.field(attempts,"Vreme (min.)"),Long.parseLong(duration.getText().toString()));return d;}
    void put(JSONObject d,JSONObject f,Object v)throws JSONException{if(f!=null)d.put(f.optString("id"),v);}
    JSONObject newDraft()throws Exception{return new JSONObject().put("data",data());}
    void remember(){getPreferences(MODE_PRIVATE).edit().putString("last_test",testId).apply();}
    void save(){if(testId.isEmpty()||metaId.isEmpty()){Ui.toast(this,"Izaberi test i metu");return;}try{store.saveRecord(attempts.optString("id"),null,data());remember();Ui.toast(this,"Pokušaj sačuvan");finish();}catch(Exception e){Ui.error(this,e);}}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(smartForm!=null)try{out.putString("smart_draft",smartForm.data().toString());}catch(Exception ignored){}out.putString("test",testId);out.putString("meta",metaId);out.putString("before",beforeId);out.putString("after",afterId);out.putString("date",date);if(cost!=null)out.putString("cost",cost.getText().toString());if(duration!=null)out.putString("duration",duration.getText().toString());}
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
