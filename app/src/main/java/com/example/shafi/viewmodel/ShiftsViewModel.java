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

    public ShiftsViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<List<Dtos.ShiftItem>>> shifts() {
        return shifts;
    }

    public void refresh() {
        if (shifts.getValue() == null || shifts.getValue().data == null) {
            shifts.setValue(Resource.loading());
        }
        AppExecutors.IO.execute(() -> {
            if (shifts.getValue() == null || shifts.getValue().data == null) {
                List<Dtos.ShiftItem> saved = repository.cachedShifts();
                if (!saved.isEmpty()) shifts.postValue(Resource.success(saved));
            }
            try {
                shifts.postValue(Resource.success(repository.shifts()));
            } catch (ApiException error) {
                shifts.postValue(Resource.error(error.getMessage()));
            } catch (IOException error) {
                List<Dtos.ShiftItem> cached = repository.cachedShifts();
                if (!cached.isEmpty()) shifts.postValue(Resource.offline(cached, "Showing saved shifts"));
                else shifts.postValue(Resource.error("Can't reach Shafi SOS. Check your connection."));
            }
        });
    }
}
