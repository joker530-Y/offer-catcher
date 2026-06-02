package com.offercatcher.adapters;

import com.offercatcher.model.Job;
import com.offercatcher.model.OfficialSource;
import com.offercatcher.model.StudentProfile;

import java.net.http.HttpClient;
import java.util.List;

public interface OfficialJobAdapter {
    List<Job> fetch(StudentProfile profile, OfficialSource source, HttpClient httpClient);
}
