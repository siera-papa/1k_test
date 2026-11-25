package rs.project1k.plan;
import android.app.*;import android.appwidget.*;import android.content.*;import android.widget.*;
public final class AttemptWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){for(int id:ids){RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.attempt_widget);Intent intent=new Intent(c,QuickEntryActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);PendingIntent pi=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);views.setOnClickPendingIntent(R.id.widget_root,pi);manager.updateAppWidget(id,views);}}
}
