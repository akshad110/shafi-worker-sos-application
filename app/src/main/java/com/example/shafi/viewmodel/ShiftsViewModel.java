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

public class ShiftsViewModel extends AndroidViewModel {
    private final WorkerRepository repository;
    private final MutableLiveData<Resource<List<Dtos.ShiftItem>>> shifts = new MutableLiveData<>();
    private long loadedAt;
    private boolean busy;

    public ShiftsViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<List<Dtos.ShiftItem>>> shifts() {
        return shifts;
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
                Resource<List<Dtos.ShiftItem>> current = shifts.getValue();
                if (current == null || current.data == null) {
                    List<Dtos.ShiftItem> saved = repository.cachedShifts();
                    if (!saved.isEmpty()) shifts.postValue(Resource.success(saved));
                    else shifts.postValue(Resource.loading());
                }
                shifts.postValue(Resource.success(repository.shifts()));
                loadedAt = System.currentTimeMillis();
            } catch (ApiException error) {
                if (shifts.getValue() == null || shifts.getValue().data == null) {
                    shifts.postValue(Resource.error(error.getMessage()));
                }
            } catch (IOException error) {
                List<Dtos.ShiftItem> cached = repository.cachedShifts();
                if (!cached.isEmpty()) {
                    shifts.postValue(Resource.offline(cached, "Showing saved shifts"));
                    loadedAt = System.currentTimeMillis();
                } else if (shifts.getValue() == null || shifts.getValue().data == null) {
                    shifts.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
                }
            } finally {
                busy = false;
            }
        });
    }
}
