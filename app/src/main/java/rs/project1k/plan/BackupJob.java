package rs.project1k.plan;

import android.app.job.*;
import android.util.Log;
import java.util.concurrent.*;

public final class BackupJob extends JobService {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicInteger generation=new java.util.concurrent.atomic.AtomicInteger();
    @Override public boolean onStartJob(JobParameters parameters){
        int token=generation.incrementAndGet();
        WORKER.execute(()->{
            boolean retry=false;
            try{AutoBackup.write(getApplicationContext());retry=AutoBackup.pending(getApplicationContext());}
            catch(Exception e){
                retry=true;Log.e("1KTestBackup","Automatic backup failed",e);
                AutoBackup.prefs(this).edit().putString("error","Automatski backup nije uspeo: "+(e.getMessage()==null?"greška pri upisu":e.getMessage())).commit();
            }
            if(generation.get()==token)jobFinished(parameters,retry);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){generation.incrementAndGet();return true;}
}
