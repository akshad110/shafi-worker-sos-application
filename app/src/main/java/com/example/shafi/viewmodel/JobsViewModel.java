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
import java.util.ArrayList;
import java.util.List;

public class JobsViewModel extends AndroidViewModel {
    private final WorkerRepository repository;
    private final MutableLiveData<Resource<List<Dtos.JobItem>>> jobs = new MutableLiveData<>();
    private final List<Dtos.JobItem> loaded = new ArrayList<>();
    private String query = "";
    private String shiftType = "";
    private boolean urgentOnly;
    private String sort = "newest";
    private int page = 1;
    private boolean hasMore;
    private boolean loading;

    public JobsViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<List<Dtos.JobItem>>> jobs() {
        return jobs;
    }

    public void search(String query, String shiftType, boolean urgentOnly, String sort) {
        this.query = query == null ? "" : query.trim();
        this.shiftType = shiftType == null ? "" : shiftType;
        this.urgentOnly = urgentOnly;
        this.sort = sort == null ? "newest" : sort;
        page = 1;
        fetch(true);
    }

    public void loadMore() {
        if (!hasMore || loading) return;
        page += 1;
        fetch(false);
    }

    private void fetch(boolean replace) {
        loading = true;
        if (replace && loaded.isEmpty()) jobs.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> {
            if (replace && loaded.isEmpty() && query.isEmpty() && shiftType.isEmpty() && !urgentOnly) {
                List<Dtos.JobItem> saved = repository.cachedJobs();
                if (!saved.isEmpty()) jobs.postValue(Resource.success(saved));
            }
            try {
                Dtos.JobPage result = repository.jobs(query, shiftType, urgentOnly, sort, page);
                List<Dtos.JobItem> items = result == null || result.items == null ? new ArrayList<>() : result.items;
                if (replace) loaded.clear();
                loaded.addAll(items);
                hasMore = result != null && result.hasMore;
                jobs.postValue(Resource.success(new ArrayList<>(loaded)));
            } catch (ApiException error) {
                jobs.postValue(Resource.error(error.getMessage()));
            } catch (IOException error) {
                List<Dtos.JobItem> cached = repository.cachedJobs();
                if (!cached.isEmpty() && replace) {
                    jobs.postValue(Resource.offline(cached, "Showing saved jobs"));
                } else {
                    jobs.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
                }
            } finally {
                loading = false;
            }
        });
    }
}
