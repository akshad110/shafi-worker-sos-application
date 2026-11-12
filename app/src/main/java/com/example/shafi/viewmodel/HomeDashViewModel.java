package com.example.shafi.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.shafi.core.ApiException;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;

import java.io.IOException;

public class HomeDashViewModel extends AndroidViewModel {
    private final WorkerRepository repository;
    private final MutableLiveData<Resource<Dtos.Dashboard>> dashboard = new MutableLiveData<>();
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);
    private long loadedAt;
    private boolean busy;

    public HomeDashViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<Dtos.Dashboard>> dashboard() {
        return dashboard;
    }

    public LiveData<Integer> unread() {
        return unread;
    }

    public void refresh() {
        refresh(false);
    }

    public void refresh(boolean force) {
        if (!force && loadedAt > 0 && System.currentTimeMillis() - loadedAt < 20_000) return;
        if (busy) return;
        busy = true;
        AppExecutors.IO.execute(() -> {
            try {
                Resource<Dtos.Dashboard> current = dashboard.getValue();
                if (current == null || current.data == null) {
                    Dtos.Dashboard saved = repository.cachedDashboard();
                    if (saved != null) dashboard.postValue(Resource.success(saved));
                    else dashboard.postValue(Resource.loading());
                }
                Dtos.Dashboard data = repository.dashboard();
                dashboard.postValue(Resource.success(data));
                loadedAt = System.currentTimeMillis();
                Dtos.NoticePage notices = repository.notifications();
                unread.postValue(notices == null ? 0 : notices.unread);
            } catch (ApiException error) {
                if (dashboard.getValue() == null || dashboard.getValue().data == null) {
                    dashboard.postValue(Resource.error(error.getMessage()));
                }
            } catch (IOException error) {
                Dtos.Dashboard cached = repository.cachedDashboard();
                if (cached != null) {
                    dashboard.postValue(Resource.offline(cached, "Showing saved dashboard"));
                    loadedAt = System.currentTimeMillis();
                } else if (dashboard.getValue() == null || dashboard.getValue().data == null) {
                    dashboard.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
                }
            } finally {
                busy = false;
            }
        });
    }
}
