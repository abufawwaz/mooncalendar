package org.nilriri.LunaCalendar.tools;

import org.nilriri.LunaCalendar.dao.ScheduleDaoImpl;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class DataManager {
    public static ScheduleDaoImpl daoSource;
    public static ScheduleDaoImpl daoTarget;
    public static ProgressDialog pd;
    public static String Error = "";

    public static Context mContext;

    public static void StartCopy(Context context, boolean work) {
        mContext = context;

        daoSource = new ScheduleDaoImpl(mContext, null, !work);
        daoTarget = new ScheduleDaoImpl(mContext, null, work);

        pd = new ProgressDialog(mContext);

        pd.setTitle("Move!");
        pd.setMessage("Move to external storage...");

        //pd.setCancelable(true);
        //pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        //pd.setIndeterminate(true);
        pd.show();

        Thread thread = new Thread(new Runnable() {

            public void run() {

                try {

                    Cursor cursor = daoSource.queryAll();
                    if (cursor.getCount() > 0) {
                        if (!daoTarget.copy(cursor))
                            Error = "������ġ ���� ����!";
                    }

                    cursor.close();
                    daoSource.close();
                    daoTarget.close();

                    handler.sendEmptyMessage(0);

                } catch (Exception e) {

                    Error = e.getMessage();

                }

            }

        });

        thread.start();

    }

    public static void StartRestore(Context context) {
        mContext = context;

        daoTarget = new ScheduleDaoImpl(mContext, null, Prefs.getSDCardUse(context));

        pd = new ProgressDialog(mContext);

        pd.setTitle("Restore!");
        pd.setMessage("Restore from backup file...");

        //pd.setCancelable(true);
        //pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        //pd.setIndeterminate(true);
        pd.show();

        Thread thread = new Thread(new Runnable() {

            public void run() {

                try {

                    if (!daoTarget.importdata()) {
                        Error = "���� ����!";
                    } else {
                        Error = "";
                    }
                    daoTarget.close();

                    handler.sendEmptyMessage(0);

                } catch (Exception e) {

                    Error = e.getMessage();

                }

            }

        });

        thread.start();
    }

    public static void StartBackup(Context context) {
        mContext = context;

        daoSource = new ScheduleDaoImpl(mContext, null, Prefs.getSDCardUse(context));

        pd = new ProgressDialog(mContext);

        pd.setTitle("Backup!");
        pd.setMessage("Backup schedule data...");

        //pd.setCancelable(true);
        //pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        //pd.setIndeterminate(true);
        pd.show();

        Thread thread = new Thread(new Runnable() {

            public void run() {

                try {

                    Cursor cursor = daoSource.queryAll();

                    if (!daoSource.exportdata(cursor)) {
                        Error = "����� �����Ͽ����ϴ�.";
                    } else {
                        Error = "";
                    }

                    cursor.close();
                    daoSource.close();

                    handler.sendEmptyMessage(0);

                } catch (Exception e) {

                    Error = e.getMessage();

                }

            }

        });

        thread.start();
    }

    public static Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            pd.dismiss();

            if ("".equals(Error)) {
                Toast.makeText(mContext, "���/���� �۾��� �Ϸ� �Ǿ����ϴ�.", Toast.LENGTH_LONG).show();
            } else {
                Log.e("Copy", "Error = " + Error);
                Toast.makeText(mContext, Error, Toast.LENGTH_LONG).show();
            }

        }

    };

}