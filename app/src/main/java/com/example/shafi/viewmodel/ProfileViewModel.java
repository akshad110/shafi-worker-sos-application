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
import java.util.Map;

public class ProfileViewModel extends AndroidViewModel {
    private final WorkerRepository repository;
    private final MutableLiveData<Resource<Dtos.ProfileResponse>> profile = new MutableLiveData<>();
    private long loadedAt;
    private boolean busy;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<Dtos.ProfileResponse>> profile() {
        return profile;
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
                Resource<Dtos.ProfileResponse> current = profile.getValue();
                if (current == null || current.data == null) {
                    Dtos.ProfileResponse saved = repository.cachedProfile();
                    if (saved != null) profile.postValue(Resource.success(saved));
                    else profile.postValue(Resource.loading());
                }
                profile.postValue(Resource.success(repository.profile()));
                loadedAt = System.currentTimeMillis();
            } catch (ApiException error) {
                if (profile.getValue() == null || profile.getValue().data == null) {
                    profile.postValue(Resource.error(error.getMessage()));
                }
            } catch (IOException error) {
                Dtos.ProfileResponse cached = repository.cachedProfile();
                if (cached != null) {
                    profile.postValue(Resource.offline(cached, "Showing saved profile"));
                    loadedAt = System.currentTimeMillis();
                } else if (profile.getValue() == null || profile.getValue().data == null) {
                    profile.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
                }
            } finally {
                busy = false;
            }
        });
    }

    public void save(Map<String, Object> body) {
        AppExecutors.IO.execute(() -> {
            try {
                profile.postValue(Resource.success(repository.updateProfile(body)));
            } catch (ApiException error) {
                profile.postValue(Resource.error(error.getMessage()));
            } catch (IOException error) {
                profile.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
            }
        });
    }
}
