package org.nilriri.LunaCalendar.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.nilriri.LunaCalendar.R;
import org.nilriri.LunaCalendar.RefreshManager;
import org.nilriri.LunaCalendar.dao.Constants.Schedule;
import org.nilriri.LunaCalendar.gcal.EventEntry;
import org.nilriri.LunaCalendar.gcal.GoogleUtil;
import org.nilriri.LunaCalendar.tools.Common;
import org.nilriri.LunaCalendar.tools.Prefs;
import org.nilriri.LunaCalendar.tools.WhereClause;
import org.nilriri.LunaCalendar.tools.lunar2solar;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.os.AsyncTask;
import android.util.Log;

public class ScheduleDaoImpl extends AbstractDao {

    private SQLiteDatabase db;
    private Context mContext;
    private ScheduleBean oldBean;

    protected RefreshManager refreshManager;

    public ScheduleDaoImpl(Context context, CursorFactory factory, boolean sdcarduse) {
        super(context, factory, sdcarduse);

        mContext = context;

        db = getWritableDatabase();
    }

    private void CallerRefresh() {
        try {
            if (refreshManager != null) {
                refreshManager.refresh();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Sync Task
     */
    public void syncInsert(Long id, RefreshManager caller) {
        this.refreshManager = caller;
        if ("auto".equals(Prefs.getSyncMethod(this.mContext)) // ����ȭ ��� 
                && !"".equals(Prefs.getSyncCalendar(mContext))) {
            oldBean = new ScheduleBean();
            oldBean.setId(id);

            new googleInsert().execute();
        }
    }

    public void syncInsert(ScheduleBean scheduleBean, RefreshManager caller) {
        this.refreshManager = caller;
        if ("auto".equals(Prefs.getSyncMethod(this.mContext)) // ����ȭ ��� 
                && !"".equals(Prefs.getSyncCalendar(mContext))) {
            oldBean = scheduleBean;

            new googleInsert().execute();
        } else {
            localInsert(scheduleBean);
            CallerRefresh();
        }

    }

    public void syncUpdate(ScheduleBean scheduleBean, RefreshManager caller) {
        this.refreshManager = caller;
        if ("auto".equals(Prefs.getSyncMethod(this.mContext)) // ����ȭ ��� 
                && !"".equals(Prefs.getSyncCalendar(mContext))) {
            oldBean = scheduleBean;

            new googleUpdate().execute();

        } else {
            localUpdate(scheduleBean);
            CallerRefresh();
        }

    }

    public void syncDelete(Long deleteId, RefreshManager caller) {
        this.refreshManager = caller;
        if ("auto".equals(Prefs.getSyncMethod(mContext)) // ����ȭ ��� 
                && !"".equals(Prefs.getSyncCalendar(mContext))) {

            // EventEntry event = new EventEntry(query(deleteId));

            oldBean = new ScheduleBean(query(deleteId));

            new googleDelete().execute();
        } else {
            localDelete(deleteId);
            CallerRefresh();
        }
    }

    public void syncImport(RefreshManager caller) {
        this.refreshManager = caller;
        new googleImport().execute();
    }

    public void doImport(List<EventEntry> events) {

        Log.d("doSync", "event count=" + events.size());
        for (int i = 0; i < events.size(); i++) {
            try {
                ScheduleBean scheduleBean = new ScheduleBean(events.get(i));
                doImport(scheduleBean);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void doImport(ScheduleBean scheduleBean) {

        WhereClause whereClause = new WhereClause();

        whereClause.put(Schedule.GID, scheduleBean.getGID());
        whereClause.put(Schedule.ETAG, scheduleBean.getEtag());

        String sql = "SELECT _id FROM " + Schedule.SCHEDULE_TABLE_NAME + " WHERE " + whereClause.getClause();

        Log.d("doSync-insert", "exists check sql=" + sql);

        Cursor c = getReadableDatabase().rawQuery(sql, null);

        int updateCnt = 0;
        if (c.moveToNext()) {
            // uid�� etag�� ������ �ڷᰡ �����ϸ�...
            Log.d("doSync-check", "exists local ID=" + c.getLong(Schedule.COL_ID));

            scheduleBean.setId(c.getLong(Schedule.COL_ID));

            c.close();

            // ���� Ķ������ ���� ������ �ڷ�� local �����͸� �����Ѵ�.
            updateCnt = localUpdate(scheduleBean);

            // ������Ʈ�� ���� �ڷᰡ ������ ...
            // ��ũ������ ���ÿ��� ������ �ڷ��� ���..
            if (updateCnt <= 0) {

                try {
                    // local data�� ���� �Ǿ�����...
                    // googleĶ���� �����͸� ���õ����Ϳ� ����ȭ �Ѵ�. 
                    GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));

                    // ���� Ķ�����ڷ�� ���� ���� ��������. 
                    String googleUpdated = scheduleBean.getUpdated();

                    // ���� �������� ��������.
                    scheduleBean = new ScheduleBean(query(scheduleBean.getId()));

                    // ����Ķ������ ������Ʈ �ð��� ���� ������Ʈ �ð��� ������ ������ ������ ����.
                    if (!googleUpdated.equals(scheduleBean.getUpdated())) {
                        gu.updateEvent(scheduleBean);
                        //gu.insertEvent(Prefs.getSyncCalendar(mContext), scheduleBean, Prefs.getAccountName(mContext));
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        } else {
            Log.d("doSync-check", "not Exists ");
            c.close();

            // etag�� uid�� ������ �ڷᰡ 
            // local db�� ���� �����̹Ƿ� �űԵ���Ѵ�.
            localInsert(scheduleBean);
        }

    }

    public void batchMakeCalendar(RefreshManager caller) {
        this.refreshManager = caller;
        new googleMakeCalendar().execute();
        //Bible Reading Plan����.
        //new googleMakeBiblePlan().execute();

    }

    /*
     * AsyncTask     
     */

    private class googleInsert extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        EventEntry event;

        @Override
        protected void onPreExecute() {

            if ("".equals(oldBean.getSchedule_title())) {
                dialog = ProgressDialog.show(mContext, "", "������ ���� �� �߰��ϰ� �ֽ��ϴ�...", true);
            } else {
                oldBean.setId(localInsert(oldBean));
            }

            Log.e(Common.TAG, "****** onPreExecute ********");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e(Common.TAG, "****** doInBackground ********");

            try {
                GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));

                if ("".equals(oldBean.getSchedule_title())) {
                    oldBean = new ScheduleBean(query(oldBean.getId()));

                    // ������ ����Ķ������ �����ϴ� ������ �ٽ� �߰��ϸ� �����Ѵ�.
                    if (!"".equals(oldBean.getSelfurl())) {
                        oldBean.setId(localInsert(oldBean));
                        oldBean.setEditurl(null);
                        oldBean.setSelfUrl(null);
                        oldBean.setEtag(null);
                        oldBean.setGID(null);
                    }
                }

                event = gu.insertEvent(Prefs.getSyncCalendar(mContext), oldBean, Prefs.getAccountName(mContext));

                if (!"".equals(event.title)) {
                    ScheduleBean eventBean = new ScheduleBean(event);
                    // eventBean.setId(oldBean.getId());

                    // ����� ������ ���� Ķ������ �ݿ��� �Ŀ� ��������� ���ÿ� �ٽ� �ݿ��Ѵ�.
                    oldBean.setTitle(eventBean.getSchedule_title());
                    oldBean.setDate(eventBean.getSchedule_date());
                    oldBean.setContents(eventBean.getSchedule_contents());
                    oldBean.setGID(eventBean.getGID());
                    oldBean.setEtag(eventBean.getEtag());
                    oldBean.setPublished(eventBean.getPublished());
                    oldBean.setUpdated(eventBean.getUpdated());
                    oldBean.setWhen(eventBean.getWhen());
                    oldBean.setWho(eventBean.getWho());
                    oldBean.setRecurrence(eventBean.getRecurrence());
                    oldBean.setSelfUrl(eventBean.getSelfurl());
                    oldBean.setEditurl(eventBean.getEditurl());
                    oldBean.setOriginalevent(eventBean.getOriginalevent());
                    oldBean.setEventstatus(eventBean.getEventstatus());

                    localUpdate(oldBean);
                }

            } catch (IOException e) {
                cancel(true);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            CallerRefresh();
            if (dialog != null && dialog.isShowing())
                dialog.dismiss();
            Log.e(Common.TAG, "****** onPostExecute ********");
        }

    }

    private class googleUpdate extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        EventEntry event;

        @Override
        protected void onPreExecute() {
            // ��������� ���� �����Ѵ�.
            localUpdate(oldBean);

            //dialog = ProgressDialog.show(this, "", "Add event...", true);
            Log.e(Common.TAG, "****** onPreExecute ********");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e(Common.TAG, "****** doInBackground ********");

            try {
                if ("".equals(oldBean.getEtag())) {
                    cancel(true);
                }

                GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));
                event = gu.updateEvent(oldBean);

                if (!"".equals(event.getEditLink())) {
                    ScheduleBean eventBean = new ScheduleBean(event);

                    // ����� ������ ���� Ķ������ �ݿ��� �Ŀ� ��������� ���ÿ� �ٽ� �ݿ��Ѵ�.
                    oldBean.setTitle(eventBean.getSchedule_title());
                    oldBean.setDate(eventBean.getSchedule_date());
                    oldBean.setContents(eventBean.getSchedule_contents());
                    oldBean.setGID(eventBean.getGID());
                    oldBean.setEtag(eventBean.getEtag());
                    oldBean.setPublished(eventBean.getPublished());
                    oldBean.setUpdated(eventBean.getUpdated());
                    oldBean.setWhen(eventBean.getWhen());
                    oldBean.setWho(eventBean.getWho());
                    oldBean.setRecurrence(eventBean.getRecurrence());
                    oldBean.setSelfUrl(eventBean.getSelfurl());
                    oldBean.setEditurl(eventBean.getEditurl());
                    oldBean.setOriginalevent(eventBean.getOriginalevent());
                    oldBean.setEventstatus(eventBean.getEventstatus());

                    localUpdate(oldBean);
                }

            } catch (IOException e) {
                cancel(true);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            CallerRefresh();

            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            Log.e(Common.TAG, "****** onPostExecute ********");
        }

    }

    private class googleDelete extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            //dialog = ProgressDialog.show(mContext, "", "Delete event from google...", true);

            localDelete(oldBean.getId());

            Log.e(Common.TAG, "****** onPreExecute ********");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e(Common.TAG, "****** doInBackground ********");
            try {

                if ("".equals(oldBean.getEtag())) {
                    cancel(true);
                }

                GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));
                gu.deleteEvent(oldBean);
            } catch (IOException e) {
                cancel(true);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            CallerRefresh();
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            Log.e(Common.TAG, "****** onPostExecute ********");
        }

    }

