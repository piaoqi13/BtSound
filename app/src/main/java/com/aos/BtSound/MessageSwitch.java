package com.aos.BtSound;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aos.BtSound.model.RecordFileInfo;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by collin on 2015-10-11.
 */
public class MessageSwitch extends Activity implements View.OnClickListener {
    private Button mBtnClosed = null;
    private Button mBtnOpen = null;
    private ListView mLvRecord = null;
    private List<RecordFileInfo> mRecords = null;//语音记事集合
    private RecordAdapter mRecordAdapter = null;
    private ImageView mIvBack = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message_switch);
        initView();
        initData();
        initListener();
    }

    private void initView() {
        mBtnClosed = (Button) findViewById(R.id.btn_closed);
        mBtnOpen = (Button) findViewById(R.id.btn_open);
        mLvRecord = (ListView) findViewById(R.id.lv_record_file);
        mIvBack = (ImageView) findViewById(R.id.iv_back);
    }

    private void initData() {
        if (Settings.getBoolean(Config.IS_READ_SMS, true, false)) {
            mBtnOpen.setTextColor(this.getResources().getColor(R.color.gray_text));
            mBtnOpen.setEnabled(false);
            mBtnClosed.setEnabled(true);
            mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.green_background));
            mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.white_text));
            mBtnClosed.setTextColor(this.getResources().getColor(R.color.black_text));
        } else {
            mBtnOpen.setTextColor(this.getResources().getColor(R.color.black_text));
            mBtnClosed.setEnabled(false);
            mBtnOpen.setEnabled(true);
            mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.white_text));
            mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.gray_text));
            mBtnClosed.setTextColor(this.getResources().getColor(R.color.black_text));
        }

        mRecords = new ArrayList<RecordFileInfo>();
        String imagePath = Environment.getExternalStorageDirectory().toString() + "/audio";
        File mfile = new File(imagePath);
        File[] files = mfile.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.getPath().endsWith(".mp3")) {
                mRecords.add(new RecordFileInfo(file.getName(), file.getPath()));
            }
        }
        mRecordAdapter = new RecordAdapter(this, mRecords);
        mLvRecord.setAdapter(mRecordAdapter);
    }

    private void initListener() {
        mBtnClosed.setOnClickListener(this);
        mBtnOpen.setOnClickListener(this);
        mIvBack.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_closed:
                Settings.setBoolean(Config.IS_READ_SMS, false, false);
                mBtnOpen.setTextColor(this.getResources().getColor(R.color.black_text));
                mBtnClosed.setEnabled(false);
                mBtnOpen.setEnabled(true);
                mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.white_text));
                mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.gray_text));
                mBtnClosed.setTextColor(this.getResources().getColor(R.color.black_text));
                Toast.makeText(this, "已关闭", Toast.LENGTH_LONG).show();
                break;
            case R.id.btn_open:
                Settings.setBoolean(Config.IS_READ_SMS, true, false);
                mBtnOpen.setTextColor(this.getResources().getColor(R.color.gray_text));
                mBtnOpen.setEnabled(false);
                mBtnOpen.setBackgroundColor(this.getResources().getColor(R.color.green_background));
                mBtnClosed.setBackgroundColor(this.getResources().getColor(R.color.white_text));
                mBtnClosed.setEnabled(true);
                mBtnClosed.setTextColor(this.getResources().getColor(R.color.black_text));
                Toast.makeText(this, "已打开", Toast.LENGTH_LONG).show();
                break;
            case R.id.iv_back:
                finish();
                break;
        }
    }


    public class RecordAdapter extends BaseAdapter {
        private Context mContext = null;
        private LayoutInflater mInflater = null;
        private List<RecordFileInfo> mRecords = null;

        private int mPosition = 0;
        private boolean isHaveSubmit = false;

        public RecordAdapter(Activity context, List<RecordFileInfo> records) {
            this.mContext = context;
            this.mRecords = records;
            mInflater = LayoutInflater.from(mContext);
        }

        @Override
        public int getCount() {
            return mRecords == null ? 0 : mRecords.size();
        }

        @Override
        public Object getItem(int position) {
            return mRecords.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.record_item_layout, null);
                holder = new ViewHolder();
                holder.mTvName = (TextView) convertView.findViewById(R.id.tv_record_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.mTvName.setText(mRecords.get(position).getFileName());

            holder.mTvName.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent(Intent.ACTION_VIEW);
                    it.setDataAndType(Uri.parse("file://" + mRecords.get(position).getFilePath()), "audio/MP3");
                    startActivity(it);
                }
            });

            holder.mTvName.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setMessage("确定要删除记录？");
                    builder.setTitle("温馨提示");

                    builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            File file = new File(mRecords.get(position).getFilePath());
                            Log.i("CollinWang","file path=" + file.getPath());
                            if (file.exists()) {
                                mRecords.remove(position);
                                boolean result = file.delete();
                                Log.i("CollinWang","result=" + result);
                            }
                            mRecordAdapter.notifyDataSetChanged();
                            Log.i("CollinWang", "notify run");
                            dialog.dismiss();
                        }
                    });

                    builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

                    builder.create().show();
                    return false;
                }
            });

            return convertView;
        }

        private class ViewHolder {
            private TextView mTvName = null;
        }

    }
}
