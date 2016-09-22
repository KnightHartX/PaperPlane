package com.marktony.zhihudaily.homepage;

import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;

import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.marktony.zhihudaily.bean.DoubanMomentNews;
import com.marktony.zhihudaily.bean.StringModelImpl;
import com.marktony.zhihudaily.db.DatabaseHelper;
import com.marktony.zhihudaily.interfaces.OnStringListener;
import com.marktony.zhihudaily.detail.DoubanDetailActivity;
import com.marktony.zhihudaily.service.CacheService;
import com.marktony.zhihudaily.util.Api;
import com.marktony.zhihudaily.util.DateFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Lizhaotailang on 2016/9/10.
 */

public class DoubanMomentPresenter implements DoubanMomentContract.Presenter, OnStringListener {

    private DoubanMomentContract.View view;
    private Context context;
    private StringModelImpl model;

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;

    private ServiceConnection conn;
    private CacheService service;

    private ArrayList<DoubanMomentNews.posts> list = new ArrayList<>();
    private ArrayList<Integer> doubanIds = new ArrayList<>();

    public DoubanMomentPresenter(Context context, DoubanMomentContract.View view) {
        this.context = context;
        this.view = view;
        this.view.setPresenter(this);
        model = new StringModelImpl(context);
        dbHelper = new DatabaseHelper(context, "History.db", null, 4);
        db = dbHelper.getWritableDatabase();
    }

    @Override
    public void startReading(int position) {
        DoubanMomentNews.posts item = list.get(position);
        Intent intent = new Intent(context, DoubanDetailActivity.class);
        intent.putExtra("id", item.getId());
        intent.putExtra("title", item.getTitle());
        if (item.getThumbs().size() == 0){
            intent.putExtra("image", "");
        } else {
            intent.putExtra("image", item.getThumbs().get(0).getMedium().getUrl());
        }
        context.startActivity(intent);
    }

    @Override
    public void loadPosts(long date, boolean clearing) {
        view.startLoading();
        if (clearing) {
            list.clear();
        }
        model.load(Api.DOUBAN_MOMENT + new DateFormatter().DoubanDateFormat(date), this);
    }

    @Override
    public void refresh() {
        list.clear();
        loadPosts(Calendar.getInstance().getTimeInMillis(), true);
    }

    @Override
    public void loadMore(long date) {
        model.load(Api.DOUBAN_MOMENT + new DateFormatter().DoubanDateFormat(date), this);
    }

    @Override
    public void bindService() {
        conn = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                CacheService.MyBinder binder = (CacheService.MyBinder) iBinder;
                service = binder.getService();
                binder.getService().setDoubanIds(doubanIds);
                bindService();
                service.startDoubanCache();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {

            }
        };

        context.bindService(new Intent(context, CacheService.class), conn, Service.BIND_AUTO_CREATE);

    }

    @Override
    public void unBindService() {
        if (service != null) {
            service.unbindService(conn);
        }
    }

    @Override
    public void start() {

    }

    @Override
    public void onSuccess(String result) {
        Gson gson = new Gson();
        DoubanMomentNews post = gson.fromJson(result, DoubanMomentNews.class);
        for (DoubanMomentNews.posts item : post.getPosts()) {
            list.add(item);
            doubanIds.add(item.getId());

            ContentValues values = new ContentValues();

            if ( !queryIfIDExists(item.getId())) {
                db.beginTransaction();
                try {
                    values.put("douban_id", item.getId());
                    values.put("douban_news", gson.toJson(item));
                    DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    Date date = format.parse(item.getPublished_time());
                    values.put("douban_time", date.getTime() / 1000);
                    values.put("douban_content", "");
                    db.insert("Douban", null, values);
                    values.clear();
                    db.setTransactionSuccessful();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    db.endTransaction();
                }
            }
        }
        view.showResults(list);
        view.stopLoading();

    }

    @Override
    public void onError(VolleyError error) {
        view.stopLoading();
        view.showLoadError();
    }

    private boolean queryIfIDExists(int id){
        Cursor cursor = db.query("Douban",null,null,null,null,null,null);
        if (cursor.moveToFirst()){
            do {
                if (id == cursor.getInt(cursor.getColumnIndex("douban_id"))){
                    return true;
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        return false;
    }

}