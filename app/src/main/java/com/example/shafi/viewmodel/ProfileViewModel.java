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

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<Dtos.ProfileResponse>> profile() {
        return profile;
    }

    public void refresh() {
        profile.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> {
            try {
                profile.postValue(Resource.success(repository.profile()));
            } catch (ApiException error) {
                profile.postValue(Resource.error(error.getMessage()));
            } catch (IOException error) {
                Dtos.ProfileResponse cached = repository.cachedProfile();
                if (cached != null) profile.postValue(Resource.offline(cached, "Showing saved profile"));
                else profile.postValue(Resource.error("Can't reach Hospital SOS. Check your connection."));
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
                profile.postValue(Resource.error("Can't reach Hospital SOS. Check your connection."));
            }
        });
    }
}
