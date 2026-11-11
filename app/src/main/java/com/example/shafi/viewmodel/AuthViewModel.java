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

public class AuthViewModel extends AndroidViewModel {
    private final WorkerRepository repository;
    private final MutableLiveData<Resource<Dtos.AuthResult>> result = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        repository = new WorkerRepository(application);
    }

    public LiveData<Resource<Dtos.AuthResult>> result() {
        return result;
    }

    public void login(String email, String password) {
        result.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> run(() -> repository.login(email, password)));
    }

    public void register(String name, String email, String password, String role) {
        result.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> run(() -> repository.register(name, email, password, role)));
    }

    public void sendOtp(String mobile, String purpose) {
        result.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> run(() -> repository.sendOtp(mobile, purpose)));
    }

    public void verify(String mobile, String code, String purpose) {
        result.setValue(Resource.loading());
        AppExecutors.IO.execute(() -> run(() -> repository.verify(mobile, code, purpose)));
    }

    private void run(Call call) {
        try {
            result.postValue(Resource.success(call.run()));
        } catch (ApiException error) {
            result.postValue(Resource.error(error.getMessage()));
        } catch (IOException error) {
            result.postValue(Resource.error("Can't reach Shafi SOS. Start the server and check the API address."));
        }
    }

    private interface Call {
        Dtos.AuthResult run() throws IOException, ApiException;
    }
}