    private class googleImport extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        List<EventEntry> events;

        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(mContext, "", "����Ķ�������� ������ �������� �ֽ��ϴ�...", true);

            Log.e(Common.TAG, "****** onPreExecute ********");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e(Common.TAG, "****** doInBackground ********");

            try {
                GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));
                String url = Prefs.getSyncCalendar(mContext);
                events = gu.getEvents(url);

            } catch (IOException e) {
                cancel(true);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            doImport(events);

            CallerRefresh();

            dialog.dismiss();

            Log.e(Common.TAG, "****** onPostExecute ********");
        }

    }

    private class googleMakeCalendar extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            //dialog = ProgressDialog.show(mContext, "��������!", "����Ķ������ ���������� �����ϰ� �ֽ��ϴ�...", true);

            dialog = new ProgressDialog(mContext);

            dialog.setTitle("��������!");
            dialog.setMessage("����Ķ������ ���������� �����ϰ� �ֽ��ϴ�...");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(2100);
            dialog.show();

            Log.e(Common.TAG, "****** onPreExecute ********");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e(Common.TAG, "****** doInBackground ********");

            try {
                GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));
                String url = Prefs.getSyncCalendar(mContext);
                gu.batchAddEvents(url, dialog);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                cancel(true);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            CallerRefresh();

            dialog.dismiss();

            Log.e(Common.TAG, "****** onPostExecute ********");
        }

    }

    private class googleMakeBiblePlan extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            //dialog = ProgressDialog.show(mContext, "��������!", "����Ķ������ ���������� �����ϰ� �ֽ��ϴ�...", true);

            dialog = new ProgressDialog(mContext);
            dialog.setTitle("��������!");
            dialog.setMessage("����Ķ������ ��ü�μ����б� ������ �����ϰ� �ֽ��ϴ�...");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(true);
            dialog.setMax(366);
            dialog.show();

            Log.e(Common.TAG, "****** onPreExecute ********");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e(Common.TAG, "****** doInBackground ********");

            try {

                String[] PlanList = mContext.getResources().getStringArray(R.array.array_bibleplan);

                GoogleUtil gu = new GoogleUtil(Prefs.getAuthToken(mContext));
                String url = Prefs.getSyncCalendar(mContext);
                gu.batchBiblePlan(url, dialog, PlanList);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                cancel(true);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            CallerRefresh();

            dialog.dismiss();

            Log.e(Common.TAG, "****** onPostExecute ********");
        }

    }

    /*
     * Local Task
     */
    public Long localInsert(ScheduleBean scheduleBean) {

        Long newId;

        db.beginTransaction();
        if (1 == scheduleBean.getDday_displayyn()) {
            // ������ ��ܿ� ǥ���ϵ��� �Ǿ��ִ� D-day������ ���ǥ�÷� �����Ѵ�.
            db.execSQL("update schedule set dday_displayyn = 0 where dday_displayyn = 1");
        }

        newId = db.insert(Schedule.SCHEDULE_TABLE_NAME, null, new ScheduleContentValues(scheduleBean).value);

        db.setTransactionSuccessful();
        db.endTransaction();

        Log.d("DaoImpl-insert", "succ.");

        return newId;

    }

    public int localUpdate(ScheduleBean scheduleBean) {

        WhereClause whereClause = new WhereClause(true);

        //whereClause.addParam(Schedule._ID);
        whereClause.put(Schedule._ID, scheduleBean.getId());
        whereClause.put(Schedule.UPDATED, scheduleBean.getUpdated(), "<=");

        db.beginTransaction();
        if (1 == scheduleBean.getDday_displayyn()) {
            // ������ ��ܿ� ǥ���ϵ��� �Ǿ��ִ� D-day������ ���ǥ�÷� �����Ѵ�.
            db.execSQL("update schedule set dday_displayyn = 0 where dday_displayyn = 1");
        }

        int result = db.update(Schedule.SCHEDULE_TABLE_NAME, new ScheduleContentValues(scheduleBean).value, whereClause.getClause(), whereClause.getParam());

        db.setTransactionSuccessful();
        db.endTransaction();

        return result;

    }

    public void localDelete(Long id) {
        String sql = "DELETE FROM " + Schedule.SCHEDULE_TABLE_NAME + " WHERE " + Schedule._ID + "=" + id;
        Log.e(Common.TAG, "DELETE SQL=" + sql);
        getWritableDatabase().execSQL(sql);
    }

    public void deleteAll() {
        String sql = "DELETE FROM " + Schedule.SCHEDULE_TABLE_NAME;
        getWritableDatabase().execSQL(sql);
    }

    /*
     * Query
     */
    public Cursor query(Long id) {

        StringBuilder query;
        query = new StringBuilder();

        query.append("SELECT " + Schedule._ID);
        query.append("    ," + Schedule.SCHEDULE_DATE);
        query.append("    ," + Schedule.SCHEDULE_TITLE);
        query.append("    ,case when " + Schedule.SCHEDULE_REPEAT + " = 9 then " + Schedule.ALARM_DATE);
        query.append("    else " + Schedule.SCHEDULE_CONTENTS + " end " + Schedule.SCHEDULE_CONTENTS);
        query.append("    ," + Schedule.SCHEDULE_REPEAT);
        query.append("    ," + Schedule.SCHEDULE_CHECK);
        query.append("    ," + Schedule.ALARM_LUNASOLAR);
        query.append("    ," + Schedule.ALARM_DATE);
        query.append("    ," + Schedule.ALARM_TIME);
        query.append("    ," + Schedule.ALARM_DAYS);
        query.append("    ," + Schedule.ALARM_DAY);
        query.append("    ," + Schedule.DDAY_ALARMYN);
        query.append("    ," + Schedule.DDAY_ALARMDAY);
        query.append("    ," + Schedule.DDAY_ALARMSIGN);
        query.append("    ," + Schedule.DDAY_DISPLAYYN);
        query.append("    ," + Schedule.GID);
        query.append("    ," + Schedule.ANNIVERSARY);
        query.append("    ," + Schedule.LUNARYN);
        query.append("    ," + Schedule.SCHEDULE_LDATE);
        query.append("    ," + Schedule.ALARM_DETAILINFO);
        query.append("    ," + Schedule.DDAY_DETAILINFO);
        query.append("    ," + Schedule.SCHEDULE_TYPE);
        query.append("    ," + Schedule.BIBLE_BOOK);
        query.append("    ," + Schedule.BIBLE_CHAPTER);
        query.append("    ," + Schedule.ETAG);
        query.append("    ," + Schedule.PUBLISHED);
        query.append("    ," + Schedule.UPDATED);
        query.append("    ," + Schedule.WHEN);
        query.append("    ," + Schedule.WHO);
        query.append("    ," + Schedule.RECURRENCE);
        query.append("    ," + Schedule.SELFURL);
        query.append("    ," + Schedule.EDITURL);
        query.append("    ," + Schedule.ORIGINALEVENT);
        query.append("    ," + Schedule.EVENTSTATUS);
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME);
        query.append(" WHERE 1 = 1 ");
        query.append(" AND " + Schedule._ID + " = " + id.toString());

        Log.d("DaoImpl-query", query.toString());

        return getReadableDatabase().rawQuery(query.toString(), null);

    }

    public Cursor query(String date, String lDay) {

        String sday = date.substring(5);
        String lday = lDay.substring(4, 6) + "-" + lDay.substring(6);

        StringBuffer query = new StringBuffer();

        //���, ���� �Ϲ� ����
        query.append("SELECT   ");
        query.append("    _id ");
        query.append("    ,'Schedule' schedule_type ");
        query.append("    ,schedule_title ");
        query.append("    ,6 kind ");
        query.append("    ,0 bible_book ");
        query.append("    ,0 bible_chapter ");
        query.append("FROM  schedule ");
        query.append("WHERE schedule_date = ? and lunaryn <> 'Y' and  anniversary <> 'Y' ");
        query.append(" OR ( schedule_ldate = '" + Common.fmtDate(lDay) + "' and lunaryn = 'Y' and anniversary <> 'Y' )");
        /*
         query.append("union all  ");
         query.append("SELECT   ");
         query.append("    _id ");
         query.append("    ,'D-day' schedule_type ");
         query.append("    ,substr(schedule_title, 1, 15) ");
         query.append("    ||'('|| ");
         query.append("case when cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) > 0  ");
         query.append("then 'D + ' || cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer)  ");
         query.append(" when cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) = 0  ");
         query.append("then 'D day' else 'D ' ||  cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) end ");
         query.append("    ||')' schedule_title ");
         query.append("FROM  schedule ");
         query.append("WHERE 1=1 ");
         query.append("and dday_displayyn = 2  ");
        */

        //����� ��±����, ���±����
        query.append("union all  ");
        query.append("select _id, 'Anniversary' schedule_type, schedule_title ");
        query.append("    ,3 kind ");
        query.append("    ,0 bible_book ");
        query.append("    ,0 bible_chapter ");
        query.append("from schedule ");
        query.append("where 1=1 ");
        query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date + "', 'LOCALTIME')||'-12-31' ");
        query.append(" and  schedule_date like '%" + date.substring(5) + "' and lunaryn <> 'Y' and  anniversary = 'Y' ");
        query.append(" OR ( schedule_date <= STRFTIME('%Y', '" + date + "', 'LOCALTIME')||'-12-31' ");
        query.append(" and schedule_ldate like '%" + Common.fmtDate(lDay).substring(5) + "' and lunaryn = 'Y' and anniversary = 'Y' )");

        // system �����
        query.append("union all  ");
        query.append("select _id, 'Anniversary' schedule_type, schedule_title ");
        query.append("    ,alarm_day kind "); //������� Ȥ�� ������.
        query.append("    ,0 bible_book ");
        query.append("    ,0 bible_chapter ");
        query.append("from schedule ");
        query.append("where schedule_repeat = 9 ");
        query.append("and schedule_date = '1900-01-01' ");
        query.append("and (   (alarm_lunasolar = 0 and alarm_date = '" + sday + "') ");
        query.append("     or (alarm_lunasolar = 1 and alarm_date = '" + lday + "') ) ");
        query.append("and alarm_time = '00:00' ");

        /*
                if (Prefs.getBplan(mContext) && (Prefs.getBplanFamily(mContext) || Prefs.getBplanPersonal(mContext))) {
                    // �����б� �÷�
                    query.append("union all  ");
                    query.append("select _id, 'B-Plan' schedule_type, schedule_title ");
                    query.append("    ,alarm_day kind "); //������� Ȥ�� ������.
                    query.append("    ,bible_book ");
                    query.append("    ,bible_chapter ");
                    query.append("from schedule ");
                    query.append("where 1 = 1 ");
                    if (Prefs.getBplanFamily(mContext) && Prefs.getBplanPersonal(mContext))
                        query.append(" and schedule_repeat in ('F','P') ");
                    else if (Prefs.getBplanFamily(mContext))
                        query.append(" and schedule_repeat = 'F' ");
                    else if (Prefs.getBplanPersonal(mContext))
                        query.append(" and schedule_repeat = 'P' ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and (   (alarm_lunasolar = 0 and alarm_date = '" + sday + "') ");
                    query.append("     or (alarm_lunasolar = 1 and alarm_date = '" + lday + "') ) ");
                    query.append("and alarm_time = '00:00' ");
                }
        */
        //d-day
        query.append("union all  ");
        query.append("SELECT   ");
        query.append("    _id ");
        query.append("    ,'D-day' schedule_type ");
        query.append("    ,substr(schedule_title, 1, 15) ");
        query.append("    ||'('|| ");
        query.append("case when cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) > 0  ");
        query.append("then 'D + ' || cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer)  ");
        query.append(" when cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) = 0  ");
        query.append("then 'D day' else 'D ' ||  cast(JULIANDAY(?, 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) end ");
        query.append("    ||')' schedule_title ");
        query.append("    ,5 kind ");
        query.append("    ,0 bible_book ");
        query.append("    ,0 bible_chapter ");
        query.append("FROM  schedule ");
        query.append("WHERE 1=1 ");
        query.append("and dday_alarmyn = 1  ");
        query.append("and dday_displayyn = 2  ");
        query.append("or (dday_alarmyn = 1  ");
        query.append("and dday_displayyn in (0, 1)  ");
        query.append("and strftime('%Y-%m-%d', DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'localtime') = ? ) ");

        query.append("ORDER BY 1 ");

        String selectionArgs[] = new String[] { date, date, date, date, date, date, };

        Log.d("DaoImpl-query", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    public Cursor queryAll() {

        StringBuffer query = new StringBuffer("SELECT " + Schedule._ID);
        query.append("   ," + Schedule.SCHEDULE_DATE);
        query.append("   ," + Schedule.SCHEDULE_TITLE);
        query.append("   ," + Schedule.SCHEDULE_CONTENTS);
        query.append("   ," + Schedule.SCHEDULE_REPEAT);
        query.append("   ," + Schedule.SCHEDULE_CHECK);
        query.append("   ," + Schedule.ALARM_LUNASOLAR);
        query.append("   ," + Schedule.ALARM_DATE);
        query.append("   ," + Schedule.ALARM_TIME);
        query.append("   ," + Schedule.ALARM_DAYS);
        query.append("   ," + Schedule.ALARM_DAY);
        query.append("   ," + Schedule.DDAY_ALARMYN);
        query.append("   ," + Schedule.DDAY_ALARMDAY);
        query.append("   ," + Schedule.DDAY_ALARMSIGN);
        query.append("   ," + Schedule.DDAY_DISPLAYYN);
        query.append("   ," + Schedule.GID);
        query.append("   ," + Schedule.ANNIVERSARY);
        query.append("   ," + Schedule.LUNARYN);
        query.append("   ," + Schedule.SCHEDULE_LDATE);
        query.append("   ," + Schedule.ALARM_DETAILINFO);
        query.append("   ," + Schedule.DDAY_DETAILINFO);
        query.append("   ," + Schedule.SCHEDULE_TYPE);
        query.append("   ," + Schedule.BIBLE_BOOK);
        query.append("   ," + Schedule.BIBLE_CHAPTER);
        query.append("   ," + Schedule.ETAG);
        query.append("   ," + Schedule.PUBLISHED);
        query.append("   ," + Schedule.UPDATED);
        query.append("   ," + Schedule.WHEN);
        query.append("   ," + Schedule.WHO);
        query.append("   ," + Schedule.RECURRENCE);
        query.append("   ," + Schedule.SELFURL);
        query.append("   ," + Schedule.EDITURL);
        query.append("   ," + Schedule.ORIGINALEVENT);
        query.append("   ," + Schedule.EVENTSTATUS);
        query.append(" FROM  schedule ");
        query.append(" WHERE schedule_date > '1900-01-01' ");
        query.append("   AND schedule_repeat not in ('F','P', '9') ");
        /*
                if (Prefs.getBplan(mContext) && (Prefs.getBplanFamily(mContext) || Prefs.getBplanPersonal(mContext))) {
                    query.append("  AND schedule_repeat not in ( '9') ");
                } else {

                    query.append("  AND schedule_repeat not in ('F','P', '9') ");
                }
        */

        Log.d(Common.TAG, "Query All=" + query.toString());
        return getReadableDatabase().rawQuery(query.toString(), null);

    }

    /*
     * Data Manger Query
     */
    public boolean export(Cursor cursor) {

        String path = "/sdcard/";
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.SUNDAY);
        //String datetime = Common.fmtDateTime(c);
        File file = new File(path + "lunarcalendar.auto.bak");
        try {
            FileOutputStream fos = new FileOutputStream(file);

            StringBuffer buf = new StringBuffer();

            buf.append("\t" + Schedule.SCHEDULE_DATE);
            buf.append("\t" + Schedule.SCHEDULE_TITLE);
            buf.append("\t" + Schedule.SCHEDULE_CONTENTS);
            buf.append("\t" + Schedule.SCHEDULE_REPEAT);
            buf.append("\t" + Schedule.SCHEDULE_CHECK);
            buf.append("\t" + Schedule.ALARM_LUNASOLAR);
            buf.append("\t" + Schedule.ALARM_DATE);
            buf.append("\t" + Schedule.ALARM_TIME);
            buf.append("\t" + Schedule.ALARM_DAYS);
            buf.append("\t" + Schedule.ALARM_DAY);
            buf.append("\t" + Schedule.DDAY_ALARMYN);
            buf.append("\t" + Schedule.DDAY_ALARMDAY);
            buf.append("\t" + Schedule.DDAY_ALARMSIGN);
            buf.append("\t" + Schedule.DDAY_DISPLAYYN);
            buf.append("\t" + Schedule.GID);
            buf.append("\t" + Schedule.ANNIVERSARY);
            buf.append("\t" + Schedule.LUNARYN);
            buf.append("\t" + Schedule.SCHEDULE_LDATE);
            buf.append("\t" + Schedule.ALARM_DETAILINFO);
            buf.append("\t" + Schedule.DDAY_DETAILINFO);
            buf.append("\t" + Schedule.SCHEDULE_TYPE);
            buf.append("\t" + Schedule.BIBLE_BOOK);
            buf.append("\t" + Schedule.BIBLE_CHAPTER);
            buf.append("\t" + Schedule.ETAG);
            buf.append("\t" + Schedule.PUBLISHED);
            buf.append("\t" + Schedule.UPDATED);
            buf.append("\t" + Schedule.WHEN);
            buf.append("\t" + Schedule.WHO);
            buf.append("\t" + Schedule.RECURRENCE);
            buf.append("\t" + Schedule.SELFURL);
            buf.append("\t" + Schedule.EDITURL);
            buf.append("\t" + Schedule.ORIGINALEVENT);
            buf.append("\t" + Schedule.EVENTSTATUS);
            buf.append("\n");

            while (cursor.moveToNext()) {

                for (int col = 0; col < cursor.getColumnCount(); col++) {
                    buf.append(cursor.getString(col)).append("||");
                }
                buf.append("\n");

            }

            fos.write(buf.toString().getBytes());

            fos.close();

            return true;
        } catch (IOException e) {
            Log.i("Export", e.getMessage());
            return false;
        }
    }

    public boolean exportdata(Cursor cursor, ProgressDialog pd) {

        String path = android.os.Environment.getExternalStorageDirectory().toString() + "/";

        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.SUNDAY);
        String datetime = Common.fmtDateTime(c);
        File file = new File(path + "lunarcalendar.backup");

        pd.setMax(cursor.getCount() + 10);

        if (file.exists()) {
            Log.d(Common.TAG, "########    exists    #######");

            //file.delete();
            if (file.renameTo(new File(path + "lunarcalendar.backup." + datetime))) {
                Log.d(Common.TAG, "########    rename succ    #######");
                file = new File(path + "lunarcalendar.backup");
            } else {
                Log.d(Common.TAG, "########    rename fail    #######");
                file = new File(path + "lunarcalendar." + datetime + ".backup");

                if (file.exists()) {
                    Log.d(Common.TAG, "########    reopen    #######");
                    Log.i("Export", "File Exists..." + file.getPath() + file.getName());
                    return false;
                }
            }
        }

        Log.d(Common.TAG, "########    file job end.    #######");

        pd.setProgress(10);

        try {
            FileOutputStream fos = new FileOutputStream(file);

            StringBuffer buf = new StringBuffer(Schedule._ID);
            buf.append("\t" + Schedule.SCHEDULE_DATE);
            buf.append("\t" + Schedule.SCHEDULE_TITLE);
            buf.append("\t" + Schedule.SCHEDULE_CONTENTS);
            buf.append("\t" + Schedule.SCHEDULE_REPEAT);
            buf.append("\t" + Schedule.SCHEDULE_CHECK);
            buf.append("\t" + Schedule.ALARM_LUNASOLAR);
            buf.append("\t" + Schedule.ALARM_DATE);
            buf.append("\t" + Schedule.ALARM_TIME);
            buf.append("\t" + Schedule.ALARM_DAYS);
            buf.append("\t" + Schedule.ALARM_DAY);
            buf.append("\t" + Schedule.DDAY_ALARMYN);
            buf.append("\t" + Schedule.DDAY_ALARMDAY);
            buf.append("\t" + Schedule.DDAY_ALARMSIGN);
            buf.append("\t" + Schedule.DDAY_DISPLAYYN);
            buf.append("\t" + Schedule.GID);
            buf.append("\t" + Schedule.ANNIVERSARY);
            buf.append("\t" + Schedule.LUNARYN);
            buf.append("\t" + Schedule.SCHEDULE_LDATE);
            buf.append("\t" + Schedule.ALARM_DETAILINFO);
            buf.append("\t" + Schedule.DDAY_DETAILINFO);
            buf.append("\t" + Schedule.SCHEDULE_TYPE);
            buf.append("\t" + Schedule.BIBLE_BOOK);
            buf.append("\t" + Schedule.BIBLE_CHAPTER);
            buf.append("\t" + Schedule.ETAG);
            buf.append("\t" + Schedule.PUBLISHED);
            buf.append("\t" + Schedule.UPDATED);
            buf.append("\t" + Schedule.WHEN);
            buf.append("\t" + Schedule.WHO);
            buf.append("\t" + Schedule.RECURRENCE);
            buf.append("\t" + Schedule.SELFURL);
            buf.append("\t" + Schedule.EDITURL);
            buf.append("\t" + Schedule.ORIGINALEVENT);
            buf.append("\t" + Schedule.EVENTSTATUS);
            buf.append("\n\r");

            while (cursor.moveToNext()) {

                Log.d(Common.TAG, "########    move next    #######");

                pd.setProgress(cursor.getPosition() + 10);

                for (int col = 0; col < cursor.getColumnCount(); col++) {
                    Log.d(Common.TAG, "########    col=" + col + "    #######");
                    buf.append(cursor.getString(col)).append("\t");
                }
                buf.append("\n\r");
            }

            Log.d(Common.TAG, "########    write start    #######");
            fos.write(buf.toString().getBytes());

            Log.d(Common.TAG, "########    write end    #######");

            fos.close();

            Log.d(Common.TAG, "########    fos close    #######");

            return true;
        } catch (Exception e) {

            Log.i("Export", e.getMessage());
            return false;
        }
    }

    public boolean importdata() {

        String path = android.os.Environment.getExternalStorageDirectory().toString() + "/";
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.SUNDAY);
        //String datetime = Common.fmtDateTime(c);
        File file = new File(path + "lunarcalendar.backup");

        if (!file.exists()) {
            return true;
        }

        // db = getWritableDatabase();
        try {

            //DataInputStream fis = new DataInputStream(new FileInputStream(file));

            String[] CheckData = null;
            FileInputStream fis = new FileInputStream(file);
            int size = fis.available();
            byte[] buf = new byte[size];

            fis.read(buf);
            String filedata = new String(buf);
            fis.close();

            Log.d("DaoImpl-import", "filedata=" + filedata);

            CheckData = filedata.split("\n\r");
            Log.d("DaoImpl-import", "CheckData=" + CheckData.toString());
            Log.d("DaoImpl-import", "CheckData=" + CheckData.length);

            db.beginTransaction();
            ContentValues val = null;
            for (int row = 0; row < CheckData.length; row++) {
                if (row == 0)
                    continue;

                String data[] = CheckData[row].toString().split("\t");
                Log.d("DaoImpl-import", "data=" + data.length);

                val = new ContentValues();

                for (int col = 0; col < data.length; col++) {
                    val.put(Constants.mColumns[col], "null".equals(data[col]) ? "" : data[col]);
                }

                if (!queryExists(data[Schedule.COL_SCHEDULE_DATE], data[Schedule.COL_SCHEDULE_TITLE], data[Schedule.COL_ETAG])) {

                    Log.d("DaoImpl-import", "val=" + val.toString());

                    val.remove(Schedule._ID);

                    db.insert(Schedule.SCHEDULE_TABLE_NAME, null, val);
                }

            }

            db.setTransactionSuccessful();
            db.endTransaction();

            return true;
        } catch (Exception e) {
            db.endTransaction();
            Log.i("Import", e.getMessage());
            return false;
        }
    }

    public boolean copy(Cursor cursor) {

        ContentValues val = null;
        // db = getWritableDatabase();

        if (!export(cursor)) {
            return false;
        }

        db.beginTransaction();

        boolean isFirst = true;

        while (cursor.moveToNext()) {

            if (isFirst) {
                StringBuffer query = new StringBuffer();
                query.append("DELETE FROM schedule ");
                db.execSQL(query.toString());
            }

            val = new ContentValues();

            for (int col = 0; col < cursor.getColumnCount(); col++) {
                val.put(Constants.mColumns[col], cursor.getString(col));
            }

            val.remove(Schedule._ID);

            db.insert("schedule", null, val);

            isFirst = false;
        }

        db.setTransactionSuccessful();
        db.endTransaction();

        return true;
    }

    public Cursor queryExistsAnniversary(String month, String lfromday, String ltoday) {

        StringBuffer query = new StringBuffer();

        lfromday = lfromday.substring(4, 8);
        ltoday = ltoday.substring(4, 8);

        boolean isChange = (lfromday.compareTo(ltoday) > 0);

        lfromday = String.format("%2s-%2s", lfromday.substring(0, 2), lfromday.substring(2));
        ltoday = String.format("%2s-%2s", ltoday.substring(0, 2), ltoday.substring(2));

        query.append("select case when alarm_lunasolar = 0 then cast(substr(alarm_date, -2) as integer)  ");
        query.append("      else cast(replace(substr(alarm_date, -5), '-', '') as integer) end lday ");
        query.append("      , cast(alarm_day as integer) flag ");
        query.append("from schedule ");
        query.append("where schedule_repeat = 9 ");
        query.append("and schedule_date = '1900-01-01' ");
        query.append("and (   (alarm_lunasolar = 0 and alarm_date like '" + month.substring(5, 7) + "%') ");
        if (isChange) {
            query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lfromday + "' and '" + lfromday.substring(0, 2) + "-31')  ");
            query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + ltoday.substring(0, 2) + "-01' and '" + ltoday + "') ) ");
        } else {
            query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lfromday + "' and '" + ltoday + "') ) ");
        }
        query.append("and alarm_time = '00:00' ");

        Log.d("DaoImpl-queryExistsAnniversary", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), null);

    }

    public boolean queryExists(String date, String title, String etag) {

        StringBuffer query = new StringBuffer();

        query.append(" select count(*) ");
        query.append(" from schedule ");
        query.append(" where schedule_date = '" + date + "' ");
        query.append(" and schedule_title = '" + title + "' ");
        query.append(" or etag = '" + etag + "' ");

        Log.d("DaoImpl-queryExists", "query=" + query.toString());

        Cursor c = getReadableDatabase().rawQuery(query.toString(), null);

        if (c.moveToNext()) {
            return (c.getInt(0) > 0);
        } else {
            return false;
        }

    }

    public Cursor queryExistsSchedule(String month) {

        StringBuffer query = new StringBuffer();

        String sDate[] = Common.tokenFn(month + "-01", "-");
        //String lStart = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), 1));
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.SUNDAY);
        c.set(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), 1);
        c.add(Calendar.DAY_OF_MONTH, -1);
        //int lastDay = c.get(Calendar.DAY_OF_MONTH);
        //String lEnd = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), lastDay));
        //boolean isChange = (lStart.substring(5).compareTo(lEnd.substring(5)) > 0);

        // �������
        query.append("SELECT  ");
        query.append("    DISTINCT CAST(STRFTIME('%d', " + Schedule.SCHEDULE_DATE + ", 'LOCALTIME') AS INTEGER) DAY  ");
        query.append("FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append("WHERE 1 = 1 ");
        query.append(" AND " + Schedule.SCHEDULE_DATE + " LIKE '" + month + "%' ");
        query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
        query.append(" AND " + Schedule.ANNIVERSARY + " <> 'Y' ");

        // ��±����
        query.append(" UNION ALL  ");
        query.append("SELECT  ");
        query.append("    DISTINCT CAST(STRFTIME('%d', " + Schedule.SCHEDULE_DATE + ", 'LOCALTIME') AS INTEGER) DAY  ");
        query.append("FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append("WHERE 1 = 1 ");
        query.append(" AND substr(" + Schedule.SCHEDULE_DATE + ",6,2) = '" + month.substring(5) + "' ");
        query.append(" AND " + Schedule.ANNIVERSARY + " = 'Y' ");
        query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
        query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + month + "-01" + "', 'LOCALTIME')||'-12-31' ");

        query.append(" ORDER BY 1 ");

        Log.d("DaoImpl-queryExistsSchedule", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), null);

    }

    public Cursor queryExistsSchedule2(String month) {

        StringBuffer query = new StringBuffer();

        String sDate[] = Common.tokenFn(month + "-01", "-");
        String lStart = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), 1));
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.SUNDAY);
        c.set(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), 1);
        c.add(Calendar.DAY_OF_MONTH, -1);
        int lastDay = c.get(Calendar.DAY_OF_MONTH);
        String lEnd = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), lastDay));
        boolean isChange = (lStart.substring(5).compareTo(lEnd.substring(5)) > 0);

        // ��������
        query.append("SELECT  ");
        //query.append("    DISTINCT CAST(STRFTIME('%d', " + Schedule.SCHEDULE_LDATE + ", 'LOCALTIME') AS INTEGER) DAY  ");
        query.append("  STRFTIME('%Y', '" + month + "-01" + "', 'LOCALTIME')||'-'|| substr(" + Schedule.SCHEDULE_LDATE + ", 6, 5) DAY  ");
        query.append("FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append("WHERE 1 = 1 ");
        query.append(" AND " + Schedule.ANNIVERSARY + " <> 'Y' ");
        query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
        if (isChange) {
            query.append(" AND (" + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '12-31'");
            query.append(" OR " + Schedule.SCHEDULE_LDATE + " between '01-01' and '" + lEnd.substring(5) + "')");
        } else {
            query.append(" AND ( " + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "')");
        }
        // ���±����
        query.append(" UNION ALL  ");
        query.append("SELECT  ");
        //query.append("    DISTINCT CAST(STRFTIME('%d', " + Schedule.SCHEDULE_LDATE + ", 'LOCALTIME') AS INTEGER) DAY  ");
        query.append("  STRFTIME('%Y', '" + month + "-01" + "', 'LOCALTIME')||'-'|| substr(" + Schedule.SCHEDULE_LDATE + ", 6, 5) DAY  ");
        query.append("FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append("WHERE 1 = 1 ");
        query.append(" AND " + Schedule.ANNIVERSARY + " = 'Y' ");
        query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
        query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + month + "-01" + "', 'LOCALTIME')||'-12-31' ");

        if (isChange) {
            query.append(" AND (substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '12-31'");
            query.append(" OR substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '01-01' and '" + lEnd.substring(5) + "')");
        } else {
            query.append(" AND ( substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "')");
        }

        query.append(" ORDER BY 1 ");

        Log.d("DaoImpl-queryExistsSchedule", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), null);

    }

    public Cursor queryExistsDday(String month) {

        StringBuffer query = new StringBuffer();

        query.append("SELECT  ");
        query.append("    DISTINCT CAST(STRFTIME('%d',  DATE(" + Schedule.SCHEDULE_DATE + " ," + Schedule.DDAY_ALARMSIGN + "|| " + Schedule.DDAY_ALARMDAY + " ||' DAY', 'LOCALTIME')) AS INTEGER) DAY ");
        query.append("FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append("WHERE " + Schedule.DDAY_ALARMYN + " = 1 ");
        query.append("    AND STRFTIME('%Y-%m',  DATE(" + Schedule.SCHEDULE_DATE + " ," + Schedule.DDAY_ALARMSIGN + "|| " + Schedule.DDAY_ALARMDAY + " ||' DAY', 'LOCALTIME')) LIKE ?  ");
        query.append("    AND " + Schedule.DDAY_DISPLAYYN + " IN (0, 1, 2)  ");
        query.append("ORDER BY 1 ");

        String selectionArgs[] = new String[] { month + "%" };

        //Log.d("DaoImpl-queryExistsDday", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    public Cursor queryDDay() {

        StringBuffer query = new StringBuffer();

        query.append("SELECT ");
        query.append(" " + Schedule.SCHEDULE_TITLE + " ");
        //query.append("," + Schedule.SCHEDULE_DATE + " ");
        query.append(",DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME') " + Schedule.SCHEDULE_DATE + " ");
        query.append(",cast(JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) dday  ");
        //query.append(",ROUND(JULIANDAY('NOW', 'LOCALTIME') - JULIANDAY(" + Schedule.SCHEDULE_DATE + ", 'LOCALTIME')) DDAY");
        //query.append("," + Schedule.SCHEDULE_LDATE + " ");
        //query.append("," + Schedule.LUNARYN + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.DDAY_DISPLAYYN + " = 1 ");

        String selectionArgs[] = null;

        Log.d("DaoImpl-queryDDay", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    public Cursor queryWidget() {

        StringBuffer query = new StringBuffer();

        query.append("SELECT ");
        query.append(" " + Schedule.SCHEDULE_TITLE + " ");
        //query.append("," + Schedule.SCHEDULE_DATE + " ");
        query.append(",DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME') " + Schedule.SCHEDULE_DATE + " ");
        query.append(",cast(JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) dday  ");
        //query.append(",ROUND(JULIANDAY('NOW', 'LOCALTIME') - JULIANDAY(" + Schedule.SCHEDULE_DATE + ", 'LOCALTIME')) DDAY");
        //query.append("," + Schedule.SCHEDULE_LDATE + " ");
        //query.append("," + Schedule.LUNARYN + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        //query.append(" WHERE " + Schedule.DDAY_DISPLAYYN + " = 1 ");
        query.append(" LIMIT 1 ");

        String selectionArgs[] = null;

        Log.d("DaoImpl-queryDDay", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    public Cursor queryDDay(Long id) {

        StringBuffer query = new StringBuffer();

        query.append("SELECT ");
        query.append(" " + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_DATE + " ");
        query.append(",cast(JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) dday  ");
        //query.append(",ROUND(JULIANDAY('NOW', 'LOCALTIME') - JULIANDAY(" + Schedule.SCHEDULE_DATE + ", 'LOCALTIME')) DDAY");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.DDAY_DISPLAYYN + " = 1 ");
        query.append(" AND " + Schedule._ID + " = " + id);

        String selectionArgs[] = null;

        //Log.d("DaoImpl-queryDDay", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    public Cursor queryAlarm(Calendar c, String lDay) {

        String date = Common.fmtDate(c);
        String time = c.get(Calendar.HOUR_OF_DAY) > 9 ? "" + c.get(Calendar.HOUR_OF_DAY) : "0" + c.get(Calendar.HOUR_OF_DAY);

        String sday = Common.fmtDate(c).substring(5);
        String lday = lDay.substring(4, 6) + "-" + lDay.substring(6);
        int ilday = Integer.parseInt(lDay.substring(6).trim());

        StringBuffer query = new StringBuffer();

        query.append("SELECT ");
        query.append(" " + Schedule._ID + " ");
        query.append("," + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_CONTENTS + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.SCHEDULE_REPEAT + "=1"); // �ѹ� �����⤷���������Ϥ��� ����
        query.append(" AND " + Schedule.ALARM_DATE + " = '" + date + "'");
        query.append(" AND " + Schedule.ALARM_TIME + " >= '" + time + ":0'");
        query.append(" AND " + Schedule.ALARM_TIME + " <= '" + time + ":59'");
        query.append(" AND " + Schedule.SCHEDULE_CHECK + " != '" + date + "'");

        query.append(" UNION ALL ");

        query.append("SELECT ");
        query.append(" " + Schedule._ID + " ");
        query.append("," + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_CONTENTS + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.SCHEDULE_REPEAT + "=2"); // ���� ���� ����
        query.append(" AND " + Schedule.ALARM_TIME + " >= '" + time + ":0'");
        query.append(" AND " + Schedule.ALARM_TIME + " <= '" + time + ":59'");
        query.append(" AND " + Schedule.SCHEDULE_CHECK + " != '" + date + "'");

        query.append(" UNION ALL ");

        query.append("SELECT ");
        query.append(" " + Schedule._ID + " ");
        query.append("," + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_CONTENTS + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.SCHEDULE_REPEAT + "=3"); // ���� ������ ���� ����
        query.append(" AND " + Schedule.ALARM_DAYS + " = " + c.get(Calendar.DAY_OF_WEEK));
        query.append(" AND " + Schedule.ALARM_TIME + " >= '" + time + ":0'");
        query.append(" AND " + Schedule.ALARM_TIME + " <= '" + time + ":59'");
        query.append(" AND " + Schedule.SCHEDULE_CHECK + " != '" + date + "'");

        query.append(" UNION ALL ");

        query.append("SELECT ");
        query.append(" " + Schedule._ID + " ");
        query.append("," + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_CONTENTS + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.SCHEDULE_REPEAT + "=4"); // �ſ� ���� ���� ����
        query.append(" AND ((" + Schedule.ALARM_LUNASOLAR + " = 0 AND " + Schedule.ALARM_DAY + " = " + c.get(Calendar.DAY_OF_MONTH));
        query.append(") OR (" + Schedule.ALARM_LUNASOLAR + " = 1 AND " + Schedule.ALARM_DAY + " = " + ilday + "))");
        query.append(" AND " + Schedule.ALARM_TIME + " >= '" + time + ":0'");
        query.append(" AND " + Schedule.ALARM_TIME + " <= '" + time + ":59'");
        query.append(" AND " + Schedule.SCHEDULE_CHECK + " != '" + date + "'");

        query.append(" UNION ALL ");

        query.append("SELECT ");
        query.append(" " + Schedule._ID + " ");
        query.append("," + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_CONTENTS + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.SCHEDULE_REPEAT + "=5"); // �ų� ���� ���� ����
        query.append(" AND ((" + Schedule.ALARM_LUNASOLAR + " = 0 AND " + Schedule.ALARM_DATE + " = '" + sday + "'");
        query.append(") OR (" + Schedule.ALARM_LUNASOLAR + " = 1 AND " + Schedule.ALARM_DATE + " = '" + lday + "'))");
        query.append(" AND " + Schedule.ALARM_TIME + " >= '" + time + ":0'");
        query.append(" AND " + Schedule.ALARM_TIME + " <= '" + time + ":59'");
        query.append(" AND " + Schedule.SCHEDULE_CHECK + " != '" + date + "'");

        query.append(" UNION ALL ");

        query.append("SELECT ");
        query.append(" " + Schedule._ID + " ");
        query.append("," + Schedule.SCHEDULE_TITLE + " ");
        query.append("," + Schedule.SCHEDULE_CONTENTS + " ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME + " ");
        query.append(" WHERE " + Schedule.SCHEDULE_REPEAT + " = 6 ");
        query.append(" and schedule_date > strftime('%Y-%m-%d', 'now', 'localtime') ");
        query.append(" and cast (julianday('now', 'localtime') -  julianday(schedule_date, 'localtime') as integer) % alarm_day  = 0 ");

        String selectionArgs[] = null;

        Log.i("DaoImpl-queryAlarm", "query=" + query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    public Cursor queryGroup(String range, String date, boolean isSearch, int operator, String keyword1, String keyword2) {

        StringBuilder query;
        query = new StringBuilder();
        String baseDate = date;

        if ("".equals(baseDate)) {
            Calendar c = Calendar.getInstance();
            c.setFirstDayOfWeek(Calendar.SUNDAY);
            baseDate = Common.fmtDate(c);
        } else if (baseDate.length() < 10) {
            baseDate += "-01";
        }

        query.append("SELECT " + Schedule._ID);
        query.append("    ,CASE WHEN " + Schedule.SCHEDULE_REPEAT + " = 9 THEN '" + mContext.getResources().getString(R.string.anniversary_label) + "'");
        query.append("    when  " + Schedule.SCHEDULE_REPEAT + " in ('F','P') THEN " + " ' B-Plan ' ");
        query.append("    when  " + Schedule.SCHEDULE_REPEAT + " < 9 and " + Schedule.DDAY_ALARMYN + " = 1 THEN " + Schedule.SCHEDULE_DATE + "||'\n'||strftime('%Y-%m-%d', DATE(" + Schedule.SCHEDULE_DATE + ", dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'localtime') ");
        query.append("    ELSE " + Schedule.SCHEDULE_DATE + " END " + Schedule.SCHEDULE_DATE);
        query.append("    ,CASE WHEN " + Schedule.SCHEDULE_REPEAT + " IN ('F','P','9') THEN " + Schedule.SCHEDULE_TITLE + "||'('||" + Schedule.ALARM_DATE + "||')'");
        query.append("    when  " + Schedule.SCHEDULE_REPEAT + " < 9 and dday_alarmyn = 1 THEN ");
        query.append("   substr(schedule_title, 1, 15) ||'('|| ");
        query.append("case when cast(JULIANDAY('" + baseDate + "', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) > 0  ");
        query.append("then 'D + ' || cast(JULIANDAY('" + baseDate + "', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer)  ");
        query.append(" when cast(JULIANDAY('" + baseDate + "', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) = 0  ");
        query.append("then 'D day' else 'D ' ||  cast(JULIANDAY('" + baseDate + "', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) end ");
        query.append("    ||')' ");
        query.append("    ELSE " + Schedule.SCHEDULE_TITLE + " END " + Schedule.SCHEDULE_TITLE);
        query.append("    , " + Schedule.SCHEDULE_REPEAT);
        query.append("    , bible_book, bible_chapter ");
        query.append(" FROM " + Schedule.SCHEDULE_TABLE_NAME);
        query.append(" WHERE 1 = 1 ");

        if (!"".equals(date)) {
            String sDate[] = Common.tokenFn(date, "-");

            String lDay = "";
            if (sDate.length > 2) {
                lDay = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), Integer.parseInt(sDate[2])));
            } else {
                lDay = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), 1));
            }

            if ("TODAY".equals(range)) {

                String lStart = lDay;
                String lEnd = lDay;

                //�������
                query.append(" AND ( " + Schedule.SCHEDULE_DATE + " = '" + date + "' ");
                query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
                query.append(" AND " + Schedule.ANNIVERSARY + " <> 'Y' )");

                //��±����
                query.append(" or ( " + Schedule.SCHEDULE_DATE + " = '" + date + "' ");
                query.append(" AND " + Schedule.ANNIVERSARY + " = 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
                query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date.substring(0, 7) + "-01" + "', 'LOCALTIME')||'-12-31') ");

                //��������
                query.append(" or ( " + Schedule.ANNIVERSARY + " <> 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
                query.append(" AND ( " + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "'))");

                // ���±����
                query.append(" or( " + Schedule.ANNIVERSARY + " = 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
                query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date.substring(0, 7) + "-01" + "', 'LOCALTIME')||'-12-31' ");
                query.append(" AND ( substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "'))");

                // dday
                query.append(" or ( dday_alarmyn = 1  ");
                query.append("and dday_displayyn = 2  ");
                query.append("or (dday_alarmyn = 1  ");
                query.append("and dday_displayyn in (0, 1)  ");
                query.append("and strftime('%Y-%m-%d', DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'localtime') = '" + date + "'  ) )");

                //�����б��÷�
                /*
                if (Prefs.getBplan(mContext) && (Prefs.getBplanFamily(mContext) || Prefs.getBplanPersonal(mContext))) {
                    query.append("  or( schedule_repeat in ('F','P') ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and alarm_date like '" + date.substring(5, 10) + "%' ");
                    query.append("and alarm_time = '00:00' )");
                }
                */

                if (Prefs.getAnniversary(this.mContext)) {

                    // �ý��� �����
                    query.append("or ( schedule_repeat = 9 ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and (   (alarm_lunasolar = 0 and alarm_date like '" + date.substring(5, 10) + "%') ");
                    //query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lStart + "' and '" + lEnd + "') ) ");
                    query.append("     or (alarm_lunasolar = 1 and alarm_date = '" + lDay + "') ) ");
                    query.append("and alarm_time = '00:00' )");

                } else {
                    // �ý��� �����
                    query.append("and schedule_repeat < 8 ");
                    query.append("and schedule_date > '1900-01-01' ");
                }

            } else if ("WEEK".equals(range)) {

                Calendar c = Calendar.getInstance();
                c.setFirstDayOfWeek(Calendar.SUNDAY);
                c.set(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]) - 1, Integer.parseInt(sDate[2]));
                c.add(Calendar.DAY_OF_MONTH, (c.get(Calendar.DAY_OF_WEEK) - 1) * -1);
                String Start = Common.fmtDate(c);
                c.add(Calendar.DAY_OF_MONTH, 6);
                String End = Common.fmtDate(c);

                c = Calendar.getInstance();
                c.setFirstDayOfWeek(Calendar.SUNDAY);
                c.set(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]) - 1, Integer.parseInt(sDate[2]));
                c.add(Calendar.DAY_OF_MONTH, ((c.get(Calendar.DAY_OF_WEEK) - 1) * -1));

                String lStart = Common.fmtDate(lunar2solar.s2l(c));
                c.add(Calendar.DAY_OF_MONTH, 6);
                String lEnd = Common.fmtDate(lunar2solar.s2l(c));
                boolean isChange = (lStart.substring(5).compareTo(lEnd.substring(5)) > 0);

                //�������
                query.append(" AND ( " + Schedule.SCHEDULE_DATE + " between '" + Start + "' and '" + End + "'");
                query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
                query.append(" AND " + Schedule.ANNIVERSARY + " <> 'Y' )");

                //��±����
                query.append(" or ( substr(" + Schedule.SCHEDULE_DATE + ",6,5) between '" + Start.substring(5, 10) + "' and '" + End.substring(5, 10) + "'");
                query.append(" AND " + Schedule.ANNIVERSARY + " = 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
                query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date.substring(0, 7) + "-01" + "', 'LOCALTIME')||'-12-31') ");

                //��������
                query.append(" or ( " + Schedule.ANNIVERSARY + " <> 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
                if (isChange) {
                    query.append(" AND (" + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '12-31'");
                    query.append(" OR " + Schedule.SCHEDULE_LDATE + " between '01-01' and '" + lEnd.substring(5) + "'))");
                } else {
                    query.append(" AND ( " + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "'))");
                }

                // ���±����
                query.append(" or( " + Schedule.ANNIVERSARY + " = 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
                query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date.substring(0, 7) + "-01" + "', 'LOCALTIME')||'-12-31' ");

                if (isChange) {
                    query.append(" AND (substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '12-31'");
                    query.append(" OR substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '01-01' and '" + lEnd.substring(5) + "'))");
                } else {
                    query.append(" AND ( substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "'))");
                }

                // dday
                query.append(" or ( dday_alarmyn = 1  ");
                query.append("and dday_displayyn = 2  ");
                query.append("or (dday_alarmyn = 1  ");
                query.append("and dday_displayyn in (0, 1)  ");
                query.append("and strftime('%Y-%m-%d', DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'localtime') between '" + Start + "' and '" + End + "' ) )");

                //�����б��÷�
                /*
                if (Prefs.getBplan(mContext) && (Prefs.getBplanFamily(mContext) || Prefs.getBplanPersonal(mContext))) {
                    query.append("  or( schedule_repeat in ('F','P') ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and alarm_date between '" + Start.substring(5, 10) + "' and '" + End.substring(5, 10) + "' ");
                    query.append("and alarm_time = '00:00' )");
                }
                */

                if (Prefs.getAnniversary(this.mContext)) {

                    // �ý��� �����
                    query.append("or ( schedule_repeat = 9 ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and (alarm_lunasolar = 0 and alarm_date between '" + Start.substring(5, 10) + "' and '" + End.substring(5, 10) + "') ");
                    if (isChange) {
                        query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lStart + "' and '" + "12-31')  ");
                        query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + "01-01' and '" + lEnd + "')  ");
                    } else {
                        query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lStart + "' and '" + lEnd + "')  ");
                    }
                    query.append("and alarm_time = '00:00' )");

                } else {
                    // �ý��� �����
                    query.append("and schedule_repeat < 8 ");
                    query.append("and schedule_date > '1900-01-01' ");
                }

            } else if ("MONTH".equals(range)) {
                String lStart = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), 1));
                Calendar c = Calendar.getInstance();
                c.setFirstDayOfWeek(Calendar.SUNDAY);
                c.set(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]) - 1, 1);
                c.add(Calendar.DAY_OF_MONTH, -1);
                int lastDay = c.get(Calendar.DAY_OF_MONTH);
                String lEnd = Common.fmtDate(lunar2solar.s2l(Integer.parseInt(sDate[0]), Integer.parseInt(sDate[1]), lastDay));

                boolean isChange = (lStart.substring(5).compareTo(lEnd.substring(5)) > 0);

                //�������
                query.append(" AND ( " + Schedule.SCHEDULE_DATE + " LIKE '" + date.substring(0, 7) + "%' ");
                query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
                query.append(" AND " + Schedule.ANNIVERSARY + " <> 'Y' )");

                //��±����
                query.append(" or ( substr(" + Schedule.SCHEDULE_DATE + ",6,2) = '" + date.substring(5, 7) + "' ");
                query.append(" AND " + Schedule.ANNIVERSARY + " = 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " <> 'Y' ");
                query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date.substring(0, 7) + "-01" + "', 'LOCALTIME')||'-12-31') ");

                //��������
                query.append(" or ( " + Schedule.ANNIVERSARY + " <> 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
                if (isChange) {
                    query.append(" AND (" + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '12-31'");
                    query.append(" OR " + Schedule.SCHEDULE_LDATE + " between '01-01' and '" + lEnd.substring(5) + "'))");
                } else {
                    query.append(" AND ( " + Schedule.SCHEDULE_LDATE + " between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "'))");
                }

                // ���±����
                query.append(" or ( " + Schedule.ANNIVERSARY + " = 'Y' ");
                query.append(" AND " + Schedule.LUNARYN + " = 'Y' ");
                query.append(" AND " + Schedule.SCHEDULE_DATE + " <= STRFTIME('%Y', '" + date.substring(0, 7) + "-01" + "', 'LOCALTIME')||'-12-31' ");

                if (isChange) {
                    query.append(" AND (substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '12-31'");
                    query.append(" OR substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '01-01' and '" + lEnd.substring(5) + "' ) ) ");
                } else {
                    query.append(" AND ( substr(" + Schedule.SCHEDULE_LDATE + ",6,5) between '" + lStart.substring(5) + "' and '" + lEnd.substring(5) + "'))");
                }

                // dday
                query.append(" or ( dday_alarmyn = 1  ");
                query.append("and dday_displayyn = 2  ");
                query.append("or (dday_alarmyn = 1  ");
                query.append("and dday_displayyn in (0, 1)  ");
                query.append("and strftime('%Y-%m-%d', DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'localtime') like '" + date.substring(0, 7) + "%'  ) )");

                //�����б��÷�
                /*
                if (Prefs.getBplan(mContext) && (Prefs.getBplanFamily(mContext) || Prefs.getBplanPersonal(mContext))) {
                    query.append("  or( schedule_repeat in ('F','P') ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and  alarm_date like '" + date.substring(5, 7) + "%' ");
                    query.append("and alarm_time = '00:00' )");
                }
                */

                if (Prefs.getAnniversary(this.mContext)) {
                    // �ý��� �����
                    query.append("or ( schedule_repeat in ('F','P','9') ");
                    query.append("and schedule_date = '1900-01-01' ");
                    query.append("and    (alarm_lunasolar = 0 and alarm_date like '" + date.substring(5, 7) + "%') ");
                    if (isChange) {
                        query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lStart + "' and '" + "12-31')  ");
                        query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + "01-01' and '" + lEnd + "') ");
                    } else {
                        query.append("     or (alarm_lunasolar = 1 and alarm_date between '" + lStart + "' and '" + lEnd + "')  ");
                    }
                    query.append("and alarm_time = '00:00' )");
                } else {
                    // �ý��� �����
                    query.append("and schedule_repeat < 8 ");
                    query.append("and schedule_date > '1900-01-01' ");
                }
            } else {

                if (!Prefs.getAnniversary(this.mContext)) {
                    // �ý��� �����
                    query.append("and schedule_repeat < 8 ");
                    query.append("and schedule_date > '1900-01-01' ");
                }
            }
        } else if (isSearch) {

            query.append("and (schedule_title||ifnull(schedule_contents,'') like '%" + keyword1 + "%')");

            if (!"".equals(keyword2)) {

                if (0 == operator || 2 == operator)//and
                    query.append(" and ");
                else if (1 == operator)//or
                    query.append(" or ");

                query.append(" (schedule_title||ifnull(schedule_contents,'') ");
                if (2 == operator)
                    query.append(" not ");
                query.append(" like '%" + keyword2 + "%')");

            }
        } else {
            if (!Prefs.getAnniversary(this.mContext)) {
                // �ý��� �����
                query.append("and schedule_repeat < 8 ");
                query.append("and schedule_date > '1900-01-01' ");
            }
        }

        //query.append(" ORDER BY " + Schedule._ID + " DESC");
        query.append(" ORDER BY SUBSTR(" + Schedule.SCHEDULE_DATE + ", -5) ASC");

        Log.d("DaoImpl-queryGroup", query.toString());

        return getReadableDatabase().rawQuery(query.toString(), null);

    }

    public Cursor queryChild(Long id) {

        String selectionArgs[] = new String[] { id.toString() };

        StringBuilder query;

        query = new StringBuilder();

        query.append("SELECT " + Schedule._ID);
        query.append("    ,case when schedule_repeat = 9 then " + Schedule.ALARM_DATE);
        query.append("    else " + Schedule.SCHEDULE_CONTENTS + " end " + Schedule.SCHEDULE_CONTENTS);
        query.append("    ,case schedule_repeat ");
        query.append("    when 1 then  alarm_date || ' ' || alarm_time ");
        query.append("    when 2 then '" + mContext.getResources().getString(R.string.every_day_label) + "' || alarm_time ");
        query.append("    when 3 then '" + mContext.getResources().getString(R.string.every_week_label) + "' || d.dayname || ' '|| alarm_time ");
        query.append("    when 4 then '" + mContext.getResources().getString(R.string.every_month_label) + "' || ( ");
        query.append("        case when alarm_lunasolar = 0 then '" + mContext.getResources().getString(R.string.gregorian) + "' else '" + mContext.getResources().getString(R.string.lunar) + "' end)|| ' '|| alarm_day || '" + mContext.getResources().getString(R.string.day_label) + "' ");
        query.append("    when 5 then '" + mContext.getResources().getString(R.string.every_year_label) + "' || ( ");
        query.append("        case when alarm_lunasolar = 0 then '" + mContext.getResources().getString(R.string.gregorian) + "' else '" + mContext.getResources().getString(R.string.lunar) + "' end)|| ' '|| alarm_date || ' ' || alarm_time ");
        query.append("    else '" + mContext.getResources().getString(R.string.alarm_none) + "' end alarm_detailinfo ");
        query.append("    ,case when cast(JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) < 0   ");
        query.append("          then 'D ' || cast(JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) || 'day'  ");
        query.append("          when cast (JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) = 0 then 'D day'  ");
        query.append("          else 'D +' || cast(JULIANDAY('now', 'LOCALTIME') - JULIANDAY(DATE(schedule_date, dday_alarmsign || dday_alarmday ||' DAY', 'LOCALTIME'), 'LOCALTIME') as integer) || 'day' end dday_detailinfo  ");
        query.append("    , " + Schedule.SCHEDULE_REPEAT);
        query.append("    , bible_book, bible_chapter ");
        query.append(" FROM " + Schedule.SCHEDULE_DAYS_JOIN_TABLE);
        query.append(" WHERE _id = ? ");

        //Log.d("DaoImpl-queryChild", query.toString());

        return getReadableDatabase().rawQuery(query.toString(), selectionArgs);

    }

    @Override
    public void close() {
        if (db != null) {
            db.close();
        }
        super.close();
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();

    }

}