package top.soyask.calendarii.ui.fragment.event;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import top.soyask.calendarii.R;
import top.soyask.calendarii.ui.adapter.event.EventAdapter;
import top.soyask.calendarii.database.dao.EventDao;
import top.soyask.calendarii.domain.Event;
import top.soyask.calendarii.ui.fragment.base.BaseFragment;


public class AllEventFragment extends BaseFragment implements EventAdapter.OnEventItemClickListener, View.OnClickListener, DeleteFragment.OnDeleteConfirmListener {

    private static final int WAIT = 0x0;
    private static final int CANCEL = 0x1;
    private static final int DELETE_ALL = 0x2;
    private static final int DELETE_COMP = 0x3;
    private static final String TITLE = "TITLE";
    private EventAdapter mEventAdapter;
    private EventDao mEventDao;
    private List<Event> mEvents;
    private List<Event> mCompleteEvents;
    private ProgressDialog mProgressDialog;
    private String mTitle;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case WAIT:
                    mProgressDialog = ProgressDialog.show(getMainActivity(), null, "正在恢复，请稍等...");
                    break;
                case CANCEL:
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                    break;
                case DELETE_ALL:
                    deleteAll();
                    break;
                case DELETE_COMP:
                    deleteComplete();
                    break;
            }
        }
    };


    private Comparator<Event> mComparator = new Comparator<Event>() {
        @Override
        public int compare(Event o1, Event o2) {
            String title0 = o1.getTitle();
            String title1 = o2.getTitle();
            int s = title0.compareTo(title1);
            return s == 0 ? o1.getId() - o2.getId() : s;
        }
    };
    private RecyclerView mRecyclerView;

    public AllEventFragment() {
        super(R.layout.fragment_all);
    }

    public static AllEventFragment newInstance(String title) {
        AllEventFragment fragment = new AllEventFragment();
        Bundle args = new Bundle();
        args.putString(TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mTitle = getArguments().getString(TITLE);
        }
    }

    @Override
    protected void setupUI() {
        loadData();
        mRecyclerView = findViewById(R.id.rv_event);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mEventAdapter = new EventAdapter(mEvents, this);
        mRecyclerView.setAdapter(mEventAdapter);

        if(mTitle != null){
            findToolbar().setTitle(mTitle);
        }
        findToolbar().setNavigationOnClickListener(this);
        findViewById(R.id.ib_delete_all).setOnClickListener(this);
        if (mEvents.isEmpty()) {
            findViewById(R.id.ib_delete_all).setVisibility(View.GONE);
        }
    }

    private void loadData() {
        mEventDao = EventDao.getInstance(getMainActivity());

        if (mTitle == null) {
            mEvents = mEventDao.queryAll();
        } else {
            mEvents = mEventDao.query(mTitle);
        }

        Collections.sort(mEvents, mComparator);
        mCompleteEvents = new ArrayList<>();
        for (Event event : mEvents) {
            if (event.isComplete()) {
                mCompleteEvents.add(event);
            }
        }
    }


    @Override
    public void onEditClick(final int position, Event event) {
        AddEventFragment addEventFragment = AddEventFragment.newInstance(null, event);
        addEventFragment.setOnUpdateListener(new AddEventFragment.OnUpdateListener() {
            @Override
            public void onUpdate() {
                mEventAdapter.notifyItemChanged(position);
            }
        });
        addFragment(addEventFragment);
    }

    @Override
    public void onDeleteClick(final int position, final Event event) {
        mEventDao.delete(event);
        mEvents.remove(event);
        mEventAdapter.notifyItemRemoved(position);
        mEventAdapter.notifyItemRangeChanged(position, mEvents.size());
        showSnackbar("删除成功^_~", "撤销", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEventDao.add(event);
                mEvents.add(event);  // FIXME: 2017/8/26 这里不能 event id变化了
                Collections.sort(mEvents, mComparator);
                mEventAdapter.notifyItemInserted(position);
                mEventAdapter.notifyItemRangeChanged(position, mEvents.size());
                if (position == 0) {
                    mRecyclerView.scrollToPosition(0);
                }
            }
        });
    }

    @Override
    public void onComplete(int position, Event event) {
        event.setComplete(true);
        mEventDao.update(event);
        mEventAdapter.notifyItemChanged(position);
        mCompleteEvents.add(event);
    }

    @Override
    public void onCompleteCancel(int position, Event event) {
        event.setComplete(false);
        mEventDao.update(event);
        mEventAdapter.notifyItemChanged(position);
        if (mCompleteEvents.contains(event)) {
            mCompleteEvents.remove(event);
        }
    }

    @Override
    public void onShare(Event event) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        String text = new StringBuffer()
                .append(event.getDetail())
                .append("\n 　　　　　--")
                .append(event.getTitle())
                .toString();
        intent.putExtra(Intent.EXTRA_SUBJECT, ".");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.setType("text/plain");
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_delete_all:
                DeleteFragment deleteFragment = DeleteFragment.newInstance();
                deleteFragment.setOnDeleteConfirmListener(this);
                getFragmentManager()
                        .beginTransaction()
                        .add(R.id.main, deleteFragment)
                        .addToBackStack(deleteFragment.getClass().getSimpleName())
                        .commit();
                break;
            default:
                removeFragment(this);
                break;
        }
    }

    @Override
    public void onConfirm(int type) {
        if (type == DeleteFragment.ALL) {
            mHandler.sendEmptyMessage(DELETE_ALL);
        } else {
            mHandler.sendEmptyMessage(DELETE_COMP);
        }
    }

    private void deleteComplete() {
        final List<Event> temp = new ArrayList<>();
        temp.addAll(mCompleteEvents);
        mEvents.removeAll(mCompleteEvents);
        mEventAdapter.notifyDataSetChanged();

        if (mTitle == null) {
            mEventDao.deleteComplete();
        } else {
            mEventDao.deleteComplete(mTitle);
        }

        mCompleteEvents.clear();
        showSnackbar("删除了划掉的事件。", "我要恢复", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.sendEmptyMessage(WAIT);
                for (Event event : temp) {
                    mEventDao.add(event);
                }
                mEvents.addAll(temp);
                mCompleteEvents.addAll(temp);
                Collections.sort(mEvents, mComparator);
                mEventAdapter.notifyDataSetChanged();
                mHandler.sendEmptyMessage(CANCEL);
            }
        });
    }

    private void deleteAll() {
        final List<Event> temp = new ArrayList<>();
        temp.addAll(mEvents);
        mEvents.clear();
        mEventAdapter.notifyDataSetChanged();

        if (mTitle == null) {
            mEventDao.deleteAll();
        } else {
            mEventDao.delete(mTitle);
        }

        showSnackbar("删除了全部的事件。", "我要恢复", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.sendEmptyMessage(WAIT);
                for (Event event : temp) {
                    mEventDao.add(event);
                }
                mEvents.addAll(temp);
                Collections.sort(mEvents, mComparator);
                mEventAdapter.notifyDataSetChanged();
                mHandler.sendEmptyMessage(CANCEL);
            }
        });
    }
}
