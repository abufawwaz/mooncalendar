/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nilriri.LunaCalendar.widget;

import org.nilriri.LunaCalendar.R;
import org.nilriri.LunaCalendar.dao.ScheduleDaoImpl;
import org.nilriri.LunaCalendar.tools.Prefs;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * A widget provider.  We have a string that we pull from a preference in order to show
 * the configuration settings and the current time when the widget was updated.  We also
 * register a BroadcastReceiver for time-changed and timezone-changed broadcasts, and
 * update then too.
 *
 * <p>See also the following files:
 * <ul>
 *   <li>AppWidgetConfigure.java</li>
 *   <li>WidgetBroadcastReceiver.java</li>
 *   <li>res/layout/appwidget_configure.xml</li>
 *   <li>res/layout/appwidget_provider.xml</li>
 *   <li>res/xml/appwidget_provider.xml</li>
 * </ul>
 */
public class WidgetProvider extends AppWidgetProvider {
    // log tag
    private static final String TAG = "WidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate");
        // For each widget that needs an update, get the text that we should display:
        //   - Create a RemoteViews object for it
        //   - Set the text in the RemoteViews object
        //   - Tell the AppWidgetManager to show that views object for the widget.
        final int N = appWidgetIds.length;
        for (int i = 0; i < N; i++) {
            int appWidgetId = appWidgetIds[i];
            //String titlePrefix = AppWidgetConfigure.loadTitlePref(context, appWidgetId);
            updateAppWidget(context, appWidgetManager, appWidgetId, true);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.d(TAG, "onDeleted");
        // When the user deletes the widget, delete the preference associated with it.
        final int N = appWidgetIds.length;
        for (int i = 0; i < N; i++) {
            AppWidgetConfigure.deleteTitlePref(context, appWidgetIds[i]);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled");
        // When the first widget is created, register for the TIMEZONE_CHANGED and TIME_CHANGED
        // broadcasts.  We don't want to be listening for these if nobody has our widget active.
        // This setting is sticky across reboots, but that doesn't matter, because this will
        // be called after boot if there is a widget instance for this provider.
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName("org.nilriri.LunarCalendar", ".widget.BroadcastReceiver"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onDisabled(Context context) {
        // When the first widget is created, stop listening for the TIMEZONE_CHANGED and
        // TIME_CHANGED broadcasts.
        Log.d(TAG, "onDisabled");
        //Class clazz = WidgetBroadcastReceiver.class;

        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName("org.nilriri.LunarCalendar", ".widget.BroadcastReceiver"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, boolean titlePrefix) {
        Log.d(TAG, "updateAppWidget appWidgetId=" + appWidgetId + " titlePrefix=" + titlePrefix);
        // Getting the string this way allows the string to be localized.  The format
        // string is filled in using java.util.Formatter-style format strings.
        //CharSequence text = context.getString(R.string.appwidget_text_format, AppWidgetConfigure.loadTitlePref(context, appWidgetId), "0x" + Long.toHexString(SystemClock.elapsedRealtime()));

        ScheduleDaoImpl dao = new ScheduleDaoImpl(context, null, Prefs.getSDCardUse(context));

        String mDday_title = "";
        String mDday_msg = "";

        Cursor cursor = dao.queryDDay();
        if (cursor.moveToNext()) {
            String D_dayTitle = cursor.getString(0);
            String D_dayDate = cursor.getString(1);
            int D_Day = cursor.getInt(2);

            if (D_Day == 0) {
                mDday_title += "D day";
            } else if (D_Day > 0) {
                mDday_title += "D+" + D_Day + context.getResources().getString(R.string.day_label) + "";
            } else {
                mDday_title += "D-" + Math.abs(D_Day - 1) + context.getResources().getString(R.string.day_label) + "";
            }

            mDday_msg += D_dayTitle.length() >= 18 ? D_dayTitle.substring(0, 18) + "..." : D_dayTitle;
            mDday_msg += "\n" + D_dayDate;

            mDday_msg = mDday_msg == null ? "" : mDday_msg;
        } else {
            mDday_msg = "";
        }
        cursor.close();

        if ("".equals(mDday_msg))
            Toast.makeText(context, "D-day Empty...", Toast.LENGTH_LONG).show();

        // Construct the RemoteViews object.  It takes the package name (in our case, it's our
        // package, but it needs this because on the other side it's the widget host inflating
        // the layout from our package).
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget_provider);
        views.setTextViewText(R.id.appwidget_title, mDday_title);
        views.setTextViewText(R.id.appwidget_text, mDday_msg);

        // Tell the widget manager
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

}