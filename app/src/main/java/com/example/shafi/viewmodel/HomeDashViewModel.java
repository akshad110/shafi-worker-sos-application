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
        if (dashboard.getValue() == null || dashboard.getValue().data == null) {
            dashboard.setValue(Resource.loading());
        }
        AppExecutors.IO.execute(() -> {
            if (dashboard.getValue() == null || dashboard.getValue().data == null) {
                Dtos.Dashboard saved = repository.cachedDashboard();
                if (saved != null) dashboard.postValue(Resource.success(saved));
            }
            try {
                Dtos.Dashboard data = repository.dashboard();
                dashboard.postValue(Resource.success(data));
                Dtos.NoticePage notices = repository.notifications();
                unread.postValue(notices == null ? 0 : notices.unread);
            } catch (ApiException error) {
                dashboard.postValue(Resource.error(error.getMessage()));
            } catch (IOException error) {
                Dtos.Dashboard cached = repository.cachedDashboard();
                if (cached != null) {
                    dashboard.postValue(Resource.offline(cached, "Showing saved dashboard"));
                } else {
                    dashboard.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
                }
            }
        });
    }
}
