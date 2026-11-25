package rs.project1k.plan;

import android.Manifest;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Persisted jobs survive the widget closing; only completed daily copies are rotated. */
final class AutoBackup {
    static final int JOB_ID=100118;
    static final String FOLDER="1K Test";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("auto_backup",Context.MODE_PRIVATE);}
    static String day(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());}
    static boolean needsPermission(Context c){return Build.VERSION.SDK_INT<29&&c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED;}
    static synchronized void request(Context context){
        Context c=context.getApplicationContext();
        android.content.SharedPreferences p=prefs(c);
        if(!p.edit().putLong("revision",p.getLong("revision",0)+1).commit())return;
        schedule(c);
    }
    static synchronized void ensure(Context context){
        Context c=context.getApplicationContext();android.content.SharedPreferences p=prefs(c);
        if(!day().equals(p.getString("last_day",""))||p.getLong("revision",0)!=p.getLong("saved_revision",-1))schedule(c);
    }
    static void schedule(Context c){
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        if(needsPermission(c)){prefs(c).edit().putString("error","Otvori aplikaciju i dozvoli čuvanje fajlova.").apply();return;}
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(c,BackupJob.class))
            .setMinimumLatency(1000).setOverrideDeadline(10000).setPersisted(true)
            .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        try{if(scheduler.schedule(info)!=JobScheduler.RESULT_SUCCESS)prefs(c).edit().putString("error","Automatski backup nije zakazan. Pokušaj ponovo.").apply();}
        catch(RuntimeException error){prefs(c).edit().putString("error","Automatski backup nije zakazan. Pokušaj ponovo.").apply();}
    }
    static boolean pending(Context c){android.content.SharedPreferences p=prefs(c);return !day().equals(p.getString("last_day",""))||p.getLong("revision",0)!=p.getLong("saved_revision",-1);}
    static String status(Context c){
        android.content.SharedPreferences p=prefs(c);long time=p.getLong("last_success",0);
        String text="Downloads/"+FOLDER+"\n\nPoslednji uspešan backup: "+(time==0?"još nije napravljen":new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.ROOT).format(new Date(time)));
        String error=p.getString("error","");
        if(!error.isEmpty())text+="\n\n"+error;
        else if(pending(c))text+="\n\nBackup čeka izvršavanje u pozadini.";
        return text+"\n\nČuva se poslednja potpuna kopija za svaki dan.";
    }
    static void write(Context c)throws Exception{
        android.content.SharedPreferences p=prefs(c);long revision=p.getLong("revision",0);
        byte[] bytes;
        try(PlanStore store=new PlanStore(c)){bytes=store.backup().toString(2).getBytes(StandardCharsets.UTF_8);}
        String day=day(),name="1k-test-"+day+"_"+new SimpleDateFormat("HHmmss-SSS",Locale.ROOT).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)+".json";
        String previous=p.getString("copy_"+day,"");
        String saved=Build.VERSION.SDK_INT>=29?writeModern(c,p,name,bytes):writeLegacy(name,bytes);
        // Never mark a revision completed until publishing and closing the entire file succeeded.
        if(!p.edit().putString("copy_"+day,saved).putString("last_day",day).putLong("last_success",System.currentTimeMillis()).putLong("saved_revision",revision).remove("error").commit())throw new IOException("Backup je upisan, ali status nije sačuvan.");
        if(!previous.isEmpty()&&!previous.equals(saved)){
            try{
                if(Build.VERSION.SDK_INT>=29)c.getContentResolver().delete(Uri.parse(previous),null,null);
                else{File old=new File(previous),folder=legacyFolder();if(folder.equals(old.getParentFile()))old.delete();}
            }catch(Exception ignored){/* The completed new copy remains usable even if old-copy cleanup fails. */}
        }
    }
    static String writeModern(Context c,android.content.SharedPreferences prefs,String name,byte[] bytes)throws Exception{
        ContentResolver resolver=c.getContentResolver();
        String abandoned=prefs.getString("pending_uri","");
        if(!abandoned.isEmpty())try{
            Uri pending=Uri.parse(abandoned);
            try(android.database.Cursor cursor=resolver.query(pending,new String[]{MediaStore.MediaColumns.IS_PENDING},null,null,null)){
                if(cursor!=null&&cursor.moveToFirst()&&cursor.getInt(0)==1)resolver.delete(pending,null,null);
            }
        }catch(Exception ignored){}
        ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/"+FOLDER+"/");values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
        if(uri==null)throw new IOException("Downloads/1K Test nije otvoren.");
        try{
            if(!prefs.edit().putString("pending_uri",uri.toString()).commit())throw new IOException("Backup nije pripremljen.");
            try(OutputStream out=resolver.openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Backup fajl nije otvoren.");out.write(bytes);out.flush();
            }
            ContentValues published=new ContentValues();published.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(resolver.update(uri,published,null,null)!=1)throw new IOException("Backup nije završen.");
            prefs.edit().remove("pending_uri").commit();
            return uri.toString();
        }catch(Exception error){try{resolver.delete(uri,null,null);}catch(Exception ignored){}throw error;}
    }
    static File legacyFolder(){return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),FOLDER);}
    static String writeLegacy(String name,byte[] bytes)throws IOException{
        File folder=legacyFolder();if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Downloads/1K Test nije napravljen.");
        File target=new File(folder,name),temp=new File(folder,"."+name+".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.flush();out.getFD().sync();}
            if(!temp.renameTo(target))throw new IOException("Backup nije završen.");
            return target.getAbsolutePath();
        }finally{if(temp.exists())temp.delete();}
    }
}
