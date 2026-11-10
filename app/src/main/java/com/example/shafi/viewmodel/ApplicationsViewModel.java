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
import java.util.List;

public class ApplicationsViewModel extends AndroidViewModel {
    private final WorkerRepository repository;
    private final MutableLiveData<Resource<List<Dtos.ApplicationItem>>> applications = new MutableLiveData<>();

    public ApplicationsViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<List<Dtos.ApplicationItem>>> applications() {
        return applications;
    }

    public void refresh() {
        applications.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> {
            try {
                applications.postValue(Resource.success(repository.applications()));
            } catch (ApiException error) {
                applications.postValue(Resource.error(error.getMessage()));
            } catch (IOException error) {
                List<Dtos.ApplicationItem> cached = repository.cachedApplications();
                if (!cached.isEmpty()) applications.postValue(Resource.offline(cached, "Showing saved applications"));
                else applications.postValue(Resource.error("Can't reach Hospital SOS. Check your connection."));
            }
        });
    }
}
